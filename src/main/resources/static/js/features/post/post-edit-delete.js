/**
 * AloUTE — Post Edit & Delete Controller
 *
 * Dedicated owner for post management actions:
 * - Edit Post: PATCH /api/posts/{postId} with { content: string }
 * - Delete Post: DELETE /api/posts/{postId} with 204 No Content
 *
 * Conforms to frontend_rule.md section 14 (shared HTTP transport via window.alouteHttp)
 * and ALOUTE_SYSTEM_ARCHITECTURE.md (ApiResponse envelope validation).
 */
(function () {
    'use strict';

    // Idempotent initialization guard
    if (typeof window !== 'undefined' && window.__aloutePostEditDeleteInitialized) return;
    if (typeof window !== 'undefined') window.__aloutePostEditDeleteInitialized = true;

    // Concurrency lock: set of active postId strings (serialized per post)
    const inFlight = new Set();
    // Success-finalization lock: postIds that successfully mutated and are awaiting hidden.bs.modal (QA F-1)
    const finalizing = new Set();

    // Track initiating trigger control to restore accessibility focus on modal dismissal
    let lastTriggerElement = null;

    // Check if element is connected to the active DOM tree
    function isConnected(el) {
        if (!el) return false;
        if (typeof el.isConnected === 'boolean') return el.isConnected;
        let curr = el;
        while (curr) {
            if (curr === document || curr === document.body) return true;
            curr = curr.parentElement;
        }
        return false;
    }

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
            clearTimeout(window.__aloutePostEditDeleteNoticeTimer);
            window.__aloutePostEditDeleteNoticeTimer = setTimeout(function () {
                box.hidden = true;
            }, 3500);
        }
    }

    // Map HTTP error status codes and backend error objects to user-friendly Vietnamese messages
    function translateError(status, body) {
        if (status === 401) {
            return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
        }
        if (status === 403) {
            return 'Bạn không có quyền thực hiện thao tác này.';
        }
        if (status === 404) {
            return 'Bài viết không tồn tại hoặc đã bị xóa.';
        }
        if (status === 400 || status === 422) {
            if (body && body.error && body.error.fieldErrors && body.error.fieldErrors.content) {
                const msgs = body.error.fieldErrors.content;
                return Array.isArray(msgs) ? msgs.join(' ') : String(msgs);
            }
            return 'Dữ liệu bài viết không hợp lệ. Vui lòng kiểm tra lại.';
        }
        if (status === 500) {
            return 'Hệ thống đang gặp sự cố khi xử lý bài viết. Vui lòng thử lại sau.';
        }
        if (body && body.error && typeof body.error.message === 'string') {
            const msg = body.error.message;
            if (msg === 'Access is denied' || msg === 'Access Denied') {
                return 'Bạn không có quyền thực hiện thao tác này.';
            }
            if (msg === 'Post not found') {
                return 'Bài viết không tồn tại hoặc đã bị xóa.';
            }
            if (msg === 'Validation failed') {
                return 'Dữ liệu không hợp lệ. Vui lòng kiểm tra lại.';
            }
        }
        return 'Không thể thực hiện thao tác lúc này. Vui lòng thử lại sau.';
    }

    // Modal state tracker for coordinating public lifecycle events & deferred dismissals (QA F-1)
    function getModalState(modalEl) {
        if (!modalEl) return null;
        if (!modalEl.__alouteModalState) {
            modalEl.__alouteModalState = {
                isShowing: false,
                isShown: false,
                dismissOnShown: false,
                onHiddenCallback: null,
                activePostId: null
            };
        }
        return modalEl.__alouteModalState;
    }

    function getActivePostId(modalEl) {
        if (!modalEl) return null;
        if (modalEl.id === 'editPostModal') {
            const form = document.getElementById('edit-post-form');
            return form ? (form.getAttribute('data-post-id') || (form.dataset && form.dataset.postId)) : null;
        }
        if (modalEl.id === 'deletePostModal') {
            return modalEl.getAttribute('data-post-id') || (modalEl.dataset && modalEl.dataset.postId);
        }
        return null;
    }

    // Modal helpers with Bootstrap 5 support and transition race coordination
    function getModalInstance(modalEl) {
        if (!modalEl || typeof window === 'undefined' || !window.bootstrap || !window.bootstrap.Modal) return null;
        return window.bootstrap.Modal.getOrCreateInstance ?
            window.bootstrap.Modal.getOrCreateInstance(modalEl) :
            new window.bootstrap.Modal(modalEl);
    }

    function showModal(modalEl) {
        if (!modalEl) {
            announce('Không tìm thấy giao diện hộp thoại. Vui lòng thử lại sau.', 'danger');
            return false;
        }
        const state = getModalState(modalEl);
        if (state) {
            state.isShowing = true;
            state.isShown = false;
            state.dismissOnShown = false;
            state.onHiddenCallback = null;
            state.activePostId = getActivePostId(modalEl);
        }
        const instance = getModalInstance(modalEl);
        if (instance && typeof instance.show === 'function') {
            instance.show();
            return true;
        }
        if (state) {
            state.isShowing = false;
        }
        // Safe Vietnamese feedback when Bootstrap 5 is unavailable; never build unstyled custom modal
        announce('Không thể mở hộp thoại lúc này. Giao diện chưa sẵn sàng. Vui lòng tải lại trang.', 'danger');
        return false;
    }

    function dismissModal(modalEl) {
        if (!modalEl) return;
        const state = getModalState(modalEl);
        if (state && state.isShowing) {
            // Modal is transitioning into view; Bootstrap ignores hide() while transitioning.
            // Queue dismissal intent to execute immediately once shown.bs.modal fires.
            state.dismissOnShown = true;
            return;
        }
        hideModal(modalEl);
    }

    function hideModal(modalEl) {
        if (!modalEl || typeof window === 'undefined' || !window.bootstrap || !window.bootstrap.Modal) return;
        const instance = window.bootstrap.Modal.getInstance ?
            window.bootstrap.Modal.getInstance(modalEl) : null;
        if (instance && typeof instance.hide === 'function') {
            instance.hide();
        }
    }

    function getTriggerReturnTarget(targetBtn) {
        if (!targetBtn) return null;
        const dropdown = targetBtn.closest('.dropdown') || targetBtn.closest('.post-card');
        const toggle = dropdown ? dropdown.querySelector('[data-bs-toggle="dropdown"]') : null;
        return toggle || targetBtn;
    }

    function restoreTriggerFocus() {
        if (lastTriggerElement && isConnected(lastTriggerElement) && typeof lastTriggerElement.focus === 'function') {
            try {
                lastTriggerElement.focus();
            } catch (_) {}
        } else if (typeof document !== 'undefined') {
            let fallback = document.getElementById('main-content') || document.querySelector('main');
            if (!fallback && document.body) {
                fallback = document.body;
            }
            if (fallback && typeof fallback.focus === 'function') {
                try {
                    if (!fallback.hasAttribute('tabindex')) {
                        fallback.setAttribute('tabindex', '-1');
                    }
                    fallback.focus();
                } catch (_) {}
            }
        }
        lastTriggerElement = null;
    }

    // Exact route segment match to avoid false positive matching post 1 on /posts/10
    function isPostDetailRoute(pathname, postId) {
        if (!pathname || !postId) return false;
        const match = String(pathname).match(/(?:^|\/)posts\/([^/?#]+)(?:[/?#]|$)/);
        return match ? match[1] === String(postId) : false;
    }

    // Bind modal internal event listeners (form submit, input change, character counter, delete confirmation)
    let modalListenersBound = false;
    function bindModalEventListeners() {
        if (modalListenersBound || typeof document === 'undefined') return;

        const editModal = document.getElementById('editPostModal');
        const deleteModal = document.getElementById('deletePostModal');
        const form = document.getElementById('edit-post-form');
        const textarea = document.getElementById('edit-post-content');
        const charCount = document.getElementById('edit-post-char-count');
        const errorFeedback = document.getElementById('edit-post-error');
        const confirmDeleteBtn = document.getElementById('btn-confirm-delete-post');
        const cancelEditBtn = document.getElementById('btn-cancel-edit-post');
        const cancelDeleteBtn = document.getElementById('btn-cancel-delete-post');

        if (!editModal && !deleteModal) return;
        modalListenersBound = true;

        // Character counter and length validation (> 4000)
        if (textarea) {
            textarea.addEventListener('input', function () {
                const len = textarea.value.length;
                if (charCount) charCount.textContent = String(len);

                if (len > 4000) {
                    if (charCount) charCount.classList.add('text-danger');
                    textarea.classList.add('is-invalid');
                    textarea.setAttribute('aria-invalid', 'true');
                    if (errorFeedback) {
                        errorFeedback.textContent = 'Nội dung bài viết vượt quá tối đa 4000 ký tự.';
                        errorFeedback.classList.remove('d-none');
                    }
                } else {
                    if (charCount) charCount.classList.remove('text-danger');
                    if (textarea.classList.contains('is-invalid')) {
                        textarea.classList.remove('is-invalid');
                        textarea.removeAttribute('aria-invalid');
                        if (errorFeedback) errorFeedback.classList.add('d-none');
                    }
                }
            });
        }

        // Edit form submission
        if (form) {
            form.addEventListener('submit', async function (e) {
                e.preventDefault();
                await handleEditSubmit();
            });
        }

        // Delete confirmation
        if (confirmDeleteBtn) {
            confirmDeleteBtn.addEventListener('click', async function (e) {
                e.preventDefault();
                await handleDeleteConfirm();
            });
        }

        // Cancel buttons
        if (cancelEditBtn) {
            cancelEditBtn.addEventListener('click', function (e) {
                const activePostId = getActivePostId(editModal);
                if (activePostId && inFlight.has(String(activePostId))) {
                    if (e && e.preventDefault) e.preventDefault();
                    return;
                }
                dismissModal(editModal);
            });
        }
        if (cancelDeleteBtn) {
            cancelDeleteBtn.addEventListener('click', function (e) {
                const activePostId = getActivePostId(deleteModal);
                if (activePostId && inFlight.has(String(activePostId))) {
                    if (e && e.preventDefault) e.preventDefault();
                    return;
                }
                dismissModal(deleteModal);
            });
        }

        // Unified modal lifecycle coordinator & early dismiss intent capture (QA F-1)
        function setupModalLifecycle(modalEl) {
            if (!modalEl || modalEl.__alouteLifecycleBound) return;
            modalEl.__alouteLifecycleBound = true;

            modalEl.addEventListener('show.bs.modal', function () {
                const state = getModalState(modalEl);
                if (state) {
                    state.isShowing = true;
                    state.isShown = false;
                }
            });

            modalEl.addEventListener('shown.bs.modal', function () {
                const state = getModalState(modalEl);
                if (state) {
                    state.isShowing = false;
                    state.isShown = true;
                    if (state.dismissOnShown) {
                        state.dismissOnShown = false;
                        hideModal(modalEl);
                        return;
                    }
                }

                if (modalEl.id === 'editPostModal') {
                    const textarea = document.getElementById('edit-post-content');
                    if (textarea && typeof textarea.focus === 'function') {
                        textarea.focus();
                    }
                } else if (modalEl.id === 'deletePostModal') {
                    const focusTarget = document.getElementById('btn-cancel-delete-post') || confirmDeleteBtn;
                    if (focusTarget && typeof focusTarget.focus === 'function') {
                        focusTarget.focus();
                    }
                }
            });

            modalEl.addEventListener('hide.bs.modal', function (e) {
                const activePostId = getActivePostId(modalEl);
                if (activePostId && inFlight.has(String(activePostId))) {
                    if (e && typeof e.preventDefault === 'function') {
                        e.preventDefault();
                    }
                    return;
                }
                const state = getModalState(modalEl);
                if (state) {
                    state.isShown = false;
                }
            });

            modalEl.addEventListener('hidden.bs.modal', function () {
                const state = getModalState(modalEl);
                const activePostId = (state && state.activePostId) || getActivePostId(modalEl);
                const postKey = activePostId ? String(activePostId) : null;

                let onHidden = null;
                if (state) {
                    state.isShowing = false;
                    state.isShown = false;
                    state.dismissOnShown = false;
                    state.activePostId = null;
                    onHidden = state.onHiddenCallback;
                    state.onHiddenCallback = null;
                }

                if (modalEl.id === 'deletePostModal') {
                    modalEl.removeAttribute('data-post-id');
                    if (modalEl.dataset) delete modalEl.dataset.postId;
                    const confirmBtn = document.getElementById('btn-confirm-delete-post');
                    const spinner = document.getElementById('delete-post-spinner');
                    const btnText = document.getElementById('delete-post-btn-text');
                    if (confirmBtn) {
                        confirmBtn.disabled = false;
                        confirmBtn.removeAttribute('aria-busy');
                    }
                    if (spinner) spinner.classList.add('d-none');
                    if (btnText) btnText.textContent = 'Xóa bài viết';
                } else if (modalEl.id === 'editPostModal') {
                    const form = document.getElementById('edit-post-form');
                    if (form) {
                        form.removeAttribute('data-post-id');
                        form.removeAttribute('data-original-content');
                        if (form.dataset) {
                            delete form.dataset.postId;
                            delete form.dataset.originalContent;
                        }
                    }
                    const submitBtn = document.getElementById('btn-save-post');
                    const spinner = document.getElementById('save-post-spinner');
                    const btnText = document.getElementById('save-post-btn-text');
                    if (submitBtn) {
                        submitBtn.disabled = false;
                        submitBtn.removeAttribute('aria-busy');
                    }
                    if (spinner) spinner.classList.add('d-none');
                    if (btnText) btnText.textContent = 'Lưu thay đổi';
                }

                if (typeof onHidden === 'function') {
                    try {
                        onHidden();
                    } catch (_) {}
                } else {
                    restoreTriggerFocus();
                }

                // Unlock success-finalization and concurrency locks strictly upon hidden.bs.modal
                if (postKey) {
                    finalizing.delete(postKey);
                    inFlight.delete(postKey);
                }
            });

            // Capture early user dismiss intent where Bootstrap itself ignores hide before shown
            const dismissBtns = modalEl.querySelectorAll('[data-bs-dismiss="modal"], .btn-close, #btn-cancel-edit-post, #btn-cancel-delete-post');
            dismissBtns.forEach(btn => {
                btn.addEventListener('click', function (e) {
                    const activePostId = getActivePostId(modalEl);
                    if (activePostId && inFlight.has(String(activePostId))) {
                        if (e && e.preventDefault) e.preventDefault();
                        return;
                    }
                    dismissModal(modalEl);
                });
            });

            // Escape key dismiss intent (both on modal and document)
            modalEl.addEventListener('keydown', function (e) {
                if (e.key === 'Escape' || e.key === 'Esc' || e.keyCode === 27) {
                    const activePostId = getActivePostId(modalEl);
                    if (activePostId && inFlight.has(String(activePostId))) {
                        if (e && e.preventDefault) e.preventDefault();
                        return;
                    }
                    dismissModal(modalEl);
                }
            });

            if (typeof document !== 'undefined') {
                document.addEventListener('keydown', function (e) {
                    if (e.key === 'Escape' || e.key === 'Esc' || e.keyCode === 27) {
                        const state = getModalState(modalEl);
                        if (state && (state.isShowing || state.isShown)) {
                            const activePostId = getActivePostId(modalEl);
                            if (activePostId && inFlight.has(String(activePostId))) {
                                if (e && e.preventDefault) e.preventDefault();
                                return;
                            }
                            dismissModal(modalEl);
                        }
                    }
                });
            }

            // Backdrop click dismiss intent (modal element itself or .modal-backdrop)
            modalEl.addEventListener('click', function (e) {
                if (e.target === modalEl) {
                    const activePostId = getActivePostId(modalEl);
                    if (activePostId && inFlight.has(String(activePostId))) {
                        if (e && e.preventDefault) e.preventDefault();
                        return;
                    }
                    dismissModal(modalEl);
                }
            });

            if (typeof document !== 'undefined') {
                document.addEventListener('click', function (e) {
                    if (e.target && e.target.classList && e.target.classList.contains('modal-backdrop')) {
                        const state = getModalState(modalEl);
                        if (state && (state.isShowing || state.isShown)) {
                            const activePostId = getActivePostId(modalEl);
                            if (activePostId && inFlight.has(String(activePostId))) {
                                if (e && e.preventDefault) e.preventDefault();
                                return;
                            }
                            dismissModal(modalEl);
                        }
                    }
                });
            }
        }

        if (editModal) setupModalLifecycle(editModal);
        if (deleteModal) setupModalLifecycle(deleteModal);
    }

    // =========================================================================
    // 1. EDIT POST WORKFLOW
    // =========================================================================

    function openEditModal(targetBtn) {
        bindModalEventListeners();

        const postId = targetBtn.getAttribute('data-post-id') ||
            targetBtn.closest('[data-post-id]')?.getAttribute('data-post-id');
        if (!postId) return;

        // Concurrency guard: block opening another action if post mutation is pending or finalizing
        if (inFlight.has(String(postId)) || finalizing.has(String(postId))) {
            announce('Thao tác với bài viết đang được xử lý.', 'warning');
            return;
        }

        // Check permission if specified in DOM
        const card = targetBtn.closest('.post-card') ||
            document.querySelector('.post-card[data-post-id="' + postId + '"]');
        const canEditAttr = targetBtn.getAttribute('data-can-edit') ||
            card?.getAttribute('data-can-edit');
        if (canEditAttr === 'false') {
            announce('Bạn không có quyền chỉnh sửa bài viết này.', 'danger');
            return;
        }

        // Extract exact current content (preserve raw textContent, no trim)
        let currentContent = '';
        if (card) {
            if (card.dataset && card.dataset.postContent !== undefined) {
                currentContent = card.dataset.postContent;
            } else if (card.hasAttribute && card.hasAttribute('data-post-content')) {
                currentContent = card.getAttribute('data-post-content');
            } else {
                const p = card.querySelector('.post-card-content p') || card.querySelector('.post-card-content');
                if (p) currentContent = p.textContent;
            }
        }

        const modalEl = document.getElementById('editPostModal');
        const form = document.getElementById('edit-post-form');
        const textarea = document.getElementById('edit-post-content');
        const charCount = document.getElementById('edit-post-char-count');
        const statusBox = document.getElementById('edit-post-status');
        const errorFeedback = document.getElementById('edit-post-error');

        if (form) {
            form.setAttribute('data-post-id', String(postId));
            form.setAttribute('data-original-content', currentContent);
            if (form.dataset) {
                form.dataset.postId = String(postId);
                form.dataset.originalContent = currentContent;
            }
        }

        if (textarea) {
            textarea.value = currentContent;
            textarea.classList.remove('is-invalid');
            textarea.removeAttribute('aria-invalid');
        }

        if (charCount) {
            charCount.textContent = String(currentContent.length);
            charCount.classList.remove('text-danger');
        }

        if (statusBox) {
            statusBox.className = 'alert d-none py-2 small mb-3';
            statusBox.textContent = '';
        }

        if (errorFeedback) {
            errorFeedback.className = 'invalid-feedback d-none';
            errorFeedback.textContent = '';
        }

        const submitBtn = document.getElementById('btn-save-post');
        const spinner = document.getElementById('save-post-spinner');
        const btnText = document.getElementById('save-post-btn-text');
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.removeAttribute('aria-busy');
        }
        if (spinner) spinner.classList.add('d-none');
        if (btnText) btnText.textContent = 'Lưu thay đổi';

        lastTriggerElement = getTriggerReturnTarget(targetBtn);
        showModal(modalEl);
    }

    async function handleEditSubmit() {
        const modalEl = document.getElementById('editPostModal');
        const form = document.getElementById('edit-post-form');
        const textarea = document.getElementById('edit-post-content');
        const statusBox = document.getElementById('edit-post-status');
        const errorFeedback = document.getElementById('edit-post-error');
        const submitBtn = document.getElementById('btn-save-post');
        const spinner = document.getElementById('save-post-spinner');
        const btnText = document.getElementById('save-post-btn-text');

        if (!form || !textarea) return;

        const postId = form.getAttribute('data-post-id') || (form.dataset && form.dataset.postId);
        const originalContent = form.getAttribute('data-original-content') !== null ?
            form.getAttribute('data-original-content') :
            ((form.dataset && form.dataset.originalContent !== undefined) ? form.dataset.originalContent : '');
        if (!postId) return;

        // Concurrency guard: serialized per post (block during in-flight and success-finalization)
        const postKey = String(postId);
        if (inFlight.has(postKey) || finalizing.has(postKey)) return;

        // Reset previous feedback
        if (statusBox) {
            statusBox.className = 'alert d-none py-2 small mb-3';
            statusBox.textContent = '';
        }
        if (errorFeedback) {
            errorFeedback.className = 'invalid-feedback d-none';
            errorFeedback.textContent = '';
        }
        textarea.classList.remove('is-invalid');
        textarea.removeAttribute('aria-invalid');

        const rawValue = textarea.value;

        // UpdatePostRequest has ONLY @Size(max=4000); empty and whitespace-only strings are valid
        if (rawValue.length > 4000) {
            textarea.classList.add('is-invalid');
            textarea.setAttribute('aria-invalid', 'true');
            if (errorFeedback) {
                errorFeedback.textContent = 'Nội dung bài viết vượt quá tối đa 4000 ký tự.';
                errorFeedback.classList.remove('d-none');
            }
            if (statusBox) {
                statusBox.className = 'alert alert-warning py-2 small mb-3';
                statusBox.textContent = 'Nội dung bài viết vượt quá tối đa 4000 ký tự.';
                statusBox.classList.remove('d-none');
            }
            return;
        }

        // Compare exact unchanged content before dispatch
        if (rawValue === originalContent) {
            dismissModal(modalEl);
            announce('Nội dung bài viết không thay đổi.', 'info');
            return;
        }

        // Acquire lock & display loading state
        inFlight.add(postKey);
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.setAttribute('aria-busy', 'true');
        }
        if (spinner) spinner.classList.remove('d-none');
        if (btnText) btnText.textContent = 'Đang lưu...';

        // Check shared HTTP client
        if (typeof window === 'undefined' || !window.alouteHttp || typeof window.alouteHttp.request !== 'function' || typeof window.alouteHttp.readJson !== 'function') {
            inFlight.delete(postKey);
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.removeAttribute('aria-busy');
            }
            if (spinner) spinner.classList.add('d-none');
            if (btnText) btnText.textContent = 'Lưu thay đổi';
            const msg = 'Không thể thực hiện thao tác lúc này. Vui lòng thử lại sau.';
            if (statusBox) {
                statusBox.className = 'alert alert-danger py-2 small mb-3';
                statusBox.textContent = msg;
                statusBox.classList.remove('d-none');
            }
            announce(msg, 'danger');
            return;
        }

        const http = window.alouteHttp;
        // Payload contains ONLY modified content; never overwrites visibility/allowReply/allowQuote
        const payload = { content: rawValue };

        try {
            const response = await http.request('/api/posts/' + encodeURIComponent(postId), {
                method: 'PATCH',
                body: payload
            });

            if (response.ok && response.status === 200) {
                const body = await http.readJson(response);

                // Reject malformed or noncanonical envelopes
                if (!body || typeof body !== 'object' || body.success !== true || !body.data || typeof body.data !== 'object') {
                    throw new Error('MALFORMED_OR_NONCANONICAL_ENVELOPE');
                }

                // Verify resource identity matches
                const returnedId = body.data.id !== undefined ? body.data.id : body.data.postId;
                if (returnedId === null || returnedId === undefined || String(returnedId).trim() !== String(postId).trim()) {
                    throw new Error('MISSING_OR_MISMATCHED_POST_IDENTITY');
                }

                // Strict validation: content must be string; reject absent/null/number/object without falling back to rawValue
                if (typeof body.data.content !== 'string') {
                    throw new Error('MALFORMED_OR_NONSTRING_CONTENT');
                }

                const updatedContent = body.data.content;

                // Synchronize all duplicate cards in the DOM
                const matchingCards = document.querySelectorAll('.post-card[data-post-id="' + postId + '"]');
                matchingCards.forEach(c => {
                    const p = c.querySelector('.post-card-content p') || c.querySelector('.post-card-content');
                    if (p) p.textContent = updatedContent;
                    c.setAttribute('data-post-content', updatedContent);
                    if (c.dataset) c.dataset.postContent = updatedContent;
                });

                // Clear inFlight and enter success-finalization state tied to hidden.bs.modal
                inFlight.delete(postKey);
                finalizing.add(postKey);
                dismissModal(modalEl);
                announce('Đã cập nhật bài viết thành công!', 'success');
            } else {
                // Non-200 response: preserve modal and input state
                let errorBody = null;
                try { errorBody = await http.readJson(response); } catch (_) {}
                const msg = translateError(response.status, errorBody);
                if (statusBox) {
                    statusBox.className = 'alert alert-danger py-2 small mb-3';
                    statusBox.textContent = msg;
                    statusBox.classList.remove('d-none');
                }
                announce(msg, 'danger');
            }
        } catch (_) {
            // Network failure or envelope validation failure: preserve modal and input
            const msg = 'Không thể thực hiện thao tác lúc này. Vui lòng thử lại sau.';
            if (statusBox) {
                statusBox.className = 'alert alert-danger py-2 small mb-3';
                statusBox.textContent = msg;
                statusBox.classList.remove('d-none');
            }
            announce(msg, 'danger');
        } finally {
            inFlight.delete(postKey);
            if (!finalizing.has(postKey)) {
                if (submitBtn) {
                    submitBtn.disabled = false;
                    submitBtn.removeAttribute('aria-busy');
                }
                if (spinner) spinner.classList.add('d-none');
                if (btnText) btnText.textContent = 'Lưu thay đổi';
            }
        }
    }

    // =========================================================================
    // 2. DELETE POST WORKFLOW
    // =========================================================================

    function openDeleteModal(targetBtn) {
        bindModalEventListeners();

        const postId = targetBtn.getAttribute('data-post-id') ||
            targetBtn.closest('[data-post-id]')?.getAttribute('data-post-id');
        if (!postId) return;

        // Concurrency guard: block opening another action if post mutation is pending or finalizing
        if (inFlight.has(String(postId)) || finalizing.has(String(postId))) {
            announce('Thao tác với bài viết đang được xử lý.', 'warning');
            return;
        }

        // Check permission if specified in DOM
        const card = targetBtn.closest('.post-card') ||
            document.querySelector('.post-card[data-post-id="' + postId + '"]');
        const canDeleteAttr = targetBtn.getAttribute('data-can-delete') ||
            card?.getAttribute('data-can-delete');
        if (canDeleteAttr === 'false') {
            announce('Bạn không có quyền xóa bài viết này.', 'danger');
            return;
        }

        const modalEl = document.getElementById('deletePostModal');
        const statusBox = document.getElementById('delete-post-status');

        if (modalEl) {
            modalEl.setAttribute('data-post-id', String(postId));
            if (modalEl.dataset) {
                modalEl.dataset.postId = String(postId);
            }
        }

        if (statusBox) {
            statusBox.className = 'alert d-none py-2 small mt-3 mb-0';
            statusBox.textContent = '';
        }

        const confirmBtn = document.getElementById('btn-confirm-delete-post');
        const spinner = document.getElementById('delete-post-spinner');
        const btnText = document.getElementById('delete-post-btn-text');
        if (confirmBtn) {
            confirmBtn.disabled = false;
            confirmBtn.removeAttribute('aria-busy');
        }
        if (spinner) spinner.classList.add('d-none');
        if (btnText) btnText.textContent = 'Xóa bài viết';

        lastTriggerElement = getTriggerReturnTarget(targetBtn);
        showModal(modalEl);
    }

    async function handleDeleteConfirm() {
        const modalEl = document.getElementById('deletePostModal');
        const statusBox = document.getElementById('delete-post-status');
        const confirmBtn = document.getElementById('btn-confirm-delete-post');
        const spinner = document.getElementById('delete-post-spinner');
        const btnText = document.getElementById('delete-post-btn-text');

        if (!modalEl) return;
        const postId = modalEl.getAttribute('data-post-id') || (modalEl.dataset && modalEl.dataset.postId);
        if (!postId) return;

        // Concurrency guard: serialized per post (block during in-flight and success-finalization)
        const postKey = String(postId);
        if (inFlight.has(postKey) || finalizing.has(postKey)) return;

        // Acquire lock & display loading state
        inFlight.add(postKey);
        if (confirmBtn) {
            confirmBtn.disabled = true;
            confirmBtn.setAttribute('aria-busy', 'true');
        }
        if (spinner) spinner.classList.remove('d-none');
        if (btnText) btnText.textContent = 'Đang xóa...';

        // Check shared HTTP client
        if (typeof window === 'undefined' || !window.alouteHttp || typeof window.alouteHttp.request !== 'function') {
            inFlight.delete(postKey);
            if (confirmBtn) {
                confirmBtn.disabled = false;
                confirmBtn.removeAttribute('aria-busy');
            }
            if (spinner) spinner.classList.add('d-none');
            if (btnText) btnText.textContent = 'Xóa bài viết';
            const msg = 'Không thể thực hiện thao tác lúc này. Vui lòng thử lại sau.';
            if (statusBox) {
                statusBox.className = 'alert alert-danger py-2 small mt-3 mb-0';
                statusBox.textContent = msg;
                statusBox.classList.remove('d-none');
            }
            announce(msg, 'danger');
            return;
        }

        const http = window.alouteHttp;

        try {
            const response = await http.request('/api/posts/' + encodeURIComponent(postId), {
                method: 'DELETE'
            });

            // HTTP 204 No Content is the canonical expected delete response
            if (response.status === 204) {
                // Clear inFlight and enter success-finalization state tied to hidden.bs.modal
                inFlight.delete(postKey);
                finalizing.add(postKey);

                const performDeleteCleanup = function () {
                    // Synchronize and remove all duplicate cards with this postId from DOM
                    const matchingElements = document.querySelectorAll('.post-card[data-post-id="' + postId + '"]');
                    matchingElements.forEach(el => el.remove());

                    // Safe Post Detail deletion & reply behavior: check exact route segment & detail reply form
                    const replyForm = document.getElementById('reply-form');
                    const replyPostId = replyForm ? (replyForm.getAttribute('data-post-id') || (replyForm.dataset && replyForm.dataset.postId)) : null;
                    const currentPath = (typeof window !== 'undefined' && window.location && typeof window.location.pathname === 'string') ? window.location.pathname : '';
                    const isDetailOfThisPost = (replyPostId && String(replyPostId) === String(postId)) || isPostDetailRoute(currentPath, postId);

                    if (isDetailOfThisPost) {
                        const commentInput = document.getElementById('commentContent');
                        const submitReply = document.getElementById('btn-submit-reply');
                        if (commentInput) commentInput.disabled = true;
                        if (submitReply) submitReply.disabled = true;

                        const replyStatus = document.getElementById('reply-status');
                        if (replyStatus) {
                            replyStatus.className = 'alert alert-secondary py-2 small mb-3';
                            replyStatus.textContent = 'Bài viết đã bị xóa.';
                            replyStatus.classList.remove('d-none');
                        }

                        announce('Đã xóa bài viết thành công!', 'success');

                        if (isPostDetailRoute(currentPath, postId)) {
                            // Immediate navigation upon actual modal dismissal (500ms guess removed)
                            window.location.href = '/feed';
                        }
                    } else {
                        announce('Đã xóa bài viết thành công!', 'success');
                    }

                    // Ensure focus remains valid after deleted-card cleanup
                    restoreTriggerFocus();
                };

                const state = getModalState(modalEl);
                if (state && (state.isShowing || state.isShown)) {
                    state.onHiddenCallback = performDeleteCleanup;
                    dismissModal(modalEl);
                } else {
                    dismissModal(modalEl);
                    performDeleteCleanup();
                    finalizing.delete(postKey);
                }
            } else {
                // Non-204 response: preserve card state in DOM
                let errorBody = null;
                try {
                    if (typeof http.readJson === 'function') {
                        errorBody = await http.readJson(response);
                    }
                } catch (_) {}
                const msg = translateError(response.status, errorBody);
                if (statusBox) {
                    statusBox.className = 'alert alert-danger py-2 small mt-3 mb-0';
                    statusBox.textContent = msg;
                    statusBox.classList.remove('d-none');
                }
                announce(msg, 'danger');
            }
        } catch (_) {
            const msg = 'Lỗi kết nối khi xóa bài viết. Vui lòng thử lại sau.';
            if (statusBox) {
                statusBox.className = 'alert alert-danger py-2 small mt-3 mb-0';
                statusBox.textContent = msg;
                statusBox.classList.remove('d-none');
            }
            announce(msg, 'danger');
        } finally {
            inFlight.delete(postKey);
            if (!finalizing.has(postKey)) {
                if (confirmBtn) {
                    confirmBtn.disabled = false;
                    confirmBtn.removeAttribute('aria-busy');
                }
                if (spinner) spinner.classList.add('d-none');
                if (btnText) btnText.textContent = 'Xóa bài viết';
            }
        }
    }

    // =========================================================================
    // 3. GLOBAL CLICK DELEGATION
    // =========================================================================

    if (typeof document !== 'undefined') {
        document.addEventListener('click', function (event) {
            const targetBtn = event.target.closest('[data-action="edit-post"], [data-action="delete-post"]');
            if (!targetBtn) return;

            event.preventDefault();
            const action = targetBtn.getAttribute('data-action');

            if (action === 'edit-post') {
                openEditModal(targetBtn);
            } else if (action === 'delete-post') {
                openDeleteModal(targetBtn);
            }
        });
    }

    // Initialize listeners when DOM ready
    if (typeof document !== 'undefined') {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', function () {
                bindModalEventListeners();
            });
        } else {
            bindModalEventListeners();
        }
    }
})();
