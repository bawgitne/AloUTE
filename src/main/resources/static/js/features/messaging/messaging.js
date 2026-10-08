/**
 * AloUTE — Messaging Feature Script (Phase A REST Implementation)
 *
 * REST Endpoints:
 * - GET  /api/conversations?page={page}&size={size}
 * - GET  /api/conversations/{id}
 * - GET  /api/conversations/{id}/messages?page={page}&size={size}
 * - POST /api/conversations/{id}/messages
 *
 * Scope:
 * - Newest-first message history from backend normalized to display order (chronological).
 * - Pagination (load older messages prepended without jumping scroll position).
 * - Desktop two-pane and mobile list/detail/back toggle.
 * - Loading, empty, error, validation, and retry states.
 * - Confirmed minimal message body { content } on send.
 * - credentials: 'include', CSRF token on mutations, no JWT reading in JS.
 * - Optional ?conversationId selection only.
 */
(function () {
    'use strict';

    // -------------------------------------------------------------------------
    // Utilities & Security
    // -------------------------------------------------------------------------

    function getCsrfToken() {
        const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
        return match ? decodeURIComponent(match[1]) : null;
    }

    function csrfHeaders(extra) {
        const headers = Object.assign({
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        }, extra || {});
        const token = getCsrfToken();
        if (token) {
            headers['X-XSRF-TOKEN'] = token;
        }
        return headers;
    }

    async function readJson(response) {
        const text = await response.text();
        if (!text) return null;
        try {
            return JSON.parse(text);
        } catch (_) {
            return null;
        }
    }

    function translateError(error, defaultMsg) {
        if (!error) return defaultMsg || 'Đã xảy ra lỗi.';
        const code = error.code;
        if (code === 'CONVERSATION_NOT_FOUND') return 'Không tìm thấy cuộc trò chuyện.';
        if (code === 'CONVERSATION_MEMBERSHIP_REQUIRED' || code === 'ACCESS_DENIED') return 'Bạn không phải thành viên của cuộc trò chuyện này.';
        if (code === 'USER_NOT_FOUND') return 'Không tìm thấy người dùng.';
        if (code === 'VALIDATION_FAILED') {
            if (error.fieldErrors && error.fieldErrors.content) {
                return Array.isArray(error.fieldErrors.content) ? error.fieldErrors.content[0] : error.fieldErrors.content;
            }
            return error.message || 'Dữ liệu không hợp lệ.';
        }
        if (code === 'AUTH_TOKEN_EXPIRED') return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
        return error.message || defaultMsg || 'Đã xảy ra lỗi.';
    }

    function escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function formatTime(isoString) {
        if (!isoString) return '';
        try {
            const date = new Date(isoString);
            const now = new Date();
            const diffMs = now - date;
            const diffMin = Math.floor(diffMs / 60000);
            if (diffMin < 1) return 'Vừa xong';
            if (diffMin < 60) return diffMin + ' phút trước';
            const diffHours = Math.floor(diffMin / 60);
            if (diffHours < 24 && date.getDate() === now.getDate()) {
                const hours = date.getHours().toString().padStart(2, '0');
                const minutes = date.getMinutes().toString().padStart(2, '0');
                return hours + ':' + minutes;
            }
            const day = date.getDate().toString().padStart(2, '0');
            const month = (date.getMonth() + 1).toString().padStart(2, '0');
            return day + '/' + month;
        } catch (_) {
            return '';
        }
    }

    // -------------------------------------------------------------------------
    // Component State
    // -------------------------------------------------------------------------

    const state = {
        currentUserId: null,
        activeConversationId: null,
        activeConversationData: null,
        conversations: [],
        conversationsPage: 0,
        conversationsHasNext: false,
        isLoadingConversations: false,
        messages: [], // Chronological display order: [oldest, ..., newest]
        messagesPage: 0,
        messagesHasNext: false,
        isLoadingMessages: false,
        isSendingMessage: false,
        lastFailedDraft: null
    };

    let messagesRequestId = 0;
    let messagesAbortController = null;
    let detailsRequestId = 0;

    // -------------------------------------------------------------------------
    // DOM Elements
    // -------------------------------------------------------------------------

    let elCard, elList, elLoading, elError, elErrorMsg, elEmpty;
    let elLoadMoreWrap, btnLoadMoreConv, searchInput, btnRetryConv;
    let elUnselectedState, elActiveContainer;
    let elHeaderAvatar, elHeaderTitle, elHeaderSubtitle;
    let elHistory, elMessagesLoading, elMessagesError, elMessagesErrorMsg, elMessagesEmpty;
    let elLoadOlderWrap, btnLoadOlder, btnRetryMessages;
    let sendForm, messageInput, btnSend, sendErrorBar, sendErrorText, btnRetrySend;
    let btnBackToList, btnRefreshMessages;

    function initDomElements() {
        const root = document.getElementById('messaging-content');
        if (!root) return false;

        // Current user ID from Thymeleaf attribute if available
        const uidAttr = root.getAttribute('data-current-user-id');
        if (uidAttr && !isNaN(parseInt(uidAttr, 10))) {
            state.currentUserId = parseInt(uidAttr, 10);
        }

        elCard = document.getElementById('messaging-card');
        elList = document.getElementById('conversations-list');
        elLoading = document.getElementById('conversations-loading');
        elError = document.getElementById('conversations-error');
        elErrorMsg = document.getElementById('conversations-error-msg');
        elEmpty = document.getElementById('conversations-empty');
        elLoadMoreWrap = document.getElementById('conversations-load-more-wrap');
        btnLoadMoreConv = document.getElementById('btn-load-more-conversations');
        searchInput = document.getElementById('search-conversations-input');
        btnRetryConv = document.getElementById('btn-retry-conversations');

        elUnselectedState = document.getElementById('chat-unselected-state');
        elActiveContainer = document.getElementById('chat-active-container');
        elHeaderAvatar = document.getElementById('chat-header-avatar');
        elHeaderTitle = document.getElementById('chat-header-title');
        elHeaderSubtitle = document.getElementById('chat-header-subtitle');

        elHistory = document.getElementById('chat-history');
        elMessagesLoading = document.getElementById('messages-loading');
        elMessagesError = document.getElementById('messages-error');
        elMessagesErrorMsg = document.getElementById('messages-error-msg');
        elMessagesEmpty = document.getElementById('messages-empty');
        elLoadOlderWrap = document.getElementById('load-older-messages-wrap');
        btnLoadOlder = document.getElementById('btn-load-older-messages');
        btnRetryMessages = document.getElementById('btn-retry-messages');

        sendForm = document.getElementById('message-send-form');
        messageInput = document.getElementById('message-input');
        btnSend = document.getElementById('btn-send-message');
        sendErrorBar = document.getElementById('send-error-bar');
        sendErrorText = document.getElementById('send-error-text');
        btnRetrySend = document.getElementById('btn-retry-send');

        btnBackToList = document.getElementById('btn-back-to-conversations');
        btnRefreshMessages = document.getElementById('btn-refresh-messages');

        return true;
    }

    // -------------------------------------------------------------------------
    // Conversation View Rendering
    // -------------------------------------------------------------------------

    function getConversationDisplayName(conv) {
        if (conv.name && conv.name.trim()) return conv.name.trim();
        if (conv.otherParticipant) {
            return conv.otherParticipant.displayName || conv.otherParticipant.username || 'Sinh viên';
        }
        if (conv.members && conv.members.length > 0) {
            const others = conv.members.filter(m => state.currentUserId == null || m.id !== state.currentUserId);
            if (others.length > 0) {
                return others[0].displayName || others[0].username || 'Sinh viên';
            }
        }
        return 'Hội thoại #' + conv.id;
    }

    function getConversationAvatar(conv) {
        if (conv.avatarUrl && conv.avatarUrl.trim()) return conv.avatarUrl;
        if (conv.otherParticipant && conv.otherParticipant.avatarUrl) return conv.otherParticipant.avatarUrl;
        if (conv.members && conv.members.length > 0) {
            const others = conv.members.filter(m => state.currentUserId == null || m.id !== state.currentUserId);
            if (others.length > 0 && others[0].avatarUrl) return others[0].avatarUrl;
        }
        return 'https://placehold.co/44x44/e2e8f0/475569?text=U';
    }

    function getConversationSubtitle(conv) {
        if (conv.otherParticipant && conv.otherParticipant.username) {
            return '@' + conv.otherParticipant.username;
        }
        if (conv.members && conv.members.length > 1) {
            return conv.members.length + ' thành viên';
        }
        return '';
    }

    function renderConversationsList() {
        if (!elList) return;
        elList.innerHTML = '';

        const filterText = (searchInput ? searchInput.value.trim().toLowerCase() : '');
        const itemsToRender = state.conversations.filter(conv => {
            if (!filterText) return true;
            const name = getConversationDisplayName(conv).toLowerCase();
            const sub = getConversationSubtitle(conv).toLowerCase();
            return name.includes(filterText) || sub.includes(filterText);
        });

        if (itemsToRender.length === 0 && state.conversations.length > 0) {
            const noMatch = document.createElement('li');
            noMatch.className = 'p-3 text-center text-muted small';
            noMatch.textContent = 'Không tìm thấy cuộc trò chuyện phù hợp.';
            elList.appendChild(noMatch);
            return;
        }

        itemsToRender.forEach(conv => {
            const li = document.createElement('li');
            li.setAttribute('role', 'option');
            li.setAttribute('aria-selected', conv.id === state.activeConversationId ? 'true' : 'false');
            li.tabIndex = 0;
            li.className = 'aloute-conversation-item' + (conv.id === state.activeConversationId ? ' active' : '');
            li.setAttribute('data-conversation-id', conv.id);

            const avatarUrl = getConversationAvatar(conv);
            const displayName = getConversationDisplayName(conv);
            const lastSnippet = conv.lastMessage ? escapeHtml(conv.lastMessage.content) : 'Chưa có tin nhắn';
            const timeStr = conv.lastMessage ? formatTime(conv.lastMessage.createdAt) : formatTime(conv.updatedAt || conv.createdAt);

            li.innerHTML = `
                <img src="${escapeHtml(avatarUrl)}" alt="Avatar" class="avatar-img avatar-sm rounded-circle flex-shrink-0" width="40" height="40">
                <div class="aloute-min-w-0 flex-grow-1">
                    <div class="d-flex align-items-center justify-content-between gap-1 mb-1">
                        <span class="fw-semibold small text-truncate text-dark">${escapeHtml(displayName)}</span>
                        <span class="small text-muted flex-shrink-0" style="font-size: 0.75rem;">${timeStr}</span>
                    </div>
                    <div class="small text-muted text-truncate">${lastSnippet}</div>
                </div>
            `;

            li.addEventListener('click', function () {
                selectConversation(conv.id);
            });
            li.addEventListener('keydown', function (e) {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    selectConversation(conv.id);
                }
            });

            elList.appendChild(li);
        });
    }

    // -------------------------------------------------------------------------
    // API: Fetch Conversations
    // -------------------------------------------------------------------------

    async function loadConversations(page, append) {
        if (state.isLoadingConversations) return;
        state.isLoadingConversations = true;

        if (!append) {
            if (elLoading) elLoading.classList.remove('d-none');
            if (elError) elError.classList.add('d-none');
            if (elEmpty) elEmpty.classList.add('d-none');
            if (elList) elList.innerHTML = '';
        }

        try {
            const response = await fetch(`/api/conversations?page=${page}&size=20`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Accept': 'application/json' }
            });

            const body = await readJson(response);

            if (!response.ok || !body || !body.success) {
                throw new Error(translateError(body && body.error, 'Không thể tải danh sách cuộc trò chuyện.'));
            }

            const data = body.data || [];
            const meta = body.meta || {};

            state.conversationsPage = meta.page != null ? meta.page : page;
            state.conversationsHasNext = Boolean(meta.hasNext);

            if (append) {
                state.conversations = state.conversations.concat(data);
            } else {
                state.conversations = data;
            }

            if (elLoading) elLoading.classList.add('d-none');

            if (state.conversations.length === 0) {
                if (elEmpty) elEmpty.classList.remove('d-none');
            } else {
                if (elEmpty) elEmpty.classList.add('d-none');
                renderConversationsList();
            }

            if (elLoadMoreWrap) {
                if (state.conversationsHasNext) {
                    elLoadMoreWrap.classList.remove('d-none');
                } else {
                    elLoadMoreWrap.classList.add('d-none');
                }
            }

            // Auto-select first conversation on desktop if none active
            if (window.innerWidth >= 768 && !state.activeConversationId && state.conversations.length > 0) {
                selectConversation(state.conversations[0].id);
            }
        } catch (err) {
            if (elLoading) elLoading.classList.add('d-none');
            if (elError) {
                elError.classList.remove('d-none');
                if (elErrorMsg) elErrorMsg.textContent = err.message || 'Không thể kết nối đến máy chủ.';
            }
        } finally {
            state.isLoadingConversations = false;
        }
    }

    // -------------------------------------------------------------------------
    // API: Select & Load Conversation Detail / Messages
    // -------------------------------------------------------------------------

    async function selectConversation(conversationId) {
        state.activeConversationId = conversationId;

        // Visual update in list
        if (elList) {
            elList.querySelectorAll('.aloute-conversation-item').forEach(el => {
                const id = parseInt(el.getAttribute('data-conversation-id'), 10);
                if (id === conversationId) {
                    el.classList.add('active');
                    el.setAttribute('aria-selected', 'true');
                } else {
                    el.classList.remove('active');
                    el.setAttribute('aria-selected', 'false');
                }
            });
        }

        // Responsive mobile view toggle: switch to chat pane
        if (elCard) {
            elCard.classList.add('show-chat');
        }

        // Show chat interface
        if (elUnselectedState) elUnselectedState.classList.add('d-none');
        if (elActiveContainer) {
            elActiveContainer.classList.remove('d-none');
            elActiveContainer.classList.add('d-flex');
        }

        // Find conversation details
        const found = state.conversations.find(c => c.id === conversationId);
        if (found) {
            state.activeConversationData = found;
            updateChatHeader(found);
        } else {
            // Fetch conversation details from GET /api/conversations/{id}
            fetchConversationDetails(conversationId);
        }

        // Abort in-flight messages request and start new generation
        if (messagesAbortController) {
            messagesAbortController.abort();
            messagesAbortController = null;
        }
        const reqId = ++messagesRequestId;
        messagesAbortController = new AbortController();

        // Reset message history & state for the newly selected conversation
        state.isLoadingMessages = false;
        state.messages = [];
        state.messagesPage = 0;
        state.messagesHasNext = false;
        if (sendErrorBar) sendErrorBar.classList.add('d-none');
        if (messageInput) messageInput.value = '';

        await loadMessages(conversationId, 0, false, reqId);
    }

    function updateChatHeader(conv) {
        if (!elHeaderTitle) return;
        elHeaderTitle.textContent = getConversationDisplayName(conv);
        if (elHeaderSubtitle) elHeaderSubtitle.textContent = getConversationSubtitle(conv);
        if (elHeaderAvatar) elHeaderAvatar.src = getConversationAvatar(conv);
    }

    async function fetchConversationDetails(conversationId) {
        const dReqId = ++detailsRequestId;
        try {
            const response = await fetch(`/api/conversations/${conversationId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Accept': 'application/json' }
            });
            const body = await readJson(response);
            if (dReqId !== detailsRequestId || conversationId !== state.activeConversationId) {
                return;
            }
            if (response.ok && body && body.success && body.data) {
                state.activeConversationData = body.data;
                updateChatHeader(body.data);
            }
        } catch (_) {}
    }

    // -------------------------------------------------------------------------
    // API: Load Messages History (Normalized newest-first to display order)
    // -------------------------------------------------------------------------

    async function loadMessages(conversationId, page, prependOlder, reqId) {
        if (conversationId !== state.activeConversationId) return;

        const currentReqId = reqId || ++messagesRequestId;
        if (prependOlder) {
            if (state.isLoadingMessages) return;
            if (currentReqId !== messagesRequestId) return;
        } else {
            if (!messagesAbortController || messagesAbortController.signal.aborted) {
                messagesAbortController = new AbortController();
            }
        }

        state.isLoadingMessages = true;

        if (!prependOlder) {
            if (elMessagesLoading) elMessagesLoading.classList.remove('d-none');
            if (elMessagesError) elMessagesError.classList.add('d-none');
            if (elMessagesEmpty) elMessagesEmpty.classList.add('d-none');
            if (elHistory) elHistory.innerHTML = '';
            if (elLoadOlderWrap) elLoadOlderWrap.classList.add('d-none');
        }

        const signal = messagesAbortController ? messagesAbortController.signal : undefined;

        try {
            const response = await fetch(`/api/conversations/${conversationId}/messages?page=${page}&size=30`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Accept': 'application/json' },
                signal: signal
            });

            const body = await readJson(response);

            // Stale response guard: ignore if user switched conversation or triggered a newer request
            if (currentReqId !== messagesRequestId || conversationId !== state.activeConversationId) {
                return;
            }

            if (!response.ok || !body || !body.success) {
                throw new Error(translateError(body && body.error, 'Không thể nạp tin nhắn.'));
            }

            const rawData = body.data || [];
            const meta = body.meta || {};

            state.messagesPage = meta.page != null ? meta.page : page;
            state.messagesHasNext = Boolean(meta.hasNext);

            // BACKEND NORMALIZATION:
            // Backend returns messages newest-first (OrderByCreatedAtDesc).
            // Normalizing to chronological display order (oldest at top, newest at bottom):
            const chronologicalBatch = rawData.slice().reverse();

            if (prependOlder) {
                const existingIds = new Set(state.messages.map(m => m.id));
                const newOlderMessages = chronologicalBatch.filter(m => !existingIds.has(m.id));
                if (newOlderMessages.length > 0) {
                    const prevHeight = elHistory ? elHistory.scrollHeight : 0;
                    state.messages = newOlderMessages.concat(state.messages);
                    renderAllMessages();
                    if (elHistory) {
                        const newHeight = elHistory.scrollHeight;
                        elHistory.scrollTop = newHeight - prevHeight; // Preserve scroll position
                    }
                }
            } else {
                const seen = new Set();
                state.messages = chronologicalBatch.filter(m => {
                    if (m && m.id != null) {
                        if (seen.has(m.id)) return false;
                        seen.add(m.id);
                    }
                    return true;
                });
                renderAllMessages();
                if (elHistory) {
                    elHistory.scrollTop = elHistory.scrollHeight; // Scroll to newest message
                }
            }

            if (elMessagesLoading) elMessagesLoading.classList.add('d-none');

            if (state.messages.length === 0) {
                if (elMessagesEmpty) elMessagesEmpty.classList.remove('d-none');
            } else {
                if (elMessagesEmpty) elMessagesEmpty.classList.add('d-none');
            }

            if (elLoadOlderWrap) {
                if (state.messagesHasNext) {
                    elLoadOlderWrap.classList.remove('d-none');
                } else {
                    elLoadOlderWrap.classList.add('d-none');
                }
            }
        } catch (err) {
            if (err.name === 'AbortError') {
                return; // Request was aborted by newer selection/refresh; ignore silently
            }
            if (currentReqId !== messagesRequestId || conversationId !== state.activeConversationId) {
                return; // Stale error from earlier request; discard
            }
            if (elMessagesLoading) elMessagesLoading.classList.add('d-none');
            if (elMessagesError) {
                elMessagesError.classList.remove('d-none');
                if (elMessagesErrorMsg) elMessagesErrorMsg.textContent = err.message || 'Không thể tải tin nhắn.';
            }
        } finally {
            if (currentReqId === messagesRequestId && conversationId === state.activeConversationId) {
                state.isLoadingMessages = false;
            }
        }
    }

    function isMyMessage(msg) {
        if (!msg || !msg.sender) return false;
        if (state.currentUserId != null) {
            return msg.sender.id === state.currentUserId;
        }
        // Infer from otherParticipant if currentUserId was not in initial viewmodel
        if (state.activeConversationData && state.activeConversationData.otherParticipant) {
            return msg.sender.id !== state.activeConversationData.otherParticipant.id;
        }
        return false;
    }

    function createMessageBubble(msg) {
        const isMine = isMyMessage(msg);
        const bubble = document.createElement('div');
        bubble.className = 'aloute-message-bubble ' + (isMine ? 'outgoing' : 'incoming');
        bubble.setAttribute('data-message-id', msg.id);

        let senderName = '';
        if (!isMine && msg.sender) {
            senderName = `<div class="fw-semibold text-primary mb-1" style="font-size: 0.8125rem;">${escapeHtml(msg.sender.displayName || msg.sender.username)}</div>`;
        }

        const timeFormatted = formatTime(msg.createdAt);

        bubble.innerHTML = `
            ${senderName}
            <div class="message-text">${escapeHtml(msg.content)}</div>
            <span class="aloute-message-time" aria-label="Thời gian gửi">${timeFormatted}</span>
        `;
        return bubble;
    }

    function renderAllMessages() {
        if (!elHistory) return;
        elHistory.innerHTML = '';
        state.messages.forEach(msg => {
            elHistory.appendChild(createMessageBubble(msg));
        });
    }

    // -------------------------------------------------------------------------
    // API: Send Message (POST /api/conversations/{id}/messages)
    // -------------------------------------------------------------------------

    async function handleSendMessage(event) {
        if (event) event.preventDefault();
        if (!state.activeConversationId || state.isSendingMessage) return;

        const content = messageInput ? messageInput.value.trim() : '';
        if (!content) {
            if (messageInput) messageInput.focus();
            return;
        }

        // Frontend character cap (backend SendMessageRequest enforces @NotBlank only on TEXT column)
        if (content.length > 2000) {
            showSendError('Tin nhắn vượt quá giới hạn 2000 ký tự.');
            return;
        }

        const targetConvId = state.activeConversationId;
        state.isSendingMessage = true;
        state.lastFailedDraft = null;
        if (sendErrorBar) sendErrorBar.classList.add('d-none');
        if (btnSend) {
            btnSend.disabled = true;
            btnSend.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>';
        }

        // Minimal confirmed payload: { content }
        const payload = {
            content: content
        };

        try {
            const response = await fetch(`/api/conversations/${targetConvId}/messages`, {
                method: 'POST',
                credentials: 'include',
                headers: csrfHeaders(),
                body: JSON.stringify(payload)
            });

            const body = await readJson(response);

            if (!response.ok || !body || !body.success) {
                throw new Error(translateError(body && body.error, 'Không thể gửi tin nhắn.'));
            }

            const sentMsg = body.data;

            // Only update view if the active conversation hasn't changed
            if (targetConvId === state.activeConversationId) {
                if (messageInput) {
                    messageInput.value = '';
                    messageInput.focus();
                }

                if (!state.messages.some(m => m.id === sentMsg.id)) {
                    state.messages.push(sentMsg);
                    if (elHistory) {
                        elHistory.appendChild(createMessageBubble(sentMsg));
                        elHistory.scrollTop = elHistory.scrollHeight;
                    }
                }
                if (elMessagesEmpty) elMessagesEmpty.classList.add('d-none');
            }

            // Update lastMessage in conversation list
            const conv = state.conversations.find(c => c.id === targetConvId);
            if (conv) {
                conv.lastMessage = sentMsg;
                conv.updatedAt = sentMsg.createdAt;
                renderConversationsList();
            }
        } catch (err) {
            if (targetConvId === state.activeConversationId) {
                state.lastFailedDraft = content;
                showSendError(err.message || 'Lỗi gửi tin nhắn.');
            }
        } finally {
            state.isSendingMessage = false;
            if (btnSend) {
                btnSend.disabled = false;
                btnSend.innerHTML = '<i class="bi bi-send-fill" aria-hidden="true"></i>';
            }
        }
    }

    function showSendError(msg) {
        if (!sendErrorBar) return;
        if (sendErrorText) sendErrorText.textContent = msg;
        sendErrorBar.classList.remove('d-none');
    }

    // -------------------------------------------------------------------------
    // Event Listeners & Initialization
    // -------------------------------------------------------------------------

    function setupEventListeners() {
        if (btnRetryConv) {
            btnRetryConv.addEventListener('click', () => loadConversations(0, false));
        }

        if (btnLoadMoreConv) {
            btnLoadMoreConv.addEventListener('click', () => {
                if (state.conversationsHasNext) {
                    loadConversations(state.conversationsPage + 1, true);
                }
            });
        }

        if (searchInput) {
            searchInput.addEventListener('input', () => renderConversationsList());
        }

        if (btnLoadOlder) {
            btnLoadOlder.addEventListener('click', () => {
                if (state.activeConversationId && state.messagesHasNext && !state.isLoadingMessages) {
                    loadMessages(state.activeConversationId, state.messagesPage + 1, true, messagesRequestId);
                }
            });
        }

        if (btnRetryMessages) {
            btnRetryMessages.addEventListener('click', () => {
                if (state.activeConversationId) {
                    if (messagesAbortController) {
                        messagesAbortController.abort();
                        messagesAbortController = null;
                    }
                    const reqId = ++messagesRequestId;
                    messagesAbortController = new AbortController();
                    state.isLoadingMessages = false;
                    loadMessages(state.activeConversationId, 0, false, reqId);
                }
            });
        }

        if (sendForm) {
            sendForm.addEventListener('submit', handleSendMessage);
        }

        if (messageInput) {
            messageInput.addEventListener('keydown', function (e) {
                if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    handleSendMessage();
                }
            });
        }

        if (btnRetrySend) {
            btnRetrySend.addEventListener('click', function () {
                if (state.lastFailedDraft && messageInput) {
                    messageInput.value = state.lastFailedDraft;
                }
                handleSendMessage();
            });
        }

        if (btnRefreshMessages) {
            btnRefreshMessages.addEventListener('click', () => {
                if (state.activeConversationId) {
                    if (messagesAbortController) {
                        messagesAbortController.abort();
                        messagesAbortController = null;
                    }
                    const reqId = ++messagesRequestId;
                    messagesAbortController = new AbortController();
                    state.isLoadingMessages = false;
                    loadMessages(state.activeConversationId, 0, false, reqId);
                }
            });
        }

        // Mobile Back Button: return to conversations list
        if (btnBackToList) {
            btnBackToList.addEventListener('click', () => {
                if (elCard) elCard.classList.remove('show-chat');
            });
        }

        // URL parameters check: auto open ?conversationId=123 only
        const urlParams = new URLSearchParams(window.location.search);
        const qConvId = urlParams.get('conversationId');

        if (qConvId && !isNaN(parseInt(qConvId, 10))) {
            const targetId = parseInt(qConvId, 10);
            selectConversation(targetId);
        }
    }

    function init() {
        if (!initDomElements()) return;
        setupEventListeners();
        loadConversations(0, false);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
