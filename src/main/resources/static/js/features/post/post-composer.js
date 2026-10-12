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

        let isSubmitting = false;

        const contentInput = document.getElementById('postContent');
        const visibilitySelect = document.getElementById('visibility');
        const allowReplyInput = document.getElementById('allowReply');
        const allowQuoteInput = document.getElementById('allowQuote');
        const submitBtn = document.getElementById('btn-create-post') || form.querySelector('button[type="submit"]') || form.querySelector('[data-action="create-post"]');
        const charCountEl = document.getElementById('char-count');
        const statusBox = document.getElementById('create-post-status');
        const contentError = document.getElementById('post-content-error');

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
                    if (file.size > 5 * 1024 * 1024) {
                        if (mediaStatusBox) {
                            mediaStatusBox.textContent = 'Tệp "' + file.name + '" vượt quá giới hạn 5MB cho phép.';
                            mediaStatusBox.classList.remove('d-none');
                        }
                        continue;
                    }

                    // Create preview card with loading state
                    const previewCard = document.createElement('div');
                    previewCard.className = 'position-relative border rounded overflow-hidden d-inline-block';
                    previewCard.style.width = '96px';
                    previewCard.style.height = '96px';

                    const img = document.createElement('img');
                    img.src = URL.createObjectURL(file);
                    img.alt = file.name;
                    img.className = 'w-100 h-100 object-fit-cover';
                    previewCard.appendChild(img);

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
                                removeBtn.title = 'Xóa ảnh';

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
                if (contentInput.classList.contains('is-invalid') && contentInput.value.trim().length > 0 && contentInput.value.length <= 2000) {
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
                return;
            }

            if (contentInput && contentInput.value.length > 2000) {
                contentInput.classList.add('is-invalid');
                if (contentError) {
                    contentError.textContent = 'Nội dung bài viết vượt quá 2000 ký tự.';
                    contentError.classList.remove('d-none');
                }
                setStatus(statusBox, 'Nội dung bài viết vượt quá tối đa 2000 ký tự.', 'warning');
                return;
            }

            isSubmitting = true;
            freezeMediaControls();

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

            // Loading state: disable button to prevent duplicate submissions
            const originalBtnHtml = submitBtn ? submitBtn.innerHTML : '';
            if (submitBtn) {
                submitBtn.disabled = true;
                submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span><span>Đang đăng...</span>';
            }

            try {
                const response = await fetch('/api/posts', {
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
                    setStatus(statusBox, 'Đăng bài viết thành công!', 'success');
                    const createdPost = body && body.data;
                    const destination = (createdPost && createdPost.id) ? ('/posts/' + encodeURIComponent(createdPost.id)) : '/feed';
                    setTimeout(function () {
                        window.location.href = destination;
                    }, 500);
                    return;
                }

                // Non-2xx response (genuine submission failure): restore button, unfreeze media controls and preserve user input
                isSubmitting = false;
                unfreezeMediaControls();
                if (submitBtn) {
                    submitBtn.disabled = false;
                    submitBtn.innerHTML = originalBtnHtml;
                }

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
                    submitBtn.disabled = false;
                    submitBtn.innerHTML = originalBtnHtml;
                }
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
