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

    if (typeof window !== 'undefined' && window.__aloutePageActionsInitialized) return;
    if (typeof window !== 'undefined') window.__aloutePageActionsInitialized = true;

    // Keyed mutation locks to prevent race conditions & duplicate API submissions
    const pendingLocks = new Set();
    function acquireLock(key) {
        if (pendingLocks.has(key)) return false;
        pendingLocks.add(key);
        return true;
    }
    function releaseLock(key) {
        pendingLocks.delete(key);
    }

    // Modal freeze helpers to prevent backdrop/escape/cancel dismissal during active mutations
    function freezeModal(modalEl, isFrozen) {
        if (!modalEl) return;
        modalEl.dataset.isFrozen = isFrozen ? 'true' : 'false';
        const closeButtons = modalEl.querySelectorAll('[data-bs-dismiss="modal"], .btn-close');
        closeButtons.forEach(btn => {
            if (isFrozen) {
                btn.setAttribute('disabled', 'true');
            } else {
                btn.removeAttribute('disabled');
            }
        });
    }

    function attachModalFreezeProtection(modalEl) {
        if (!modalEl || modalEl.dataset.freezeAttached) return;
        modalEl.dataset.freezeAttached = 'true';
        modalEl.addEventListener('hide.bs.modal', function (e) {
            if (modalEl.dataset.isFrozen === 'true') {
                e.preventDefault();
                e.stopPropagation();
            }
        });
    }

    // Modal session and lifecycle tracking to prevent stale callbacks and transition races
    let reportModalSession = 0;
    let quoteModalSession = 0;
    let reportFormState = 'idle'; // 'idle' | 'pending' | 'confirmed' | 'closing'
    const reportedTargetsInSession = new Set();
    let quoteFormState = 'idle'; // 'idle' | 'pending' | 'confirmed-navigating'

    let reportModalLifecycle = {
        isShowing: false,
        isShown: false,
        isClosing: false,
        dismissOnShown: false,
        queuedOpen: null,
        activeSession: 0
    };

    let quoteModalLifecycle = {
        isShowing: false,
        isShown: false,
        dismissOnShown: false
    };

    // Positive safe-integer scalar check (strictly no coercion from boolean, string, array)
    function isPositiveSafeInteger(val) {
        return typeof val === 'number' && Number.isInteger(val) && val > 0 && Number.isSafeInteger(val);
    }

    // SSR authenticated viewer ID helper (strict canonical safe integer, no prefix acceptance)
    function getCurrentUserId() {
        const raw = (typeof document !== 'undefined' && document.body && document.body.dataset && document.body.dataset.currentUserId) ||
            (typeof document !== 'undefined' && document.querySelector('[data-current-user-id]') && document.querySelector('[data-current-user-id]').getAttribute('data-current-user-id'));
        if (typeof raw !== 'string') return null;
        const trimmed = raw.trim();
        if (!/^[1-9]\d*$/.test(trimmed)) return null;
        const parsed = Number(trimmed);
        return isPositiveSafeInteger(parsed) ? parsed : null;
    }

    // Shared transport accessor: requires window.alouteHttp with .request
    function getHttp() {
        if (typeof window !== 'undefined' && window.alouteHttp && typeof window.alouteHttp.request === 'function') {
            return window.alouteHttp;
        }
        return null;
    }

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

    // Validate that a conversation ID is a positive safe integer
    function isValidConversationId(id) {
        if (typeof id === 'number') {
            return Number.isSafeInteger(id) && id > 0;
        }
        if (typeof id === 'string') {
            return /^[1-9]\d*$/.test(id) && Number.isSafeInteger(Number(id));
        }
        return false;
    }

    // Safely translate stable backend error codes/messages to Vietnamese
    function translateError(error, defaultMsg) {
        if (!error) return defaultMsg || 'Đã xảy ra lỗi.';
        if (window.console && typeof window.console.warn === 'function') {
            window.console.warn('[AloUTE] Backend error diagnostic:', error);
        }
        const code = error.code;
        const message = error.message;

        if (code === 'AUTH_INVALID_CREDENTIALS') return 'Email hoặc mật khẩu không chính xác.';
        if (code === 'AUTH_FORBIDDEN') return 'Bạn không có quyền thực hiện thao tác này.';
        if (code === 'AUTH_TOKEN_EXPIRED') return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
        if (code === 'USER_NOT_FOUND') return 'Không tìm thấy thông tin người dùng.';
        if (code === 'USER_INACTIVE') return 'Tài khoản người dùng đã bị khóa hoặc chưa kích hoạt.';
        if (code === 'USER_USERNAME_ALREADY_EXISTS') return 'Tên người dùng đã tồn tại.';
        if (code === 'USER_EMAIL_ALREADY_EXISTS') return 'Địa chỉ email đã được sử dụng.';
        if (code === 'VALIDATION_FAILED') return 'Dữ liệu không hợp lệ. Vui lòng kiểm tra lại.';
        if (code === 'PRIVATE_FOLLOW_REQUEST_NOT_IMPLEMENTED') return 'Chức năng gửi yêu cầu theo dõi tài khoản riêng tư chưa được hỗ trợ.';
        if (code === 'FOLLOW_ALREADY_EXISTS' || code === 'SOCIAL_FOLLOW_ALREADY_EXISTS') return 'Bạn đã theo dõi người dùng này rồi.';
        if (code === 'FOLLOW_SELF_NOT_ALLOWED' || code === 'SOCIAL_FOLLOW_SELF_NOT_ALLOWED') return 'Bạn không thể tự theo dõi chính mình.';
        if (code === 'INTERNAL_SERVER_ERROR') return 'Hệ thống đang gặp sự cố. Vui lòng thử lại sau.';
        if (code === 'ACCESS_DENIED') return 'Bạn không có quyền thực hiện thao tác này.';
        if (code === 'COMMUNITY_NOT_FOUND') return 'Không tìm thấy cộng đồng.';
        if (code === 'COMMUNITY_NAME_DUPLICATED' || code === 'COMMUNITY_NAME_ALREADY_EXISTS') return 'Tên cộng đồng đã được sử dụng.';
        if (code === 'COMMUNITY_SLUG_DUPLICATED' || code === 'COMMUNITY_SLUG_ALREADY_EXISTS') return 'Đường dẫn (slug) cộng đồng đã được sử dụng.';
        if (code === 'COMMUNITY_MEMBER_ALREADY_EXISTS') return 'Bạn đã là thành viên của cộng đồng này.';
        if (code === 'COMMUNITY_MEMBER_NOT_FOUND') return 'Bạn chưa tham gia cộng đồng này.';
        if (code === 'OWNER_CANNOT_LEAVE' || code === 'COMMUNITY_OWNER_CANNOT_LEAVE') return 'Chủ sở hữu không thể rời cộng đồng khi chưa chuyển quyền sở hữu.';
        if (code === 'POST_NOT_FOUND') return 'Bài viết không tồn tại.';
        if (code === 'REPORT_ALREADY_EXISTS') return 'Bạn đã gửi báo cáo cho nội dung này trước đó.';

        // Safe translations for known English messages
        if (message === 'Invalid email or password' || message === 'Invalid credentials' || message === 'Bad credentials') {
            return 'Email hoặc mật khẩu không chính xác.';
        }
        if (message === 'Operation is not permitted' || message === 'Access is denied' || message === 'Access Denied') {
            return 'Bạn không có quyền thực hiện thao tác này.';
        }
        if (message === 'User not found') {
            return 'Không tìm thấy thông tin người dùng.';
        }
        if (message === 'Validation failed') {
            return 'Dữ liệu không hợp lệ. Vui lòng kiểm tra lại.';
        }

        // Generic Vietnamese status fallback, suppressing raw unknown English/technical messages
        return defaultMsg || 'Đã xảy ra lỗi không xác định. Vui lòng thử lại sau.';
    }

    // Safely translate raw profile field errors to Vietnamese with generic fallbacks
    function translateProfileFieldError(field, rawMsgs) {
        if (window.console && typeof window.console.warn === 'function') {
            window.console.warn('[AloUTE] Profile validation error diagnostic:', field, rawMsgs);
        }
        const text = (Array.isArray(rawMsgs) ? rawMsgs.join(' ') : String(rawMsgs || '')).toLowerCase();

        // Pattern matching for standard Spring / Hibernate validator messages
        if (text.includes('blank') || text.includes('empty') || text.includes('không được để trống') || text.includes('required')) {
            if (field === 'displayName') return 'Tên hiển thị không được để trống.';
            return 'Trường này không được để trống.';
        }

        if (text.includes('size') || text.includes('length') || text.includes('ký tự')) {
            if (field === 'displayName') return 'Tên hiển thị tối đa 100 ký tự.';
            if (field === 'bio') return 'Giới thiệu ngắn tối đa 500 ký tự.';
            return 'Độ dài trường dữ liệu không hợp lệ.';
        }

        if (text.includes('url') || text.includes('http') || text.includes('protocol')) {
            if (field === 'website') return 'Website phải là một URL hợp lệ bắt đầu bằng http:// hoặc https://';
        }

        // Generic Vietnamese field fallbacks
        const genericFallbacks = {
            'displayName': 'Tên hiển thị không hợp lệ.',
            'bio': 'Giới thiệu ngắn không hợp lệ.',
            'website': 'Địa chỉ website không hợp lệ.',
            'isPrivate': 'Cài đặt riêng tư không hợp lệ.'
        };

        return genericFallbacks[field] || 'Dữ liệu không hợp lệ.';
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
            const msg = translateError(body && body.error, 'Đăng xuất không thành công (HTTP ' + response.status + ').');
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
                            feedback.textContent = translateProfileFieldError(field, msgs);
                            feedback.classList.remove('d-none');
                        }
                    }
                    if (statusBox) {
                        statusBox.className = 'alert alert-danger py-2 small mb-3';
                        statusBox.textContent = translateError(body.error, 'Dữ liệu chỉnh sửa không hợp lệ.');
                        statusBox.classList.remove('d-none');
                    }
                } else {
                    const msg = translateError(body && body.error, 'Cập nhật thất bại (HTTP ' + response.status + ').');
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
                const msg = translateError(body && body.error, 'Hủy theo dõi thất bại (HTTP ' + response.status + ').');
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
                    if (code === 'PRIVATE_FOLLOW_REQUEST_NOT_IMPLEMENTED') {
                        renderFollowState(btn, false);
                        notice('Tài khoản này là riêng tư. Chức năng gửi yêu cầu theo dõi tài khoản riêng tư chưa được hỗ trợ.', 'warning');
                    } else if (code === 'FOLLOW_ALREADY_EXISTS' || code === 'SOCIAL_FOLLOW_ALREADY_EXISTS') {
                        renderFollowState(btn, true);
                        notice('Bạn đã theo dõi người dùng này rồi.', 'info');
                    } else if (code === 'FOLLOW_SELF_NOT_ALLOWED' || code === 'SOCIAL_FOLLOW_SELF_NOT_ALLOWED') {
                        btn.style.display = 'none';
                        notice('Bạn không thể tự theo dõi chính mình.', 'warning');
                    } else {
                        renderFollowState(btn, false);
                        const msg = translateError(body && body.error, 'Không thể theo dõi người dùng này.');
                        notice(msg, 'danger');
                    }
                } else {
                    renderFollowState(btn, false);
                    const msg = translateError(body && body.error, 'Theo dõi thất bại (HTTP ' + response.status + ').');
                    notice(msg, 'danger');
                }
            } catch (error) {
                renderFollowState(btn, false);
                notice('Lỗi kết nối khi thực hiện theo dõi.', 'danger');
            }
        }
    }

    // =========================================================================
    // Profile Actions: Mute, Block, Restrict, Copy Link
    // =========================================================================
    let pendingProfileBlockTarget = null;
    let pendingProfileMuteTarget = null;

    async function handleMuteUserToggle(btn) {
        if (!btn || btn.disabled) return;
        const userId = btn.dataset.userId;
        const username = btn.dataset.username || 'user';
        const isMuted = btn.dataset.muted === 'true';

        if (!isMuted) {
            pendingProfileMuteTarget = { userId, username, btn };
            const usernameEl = document.getElementById('modal-mute-profile-username');
            if (usernameEl) usernameEl.textContent = '@' + username;
            const modalEl = document.getElementById('modal-confirm-mute-profile');
            if (modalEl && typeof bootstrap !== 'undefined') {
                const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
                modal.show();
            } else {
                await executeMute(userId, username, btn, true);
            }
        } else {
            await executeMute(userId, username, btn, false);
        }
    }

    async function executeMute(userId, username, btn, shouldMute) {
        const http = getHttp();
        if (!http) {
            notice('Lỗi kết nối ứng dụng.', 'danger');
            return;
        }
        btn.disabled = true;
        try {
            const url = '/api/social/mutes/' + encodeURIComponent(userId);
            const method = shouldMute ? 'PUT' : 'DELETE';
            const response = await http.request(url, { method: method });
            if (response.ok || response.status === 204) {
                btn.dataset.muted = shouldMute ? 'true' : 'false';
                const textEl = document.getElementById('text-profile-mute');
                const iconEl = document.getElementById('icon-profile-mute');
                if (textEl) textEl.textContent = shouldMute ? 'Bật lại thông báo' : 'Tắt thông báo';
                if (iconEl) {
                    iconEl.className = shouldMute ? 'bi bi-bell text-primary me-2' : 'bi bi-bell-slash text-secondary me-2';
                }
                const banner = document.getElementById('banner-user-muted');
                if (banner) {
                    if (shouldMute) banner.classList.remove('d-none');
                    else banner.classList.add('d-none');
                }
                notice(shouldMute ? ('Đã tắt thông báo của @' + username + '.') : ('Đã bật lại thông báo cho @' + username + '.'), 'success');
            } else {
                notice('Không thể thực hiện thao tác tắt thông báo.', 'danger');
            }
        } catch (_) {
            notice('Lỗi kết nối khi tắt thông báo.', 'danger');
        } finally {
            btn.disabled = false;
        }
    }

    async function handleBlockUserToggle(btn) {
        if (!btn || btn.disabled) return;
        const userId = btn.dataset.userId;
        const username = btn.dataset.username || 'user';
        const isBlocked = btn.dataset.blocked === 'true';

        if (!isBlocked) {
            pendingProfileBlockTarget = { userId, username, btn };
            const usernameEl = document.getElementById('modal-block-profile-username');
            if (usernameEl) usernameEl.textContent = '@' + username;
            const modalEl = document.getElementById('modal-confirm-block-profile');
            if (modalEl && typeof bootstrap !== 'undefined') {
                const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
                modal.show();
            } else {
                await executeBlock(userId, username, btn, true);
            }
        } else {
            await executeBlock(userId, username, btn, false);
        }
    }

    async function executeBlock(userId, username, btn, shouldBlock) {
        const http = getHttp();
        if (!http) {
            notice('Lỗi kết nối ứng dụng.', 'danger');
            return;
        }
        btn.disabled = true;
        try {
            const url = '/api/social/blocks/' + encodeURIComponent(userId);
            const method = shouldBlock ? 'PUT' : 'DELETE';
            const response = await http.request(url, { method: method });
            if (response.ok || response.status === 204) {
                btn.dataset.blocked = shouldBlock ? 'true' : 'false';
                const textEl = document.getElementById('text-profile-block');
                const iconEl = document.getElementById('icon-profile-block');
                if (textEl) textEl.textContent = shouldBlock ? 'Bỏ chặn tài khoản' : 'Chặn người dùng';
                if (iconEl) {
                    iconEl.className = shouldBlock ? 'bi bi-shield-check text-success me-2' : 'bi bi-slash-circle text-danger me-2';
                }
                const banner = document.getElementById('banner-user-blocked');
                if (banner) {
                    if (shouldBlock) banner.classList.remove('d-none');
                    else banner.classList.add('d-none');
                }
                notice(shouldBlock ? ('Đã chặn người dùng @' + username + '.') : ('Đã bỏ chặn người dùng @' + username + '.'), shouldBlock ? 'warning' : 'success');
            } else {
                notice('Không thể thực hiện chặn người dùng.', 'danger');
            }
        } catch (_) {
            notice('Lỗi kết nối khi cập nhật chặn người dùng.', 'danger');
        } finally {
            btn.disabled = false;
        }
    }

    async function handleRestrictUserToggle(btn) {
        if (!btn || btn.disabled) return;
        const userId = btn.dataset.userId;
        const username = btn.dataset.username || 'user';
        const isRestricted = btn.dataset.restricted === 'true';
        const http = getHttp();
        if (!http) return;
        btn.disabled = true;
        try {
            const url = '/api/social/restrictions/' + encodeURIComponent(userId);
            const method = isRestricted ? 'DELETE' : 'PUT';
            const response = await http.request(url, { method: method });
            if (response.ok || response.status === 204) {
                const nextState = !isRestricted;
                btn.dataset.restricted = nextState ? 'true' : 'false';
                const textEl = document.getElementById('text-profile-restrict');
                const iconEl = document.getElementById('icon-profile-restrict');
                if (textEl) textEl.textContent = nextState ? 'Bỏ hạn chế' : 'Hạn chế tương tác';
                if (iconEl) {
                    iconEl.className = nextState ? 'bi bi-eye text-primary me-2' : 'bi bi-eye-slash text-secondary me-2';
                }
                notice(nextState ? ('Đã hạn chế tương tác với @' + username + '.') : ('Đã hủy hạn chế tương tác với @' + username + '.'), 'info');
            } else {
                notice('Không thể thay đổi trạng thái hạn chế.', 'danger');
            }
        } catch (_) {
            notice('Lỗi kết nối khi cập nhật hạn chế.', 'danger');
        } finally {
            btn.disabled = false;
        }
    }

    async function handleCopyProfileLink(btn) {
        const username = btn.dataset.username || '';
        const url = window.location.origin + '/users/' + encodeURIComponent(username);
        try {
            if (navigator.clipboard && navigator.clipboard.writeText) {
                await navigator.clipboard.writeText(url);
            } else {
                const tempInput = document.createElement('input');
                tempInput.value = url;
                document.body.appendChild(tempInput);
                tempInput.select();
                document.execCommand('copy');
                document.body.removeChild(tempInput);
            }
            notice('Đã sao chép liên kết trang cá nhân vào clipboard!', 'success');
        } catch (_) {
            notice('Không thể sao chép liên kết.', 'warning');
        }
    }

    function initProfileMoreActions() {
        const btnModalBlock = document.getElementById('btn-modal-confirm-block');
        if (btnModalBlock) {
            btnModalBlock.addEventListener('click', async function () {
                if (!pendingProfileBlockTarget) return;
                const { userId, username, btn } = pendingProfileBlockTarget;
                const modalEl = document.getElementById('modal-confirm-block-profile');
                if (modalEl && typeof bootstrap !== 'undefined') {
                    const modal = bootstrap.Modal.getInstance(modalEl);
                    if (modal) modal.hide();
                }
                await executeBlock(userId, username, btn, true);
            });
        }

        const btnModalMute = document.getElementById('btn-modal-confirm-mute');
        if (btnModalMute) {
            btnModalMute.addEventListener('click', async function () {
                if (!pendingProfileMuteTarget) return;
                const { userId, username, btn } = pendingProfileMuteTarget;
                const modalEl = document.getElementById('modal-confirm-mute-profile');
                if (modalEl && typeof bootstrap !== 'undefined') {
                    const modal = bootstrap.Modal.getInstance(modalEl);
                    if (modal) modal.hide();
                }
                await executeMute(userId, username, btn, true);
            });
        }
    }

    // Report Modal Open Target (module-scoped for both click handler and hidden.bs.modal listener)
    function openReportTarget(reportType, reportedId, reportedName) {
        if (isPositiveSafeInteger(reportedId)) {
            const targetKey = (reportType === 'user' ? 'user' : 'post') + ':' + reportedId;
            if (reportedTargetsInSession.has(targetKey)) {
                notice('Bạn đã báo cáo đối tượng này trong phiên làm việc hiện tại.', 'info');
                return;
            }
        }
        reportFormState = 'idle';
        reportModalLifecycle.isClosing = false;
        reportModalLifecycle.dismissOnShown = false;
        reportModalSession++;
        reportModalLifecycle.activeSession = reportModalSession;

        const modalEl = document.getElementById('reportModal');
        const typeInput = document.getElementById('report-target-type');
        const idInput = document.getElementById('report-target-id');
        const previewEl = document.getElementById('report-target-preview');
        const reasonSelect = document.getElementById('report-reason');
        const descEl = document.getElementById('report-description');
        const descChar = document.getElementById('report-char-count');
        const statusEl = document.getElementById('report-status');
        const reasonErr = document.getElementById('report-reason-error');
        const submitBtn = document.getElementById('btn-submit-report');
        const spinner = document.getElementById('report-spinner');
        const btnText = document.getElementById('report-btn-text');

        if (typeInput) typeInput.value = reportType;
        if (idInput) idInput.value = isPositiveSafeInteger(reportedId) ? String(reportedId) : '';
        if (previewEl) {
            previewEl.textContent = reportType === 'user'
                ? ('Người dùng: ' + (reportedName || ('ID #' + reportedId)))
                : ('Bài viết #' + reportedId + (reportedName ? (' (' + reportedName + ')') : ''));
        }
        if (reasonSelect) {
            reasonSelect.value = '';
            reasonSelect.classList.remove('is-invalid');
        }
        if (descEl) descEl.value = '';
        if (descChar) descChar.textContent = '0';
        if (statusEl) statusEl.className = 'alert d-none py-2 small mb-3';
        if (reasonErr) reasonErr.classList.add('d-none');
        if (submitBtn) submitBtn.removeAttribute('disabled');
        if (spinner) spinner.classList.add('d-none');
        if (btnText) btnText.textContent = 'Gửi báo cáo';

        if (modalEl && window.bootstrap && window.bootstrap.Modal) {
            const modal = window.bootstrap.Modal.getOrCreateInstance(modalEl);
            modal.show();
        }
    }

    // =========================================================================
    // 4. GLOBAL ACTION DELEGATION
    // =========================================================================
    document.addEventListener('click', async function (event) {
        const target = event.target.closest('[data-action]');
        if (!target) return;

        // Do not intercept natural link navigation if the clicked element is an anchor without data-action
        const closestAnchor = event.target.closest('a');
        if (closestAnchor && !closestAnchor.dataset.action) {
            const href = closestAnchor.getAttribute('href');
            if (href && href !== '#' && !href.startsWith('javascript:')) {
                return;
            }
        }

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
            const modalEl = document.getElementById('createPostModal');
            const bs = (typeof window !== 'undefined' && window.bootstrap) || (typeof bootstrap !== 'undefined' ? bootstrap : null);
            if (modalEl && bs && bs.Modal) {
                event.preventDefault();
                const commId = target.getAttribute('data-community-id') ||
                               (document.querySelector('[data-community-id]') ? document.querySelector('[data-community-id]').getAttribute('data-community-id') : null);
                const commInput = document.getElementById('post-community-id');
                if (commInput) {
                    commInput.value = (commId && /^\d+$/.test(commId)) ? commId : '';
                }
                const modal = bs.Modal.getInstance(modalEl) || new bs.Modal(modalEl);
                modalEl._invokingTrigger = target;
                modal.show(target);
                return;
            }
            // Fallback when #createPostModal is absent (e.g. on /post/create where modal is omitted) or Bootstrap modal is unavailable
            event.preventDefault();
            const href = target.getAttribute('href');
            window.location.href = (href && href !== '#' && !href.startsWith('javascript:')) ? href : '/post/create';
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
        if (action === 'view-comments') {
            const postId = target.dataset.postId;
            if (postId) {
                event.preventDefault();
                window.location.href = '/posts/' + encodeURIComponent(postId);
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

        if (action === 'toggle-mute-user') {
            event.preventDefault();
            await handleMuteUserToggle(target);
            return;
        }

        if (action === 'toggle-block-user') {
            event.preventDefault();
            await handleBlockUserToggle(target);
            return;
        }

        if (action === 'toggle-restrict-user') {
            event.preventDefault();
            await handleRestrictUserToggle(target);
            return;
        }

        if (action === 'copy-profile-link') {
            event.preventDefault();
            await handleCopyProfileLink(target);
            return;
        }

        if (action === 'create-direct-conversation') {
            event.preventDefault();
            const targetUserId = target.dataset.targetUserId;
            const username = target.dataset.username;
            if (!targetUserId) return;
            const originalHtml = target.innerHTML;
            target.setAttribute('disabled', 'true');
            target.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span><span>Đang kết nối...</span>';
            try {
                const response = await fetch('/api/conversations', {
                    method: 'POST',
                    credentials: 'include',
                    headers: csrfHeaders({ 'Content-Type': 'application/json', 'Accept': 'application/json' }),
                    body: JSON.stringify({
                        type: 'DIRECT',
                        targetUserId: Number(targetUserId)
                    })
                });
                target.removeAttribute('disabled');
                target.innerHTML = originalHtml;
                const body = await readJson(response);
                if (response.ok && (response.status === 200 || response.status === 201) && body && body.success === true && body.data) {
                    const convId = body.data.id;
                    if (isValidConversationId(convId)) {
                        window.location.href = '/messages/' + encodeURIComponent(String(convId));
                        return;
                    }
                }
                const msg = translateError(body && body.error, 'Dữ liệu phản hồi cuộc trò chuyện không hợp lệ.');
                notice(msg, 'danger');
            } catch (_) {
                target.removeAttribute('disabled');
                target.innerHTML = originalHtml;
                notice('Lỗi kết nối máy chủ khi tạo cuộc trò chuyện.', 'danger');
            }
            return;
        }

        // Edit profile button is wired via Bootstrap data-bs-toggle="modal", handled natively
        if (action === 'edit-profile') {
            return;
        }

        // Post creation and reply submissions are handled by post-composer.js
        if (action === 'create-post' || action === 'submit-reply') {
            return;
        }

        // Post interactions (like, bookmark, repost) are handled by dedicated post-like script
        if (action === 'like' || action === 'bookmark' || action === 'repost') {
            return;
        }

        // Post edit and delete actions are handled by dedicated post-edit-delete script
        if (action === 'edit-post' || action === 'delete-post') {
            return;
        }

        // P1-B: Join Community
        if (action === 'join-community') {
            event.preventDefault();
            const communityId = parseInt(target.getAttribute('data-community-id'), 10);
            if (!isPositiveSafeInteger(communityId)) return;

            const lockKey = 'community:join:' + communityId;
            if (!acquireLock(lockKey)) return;

            const http = getHttp();
            if (!http) {
                releaseLock(lockKey);
                notice('Lỗi kết nối ứng dụng.', 'danger');
                return;
            }

            target.setAttribute('disabled', 'true');
            const originalHtml = target.innerHTML;
            target.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span>Đang tham gia...';

            (async function () {
                try {
                    const response = await http.request('/api/communities/' + encodeURIComponent(communityId) + '/join', {
                        method: 'POST'
                    });
                    const body = await http.readJson(response);

                    // Strict canonical CommunityMembershipResponse confirmation:
                    // 1. EXACT HTTP 200 (rejects 201, 204, etc.)
                    // 2. body.success === true
                    // 3. body.data has canonical membership identity:
                    //    - communityId is positive safe integer matching requested communityId
                    //    - role is valid enum: MEMBER, MODERATOR, ADMIN
                    //    - user is object with positive safe integer id matching authenticated currentUserId (strict, no legacy fallback, no viewer bypass)
                    //    - joinedAt is valid date-time string
                    const data = body && body.data;
                    const authUserId = getCurrentUserId();
                    const isValidCommunityId = !!(data && isPositiveSafeInteger(data.communityId) && data.communityId === communityId);
                    const isValidRole = !!(data && typeof data.role === 'string' && ['MEMBER', 'MODERATOR', 'ADMIN'].includes(data.role));
                    const isValidUser = !!(data && data.user &&
                        isPositiveSafeInteger(data.user.id) &&
                        typeof data.user.username === 'string' &&
                        data.user.username.trim().length > 0 &&
                        authUserId !== null &&
                        data.user.id === authUserId);
                    const isValidJoinedAt = !!(data && typeof data.joinedAt === 'string' && !isNaN(Date.parse(data.joinedAt)));

                    const hasOwnNullMeta = !!(body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'meta') && body.meta === null);

                    const isSuccess = response.status === 200 &&
                        body && body.success === true &&
                        hasOwnNullMeta &&
                        isValidCommunityId &&
                        isValidRole &&
                        isValidUser &&
                        isValidJoinedAt;

                    if (isSuccess) {
                        target.removeAttribute('disabled');
                        target.className = 'btn btn-sm btn-outline-danger rounded-pill px-3';
                        target.setAttribute('data-action', 'leave-community');
                        target.setAttribute('id', 'btn-leave-community');
                        target.innerHTML = '<i class="bi bi-box-arrow-right me-1" aria-hidden="true"></i>Rời cộng đồng';

                        const postBtn = document.getElementById('btn-community-post');
                        if (postBtn) {
                            postBtn.classList.remove('d-none');
                        }

                        const countEl = document.getElementById('community-member-count');
                        if (countEl) {
                            const cur = parseInt(countEl.textContent, 10) || 0;
                            countEl.textContent = String(cur + 1);
                        }
                        const statusEl = document.getElementById('community-action-status');
                        if (statusEl) {
                            statusEl.className = 'alert alert-success py-2 px-3 small mt-2 mb-2';
                            statusEl.textContent = 'Bạn đã tham gia cộng đồng thành công.';
                            statusEl.classList.remove('d-none');
                        } else {
                            notice('Tham gia cộng đồng thành công!', 'success');
                        }
                    } else {
                        target.removeAttribute('disabled');
                        target.innerHTML = originalHtml;
                        const msg = translateError(body && body.error, 'Không thể tham gia cộng đồng (phản hồi không hợp lệ).');
                        const statusEl = document.getElementById('community-action-status');
                        if (statusEl) {
                            statusEl.className = 'alert alert-danger py-2 px-3 small mt-2 mb-2';
                            statusEl.textContent = msg;
                            statusEl.classList.remove('d-none');
                        }
                        notice(msg, 'danger');
                    }
                } catch (_) {
                    target.removeAttribute('disabled');
                    target.innerHTML = originalHtml;
                    notice('Lỗi kết nối khi tham gia cộng đồng.', 'danger');
                } finally {
                    releaseLock(lockKey);
                }
            })();
            return;
        }

        if (action === 'confirm-leave-community') {
            event.preventDefault();
            return;
        }

        // P1-B: Leave Community (Accessible Bootstrap in-page confirmation modal)
        if (action === 'leave-community') {
            event.preventDefault();
            const communityId = parseInt(target.getAttribute('data-community-id'), 10);
            if (!isPositiveSafeInteger(communityId)) return;

            const modalEl = document.getElementById('leaveCommunityModal');
            if (modalEl && window.bootstrap && window.bootstrap.Modal) {
                modalEl._triggerBtn = target;
                modalEl._communityId = communityId;
                if (!modalEl.dataset.initializedListeners) {
                    modalEl.dataset.initializedListeners = 'true';
                    attachModalFreezeProtection(modalEl);
                    modalEl.addEventListener('hidden.bs.modal', function () {
                        if (modalEl._triggerBtn && typeof modalEl._triggerBtn.focus === 'function') {
                            modalEl._triggerBtn.focus();
                        }
                    });
                    const confirmBtn = modalEl.querySelector('#btn-confirm-leave-community, [data-action="confirm-leave-community"]');
                    if (confirmBtn) {
                        confirmBtn.addEventListener('click', function (e) {
                            e.preventDefault();
                            if (typeof e.stopPropagation === 'function') e.stopPropagation();
                            executeLeaveCommunity(modalEl._communityId, modalEl._triggerBtn, modalEl);
                        });
                    }
                }
                const modal = window.bootstrap.Modal.getOrCreateInstance(modalEl);
                modal.show();
                return;
            }

            // Fail closed: modal dependency absent, never fallback to native confirm or auto-accept
            notice('Không thể mở hộp thoại xác nhận rời cộng đồng.', 'warning');
            return;
        }

        function executeLeaveCommunity(communityId, target, modalEl) {
            if (!isPositiveSafeInteger(communityId)) return;
            const lockKey = 'community:leave:' + communityId;
            if (!acquireLock(lockKey)) return;

            const http = getHttp();
            if (!http) {
                releaseLock(lockKey);
                notice('Lỗi kết nối ứng dụng.', 'danger');
                return;
            }

            const confirmBtn = modalEl ? modalEl.querySelector('#btn-confirm-leave-community, [data-action="confirm-leave-community"]') : null;
            const spinner = modalEl ? modalEl.querySelector('#leave-community-spinner') : null;
            if (confirmBtn) {
                confirmBtn.setAttribute('disabled', 'true');
                if (spinner) spinner.classList.remove('d-none');
            }
            if (modalEl) {
                freezeModal(modalEl, true);
            }

            let originalTargetHtml = '';
            if (target) {
                target.setAttribute('disabled', 'true');
                originalTargetHtml = target.innerHTML;
                target.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span>Đang rời...';
            }

            (async function () {
                function hideModalIfPresent() {
                    if (modalEl) {
                        freezeModal(modalEl, false);
                        if (window.bootstrap && window.bootstrap.Modal) {
                            const modalInstance = window.bootstrap.Modal.getInstance(modalEl);
                            if (modalInstance) modalInstance.hide();
                        }
                    }
                }

                try {
                    const response = await http.request('/api/communities/' + encodeURIComponent(communityId) + '/members/me', {
                        method: 'DELETE'
                    });

                    // Strict confirmation:
                    // MUST be exact HTTP 204 No Content! Reject 200 HTML/JSON/201!
                    if (response.status === 204) {
                        hideModalIfPresent();

                        const wrapper = document.querySelector('.community-detail-wrapper');
                        const isPrivate = wrapper && wrapper.getAttribute('data-is-private') === 'true';
                        if (isPrivate) {
                            window.location.reload();
                            return;
                        }

                        if (target) {
                            target.removeAttribute('disabled');
                            target.className = 'btn btn-sm btn-primary rounded-pill px-3';
                            target.setAttribute('data-action', 'join-community');
                            target.setAttribute('id', 'btn-join-community');
                            target.innerHTML = '<i class="bi bi-plus-lg me-1" aria-hidden="true"></i>Tham gia';
                            if (typeof target.focus === 'function') target.focus();
                        }

                        const postBtn = document.getElementById('btn-community-post');
                        if (postBtn) {
                            postBtn.classList.add('d-none');
                        }

                        const countEl = document.getElementById('community-member-count');
                        if (countEl) {
                            const cur = parseInt(countEl.textContent, 10) || 0;
                            countEl.textContent = String(Math.max(0, cur - 1));
                        }
                        const statusEl = document.getElementById('community-action-status');
                        if (statusEl) {
                            statusEl.className = 'alert alert-info py-2 px-3 small mt-2 mb-2';
                            statusEl.textContent = 'Bạn đã rời khỏi cộng đồng.';
                            statusEl.classList.remove('d-none');
                        } else {
                            notice('Đã rời khỏi cộng đồng.', 'info');
                        }
                    } else {
                        hideModalIfPresent();
                        if (target) {
                            target.removeAttribute('disabled');
                            target.innerHTML = originalTargetHtml;
                            if (typeof target.focus === 'function') target.focus();
                        }
                        const body = await http.readJson(response);
                        const msg = translateError(body && body.error, 'Không thể rời cộng đồng (phản hồi không hợp lệ).');
                        const statusEl = document.getElementById('community-action-status');
                        if (statusEl) {
                            statusEl.className = 'alert alert-danger py-2 px-3 small mt-2 mb-2';
                            statusEl.textContent = msg;
                            statusEl.classList.remove('d-none');
                        }
                        notice(msg, 'danger');
                    }
                } catch (_) {
                    hideModalIfPresent();
                    if (target) {
                        target.removeAttribute('disabled');
                        target.innerHTML = originalTargetHtml;
                        if (typeof target.focus === 'function') target.focus();
                    }
                    notice('Lỗi kết nối khi rời cộng đồng.', 'danger');
                } finally {
                    if (modalEl) {
                        freezeModal(modalEl, false);
                    }
                    if (confirmBtn) {
                        confirmBtn.removeAttribute('disabled');
                        if (spinner) spinner.classList.add('d-none');
                    }
                    releaseLock(lockKey);
                }
            })();
        }

        // P1-C: Open Report Modal
        if (action === 'open-report-modal') {
            event.preventDefault();
            const modalEl = document.getElementById('reportModal');
            if (modalEl && modalEl.dataset.isFrozen === 'true') {
                // Pending request in flight: do NOT retarget or reset inputs!
                return;
            }
            if (reportFormState === 'pending') {
                return;
            }

            const reportType = target.getAttribute('data-report-type') || 'post';
            const reportedIdRaw = target.getAttribute('data-reported-id');
            const reportedId = parseInt(reportedIdRaw, 10);
            const reportedName = target.getAttribute('data-reported-name') || '';

            if (reportFormState === 'closing' || reportModalLifecycle.isClosing) {
                // Async hide in flight: queue target and open cleanly once hidden
                reportModalLifecycle.queuedOpen = {
                    reportType: reportType,
                    reportedId: reportedId,
                    reportedName: reportedName
                };
                return;
            }

            openReportTarget(reportType, reportedId, reportedName);
            return;
        }

        // P2-A: Open Quote Modal
        if (action === 'open-quote-modal') {
            event.preventDefault();
            const modalEl = document.getElementById('quotePostModal');
            if (modalEl && modalEl.dataset.isFrozen === 'true') {
                // Pending request in flight: do NOT retarget or reset inputs!
                return;
            }
            if (quoteFormState === 'pending' || quoteFormState === 'confirmed-navigating') {
                return;
            }
            quoteFormState = 'idle';
            quoteModalSession++;

            const postIdRaw = target.getAttribute('data-post-id');
            const postId = parseInt(postIdRaw, 10);
            const author = target.getAttribute('data-post-author') || 'Tác giả';
            const username = target.getAttribute('data-post-username') || '';
            const content = target.getAttribute('data-post-content') || '';

            const idInput = document.getElementById('quote-target-post-id');
            const previewAuthor = document.getElementById('quoted-preview-author');
            const previewHandle = document.getElementById('quoted-preview-handle');
            const previewContent = document.getElementById('quoted-preview-content');
            const contentInput = document.getElementById('quote-post-content');
            const statusEl = document.getElementById('quote-post-status');
            const errEl = document.getElementById('quote-post-error');
            const visSelect = document.getElementById('quote-post-visibility');

            if (idInput) idInput.value = isPositiveSafeInteger(postId) ? String(postId) : '';
            if (previewAuthor) previewAuthor.textContent = author;
            if (previewHandle) previewHandle.textContent = username ? ('@' + username) : '';
            if (previewContent) previewContent.textContent = content || '(Nội dung không có chữ)';
            if (contentInput) {
                contentInput.value = '';
                contentInput.classList.remove('is-invalid');
            }
            if (visSelect) visSelect.value = 'PUBLIC';
            if (statusEl) statusEl.className = 'alert d-none py-2 small mb-3';
            if (errEl) errEl.classList.add('d-none');

            if (modalEl && window.bootstrap && window.bootstrap.Modal) {
                const modal = window.bootstrap.Modal.getOrCreateInstance(modalEl);
                modal.show();
            }
            return;
        }

        // Preserved inert non-scope actions
        event.preventDefault();
        const nonScopeLabels = {
            'topic': 'Chức năng chủ đề.'
        };
        notice(nonScopeLabels[action] || ('Nút hoạt động. Chức năng "' + action + '" chưa được ghép trong phạm vi hiện tại.'));
    });

    // P1-B: Initialize Create Community Modal
    function initCreateCommunityModal() {
        const form = document.getElementById('create-community-form');
        const modalEl = document.getElementById('createCommunityModal');
        if (!form) return;

        if (modalEl) {
            attachModalFreezeProtection(modalEl);
        }

        const nameInput = document.getElementById('community-name-input');
        const slugInput = document.getElementById('community-slug-input');
        const descInput = document.getElementById('community-description-input');
        const statusEl = document.getElementById('create-community-status');
        const submitBtn = document.getElementById('btn-submit-create-community');
        const spinner = document.getElementById('create-community-spinner');
        const btnText = document.getElementById('create-community-btn-text');
        const nameErr = document.getElementById('community-name-error');
        const slugErr = document.getElementById('community-slug-error');
        const descChar = document.getElementById('community-desc-char-count');

        if (descInput && descChar) {
            descInput.addEventListener('input', function () {
                descChar.textContent = String(descInput.value.length);
            });
        }

        if (nameInput && slugInput) {
            let userEditedSlug = false;
            slugInput.addEventListener('input', function () {
                userEditedSlug = slugInput.value.trim().length > 0;
            });
            nameInput.addEventListener('input', function () {
                if (!userEditedSlug) {
                    slugInput.value = nameInput.value
                        .toLowerCase()
                        .normalize('NFD')
                        .replace(/[\u0300-\u036f]/g, '')
                        .replace(/[đĐ]/g, 'd')
                        .replace(/[^a-z0-9]+/g, '-')
                        .replace(/^-+|-+$/g, '')
                        .slice(0, 50);
                }
            });
        }

        form.addEventListener('submit', async function (e) {
            e.preventDefault();
            const name = (nameInput ? nameInput.value : '').trim();
            const slug = (slugInput ? slugInput.value : '').trim().toLowerCase();
            const description = (descInput ? descInput.value : '').trim();
            const isPrivateRadio = document.querySelector('input[name="isPrivate"]:checked');
            const isPrivate = isPrivateRadio ? isPrivateRadio.value === 'true' : false;

            let valid = true;
            if (!name || name.length > 100) {
                if (nameInput) nameInput.classList.add('is-invalid');
                if (nameErr) nameErr.classList.remove('d-none');
                valid = false;
            } else {
                if (nameInput) nameInput.classList.remove('is-invalid');
                if (nameErr) nameErr.classList.add('d-none');
            }

            if (!slug || slug.length > 100 || !/^[a-z0-9-]+$/.test(slug)) {
                if (slugInput) slugInput.classList.add('is-invalid');
                if (slugErr) slugErr.classList.remove('d-none');
                valid = false;
            } else {
                if (slugInput) slugInput.classList.remove('is-invalid');
                if (slugErr) slugErr.classList.add('d-none');
            }

            if (!valid) return;

            const lockKey = 'community:create';
            if (!acquireLock(lockKey)) return;

            const http = getHttp();
            if (!http) {
                releaseLock(lockKey);
                if (statusEl) {
                    statusEl.className = 'alert alert-danger py-2 small mb-3';
                    statusEl.textContent = 'Lỗi kết nối ứng dụng.';
                }
                return;
            }

            freezeModal(modalEl, true);
            if (submitBtn) submitBtn.setAttribute('disabled', 'true');
            if (spinner) spinner.classList.remove('d-none');
            if (btnText) btnText.textContent = 'Đang tạo...';
            if (statusEl) statusEl.className = 'alert d-none py-2 small mb-3';

            try {
                const response = await http.request('/api/communities', {
                    method: 'POST',
                    body: {
                        name: name,
                        slug: slug,
                        description: description || null,
                        isPrivate: isPrivate
                    }
                });
                const body = await http.readJson(response);

                // Strict confirmation: EXACT HTTP 201 only (rejects 200), success===true, and positive safe integer scalar ID
                const newId = body && body.data && body.data.id;
                const isValidId = isPositiveSafeInteger(newId);

                const isSuccess = response.status === 201 &&
                    body && body.success === true && isValidId;

                if (isSuccess) {
                    freezeModal(modalEl, false);
                    if (statusEl) {
                        statusEl.className = 'alert alert-success py-2 small mb-3';
                        statusEl.textContent = 'Tạo cộng đồng thành công! Đang chuyển hướng...';
                    }
                    setTimeout(function () {
                        window.location.href = '/communities/' + encodeURIComponent(newId);
                    }, 500);
                } else {
                    // Failure: unfreeze modal, preserve form draft, show diagnostic error
                    freezeModal(modalEl, false);
                    if (submitBtn) submitBtn.removeAttribute('disabled');
                    if (spinner) spinner.classList.add('d-none');
                    if (btnText) btnText.textContent = 'Tạo cộng đồng';
                    const msg = translateError(body && body.error, 'Không thể tạo cộng đồng (phản hồi không hợp lệ).');
                    if (statusEl) {
                        statusEl.className = 'alert alert-danger py-2 small mb-3';
                        statusEl.textContent = msg;
                    }
                    if (nameInput) nameInput.focus();
                }
            } catch (_) {
                freezeModal(modalEl, false);
                if (submitBtn) submitBtn.removeAttribute('disabled');
                if (spinner) spinner.classList.add('d-none');
                if (btnText) btnText.textContent = 'Tạo cộng đồng';
                if (statusEl) {
                    statusEl.className = 'alert alert-danger py-2 small mb-3';
                    statusEl.textContent = 'Lỗi kết nối máy chủ khi tạo cộng đồng.';
                }
                if (nameInput) nameInput.focus();
            } finally {
                releaseLock(lockKey);
            }
        });
    }

    // P1-C: Initialize Report Modal
    function initReportModal() {
        const form = document.getElementById('report-form');
        const modalEl = document.getElementById('reportModal');
        if (!form) return;

        const reasonSelect = document.getElementById('report-reason');
        const descInput = document.getElementById('report-description');
        const descChar = document.getElementById('report-char-count');
        const statusEl = document.getElementById('report-status');
        const submitBtn = document.getElementById('btn-submit-report');
        const spinner = document.getElementById('report-spinner');
        const btnText = document.getElementById('report-btn-text');
        const reasonErr = document.getElementById('report-reason-error');
        const typeInput = document.getElementById('report-target-type');
        const idInput = document.getElementById('report-target-id');

        if (modalEl) {
            attachModalFreezeProtection(modalEl);
            modalEl.addEventListener('show.bs.modal', function () {
                reportModalLifecycle.isShowing = true;
                reportModalLifecycle.isClosing = false;
            });
            modalEl.addEventListener('shown.bs.modal', function () {
                reportModalLifecycle.isShowing = false;
                reportModalLifecycle.isShown = true;
                if (reportModalLifecycle.dismissOnShown || reportFormState === 'closing' || reportFormState === 'confirmed') {
                    reportModalLifecycle.dismissOnShown = false;
                    const modal = window.bootstrap && window.bootstrap.Modal && window.bootstrap.Modal.getInstance(modalEl);
                    if (modal) {
                        modal.hide();
                    }
                }
            });
            modalEl.addEventListener('hide.bs.modal', function () {
                reportModalLifecycle.isClosing = true;
                reportModalLifecycle.isShown = false;
            });
            modalEl.addEventListener('hidden.bs.modal', function () {
                reportModalLifecycle.isClosing = false;
                reportModalLifecycle.isShowing = false;
                reportModalLifecycle.isShown = false;
                reportModalLifecycle.dismissOnShown = false;

                if (reasonSelect) {
                    reasonSelect.value = '';
                    reasonSelect.classList.remove('is-invalid');
                }
                if (descInput) descInput.value = '';
                if (descChar) descChar.textContent = '0';
                if (statusEl) statusEl.className = 'alert d-none py-2 small mb-3';
                if (reasonErr) reasonErr.classList.add('d-none');
                if (submitBtn) submitBtn.removeAttribute('disabled');
                if (spinner) spinner.classList.add('d-none');
                if (btnText) btnText.textContent = 'Gửi báo cáo';

                if (reportModalLifecycle.queuedOpen) {
                    const next = reportModalLifecycle.queuedOpen;
                    reportModalLifecycle.queuedOpen = null;
                    openReportTarget(next.reportType, next.reportedId, next.reportedName);
                } else {
                    reportFormState = 'idle';
                }
            });
        }

        if (descInput && descChar) {
            descInput.addEventListener('input', function () {
                descChar.textContent = String(descInput.value.length);
            });
        }

        form.addEventListener('submit', async function (e) {
            e.preventDefault();

            if (reportFormState === 'pending' || reportFormState === 'confirmed' || reportFormState === 'closing') {
                return;
            }

            // Client availability check: fail gracefully if alouteHttp is missing without firing raw fetch
            const http = getHttp();
            if (!http) {
                if (statusEl) {
                    statusEl.className = 'alert alert-danger py-2 small mb-3';
                    statusEl.textContent = 'Dịch vụ mạng không khả dụng. Vui lòng tải lại trang.';
                    statusEl.classList.remove('d-none');
                }
                return;
            }

            const reason = reasonSelect ? reasonSelect.value : '';
            const validReasons = ['SPAM', 'HARASSMENT', 'HATE', 'MISINFORMATION', 'VIOLENCE', 'OTHER'];
            if (!reason || !validReasons.includes(reason)) {
                if (reasonSelect) reasonSelect.classList.add('is-invalid');
                if (reasonErr) reasonErr.classList.remove('d-none');
                return;
            } else {
                if (reasonSelect) reasonSelect.classList.remove('is-invalid');
                if (reasonErr) reasonErr.classList.add('d-none');
            }

            const targetType = typeInput && typeInput.value === 'user' ? 'user' : 'post';
            const rawId = idInput ? idInput.value.trim() : '';
            const targetId = /^[1-9]\d*$/.test(rawId) ? Number(rawId) : null;
            if (!isPositiveSafeInteger(targetId)) {
                if (statusEl) {
                    statusEl.className = 'alert alert-danger py-2 small mb-3';
                    statusEl.textContent = 'Đối tượng báo cáo không hợp lệ.';
                    statusEl.classList.remove('d-none');
                }
                return;
            }

            const targetKey = targetType + ':' + targetId;
            if (reportedTargetsInSession.has(targetKey)) {
                if (statusEl) {
                    statusEl.className = 'alert alert-info py-2 small mb-3';
                    statusEl.textContent = 'Bạn đã báo cáo đối tượng này trong phiên làm việc hiện tại.';
                    statusEl.classList.remove('d-none');
                }
                return;
            }

            const payload = {
                reason: reason,
                description: descInput && descInput.value.trim() ? descInput.value.trim() : null
            };
            if (targetType === 'user') {
                payload.reportedUserId = targetId;
            } else {
                payload.reportedPostId = targetId;
            }

            const lockKey = 'report:submit';
            if (!acquireLock(lockKey)) return;

            reportFormState = 'pending';
            const currentSession = reportModalSession;
            freezeModal(modalEl, true);
            if (submitBtn) submitBtn.setAttribute('disabled', 'true');
            if (spinner) spinner.classList.remove('d-none');
            if (btnText) btnText.textContent = 'Đang gửi...';
            if (statusEl) statusEl.className = 'alert d-none py-2 small mb-3';

            try {
                const response = await http.request('/api/reports', {
                    method: 'POST',
                    body: payload
                });
                const body = await http.readJson(response);

                // Exact confirmation status 201 only (reject 200 or anything else)
                const validReportStatuses = ['PENDING', 'REVIEWED', 'REJECTED', 'RESOLVED'];
                const reportData = body && body.data;
                const isValidReportId = !!(reportData && isPositiveSafeInteger(reportData.id));
                const isValidReason = !!(reportData &&
                    typeof reportData.reason === 'string' &&
                    validReasons.includes(reportData.reason) &&
                    reportData.reason === reason);
                const isValidStatus = !!(reportData &&
                    typeof reportData.status === 'string' &&
                    validReportStatuses.includes(reportData.status));
                const isValidCreatedAt = !!(reportData &&
                    typeof reportData.createdAt === 'string' &&
                    !isNaN(Date.parse(reportData.createdAt)));

                let isTargetReflected = false;
                if (reportData) {
                    if (targetType === 'user') {
                        // user report: reportedUserId matches scalar ID, reportedPostId must be null/absent
                        isTargetReflected = isPositiveSafeInteger(reportData.reportedUserId) &&
                            reportData.reportedUserId === targetId &&
                            (reportData.reportedPostId == null);
                    } else {
                        // post report: reportedPostId matches scalar ID, reportedUserId must be null/absent
                        isTargetReflected = isPositiveSafeInteger(reportData.reportedPostId) &&
                            reportData.reportedPostId === targetId &&
                            (reportData.reportedUserId == null);
                    }
                }

                const hasOwnNullMeta = !!(body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'meta') && body.meta === null);

                const isSuccess = response.status === 201 &&
                    body && body.success === true &&
                    hasOwnNullMeta &&
                    isValidReportId &&
                    isValidReason &&
                    isValidStatus &&
                    isValidCreatedAt &&
                    isTargetReflected;

                if (isSuccess) {
                    reportFormState = 'closing';
                    reportedTargetsInSession.add(targetKey);
                    freezeModal(modalEl, false);
                    if (statusEl) {
                        statusEl.className = 'alert alert-success py-2 small mb-3';
                        statusEl.textContent = 'Báo cáo của bạn đã được gửi thành công. Cảm ơn bạn!';
                        statusEl.classList.remove('d-none');
                    }
                    if (submitBtn) submitBtn.setAttribute('disabled', 'true');
                    if (spinner) spinner.classList.add('d-none');
                    if (btnText) btnText.textContent = 'Đã báo cáo';

                    if (currentSession === reportModalSession) {
                        notice('Báo cáo đã được ghi nhận.', 'success');
                        if (reportModalLifecycle.isShowing) {
                            reportModalLifecycle.dismissOnShown = true;
                        } else {
                            if (modalEl && window.bootstrap && window.bootstrap.Modal) {
                                const modal = window.bootstrap.Modal.getInstance(modalEl);
                                if (modal) modal.hide();
                            }
                        }
                    }
                } else {
                    // Failure: unfreeze modal, preserve form fields/draft, show diagnostic error, focus reason
                    reportFormState = 'idle';
                    freezeModal(modalEl, false);
                    if (submitBtn) submitBtn.removeAttribute('disabled');
                    if (spinner) spinner.classList.add('d-none');
                    if (btnText) btnText.textContent = 'Gửi báo cáo';
                    const msg = translateError(body && body.error, 'Không thể gửi báo cáo (phản hồi không hợp lệ).');
                    if (statusEl) {
                        statusEl.className = 'alert alert-danger py-2 small mb-3';
                        statusEl.textContent = msg;
                        statusEl.classList.remove('d-none');
                    }
                    if (reasonSelect) reasonSelect.focus();
                }
            } catch (_) {
                reportFormState = 'idle';
                freezeModal(modalEl, false);
                if (submitBtn) submitBtn.removeAttribute('disabled');
                if (spinner) spinner.classList.add('d-none');
                if (btnText) btnText.textContent = 'Gửi báo cáo';
                if (statusEl) {
                    statusEl.className = 'alert alert-danger py-2 small mb-3';
                    statusEl.textContent = 'Lỗi kết nối khi gửi báo cáo.';
                    statusEl.classList.remove('d-none');
                }
                if (reasonSelect) reasonSelect.focus();
            } finally {
                releaseLock(lockKey);
            }
        });
    }

    // P2-A: Initialize Quote Post Modal
    function initQuotePostModal() {
        const form = document.getElementById('quote-post-form');
        const modalEl = document.getElementById('quotePostModal');
        if (!form) return;

        const contentInput = document.getElementById('quote-post-content');
        const charCount = document.getElementById('quote-post-char-count');
        const statusEl = document.getElementById('quote-post-status');
        const submitBtn = document.getElementById('btn-submit-quote');
        const spinner = document.getElementById('quote-post-spinner');
        const btnText = document.getElementById('quote-post-btn-text');
        const errEl = document.getElementById('quote-post-error');
        const idInput = document.getElementById('quote-target-post-id');
        const visSelect = document.getElementById('quote-post-visibility');

        if (modalEl) {
            attachModalFreezeProtection(modalEl);
            modalEl.addEventListener('show.bs.modal', function () {
                quoteModalLifecycle.isShowing = true;
            });
            modalEl.addEventListener('shown.bs.modal', function () {
                quoteModalLifecycle.isShowing = false;
                quoteModalLifecycle.isShown = true;
                if (quoteModalLifecycle.dismissOnShown || quoteFormState === 'confirmed-navigating') {
                    quoteModalLifecycle.dismissOnShown = false;
                    const modal = window.bootstrap && window.bootstrap.Modal && window.bootstrap.Modal.getInstance(modalEl);
                    if (modal) modal.hide();
                }
            });
            modalEl.addEventListener('hide.bs.modal', function () {
                quoteModalLifecycle.isShown = false;
            });
            modalEl.addEventListener('hidden.bs.modal', function () {
                quoteModalLifecycle.isShowing = false;
                quoteModalLifecycle.isShown = false;
                quoteModalLifecycle.dismissOnShown = false;
                if (quoteFormState === 'idle') {
                    if (contentInput) {
                        contentInput.value = '';
                        contentInput.classList.remove('is-invalid');
                    }
                    if (charCount) charCount.textContent = '0';
                    if (statusEl) statusEl.className = 'alert d-none py-2 small mb-3';
                    if (errEl) errEl.classList.add('d-none');
                    if (submitBtn) submitBtn.removeAttribute('disabled');
                    if (spinner) spinner.classList.add('d-none');
                    if (btnText) btnText.textContent = 'Đăng trích dẫn';
                }
            });
        }

        if (contentInput && charCount) {
            contentInput.addEventListener('input', function () {
                charCount.textContent = String(contentInput.value.length);
            });
        }

        form.addEventListener('submit', async function (e) {
            e.preventDefault();

            if (quoteFormState === 'pending' || quoteFormState === 'confirmed-navigating') {
                return;
            }

            // Client availability check: fail gracefully if alouteHttp is missing without firing raw fetch
            const http = getHttp();
            if (!http) {
                if (statusEl) {
                    statusEl.className = 'alert alert-danger py-2 small mb-3';
                    statusEl.textContent = 'Dịch vụ mạng không khả dụng. Vui lòng tải lại trang.';
                    statusEl.classList.remove('d-none');
                }
                return;
            }

            const content = contentInput ? contentInput.value.trim() : '';
            const rawPostId = idInput ? idInput.value.trim() : '';
            const postId = /^[1-9]\d*$/.test(rawPostId) ? Number(rawPostId) : null;
            const visibility = visSelect ? visSelect.value : 'PUBLIC';
            const validVisibilities = ['PUBLIC', 'FOLLOWERS'];

            if (!content || content.length > 4000) {
                if (contentInput) contentInput.classList.add('is-invalid');
                if (errEl) errEl.classList.remove('d-none');
                return;
            } else {
                if (contentInput) contentInput.classList.remove('is-invalid');
                if (errEl) errEl.classList.add('d-none');
            }

            if (!validVisibilities.includes(visibility)) {
                if (statusEl) {
                    statusEl.className = 'alert alert-danger py-2 small mb-3';
                    statusEl.textContent = 'Phạm vi hiển thị không hợp lệ.';
                    statusEl.classList.remove('d-none');
                }
                return;
            }

            if (!isPositiveSafeInteger(postId)) {
                if (statusEl) {
                    statusEl.className = 'alert alert-danger py-2 small mb-3';
                    statusEl.textContent = 'Bài viết cần trích dẫn không hợp lệ.';
                    statusEl.classList.remove('d-none');
                }
                return;
            }

            const lockKey = 'quote:submit:' + postId;
            if (!acquireLock(lockKey)) return;

            quoteFormState = 'pending';
            const currentSession = quoteModalSession;
            freezeModal(modalEl, true);
            if (submitBtn) submitBtn.setAttribute('disabled', 'true');
            if (spinner) spinner.classList.remove('d-none');
            if (btnText) btnText.textContent = 'Đang đăng...';
            if (statusEl) statusEl.className = 'alert d-none py-2 small mb-3';

            try {
                const response = await http.request('/api/posts/' + encodeURIComponent(postId) + '/quotes', {
                    method: 'POST',
                    body: {
                        content: content,
                        visibility: visibility,
                        allowReply: true,
                        allowQuote: true
                    }
                });
                const body = await http.readJson(response);

                // Exact confirmation status: 201 only. Valid returned post ID and scalar quotedPostId ONLY (no nested fallback)
                const postData = body && body.data;
                const isValidPostId = postData && isPositiveSafeInteger(postData.id);
                const reflectsQuotedPost = postData && isPositiveSafeInteger(postData.quotedPostId) && postData.quotedPostId === postId;

                const hasOwnNullMeta = !!(body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'meta') && body.meta === null);

                const isSuccess = response.status === 201 &&
                    body && body.success === true &&
                    hasOwnNullMeta &&
                    isValidPostId &&
                    reflectsQuotedPost;

                if (isSuccess) {
                    quoteFormState = 'confirmed-navigating';
                    // Disable submit button and keep lock active during confirmed navigation; reload directly without timer window
                    if (submitBtn) submitBtn.setAttribute('disabled', 'true');
                    if (spinner) spinner.classList.remove('d-none');
                    if (btnText) btnText.textContent = 'Đang chuyển hướng...';
                    if (statusEl) {
                        statusEl.className = 'alert alert-success py-2 small mb-3';
                        statusEl.textContent = 'Đã đăng trích dẫn thành công!';
                        statusEl.classList.remove('d-none');
                    }

                    if (currentSession === quoteModalSession) {
                        let flashSaved = false;
                        try {
                            if (typeof window !== 'undefined' && window.sessionStorage) {
                                window.sessionStorage.setItem('aloute_flash_notice', JSON.stringify({
                                    message: 'Đã đăng trích dẫn thành công!',
                                    type: 'success'
                                }));
                                flashSaved = true;
                            }
                        } catch (_) {
                            flashSaved = false;
                        }

                        if (quoteModalLifecycle.isShowing) {
                            quoteModalLifecycle.dismissOnShown = true;
                        } else {
                            if (modalEl && window.bootstrap && window.bootstrap.Modal) {
                                const modal = window.bootstrap.Modal.getInstance(modalEl);
                                if (modal) modal.hide();
                            }
                        }

                        if (flashSaved) {
                            window.location.reload();
                        } else {
                            // Storage absent or threw: fallback to canonical newly created post destination
                            // displaying the confirmed quote post directly without reload wipeout
                            window.location.href = '/posts/' + encodeURIComponent(postData.id);
                        }
                    }
                } else {
                    // Failure: unfreeze modal, preserve form draft, show diagnostic error, focus content
                    quoteFormState = 'idle';
                    freezeModal(modalEl, false);
                    if (submitBtn) submitBtn.removeAttribute('disabled');
                    if (spinner) spinner.classList.add('d-none');
                    if (btnText) btnText.textContent = 'Đăng trích dẫn';
                    const msg = translateError(body && body.error, 'Không thể đăng trích dẫn (phản hồi không hợp lệ).');
                    if (statusEl) {
                        statusEl.className = 'alert alert-danger py-2 small mb-3';
                        statusEl.textContent = msg;
                        statusEl.classList.remove('d-none');
                    }
                    if (contentInput) contentInput.focus();
                }
            } catch (_) {
                quoteFormState = 'idle';
                freezeModal(modalEl, false);
                if (submitBtn) submitBtn.removeAttribute('disabled');
                if (spinner) spinner.classList.add('d-none');
                if (btnText) btnText.textContent = 'Đăng trích dẫn';
                if (statusEl) {
                    statusEl.className = 'alert alert-danger py-2 small mb-3';
                    statusEl.textContent = 'Lỗi kết nối máy chủ khi đăng trích dẫn.';
                    statusEl.classList.remove('d-none');
                }
                if (contentInput) contentInput.focus();
            } finally {
                if (quoteFormState !== 'confirmed-navigating') {
                    releaseLock(lockKey);
                }
            }
        });
    }

    // Helper: validate unread count scalar
    function isValidUnreadCount(val) {
        return typeof val === 'number' && Number.isInteger(val) && Number.isSafeInteger(val) && val >= 0;
    }

    let pageActionsUnreadVersion = 0;
    let pageActionsConfirmedUnreadCount = null;

    // P1-D: Initialize Notification Badges across Navbar & Sidebar
    function initNotificationBadges() {
        const badges = document.querySelectorAll('.sidebar-notification-badge, [data-badge="notification"], #sidebar-notification-badge, .navbar-notification-badge, #navbar-notification-badge');
        if (!badges || badges.length === 0) return;

        function setBadges(rawCount) {
            if (!isValidUnreadCount(rawCount)) {
                // NEVER convert malformed counts to fake 0! Preserve last confirmed state
                return;
            }
            pageActionsConfirmedUnreadCount = rawCount;
            const num = rawCount;
            const text = num > 99 ? '99+' : String(num);
            const isHidden = num <= 0;
            const badgeLabel = num <= 0 ? 'Không có thông báo mới' : (num > 99 ? 'Hơn 99 thông báo chưa đọc' : (num + ' thông báo chưa đọc'));
            const parentAriaLabel = num > 0 ? ('Thông báo (' + text + ' chưa đọc)') : 'Thông báo';

            const currentBadges = document.querySelectorAll('.sidebar-notification-badge, [data-badge="notification"], #sidebar-notification-badge, .navbar-notification-badge, #navbar-notification-badge');
            currentBadges.forEach(function (badge) {
                badge.textContent = isHidden ? '0' : text;
                badge.setAttribute('aria-label', badgeLabel);
                if (isHidden) {
                    badge.classList.add('d-none');
                } else {
                    badge.classList.remove('d-none');
                }
                const parentLink = badge.closest('a, button');
                if (parentLink) {
                    parentLink.setAttribute('aria-label', parentAriaLabel);
                }
            });
        }

        window.addEventListener('aloute:unread-count-updated', function (e) {
            if (e && e.detail) {
                const eventVersion = typeof e.detail.version === 'number' ? e.detail.version : null;
                if (eventVersion !== null && eventVersion < pageActionsUnreadVersion) {
                    return; // Stale event discarded
                }
                if (eventVersion !== null) {
                    pageActionsUnreadVersion = Math.max(pageActionsUnreadVersion, eventVersion);
                }
                if (isValidUnreadCount(e.detail.unreadCount)) {
                    pageActionsConfirmedUnreadCount = e.detail.unreadCount;
                    setBadges(e.detail.unreadCount);
                }
            }
        });

        // SINGLE OWNER: If current page has #notifications-content, notification.js owns initial fetch!
        if (document.getElementById('notifications-content')) {
            return;
        }

        const isAuth = !!document.querySelector('.aloute-user-dropdown-btn, [data-current-user-id]:not([data-current-user-id=""])');
        if (isAuth) {
            const http = getHttp();
            if (!http) {
                return;
            }
            const currentFetchVersion = ++pageActionsUnreadVersion;
            http.request('/api/notifications/unread-count', {
                method: 'GET'
            })
            .then(async function (res) {
                if (!res || res.status !== 200) return null;
                return http.readJson(res);
            })
            .then(function (body) {
                if (currentFetchVersion !== pageActionsUnreadVersion) {
                    return; // Stale response discarded
                }
                const hasValidMeta = !body || body.meta === undefined || body.meta === null;
                if (body && body.success === true && hasValidMeta && body.data && isValidUnreadCount(body.data.unreadCount)) {
                    setBadges(body.data.unreadCount);
                }
                // Malformed response: preserve last confirmed state, do NOT set fake 0!
            })
            .catch(function () {
                // Network error: preserve last confirmed state
            });
        }
    }

    let isNavbarPreviewLoading = false;
    let navbarPreviewData = null;
    let navbarPreviewRequestId = 0;

    function escapeHtml(str) {
        if (str === null || str === undefined) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function formatPreviewTime(isoString) {
        if (!isoString) return '';
        try {
            const date = new Date(isoString);
            const now = new Date();
            const diffMs = now - date;
            const diffMin = Math.floor(diffMs / 60000);
            if (diffMin < 1) return 'Vừa xong';
            if (diffMin < 60) return diffMin + 'm';
            const diffHours = Math.floor(diffMin / 60);
            if (diffHours < 24) return diffHours + 'h';
            const day = date.getDate().toString().padStart(2, '0');
            const month = (date.getMonth() + 1).toString().padStart(2, '0');
            return day + '/' + month;
        } catch (_) {
            return '';
        }
    }

    function resolveNotificationDeepLink(item) {
        if (!item) return '/notifications';
        const targetType = (item.targetType || '').toUpperCase();
        const targetId = isPositiveSafeInteger(item.targetId) ? item.targetId : null;
        const actorId = (item.actor && isPositiveSafeInteger(item.actor.id)) ? item.actor.id :
                        (isPositiveSafeInteger(item.actorId) ? item.actorId : null);

        if (targetType === 'POST') {
            return targetId ? ('/posts/' + encodeURIComponent(targetId)) : '/feed';
        }
        if (targetType === 'USER') {
            if (targetId) return '/users/' + encodeURIComponent(targetId);
            if (actorId) return '/users/' + encodeURIComponent(actorId);
            return '/friends';
        }
        if (targetType === 'COMMUNITY') {
            return targetId ? ('/communities/' + encodeURIComponent(targetId)) : '/communities';
        }
        if (targetType === 'MESSAGE') {
            // Generic MESSAGE targetId is a messageId, NOT a conversationId; honestly link to /messages
            return '/messages';
        }
        if (targetType === 'CONVERSATION') {
            return targetId ? ('/messages/' + encodeURIComponent(targetId)) : '/messages';
        }
        return '/notifications';
    }

    function getNotificationPreviewText(item) {
        const type = (item && item.type ? item.type : '').toUpperCase();
        if (type.includes('LIKE')) return 'đã thích bài viết của bạn';
        if (type.includes('REPLY') || type.includes('COMMENT')) return 'đã bình luận về bài viết';
        if (type.includes('QUOTE')) return 'đã trích dẫn bài viết';
        if (type.includes('REPOST')) return 'đã chia sẻ lại bài viết';
        if (type.includes('MENTION')) return 'đã nhắc đến bạn';
        if (type.includes('FOLLOW')) return 'đã theo dõi bạn';
        if (type.includes('FRIEND_ACCEPT')) return 'đã chấp nhận kết bạn';
        if (type.includes('FRIEND')) return 'đã gửi lời mời kết bạn';
        return 'đã tương tác với bạn';
    }

    function normalizeNotificationReadState(item) {
        if (!item || typeof item !== 'object') return item;
        let readState;
        if (typeof item.read === 'boolean') {
            readState = item.read;
        } else if (typeof item.isRead === 'boolean') {
            readState = item.isRead;
        }
        if (typeof readState === 'boolean') {
            item.read = readState;
            item.isRead = readState;
        }
        return item;
    }

    const pendingNotificationReadIds = new Set();

    function renderNavbarPreviewItems(container, items) {
        if (!container) return;
        container.innerHTML = '';
        if (!items || items.length === 0) {
            container.innerHTML = '<div class="p-4 text-center text-muted small"><i class="bi bi-bell-slash fs-4 d-block mb-2 text-secondary"></i>Không có thông báo mới</div>';
            return;
        }

        items.forEach(function (item) {
            normalizeNotificationReadState(item);
            const isUnread = item.isRead === false;
            const el = document.createElement('a');
            el.className = 'dropdown-item aloute-notification-preview-item' + (isUnread ? ' unread' : '');
            const deepLink = resolveNotificationDeepLink(item);
            el.setAttribute('href', deepLink);
            el.href = deepLink;
            el.setAttribute('data-notification-id', String(item.id));
            el.setAttribute('role', 'menuitem');

            const avatarUrl = (item.actor && item.actor.avatarUrl) ? item.actor.avatarUrl : '/images/avatar-fallback.svg';
            const actorName = (item.actor && (item.actor.displayName || item.actor.username)) || 'Sinh viên';
            const actionText = getNotificationPreviewText(item);
            const timeText = formatPreviewTime(item.createdAt);
            const contentSnippet = item.contentPreview ? `<div class="small text-muted text-truncate mt-1" style="max-width: 250px;">"${escapeHtml(item.contentPreview)}"</div>` : '';
            const unreadBadge = isUnread ? '<span class="badge bg-primary-subtle text-primary border border-primary-subtle" style="font-size: 0.65rem;">Chưa đọc</span>' : '';
            const unreadDot = isUnread ? '<span class="aloute-preview-unread-dot" aria-hidden="true"></span>' : '';

            el.innerHTML = `
                ${unreadDot}
                <img src="${escapeHtml(avatarUrl)}" alt="${escapeHtml(actorName)}" class="aloute-preview-avatar">
                <div class="flex-grow-1 aloute-min-w-0">
                    <div class="d-flex align-items-baseline justify-content-between gap-1">
                        <span class="small fw-semibold text-truncate text-dark" style="max-width: 160px;">${escapeHtml(actorName)}</span>
                        <span class="text-muted small flex-shrink-0" style="font-size: 0.7rem;">${escapeHtml(timeText)}</span>
                    </div>
                    <div class="small text-secondary">${escapeHtml(actionText)}</div>
                    ${contentSnippet}
                    <div class="mt-1">${unreadBadge}</div>
                </div>
            `;

            el.addEventListener('click', async function (e) {
                if (item.isRead === false) {
                    e.preventDefault();
                    if (pendingNotificationReadIds.has(item.id)) {
                        return; // Rapid repeated clicks suppressed
                    }
                    pendingNotificationReadIds.add(item.id);
                    const http = getHttp();
                    if (http && isPositiveSafeInteger(item.id)) {
                        try {
                            const res = await http.request('/api/notifications/' + encodeURIComponent(item.id) + '/read', {
                                method: 'PATCH'
                            });
                            const body = await http.readJson(res);
                            if (res.status === 200 && body && body.success === true) {
                                item.isRead = true;
                                item.read = true;
                                if (body.data && typeof body.data === 'object') {
                                    normalizeNotificationReadState(body.data);
                                }
                                el.classList.remove('unread');
                                const dot = el.querySelector('.aloute-preview-unread-dot');
                                if (dot) dot.remove();
                                const badge = el.querySelector('.badge');
                                if (badge) badge.remove();

                                let count;
                                if (typeof pageActionsConfirmedUnreadCount === 'number') {
                                    count = pageActionsConfirmedUnreadCount;
                                } else {
                                    const curBadge = document.getElementById('navbar-notification-badge');
                                    const badgeText = curBadge ? curBadge.textContent.trim() : '';
                                    count = parseInt(badgeText, 10) || 0;
                                }
                                const nextCount = Math.max(0, count - 1);
                                pageActionsConfirmedUnreadCount = nextCount;
                                const nextVersion = ++pageActionsUnreadVersion;
                                window.dispatchEvent(new CustomEvent('aloute:notification-read', {
                                    detail: { id: item.id, unreadCount: nextCount, version: nextVersion }
                                }));
                                window.dispatchEvent(new CustomEvent('aloute:unread-count-updated', {
                                    detail: { unreadCount: nextCount, version: nextVersion }
                                }));
                            }
                        } catch (_) {
                        } finally {
                            pendingNotificationReadIds.delete(item.id);
                        }
                    } else {
                        pendingNotificationReadIds.delete(item.id);
                    }
                    window.location.href = el.href;
                }
            });

            container.appendChild(el);
        });
    }

    async function fetchNavbarRecentNotifications(force) {
        const listContainer = document.getElementById('navbar-notification-list');
        if (!listContainer) return;
        if (isNavbarPreviewLoading) return; // Deduplication
        if (navbarPreviewData && !force) {
            renderNavbarPreviewItems(listContainer, navbarPreviewData);
            return;
        }

        const currentPreviewReqId = ++navbarPreviewRequestId;
        isNavbarPreviewLoading = true;
        listContainer.innerHTML = '<div class="p-4 text-center text-muted small"><span class="spinner-border spinner-border-sm text-primary me-2" role="status"></span>Đang tải thông báo...</div>';

        const http = getHttp();
        if (!http) {
            isNavbarPreviewLoading = false;
            listContainer.innerHTML = '<div class="p-3 text-center text-danger small">Không thể tải thông báo.</div>';
            return;
        }

        try {
            const res = await http.request('/api/notifications?page=0&size=5', {
                method: 'GET'
            });
            const body = await http.readJson(res);
            isNavbarPreviewLoading = false;

            if (currentPreviewReqId !== navbarPreviewRequestId) {
                return; // Stale preview response discarded (superseded or read-all occurred)
            }

            if (res.status === 200 && body && body.success === true && Array.isArray(body.data)) {
                body.data.forEach(normalizeNotificationReadState);
                navbarPreviewData = body.data;
                renderNavbarPreviewItems(listContainer, navbarPreviewData);
            } else {
                listContainer.innerHTML = '<div class="p-3 text-center text-danger small">Không thể tải danh sách thông báo.</div>';
            }
        } catch (err) {
            isNavbarPreviewLoading = false;
            if (currentPreviewReqId !== navbarPreviewRequestId) {
                return;
            }
            listContainer.innerHTML = '<div class="p-3 text-center text-danger small">Lỗi kết nối máy chủ.</div>';
        }
    }

    function initNavbarNotificationPreview() {
        const toggle = document.getElementById('navbarNotificationDropdown');
        const listContainer = document.getElementById('navbar-notification-list');
        const btnMarkAll = document.getElementById('btn-navbar-mark-all-read');
        const feedbackEl = document.getElementById('navbar-notif-feedback');
        const previewMenu = document.getElementById('navbar-notification-preview');
        if (!toggle || !listContainer) return;

        // Space key activation on role="button" anchor toggle
        if (toggle.dataset.spaceWired !== 'true') {
            toggle.dataset.spaceWired = 'true';
            toggle.addEventListener('keydown', function (e) {
                if (e.key === ' ' || e.key === 'Spacebar') {
                    e.preventDefault();
                    toggle.click();
                }
            });
        }

        // Keyboard traversal (ArrowDown / ArrowUp / Escape) within dropdown menu
        if (previewMenu && previewMenu.dataset.navWired !== 'true') {
            previewMenu.dataset.navWired = 'true';
            previewMenu.addEventListener('keydown', function (e) {
                if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
                    const items = Array.from(previewMenu.querySelectorAll('.dropdown-item')).filter(function (el) {
                        return !el.classList.contains('disabled') && !el.hasAttribute('disabled') && el.getAttribute('aria-disabled') !== 'true';
                    });
                    if (items.length === 0) return;
                    const currentIndex = items.indexOf(document.activeElement);
                    let nextIndex;
                    if (e.key === 'ArrowDown') {
                        nextIndex = (currentIndex < items.length - 1) ? currentIndex + 1 : 0;
                    } else {
                        nextIndex = (currentIndex > 0) ? currentIndex - 1 : items.length - 1;
                    }
                    e.preventDefault();
                    items[nextIndex].focus();
                } else if (e.key === 'Escape' || e.key === 'Esc') {
                    e.preventDefault();
                    const bs = (typeof window !== 'undefined' && window.bootstrap) || (typeof bootstrap !== 'undefined' ? bootstrap : null);
                    if (bs && bs.Dropdown) {
                        const inst = bs.Dropdown.getInstance(toggle);
                        if (inst && typeof inst.hide === 'function') {
                            inst.hide();
                        }
                    } else if (previewMenu.classList.contains('show')) {
                        previewMenu.classList.remove('show');
                    }
                    try { toggle.focus(); } catch (_) {}
                }
            });
        }

        const dropdownParent = toggle.closest('.dropdown');
        if (dropdownParent && dropdownParent.dataset.previewWired !== 'true') {
            dropdownParent.dataset.previewWired = 'true';
            dropdownParent.addEventListener('show.bs.dropdown', function () {
                fetchNavbarRecentNotifications(true);
            });
        }

        if (typeof window !== 'undefined' && !window.__aloutePageActionsReadAllWired) {
            window.__aloutePageActionsReadAllWired = true;
            window.addEventListener('aloute:notifications-read-all', function () {
                navbarPreviewRequestId++;
                pageActionsConfirmedUnreadCount = 0;
                if (navbarPreviewData) {
                    navbarPreviewData.forEach(function (item) {
                        item.isRead = true;
                        item.read = true;
                    });
                    if (listContainer) {
                        renderNavbarPreviewItems(listContainer, navbarPreviewData);
                    }
                }
            });
        }

        if (btnMarkAll && btnMarkAll.dataset.wired !== 'true') {
            btnMarkAll.dataset.wired = 'true';
            btnMarkAll.addEventListener('click', async function (e) {
                e.preventDefault();
                e.stopPropagation();
                const http = getHttp();
                if (!http) return;

                btnMarkAll.disabled = true;
                const origText = btnMarkAll.textContent;
                btnMarkAll.innerHTML = '<span class="spinner-border spinner-border-sm" role="status"></span>';

                try {
                    const res = await http.request('/api/notifications/read-all', {
                        method: 'PATCH'
                    });
                    const body = await http.readJson(res);
                    btnMarkAll.disabled = false;
                    btnMarkAll.textContent = origText;

                    if (res.status === 200 && body && body.success === true) {
                        navbarPreviewRequestId++;
                        if (navbarPreviewData) {
                            navbarPreviewData.forEach(function (item) {
                                item.isRead = true;
                                item.read = true;
                            });
                            renderNavbarPreviewItems(listContainer, navbarPreviewData);
                        }
                        pageActionsConfirmedUnreadCount = 0;
                        const nextVersion = ++pageActionsUnreadVersion;
                        window.dispatchEvent(new CustomEvent('aloute:notifications-read-all', {
                            detail: { unreadCount: 0, version: nextVersion }
                        }));
                        window.dispatchEvent(new CustomEvent('aloute:unread-count-updated', {
                            detail: { unreadCount: 0, version: nextVersion }
                        }));
                        if (feedbackEl) {
                            feedbackEl.className = 'alert alert-success py-1 px-3 mb-0 small rounded-0';
                            feedbackEl.textContent = 'Đã đánh dấu tất cả đã đọc.';
                            feedbackEl.classList.remove('d-none');
                            setTimeout(function () { feedbackEl.classList.add('d-none'); }, 3000);
                        }
                    } else {
                        if (feedbackEl) {
                            feedbackEl.className = 'alert alert-danger py-1 px-3 mb-0 small rounded-0';
                            feedbackEl.textContent = 'Không thể đánh dấu đã đọc. Vui lòng thử lại.';
                            feedbackEl.classList.remove('d-none');
                            setTimeout(function () { feedbackEl.classList.add('d-none'); }, 3000);
                        }
                    }
                } catch (err) {
                    btnMarkAll.disabled = false;
                    btnMarkAll.textContent = origText;
                    if (feedbackEl) {
                        feedbackEl.className = 'alert alert-danger py-1 px-3 mb-0 small rounded-0';
                        feedbackEl.textContent = 'Lỗi kết nối máy chủ.';
                        feedbackEl.classList.remove('d-none');
                        setTimeout(function () { feedbackEl.classList.add('d-none'); }, 3000);
                    }
                }
            });
        }
    }

    // P3: Poll Voting Controller (POST /api/polls/{id}/vote)
    function initPollVoting() {
        document.addEventListener('submit', async function (e) {
            const form = e.target.closest('.poll-vote-form');
            if (!form) return;
            e.preventDefault();

            const pollIdStr = form.dataset.pollId;
            const pollId = Number(pollIdStr);
            if (!isPositiveSafeInteger(pollId)) return;

            const lockKey = 'poll-vote-' + pollId;
            if (!acquireLock(lockKey)) return;

            const isMultiple = form.dataset.allowMultiple === 'true';
            const errorEl = form.querySelector('.poll-vote-error');
            const submitBtn = form.querySelector('.btn-vote-poll');
            const spinner = submitBtn ? submitBtn.querySelector('.spinner-border') : null;

            if (errorEl) {
                errorEl.textContent = '';
                errorEl.classList.add('d-none');
            }

            const container = form.closest('.post-poll-container');
            if (container && (container.dataset.isClosed === 'true' || container.getAttribute('data-is-closed') === 'true')) {
                releaseLock(lockKey);
                if (errorEl) {
                    errorEl.textContent = 'Bình chọn này đã kết thúc.';
                    errorEl.classList.remove('d-none');
                }
                return;
            }

            let selectedIds = [];
            if (isMultiple) {
                const checkedBoxes = form.querySelectorAll('input[type="checkbox"]:checked');
                checkedBoxes.forEach(function (cb) {
                    const val = Number(cb.value);
                    if (isPositiveSafeInteger(val)) {
                        selectedIds.push(val);
                    }
                });
            } else {
                const checkedRadio = form.querySelector('input[type="radio"]:checked');
                if (checkedRadio) {
                    const val = Number(checkedRadio.value);
                    if (isPositiveSafeInteger(val)) {
                        selectedIds.push(val);
                    }
                }
            }

            if (selectedIds.length === 0) {
                releaseLock(lockKey);
                if (errorEl) {
                    errorEl.textContent = isMultiple ? 'Vui lòng chọn ít nhất một phương án.' : 'Vui lòng chọn một phương án.';
                    errorEl.classList.remove('d-none');
                }
                return;
            }

            if (submitBtn) submitBtn.disabled = true;
            if (spinner) spinner.classList.remove('d-none');

            const http = getHttp();
            if (!http) {
                releaseLock(lockKey);
                if (submitBtn) submitBtn.disabled = false;
                if (spinner) spinner.classList.add('d-none');
                if (errorEl) {
                    errorEl.textContent = 'Lỗi kết nối. Không tìm thấy HTTP client.';
                    errorEl.classList.remove('d-none');
                }
                return;
            }

            const payload = isMultiple ? { optionIds: selectedIds } : { optionId: selectedIds[0] };

            try {
                const res = await http.request('/api/polls/' + encodeURIComponent(pollId) + '/vote', {
                    method: 'POST',
                    body: payload
                });

                if (res && (res.status === 200 || res.status === 201)) {
                    const body = await http.readJson(res);
                    const pollData = body && body.data;
                    if (pollData && Array.isArray(pollData.options)) {
                        renderPollResults(form, pollData);
                        releaseLock(lockKey);
                        notice('Bình chọn thành công!', 'success');
                        return;
                    }
                }

                // Error response
                const errBody = res ? await http.readJson(res) : null;
                const errCode = errBody && errBody.error && errBody.error.code;
                let errMsg = 'Không thể gửi bình chọn. Vui lòng thử lại.';
                if (errCode === 'POLL_ALREADY_VOTED' || (res && res.status === 409)) {
                    errMsg = 'Bạn đã tham gia bình chọn này rồi.';
                } else if (errCode === 'POLL_CLOSED') {
                    errMsg = 'Bình chọn này đã kết thúc.';
                } else if (res && res.status === 401) {
                    errMsg = 'Vui lòng đăng nhập để bình chọn.';
                } else if (errBody && errBody.error && errBody.error.message) {
                    errMsg = errBody.error.message;
                }

                if (errorEl) {
                    errorEl.textContent = errMsg;
                    errorEl.classList.remove('d-none');
                }
            } catch (err) {
                if (errorEl) {
                    errorEl.textContent = 'Lỗi kết nối máy chủ khi gửi bình chọn.';
                    errorEl.classList.remove('d-none');
                }
            } finally {
                releaseLock(lockKey);
                if (submitBtn) submitBtn.disabled = false;
                if (spinner) spinner.classList.add('d-none');
            }
        });
    }

    function renderPollResults(formEl, poll) {
        const total = poll.totalVotes || 0;
        const container = formEl.closest('.post-poll-container');
        if (!container) return;

        const resultsDiv = document.createElement('div');
        resultsDiv.className = 'poll-results d-flex flex-column gap-2';

        poll.options.forEach(function (opt) {
            const pct = total > 0 ? Math.round((opt.voteCount * 100) / total) : 0;
            const optDiv = document.createElement('div');
            optDiv.className = 'poll-option-result position-relative border rounded-2 p-2 overflow-hidden bg-white';

            const bgBar = document.createElement('div');
            bgBar.className = 'poll-progress-bg position-absolute top-0 start-0 h-100';
            bgBar.style.width = pct + '%';
            bgBar.style.backgroundColor = 'rgba(37, 99, 235, 0.12)';
            bgBar.style.zIndex = '0';

            const contentRow = document.createElement('div');
            contentRow.className = 'position-relative d-flex justify-content-between align-items-center';
            contentRow.style.zIndex = '1';

            const leftCol = document.createElement('div');
            leftCol.className = 'd-flex align-items-center gap-2';

            if (opt.selectedByCurrentUser || opt.id === poll.userVotedOptionId) {
                const checkIcon = document.createElement('i');
                checkIcon.className = 'bi bi-check-circle-fill text-primary';
                checkIcon.setAttribute('aria-label', 'Lựa chọn của bạn');
                leftCol.appendChild(checkIcon);
            }

            const textSpan = document.createElement('span');
            textSpan.className = 'small fw-medium text-dark';
            textSpan.textContent = opt.text || '';
            leftCol.appendChild(textSpan);

            const rightCol = document.createElement('div');
            rightCol.className = 'small text-muted d-flex align-items-center gap-2';

            const pctSpan = document.createElement('span');
            pctSpan.textContent = pct + '%';
            const countSpan = document.createElement('span');
            countSpan.textContent = '(' + (opt.voteCount || 0) + ')';

            rightCol.appendChild(pctSpan);
            rightCol.appendChild(countSpan);

            contentRow.appendChild(leftCol);
            contentRow.appendChild(rightCol);

            optDiv.appendChild(bgBar);
            optDiv.appendChild(contentRow);
            resultsDiv.appendChild(optDiv);
        });

        const footerDiv = document.createElement('div');
        footerDiv.className = 'd-flex justify-content-between align-items-center small text-muted mt-1';
        const totalSpan = document.createElement('span');
        totalSpan.textContent = total + ' lượt bình chọn';
        footerDiv.appendChild(totalSpan);

        if (poll.allowMultiple) {
            const multSpan = document.createElement('span');
            multSpan.className = 'fst-italic';
            multSpan.textContent = 'Nhiều lựa chọn';
            footerDiv.appendChild(multSpan);
        }

        resultsDiv.appendChild(footerDiv);
        formEl.replaceWith(resultsDiv);
    }

    // Consume pending one-shot flash notice if present (e.g. post-reload success feedback)
    function consumeFlashNotice() {
        try {
            if (typeof window !== 'undefined' && window.sessionStorage) {
                const raw = window.sessionStorage.getItem('aloute_flash_notice');
                if (raw) {
                    window.sessionStorage.removeItem('aloute_flash_notice');
                    const data = JSON.parse(raw);
                    if (data && typeof data.message === 'string') {
                        notice(data.message, data.type || 'info');
                    }
                }
            }
        } catch (_) {}
    }

    // Initialize interactive components when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () {
            initProfileEditModal();
            initFollowButton();
            initProfileMoreActions();
            initCreateCommunityModal();
            initReportModal();
            initQuotePostModal();
            initNotificationBadges();
            initNavbarNotificationPreview();
            initPollVoting();
            consumeFlashNotice();
        });
    } else {
        initProfileEditModal();
        initFollowButton();
        initProfileMoreActions();
        initCreateCommunityModal();
        initReportModal();
        initQuotePostModal();
        initNotificationBadges();
        initNavbarNotificationPreview();
        initPollVoting();
        consumeFlashNotice();
    }

})();
