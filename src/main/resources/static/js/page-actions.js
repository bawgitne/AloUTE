/**
 * AloUTE — Page Actions & Global Interactivity Script
 * Handles CSRF-protected operations:
 * - Logout (/api/auth/logout POST)
 * - Profile Edit (/api/users/me/profile PATCH) via accessible Bootstrap Modal
 * - Follow / Unfollow (/api/social/follows/{id} PUT/DELETE) with paged initial state resolution
 * Preserves inert hooks for non-scope actions (Post, Topic).
 */
(function () {
    'use strict';

    // Accessible floating toast notification helper
    function notice(message, type) {
        let box = document.getElementById('aloute-toast-notice');
        if (!box) {
            box = document.createElement('div');
            box.id = 'aloute-toast-notice';
            box.className = 'position-fixed bottom-0 end-0 m-3 alert shadow';
            box.style.zIndex = '2050';
            box.setAttribute('role', 'status');
            box.setAttribute('aria-live', 'polite');
            document.body.appendChild(box);
        }
        box.className = 'position-fixed bottom-0 end-0 m-3 alert alert-' + (type || 'info') + ' shadow';
        box.textContent = message;
        box.hidden = false;
        clearTimeout(window.__alouteNoticeTimer);
        window.__alouteNoticeTimer = setTimeout(function () {
            box.hidden = true;
        }, 3500);
    }

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

    // Safe JSON reader
    async function readJson(response) {
        const text = await response.text();
        if (!text) return null;
        try {
            return JSON.parse(text);
        } catch (_) {
            return null;
        }
    }

    // =========================================================================
    // 1. LOGOUT FLOW
    // =========================================================================
    async function handleLogout(trigger) {
        if (!trigger) return;
        const originalHtml = trigger.innerHTML;
        trigger.setAttribute('disabled', 'true');
        trigger.classList.add('disabled');
        trigger.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span><span>Đang đăng xuất...</span>';

        try {
            const response = await fetch('/api/auth/logout', {
                method: 'POST',
                credentials: 'include',
                headers: csrfHeaders({ 'Accept': 'application/json' })
            });

            // 204 No Content expected on success; do not parse JSON
            if (response.status === 204 || response.ok) {
                window.location.href = '/login?logout=1';
                return;
            }

            trigger.removeAttribute('disabled');
            trigger.classList.remove('disabled');
            trigger.innerHTML = originalHtml;
            const body = await readJson(response);
            const msg = (body && body.error && body.error.message) || ('Đăng xuất không thành công (HTTP ' + response.status + ').');
            notice(msg, 'danger');
        } catch (error) {
            trigger.removeAttribute('disabled');
            trigger.classList.remove('disabled');
            trigger.innerHTML = originalHtml;
            notice('Lỗi kết nối tới máy chủ khi đăng xuất.', 'danger');
        }
    }

    // =========================================================================
    // 2. PROFILE EDIT FLOW (Accessible Bootstrap Modal)
    // =========================================================================
    function initProfileEditModal() {
        const form = document.getElementById('edit-profile-form');
        const modalEl = document.getElementById('editProfileModal');
        if (!form || !modalEl) return;

        const bioInput = document.getElementById('edit-bio');
        const charCount = document.getElementById('bio-char-count');
        const statusBox = document.getElementById('edit-profile-status');
        const submitBtn = document.getElementById('btn-save-profile');
        const spinner = document.getElementById('save-profile-spinner');
        const btnText = document.getElementById('save-profile-btn-text');

        function updateBioCount() {
            if (bioInput && charCount) {
                charCount.textContent = bioInput.value.length;
            }
        }

        if (bioInput) {
            bioInput.addEventListener('input', updateBioCount);
            updateBioCount();
        }

        function clearModalErrors() {
            if (statusBox) {
                statusBox.classList.add('d-none');
                statusBox.textContent = '';
            }
            form.querySelectorAll('.is-invalid').forEach(el => {
                el.classList.remove('is-invalid');
                el.setAttribute('aria-invalid', 'false');
            });
            form.querySelectorAll('input, textarea').forEach(el => {
                el.setAttribute('aria-invalid', 'false');
            });
            form.querySelectorAll('.invalid-feedback').forEach(el => {
                el.classList.add('d-none');
                el.textContent = '';
            });
        }

        modalEl.addEventListener('show.bs.modal', function () {
            clearModalErrors();
            updateBioCount();
        });

        form.addEventListener('submit', async function (event) {
            event.preventDefault();
            clearModalErrors();

            const displayNameInput = document.getElementById('edit-display-name');
            const websiteInput = document.getElementById('edit-website');
            const isPrivateInput = document.getElementById('edit-is-private');

            const displayName = displayNameInput ? displayNameInput.value.trim() : '';
            const bio = bioInput ? bioInput.value.trim() : '';
            const website = websiteInput ? websiteInput.value.trim() : '';
            const isPrivate = isPrivateInput ? isPrivateInput.checked : false;

            let hasError = false;

            // Reject empty or whitespace-only displayName before sending
            if (!displayName || displayName.length === 0) {
                if (displayNameInput) {
                    displayNameInput.classList.add('is-invalid');
                    displayNameInput.setAttribute('aria-invalid', 'true');
                }
                const err = document.getElementById('edit-displayName-error');
                if (err) {
                    err.textContent = 'Tên hiển thị không được để trống.';
                    err.classList.remove('d-none');
                }
                hasError = true;
            } else if (displayName.length > 100) {
                if (displayNameInput) {
                    displayNameInput.classList.add('is-invalid');
                    displayNameInput.setAttribute('aria-invalid', 'true');
                }
                const err = document.getElementById('edit-displayName-error');
                if (err) {
                    err.textContent = 'Tên hiển thị tối đa 100 ký tự.';
                    err.classList.remove('d-none');
                }
                hasError = true;
            }

            if (bio.length > 500) {
                if (bioInput) {
                    bioInput.classList.add('is-invalid');
                    bioInput.setAttribute('aria-invalid', 'true');
                }
                const err = document.getElementById('edit-bio-error');
                if (err) {
                    err.textContent = 'Giới thiệu ngắn tối đa 500 ký tự.';
                    err.classList.remove('d-none');
                }
                hasError = true;
            }

            if (website) {
                try {
                    const parsedUrl = new URL(website);
                    if (parsedUrl.protocol !== 'http:' && parsedUrl.protocol !== 'https:') {
                        throw new Error('Chỉ hỗ trợ giao thức http hoặc https.');
                    }
                } catch (_) {
                    if (websiteInput) {
                        websiteInput.classList.add('is-invalid');
                        websiteInput.setAttribute('aria-invalid', 'true');
                    }
                    const err = document.getElementById('edit-website-error');
                    if (err) {
                        err.textContent = 'Website phải là một URL hợp lệ bắt đầu bằng http:// hoặc https://';
                        err.classList.remove('d-none');
                    }
                    hasError = true;
                }
            }

            if (hasError) {
                const firstInvalid = form.querySelector('.is-invalid');
                if (firstInvalid) firstInvalid.focus();
                return;
            }

            // Build strictly supported DTO payload (displayName is non-blank, no avatarMediaId)
            const payload = {
                displayName: displayName,
                bio: bio || null,
                website: website || null,
                isPrivate: isPrivate
            };

            if (submitBtn) submitBtn.disabled = true;
            if (spinner) spinner.classList.remove('d-none');
            if (btnText) btnText.textContent = 'Đang lưu...';

            try {
                const response = await fetch('/api/users/me/profile', {
                    method: 'PATCH',
                    credentials: 'include',
                    headers: csrfHeaders({
                        'Content-Type': 'application/json',
                        'Accept': 'application/json'
                    }),
                    body: JSON.stringify(payload)
                });

                const body = await readJson(response);

                if (submitBtn) submitBtn.disabled = false;
                if (spinner) spinner.classList.add('d-none');
                if (btnText) btnText.textContent = 'Lưu thay đổi';

                if (response.ok && body && body.data) {
                    const updated = body.data;

                    // Update visible profile DOM in-place
                    const nameEl = document.getElementById('profile-display-name');
                    if (nameEl) nameEl.textContent = updated.displayName || updated.username || 'Sinh viên AloUTE';

                    const bioEl = document.getElementById('profile-bio-text');
                    if (bioEl) {
                        if (updated.bio) {
                            bioEl.textContent = updated.bio;
                            bioEl.classList.remove('d-none');
                        } else {
                            bioEl.textContent = '';
                            bioEl.classList.add('d-none');
                        }
                    }

                    const websiteLink = document.getElementById('profile-website-link');
                    const websiteContainer = document.getElementById('profile-website-container');
                    if (websiteLink && websiteContainer) {
                        if (updated.website) {
                            websiteLink.href = updated.website;
                            websiteLink.textContent = updated.website;
                            websiteContainer.classList.remove('d-none');
                        } else {
                            websiteLink.href = '';
                            websiteLink.textContent = '';
                            websiteContainer.classList.add('d-none');
                        }
                    }

                    let privateBadge = document.getElementById('profile-private-badge');
                    if (updated.isPrivate) {
                        if (!privateBadge) {
                            privateBadge = document.createElement('span');
                            privateBadge.id = 'profile-private-badge';
                            privateBadge.className = 'badge bg-warning-subtle text-warning border border-warning-subtle small py-1 px-2';
                            privateBadge.innerHTML = '<i class="bi bi-lock-fill me-1" aria-hidden="true"></i>Riêng tư';
                            const titleRow = document.querySelector('.profile-identity .d-flex');
                            if (titleRow) titleRow.appendChild(privateBadge);
                        } else {
                            privateBadge.classList.remove('d-none');
                        }
                    } else if (privateBadge) {
                        privateBadge.classList.add('d-none');
                    }

                    // Hide modal safely using Bootstrap instance
                    if (window.bootstrap && window.bootstrap.Modal) {
                        const modalInstance = window.bootstrap.Modal.getInstance(modalEl);
                        if (modalInstance) modalInstance.hide();
                    }

                    notice('Đã cập nhật hồ sơ cá nhân thành công!', 'success');
                    return;
                }

                if (response.status === 422 && body && body.error && body.error.fieldErrors) {
                    for (const [field, msgs] of Object.entries(body.error.fieldErrors)) {
                        const input = document.getElementById('edit-' + field) || document.getElementById('edit-display-name');
                        if (input) {
                            input.classList.add('is-invalid');
                            input.setAttribute('aria-invalid', 'true');
                        }
                        const feedback = document.getElementById('edit-' + field + '-error');
                        if (feedback) {
                            feedback.textContent = Array.isArray(msgs) ? msgs.join(', ') : String(msgs);
                            feedback.classList.remove('d-none');
                        }
                    }
                    if (statusBox) {
                        statusBox.className = 'alert alert-danger py-2 small mb-3';
                        statusBox.textContent = body.error.message || 'Dữ liệu chỉnh sửa không hợp lệ.';
                        statusBox.classList.remove('d-none');
                    }
                } else {
                    const msg = (body && body.error && body.error.message) || ('Cập nhật thất bại (HTTP ' + response.status + ').');
                    if (statusBox) {
                        statusBox.className = 'alert alert-danger py-2 small mb-3';
                        statusBox.textContent = msg;
                        statusBox.classList.remove('d-none');
                    }
                }
            } catch (error) {
                if (submitBtn) submitBtn.disabled = false;
                if (spinner) spinner.classList.add('d-none');
                if (btnText) btnText.textContent = 'Lưu thay đổi';
                if (statusBox) {
                    statusBox.className = 'alert alert-danger py-2 small mb-3';
                    statusBox.textContent = 'Lỗi kết nối khi cập nhật hồ sơ.';
                    statusBox.classList.remove('d-none');
                }
            }
        });
    }

    // =========================================================================
    // 3. FOLLOW / UNFOLLOW FLOW (Paged traversal for initial state)
    // =========================================================================
    async function resolveFollowStatus(currentUserId, targetUserId) {
        let page = 0;
        const size = 100;
        let isFollowing = false;

        while (true) {
            const resp = await fetch('/api/users/' + encodeURIComponent(currentUserId) + '/following?page=' + page + '&size=' + size, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Accept': 'application/json' }
            });

            if (!resp.ok) {
                throw new Error('HTTP ' + resp.status);
            }

            const body = await resp.json();
            const list = (body && body.data) || [];

            if (list.some(function (item) { return String(item.id) === String(targetUserId); })) {
                isFollowing = true;
                break;
            }

            if (!body.meta || !body.meta.hasNext) {
                break;
            }
            page++;
        }

        return isFollowing;
    }

    function renderFollowState(btn, isFollowing) {
        btn.setAttribute('aria-pressed', isFollowing ? 'true' : 'false');
        btn.disabled = false;
        btn.dataset.state = 'ready';

        if (isFollowing) {
            btn.className = 'btn btn-sm btn-outline-primary aloute-follow-btn';
            btn.innerHTML = '<i class="bi bi-person-check me-1" aria-hidden="true"></i><span>Đang theo dõi</span>';
        } else {
            btn.className = 'btn btn-sm btn-primary aloute-follow-btn';
            btn.innerHTML = '<i class="bi bi-person-plus me-1" aria-hidden="true"></i><span>Theo dõi</span>';
        }
    }

    function renderFollowError(btn) {
        btn.disabled = false;
        btn.dataset.state = 'error';
        btn.className = 'btn btn-sm btn-outline-warning aloute-follow-btn';
        btn.innerHTML = '<i class="bi bi-arrow-clockwise me-1" aria-hidden="true"></i><span>Lỗi tải. Thử lại</span>';
    }

    async function initFollowButton() {
        const btn = document.getElementById('btn-follow');
        if (!btn) return;

        const targetUserId = btn.dataset.userId;
        const currentUserId = btn.dataset.currentUserId;

        if (!targetUserId || !currentUserId || String(targetUserId) === String(currentUserId)) {
            btn.style.display = 'none';
            return;
        }

        btn.disabled = true;
        btn.dataset.state = 'resolving';
        btn.className = 'btn btn-sm btn-primary aloute-follow-btn disabled';
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span><span>Đang tải...</span>';

        try {
            const isFollowing = await resolveFollowStatus(currentUserId, targetUserId);
            renderFollowState(btn, isFollowing);
        } catch (error) {
            renderFollowError(btn);
        }
    }

    async function handleFollowToggle(btn) {
        if (!btn || btn.disabled) return;

        if (btn.dataset.state === 'error') {
            await initFollowButton();
            return;
        }

        const targetUserId = btn.dataset.userId;
        const isCurrentlyFollowing = btn.getAttribute('aria-pressed') === 'true';

        btn.disabled = true;

        if (isCurrentlyFollowing) {
            // Unfollow flow (DELETE)
            btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span><span>Đang hủy...</span>';

            try {
                const response = await fetch('/api/social/follows/' + encodeURIComponent(targetUserId), {
                    method: 'DELETE',
                    credentials: 'include',
                    headers: csrfHeaders({ 'Accept': 'application/json' })
                });

                // 204 No Content expected on successful deletion; do not parse body
                if (response.status === 204 || response.ok) {
                    renderFollowState(btn, false);
                    const followersCountEl = document.getElementById('profile-followers-count');
                    if (followersCountEl) {
                        const current = parseInt(followersCountEl.textContent, 10) || 0;
                        followersCountEl.textContent = String(Math.max(0, current - 1));
                    }
                    notice('Đã hủy theo dõi người dùng.', 'info');
                    return;
                }

                const body = await readJson(response);
                renderFollowState(btn, true);
                const msg = (body && body.error && body.error.message) || ('Hủy theo dõi thất bại (HTTP ' + response.status + ').');
                notice(msg, 'danger');
            } catch (error) {
                renderFollowState(btn, true);
                notice('Lỗi kết nối khi hủy theo dõi.', 'danger');
            }
        } else {
            // Follow flow (PUT)
            btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span><span>Đang theo dõi...</span>';

            try {
                const response = await fetch('/api/social/follows/' + encodeURIComponent(targetUserId), {
                    method: 'PUT',
                    credentials: 'include',
                    headers: csrfHeaders({ 'Accept': 'application/json' })
                });

                const body = await readJson(response);

                if (response.ok && (response.status === 200 || response.status === 201)) {
                    renderFollowState(btn, true);
                    const followersCountEl = document.getElementById('profile-followers-count');
                    if (followersCountEl) {
                        const current = parseInt(followersCountEl.textContent, 10) || 0;
                        followersCountEl.textContent = String(current + 1);
                    }
                    notice('Đã theo dõi người dùng thành công!', 'success');
                    return;
                }

                if (response.status === 409) {
                    const code = body && body.error && body.error.code;
                    const msg = (body && body.error && body.error.message) || '';
                    if (code === 'PRIVATE_FOLLOW_REQUEST_NOT_IMPLEMENTED') {
                        renderFollowState(btn, false);
                        notice('Tài khoản này là riêng tư. Chức năng gửi yêu cầu theo dõi tài khoản riêng tư chưa được hỗ trợ.', 'warning');
                    } else if (code === 'FOLLOW_ALREADY_EXISTS') {
                        renderFollowState(btn, true);
                        notice('Bạn đã theo dõi người dùng này rồi.', 'info');
                    } else if (code === 'FOLLOW_SELF_NOT_ALLOWED') {
                        btn.style.display = 'none';
                        notice('Bạn không thể tự theo dõi chính mình.', 'warning');
                    } else {
                        renderFollowState(btn, false);
                        notice(msg || 'Không thể theo dõi người dùng này.', 'danger');
                    }
                } else {
                    renderFollowState(btn, false);
                    const msg = (body && body.error && body.error.message) || ('Theo dõi thất bại (HTTP ' + response.status + ').');
                    notice(msg, 'danger');
                }
            } catch (error) {
                renderFollowState(btn, false);
                notice('Lỗi kết nối khi thực hiện theo dõi.', 'danger');
            }
        }
    }

    // =========================================================================
    // 4. GLOBAL ACTION DELEGATION
    // =========================================================================
    document.addEventListener('click', async function (event) {
        const target = event.target.closest('[data-action]');
        if (!target) return;

        const action = target.dataset.action;

        // Semantic page navigations
        if (action === 'navigate-login') {
            event.preventDefault();
            window.location.href = '/login';
            return;
        }
        if (action === 'navigate-register') {
            event.preventDefault();
            window.location.href = '/register';
            return;
        }
        if (action === 'navigate-create-post') {
            event.preventDefault();
            window.location.href = '/post/create';
            return;
        }
        if (action === 'navigate-profile') {
            const href = target.getAttribute('href');
            if (href && href !== '#') {
                event.preventDefault();
                window.location.href = href;
            }
            return;
        }

        // Authenticated actions
        if (action === 'logout') {
            event.preventDefault();
            await handleLogout(target);
            return;
        }

        if (action === 'toggle-follow') {
            event.preventDefault();
            await handleFollowToggle(target);
            return;
        }

        // Edit profile button is wired via Bootstrap data-bs-toggle="modal", handled natively
        if (action === 'edit-profile') {
            return;
        }

        // Preserved inert non-scope actions
        event.preventDefault();
        const nonScopeLabels = {
            'create-post': 'API tạo bài viết thuộc module Post chưa được ghép.',
            'submit-reply': 'API Reply thuộc module Post chưa được ghép.',
            'edit-post': 'API sửa bài viết chưa được ghép.',
            'delete-post': 'API xóa bài viết chưa được ghép.',
            'topic': 'Topic/Community thuộc module Ngọc chưa được ghép.'
        };
        notice(nonScopeLabels[action] || ('Nút hoạt động. Chức năng "' + action + '" chưa được ghép trong phạm vi hiện tại.'));
    });

    // Initialize interactive components when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () {
            initProfileEditModal();
            initFollowButton();
        });
    } else {
        initProfileEditModal();
        initFollowButton();
    }

})();
