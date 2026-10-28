/**
 * AloUTE — Post Interactions (Like, Bookmark, Repost)
 *
 * Dedicated owner for post interaction actions:
 * - Like: PUT / DELETE /api/posts/{id}/likes/me
 * - Bookmark: PUT / DELETE /api/posts/{id}/bookmarks/me
 * - Repost: PUT / DELETE /api/posts/{id}/reposts/me
 *
 * Conforms to frontend_rule.md section 14 (shared HTTP transport via window.alouteHttp)
 * and ALOUTE_SYSTEM_ARCHITECTURE.md (ApiResponse envelope validation).
 */
(function () {
    'use strict';

    // Idempotent initialization guard
    if (typeof window !== 'undefined' && window.__aloutePostInteractionsInitialized) return;
    if (typeof window !== 'undefined') window.__aloutePostInteractionsInitialized = true;

    // Concurrency lock: set of active "postId:action" strings
    const inFlight = new Set();

    // Accessible floating notice / announcer (safe textContent, role="status", aria-live="polite")
    function announce(message, type) {
        if (!message || typeof document === 'undefined') return;
        let box = document.getElementById('aloute-toast-notice');
        if (!box) {
            box = document.createElement('div');
            box.id = 'aloute-toast-notice';
            box.className = 'position-fixed bottom-0 end-0 m-3 alert shadow';
            box.style.zIndex = '2050';
            box.setAttribute('role', 'status');
            box.setAttribute('aria-live', 'polite');
            if (document.body) {
                document.body.appendChild(box);
            }
        }
        box.className = 'position-fixed bottom-0 end-0 m-3 alert alert-' + (type || 'info') + ' shadow';
        box.textContent = message;
        box.hidden = false;
        if (typeof window !== 'undefined') {
            clearTimeout(window.__aloutePostInteractionNoticeTimer);
            window.__aloutePostInteractionNoticeTimer = setTimeout(function () {
                box.hidden = true;
            }, 3500);
        }
    }

    // Map HTTP error status codes to clean user-friendly Vietnamese messages
    function translateErrorStatus(status, body) {
        if (status === 401) {
            return 'Vui lòng đăng nhập để thực hiện thao tác này.';
        }
        if (status === 403) {
            return 'Bạn không có quyền thực hiện thao tác này.';
        }
        if (status === 404) {
            return 'Bài viết không tồn tại hoặc đã bị xóa.';
        }
        if (body && body.error && typeof body.error.message === 'string') {
            const msg = body.error.message;
            if (msg === 'Access is denied' || msg === 'Access Denied') {
                return 'Bạn không có quyền thực hiện thao tác này.';
            }
            if (msg === 'Post not found') {
                return 'Bài viết không tồn tại hoặc đã bị xóa.';
            }
        }
        return 'Không thể thực hiện thao tác lúc này. Vui lòng thử lại sau.';
    }

    // Query all matching interaction buttons in document for the same postId and action
    function getMatchingButtons(action, postId) {
        if (typeof document === 'undefined') return [];
        return Array.from(document.querySelectorAll('[data-action="' + action + '"][data-post-id="' + postId + '"]'));
    }

    // Apply interactive state to a list of buttons
    function applyState(buttons, action, active, countVal) {
        buttons.forEach(btn => {
            btn.classList.toggle('is-active', active);
            btn.setAttribute('aria-pressed', active ? 'true' : 'false');

            const icon = btn.querySelector('i');
            if (action === 'like') {
                if (icon) {
                    icon.classList.remove(active ? 'bi-heart' : 'bi-heart-fill');
                    icon.classList.add(active ? 'bi-heart-fill' : 'bi-heart');
                }
            } else if (action === 'bookmark') {
                btn.setAttribute('aria-label', active ? 'Bỏ lưu bài viết' : 'Lưu bài viết');
                if (icon) {
                    icon.classList.remove(active ? 'bi-bookmark' : 'bi-bookmark-fill');
                    icon.classList.add(active ? 'bi-bookmark-fill' : 'bi-bookmark');
                }
            } else if (action === 'repost') {
                if (icon) {
                    if (!icon.classList.contains('bi-arrow-repeat')) {
                        icon.className = 'bi bi-arrow-repeat';
                    }
                }
            }

            // Only update counter if countVal is provided as finite number (like, repost)
            if (typeof countVal === 'number' && Number.isFinite(countVal)) {
                const countEl = btn.querySelector('.interaction-count, .count, [data-role="count"]');
                if (countEl) {
                    countEl.textContent = Math.max(0, countVal).toString();
                }
            }
        });
    }

    // Set or unset busy/disabled state on matching buttons
    function setBusy(buttons, busy) {
        buttons.forEach(btn => {
            if (busy) {
                btn.setAttribute('aria-busy', 'true');
                btn.disabled = true;
            } else {
                btn.removeAttribute('aria-busy');
                btn.disabled = false;
            }
        });
    }

    // Main action handler
    async function handleInteraction(targetBtn) {
        const action = targetBtn.getAttribute('data-action');
        const postId = targetBtn.getAttribute('data-post-id');
        if (!postId || !action) return;

        // Verify valid action
        if (action !== 'like' && action !== 'bookmark' && action !== 'repost') return;

        const lockKey = postId + ':' + action;
        if (inFlight.has(lockKey)) {
            // Drop duplicate click while request is in flight
            return;
        }

        // Determine current state from clicked button
        const wasActive = targetBtn.classList.contains('is-active') || targetBtn.getAttribute('aria-pressed') === 'true';
        const targetActive = !wasActive;

        let buttons = getMatchingButtons(action, postId);
        if (buttons.length === 0) return;

        // Read current counter if present (like, repost)
        let currentCount = null;
        if (action === 'like' || action === 'repost') {
            for (const b of buttons) {
                const cntEl = b.querySelector('.interaction-count, .count, [data-role="count"]');
                if (cntEl) {
                    const parsed = parseInt(cntEl.textContent.trim(), 10);
                    if (!isNaN(parsed) && Number.isFinite(parsed)) {
                        currentCount = Math.max(0, parsed);
                        break;
                    }
                }
            }
            if (currentCount === null) currentCount = 0;
        }

        // Compute optimistic counter
        let optimisticCount = null;
        if (currentCount !== null) {
            optimisticCount = targetActive ? currentCount + 1 : Math.max(0, currentCount - 1);
        }

        // Acquire lock and apply busy state across all matching buttons
        inFlight.add(lockKey);
        setBusy(buttons, true);

        // Apply optimistic update immediately
        applyState(buttons, action, targetActive, optimisticCount);

        // Verify shared transport client is present; if missing, rollback immediately with safe notice
        if (typeof window === 'undefined' || !window.alouteHttp || typeof window.alouteHttp.request !== 'function' || typeof window.alouteHttp.readJson !== 'function') {
            inFlight.delete(lockKey);
            buttons = getMatchingButtons(action, postId);
            applyState(buttons, action, wasActive, currentCount);
            setBusy(buttons, false);
            announce('Không thể thực hiện thao tác lúc này. Vui lòng thử lại sau.', 'danger');
            return;
        }

        const http = window.alouteHttp;
        const endpointSegments = {
            'like': 'likes',
            'bookmark': 'bookmarks',
            'repost': 'reposts'
        };
        const segment = endpointSegments[action];
        const url = '/api/posts/' + encodeURIComponent(postId) + '/' + segment + '/me';
        const method = targetActive ? 'PUT' : 'DELETE';

        try {
            const response = await http.request(url, { method: method });

            if (response.status === 204) {
                // HTTP 204 No Content is valid ONLY for expected DELETE mutations; never turn a PUT into false
                if (method !== 'DELETE') {
                    throw new Error('UNEXPECTED_204_FOR_PUT');
                }

                const finalActive = false;
                let finalCount = null;
                if (action === 'like' || action === 'repost') {
                    if (currentCount !== null) {
                        finalCount = wasActive ? Math.max(0, currentCount - 1) : currentCount;
                    }
                }

                // Re-query matching buttons immediately before applying final state
                buttons = getMatchingButtons(action, postId);
                applyState(buttons, action, finalActive, finalCount);
            } else if (response.ok && response.status === 200) {
                const body = await http.readJson(response);

                // Reject malformed, empty or noncanonical envelopes
                if (!body || typeof body !== 'object' || body.success !== true || !body.data || typeof body.data !== 'object') {
                    throw new Error('MALFORMED_OR_NONCANONICAL_ENVELOPE');
                }

                const payload = body.data;

                // Both canonical DTO shapes require post identity: reject missing, null, or mismatched identity
                const rawRespId = payload.id !== undefined ? payload.id : payload.postId;
                if (rawRespId === null || rawRespId === undefined || String(rawRespId).trim() !== String(postId).trim()) {
                    throw new Error('MISSING_OR_MISMATCHED_POST_IDENTITY');
                }

                // Per-action boolean validation
                let finalActive = null;
                if (payload.viewerState && typeof payload.viewerState === 'object') {
                    if (action === 'like' && typeof payload.viewerState.liked === 'boolean') {
                        finalActive = payload.viewerState.liked;
                    } else if (action === 'bookmark' && typeof payload.viewerState.bookmarked === 'boolean') {
                        finalActive = payload.viewerState.bookmarked;
                    } else if (action === 'repost' && typeof payload.viewerState.reposted === 'boolean') {
                        finalActive = payload.viewerState.reposted;
                    }
                } else if (typeof payload.active === 'boolean') {
                    finalActive = payload.active;
                }

                if (typeof finalActive !== 'boolean') {
                    throw new Error('MISSING_OR_INVALID_ACTION_STATE');
                }

                // Reconcile relevant counter: authoritative count if provided,
                // otherwise calculate from prior count/state vs final state
                let authoritativeCount = null;
                if (action === 'like' && typeof payload.likeCount === 'number' && Number.isFinite(payload.likeCount)) {
                    authoritativeCount = Math.max(0, payload.likeCount);
                } else if (action === 'repost' && typeof payload.repostCount === 'number' && Number.isFinite(payload.repostCount)) {
                    authoritativeCount = Math.max(0, payload.repostCount);
                }

                let finalCount = null;
                if (action === 'like' || action === 'repost') {
                    if (authoritativeCount !== null) {
                        finalCount = authoritativeCount;
                    } else if (currentCount !== null) {
                        if (finalActive === wasActive) {
                            // Disagreement or unchanged: preserve prior count
                            finalCount = currentCount;
                        } else if (finalActive) {
                            finalCount = currentCount + 1;
                        } else {
                            finalCount = Math.max(0, currentCount - 1);
                        }
                    }
                }

                // Re-query matching buttons immediately before applying final state
                buttons = getMatchingButtons(action, postId);
                applyState(buttons, action, finalActive, finalCount);
            } else {
                // Non-2xx response: rollback optimistic update
                let errorBody = null;
                try {
                    errorBody = await http.readJson(response);
                } catch (_) {}

                buttons = getMatchingButtons(action, postId);
                applyState(buttons, action, wasActive, currentCount);
                const msg = translateErrorStatus(response.status, errorBody);
                announce(msg, 'danger');
            }
        } catch (_) {
            // Network failure, malformed response or envelope validation failure: rollback optimistic update
            buttons = getMatchingButtons(action, postId);
            applyState(buttons, action, wasActive, currentCount);
            announce('Không thể thực hiện thao tác lúc này. Vui lòng thử lại sau.', 'danger');
        } finally {
            inFlight.delete(lockKey);
            buttons = getMatchingButtons(action, postId);
            setBusy(buttons, false);
        }
    }

    // Global click listener using event delegation
    if (typeof document !== 'undefined') {
        document.addEventListener('click', function (event) {
            const targetBtn = event.target.closest('[data-action="like"], [data-action="bookmark"], [data-action="repost"]');
            if (!targetBtn) return;
            event.preventDefault();
            handleInteraction(targetBtn);
        });
    }
})();
