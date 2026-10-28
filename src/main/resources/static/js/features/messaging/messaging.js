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
        if (code === 'CONVERSATION_MEMBERSHIP_REQUIRED' || code === 'ACCESS_DENIED' || code === 'FORBIDDEN') return 'Bạn không phải thành viên của cuộc trò chuyện này.';
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
        lastFailedDraft: null,
        mutationSeq: 0
    };

    let messagesRequestId = 0;
    let messagesAbortController = null;
    let detailsRequestId = 0;

    // -------------------------------------------------------------------------
    // DOM Elements
    // -------------------------------------------------------------------------

    let elWrapper;
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
        elWrapper = root;

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
        return '/images/avatar-fallback.svg';
    }

    function getTargetConversationId() {
        const wrapper = elWrapper || (typeof document !== 'undefined' ? document.getElementById('messaging-content') : null);
        if (wrapper && wrapper.dataset && wrapper.dataset.initialConversationId) {
            const id = parseInt(wrapper.dataset.initialConversationId, 10);
            if (!isNaN(id) && id > 0) return id;
        }
        if (typeof window !== 'undefined' && window.location) {
            if (window.location.pathname) {
                const match = window.location.pathname.match(/\/messages\/(\d+)/);
                if (match) {
                    const id = parseInt(match[1], 10);
                    if (!isNaN(id) && id > 0) return id;
                }
            }
            if (window.location.search) {
                const urlParams = new URLSearchParams(window.location.search);
                const q = urlParams.get('conversationId');
                if (q) {
                    const id = parseInt(q, 10);
                    if (!isNaN(id) && id > 0) return id;
                }
            }
        }
        return null;
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

            const unreadBadge = (conv.unreadCount && conv.unreadCount > 0)
                ? `<span class="badge rounded-pill bg-primary ms-auto flex-shrink-0" style="font-size: 0.7rem;">${conv.unreadCount > 99 ? '99+' : conv.unreadCount}</span>`
                : '';

            li.innerHTML = `
                <img src="${escapeHtml(avatarUrl)}" alt="Avatar" class="avatar-img avatar-sm rounded-circle flex-shrink-0" width="40" height="40">
                <div class="aloute-min-w-0 flex-grow-1">
                    <div class="d-flex align-items-center justify-content-between gap-1 mb-1">
                        <span class="fw-semibold small text-truncate text-dark">${escapeHtml(displayName)}</span>
                        <span class="small text-muted flex-shrink-0" style="font-size: 0.75rem;">${timeStr}</span>
                    </div>
                    <div class="d-flex align-items-center justify-content-between gap-1">
                        <div class="small text-muted text-truncate">${lastSnippet}</div>
                        ${unreadBadge}
                    </div>
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

    let pendingConversationsRefresh = false;
    let conversationsRequestId = 0;

    function getMessageTimestamp(msg) {
        if (!msg || !msg.createdAt) return 0;
        const t = new Date(msg.createdAt).getTime();
        return isNaN(t) ? 0 : t;
    }

    function compareMessages(msgA, msgB) {
        if (!msgA && !msgB) return 0;
        if (!msgA) return -1;
        if (!msgB) return 1;
        const tA = getMessageTimestamp(msgA);
        const tB = getMessageTimestamp(msgB);
        if (tA !== tB) {
            return tA > tB ? 1 : -1;
        }
        const idA = typeof msgA.id === 'number' ? msgA.id : parseInt(msgA.id, 10) || 0;
        const idB = typeof msgB.id === 'number' ? msgB.id : parseInt(msgB.id, 10) || 0;
        if (idA !== idB) {
            return idA > idB ? 1 : -1;
        }
        return 0;
    }

    async function loadConversations(page, append) {
        if (state.isLoadingConversations) {
            if (!append) {
                pendingConversationsRefresh = true;
            }
            return;
        }
        state.isLoadingConversations = true;
        const currentReqId = ++conversationsRequestId;
        const requestDispatchedSeq = ++state.mutationSeq;

        if (!append && state.conversations.length === 0) {
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

            // Stale response guard: ignore if a newer request was dispatched while this was in flight
            if (currentReqId !== conversationsRequestId) {
                return;
            }

            if (!response.ok || !body || !body.success) {
                throw new Error(translateError(body && body.error, 'Không thể tải danh sách cuộc trò chuyện.'));
            }

            const data = body.data || [];
            const meta = body.meta || {};

            state.conversationsPage = meta.page != null ? meta.page : page;
            state.conversationsHasNext = Boolean(meta.hasNext);

            // Reconcile conversation summaries without clobbering newer realtime state
            for (const serverConv of data) {
                const existing = state.conversations.find(c => c.id === serverConv.id);
                if (!existing) {
                    serverConv.summaryLastMessage = serverConv.lastMessage;
                    serverConv.lastMutationSeq = requestDispatchedSeq;
                    if (state.activeConversationId && serverConv.id === state.activeConversationId) {
                        serverConv.unreadCount = 0;
                        serverConv.lastMutationSeq = ++state.mutationSeq;
                    }
                } else {
                    const cmp = compareMessages(serverConv.lastMessage, existing.lastMessage);

                    // Reconcile preview: preserve existing if local is strictly newer (cmp < 0)
                    if (cmp > 0) {
                        existing.lastMessage = serverConv.lastMessage;
                        existing.updatedAt = serverConv.updatedAt || (existing.lastMessage ? existing.lastMessage.createdAt : existing.updatedAt);
                        existing.summaryLastMessage = serverConv.lastMessage;
                    }

                    // Reconcile unreadCount
                    if (state.activeConversationId && existing.id === state.activeConversationId) {
                        existing.unreadCount = 0;
                        existing.lastMutationSeq = ++state.mutationSeq;
                        existing.summaryLastMessage = existing.lastMessage;
                    } else if (existing.lastMutationSeq && existing.lastMutationSeq > requestDispatchedSeq) {
                        // Intervening local mutations (reads or live events) occurred after this request was dispatched:
                        // preserve local unreadCount
                    } else {
                        // Authoritative fresh response with no intervening local mutations:
                        existing.unreadCount = serverConv.unreadCount || 0;
                        existing.summaryLastMessage = serverConv.lastMessage;
                        existing.lastMutationSeq = requestDispatchedSeq;
                    }

                    serverConv.lastMessage = existing.lastMessage;
                    serverConv.updatedAt = existing.updatedAt;
                    serverConv.unreadCount = existing.unreadCount;
                    serverConv.summaryLastMessage = existing.summaryLastMessage;
                    serverConv.lastMutationSeq = existing.lastMutationSeq;
                }
            }

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

            // Only auto-select a conversation explicitly resolved from the URL/dataset.
            // Desktop fallback to the first conversation would fire a read receipt
            // for content the user never opened, consuming the unread state.
            const targetId = getTargetConversationId();
            if (targetId && !state.activeConversationId) {
                selectConversation(targetId);
            }
        } catch (err) {
            if (elLoading) elLoading.classList.add('d-none');
            if (elError) {
                elError.classList.remove('d-none');
                if (elErrorMsg) {
                    elErrorMsg.textContent = err && err.name === 'TypeError'
                        ? 'Không thể kết nối đến máy chủ.'
                        : (err.message || 'Không thể kết nối đến máy chủ.');
                }
            }
        } finally {
            state.isLoadingConversations = false;
            if (pendingConversationsRefresh) {
                pendingConversationsRefresh = false;
                loadConversations(0, false);
            }
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
            found.lastMutationSeq = ++state.mutationSeq;
            found.summaryLastMessage = found.lastMessage;
            if (found.unreadCount > 0) {
                found.unreadCount = 0;
                renderConversationsList();
            }
        } else {
            // Fetch conversation details from GET /api/conversations/{id}
            fetchConversationDetails(conversationId);
        }

        // Send read receipt to backend
        fetch(`/api/conversations/${conversationId}/read`, {
            method: 'POST',
            headers: csrfHeaders(),
            credentials: 'include'
        }).catch(err => console.debug('Failed to send read receipt:', err));

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
                // Reset to consistent newest page 0 from server, preserving only live arrivals during fetch
                const batchIds = new Set(chronologicalBatch.map(m => m.id));
                const latestBatchTime = chronologicalBatch.length > 0 && chronologicalBatch[chronologicalBatch.length - 1].createdAt
                    ? new Date(chronologicalBatch[chronologicalBatch.length - 1].createdAt).getTime()
                    : 0;
                const liveArrivals = state.messages.filter(m =>
                    m && m.id != null && !batchIds.has(m.id) &&
                    (m.createdAt ? new Date(m.createdAt).getTime() >= latestBatchTime : true)
                );
                state.messages = chronologicalBatch.concat(liveArrivals);
                state.messages.sort((a, b) => {
                    const tA = a.createdAt ? new Date(a.createdAt).getTime() : 0;
                    const tB = b.createdAt ? new Date(b.createdAt).getTime() : 0;
                    if (tA !== tB) return tA - tB;
                    return (a.id || 0) - (b.id || 0);
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
                if (elMessagesErrorMsg) {
                    elMessagesErrorMsg.textContent = err && err.name === 'TypeError'
                        ? 'Không thể kết nối đến máy chủ.'
                        : (err.message || 'Không thể tải tin nhắn.');
                }
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
            if (messageInput) messageInput.focus({ preventScroll: true });
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
                    messageInput.focus({ preventScroll: true });
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
                conv.lastMutationSeq = ++state.mutationSeq;
                renderConversationsList();
            }
        } catch (err) {
            if (targetConvId === state.activeConversationId) {
                state.lastFailedDraft = content;
                showSendError(err && err.name === 'TypeError'
                    ? 'Không thể kết nối đến máy chủ. Vui lòng thử lại.'
                    : (err.message || 'Lỗi gửi tin nhắn.'));
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

        // Page lifecycle listeners for cleanup and bfcache restoration
        if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
            window.addEventListener('pagehide', () => cleanupStomp(true));
            window.addEventListener('beforeunload', () => cleanupStomp(true));
            window.addEventListener('pageshow', (event) => {
                if (event && event.persisted) {
                    initStompClient();
                }
            });
        }

        // Auto open initial conversation if target resolved from URL, path, or dataset
        const targetId = getTargetConversationId();
        if (targetId) {
            selectConversation(targetId);
        }
    }

    // -------------------------------------------------------------------------
    // STOMP WebSocket Realtime Integration
    // -------------------------------------------------------------------------

    let stompSocket = null;
    let stompConnected = false;
    let stompHasConnectedOnce = false;
    let stompReconnectTimer = null;
    let currentSocketId = 0;
    let incomingBuffer = '';

    function buildStompFrame(command, headers, body) {
        let frame = command + '\n';
        for (const [k, v] of Object.entries(headers || {})) {
            if (v !== undefined && v !== null) {
                frame += `${k}:${v}\n`;
            }
        }
        frame += '\n' + (body || '') + '\0';
        return frame;
    }

    function processIncomingStompData(rawChunk) {
        incomingBuffer += rawChunk;
        while (true) {
            // Strip any leading heartbeats (single or repeated newlines/carriage returns)
            while (incomingBuffer.startsWith('\r') || incomingBuffer.startsWith('\n')) {
                incomingBuffer = incomingBuffer.slice(1);
            }
            if (!incomingBuffer) break;

            const nullIdx = incomingBuffer.indexOf('\0');
            if (nullIdx === -1) {
                // Fragmented frame: wait for more chunks
                break;
            }

            const rawFrame = incomingBuffer.slice(0, nullIdx);
            incomingBuffer = incomingBuffer.slice(nullIdx + 1);

            if (rawFrame.trim()) {
                parseAndDispatchFrame(rawFrame);
            }
        }
    }

    function parseAndDispatchFrame(rawFrame) {         const nl = rawFrame.indexOf('\n');
        if (nl === -1) return;
        const command = rawFrame.slice(0, nl).trim();
        const rest = rawFrame.slice(nl + 1);
        const idx = rest.indexOf('\n\n');
        const headerBlock = idx === -1 ? rest : rest.slice(0, idx);
        const body = idx === -1 ? '' : rest.slice(idx + 2);

        const headers = {};
        for (const line of headerBlock.split('\n')) {
            const colon = line.indexOf(':');
            if (colon !== -1) {
                const k = line.slice(0, colon).trim();
                const v = line.slice(colon + 1).trim();
                if (!(k in headers)) {
                    headers[k] = v;
                }
            }
        }

        if (command === 'CONNECTED') {
            stompConnected = true;
            const isSocketOpen = stompSocket && (stompSocket.readyState === 1 || (typeof WebSocket !== 'undefined' && stompSocket.readyState === WebSocket.OPEN));
            if (isSocketOpen) {
                const subFrame = buildStompFrame('SUBSCRIBE', {
                    id: 'sub-messages',
                    destination: '/user/queue/messages'
                });
                stompSocket.send(subFrame);
            }
            // Refresh authoritative conversation summaries on reconnects only —
            // the first connect rides on the list init() already loaded.
            if (stompHasConnectedOnce) {
                loadConversations(0, false);
            }
            stompHasConnectedOnce = true;
            // Reconnect / catchup for current active conversation
            if (state.activeConversationId) {
                loadMessages(state.activeConversationId, 0, false, messagesRequestId);
            }
        } else if (command === 'MESSAGE') {
            handleIncomingStompMessage(body);
        }
    }

    const recentMessageIds = new Set();
    const recentMessageIdQueue = [];
    const MAX_RECENT_MESSAGES = 200;

    function recordRecentMessageId(id) {
        if (!id || recentMessageIds.has(id)) return false;
        recentMessageIds.add(id);
        recentMessageIdQueue.push(id);
        if (recentMessageIdQueue.length > MAX_RECENT_MESSAGES) {
            const oldest = recentMessageIdQueue.shift();
            recentMessageIds.delete(oldest);
        }
        return true;
    }

    function handleIncomingStompMessage(rawBody) {         if (!rawBody) return;
        try {
            const msg = JSON.parse(rawBody);
            if (!msg || !msg.id) return;

            // Bounded deduplication: ignore repeat delivery of already-reflected message
            if (!recordRecentMessageId(msg.id)) {
                return;
            }

            // Deliver immediately to active conversation if matches
            if (state.activeConversationId && msg.conversationId === state.activeConversationId) {
                if (!state.messages.some(m => m.id === msg.id)) {
                    state.messages.push(msg);
                    state.messages.sort(compareMessages);
                    renderAllMessages();
                    if (elHistory) {
                        elHistory.scrollTop = elHistory.scrollHeight;
                    }
                    if (elMessagesEmpty) elMessagesEmpty.classList.add('d-none');
                }

                // Post read receipt to backend for visible active conversation
                fetch(`/api/conversations/${state.activeConversationId}/read`, {
                    method: 'POST',
                    headers: csrfHeaders(),
                    credentials: 'include'
                }).catch(err => console.debug('Failed to send read receipt for incoming active message:', err));
            }

            // Update conversation snippet in left pane
            const conv = state.conversations.find(c => c.id === msg.conversationId);
            if (conv) {
                conv.lastMutationSeq = ++state.mutationSeq;
                if (state.activeConversationId && conv.id === state.activeConversationId) {
                    conv.unreadCount = 0;
                    conv.summaryLastMessage = conv.lastMessage;
                } else {
                    // Inactive conversation: compare against summaryLastMessage
                    const isNewerThanSummary = !conv.summaryLastMessage || compareMessages(msg, conv.summaryLastMessage) > 0;
                    if (isNewerThanSummary) {
                        conv.unreadCount = (conv.unreadCount || 0) + 1;
                    }
                }

                // Update preview only if strictly newer than current preview
                if (!conv.lastMessage || compareMessages(msg, conv.lastMessage) > 0) {
                    conv.lastMessage = msg;
                    conv.updatedAt = msg.createdAt;
                }

                if (state.activeConversationId && conv.id === state.activeConversationId) {
                    conv.unreadCount = 0;
                }
                renderConversationsList();
            } else {
                loadConversations(0, false);
            }
        } catch (e) {
            console.error('[STOMP] Failed to parse message body:', e);
        }
    }

    function initStompClient() {
        const WS = (typeof WebSocket !== 'undefined') ? WebSocket : (typeof window !== 'undefined' && window.WebSocket ? window.WebSocket : null);
        if (!WS) return;

        cleanupStomp(false);

        const socketId = ++currentSocketId;
        const protocol = (typeof window !== 'undefined' && window.location && window.location.protocol === 'https:') ? 'wss:' : 'ws:';
        const host = (typeof window !== 'undefined' && window.location && window.location.host) ? window.location.host : 'localhost';
        const wsUrl = `${protocol}//${host}/ws`;

        try {
            const socket = new WS(wsUrl);
            stompSocket = socket;

            socket.onopen = () => {
                if (socketId !== currentSocketId) return;
                const connectFrame = buildStompFrame('CONNECT', {
                    'accept-version': '1.1,1.2',
                    'heart-beat': '0,0'
                });
                socket.send(connectFrame);
            };

            socket.onmessage = (event) => {
                if (socketId !== currentSocketId) return;
                const raw = typeof event.data === 'string' ? event.data : '';
                processIncomingStompData(raw);
            };

            socket.onerror = () => {
                if (socketId !== currentSocketId) return;
                stompConnected = false;
            };

            socket.onclose = () => {
                if (socketId !== currentSocketId) return;
                stompConnected = false;
                stompSocket = null;
                scheduleStompReconnect();
            };
        } catch (e) {
            console.warn('[STOMP] Socket creation failed:', e);
            scheduleStompReconnect();
        }
    }

    function scheduleStompReconnect() {
        if (stompReconnectTimer) return;
        const setTimer = (typeof window !== 'undefined' && window.setTimeout) ? window.setTimeout.bind(window) : (typeof setTimeout !== 'undefined' ? setTimeout : null);
        if (!setTimer) return;
        stompReconnectTimer = setTimer(() => {
            stompReconnectTimer = null;
            initStompClient();
        }, 5000);
    }

    function cleanupStomp(fullTeardown) {
        if (stompReconnectTimer) {
            const clearTimer = (typeof window !== 'undefined' && window.clearTimeout) ? window.clearTimeout.bind(window) : (typeof clearTimeout !== 'undefined' ? clearTimeout : null);
            if (clearTimer) clearTimer(stompReconnectTimer);
            stompReconnectTimer = null;
        }
        if (stompSocket) {
            const sock = stompSocket;
            stompSocket = null;
            sock.onopen = null;
            sock.onmessage = null;
            sock.onerror = null;
            sock.onclose = null;
            try { sock.close(); } catch (_) {}
        }
        stompConnected = false;
        incomingBuffer = '';
        if (fullTeardown) {
            currentSocketId++;
        }
    }

    function init() {
        if (!initDomElements()) return;
        setupEventListeners();
        loadConversations(0, false);
        initStompClient();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
