/**
 * AloUTE — Notifications Feature Script (Phase A REST Implementation)
 *
 * REST Endpoints:
 * - GET    /api/notifications?filter={filter}&page={page}&size={size}
 *   Filters: ALL, LIKE, REPLY, MENTION, FOLLOW (exact backend-supported values)
 * - GET    /api/notifications/unread-count
 * - PATCH  /api/notifications/{id}/read
 * - PATCH  /api/notifications/read-all
 * - DELETE /api/notifications/{id}
 *
 * Security:
 * - credentials: 'include', CSRF token on PATCH / DELETE mutations, no JWT reading in JS.
 */
(function () {
    'use strict';

    if (typeof window !== 'undefined' && window.__alouteNotificationInitialized) return;
    if (typeof window !== 'undefined') window.__alouteNotificationInitialized = true;

    // -------------------------------------------------------------------------
    // Utilities & Security
    // -------------------------------------------------------------------------

    function isValidUnreadCount(val) {
        return typeof val === 'number' && Number.isInteger(val) && Number.isSafeInteger(val) && val >= 0;
    }

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
        if (code === 'NOTIFICATION_NOT_FOUND') return 'Không tìm thấy thông báo.';
        if (code === 'ACCESS_DENIED') return 'Bạn không có quyền thực hiện thao tác này.';
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
        currentFilter: 'ALL',
        currentPage: 0,
        hasNext: false,
        notifications: [],
        unreadCount: 0,
        isLoading: false
    };

    let notifRequestId = 0;
    let notifAbortController = null;
    let unreadCountVersion = 0;
    let unreadCountAbortController = null;

    // -------------------------------------------------------------------------
    // DOM Elements
    // -------------------------------------------------------------------------

    let elList, elLoading, elError, elErrorMsg, elEmpty;
    let elLoadMoreWrap, btnLoadMore, btnRetry;
    let badgeUnread, btnMarkAllRead, btnRefresh, tabsContainer;
    let feedbackArea, feedbackText;

    function initDomElements() {
        const root = document.getElementById('notifications-content');
        if (!root) return false;

        elList = document.getElementById('notifications-list');
        elLoading = document.getElementById('notifications-loading');
        elError = document.getElementById('notifications-error');
        elErrorMsg = document.getElementById('notifications-error-msg');
        elEmpty = document.getElementById('notifications-empty');
        elLoadMoreWrap = document.getElementById('notifications-load-more-wrap');
        btnLoadMore = document.getElementById('btn-load-more-notifications');
        btnRetry = document.getElementById('btn-retry-notifications');

        badgeUnread = document.getElementById('unread-count-badge');
        btnMarkAllRead = document.getElementById('btn-mark-all-read');
        btnRefresh = document.getElementById('btn-refresh-notifications');
        tabsContainer = document.getElementById('notificationFilterTabs');

        feedbackArea = document.getElementById('notification-feedback-area');
        feedbackText = document.getElementById('notification-feedback-text');

        return true;
    }

    function showFeedback(msg, type) {
        if (!feedbackArea || !feedbackText) return;
        feedbackText.className = 'alert alert-' + (type || 'info') + ' alert-dismissible py-2 mb-0 small';
        feedbackText.textContent = msg;
        feedbackArea.classList.remove('d-none');
        clearTimeout(window.__alouteNotifFeedbackTimer);
        window.__alouteNotifFeedbackTimer = setTimeout(() => {
            if (feedbackArea) feedbackArea.classList.add('d-none');
        }, 3500);
    }

    // -------------------------------------------------------------------------
    // Type-to-Icon & Action Phrasing Mapping
    // -------------------------------------------------------------------------

    function getNotificationTypeMeta(type) {
        const t = (type || '').toUpperCase();
        if (t.includes('LIKE')) {
            return {
                iconClass: 'bi-heart-fill text-danger',
                actionText: t.includes('REPLY') ? 'đã thích bình luận của bạn' : 'đã thích bài viết của bạn'
            };
        }
        if (t.includes('REPLY') || t.includes('COMMENT')) {
            return {
                iconClass: 'bi-chat-left-dots-fill text-primary',
                actionText: t.includes('REPLY_REPLY') ? 'đã trả lời bình luận của bạn' : 'đã bình luận về bài viết của bạn'
            };
        }
        if (t.includes('QUOTE')) {
            return {
                iconClass: 'bi-quote text-primary',
                actionText: 'đã trích dẫn bài viết của bạn'
            };
        }
        if (t.includes('REPOST')) {
            return {
                iconClass: 'bi-repeat text-success',
                actionText: 'đã chia sẻ lại bài viết của bạn'
            };
        }
        if (t.includes('MENTION')) {
            return {
                iconClass: 'bi-at text-info',
                actionText: 'đã nhắc đến bạn trong một thảo luận'
            };
        }
        if (t.includes('FOLLOW')) {
            return {
                iconClass: 'bi-person-plus-fill text-success',
                actionText: 'đã bắt đầu theo dõi bạn'
            };
        }
        if (t.includes('FRIEND_ACCEPT')) {
            return {
                iconClass: 'bi-person-check-fill text-success',
                actionText: 'đã chấp nhận kết nối bạn bè'
            };
        }
        if (t.includes('FRIEND')) {
            return {
                iconClass: 'bi-person-plus text-primary',
                actionText: 'đã gửi yêu cầu kết nối'
            };
        }
        return {
            iconClass: 'bi-bell-fill text-secondary',
            actionText: 'đã tương tác với bạn'
        };
    }

    // -------------------------------------------------------------------------
    // Notification Item Rendering
    // -------------------------------------------------------------------------

    function createNotificationElement(item) {
        const div = document.createElement('article');
        div.className = 'aloute-notification-item' + (!item.isRead ? ' unread' : '');
        div.setAttribute('data-id', item.id);
        div.setAttribute('role', 'article');
        div.setAttribute('aria-label', (!item.isRead ? 'Chưa đọc: ' : '') + 'Thông báo từ ' + (item.actor ? item.actor.displayName : 'Người dùng'));

        const meta = getNotificationTypeMeta(item.type);
        const avatarUrl = item.actor && item.actor.avatarUrl ? item.actor.avatarUrl : '/images/avatar-fallback.svg';
        const actorName = item.actor ? (item.actor.displayName || item.actor.username || 'Sinh viên') : 'Sinh viên';
        const actorUsername = item.actor ? item.actor.username : '';
        const timeStr = formatTime(item.createdAt);
        const preview = item.contentPreview ? `<p class="text-secondary small mt-1 mb-0 fst-italic text-break">"${escapeHtml(item.contentPreview)}"</p>` : '';

        const unreadDot = !item.isRead ? '<span class="aloute-notification-item-dot" title="Chưa đọc" aria-hidden="true"></span>' : '';
        const markReadBtn = !item.isRead ? `
            <button type="button" class="btn btn-sm btn-link p-0 text-decoration-none text-muted btn-mark-read" title="Đánh dấu đã đọc" aria-label="Đánh dấu đã đọc">
                <i class="bi bi-check-circle" aria-hidden="true"></i>
            </button>
        ` : '';

        div.innerHTML = `
            ${unreadDot}
            <div class="position-relative flex-shrink-0">
                <img src="${escapeHtml(avatarUrl)}" alt="Avatar của ${escapeHtml(actorName)}" class="aloute-notification-actor-avatar" width="44" height="44">
                <span class="aloute-notification-icon-badge" aria-hidden="true">
                    <i class="bi ${meta.iconClass}"></i>
                </span>
            </div>
            <div class="flex-grow-1 aloute-min-w-0">
                <div class="d-flex align-items-center justify-content-between gap-2 flex-wrap">
                    <div class="aloute-min-w-0">
                        <span class="fw-semibold text-dark">${escapeHtml(actorName)}</span>
                        ${actorUsername ? `<span class="small text-muted me-1">@${escapeHtml(actorUsername)}</span>` : ''}
                        <span class="text-secondary small">${escapeHtml(meta.actionText)}</span>
                    </div>
                    <div class="d-flex align-items-center gap-2 flex-shrink-0">
                        <span class="small text-muted" style="font-size: 0.75rem;">${timeStr}</span>
                        ${markReadBtn}
                        <button type="button" class="btn btn-sm btn-link p-0 text-decoration-none text-danger btn-delete-notif" title="Xóa thông báo" aria-label="Xóa thông báo">
                            <i class="bi bi-trash" aria-hidden="true"></i>
                        </button>
                    </div>
                </div>
                ${preview}
            </div>
        `;

        // Wire Item Actions
        const btnRead = div.querySelector('.btn-mark-read');
        if (btnRead) {
            btnRead.addEventListener('click', () => handleMarkAsRead(item.id, div));
        }

        const btnDelete = div.querySelector('.btn-delete-notif');
        if (btnDelete) {
            btnDelete.addEventListener('click', () => handleDeleteNotification(item.id, div));
        }

        return div;
    }

    function renderNotificationsList() {
        if (!elList) return;
        elList.innerHTML = '';
        state.notifications.forEach(item => {
            elList.appendChild(createNotificationElement(item));
        });
    }

    // -------------------------------------------------------------------------
    // Helper: Reset & Trigger Filter Change
    // -------------------------------------------------------------------------

    function triggerFilterChange(filter) {
        if (notifAbortController) {
            notifAbortController.abort();
            notifAbortController = null;
        }
        const reqId = ++notifRequestId;
        notifAbortController = new AbortController();

        state.currentPage = 0;
        state.hasNext = false;
        state.notifications = [];
        state.isLoading = false;

        if (elList) elList.innerHTML = '';
        if (elLoading) elLoading.classList.remove('d-none');
        if (elError) elError.classList.add('d-none');
        if (elEmpty) elEmpty.classList.add('d-none');
        if (elLoadMoreWrap) elLoadMoreWrap.classList.add('d-none');

        loadNotifications(filter, 0, false, reqId);
    }

    // -------------------------------------------------------------------------
    // API: Fetch Notifications
    // -------------------------------------------------------------------------

    async function loadNotifications(filter, page, append, reqId) {
        const f = filter || 'ALL';
        if (f !== state.currentFilter) return;

        const currentReqId = reqId || ++notifRequestId;
        if (append) {
            if (state.isLoading) return;
            if (currentReqId !== notifRequestId) return;
        } else {
            if (!notifAbortController || notifAbortController.signal.aborted) {
                notifAbortController = new AbortController();
            }
        }

        state.isLoading = true;

        if (!append) {
            if (elLoading) elLoading.classList.remove('d-none');
            if (elError) elError.classList.add('d-none');
            if (elEmpty) elEmpty.classList.add('d-none');
            if (elList) elList.innerHTML = '';
            if (elLoadMoreWrap) elLoadMoreWrap.classList.add('d-none');
        }

        const signal = notifAbortController ? notifAbortController.signal : undefined;

        try {
            const response = await fetch(`/api/notifications?filter=${encodeURIComponent(f)}&page=${page}&size=20`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Accept': 'application/json' },
                signal: signal
            });

            const body = await readJson(response);

            // Stale response guard: ignore if filter or generation changed
            if (currentReqId !== notifRequestId || f !== state.currentFilter) {
                return;
            }

            if (!response.ok || !body || !body.success) {
                throw new Error(translateError(body && body.error, 'Không thể tải danh sách thông báo.'));
            }

            const data = body.data || [];
            const meta = body.meta || {};

            state.currentPage = meta.page != null ? meta.page : page;
            state.hasNext = Boolean(meta.hasNext);

            if (append) {
                const existingIds = new Set(state.notifications.map(n => n.id));
                const uniqueData = data.filter(n => !existingIds.has(n.id));
                state.notifications = state.notifications.concat(uniqueData);
                uniqueData.forEach(item => {
                    if (elList) elList.appendChild(createNotificationElement(item));
                });
            } else {
                const seen = new Set();
                state.notifications = data.filter(n => {
                    if (n && n.id != null) {
                        if (seen.has(n.id)) return false;
                        seen.add(n.id);
                    }
                    return true;
                });
                renderNotificationsList();
            }

            if (elLoading) elLoading.classList.add('d-none');

            if (state.notifications.length === 0) {
                if (elEmpty) elEmpty.classList.remove('d-none');
            } else {
                if (elEmpty) elEmpty.classList.add('d-none');
            }

            if (elLoadMoreWrap) {
                if (state.hasNext) {
                    elLoadMoreWrap.classList.remove('d-none');
                } else {
                    elLoadMoreWrap.classList.add('d-none');
                }
            }
        } catch (err) {
            if (err.name === 'AbortError') {
                return; // Aborted by newer filter/refresh; ignore silently
            }
            if (currentReqId !== notifRequestId || f !== state.currentFilter) {
                return; // Stale error; ignore
            }
            if (elLoading) elLoading.classList.add('d-none');
            if (elError) {
                elError.classList.remove('d-none');
                if (elErrorMsg) elErrorMsg.textContent = err.message || 'Không thể kết nối đến máy chủ.';
            }
        } finally {
            if (currentReqId === notifRequestId && f === state.currentFilter) {
                state.isLoading = false;
            }
        }
    }

    // -------------------------------------------------------------------------
    // API: Fetch Unread Count
    // -------------------------------------------------------------------------

    async function loadUnreadCount() {
        if (unreadCountAbortController) {
            unreadCountAbortController.abort();
            unreadCountAbortController = null;
        }
        unreadCountAbortController = new AbortController();
        const currentVersion = ++unreadCountVersion;
        const signal = unreadCountAbortController.signal;

        try {
            const response = await fetch('/api/notifications/unread-count', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Accept': 'application/json' },
                signal: signal
            });
            const body = await readJson(response);
            if (currentVersion !== unreadCountVersion) {
                return; // Stale count response, discard
            }
            const hasValidMeta = !body || body.meta === undefined || body.meta === null;
            if (response.status === 200 && body && body.success === true && hasValidMeta && body.data && isValidUnreadCount(body.data.unreadCount)) {
                state.unreadCount = body.data.unreadCount;
                updateUnreadBadge();
            }
        } catch (err) {
            if (err.name === 'AbortError') return;
            // Silent error on unread count fetch to not disrupt notification feed
        }
    }

    function updateUnreadBadge() {
        const raw = state.unreadCount;
        if (!isValidUnreadCount(raw)) {
            // Malformed count: do not render fake 0, preserve DOM state
            return;
        }
        const num = raw;
        const text = num > 99 ? '99+' : String(num);
        const isHidden = num <= 0;
        const parentLabel = num <= 0 ? 'Thông báo' : (num > 99 ? 'Thông báo (Hơn 99 thông báo chưa đọc)' : 'Thông báo (' + num + ' thông báo chưa đọc)');
        const badgeLabel = num <= 0 ? 'Không có thông báo mới' : (num > 99 ? 'Hơn 99 thông báo chưa đọc' : (num + ' thông báo chưa đọc'));

        if (badgeUnread) {
            badgeUnread.textContent = isHidden ? '0' : text;
            badgeUnread.setAttribute('aria-label', badgeLabel);
            if (isHidden) badgeUnread.classList.add('d-none');
            else badgeUnread.classList.remove('d-none');
        }

        const navBadges = document.querySelectorAll('#navbar-notification-badge, .navbar-notification-badge');
        navBadges.forEach(function (badge) {
            badge.textContent = isHidden ? '0' : text;
            badge.setAttribute('aria-label', badgeLabel);
            if (isHidden) badge.classList.add('d-none');
            else badge.classList.remove('d-none');
            const parentLink = badge.closest('a');
            if (parentLink) parentLink.setAttribute('aria-label', parentLabel);
        });

        const sideBadges = document.querySelectorAll('.sidebar-notification-badge, [data-badge="notification"], #sidebar-notification-badge');
        sideBadges.forEach(function (badge) {
            badge.textContent = isHidden ? '0' : text;
            badge.setAttribute('aria-label', badgeLabel);
            if (isHidden) badge.classList.add('d-none');
            else badge.classList.remove('d-none');
            const parentLink = badge.closest('a');
            if (parentLink) parentLink.setAttribute('aria-label', parentLabel);
        });

        try {
            window.dispatchEvent(new CustomEvent('aloute:unread-count-updated', {
                detail: { unreadCount: num, version: unreadCountVersion }
            }));
        } catch (_) {}
    }

    // -------------------------------------------------------------------------
    // API: Mark One As Read (PATCH /api/notifications/{id}/read)
    // -------------------------------------------------------------------------

    async function handleMarkAsRead(id, element) {
        try {
            const response = await fetch(`/api/notifications/${id}/read`, {
                method: 'PATCH',
                credentials: 'include',
                headers: csrfHeaders()
            });
            const body = await readJson(response);
            if (response.ok && body && body.success) {
                element.classList.remove('unread');
                const dot = element.querySelector('.aloute-notification-item-dot');
                if (dot) dot.remove();
                const btnRead = element.querySelector('.btn-mark-read');
                if (btnRead) btnRead.remove();

                const item = state.notifications.find(n => n.id === id);
                if (item) item.isRead = true;

                if (state.unreadCount > 0) {
                    unreadCountVersion++;
                    state.unreadCount--;
                    updateUnreadBadge();
                }

                // Authoritative unread-count refresh after mutation
                loadUnreadCount();
            } else {
                showFeedback(translateError(body && body.error, 'Không thể cập nhật trạng thái thông báo.'), 'danger');
            }
        } catch (err) {
            if (err && err.name === 'AbortError') return;
            showFeedback('Lỗi kết nối khi đánh dấu đã đọc.', 'danger');
        }
    }

    // -------------------------------------------------------------------------
    // API: Mark All As Read (PATCH /api/notifications/read-all)
    // -------------------------------------------------------------------------

    async function handleMarkAllAsRead() {
        if (btnMarkAllRead) {
            btnMarkAllRead.disabled = true;
        }
        try {
            const response = await fetch('/api/notifications/read-all', {
                method: 'PATCH',
                credentials: 'include',
                headers: csrfHeaders()
            });
            const body = await readJson(response);
            if (response.ok && body && body.success) {
                state.notifications.forEach(n => { n.isRead = true; });
                if (elList) {
                    elList.querySelectorAll('.aloute-notification-item.unread').forEach(el => {
                        el.classList.remove('unread');
                        const dot = el.querySelector('.aloute-notification-item-dot');
                        if (dot) dot.remove();
                        const btnRead = el.querySelector('.btn-mark-read');
                        if (btnRead) btnRead.remove();
                    });
                }
                unreadCountVersion++;
                state.unreadCount = 0;
                updateUnreadBadge();
                showFeedback('Đã đánh dấu tất cả thông báo là đã đọc.', 'success');

                // Authoritative unread-count refresh after mutation
                loadUnreadCount();
            } else {
                showFeedback(translateError(body && body.error, 'Không thể đánh dấu tất cả đã đọc.'), 'danger');
            }
        } catch (err) {
            if (err && err.name === 'AbortError') return;
            showFeedback('Lỗi kết nối máy chủ.', 'danger');
        } finally {
            if (btnMarkAllRead) {
                btnMarkAllRead.disabled = false;
            }
        }
    }

    // -------------------------------------------------------------------------
    // API: Delete Notification (DELETE /api/notifications/{id})
    // -------------------------------------------------------------------------

    async function handleDeleteNotification(id, element) {
        try {
            const response = await fetch(`/api/notifications/${id}`, {
                method: 'DELETE',
                credentials: 'include',
                headers: csrfHeaders()
            });
            const body = await readJson(response);
            if (response.ok && body && body.success) {
                const wasUnread = element.classList.contains('unread');
                element.style.transition = 'opacity 0.2s ease, transform 0.2s ease';
                element.style.opacity = '0';
                element.style.transform = 'translateX(20px)';
                setTimeout(() => {
                    element.remove();
                    state.notifications = state.notifications.filter(n => n.id !== id);
                    if (state.notifications.length === 0 && elEmpty) {
                        elEmpty.classList.remove('d-none');
                    }
                }, 200);

                if (wasUnread && state.unreadCount > 0) {
                    unreadCountVersion++;
                    state.unreadCount--;
                    updateUnreadBadge();
                }
                showFeedback('Đã xóa thông báo.', 'info');

                // Authoritative unread-count refresh after mutation
                loadUnreadCount();
            } else {
                showFeedback(translateError(body && body.error, 'Không thể xóa thông báo.'), 'danger');
            }
        } catch (err) {
            if (err && err.name === 'AbortError') return;
            showFeedback('Lỗi kết nối khi xóa thông báo.', 'danger');
        }
    }

    // -------------------------------------------------------------------------
    // Filter Tabs & Event Handling
    // -------------------------------------------------------------------------

    function setupEventListeners() {
        if (tabsContainer) {
            tabsContainer.addEventListener('click', function (e) {
                const tab = e.target.closest('[data-filter]');
                if (!tab) return;
                const newFilter = tab.getAttribute('data-filter');
                if (newFilter === state.currentFilter) return;

                tabsContainer.querySelectorAll('.nav-link').forEach(t => {
                    t.classList.remove('active');
                    t.setAttribute('aria-selected', 'false');
                });
                tab.classList.add('active');
                tab.setAttribute('aria-selected', 'true');

                state.currentFilter = newFilter;
                triggerFilterChange(newFilter);
            });
        }

        if (btnRetry) {
            btnRetry.addEventListener('click', () => triggerFilterChange(state.currentFilter));
        }

        if (btnRefresh) {
            btnRefresh.addEventListener('click', () => {
                loadUnreadCount();
                triggerFilterChange(state.currentFilter);
            });
        }

        if (btnMarkAllRead) {
            btnMarkAllRead.addEventListener('click', handleMarkAllAsRead);
        }

        if (btnLoadMore) {
            btnLoadMore.addEventListener('click', () => {
                if (state.hasNext && !state.isLoading) {
                    loadNotifications(state.currentFilter, state.currentPage + 1, true, notifRequestId);
                }
            });
        }
    }

    function init() {
        if (!initDomElements()) return;
        setupEventListeners();
        loadUnreadCount();
        triggerFilterChange('ALL');
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
