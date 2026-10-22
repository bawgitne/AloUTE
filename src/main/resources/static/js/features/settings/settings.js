/**
 * AloUTE — Settings Page Interactivity Controller
 *
 * Implements client-side logic for the Account Settings module:
 * - Dynamic tab navigation (Profile, Privacy, Security, Appearance)
 * - Avatar upload to Firebase Storage via POST /api/media (USER_AVATAR purpose)
 * - Profile updates (displayName, bio, website, avatarMediaId) via PATCH /api/users/me/profile
 * - Privacy settings (isPrivate toggle) via PATCH /api/users/me/profile
 * - Secure password changing via POST /api/users/me/password
 * - Live character counter, password visibility toggle, copy profile link, and theme switcher
 */
(function () {
    'use strict';

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initSettings);
    } else {
        initSettings();
    }

    function initSettings() {
        initTabs();
        initAlertClose();
        initCoverUpload();
        initAvatarUpload();
        initBioCounter();
        initProfileForm();
        initPrivacyForm();
        initCopyProfileLink();
        initPasswordForm();
        initPasswordVisibilityToggles();
        initThemeSelector();
        initLogoutButtons();
        initTwoFactorSettings();
        initBlockedUsers();
        initMutedUsers();
        initNotificationSettings();
        initSessionAndDeactivation();
    }

    // =========================================================================
    // 1. Alerts & Notifications Helper
    // =========================================================================
    function showAlert(message, type) {
        const container = document.getElementById('settings-alert-container');
        const box = document.getElementById('settings-alert-box');
        const icon = document.getElementById('settings-alert-icon');
        const msgEl = document.getElementById('settings-alert-message');

        if (!container || !box || !icon || !msgEl) return;

        // Reset classes
        box.classList.remove('alert-success', 'alert-danger', 'alert-warning', 'alert-info');
        icon.className = 'bi fs-5';

        if (type === 'success') {
            box.classList.add('alert-success');
            icon.classList.add('bi-check-circle-fill', 'text-success');
        } else if (type === 'warning') {
            box.classList.add('alert-warning');
            icon.classList.add('bi-exclamation-triangle-fill', 'text-warning');
        } else {
            box.classList.add('alert-danger');
            icon.classList.add('bi-x-circle-fill', 'text-danger');
        }

        msgEl.textContent = message;
        container.classList.remove('d-none');

        // Smooth scroll to alert if out of view
        container.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }

    function hideAlert() {
        const container = document.getElementById('settings-alert-container');
        if (container) container.classList.add('d-none');
    }

    function initAlertClose() {
        const closeBtn = document.getElementById('settings-alert-close');
        if (closeBtn) {
            closeBtn.addEventListener('click', hideAlert);
        }
    }

    // =========================================================================
    // 2. Tab Navigation
    // =========================================================================
    function initTabs() {
        const nav = document.getElementById('settings-tabs-nav');

        function activateTab(tabName) {
            if (!tabName) return;
            const tabButtons = document.querySelectorAll('#settings-tabs-nav [data-tab]');
            const tabPanes = document.querySelectorAll('.tab-pane-content');

            tabButtons.forEach(btn => {
                const isActive = btn.getAttribute('data-tab') === tabName;
                btn.classList.toggle('active', isActive);
                btn.setAttribute('aria-selected', isActive ? 'true' : 'false');
            });

            tabPanes.forEach(pane => {
                const isTarget = pane.id === 'pane-' + tabName;
                pane.classList.toggle('d-none', !isTarget);
            });

            // Update URL without full refresh
            if (window.history && window.history.replaceState) {
                window.history.replaceState(null, '', '/settings/' + tabName);
            }

            if (tabName === 'blocking' && typeof window.__alouteLoadBlockedUsers === 'function') {
                window.__alouteLoadBlockedUsers();
            }
            if (tabName === 'muted' && typeof window.__alouteLoadMutedUsers === 'function') {
                window.__alouteLoadMutedUsers();
            }
        }

        if (nav) {
            nav.addEventListener('click', function (e) {
                const btn = e.target.closest('[data-tab]');
                if (!btn) return;
                e.preventDefault();
                e.stopPropagation();
                const tabName = btn.getAttribute('data-tab');
                if (tabName) {
                    hideAlert();
                    activateTab(tabName);
                }
            });
        }

        // Check hash or path on load
        const pathParts = window.location.pathname.split('/').filter(Boolean);
        if (pathParts.length >= 2 && pathParts[0] === 'settings') {
            const initialTab = pathParts[1].toLowerCase();
            if (document.getElementById('pane-' + initialTab)) {
                activateTab(initialTab);
            }
        } else if (window.location.hash) {
            const hashTab = window.location.hash.replace('#', '').toLowerCase();
            if (document.getElementById('pane-' + hashTab)) {
                activateTab(hashTab);
            }
        }

        window.addEventListener('popstate', function () {
            const pathParts = window.location.pathname.split('/').filter(Boolean);
            if (pathParts.length >= 2 && pathParts[0] === 'settings') {
                const tab = pathParts[1].toLowerCase();
                if (document.getElementById('pane-' + tab)) {
                    activateTab(tab);
                    return;
                }
            }
            activateTab('profile');
        });
    }

    // =========================================================================
    // 3. Cover Photo Upload & Preview (Firebase Storage)
    // =========================================================================
    function initCoverUpload() {
        const fileInput = document.getElementById('settings-cover-file-input');
        const previewImg = document.getElementById('settings-cover-preview');
        const spinner = document.getElementById('settings-cover-spinner');
        const mediaIdInput = document.getElementById('settings-cover-media-id');
        const removeBtn = document.getElementById('settings-btn-remove-cover');

        if (!fileInput || !previewImg) return;

        fileInput.addEventListener('change', async function () {
            const file = this.files && this.files[0];
            if (!file) return;

            if (file.size > 5 * 1024 * 1024) {
                showAlert('Ảnh bìa vượt quá dung lượng tối đa cho phép (5MB). Vui lòng chọn ảnh khác.', 'danger');
                this.value = '';
                return;
            }

            const objectUrl = URL.createObjectURL(file);
            previewImg.src = objectUrl;

            if (spinner) spinner.classList.remove('d-none');
            hideAlert();

            const formData = new FormData();
            formData.append('file', file);
            formData.append('purpose', 'USER_COVER');

            try {
                const response = await window.alouteHttp.request('/api/media', {
                    method: 'POST',
                    body: formData
                });
                const result = await window.alouteHttp.readJson(response);

                if (response.ok && result && result.data && result.data.id) {
                    if (mediaIdInput) mediaIdInput.value = result.data.id;
                    if (result.data.url) previewImg.src = result.data.url;
                    showAlert('Ảnh bìa đã được tải lên thành công! Hãy bấm "Lưu thay đổi hồ sơ" để hoàn tất.', 'success');
                } else {
                    const errorMsg = (result && result.message) || 'Không thể tải ảnh bìa lên Firebase Storage.';
                    showAlert(errorMsg, 'danger');
                    if (mediaIdInput) mediaIdInput.value = '';
                }
            } catch (err) {
                showAlert('Lỗi kết nối khi tải ảnh bìa: ' + err.message, 'danger');
                if (mediaIdInput) mediaIdInput.value = '';
            } finally {
                if (spinner) spinner.classList.add('d-none');
                fileInput.value = '';
            }
        });

        if (removeBtn) {
            removeBtn.addEventListener('click', function () {
                previewImg.src = '/images/cover-fallback.svg';
                if (mediaIdInput) mediaIdInput.value = 'null';
                showAlert('Ảnh bìa đã được chọn để gỡ bỏ. Hãy bấm "Lưu thay đổi hồ sơ" để hoàn tất.', 'warning');
            });
        }
    }

    // =========================================================================
    // 3b. Avatar Upload & Preview (Firebase Storage)
    // =========================================================================
    function initAvatarUpload() {
        const fileInput = document.getElementById('settings-avatar-file-input');
        const previewImg = document.getElementById('settings-avatar-preview');
        const spinner = document.getElementById('settings-avatar-spinner');
        const mediaIdInput = document.getElementById('settings-avatar-media-id');
        const removeBtn = document.getElementById('settings-btn-remove-avatar');

        if (!fileInput || !previewImg) return;

        fileInput.addEventListener('change', async function () {
            const file = this.files && this.files[0];
            if (!file) return;

            // Validate max 5MB
            if (file.size > 5 * 1024 * 1024) {
                showAlert('Ảnh vượt quá dung lượng tối đa cho phép (5MB). Vui lòng chọn ảnh khác.', 'danger');
                this.value = '';
                return;
            }

            // Local preview immediately
            const objectUrl = URL.createObjectURL(file);
            previewImg.src = objectUrl;

            // Upload directly to Firebase Storage via POST /api/media
            if (spinner) spinner.classList.remove('d-none');
            hideAlert();

            const formData = new FormData();
            formData.append('file', file);
            formData.append('purpose', 'USER_AVATAR');

            try {
                const response = await window.alouteHttp.request('/api/media', {
                    method: 'POST',
                    body: formData
                });
                const result = await window.alouteHttp.readJson(response);

                if (response.ok && result && result.data && result.data.id) {
                    if (mediaIdInput) mediaIdInput.value = result.data.id;
                    if (result.data.url) previewImg.src = result.data.url;
                    showAlert('Ảnh đại diện đã được tải lên thành công! Hãy bấm "Lưu thay đổi hồ sơ" để hoàn tất.', 'success');
                } else {
                    const errorMsg = (result && result.message) || 'Không thể tải ảnh lên Firebase Storage.';
                    showAlert(errorMsg, 'danger');
                    if (mediaIdInput) mediaIdInput.value = '';
                }
            } catch (err) {
                showAlert('Lỗi kết nối khi tải ảnh đại diện: ' + err.message, 'danger');
                if (mediaIdInput) mediaIdInput.value = '';
            } finally {
                if (spinner) spinner.classList.add('d-none');
                fileInput.value = '';
            }
        });

        if (removeBtn) {
            removeBtn.addEventListener('click', function () {
                previewImg.src = '/images/avatar-fallback.svg';
                if (mediaIdInput) mediaIdInput.value = 'null';
                showAlert('Ảnh đại diện đã được chọn để gỡ bỏ. Hãy bấm "Lưu thay đổi hồ sơ" để hoàn tất.', 'warning');
            });
        }
    }

    // =========================================================================
    // 4. Bio Counter
    // =========================================================================
    function initBioCounter() {
        const bioTextarea = document.getElementById('settings-input-bio');
        const counter = document.getElementById('settings-bio-counter');
        if (!bioTextarea || !counter) return;

        function updateCounter() {
            counter.textContent = bioTextarea.value.length + '/500';
        }

        bioTextarea.addEventListener('input', updateCounter);
        updateCounter();
    }

    // =========================================================================
    // 5. Profile Form Submit (PATCH /api/users/me/profile)
    // =========================================================================
    function initProfileForm() {
        const form = document.getElementById('settings-profile-form');
        const submitBtn = document.getElementById('settings-btn-save-profile');
        const spinner = document.getElementById('settings-save-profile-spinner');
        const btnText = document.getElementById('settings-save-profile-text');

        if (!form) return;

        form.addEventListener('submit', async function (e) {
            e.preventDefault();
            hideAlert();

            const displayNameInput = document.getElementById('settings-input-display-name');
            const usernameInput = document.getElementById('settings-input-username');
            const bioInput = document.getElementById('settings-input-bio');
            const websiteInput = document.getElementById('settings-input-website');
            const mediaIdInput = document.getElementById('settings-avatar-media-id');

            const rawDisplayName = displayNameInput ? displayNameInput.value.trim() : '';
            const username = usernameInput ? usernameInput.value.trim() : '';
            // Auto fallback to username (ID) just like Instagram when display name is blank
            const finalDisplayName = rawDisplayName || username;

            if (displayNameInput) {
                displayNameInput.classList.remove('is-invalid');
            }
            const displayNameFb = document.getElementById('settings-display-name-feedback');
            if (displayNameFb) displayNameFb.classList.add('d-none');

            if (finalDisplayName.length > 100) {
                if (displayNameInput) displayNameInput.classList.add('is-invalid');
                if (displayNameFb) displayNameFb.classList.remove('d-none');
                showAlert('Tên hiển thị tối đa 100 ký tự.', 'danger');
                return;
            }

            const website = websiteInput ? websiteInput.value.trim() : '';
            const websiteFb = document.getElementById('settings-website-feedback');
            if (website && !/^https?:\/\/.+/i.test(website)) {
                if (websiteInput) websiteInput.classList.add('is-invalid');
                if (websiteFb) websiteFb.classList.remove('d-none');
                showAlert('Địa chỉ liên kết cá nhân phải là URL hợp lệ (bắt đầu bằng http:// hoặc https://).', 'danger');
                return;
            } else {
                if (websiteInput) websiteInput.classList.remove('is-invalid');
                if (websiteFb) websiteFb.classList.add('d-none');
            }

            const payload = {
                displayName: finalDisplayName,
                bio: bioInput ? bioInput.value.trim() : '',
                website: website ? website : null
            };

            if (mediaIdInput && mediaIdInput.value) {
                if (mediaIdInput.value === 'null') {
                    payload.avatarMediaId = null;
                } else if (!isNaN(mediaIdInput.value)) {
                    payload.avatarMediaId = Number(mediaIdInput.value);
                }
            }

            const coverMediaIdInput = document.getElementById('settings-cover-media-id');
            if (coverMediaIdInput && coverMediaIdInput.value) {
                if (coverMediaIdInput.value === 'null') {
                    payload.coverMediaId = null;
                } else if (!isNaN(coverMediaIdInput.value)) {
                    payload.coverMediaId = Number(coverMediaIdInput.value);
                }
            }

            // Lock UI & show spinner
            if (submitBtn) submitBtn.disabled = true;
            if (spinner) spinner.classList.remove('d-none');
            if (btnText) btnText.textContent = 'Đang lưu...';

            try {
                const response = await window.alouteHttp.request('/api/users/me/profile', {
                    method: 'PATCH',
                    body: payload
                });
                const result = await window.alouteHttp.readJson(response);

                if (response.ok) {
                    showAlert('Cập nhật hồ sơ cá nhân thành công!', 'success');
                    if (mediaIdInput) mediaIdInput.value = '';

                    // Live update header & sidebar if matching elements exist
                    if (result && result.data) {
                        const newName = result.data.displayName || result.data.username;
                        document.querySelectorAll('.sidebar-user-name, #navbar-user-display-name').forEach(el => {
                            el.textContent = newName;
                        });
                        if (result.data.avatarUrl) {
                            document.querySelectorAll('.sidebar-wrapper .avatar-img, .aloute-user-dropdown-btn .avatar-img').forEach(el => {
                                el.src = result.data.avatarUrl;
                            });
                        }
                    }
                } else {
                    const errorMsg = (result && result.message) || 'Không thể lưu hồ sơ cá nhân.';
                    showAlert(errorMsg, 'danger');
                }
            } catch (err) {
                showAlert('Lỗi kết nối khi cập nhật hồ sơ: ' + err.message, 'danger');
            } finally {
                if (submitBtn) submitBtn.disabled = false;
                if (spinner) spinner.classList.add('d-none');
                if (btnText) btnText.textContent = 'Lưu thay đổi hồ sơ';
            }
        });
    }

    // =========================================================================
    // 6. Privacy Form Submit (isPrivate Toggle)
    // =========================================================================
    function initPrivacyForm() {
        const form = document.getElementById('settings-privacy-form');
        const switchEl = document.getElementById('settings-switch-private');
        const submitBtn = document.getElementById('settings-btn-save-privacy');
        const spinner = document.getElementById('settings-save-privacy-spinner');

        if (!form || !switchEl) return;

        form.addEventListener('submit', async function (e) {
            e.preventDefault();
            hideAlert();

            const isPrivate = switchEl.checked;
            const payload = { isPrivate: isPrivate };

            if (submitBtn) submitBtn.disabled = true;
            if (spinner) spinner.classList.remove('d-none');

            try {
                const response = await window.alouteHttp.request('/api/users/me/profile', {
                    method: 'PATCH',
                    body: payload
                });
                const result = await window.alouteHttp.readJson(response);

                if (response.ok) {
                    const msg = isPrivate
                        ? 'Đã bật chế độ tài khoản riêng tư. Chỉ những người bạn chấp thuận mới xem được bài viết.'
                        : 'Đã tắt chế độ riêng tư. Bài viết của bạn sẽ ở chế độ công khai theo thiết lập.';
                    showAlert(msg, 'success');
                } else {
                    const errorMsg = (result && result.message) || 'Không thể cập nhật thiết lập riêng tư.';
                    showAlert(errorMsg, 'danger');
                }
            } catch (err) {
                showAlert('Lỗi kết nối khi lưu thiết lập riêng tư: ' + err.message, 'danger');
            } finally {
                if (submitBtn) submitBtn.disabled = false;
                if (spinner) spinner.classList.add('d-none');
            }
        });
    }

    // =========================================================================
    // 7. Copy Profile Link
    // =========================================================================
    function initCopyProfileLink() {
        const copyBtn = document.getElementById('settings-btn-copy-profile-link');
        const label = document.getElementById('copy-link-text');
        if (!copyBtn) return;

        copyBtn.addEventListener('click', function () {
            const username = this.getAttribute('data-username') || '';
            const profileUrl = window.location.origin + '/users/by-username/' + encodeURIComponent(username);

            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(profileUrl).then(() => {
                    if (label) label.textContent = 'Đã sao chép!';
                    setTimeout(() => {
                        if (label) label.textContent = 'Sao chép liên kết';
                    }, 2000);
                });
            } else {
                showAlert('Liên kết trang cá nhân của bạn: ' + profileUrl, 'info');
            }
        });
    }

    // =========================================================================
    // 8. Password Form Submit (POST /api/users/me/password)
    // =========================================================================
    function initPasswordForm() {
        const form = document.getElementById('settings-password-form');
        const currentPasswordInput = document.getElementById('settings-input-current-password');
        const newPasswordInput = document.getElementById('settings-input-new-password');
        const confirmPasswordInput = document.getElementById('settings-input-confirm-password');
        const mismatchFeedback = document.getElementById('settings-password-mismatch-feedback');
        const submitBtn = document.getElementById('settings-btn-save-password');
        const spinner = document.getElementById('settings-save-password-spinner');

        if (!form) return;

        form.addEventListener('submit', async function (e) {
            e.preventDefault();
            hideAlert();

            const currentPassword = currentPasswordInput ? currentPasswordInput.value : '';
            const newPassword = newPasswordInput ? newPasswordInput.value : '';
            const confirmPassword = confirmPasswordInput ? confirmPasswordInput.value : '';

            if (!currentPassword) {
                showAlert('Vui lòng nhập mật khẩu hiện tại.', 'danger');
                return;
            }
            if (newPassword.length < 8) {
                showAlert('Mật khẩu mới phải có tối thiểu 8 ký tự.', 'danger');
                return;
            }
            if (newPassword !== confirmPassword) {
                if (mismatchFeedback) mismatchFeedback.classList.remove('d-none');
                showAlert('Mật khẩu xác nhận không khớp với mật khẩu mới.', 'danger');
                return;
            } else if (mismatchFeedback) {
                mismatchFeedback.classList.add('d-none');
            }

            const payload = {
                currentPassword: currentPassword,
                newPassword: newPassword,
                confirmPassword: confirmPassword
            };

            if (submitBtn) submitBtn.disabled = true;
            if (spinner) spinner.classList.remove('d-none');

            try {
                const response = await window.alouteHttp.request('/api/users/me/password', {
                    method: 'POST',
                    body: payload
                });
                const result = await window.alouteHttp.readJson(response);

                if (response.ok) {
                    showAlert('Đổi mật khẩu thành công! Phiên đăng nhập hiện tại đã được gia hạn an toàn.', 'success');
                    form.reset();
                } else {
                    const errorMsg = (result && result.message) || 'Không thể đổi mật khẩu. Vui lòng kiểm tra lại mật khẩu hiện tại.';
                    showAlert(errorMsg, 'danger');
                }
            } catch (err) {
                showAlert('Lỗi kết nối khi cập nhật mật khẩu: ' + err.message, 'danger');
            } finally {
                if (submitBtn) submitBtn.disabled = false;
                if (spinner) spinner.classList.add('d-none');
            }
        });
    }

    // =========================================================================
    // 9. Password Visibility Toggles
    // =========================================================================
    function initPasswordVisibilityToggles() {
        document.querySelectorAll('.btn-toggle-password').forEach(btn => {
            btn.addEventListener('click', function () {
                const targetId = this.getAttribute('data-target');
                const targetInput = document.getElementById(targetId);
                const icon = this.querySelector('i');
                if (!targetInput) return;

                if (targetInput.type === 'password') {
                    targetInput.type = 'text';
                    if (icon) {
                        icon.classList.remove('bi-eye');
                        icon.classList.add('bi-eye-slash');
                    }
                } else {
                    targetInput.type = 'password';
                    if (icon) {
                        icon.classList.remove('bi-eye-slash');
                        icon.classList.add('bi-eye');
                    }
                }
            });
        });
    }

    // =========================================================================
    // 10. Theme Selector (Light Campus / Dark Campus)
    // =========================================================================
    function initThemeSelector() {
        const lightCard = document.getElementById('theme-card-light');
        const darkCard = document.getElementById('theme-card-dark');
        const lightCheck = document.getElementById('theme-check-light');
        const darkCheck = document.getElementById('theme-check-dark');

        if (!lightCard || !darkCard) return;

        const currentTheme = localStorage.getItem('aloute-theme') || 'light';
        applyTheme(currentTheme);

        function applyTheme(theme) {
            if (theme === 'dark') {
                darkCard.classList.add('border-primary', 'shadow-sm');
                lightCard.classList.remove('border-primary', 'shadow-sm');
                if (darkCheck) darkCheck.classList.remove('d-none');
                if (lightCheck) lightCheck.classList.add('d-none');
                document.documentElement.setAttribute('data-bs-theme', 'dark');
            } else {
                lightCard.classList.add('border-primary', 'shadow-sm');
                darkCard.classList.remove('border-primary', 'shadow-sm');
                if (lightCheck) lightCheck.classList.remove('d-none');
                if (darkCheck) darkCheck.classList.add('d-none');
                document.documentElement.setAttribute('data-bs-theme', 'light');
            }
            localStorage.setItem('aloute-theme', theme);
        }

        lightCard.addEventListener('click', () => {
            applyTheme('light');
            showAlert('Đã chuyển sang phong cách Giảng đường Sáng (Light Campus HCMUTE).', 'success');
        });

        darkCard.addEventListener('click', () => {
            applyTheme('dark');
            showAlert('Đã chuyển sang chế độ Tối (Dark Campus).', 'info');
        });
    }

    // =========================================================================
    // 11. Logout Handlers
    // =========================================================================
    function initLogoutButtons() {
        document.querySelectorAll('[data-action="logout"], #settings-btn-logout').forEach(btn => {
            btn.addEventListener('click', async function (e) {
                e.preventDefault();
                if (!confirm('Bạn có chắc chắn muốn đăng xuất khỏi tài khoản không?')) {
                    return;
                }
                try {
                    await window.alouteHttp.request('/api/auth/logout', { method: 'POST' });
                } catch (_) {}
                window.location.href = '/login';
            });
        });
    }

    // =========================================================================
    // 12. Two-Factor Authentication (2FA) Handlers
    // =========================================================================
    function initTwoFactorSettings() {
        let currentBackupCodes = [];

        // 1. Start Setup
        const btnStartSetup = document.getElementById('btn-start-2fa-setup');
        const setupModalEl = document.getElementById('modal-2fa-setup');
        const setupModal = setupModalEl && window.bootstrap ? new window.bootstrap.Modal(setupModalEl) : null;

        if (btnStartSetup && setupModal) {
            btnStartSetup.addEventListener('click', async function () {
                setupModal.show();
                const loadingEl = document.getElementById('setup-2fa-loading');
                const contentEl = document.getElementById('setup-2fa-content');
                const qrImg = document.getElementById('setup-2fa-qr-img');
                const secretKeyInput = document.getElementById('setup-2fa-secret-key');
                const feedbackEl = document.getElementById('setup-2fa-feedback');
                const codeInput = document.getElementById('setup-2fa-input-code');

                if (loadingEl) loadingEl.classList.remove('d-none');
                if (contentEl) contentEl.classList.add('d-none');
                if (feedbackEl) feedbackEl.classList.add('d-none');
                if (codeInput) codeInput.value = '';

                try {
                    const response = await window.alouteHttp.request('/api/users/me/2fa/setup', { method: 'POST' });
                    const result = await window.alouteHttp.readJson(response);

                    if (response.ok && result && result.data) {
                        if (qrImg) qrImg.src = result.data.qrCodeDataUrl;
                        if (secretKeyInput) secretKeyInput.value = result.data.secretKey;
                        if (loadingEl) loadingEl.classList.add('d-none');
                        if (contentEl) contentEl.classList.remove('d-none');
                        if (codeInput) setTimeout(() => codeInput.focus(), 300);
                    } else {
                        if (setupModal) setupModal.hide();
                        showAlert('Không thể khởi tạo mã QR bảo mật. Vui lòng thử lại.', 'danger');
                    }
                } catch (err) {
                    if (setupModal) setupModal.hide();
                    showAlert('Lỗi kết nối khi khởi tạo 2FA. Vui lòng thử lại.', 'danger');
                }
            });
        }

        // 2. Copy Secret Key Button
        const btnCopySecret = document.getElementById('btn-copy-2fa-secret');
        if (btnCopySecret) {
            btnCopySecret.addEventListener('click', function () {
                const secretKeyInput = document.getElementById('setup-2fa-secret-key');
                const copyText = document.getElementById('copy-secret-text');
                if (secretKeyInput && secretKeyInput.value) {
                    navigator.clipboard.writeText(secretKeyInput.value).then(() => {
                        if (copyText) copyText.textContent = 'Đã chép!';
                        setTimeout(() => { if (copyText) copyText.textContent = 'Sao chép'; }, 2000);
                    });
                }
            });
        }

        // 3. Confirm 2FA Setup
        const setupForm = document.getElementById('setup-2fa-form');
        const backupModalEl = document.getElementById('modal-2fa-backup-codes');
        const backupModal = backupModalEl && window.bootstrap ? new window.bootstrap.Modal(backupModalEl) : null;

        if (setupForm) {
            setupForm.addEventListener('submit', async function (e) {
                e.preventDefault();
                const codeInput = document.getElementById('setup-2fa-input-code');
                const feedbackEl = document.getElementById('setup-2fa-feedback');
                const spinner = document.getElementById('setup-2fa-spinner');
                const submitBtn = document.getElementById('btn-confirm-2fa-setup');

                const code = codeInput ? codeInput.value.trim() : '';
                if (!code || code.length !== 6) {
                    if (feedbackEl) {
                        feedbackEl.textContent = 'Vui lòng nhập đúng 6 chữ số từ ứng dụng Google Authenticator.';
                        feedbackEl.classList.remove('d-none');
                    }
                    if (codeInput) codeInput.focus();
                    return;
                }

                if (submitBtn) submitBtn.disabled = true;
                if (spinner) spinner.classList.remove('d-none');
                if (feedbackEl) feedbackEl.classList.add('d-none');

                try {
                    const response = await window.alouteHttp.request('/api/users/me/2fa/enable', {
                        method: 'POST',
                        body: { code: code }
                    });
                    const result = await window.alouteHttp.readJson(response);

                    if (response.ok && result && result.data) {
                        currentBackupCodes = result.data.backupCodes || [];
                        renderBackupCodes(currentBackupCodes);
                        if (setupModal) setupModal.hide();
                        if (backupModal) backupModal.show();
                    } else {
                        const errMsg = (result && result.error && result.error.message) || 'Mã xác thực không chính xác. Vui lòng thử lại.';
                        if (feedbackEl) {
                            feedbackEl.textContent = errMsg;
                            feedbackEl.classList.remove('d-none');
                        }
                    }
                } catch (err) {
                    if (feedbackEl) {
                        feedbackEl.textContent = 'Lỗi kết nối máy chủ. Vui lòng thử lại sau.';
                        feedbackEl.classList.remove('d-none');
                    }
                } finally {
                    if (submitBtn) submitBtn.disabled = false;
                    if (spinner) spinner.classList.add('d-none');
                }
            });
        }

        // 1B. Start Email 2FA Setup
        const btnOpenEmail2fa = document.getElementById('btn-open-email-2fa');
        const emailModalEl = document.getElementById('modal-2fa-email');
        const emailModal = emailModalEl && window.bootstrap ? new window.bootstrap.Modal(emailModalEl) : null;
        const btnSendEmailCode = document.getElementById('btn-send-email-2fa-code');
        const sendEmailSpinner = document.getElementById('send-email-2fa-spinner');
        const sendEmailBtnText = document.getElementById('send-email-2fa-btn-text');
        const emailStatusTitle = document.getElementById('email-2fa-status-title');
        const emailStatusText = document.getElementById('email-2fa-status-text');
        const emailForm = document.getElementById('form-email-2fa-verify');
        const emailCodeInput = document.getElementById('email-2fa-code-input');
        const emailFeedback = document.getElementById('email-2fa-feedback');
        const emailVerifySpinner = document.getElementById('email-2fa-verify-spinner');
        const btnConfirmEmail2fa = document.getElementById('btn-confirm-email-2fa');

        let emailCountdownTimer = null;
        let emailCountdownActive = false;
        let emailCooldownEndsAt = 0;

        function startEmailCountdown(seconds) {
            let left = seconds;
            emailCooldownEndsAt = Date.now() + (seconds * 1000);
            emailCountdownActive = true;
            if (btnSendEmailCode) btnSendEmailCode.disabled = true;
            if (emailCountdownTimer) clearInterval(emailCountdownTimer);

            emailCountdownTimer = setInterval(() => {
                left--;
                if (left <= 0) {
                    clearInterval(emailCountdownTimer);
                    emailCountdownActive = false;
                    if (btnSendEmailCode) btnSendEmailCode.disabled = false;
                    if (sendEmailBtnText) sendEmailBtnText.textContent = 'Gửi lại mã';
                    if (emailStatusText) emailStatusText.textContent = 'Chưa nhận được? Bạn có thể gửi lại mã.';
                } else {
                    if (sendEmailBtnText) sendEmailBtnText.textContent = `Gửi lại (${left}s)`;
                }
            }, 1000);
        }

        async function triggerSendEmail2faCode() {
            if (btnSendEmailCode) btnSendEmailCode.disabled = true;
            if (sendEmailSpinner) sendEmailSpinner.classList.remove('d-none');
            if (emailFeedback) emailFeedback.classList.add('d-none');

            try {
                const response = await window.alouteHttp.request('/api/users/me/2fa/email/send-code', { method: 'POST' });
                const result = await window.alouteHttp.readJson(response);

                if (response.ok) {
                    if (emailStatusTitle) emailStatusTitle.textContent = 'Đã gửi mã thành công!';
                    if (emailStatusText) emailStatusText.textContent = 'Vui lòng kiểm tra hộp thư đến (hoặc thư rác).';
                    startEmailCountdown(60);
                    if (emailCodeInput) emailCodeInput.focus();
                } else {
                    const msg = (result && result.error && result.error.message) || 'Không thể gửi mã qua email.';
                    if (emailFeedback) {
                        emailFeedback.textContent = msg;
                        emailFeedback.classList.remove('d-none');
                    }
                    if (btnSendEmailCode) btnSendEmailCode.disabled = false;
                }
            } catch (err) {
                if (emailFeedback) {
                    emailFeedback.textContent = 'Lỗi kết nối khi gửi email xác thực.';
                    emailFeedback.classList.remove('d-none');
                }
                if (btnSendEmailCode) btnSendEmailCode.disabled = false;
            } finally {
                if (sendEmailSpinner) sendEmailSpinner.classList.add('d-none');
            }
        }

        if (btnOpenEmail2fa && emailModal) {
            btnOpenEmail2fa.addEventListener('click', function () {
                emailModal.show();
                if (emailCodeInput) emailCodeInput.value = '';
                if (emailFeedback) emailFeedback.classList.add('d-none');
                if (emailCountdownActive && Date.now() < emailCooldownEndsAt) {
                    if (emailCodeInput) emailCodeInput.focus();
                } else {
                    triggerSendEmail2faCode();
                }
            });
        }

        if (btnSendEmailCode) {
            btnSendEmailCode.addEventListener('click', triggerSendEmail2faCode);
        }

        if (emailForm) {
            emailForm.addEventListener('submit', async function (e) {
                e.preventDefault();
                const code = emailCodeInput ? emailCodeInput.value.trim() : '';
                if (!code || code.length !== 6) {
                    if (emailFeedback) {
                        emailFeedback.textContent = 'Vui lòng nhập đủ 6 chữ số mã xác thực.';
                        emailFeedback.classList.remove('d-none');
                    }
                    return;
                }

                if (btnConfirmEmail2fa) btnConfirmEmail2fa.disabled = true;
                if (emailVerifySpinner) emailVerifySpinner.classList.remove('d-none');
                if (emailFeedback) emailFeedback.classList.add('d-none');

                try {
                    const response = await window.alouteHttp.request('/api/users/me/2fa/email/enable', {
                        method: 'POST',
                        body: { code: code }
                    });
                    const result = await window.alouteHttp.readJson(response);

                    if (response.ok && result && result.data) {
                        currentBackupCodes = result.data.backupCodes || [];
                        renderBackupCodes(currentBackupCodes);
                        if (emailModal) emailModal.hide();
                        if (backupModal) backupModal.show();
                    } else {
                        const errMsg = (result && result.error && result.error.message) || 'Mã xác thực email không chính xác hoặc đã hết hạn.';
                        if (emailFeedback) {
                            emailFeedback.textContent = errMsg;
                            emailFeedback.classList.remove('d-none');
                        }
                    }
                } catch (err) {
                    if (emailFeedback) {
                        emailFeedback.textContent = 'Lỗi kết nối máy chủ. Vui lòng thử lại sau.';
                        emailFeedback.classList.remove('d-none');
                    }
                } finally {
                    if (btnConfirmEmail2fa) btnConfirmEmail2fa.disabled = false;
                    if (emailVerifySpinner) emailVerifySpinner.classList.add('d-none');
                }
            });
        }

        function renderBackupCodes(codes) {
            const container = document.getElementById('backup-codes-container');
            if (!container) return;
            container.innerHTML = '';
            codes.forEach(code => {
                const col = document.createElement('div');
                col.className = 'col-6 col-sm-3';
                col.innerHTML = `<div class="p-2 border rounded text-center font-monospace fw-bold bg-white shadow-sm text-dark">${code}</div>`;
                container.appendChild(col);
            });
        }

        // 4. Copy All Backup Codes
        const btnCopyBackup = document.getElementById('btn-copy-backup-codes');
        if (btnCopyBackup) {
            btnCopyBackup.addEventListener('click', function () {
                if (currentBackupCodes.length === 0) return;
                const text = "MÃ KHÔI PHỤC DỰ PHÒNG ALOUTE (2FA):\n" + currentBackupCodes.join('\n');
                navigator.clipboard.writeText(text).then(() => {
                    const originalHtml = btnCopyBackup.innerHTML;
                    btnCopyBackup.innerHTML = '<i class="bi bi-check2 me-1"></i>Đã sao chép!';
                    setTimeout(() => { btnCopyBackup.innerHTML = originalHtml; }, 2000);
                });
            });
        }

        // 5. Download Backup Codes .txt
        const btnDownloadBackup = document.getElementById('btn-download-backup-codes');
        if (btnDownloadBackup) {
            btnDownloadBackup.addEventListener('click', function () {
                if (currentBackupCodes.length === 0) return;
                const dateStr = new Date().toLocaleString('vi-VN');
                const content = `=================================================\n` +
                                `ALOUTE — MÃ KHÔI PHỤC BẢO MẬT 2 LỚP (2FA BACKUP CODES)\n` +
                                `Ngày tạo: ${dateStr}\n` +
                                `=================================================\n\n` +
                                currentBackupCodes.map((c, i) => `[${i + 1}] ${c}`).join('\n') +
                                `\n\nLưu ý: Mỗi mã chỉ có hiệu lực sử dụng 01 lần duy nhất.\n` +
                                `Giữ file này ở nơi an toàn để tránh bị mất quyền truy cập tài khoản.\n`;
                const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `aloute-backup-codes-${Date.now()}.txt`;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                URL.revokeObjectURL(url);
            });
        }

        // 6. Close Backup Modal -> Reload to update status card
        const btnCloseBackup = document.getElementById('btn-close-backup-codes');
        if (btnCloseBackup) {
            btnCloseBackup.addEventListener('click', function () {
                window.location.href = '/settings/security';
            });
        }

        // 7. Disable 2FA
        const btnOpenDisable = document.getElementById('btn-open-disable-2fa');
        const disableModalEl = document.getElementById('modal-2fa-disable');
        const disableModal = disableModalEl && window.bootstrap ? new window.bootstrap.Modal(disableModalEl) : null;
        const disableForm = document.getElementById('form-2fa-disable');

        if (btnOpenDisable && disableModal) {
            btnOpenDisable.addEventListener('click', function () {
                const pwInput = document.getElementById('disable-2fa-password');
                const codeInput = document.getElementById('disable-2fa-code');
                const feedbackEl = document.getElementById('disable-2fa-feedback');
                const emailSentFeedback = document.getElementById('disable-email-sent-feedback');
                if (pwInput) pwInput.value = '';
                if (codeInput) codeInput.value = '';
                if (feedbackEl) feedbackEl.classList.add('d-none');
                if (emailSentFeedback) emailSentFeedback.classList.add('d-none');
                disableModal.show();
            });
        }

        const btnDisableSendEmailOtp = document.getElementById('btn-disable-send-email-otp');
        const disableEmailSentFeedback = document.getElementById('disable-email-sent-feedback');
        let disableEmailCooldownTimer = null;
        function startDisableEmailCountdown(seconds) {
            let left = seconds;
            if (btnDisableSendEmailOtp) btnDisableSendEmailOtp.disabled = true;
            if (disableEmailCooldownTimer) clearInterval(disableEmailCooldownTimer);
            disableEmailCooldownTimer = setInterval(() => {
                left--;
                if (left <= 0) {
                    clearInterval(disableEmailCooldownTimer);
                    if (btnDisableSendEmailOtp) {
                        btnDisableSendEmailOtp.disabled = false;
                        btnDisableSendEmailOtp.innerHTML = '<i class="bi bi-envelope me-1"></i>Gửi lại mã qua Email';
                    }
                } else {
                    if (btnDisableSendEmailOtp) {
                        btnDisableSendEmailOtp.innerHTML = `<i class="bi bi-hourglass-split me-1"></i>Gửi lại (${left}s)`;
                    }
                }
            }, 1000);
        }

        if (btnDisableSendEmailOtp) {
            btnDisableSendEmailOtp.addEventListener('click', async function () {
                btnDisableSendEmailOtp.disabled = true;
                btnDisableSendEmailOtp.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Đang gửi...';
                try {
                    const response = await window.alouteHttp.request('/api/users/me/2fa/email/send-code', { method: 'POST' });
                    const result = await window.alouteHttp.readJson(response);
                    if (response.ok) {
                        if (disableEmailSentFeedback) disableEmailSentFeedback.classList.remove('d-none');
                        const codeInput = document.getElementById('disable-2fa-code');
                        if (codeInput) codeInput.focus();
                        startDisableEmailCountdown(60);
                    } else {
                        const msg = (result && result.error && result.error.message) || 'Không thể gửi mã xác thực qua email.';
                        showAlert(msg, 'danger');
                        btnDisableSendEmailOtp.disabled = false;
                        btnDisableSendEmailOtp.innerHTML = '<i class="bi bi-envelope me-1"></i>Gửi lại mã qua Email';
                    }
                } catch (e) {
                    showAlert('Lỗi kết nối khi gửi mã qua email.', 'danger');
                    btnDisableSendEmailOtp.disabled = false;
                    btnDisableSendEmailOtp.innerHTML = '<i class="bi bi-envelope me-1"></i>Gửi lại mã qua Email';
                }
            });
        }

        if (disableForm) {
            disableForm.addEventListener('submit', async function (e) {
                e.preventDefault();
                const pwInput = document.getElementById('disable-2fa-password');
                const codeInput = document.getElementById('disable-2fa-code');
                const feedbackEl = document.getElementById('disable-2fa-feedback');
                const spinner = document.getElementById('disable-2fa-spinner');
                const submitBtn = document.getElementById('btn-confirm-disable-2fa');

                const currentPassword = pwInput ? pwInput.value : '';
                const code = codeInput ? codeInput.value.trim() : '';

                if (!currentPassword || !code) {
                    if (feedbackEl) {
                        feedbackEl.textContent = 'Vui lòng nhập đầy đủ mật khẩu và mã xác thực.';
                        feedbackEl.classList.remove('d-none');
                    }
                    return;
                }

                if (submitBtn) submitBtn.disabled = true;
                if (spinner) spinner.classList.remove('d-none');
                if (feedbackEl) feedbackEl.classList.add('d-none');

                try {
                    const response = await window.alouteHttp.request('/api/users/me/2fa/disable', {
                        method: 'POST',
                        body: { currentPassword: currentPassword, code: code }
                    });
                    const result = await window.alouteHttp.readJson(response);

                    if (response.ok) {
                        if (disableModal) disableModal.hide();
                        showAlert('Đã tắt tính năng bảo mật 2 lớp thành công.', 'success');
                        setTimeout(() => { window.location.href = '/settings/security'; }, 800);
                    } else {
                        const errMsg = (result && result.error && result.error.message) || 'Mật khẩu hoặc mã xác thực không chính xác.';
                        if (feedbackEl) {
                            feedbackEl.textContent = errMsg;
                            feedbackEl.classList.remove('d-none');
                        }
                    }
                } catch (err) {
                    if (feedbackEl) {
                        feedbackEl.textContent = 'Lỗi kết nối máy chủ. Vui lòng thử lại sau.';
                        feedbackEl.classList.remove('d-none');
                    }
                } finally {
                    if (submitBtn) submitBtn.disabled = false;
                    if (spinner) spinner.classList.add('d-none');
                }
            });
        }

        // 8. Regenerate Backup Codes
        const btnOpenRegen = document.getElementById('btn-open-regenerate-backup-codes');
        const regenModalEl = document.getElementById('modal-2fa-regenerate');
        const regenModal = regenModalEl && window.bootstrap ? new window.bootstrap.Modal(regenModalEl) : null;
        const regenForm = document.getElementById('form-2fa-regenerate');

        if (btnOpenRegen && regenModal) {
            btnOpenRegen.addEventListener('click', function () {
                const pwInput = document.getElementById('regenerate-2fa-password');
                const feedbackEl = document.getElementById('regenerate-2fa-feedback');
                if (pwInput) pwInput.value = '';
                if (feedbackEl) feedbackEl.classList.add('d-none');
                regenModal.show();
            });
        }

        if (regenForm) {
            regenForm.addEventListener('submit', async function (e) {
                e.preventDefault();
                const pwInput = document.getElementById('regenerate-2fa-password');
                const feedbackEl = document.getElementById('regenerate-2fa-feedback');
                const spinner = document.getElementById('regenerate-2fa-spinner');
                const submitBtn = document.getElementById('btn-confirm-regenerate-2fa');

                const currentPassword = pwInput ? pwInput.value : '';
                if (!currentPassword) {
                    if (feedbackEl) {
                        feedbackEl.textContent = 'Vui lòng nhập mật khẩu tài khoản.';
                        feedbackEl.classList.remove('d-none');
                    }
                    return;
                }

                if (submitBtn) submitBtn.disabled = true;
                if (spinner) spinner.classList.remove('d-none');
                if (feedbackEl) feedbackEl.classList.add('d-none');

                try {
                    const response = await window.alouteHttp.request('/api/users/me/2fa/backup-codes', {
                        method: 'POST',
                        body: { currentPassword: currentPassword }
                    });
                    const result = await window.alouteHttp.readJson(response);

                    if (response.ok && result && result.data) {
                        currentBackupCodes = result.data || [];
                        renderBackupCodes(currentBackupCodes);
                        if (regenModal) regenModal.hide();
                        if (backupModal) backupModal.show();
                    } else {
                        const errMsg = (result && result.error && result.error.message) || 'Mật khẩu tài khoản không chính xác.';
                        if (feedbackEl) {
                            feedbackEl.textContent = errMsg;
                            feedbackEl.classList.remove('d-none');
                        }
                    }
                } catch (err) {
                    if (feedbackEl) {
                        feedbackEl.textContent = 'Lỗi kết nối máy chủ. Vui lòng thử lại sau.';
                        feedbackEl.classList.remove('d-none');
                    }
                } finally {
                    if (submitBtn) submitBtn.disabled = false;
                    if (spinner) spinner.classList.add('d-none');
                }
            });
        }
    }

    // =========================================================================
    // 5. Blocked Users Management
    // =========================================================================
    function initBlockedUsers() {
            const blockedUsersLoading = document.getElementById('blocked-users-loading');
            const blockedUsersEmpty = document.getElementById('blocked-users-empty');
            const blockedUsersList = document.getElementById('blocked-users-list');
            const btnRefreshBlocked = document.getElementById('btn-refresh-blocked-users');
            const formQuickBlock = document.getElementById('form-quick-block');
            const inputBlockUsername = document.getElementById('input-block-username');
            const btnSubmitBlock = document.getElementById('btn-submit-quick-block');
            const blockSpinner = document.getElementById('block-user-spinner');
            const blockFeedback = document.getElementById('block-feedback');

            const unblockModalEl = document.getElementById('modal-confirm-unblock');
            const unblockModal = unblockModalEl && window.bootstrap ? new window.bootstrap.Modal(unblockModalEl) : null;
            const unblockConfirmText = document.getElementById('unblock-confirm-text');
            const unblockFeedback = document.getElementById('unblock-feedback');
            const btnDoUnblock = document.getElementById('btn-do-unblock');
            const unblockSpinner = document.getElementById('unblock-spinner');
            let pendingUnblockUserId = null;
            let pendingUnblockUsername = null;

            async function loadBlockedUsers() {
                if (!blockedUsersList) return;
                if (blockedUsersLoading) blockedUsersLoading.classList.remove('d-none');
                if (blockedUsersEmpty) blockedUsersEmpty.classList.add('d-none');
                blockedUsersList.innerHTML = '';

                try {
                    const response = await window.alouteHttp.request('/api/social/blocks');
                    const result = await window.alouteHttp.readJson(response);

                    if (response.ok && result && result.data) {
                        const list = result.data || [];
                        if (list.length === 0) {
                            if (blockedUsersEmpty) blockedUsersEmpty.classList.remove('d-none');
                        } else {
                            renderBlockedUsers(list);
                        }
                    } else {
                        blockedUsersList.innerHTML = '<div class="alert alert-warning small mb-0">Không thể tải danh sách chặn. Vui lòng thử lại sau.</div>';
                    }
                } catch (err) {
                    blockedUsersList.innerHTML = '<div class="alert alert-danger small mb-0">Lỗi kết nối khi tải danh sách chặn.</div>';
                } finally {
                    if (blockedUsersLoading) blockedUsersLoading.classList.add('d-none');
                }
            }

            window.__alouteLoadBlockedUsers = loadBlockedUsers;

            function renderBlockedUsers(users) {
                blockedUsersList.innerHTML = '';
                users.forEach(u => {
                    const item = document.createElement('div');
                    item.className = 'p-3 border rounded-3 bg-white d-flex align-items-center justify-content-between gap-3 shadow-sm';
                    const avatar = u.avatarUrl || '/images/avatar-fallback.svg';
                    const dispName = u.displayName || u.username;
                    item.innerHTML = `
                        <div class="d-flex align-items-center gap-3">
                            <img src="${avatar}" alt="${dispName}" class="rounded-circle border object-fit-cover" width="44" height="44">
                            <div>
                                <div class="fw-bold small text-dark d-flex align-items-center gap-1">
                                    <span>${dispName}</span>
                                    ${u.isVerified ? '<i class="bi bi-patch-check-fill text-primary" title="Đã xác thực"></i>' : ''}
                                </div>
                                <div class="text-muted small">@${u.username}</div>
                            </div>
                        </div>
                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-pill px-3 btn-unblock-trigger" data-user-id="${u.id}" data-username="${u.username}">
                            Bỏ chặn
                        </button>
                    `;
                    blockedUsersList.appendChild(item);
                });
            }

            if (blockedUsersList) {
                blockedUsersList.addEventListener('click', function(e) {
                    const btn = e.target.closest('.btn-unblock-trigger');
                    if (btn) {
                        pendingUnblockUserId = btn.dataset.userId;
                        pendingUnblockUsername = btn.dataset.username;
                        if (unblockConfirmText) {
                            unblockConfirmText.textContent = `Bạn có chắc chắn muốn bỏ chặn @${pendingUnblockUsername}? Hai bạn sẽ có thể nhìn thấy bài viết của nhau trở lại.`;
                        }
                        if (unblockFeedback) unblockFeedback.classList.add('d-none');
                        if (unblockModal) unblockModal.show();
                    }
                });
            }

            if (btnDoUnblock) {
                btnDoUnblock.addEventListener('click', async function() {
                    if (!pendingUnblockUserId) return;
                    btnDoUnblock.disabled = true;
                    if (unblockSpinner) unblockSpinner.classList.remove('d-none');
                    if (unblockFeedback) unblockFeedback.classList.add('d-none');

                    try {
                        const response = await window.alouteHttp.request('/api/social/blocks/' + pendingUnblockUserId, { method: 'DELETE' });
                        if (response.ok || response.status === 204) {
                            if (unblockModal) unblockModal.hide();
                            showAlert('Đã bỏ chặn @' + pendingUnblockUsername + ' thành công.', 'success');
                            loadBlockedUsers();
                        } else {
                            const res = await window.alouteHttp.readJson(response);
                            if (unblockFeedback) {
                                unblockFeedback.textContent = (res && res.error && res.error.message) || 'Không thể bỏ chặn.';
                                unblockFeedback.classList.remove('d-none');
                            }
                        }
                    } catch (err) {
                        if (unblockFeedback) {
                            unblockFeedback.textContent = 'Lỗi kết nối máy chủ.';
                            unblockFeedback.classList.remove('d-none');
                        }
                    } finally {
                        btnDoUnblock.disabled = false;
                        if (unblockSpinner) unblockSpinner.classList.add('d-none');
                    }
                });
            }

            if (formQuickBlock) {
                formQuickBlock.addEventListener('submit', async function(e) {
                    e.preventDefault();
                    const username = inputBlockUsername ? inputBlockUsername.value.trim() : '';
                    if (!username) return;

                    if (btnSubmitBlock) btnSubmitBlock.disabled = true;
                    if (blockSpinner) blockSpinner.classList.remove('d-none');
                    if (blockFeedback) blockFeedback.classList.add('d-none');

                    try {
                        const clean = username.startsWith('@') ? username.substring(1) : username;
                        const response = await window.alouteHttp.request('/api/social/blocks/by-username/' + encodeURIComponent(clean), {
                            method: 'POST'
                        });
                        const result = await window.alouteHttp.readJson(response);

                        if (response.ok) {
                            if (inputBlockUsername) inputBlockUsername.value = '';
                            if (blockFeedback) {
                                blockFeedback.textContent = 'Đã chặn tài khoản @' + clean + ' thành công.';
                                blockFeedback.className = 'small mt-2 text-success';
                                blockFeedback.classList.remove('d-none');
                            }
                            showAlert('Đã chặn @' + clean + '.', 'success');
                            loadBlockedUsers();
                        } else {
                            const errMsg = (result && result.error && result.error.message) || 'Không thể chặn tài khoản này.';
                            if (blockFeedback) {
                                blockFeedback.textContent = errMsg;
                                blockFeedback.className = 'small mt-2 text-danger';
                                blockFeedback.classList.remove('d-none');
                            }
                        }
                    } catch (err) {
                        if (blockFeedback) {
                            blockFeedback.textContent = 'Lỗi kết nối máy chủ.';
                            blockFeedback.className = 'small mt-2 text-danger';
                            blockFeedback.classList.remove('d-none');
                        }
                    } finally {
                        if (btnSubmitBlock) btnSubmitBlock.disabled = false;
                        if (blockSpinner) blockSpinner.classList.add('d-none');
                    }
                });
            }

            if (btnRefreshBlocked) {
                btnRefreshBlocked.addEventListener('click', loadBlockedUsers);
            }

            // Load on init if current section is blocking
            const pathParts = window.location.pathname.split('/').filter(Boolean);
            if (pathParts.length >= 2 && pathParts[1].toLowerCase() === 'blocking') {
                loadBlockedUsers();
            }
        }

        // =========================================================================
        // 5b. Muted Users Management
        // =========================================================================
        function initMutedUsers() {
            const mutedUsersLoading = document.getElementById('muted-users-loading');
            const mutedUsersEmpty = document.getElementById('muted-users-empty');
            const mutedUsersList = document.getElementById('muted-users-list');
            const btnRefreshMuted = document.getElementById('btn-refresh-muted-users');
            const formQuickMute = document.getElementById('form-quick-mute');
            const inputMuteUsername = document.getElementById('input-mute-username');
            const btnSubmitMute = document.getElementById('btn-submit-quick-mute');
            const muteSpinner = document.getElementById('mute-user-spinner');
            const muteFeedback = document.getElementById('mute-feedback');

            const unmuteModalEl = document.getElementById('modal-confirm-unmute');
            const unmuteModal = unmuteModalEl && window.bootstrap ? new window.bootstrap.Modal(unmuteModalEl) : null;
            const unmuteConfirmText = document.getElementById('unmute-confirm-text');
            const unmuteFeedback = document.getElementById('unmute-feedback');
            const btnDoUnmute = document.getElementById('btn-do-unmute');
            const unmuteSpinner = document.getElementById('unmute-spinner');
            let pendingUnmuteUserId = null;
            let pendingUnmuteUsername = null;

            async function loadMutedUsers() {
                if (!mutedUsersList) return;
                if (mutedUsersLoading) mutedUsersLoading.classList.remove('d-none');
                if (mutedUsersEmpty) mutedUsersEmpty.classList.add('d-none');
                mutedUsersList.innerHTML = '';

                try {
                    const response = await window.alouteHttp.request('/api/social/mutes');
                    const result = await window.alouteHttp.readJson(response);

                    if (response.ok && result && result.data) {
                        const list = result.data || [];
                        if (list.length === 0) {
                            if (mutedUsersEmpty) mutedUsersEmpty.classList.remove('d-none');
                        } else {
                            renderMutedUsers(list);
                        }
                    } else {
                        mutedUsersList.innerHTML = '<div class="alert alert-warning small mb-0">Không thể tải danh sách mute. Vui lòng thử lại sau.</div>';
                    }
                } catch (err) {
                    mutedUsersList.innerHTML = '<div class="alert alert-danger small mb-0">Lỗi kết nối khi tải danh sách mute.</div>';
                } finally {
                    if (mutedUsersLoading) mutedUsersLoading.classList.add('d-none');
                }
            }

            function renderMutedUsers(users) {
                mutedUsersList.innerHTML = '';
                users.forEach(u => {
                    const item = document.createElement('div');
                    item.className = 'd-flex align-items-center justify-content-between p-3 bg-white border rounded-3 shadow-sm';
                    const avatar = u.avatarUrl || '/images/avatar-fallback.svg';
                    const dispName = u.displayName || u.username;
                    item.innerHTML = `
                        <div class="d-flex align-items-center gap-3">
                            <img src="${avatar}" alt="${dispName}" class="rounded-circle border object-fit-cover" width="40" height="40">
                            <div>
                                <div class="fw-bold small text-dark d-flex align-items-center gap-1">
                                    <span>${dispName}</span>
                                    ${u.isVerified ? '<i class="bi bi-patch-check-fill text-primary" title="Đã xác thực"></i>' : ''}
                                </div>
                                <div class="text-muted small">@${u.username}</div>
                            </div>
                        </div>
                        <button type="button" class="btn btn-sm btn-outline-warning text-dark rounded-pill px-3 btn-unmute-trigger" data-user-id="${u.id}" data-username="${u.username}">
                            <i class="bi bi-bell me-1"></i>Bật lại thông báo
                        </button>
                    `;
                    mutedUsersList.appendChild(item);
                });
            }

            if (mutedUsersList) {
                mutedUsersList.addEventListener('click', function(e) {
                    const btn = e.target.closest('.btn-unmute-trigger');
                    if (btn) {
                        pendingUnmuteUserId = btn.dataset.userId;
                        pendingUnmuteUsername = btn.dataset.username;
                        if (unmuteConfirmText) {
                            unmuteConfirmText.textContent = `Bạn có chắc chắn muốn bật lại thông báo cho @${pendingUnmuteUsername}? Bạn sẽ nhận lại thông báo khi họ tương tác.`;
                        }
                        if (unmuteFeedback) unmuteFeedback.classList.add('d-none');
                        if (unmuteModal) unmuteModal.show();
                    }
                });
            }

            if (btnDoUnmute) {
                btnDoUnmute.addEventListener('click', async function() {
                    if (!pendingUnmuteUserId) return;
                    btnDoUnmute.disabled = true;
                    if (unmuteSpinner) unmuteSpinner.classList.remove('d-none');
                    if (unmuteFeedback) unmuteFeedback.classList.add('d-none');

                    try {
                        const response = await window.alouteHttp.request('/api/social/mutes/' + pendingUnmuteUserId, { method: 'DELETE' });
                        if (response.ok || response.status === 204) {
                            if (unmuteModal) unmuteModal.hide();
                            showAlert('Đã bật lại thông báo cho @' + pendingUnmuteUsername + ' thành công.', 'success');
                            loadMutedUsers();
                        } else {
                            const res = await window.alouteHttp.readJson(response);
                            if (unmuteFeedback) {
                                unmuteFeedback.textContent = (res && res.error && res.error.message) || 'Không thể bật lại thông báo.';
                                unmuteFeedback.classList.remove('d-none');
                            }
                        }
                    } catch (err) {
                        if (unmuteFeedback) {
                            unmuteFeedback.textContent = 'Lỗi kết nối máy chủ.';
                            unmuteFeedback.classList.remove('d-none');
                        }
                    } finally {
                        btnDoUnmute.disabled = false;
                        if (unmuteSpinner) unmuteSpinner.classList.add('d-none');
                    }
                });
            }

            if (formQuickMute) {
                formQuickMute.addEventListener('submit', async function(e) {
                    e.preventDefault();
                    const username = inputMuteUsername ? inputMuteUsername.value.trim() : '';
                    if (!username) return;

                    if (btnSubmitMute) btnSubmitMute.disabled = true;
                    if (muteSpinner) muteSpinner.classList.remove('d-none');
                    if (muteFeedback) muteFeedback.classList.add('d-none');

                    try {
                        const clean = username.startsWith('@') ? username.substring(1) : username;
                        const response = await window.alouteHttp.request('/api/social/mutes/by-username/' + encodeURIComponent(clean), {
                            method: 'POST'
                        });
                        const result = await window.alouteHttp.readJson(response);

                        if (response.ok) {
                            if (inputMuteUsername) inputMuteUsername.value = '';
                            if (muteFeedback) {
                                muteFeedback.textContent = 'Đã tắt thông báo tài khoản @' + clean + ' thành công.';
                                muteFeedback.className = 'small mt-2 text-success';
                                muteFeedback.classList.remove('d-none');
                            }
                            showAlert('Đã tắt thông báo của @' + clean + '.', 'success');
                            loadMutedUsers();
                        } else {
                            const errMsg = (result && result.error && result.error.message) || 'Không thể tắt thông báo tài khoản này.';
                            if (muteFeedback) {
                                muteFeedback.textContent = errMsg;
                                muteFeedback.className = 'small mt-2 text-danger';
                                muteFeedback.classList.remove('d-none');
                            }
                        }
                    } catch (err) {
                        if (muteFeedback) {
                            muteFeedback.textContent = 'Lỗi kết nối máy chủ.';
                            muteFeedback.className = 'small mt-2 text-danger';
                            muteFeedback.classList.remove('d-none');
                        }
                    } finally {
                        if (btnSubmitMute) btnSubmitMute.disabled = false;
                        if (muteSpinner) muteSpinner.classList.add('d-none');
                    }
                });
            }

            if (btnRefreshMuted) {
                btnRefreshMuted.addEventListener('click', loadMutedUsers);
            }

            const pathParts = window.location.pathname.split('/').filter(Boolean);
            if (pathParts.length >= 2 && pathParts[1].toLowerCase() === 'muted') {
                loadMutedUsers();
            }

            window.__alouteLoadMutedUsers = loadMutedUsers;
        }

        // =========================================================================
        // 6. Notification Settings Form
        // =========================================================================
        function initNotificationSettings() {
            const formNotify = document.getElementById('form-notification-settings');
            const btnSaveNotify = document.getElementById('btn-save-notification-settings');
            const saveNotifySpinner = document.getElementById('save-notifications-spinner');

            if (!formNotify) return;

            formNotify.addEventListener('submit', async function(e) {
                e.preventDefault();
                if (btnSaveNotify) btnSaveNotify.disabled = true;
                if (saveNotifySpinner) saveNotifySpinner.classList.remove('d-none');

                const payload = {
                    likesEnabled: document.getElementById('setting-notify-likes')?.checked ?? true,
                    commentsEnabled: document.getElementById('setting-notify-comments')?.checked ?? true,
                    followsEnabled: document.getElementById('setting-notify-follows')?.checked ?? true,
                    mentionsEnabled: document.getElementById('setting-notify-mentions')?.checked ?? true,
                    emailNotificationsEnabled: document.getElementById('setting-notify-email')?.checked ?? true,
                    soundEnabled: document.getElementById('setting-notify-sound')?.checked ?? true
                };

                try {
                    const response = await window.alouteHttp.request('/api/users/me/notifications/settings', {
                        method: 'PUT',
                        body: payload
                    });
                    const result = await window.alouteHttp.readJson(response);

                    if (response.ok) {
                        showAlert('Đã lưu thiết lập thông báo thành công!', 'success');
                    } else {
                        const msg = (result && result.error && result.error.message) || 'Không thể lưu cài đặt thông báo.';
                        showAlert(msg, 'danger');
                    }
                } catch (err) {
                    showAlert('Lỗi kết nối khi lưu cài đặt.', 'danger');
                } finally {
                    if (btnSaveNotify) btnSaveNotify.disabled = false;
                    if (saveNotifySpinner) saveNotifySpinner.classList.add('d-none');
                }
            });
        }

        // =========================================================================
        // 7. Sessions & Account Deactivation
        // =========================================================================
        function initSessionAndDeactivation() {
            // Logout other sessions
            const btnLogoutOthers = document.getElementById('btn-logout-others');
            const logoutOthersSpinner = document.getElementById('logout-others-spinner');

            if (btnLogoutOthers) {
                btnLogoutOthers.addEventListener('click', async function() {
                    if (!confirm('Bạn có chắc chắn muốn đăng xuất khỏi tất cả các thiết bị và trình duyệt khác không? Phiên làm việc hiện tại vẫn được giữ nguyên.')) {
                        return;
                    }
                    btnLogoutOthers.disabled = true;
                    if (logoutOthersSpinner) logoutOthersSpinner.classList.remove('d-none');

                    try {
                        const response = await window.alouteHttp.request('/api/users/me/sessions/logout-others', {
                            method: 'POST'
                        });
                        const result = await window.alouteHttp.readJson(response);
                        if (response.ok) {
                            showAlert('Đã đăng xuất khỏi tất cả các thiết bị khác thành công!', 'success');
                        } else {
                            const msg = (result && result.error && result.error.message) || 'Không thể đăng xuất các thiết bị khác.';
                            showAlert(msg, 'danger');
                        }
                    } catch (err) {
                        showAlert('Lỗi kết nối máy chủ.', 'danger');
                    } finally {
                        btnLogoutOthers.disabled = false;
                        if (logoutOthersSpinner) logoutOthersSpinner.classList.add('d-none');
                    }
                });
            }

            // Deactivate Account
            const btnOpenDeactivate = document.getElementById('btn-open-deactivate-modal');
            const deactivateModalEl = document.getElementById('modal-deactivate-account');
            const deactivateModal = deactivateModalEl && window.bootstrap ? new window.bootstrap.Modal(deactivateModalEl) : null;
            const formDeactivate = document.getElementById('form-confirm-deactivate');
            const deactivatePasswordInput = document.getElementById('deactivate-password-input');
            const deactivateFeedback = document.getElementById('deactivate-feedback');
            const btnConfirmDeactivate = document.getElementById('btn-confirm-deactivate-submit');
            const deactivateSpinner = document.getElementById('deactivate-spinner');

            if (btnOpenDeactivate && deactivateModal) {
                btnOpenDeactivate.addEventListener('click', function() {
                    if (deactivatePasswordInput) deactivatePasswordInput.value = '';
                    if (deactivateFeedback) deactivateFeedback.classList.add('d-none');
                    deactivateModal.show();
                });
            }

            if (formDeactivate) {
                formDeactivate.addEventListener('submit', async function(e) {
                    e.preventDefault();
                    const password = deactivatePasswordInput ? deactivatePasswordInput.value : '';
                    if (!password) {
                        if (deactivateFeedback) {
                            deactivateFeedback.textContent = 'Vui lòng nhập mật khẩu xác nhận.';
                            deactivateFeedback.classList.remove('d-none');
                        }
                        return;
                    }

                    if (btnConfirmDeactivate) btnConfirmDeactivate.disabled = true;
                    if (deactivateSpinner) deactivateSpinner.classList.remove('d-none');
                    if (deactivateFeedback) deactivateFeedback.classList.add('d-none');

                    try {
                        const response = await window.alouteHttp.request('/api/users/me/deactivate', {
                            method: 'POST',
                            body: { password: password }
                        });
                        const result = await window.alouteHttp.readJson(response);

                        if (response.ok) {
                            if (deactivateModal) deactivateModal.hide();
                            alert('Tài khoản của bạn đã được vô hiệu hóa thành công.');
                            window.location.href = '/login?deactivated=true';
                        } else {
                            const msg = (result && result.error && result.error.message) || 'Mật khẩu xác nhận không chính xác.';
                            if (deactivateFeedback) {
                                deactivateFeedback.textContent = msg;
                                deactivateFeedback.classList.remove('d-none');
                            }
                        }
                    } catch (err) {
                        if (deactivateFeedback) {
                            deactivateFeedback.textContent = 'Lỗi kết nối máy chủ.';
                            deactivateFeedback.classList.remove('d-none');
                        }
                    } finally {
                        if (btnConfirmDeactivate) btnConfirmDeactivate.disabled = false;
                        if (deactivateSpinner) deactivateSpinner.classList.add('d-none');
                    }
                });
            }
        }
})();
