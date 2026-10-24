/**
 * AloUTE — Post Composer & Reply Controller
 * Scoped interactivity for:
 * - Create Post page (/post/create): POST /api/posts
 * - Post Detail page (/posts/{id}): POST /api/posts/{id}/replies
 */
(function () {
    'use strict';

    // CSRF token extractor and header builder
    function getCsrfToken() {
        const cookie = document.cookie.split('; ').find(row => row.startsWith('XSRF-TOKEN='));
        return cookie ? decodeURIComponent(cookie.split('=').slice(1).join('=')) : null;
    }

    function csrfHeaders(extra) {
        const headers = Object.assign({}, extra || {});
        const token = getCsrfToken();
        if (token) {
            headers['X-XSRF-TOKEN'] = token;
        }
        return headers;
    }

    // Safe JSON response reader
    async function readJson(response) {
        const text = await response.text();
        if (!text) return null;
        try {
            return JSON.parse(text);
        } catch (_) {
            return null;
        }
    }

    // Translate backend error codes and messages into user-friendly Vietnamese
    function translateError(error, defaultMsg) {
        if (!error) return defaultMsg || 'Đã xảy ra lỗi.';
        if (window.console && typeof window.console.warn === 'function') {
            window.console.warn('[AloUTE] Post operation error diagnostic:', error);
        }
        const code = error.code;
        const message = error.message;

        if (code === 'VALIDATION_FAILED') return 'Dữ liệu không hợp lệ. Vui lòng kiểm tra lại.';
        if (code === 'AUTH_FORBIDDEN' || code === 'ACCESS_DENIED') return 'Bạn không có quyền thực hiện thao tác này.';
        if (code === 'AUTH_TOKEN_EXPIRED') return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
        if (code === 'INTERNAL_SERVER_ERROR') return 'Hệ thống đang gặp sự cố. Vui lòng thử lại sau.';
        if (code === 'POST_NOT_FOUND') return 'Bài viết không tồn tại hoặc đã bị xóa.';
        if (code === 'POST_REPLY_NOT_ALLOWED' || code === 'REPLY_NOT_ALLOWED') return 'Tác giả đã tắt tính năng bình luận cho bài viết này.';

        if (message === 'Validation failed') return 'Dữ liệu không hợp lệ. Vui lòng kiểm tra lại.';
        if (message === 'Access Denied' || message === 'Access is denied') return 'Bạn không có quyền thực hiện thao tác này.';

        return defaultMsg || 'Đã xảy ra lỗi không xác định. Vui lòng thử lại sau.';
    }

    // Display inline status alert
    function setStatus(statusEl, message, type) {
        if (!statusEl) return;
        if (!message) {
            statusEl.className = 'alert d-none mb-3';
            statusEl.textContent = '';
            return;
        }
        statusEl.className = 'alert alert-' + (type || 'info') + ' mb-3';
        statusEl.textContent = message;
    }

    // =========================================================================
    // 1. CREATE POST (/post/create)
    // =========================================================================
    function initCreatePost() {
        const form = document.getElementById('create-post-form');
        if (!form) return;
        if (form.dataset.composerInitialized === 'true') return;
        form.dataset.composerInitialized = 'true';

        let isSubmitting = false;

        const contentInput = document.getElementById('postContent');
        const visibilitySelect = document.getElementById('visibility');
        const allowReplyInput = document.getElementById('allowReply');
        const allowQuoteInput = document.getElementById('allowQuote');
        const submitBtn = document.getElementById('btn-create-post') || form.querySelector('button[type="submit"]') || form.querySelector('[data-action="create-post"]');
        const charCountEl = document.getElementById('char-count');
        const statusBox = document.getElementById('create-post-status');
        const contentError = document.getElementById('post-content-error');

        function updateSubmitButtonState() {
            if (!submitBtn) return;
            if (isSubmitting) {
                submitBtn.disabled = true;
                return;
            }
            const trimmed = contentInput ? contentInput.value.trim() : '';
            const length = contentInput ? contentInput.value.length : 0;
            const isValid = trimmed.length > 0 && length <= 2000;
            submitBtn.disabled = !isValid;
        }

        // =====================================================================
        // Local draft recovery: unsent composer content survives page reloads,
        // accidental navigation and accidental modal dismissal.
        // Drafts are scoped per authenticated user AND per composition context
        // (standalone post vs a specific community) so content can never leak
        // across accounts on a shared browser. Fail-closed: without a user id
        // nothing is persisted at all.
        // =====================================================================
        let draftSaveTimer = null;
        let draftDiscardBtn = null;

        function resolveDraftScope() {
            const body = document.body;
            const userId = body && body.dataset ? String(body.dataset.currentUserId || '').trim() : '';
            if (!userId || userId === 'null' || userId === 'undefined') {
                return null;
            }
            const commInput = document.getElementById('post-community-id');
            const communityId = (commInput && commInput.value && /^\d+$/.test(commInput.value)) ? commInput.value : 'root';
            return {
                userId: userId,
                communityId: communityId,
                key: 'aloute.post.draft.v1.u' + userId + '.c' + communityId
            };
        }

        const draftScope = resolveDraftScope();

        function readDraft() {
            if (!draftScope) return null;
            try {
                const raw = window.localStorage.getItem(draftScope.key);
                if (!raw) return null;
                const draft = JSON.parse(raw);
                if (!draft || typeof draft !== 'object' || typeof draft.content !== 'string') return null;
                return draft;
            } catch (_) {
                return null;
            }
        }

        function clearDraftQuietly() {
            if (!draftScope) return;
            try {
                window.localStorage.removeItem(draftScope.key);
            } catch (_) {}
        }

        function cancelPendingDraftSave() {
            if (draftSaveTimer) {
                clearTimeout(draftSaveTimer);
                draftSaveTimer = null;
            }
        }

        function removeDraftNotice() {
            if (draftDiscardBtn) {
                draftDiscardBtn.remove();
                draftDiscardBtn = null;
            }
            setStatus(statusBox, null);
        }

        function discardDraft() {
            if (contentInput) contentInput.value = '';
            if (visibilitySelect) visibilitySelect.value = 'PUBLIC';
            if (allowReplyInput) allowReplyInput.checked = true;
            if (allowQuoteInput) allowQuoteInput.checked = true;
            resetPollBuilder();
            cancelPendingDraftSave();
            clearDraftQuietly();
            removeDraftNotice();
            updateCharCount();
            updateSubmitButtonState();
            if (contentInput) contentInput.focus();
        }

        function showDraftNotice(savedAt) {
            const timeLabel = savedAt ? new Date(savedAt).toLocaleString('vi-VN') : 'trước đó';
            setStatus(statusBox, 'Đã khôi phục bản nháp chưa đăng (lưu lúc ' + timeLabel + ').', 'info');

            if (!draftDiscardBtn && statusBox && statusBox.parentNode) {
                draftDiscardBtn = document.createElement('button');
                draftDiscardBtn.type = 'button';
                draftDiscardBtn.id = 'btn-discard-draft';
                draftDiscardBtn.className = 'btn btn-sm btn-outline-secondary mt-2';
                draftDiscardBtn.innerHTML = '<i class="bi bi-trash me-1" aria-hidden="true"></i>Bỏ bản nháp';
                draftDiscardBtn.addEventListener('click', function () {
                    discardDraft();
                });
                statusBox.parentNode.insertBefore(draftDiscardBtn, statusBox.nextSibling);
            }
        }

        function restoreDraftIfAny() {
            if (!contentInput) return;
            // Never overwrite server-repopulated content (validation round-trips).
            if (contentInput.value.trim().length > 0) return;

            const draft = readDraft();
            if (!draft || draft.content.trim().length === 0) return;

            contentInput.value = draft.content;
            if (visibilitySelect && draft.visibility) {
                visibilitySelect.value = draft.visibility;
            }
            if (allowReplyInput && typeof draft.allowReply === 'boolean') {
                allowReplyInput.checked = draft.allowReply;
            }
            if (allowQuoteInput && typeof draft.allowQuote === 'boolean') {
                allowQuoteInput.checked = draft.allowQuote;
            }
            restorePollDraftState(draft.poll);
            updateCharCount();
            updateSubmitButtonState();
            showDraftNotice(draft.savedAt);
        }

        // Snapshot of the poll builder for the draft, or null when the builder is absent.
        function collectPollDraftState() {
            if (!pollBuilder || !isPollActive()) return null;
            const durationValue = pollDurationSelect ? pollDurationSelect.value : '';
            return {
                question: pollQuestionInput ? pollQuestionInput.value : '',
                options: getPollOptions(),
                allowMultiple: !!(pollAllowMultipleInput && pollAllowMultipleInput.checked),
                durationDays: durationValue ? parseInt(durationValue, 10) : null
            };
        }

        // Restores a drafted poll into the builder. Runs after the poll elements are
        // wired, so it is invoked from restoreDraftIfAny through a deferred hook.
        function restorePollDraftState(pollDraft) {
            if (!pollBuilder || !pollDraft || typeof pollDraft !== 'object') return;
            const hasQuestion = pollDraft.question && pollDraft.question.trim().length > 0;
            const hasOptions = Array.isArray(pollDraft.options)
                && pollDraft.options.some(function (o) { return o && o.trim().length > 0; });
            if (!hasQuestion && !hasOptions) return;

            setPollActive(true);
            if (pollQuestionInput && pollDraft.question) {
                pollQuestionInput.value = pollDraft.question;
            }
            if (pollAllowMultipleInput) {
                pollAllowMultipleInput.checked = pollDraft.allowMultiple === true;
            }
            if (pollDurationSelect && pollDraft.durationDays) {
                pollDurationSelect.value = String(pollDraft.durationDays);
            }
            if (pollOptionsList && Array.isArray(pollDraft.options)) {
                const options = pollDraft.options.slice(0, POLL_MAX_OPTIONS);
                Array.from(pollOptionsList.querySelectorAll('.poll-option-row')).forEach(function (row, idx) {
                    if (idx >= POLL_MIN_OPTIONS) row.remove();
                });
                while (pollOptionsList.querySelectorAll('.poll-option-row').length < options.length) {
                    addPollOptionRow(false);
                }
                Array.from(pollOptionsList.querySelectorAll('.poll-option-input')).forEach(function (input, idx) {
                    input.value = options[idx] || '';
                });
            }
            refreshPollOptionUi();
        }

        function scheduleDraftSave() {
            if (isSubmitting || !draftScope) return;
            if (draftSaveTimer) {
                clearTimeout(draftSaveTimer);
            }
            draftSaveTimer = setTimeout(function () {
                draftSaveTimer = null;
                if (isSubmitting || !draftScope) return;
                const content = contentInput ? contentInput.value : '';
                if (content.trim().length === 0) {
                    // An emptied composer means the draft is no longer wanted.
                    clearDraftQuietly();
                    removeDraftNotice();
                    return;
                }
                const draft = {
                    content: content,
                    visibility: (visibilitySelect && visibilitySelect.value) || 'PUBLIC',
                    allowReply: allowReplyInput ? allowReplyInput.checked : true,
                    allowQuote: allowQuoteInput ? allowQuoteInput.checked : true,
                    poll: collectPollDraftState(),
                    savedAt: Date.now()
                };
                try {
                    window.localStorage.setItem(draftScope.key, JSON.stringify(draft));
                } catch (_) {}
            }, 400);
        }

        // =====================================================================
        // Poll builder (optional): question, 2–10 options, multi-choice switch,
        // optional duration. Attached to the create payload as poll: PollCreateInput.
        // Every element is optional so pages/DOM without the builder keep working.
        // =====================================================================
        const pollToggleBtn = document.getElementById('btn-toggle-poll');
        const pollBuilder = document.getElementById('poll-builder');
        const pollQuestionInput = document.getElementById('poll-question');
        const pollOptionsList = document.getElementById('poll-options-list');
        const pollAddOptionBtn = document.getElementById('btn-add-poll-option');
        const pollAllowMultipleInput = document.getElementById('poll-allow-multiple');
        const pollDurationSelect = document.getElementById('poll-duration');
        const pollOptionCountEl = document.getElementById('poll-option-count');
        const pollStatusBox = document.getElementById('poll-builder-status');
        const POLL_MAX_OPTIONS = 10;
        const POLL_MIN_OPTIONS = 2;

        function isPollActive() {
            return !!(pollBuilder && !pollBuilder.classList.contains('d-none'));
        }

        function setPollStatus(message) {
            if (!pollStatusBox) return;
            if (!message) {
                pollStatusBox.classList.add('d-none');
                pollStatusBox.textContent = '';
                return;
            }
            pollStatusBox.textContent = message;
            pollStatusBox.classList.remove('d-none');
        }

        function getPollOptions() {
            if (!pollOptionsList) return [];
            return Array.from(pollOptionsList.querySelectorAll('.poll-option-input'))
                .map(function (input) { return input.value.trim(); });
        }

        function refreshPollOptionUi() {
            if (!pollOptionsList) return;
            const rows = Array.from(pollOptionsList.querySelectorAll('.poll-option-row'));
            rows.forEach(function (row, idx) {
                const badge = row.querySelector('.input-group-text');
                if (badge) badge.textContent = String(idx + 1);
                const removeBtn = row.querySelector('.poll-option-remove');
                if (removeBtn) removeBtn.classList.toggle('d-none', rows.length <= POLL_MIN_OPTIONS);
                const input = row.querySelector('.poll-option-input');
                if (input) input.placeholder = 'Phương án ' + (idx + 1);
            });
            if (pollOptionCountEl) {
                pollOptionCountEl.textContent = rows.length + ' / ' + POLL_MAX_OPTIONS + ' phương án';
            }
            if (pollAddOptionBtn) {
                pollAddOptionBtn.disabled = rows.length >= POLL_MAX_OPTIONS;
            }
        }

        function addPollOptionRow(focus) {
            if (!pollOptionsList) return;
            const rows = pollOptionsList.querySelectorAll('.poll-option-row');
            if (rows.length >= POLL_MAX_OPTIONS) return;

            const row = document.createElement('div');
            row.className = 'poll-option-row input-group input-group-sm';

            const badge = document.createElement('span');
            badge.className = 'input-group-text';
            badge.textContent = String(rows.length + 1);

            const input = document.createElement('input');
            input.type = 'text';
            input.className = 'form-control poll-option-input';
            input.maxLength = 200;
            input.placeholder = 'Phương án ' + (rows.length + 1);

            const removeBtn = document.createElement('button');
            removeBtn.type = 'button';
            removeBtn.className = 'btn btn-outline-secondary poll-option-remove';
            removeBtn.setAttribute('aria-label', 'Xóa phương án ' + (rows.length + 1));
            removeBtn.tabIndex = -1;
            removeBtn.innerHTML = '<i class="bi bi-x-lg" aria-hidden="true"></i>';
            removeBtn.addEventListener('click', function () {
                row.remove();
                refreshPollOptionUi();
                scheduleDraftSave();
            });

            row.appendChild(badge);
            row.appendChild(input);
            row.appendChild(removeBtn);
            pollOptionsList.appendChild(row);
            input.addEventListener('input', scheduleDraftSave);
            refreshPollOptionUi();
            if (focus) input.focus();
        }

        function setPollActive(active) {
            if (!pollBuilder || !pollToggleBtn) return;
            pollBuilder.classList.toggle('d-none', !active);
            pollToggleBtn.setAttribute('aria-pressed', active ? 'true' : 'false');
            if (active && pollQuestionInput && !pollQuestionInput.value) {
                pollQuestionInput.focus();
            }
            setPollStatus(null);
        }

        function resetPollBuilder() {
            if (pollQuestionInput) pollQuestionInput.value = '';
            if (pollAllowMultipleInput) pollAllowMultipleInput.checked = false;
            if (pollDurationSelect) pollDurationSelect.value = '';
            if (pollOptionsList) {
                Array.from(pollOptionsList.querySelectorAll('.poll-option-row')).forEach(function (row, idx) {
                    if (idx >= POLL_MIN_OPTIONS) {
                        row.remove();
                    } else {
                        const input = row.querySelector('.poll-option-input');
                        if (input) input.value = '';
                    }
                });
            }
            setPollActive(false);
            refreshPollOptionUi();
            setPollStatus(null);
        }

        /**
         * Validates the active poll builder and returns the PollCreateInput payload,
         * or null when the builder is inactive. Returns {valid:false} with a status
         * message when the builder is active but malformed.
         */
        function collectPollForSubmit() {
            if (!isPollActive()) {
                return { valid: true, poll: null };
            }
            setPollStatus(null);

            const question = pollQuestionInput ? pollQuestionInput.value.trim() : '';
            if (!question) {
                setPollStatus('Vui lòng nhập câu hỏi bình chọn.');
                if (pollQuestionInput) pollQuestionInput.focus();
                return { valid: false, poll: null };
            }

            const options = getPollOptions().filter(function (text) { return text.length > 0; });
            if (options.length < POLL_MIN_OPTIONS) {
                setPollStatus('Bình chọn cần ít nhất ' + POLL_MIN_OPTIONS + ' phương án không để trống.');
                return { valid: false, poll: null };
            }

            const durationValue = pollDurationSelect ? pollDurationSelect.value : '';
            const durationDays = durationValue ? parseInt(durationValue, 10) : null;

            return {
                valid: true,
                poll: {
                    question: question,
                    options: options,
                    allowMultiple: !!(pollAllowMultipleInput && pollAllowMultipleInput.checked),
                    durationDays: durationDays
                }
            };
        }

        if (pollToggleBtn && pollBuilder) {
            pollToggleBtn.addEventListener('click', function () {
                setPollActive(!isPollActive());
                scheduleDraftSave();
            });
        }
        if (pollAddOptionBtn) {
            pollAddOptionBtn.addEventListener('click', function () {
                addPollOptionRow(true);
                scheduleDraftSave();
            });
        }
        if (pollQuestionInput) {
            pollQuestionInput.addEventListener('input', scheduleDraftSave);
        }
        if (pollAllowMultipleInput) {
            pollAllowMultipleInput.addEventListener('change', scheduleDraftSave);
        }
        if (pollDurationSelect) {
            pollDurationSelect.addEventListener('change', scheduleDraftSave);
        }
        refreshPollOptionUi();

        // Draft restore runs last so the poll builder elements above exist when a
        // drafted poll is written back into them.
        restoreDraftIfAny();

        // Modal lifecycle listeners
        const modalEl = document.getElementById('createPostModal');
        if (modalEl && modalEl.dataset.composerModalWired !== 'true') {
            modalEl.dataset.composerModalWired = 'true';
            let invokingTrigger = null;
            modalEl.addEventListener('show.bs.modal', function (e) {
                invokingTrigger = (e && e.relatedTarget) || modalEl._invokingTrigger || null;
            });
            modalEl.addEventListener('shown.bs.modal', function () {
                if (contentInput) {
                    contentInput.focus();
                }
                updateSubmitButtonState();
            });
            modalEl.addEventListener('hide.bs.modal', function (e) {
                if (isSubmitting) {
                    e.preventDefault();
                }
            });
            modalEl.addEventListener('hidden.bs.modal', function () {
                const trigger = invokingTrigger || modalEl._invokingTrigger;
                if (trigger && typeof trigger.focus === 'function') {
                    try {
                        trigger.focus();
                    } catch (_) {}
                }
                invokingTrigger = null;
                modalEl._invokingTrigger = null;
            });
        }

        // Media attachment management
        const mediaInput = document.getElementById('post-media-input');
        const selectMediaBtn = document.getElementById('btn-select-media');
        const mediaPreviewList = document.getElementById('media-preview-list');
        const mediaStatusBox = document.getElementById('media-upload-status');
        const attachedMedia = []; // Array of { id: number, element: HTMLElement }
        let pendingUploadsCount = 0;
        let pendingDeletionsCount = 0;

        function freezeMediaControls() {
            if (mediaInput) mediaInput.disabled = true;
            if (selectMediaBtn) selectMediaBtn.disabled = true;
            attachedMedia.forEach(function (m) {
                const btn = m.element.querySelector('button[title="Xóa ảnh"]');
                if (btn) btn.disabled = true;
            });
        }

        function unfreezeMediaControls() {
            if (mediaInput) mediaInput.disabled = false;
            if (selectMediaBtn) selectMediaBtn.disabled = false;
            attachedMedia.forEach(function (m) {
                const btn = m.element.querySelector('button[title="Xóa ảnh"]');
                if (btn) btn.disabled = false;
            });
        }

        if (selectMediaBtn) {
            selectMediaBtn.addEventListener('click', function () {
                if (isSubmitting || selectMediaBtn.disabled) return;
                if (mediaInput) mediaInput.click();
            });
        }

        if (mediaInput) {
            mediaInput.addEventListener('change', async function () {
                if (isSubmitting || mediaInput.disabled) return;
                if (mediaStatusBox) {
                    mediaStatusBox.textContent = '';
                    mediaStatusBox.classList.add('d-none');
                }

                const files = Array.from(mediaInput.files || []);
                if (files.length === 0) return;

                for (const file of files) {
                    const isVideo = file.type && file.type.startsWith('video/');
                    const maxBytes = isVideo ? 30 * 1024 * 1024 : 5 * 1024 * 1024;
                    const limitLabel = isVideo ? '30MB' : '5MB';
                    if (file.size > maxBytes) {
                        if (mediaStatusBox) {
                            mediaStatusBox.textContent = 'Tệp "' + file.name + '" vượt quá giới hạn ' + limitLabel + ' cho phép.';
                            mediaStatusBox.classList.remove('d-none');
                        }
                        continue;
                    }

                    // Create preview card with loading state
                    const previewCard = document.createElement('div');
                    previewCard.className = 'position-relative border rounded overflow-hidden d-inline-block';
                    previewCard.style.width = '96px';
                    previewCard.style.height = '96px';

                    let previewMedia;
                    if (isVideo) {
                        previewMedia = document.createElement('video');
                        previewMedia.src = URL.createObjectURL(file);
                        previewMedia.className = 'w-100 h-100 object-fit-cover';
                        previewMedia.muted = true;
                        previewMedia.playsInline = true;
                    } else {
                        previewMedia = document.createElement('img');
                        previewMedia.src = URL.createObjectURL(file);
                        previewMedia.alt = file.name;
                        previewMedia.className = 'w-100 h-100 object-fit-cover';
                    }
                    previewCard.appendChild(previewMedia);

                    const overlay = document.createElement('div');
                    overlay.className = 'position-absolute top-0 start-0 w-100 h-100 d-flex align-items-center justify-content-center bg-dark bg-opacity-50 text-white';
                    overlay.innerHTML = '<span class="spinner-border spinner-border-sm" role="status"></span>';
                    previewCard.appendChild(overlay);

                    if (mediaPreviewList) {
                        mediaPreviewList.appendChild(previewCard);
                    }

                    pendingUploadsCount++;

                    try {
                        const fd = new FormData();
                        fd.append('file', file);
                        fd.append('purpose', new Blob([JSON.stringify('POST')], { type: 'application/json' }));

                        const uploadRes = await fetch('/api/media', {
                            method: 'POST',
                            credentials: 'include',
                            headers: csrfHeaders(),
                            body: fd
                        });

                        if (uploadRes.status === 201) {
                            const uploadBody = await readJson(uploadRes);
                            const mediaData = uploadBody && uploadBody.data;
                            const mediaId = (mediaData && typeof mediaData.id === 'number' && Number.isSafeInteger(mediaData.id) && mediaData.id > 0) ? mediaData.id : null;

                            if (uploadBody && uploadBody.success === true && mediaId) {
                                overlay.remove();
                                const mediaItem = { id: mediaId, element: previewCard };
                                attachedMedia.push(mediaItem);

                                const removeBtn = document.createElement('button');
                                removeBtn.type = 'button';
                                removeBtn.className = 'btn btn-danger btn-sm position-absolute top-0 end-0 m-1 p-0 rounded-circle d-flex align-items-center justify-content-center';
                                removeBtn.style.width = '22px';
                                removeBtn.style.height = '22px';
                                removeBtn.innerHTML = '<i class="bi bi-x small" aria-hidden="true"></i>';
                                removeBtn.title = isVideo ? 'Xóa video' : 'Xóa ảnh';

                                removeBtn.addEventListener('click', async function (e) {
                                    e.preventDefault();
                                    e.stopPropagation();
                                    if (isSubmitting || removeBtn.disabled) return;

                                    removeBtn.disabled = true;
                                    removeBtn.innerHTML = '<span class="spinner-border spinner-border-sm text-white" style="width: 10px; height: 10px;" role="status"></span>';
                                    pendingDeletionsCount++;

                                    try {
                                        const delRes = await fetch('/api/media/' + encodeURIComponent(mediaId), {
                                            method: 'DELETE',
                                            credentials: 'include',
                                            headers: csrfHeaders()
                                        });

                                        if (delRes.status === 204) {
                                            // Strict 204 confirmation: remove from DOM and state
                                            previewCard.remove();
                                            const idx = attachedMedia.findIndex(function (m) { return m.id === mediaId; });
                                            if (idx !== -1) attachedMedia.splice(idx, 1);
                                            if (mediaStatusBox) {
                                                mediaStatusBox.textContent = '';
                                                mediaStatusBox.classList.add('d-none');
                                            }
                                        } else {
                                            // Removal failed: retain card, restore button, show retryable error
                                            removeBtn.disabled = false;
                                            removeBtn.innerHTML = '<i class="bi bi-x small" aria-hidden="true"></i>';
                                            if (mediaStatusBox) {
                                                mediaStatusBox.textContent = 'Không thể xóa ảnh (HTTP ' + delRes.status + '). Vui lòng thử lại.';
                                                mediaStatusBox.classList.remove('d-none');
                                            }
                                        }
                                    } catch (err) {
                                        // Network error: retain card, restore button, show retryable error
                                        removeBtn.disabled = false;
                                        removeBtn.innerHTML = '<i class="bi bi-x small" aria-hidden="true"></i>';
                                        if (mediaStatusBox) {
                                            mediaStatusBox.textContent = 'Lỗi kết nối khi xóa ảnh. Vui lòng thử lại.';
                                            mediaStatusBox.classList.remove('d-none');
                                        }
                                    } finally {
                                        pendingDeletionsCount--;
                                    }
                                });

                                previewCard.appendChild(removeBtn);
                                continue;
                            } else {
                                // Malformed 201 response
                                previewCard.remove();
                                if (mediaStatusBox) {
                                    mediaStatusBox.textContent = 'Phản hồi tải ảnh không hợp lệ từ máy chủ.';
                                    mediaStatusBox.classList.remove('d-none');
                                }
                            }
                        } else {
                            // Non-201 response
                            previewCard.remove();
                            const errBody = await readJson(uploadRes);
                            const errMsg = (errBody && errBody.error && errBody.error.message) || ('Không thể tải lên ảnh "' + file.name + '" (HTTP ' + uploadRes.status + ').');
                            if (mediaStatusBox) {
                                mediaStatusBox.textContent = errMsg;
                                mediaStatusBox.classList.remove('d-none');
                            }
                        }
                    } catch (err) {
                        previewCard.remove();
                        if (mediaStatusBox) {
                            mediaStatusBox.textContent = 'Lỗi kết nối khi tải lên ảnh "' + file.name + '".';
                            mediaStatusBox.classList.remove('d-none');
                        }
                    } finally {
                        pendingUploadsCount--;
                    }
                }

                // Reset file input
                mediaInput.value = '';
            });
        }

        // Character counter
        function updateCharCount() {
            const length = contentInput ? contentInput.value.length : 0;
            if (charCountEl) {
                charCountEl.textContent = length + ' / 2000';
                if (length > 2000) {
                    charCountEl.classList.add('text-danger');
                    charCountEl.classList.remove('text-muted');
                } else {
                    charCountEl.classList.remove('text-danger');
                    charCountEl.classList.add('text-muted');
                }
            }
        }

        if (contentInput) {
            contentInput.addEventListener('input', function () {
                updateCharCount();
                updateSubmitButtonState();
                scheduleDraftSave();
                if (contentInput.classList.contains('is-invalid') && contentInput.value.trim().length > 0 && contentInput.value.length <= 2000) {
                    contentInput.classList.remove('is-invalid');
                    if (contentError) contentError.classList.add('d-none');
                }
            });
            updateCharCount();
            updateSubmitButtonState();
        } else {
            updateSubmitButtonState();
        }

        if (visibilitySelect) {
            visibilitySelect.addEventListener('change', scheduleDraftSave);
        }
        if (allowReplyInput) {
            allowReplyInput.addEventListener('change', scheduleDraftSave);
        }
        if (allowQuoteInput) {
            allowQuoteInput.addEventListener('change', scheduleDraftSave);
        }

        form.addEventListener('reset', function () {
            setTimeout(function () {
                updateCharCount();
                updateSubmitButtonState();
            }, 0);
        });

        if (submitBtn && submitBtn.type !== 'submit') {
            submitBtn.addEventListener('click', function (e) {
                e.preventDefault();
                if (isSubmitting) return;
                if (form.requestSubmit) {
                    form.requestSubmit();
                } else {
                    form.dispatchEvent(new Event('submit', { cancelable: true }));
                }
            });
        }

        form.addEventListener('submit', async function (event) {
            event.preventDefault();
            if (isSubmitting) return;

            // Clear previous status
            setStatus(statusBox, null);
            if (contentError) contentError.classList.add('d-none');
            if (contentInput) contentInput.classList.remove('is-invalid');

            // Guard against pending uploads and deletions
            if (pendingUploadsCount > 0) {
                setStatus(statusBox, 'Vui lòng đợi quá trình tải ảnh hoàn tất trước khi đăng bài.', 'warning');
                return;
            }
            if (pendingDeletionsCount > 0) {
                setStatus(statusBox, 'Đang xử lý ảnh, vui lòng đợi trong giây lát trước khi đăng bài.', 'warning');
                return;
            }

            const content = contentInput ? contentInput.value.trim() : '';
            if (!content || content.length === 0) {
                if (contentInput) contentInput.classList.add('is-invalid');
                if (contentError) {
                    contentError.textContent = 'Nội dung bài viết không được để trống.';
                    contentError.classList.remove('d-none');
                }
                setStatus(statusBox, 'Nội dung bài viết không được để trống.', 'warning');
                if (contentInput) contentInput.focus();
                updateSubmitButtonState();
                return;
            }

            if (contentInput && contentInput.value.length > 2000) {
                contentInput.classList.add('is-invalid');
                if (contentError) {
                    contentError.textContent = 'Nội dung bài viết vượt quá 2000 ký tự.';
                    contentError.classList.remove('d-none');
                }
                setStatus(statusBox, 'Nội dung bài viết vượt quá tối đa 2000 ký tự.', 'warning');
                updateSubmitButtonState();
                return;
            }

            // Poll builder validation (no request leaves the browser with a malformed poll)
            const pollResult = collectPollForSubmit();
            if (!pollResult.valid) {
                setStatus(statusBox, 'Thông tin bình chọn chưa hợp lệ. Vui lòng kiểm tra lại.', 'warning');
                updateSubmitButtonState();
                return;
            }

            isSubmitting = true;
            freezeMediaControls();
            updateSubmitButtonState();

            const visibility = (visibilitySelect && visibilitySelect.value) ? visibilitySelect.value : 'PUBLIC';
            const allowReply = allowReplyInput ? allowReplyInput.checked : true;
            const allowQuote = allowQuoteInput ? allowQuoteInput.checked : true;

            const payload = {
                content: content,
                visibility: visibility,
                allowReply: allowReply,
                allowQuote: allowQuote
            };

            if (attachedMedia.length > 0) {
                payload.mediaIds = attachedMedia.map(function (m) { return m.id; });
            }

            if (pollResult.poll) {
                payload.poll = pollResult.poll;
            }

            // Loading state: disable button to prevent duplicate submissions
            const originalBtnHtml = submitBtn ? submitBtn.innerHTML : '';
            if (submitBtn) {
                submitBtn.disabled = true;
                submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span><span>Đang đăng...</span>';
            }

            try {
                const commInput = document.getElementById('post-community-id');
                const communityId = (commInput && commInput.value && /^\d+$/.test(commInput.value)) ? parseInt(commInput.value, 10) : null;
                const endpoint = communityId ? ('/api/communities/' + encodeURIComponent(communityId) + '/posts') : '/api/posts';

                const response = await fetch(endpoint, {
                    method: 'POST',
                    credentials: 'include',
                    headers: csrfHeaders({
                        'Content-Type': 'application/json',
                        'Accept': 'application/json'
                    }),
                    body: JSON.stringify(payload)
                });

                if (response.ok && (response.status === 200 || response.status === 201)) {
                    // Confirmed success: retain isSubmitting = true and keep media controls frozen through navigation
                    const body = await readJson(response);
                    // The draft has been published; make sure it never resurrects.
                    cancelPendingDraftSave();
                    clearDraftQuietly();
                    removeDraftNotice();
                    setStatus(statusBox, 'Đăng bài viết thành công!', 'success');
                    const createdPost = body && body.data;
                    const destination = (createdPost && createdPost.id) ? ('/posts/' + encodeURIComponent(createdPost.id)) : (communityId ? ('/communities/' + encodeURIComponent(communityId)) : '/feed');

                    const modalEl = document.getElementById('createPostModal');
                    if (modalEl && typeof bootstrap !== 'undefined' && bootstrap.Modal) {
                        try {
                            const inst = bootstrap.Modal.getInstance(modalEl);
                            if (inst) inst.hide();
                        } catch (_) {}
                    }
                    setTimeout(function () {
                        window.location.href = destination;
                    }, 500);
                    return;
                }

                // Non-2xx response (genuine submission failure): restore button, unfreeze media controls and preserve user input
                isSubmitting = false;
                unfreezeMediaControls();
                if (submitBtn) {
                    submitBtn.innerHTML = originalBtnHtml;
                }
                updateSubmitButtonState();

                if (response.status === 500) {
                    setStatus(statusBox, 'Hệ thống đang gặp sự cố khi xử lý bài viết (HTTP 500). Vui lòng thử lại sau.', 'danger');
                    return;
                }

                const body = await readJson(response);
                if (response.status === 401) {
                    setStatus(statusBox, 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại để đăng bài.', 'warning');
                    return;
                }

                const errorMsg = translateError(body && body.error, 'Không thể tạo bài viết (HTTP ' + response.status + '). Vui lòng thử lại.');
                setStatus(statusBox, errorMsg, 'danger');
            } catch (err) {
                // Network / unexpected error (genuine submission failure): restore button, unfreeze media controls and preserve user input
                isSubmitting = false;
                unfreezeMediaControls();
                if (submitBtn) {
                    submitBtn.innerHTML = originalBtnHtml;
                }
                updateSubmitButtonState();
                setStatus(statusBox, 'Lỗi kết nối máy chủ khi đăng bài viết. Vui lòng thử lại.', 'danger');
            }
        });
    }

    // =========================================================================
    // 2. REPLY TO POST (/posts/{id})
    // =========================================================================
    function initPostReply() {
        const form = document.getElementById('reply-form');
        if (!form) return;
        if (form.dataset.replyInitialized === 'true') return;
        form.dataset.replyInitialized = 'true';

        const postId = form.dataset.postId;
        if (!postId) return;

        let isSubmitting = false;

        const contentInput = document.getElementById('commentContent');
        const submitBtn = document.getElementById('btn-submit-reply') || form.querySelector('button[type="submit"]') || form.querySelector('[data-action="submit-reply"]');
        const charCountEl = document.getElementById('reply-char-count');
        const statusBox = document.getElementById('reply-status');
        const contentError = document.getElementById('reply-content-error');

        // Character counter
        function updateCharCount() {
            const length = contentInput ? contentInput.value.length : 0;
            if (charCountEl) {
                charCountEl.textContent = length + ' / 1000';
                if (length > 1000) {
                    charCountEl.classList.add('text-danger');
                    charCountEl.classList.remove('text-muted');
                } else {
                    charCountEl.classList.remove('text-danger');
                    charCountEl.classList.add('text-muted');
                }
            }
        }

        if (contentInput) {
            contentInput.addEventListener('input', function () {
                updateCharCount();
                if (contentInput.classList.contains('is-invalid') && contentInput.value.trim().length > 0 && contentInput.value.length <= 1000) {
                    contentInput.classList.remove('is-invalid');
                    if (contentError) contentError.classList.add('d-none');
                }
            });
            updateCharCount();
        }

        if (submitBtn && submitBtn.type !== 'submit') {
            submitBtn.addEventListener('click', function (e) {
                e.preventDefault();
                if (isSubmitting) return;
                if (form.requestSubmit) {
                    form.requestSubmit();
                } else {
                    form.dispatchEvent(new Event('submit', { cancelable: true }));
                }
            });
        }

        form.addEventListener('submit', async function (event) {
            event.preventDefault();
            if (isSubmitting) return;

            // Clear previous status
            setStatus(statusBox, null);
            if (contentError) contentError.classList.add('d-none');
            if (contentInput) contentInput.classList.remove('is-invalid');

            const content = contentInput ? contentInput.value.trim() : '';
            if (!content || content.length === 0) {
                if (contentInput) contentInput.classList.add('is-invalid');
                if (contentError) {
                    contentError.textContent = 'Nội dung bình luận không được để trống.';
                    contentError.classList.remove('d-none');
                }
                setStatus(statusBox, 'Nội dung bình luận không được để trống.', 'warning');
                if (contentInput) contentInput.focus();
                return;
            }

            if (contentInput && contentInput.value.length > 1000) {
                contentInput.classList.add('is-invalid');
                if (contentError) {
                    contentError.textContent = 'Nội dung bình luận vượt quá 1000 ký tự.';
                    contentError.classList.remove('d-none');
                }
                setStatus(statusBox, 'Nội dung bình luận vượt quá tối đa 1000 ký tự.', 'warning');
                return;
            }

            isSubmitting = true;

            // CreatePostRequest shape: content, visibility (NotNull), allowReply, allowQuote
            const payload = {
                content: content,
                visibility: 'PUBLIC',
                allowReply: true,
                allowQuote: true
            };

            const originalBtnHtml = submitBtn ? submitBtn.innerHTML : '';
            if (submitBtn) {
                submitBtn.disabled = true;
                submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span><span>Đang gửi...</span>';
            }

            try {
                const response = await fetch('/api/posts/' + encodeURIComponent(postId) + '/replies', {
                    method: 'POST',
                    credentials: 'include',
                    headers: csrfHeaders({
                        'Content-Type': 'application/json',
                        'Accept': 'application/json'
                    }),
                    body: JSON.stringify(payload)
                });

                if (response.ok && (response.status === 200 || response.status === 201)) {
                    isSubmitting = false;
                    const body = await readJson(response);
                    const reply = (body && body.data) || { content: content, createdAt: null, author: null };

                    // Restore submit button
                    if (submitBtn) {
                        submitBtn.disabled = false;
                        submitBtn.innerHTML = originalBtnHtml;
                    }

                    // Clear textarea and reset counter
                    if (contentInput) {
                        contentInput.value = '';
                        updateCharCount();
                    }

                    setStatus(statusBox, 'Đã gửi bình luận thành công!', 'success');

                    // Safe DOM insertion of the new reply
                    insertReplyCard(reply);

                    // Update reply count badges
                    incrementReplyCounters();
                    return;
                }

                // Failure: restore button, preserve input
                isSubmitting = false;
                if (submitBtn) {
                    submitBtn.disabled = false;
                    submitBtn.innerHTML = originalBtnHtml;
                }

                if (response.status === 500) {
                    setStatus(statusBox, 'Hệ thống đang gặp sự cố khi xử lý bình luận (HTTP 500). Vui lòng thử lại sau.', 'danger');
                    return;
                }

                const body = await readJson(response);
                if (response.status === 401) {
                    setStatus(statusBox, 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại để bình luận.', 'warning');
                    return;
                }

                const errorMsg = translateError(body && body.error, 'Không thể gửi bình luận (HTTP ' + response.status + '). Vui lòng thử lại.');
                setStatus(statusBox, errorMsg, 'danger');
            } catch (err) {
                isSubmitting = false;
                if (submitBtn) {
                    submitBtn.disabled = false;
                    submitBtn.innerHTML = originalBtnHtml;
                }
                setStatus(statusBox, 'Lỗi kết nối máy chủ khi gửi bình luận. Vui lòng thử lại.', 'danger');
            }
        });
    }

    function insertReplyCard(reply) {
        const emptyState = document.getElementById('replies-empty');
        if (emptyState) {
            emptyState.classList.add('d-none');
        }

        const repliesList = document.getElementById('replies-list');
        if (!repliesList) return;
        repliesList.classList.remove('d-none');

        const article = document.createElement('article');
        article.className = 'comment-card';
        if (reply.id) {
            article.dataset.replyId = String(reply.id);
        }

        const img = document.createElement('img');
        img.className = 'avatar-img avatar-sm';
        img.width = 36;
        img.height = 36;
        img.alt = 'Avatar người bình luận';
        img.src = (reply.author && reply.author.avatarUrl) ? reply.author.avatarUrl : '/images/avatar-fallback.svg';

        const body = document.createElement('div');
        body.className = 'comment-body';

        const meta = document.createElement('div');
        meta.className = 'd-flex align-items-center justify-content-between mb-1';

        const authorName = document.createElement('span');
        authorName.className = 'comment-author-name';
        authorName.textContent = (reply.author && (reply.author.displayName || reply.author.username)) || 'Sinh viên AloUTE';

        const timeSpan = document.createElement('span');
        timeSpan.className = 'text-muted small';
        timeSpan.textContent = 'Vừa xong';

        meta.appendChild(authorName);
        meta.appendChild(timeSpan);

        const textDiv = document.createElement('div');
        textDiv.className = 'comment-text';
        textDiv.textContent = reply.content || '';

        body.appendChild(meta);
        body.appendChild(textDiv);

        article.appendChild(img);
        article.appendChild(body);

        repliesList.appendChild(article);
    }

    function incrementReplyCounters() {
        const countSpan = document.getElementById('post-replies-count');
        if (countSpan) {
            const current = parseInt(countSpan.textContent, 10) || 0;
            countSpan.textContent = String(current + 1);
        }
        const barReplyBtns = document.querySelectorAll('.interaction-btn[data-action="view-comments"] .interaction-count');
        barReplyBtns.forEach(function (btnCount) {
            const val = parseInt(btnCount.textContent, 10) || 0;
            btnCount.textContent = String(val + 1);
        });
    }

    // Initialize when DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () {
            initCreatePost();
            initPostReply();
        });
    } else {
        initCreatePost();
        initPostReply();
    }
})();
