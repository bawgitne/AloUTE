/**
 * AloUTE — Authentication Module Client Script
 * Handles semantic form submissions, DTO-aligned validation,
 * exact REST API integration (/api/auth/login, /api/auth/register),
 * duplicate-submit prevention, and accessible status/error rendering.
 */
(function () {
    'use strict';

    function statusBox(message, type, asHtml) {
        let box = document.getElementById('auth-status');
        if (!box) {
            box = document.createElement('div');
            box.id = 'auth-status';
            box.setAttribute('role', 'alert');
            box.setAttribute('aria-live', 'polite');
            const form = document.querySelector('form[data-action="login"], form[data-action="register"], form[data-action="forgot-password"], form[data-action="reset-password"], form[data-action="verify-otp"]');
            if (form) form.prepend(box);
            else document.body.prepend(box);
        }
        box.className = 'alert alert-' + (type || 'info') + ' py-2 small mb-3';
        if (asHtml) {
            box.innerHTML = message;
        } else {
            box.textContent = message;
        }
        box.classList.remove('d-none');
        box.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }

    function clearStatus() {
        const box = document.getElementById('auth-status');
        if (box) {
            box.classList.add('d-none');
            box.textContent = '';
        }
    }

    function clearErrors(form) {
        clearStatus();
        if (!form) return;
        form.querySelectorAll('.is-invalid').forEach(el => {
            el.classList.remove('is-invalid');
            el.setAttribute('aria-invalid', 'false');
        });
        form.querySelectorAll('input').forEach(el => {
            el.setAttribute('aria-invalid', 'false');
        });
        form.querySelectorAll('.invalid-feedback').forEach(el => {
            el.classList.add('d-none');
            el.textContent = '';
        });
    }

    function setFieldError(fieldId, message) {
        const input = document.getElementById(fieldId);
        if (input) {
            input.classList.add('is-invalid');
            input.setAttribute('aria-invalid', 'true');
        }
        const feedback = document.getElementById(fieldId + '-feedback');
        if (feedback) {
            feedback.textContent = message;
            feedback.classList.remove('d-none');
        }
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
        if (code === 'INTERNAL_SERVER_ERROR') return 'Hệ thống đang gặp sự cố. Vui lòng thử lại sau.';
        if (code === 'ACCESS_DENIED') return 'Bạn không có quyền thực hiện thao tác này.';

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
        if (message === 'Username already exists') {
            return 'Tên người dùng đã tồn tại.';
        }
        if (message === 'Email already exists') {
            return 'Địa chỉ email đã được sử dụng.';
        }
        if (message === 'Validation failed') {
            return 'Dữ liệu không hợp lệ. Vui lòng kiểm tra lại.';
        }

        // Generic Vietnamese status fallback, suppressing raw unknown English/technical messages
        return defaultMsg || 'Đã xảy ra lỗi không xác định. Vui lòng thử lại sau.';
    }

    // Safely translate raw backend field errors to Vietnamese with generic fallbacks
    function translateFieldError(field, rawMsgs) {
        if (window.console && typeof window.console.warn === 'function') {
            window.console.warn('[AloUTE] Field validation error diagnostic:', field, rawMsgs);
        }
        const text = (Array.isArray(rawMsgs) ? rawMsgs.join(' ') : String(rawMsgs || '')).toLowerCase();

        // Pattern matching for standard Spring / Hibernate validator messages
        if (text.includes('blank') || text.includes('empty') || text.includes('không được để trống') || text.includes('required')) {
            if (field === 'email') return 'Vui lòng nhập email.';
            if (field === 'password') return 'Vui lòng nhập mật khẩu.';
            if (field === 'username') return 'Vui lòng nhập tên người dùng.';
            if (field === 'confirmPassword') return 'Vui lòng nhập lại mật khẩu.';
            return 'Trường này không được để trống.';
        }

        if (text.includes('email') || text.includes('định dạng') || text.includes('format')) {
            return 'Email không đúng định dạng.';
        }

        if (text.includes('size') || text.includes('length') || text.includes('ký tự')) {
            if (field === 'username') return 'Tên người dùng phải từ 3 đến 50 ký tự (có thể chứa dấu cách).';
            if (field === 'password') return 'Mật khẩu phải có từ 8 đến 128 ký tự.';
            return 'Độ dài trường dữ liệu không hợp lệ.';
        }

        if (text.includes('match') || text.includes('khớp')) {
            return 'Mật khẩu xác nhận không khớp.';
        }

        // Generic Vietnamese field fallbacks
        const genericFallbacks = {
            'email': 'Email không hợp lệ.',
            'password': 'Mật khẩu không hợp lệ.',
            'username': 'Tên người dùng không hợp lệ.',
            'confirmPassword': 'Mật khẩu xác nhận không hợp lệ.'
        };

        return genericFallbacks[field] || 'Dữ liệu không hợp lệ.';
    }

    function setSubmitting(btn, isSubmitting, loadingText, defaultText, iconId, spinnerId) {
        if (!btn) return;
        btn.disabled = isSubmitting;
        const spinner = document.getElementById(spinnerId);
        const icon = document.getElementById(iconId);
        const textEl = btn.querySelector('span:not(.spinner-border)') || btn;

        if (isSubmitting) {
            if (spinner) spinner.classList.remove('d-none');
            if (icon) icon.classList.add('d-none');
            if (textEl) textEl.textContent = loadingText;
        } else {
            if (spinner) spinner.classList.add('d-none');
            if (icon) icon.classList.remove('d-none');
            if (textEl) textEl.textContent = defaultText;
        }
    }

    async function login(form) {
        clearErrors(form);

        const emailInput = document.getElementById('email');
        const passwordInput = document.getElementById('password');
        const submitBtn = document.getElementById('login-submit-btn');

        const email = emailInput ? emailInput.value.trim() : '';
        const password = passwordInput ? passwordInput.value : '';

        let hasError = false;
        if (!email) {
            setFieldError('email', 'Vui lòng nhập email.');
            hasError = true;
        } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
            setFieldError('email', 'Email không đúng định dạng.');
            hasError = true;
        }

        if (!password) {
            setFieldError('password', 'Vui lòng nhập mật khẩu.');
            hasError = true;
        }

        if (hasError) {
            const firstInvalid = form.querySelector('.is-invalid');
            if (firstInvalid) firstInvalid.focus();
            return;
        }

        setSubmitting(submitBtn, true, 'Đang đăng nhập...', 'Đăng nhập', 'login-icon', 'login-spinner');
        statusBox('Đang kết nối xác thực...', 'info');

        try {
            const response = await fetch('/api/auth/login', {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                body: JSON.stringify({ email: email, password: password })
            });

            const body = await readJson(response);

            if (response.ok && (response.status === 200 || response.status === 204)) {
                statusBox('Đăng nhập thành công! Đang chuyển hướng đến bảng tin...', 'success');
                window.setTimeout(function () {
                    window.location.href = '/feed';
                }, 300);
                return;
            }

            setSubmitting(submitBtn, false, 'Đang đăng nhập...', 'Đăng nhập', 'login-icon', 'login-spinner');

            if (response.status === 401) {
                const code = body && body.error && body.error.code;
                if (code === 'AUTH_ACCOUNT_NOT_VERIFIED') {
                    const emailVal = emailInput ? emailInput.value.trim() : '';
                    const verifyUrl = '/verify-otp' + (emailVal ? '?email=' + encodeURIComponent(emailVal) : '');
                    const linkHtml = 'Tài khoản chưa được xác thực email. <a href="' + verifyUrl + '" class="alert-link fw-semibold">Bấm vào đây để xác thực OTP</a>.';
                    statusBox(linkHtml, 'warning', true);
                } else {
                    const msg = translateError(body && body.error, 'Email hoặc mật khẩu không chính xác.');
                    statusBox(msg, 'danger');
                }
                if (passwordInput) {
                    passwordInput.value = '';
                    passwordInput.focus();
                }
            } else if (response.status === 403) {
                const msg = translateError(body && body.error, 'Bạn không có quyền thực hiện thao tác này.');
                statusBox(msg, 'danger');
            } else if (response.status === 422) {
                if (body && body.error && body.error.fieldErrors) {
                    for (const [field, msgs] of Object.entries(body.error.fieldErrors)) {
                        setFieldError(field, translateFieldError(field, msgs));
                    }
                }
                const msg = translateError(body && body.error, 'Thông tin đăng nhập không hợp lệ.');
                statusBox(msg, 'danger');
            } else {
                const msg = translateError(body && body.error, 'Đăng nhập thất bại (HTTP ' + response.status + ').');
                statusBox(msg, 'danger');
            }
        } catch (error) {
            setSubmitting(submitBtn, false, 'Đang đăng nhập...', 'Đăng nhập', 'login-icon', 'login-spinner');
            statusBox('Lỗi kết nối tới máy chủ. Vui lòng kiểm tra mạng và thử lại.', 'danger');
        }
    }

    async function register(form) {
        clearErrors(form);

        const usernameInput = document.getElementById('username');
        const emailInput = document.getElementById('email');
        const passwordInput = document.getElementById('password');
        const confirmPasswordInput = document.getElementById('confirmPassword');
        const submitBtn = document.getElementById('register-submit-btn');

        const username = usernameInput ? usernameInput.value.trim() : '';
        const email = emailInput ? emailInput.value.trim() : '';
        const password = passwordInput ? passwordInput.value : '';
        const confirmPassword = confirmPasswordInput ? confirmPasswordInput.value : '';

        let hasError = false;

        if (!username) {
            setFieldError('username', 'Vui lòng nhập tên người dùng.');
            hasError = true;
        } else if (username.length < 3 || username.length > 50) {
            setFieldError('username', 'Tên người dùng phải có từ 3 đến 50 ký tự (có thể chứa dấu cách).');
            hasError = true;
        }

        if (!email) {
            setFieldError('email', 'Vui lòng nhập email.');
            hasError = true;
        } else if (email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
            setFieldError('email', 'Email không đúng định dạng hoặc vượt quá 254 ký tự.');
            hasError = true;
        }

        if (!password) {
            setFieldError('password', 'Vui lòng nhập mật khẩu.');
            hasError = true;
        } else if (password.length < 8 || password.length > 128) {
            setFieldError('password', 'Mật khẩu phải có độ dài từ 8 đến 128 ký tự.');
            hasError = true;
        }

        if (!confirmPassword) {
            setFieldError('confirmPassword', 'Vui lòng nhập lại mật khẩu.');
            hasError = true;
        } else if (password !== confirmPassword) {
            setFieldError('confirmPassword', 'Mật khẩu xác nhận không khớp.');
            hasError = true;
        }

        if (hasError) {
            const firstInvalid = form.querySelector('.is-invalid');
            if (firstInvalid) firstInvalid.focus();
            return;
        }

        setSubmitting(submitBtn, true, 'Đang tạo tài khoản...', 'Tạo tài khoản', 'register-icon', 'register-spinner');
        statusBox('Đang gửi yêu cầu đăng ký...', 'info');

        try {
            // Note: confirmPassword is strictly validated on client and not sent to backend DTO
            const response = await fetch('/api/auth/register', {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                body: JSON.stringify({
                    username: username,
                    email: email,
                    password: password
                })
            });

            const body = await readJson(response);

            if (response.status === 201 || (response.ok && response.status === 200)) {
                statusBox('Đăng ký tài khoản thành công! Vui lòng hoàn tất xác thực OTP để kích hoạt tài khoản.', 'success');
                window.setTimeout(function () {
                    window.location.href = '/verify-otp?email=' + encodeURIComponent(email);
                }, 400);
                return;
            }

            setSubmitting(submitBtn, false, 'Đang tạo tài khoản...', 'Tạo tài khoản', 'register-icon', 'register-spinner');

            if (response.status === 409) {
                const code = body && body.error && body.error.code;
                const msg = translateError(body && body.error, 'Tài khoản hoặc email đã tồn tại.');
                if (code === 'USER_USERNAME_ALREADY_EXISTS') {
                    setFieldError('username', msg);
                } else if (code === 'USER_EMAIL_ALREADY_EXISTS') {
                    setFieldError('email', msg);
                }
                statusBox(msg, 'danger');
            } else if (response.status === 403) {
                const msg = translateError(body && body.error, 'Bạn không có quyền thực hiện thao tác này.');
                statusBox(msg, 'danger');
            } else if (response.status === 422) {
                if (body && body.error && body.error.fieldErrors) {
                    for (const [field, msgs] of Object.entries(body.error.fieldErrors)) {
                        setFieldError(field, translateFieldError(field, msgs));
                    }
                }
                const msg = translateError(body && body.error, 'Dữ liệu đăng ký không hợp lệ.');
                statusBox(msg, 'danger');
            } else {
                const msg = translateError(body && body.error, 'Đăng ký thất bại (HTTP ' + response.status + ').');
                statusBox(msg, 'danger');
            }
        } catch (error) {
            setSubmitting(submitBtn, false, 'Đang tạo tài khoản...', 'Tạo tài khoản', 'register-icon', 'register-spinner');
            statusBox('Lỗi kết nối tới máy chủ. Vui lòng kiểm tra mạng và thử lại.', 'danger');
        }
    }

    async function forgotPassword(form) {
        clearErrors(form);
        const emailInput = document.getElementById('email');
        const submitBtn = document.getElementById('forgot-submit-btn');
        const email = emailInput ? emailInput.value.trim() : '';

        if (!email) {
            setFieldError('email', 'Vui lòng nhập địa chỉ email.');
            return;
        }

        setSubmitting(submitBtn, true, 'Đang gửi...', 'Gửi liên kết đặt lại', 'forgot-icon', 'forgot-spinner');
        statusBox('Đang gửi yêu cầu...', 'info');

        try {
            const response = await fetch('/api/auth/forgot-password', {
                method: 'POST',
                credentials: 'include',
                headers: csrfHeaders({
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }),
                body: JSON.stringify({ email: email })
            });

            const body = await readJson(response);
            setSubmitting(submitBtn, false, 'Đang gửi...', 'Gửi liên kết đặt lại', 'forgot-icon', 'forgot-spinner');

            if (response.ok) {
                statusBox('Yêu cầu đặt lại mật khẩu đã được tiếp nhận. Vui lòng kiểm tra hộp thư (bao gồm mục spam) nếu thông tin tài khoản hợp lệ.', 'info');
                if (emailInput) emailInput.value = '';
                return;
            }

            if (response.status === 429) {
                statusBox('Bạn đã yêu cầu quá nhiều lần. Vui lòng chờ trước khi thử lại.', 'warning');
            } else if (response.status === 422) {
                if (body && body.error && body.error.fieldErrors) {
                    for (const [field, msgs] of Object.entries(body.error.fieldErrors)) {
                        setFieldError(field, translateFieldError(field, msgs));
                    }
                }
                statusBox('Địa chỉ email không hợp lệ.', 'danger');
            } else if (response.status === 403) {
                statusBox('Phiên làm việc hết hạn hoặc thiếu mã bảo mật CSRF. Vui lòng tải lại trang.', 'danger');
            } else {
                statusBox('Đã có lỗi xảy ra. Vui lòng thử lại sau.', 'danger');
            }
        } catch (e) {
            setSubmitting(submitBtn, false, 'Đang gửi...', 'Gửi liên kết đặt lại', 'forgot-icon', 'forgot-spinner');
            statusBox('Lỗi kết nối tới máy chủ. Vui lòng kiểm tra mạng và thử lại.', 'danger');
        }
    }

    async function resetPassword(form) {
        clearErrors(form);
        const tokenInput = document.getElementById('reset-token');
        const passwordInput = document.getElementById('password');
        const confirmPasswordInput = document.getElementById('confirmPassword');
        const submitBtn = document.getElementById('reset-submit-btn');

        const token = tokenInput ? tokenInput.value.trim() : '';
        const password = passwordInput ? passwordInput.value : '';
        const confirmPassword = confirmPasswordInput ? confirmPasswordInput.value : '';

        if (!token) {
            statusBox('Mã xác thực đặt lại mật khẩu không hợp lệ hoặc thiếu.', 'danger');
            return;
        }

        let hasError = false;
        if (!password) {
            setFieldError('password', 'Vui lòng nhập mật khẩu mới.');
            hasError = true;
        } else if (password.length < 8 || password.length > 128) {
            setFieldError('password', 'Mật khẩu phải từ 8 đến 128 ký tự.');
            hasError = true;
        }

        if (!confirmPassword) {
            setFieldError('confirmPassword', 'Vui lòng xác nhận mật khẩu.');
            hasError = true;
        } else if (password !== confirmPassword) {
            setFieldError('confirmPassword', 'Mật khẩu xác nhận không khớp.');
            hasError = true;
        }

        if (hasError) return;

        setSubmitting(submitBtn, true, 'Đang lưu...', 'Lưu mật khẩu mới', 'reset-icon', 'reset-spinner');
        statusBox('Đang cập nhật mật khẩu...', 'info');

        try {
            const response = await fetch('/api/auth/reset-password', {
                method: 'POST',
                credentials: 'include',
                headers: csrfHeaders({
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }),
                body: JSON.stringify({ token: token, password: password, confirmPassword: confirmPassword })
            });

            const body = await readJson(response);
            setSubmitting(submitBtn, false, 'Đang lưu...', 'Lưu mật khẩu mới', 'reset-icon', 'reset-spinner');

            if (response.ok) {
                statusBox('Đặt lại mật khẩu thành công! Đang chuyển hướng về trang đăng nhập...', 'success');
                window.setTimeout(function () {
                    window.location.href = '/login';
                }, 1500);
                return;
            }

            if (response.status === 400) {
                statusBox('Liên kết đặt lại mật khẩu không hợp lệ, đã hết hạn hoặc đã được sử dụng.', 'danger');
            } else if (response.status === 422) {
                if (body && body.error && body.error.fieldErrors) {
                    for (const [field, msgs] of Object.entries(body.error.fieldErrors)) {
                        setFieldError(field, translateFieldError(field, msgs));
                    }
                }
                statusBox('Thông tin mật khẩu không hợp lệ.', 'danger');
            } else if (response.status === 403) {
                statusBox('Phiên làm việc hết hạn hoặc thiếu mã bảo mật CSRF. Vui lòng tải lại trang.', 'danger');
            } else {
                statusBox('Đã có lỗi xảy ra. Vui lòng thử lại sau.', 'danger');
            }
        } catch (e) {
            setSubmitting(submitBtn, false, 'Đang lưu...', 'Lưu mật khẩu mới', 'reset-icon', 'reset-spinner');
            statusBox('Lỗi kết nối tới máy chủ. Vui lòng kiểm tra mạng và thử lại.', 'danger');
        }
    }

    // Attach form submit listeners
    const loginForm = document.querySelector('form[data-action="login"]');
    if (loginForm) {
        loginForm.addEventListener('submit', function (event) {
            event.preventDefault();
            login(loginForm);
        });
    }

    const registerForm = document.querySelector('form[data-action="register"]');
    if (registerForm) {
        registerForm.addEventListener('submit', function (event) {
            event.preventDefault();
            register(registerForm);
        });
    }

    const forgotForm = document.querySelector('form[data-action="forgot-password"]');
    if (forgotForm) {
        forgotForm.addEventListener('submit', function (event) {
            event.preventDefault();
            forgotPassword(forgotForm);
        });
    }

    const resetForm = document.querySelector('form[data-action="reset-password"]');
    if (resetForm) {
        const hash = window.location.hash;
        let token = '';
        if (hash) {
            const match = hash.match(/token=([a-zA-Z0-9_-]+)/);
            if (match) {
                token = match[1];
            }
            // Immediately strip fragment from location bar to avoid referrer/history leaks
            try {
                history.replaceState(null, '', window.location.pathname + window.location.search);
            } catch (_) {}
        }
        const tokenInput = document.getElementById('reset-token');
        if (tokenInput && token) {
            tokenInput.value = token;
        } else if (!token) {
            statusBox('Liên kết đặt lại mật khẩu không hợp lệ hoặc thiếu mã xác thực.', 'warning');
            const submitBtn = document.getElementById('reset-submit-btn');
            if (submitBtn) submitBtn.disabled = true;
        }

        resetForm.addEventListener('submit', function (event) {
            event.preventDefault();
            resetPassword(resetForm);
        });
    }


    function maskEmail(email) {
        if (!email) return '...';
        const atIndex = email.indexOf('@');
        if (atIndex <= 2) return '***' + (atIndex >= 0 ? email.substring(atIndex) : '');
        return email.charAt(0) + '***' + email.charAt(atIndex - 1) + email.substring(atIndex);
    }

    let cooldownTimer = null;
    function startResendCooldown(seconds) {
        const btn = document.getElementById('resend-otp-btn');
        const textSpan = document.getElementById('resend-btn-text');
        if (!btn || !textSpan) return;

        btn.disabled = true;
        let remaining = seconds || 60;
        textSpan.textContent = 'Gửi lại (' + remaining + 's)';

        if (cooldownTimer) clearInterval(cooldownTimer);
        cooldownTimer = setInterval(function () {
            remaining--;
            if (remaining <= 0) {
                clearInterval(cooldownTimer);
                cooldownTimer = null;
                btn.disabled = false;
                textSpan.textContent = 'Gửi lại mã';
            } else {
                textSpan.textContent = 'Gửi lại (' + remaining + 's)';
            }
        }, 1000);
    }

    async function verifyOtp(form) {
        clearErrors(form);
        const emailInput = document.getElementById('otp-email');
        const otpInput = document.getElementById('otp');
        const submitBtn = document.getElementById('verify-submit-btn');

        const emailManualInput = document.getElementById('otp-email-input');
        let email = emailInput ? emailInput.value.trim() : '';
        if (!email && emailManualInput) {
            email = emailManualInput.value.trim();
            if (emailInput) emailInput.value = email;
        }
        const otp = otpInput ? otpInput.value.trim() : '';

        let hasError = false;
        if (!otp || !/^\d{6}$/.test(otp)) {
            setFieldError('otp', 'Mã xác thực phải gồm đúng 6 chữ số.');
            hasError = true;
        }

        if (hasError) {
            if (otpInput) otpInput.focus();
            return;
        }

        setSubmitting(submitBtn, true, 'Đang xác thực...', 'Xác thực tài khoản', 'verify-icon', 'verify-spinner');
        statusBox('Đang kiểm tra mã xác thực...', 'info');

        try {
            const response = await fetch('/api/auth/verify-registration-otp', {
                method: 'POST',
                credentials: 'include',
                headers: csrfHeaders({
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }),
                body: JSON.stringify({ email: email, otp: otp })
            });

            const body = await readJson(response);

            if (response.ok) {
                statusBox('Xác thực tài khoản thành công! Đang chuyển sang trang đăng nhập...', 'success');
                window.setTimeout(function () {
                    window.location.href = '/login?verified=1&email=' + encodeURIComponent(email);
                }, 500);
                return;
            }

            setSubmitting(submitBtn, false, 'Đang xác thực...', 'Xác thực tài khoản', 'verify-icon', 'verify-spinner');
            const msg = (body && body.error && body.error.message) || (body && body.message) || 'Mã xác thực không hợp lệ hoặc đã hết hạn.';
            setFieldError('otp', msg);
            statusBox(msg, 'danger');
        } catch (err) {
            setSubmitting(submitBtn, false, 'Đang xác thực...', 'Xác thực tài khoản', 'verify-icon', 'verify-spinner');
            statusBox('Lỗi kết nối tới máy chủ. Vui lòng thử lại.', 'danger');
        }
    }

    async function resendOtp() {
        const emailInput = document.getElementById('otp-email');
        const resendBtn = document.getElementById('resend-otp-btn');
        const spinner = document.getElementById('resend-spinner');
        const emailManualInput = document.getElementById('otp-email-input');
        let email = emailInput ? emailInput.value.trim() : '';
        if (!email && emailManualInput) {
            email = emailManualInput.value.trim();
            if (emailInput) emailInput.value = email;
        }

        if (!email) {
            statusBox('Vui lòng nhập địa chỉ email để gửi lại mã xác thực.', 'danger');
            if (emailManualInput) emailManualInput.focus();
            return;
        }

        if (resendBtn) resendBtn.disabled = true;
        if (spinner) spinner.classList.remove('d-none');
        statusBox('Đang xử lý yêu cầu gửi lại mã xác thực...', 'info');

        try {
            const response = await fetch('/api/auth/resend-registration-otp', {
                method: 'POST',
                credentials: 'include',
                headers: csrfHeaders({
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }),
                body: JSON.stringify({ email: email })
            });

            const body = await readJson(response);
            if (spinner) spinner.classList.add('d-none');

            if (response.ok) {
                statusBox('Yêu cầu gửi lại mã xác thực đã được xử lý. Vui lòng kiểm tra hộp thư của bạn.', 'info');
                startResendCooldown(60);
                const otpInput = document.getElementById('otp');
                if (otpInput) { otpInput.value = ''; otpInput.focus(); }
            } else if (response.status === 429) {
                const msg = (body && body.error && body.error.message) || 'Vui lòng đợi trước khi gửi lại mã.';
                statusBox(msg, 'warning');
                startResendCooldown(60);
            } else {
                const msg = (body && body.error && body.error.message) || 'Không thể gửi lại mã xác thực. Vui lòng thử lại.';
                statusBox(msg, 'danger');
                if (resendBtn) resendBtn.disabled = false;
            }
        } catch (err) {
            if (spinner) spinner.classList.add('d-none');
            if (resendBtn) resendBtn.disabled = false;
            statusBox('Lỗi kết nối tới máy chủ. Vui lòng thử lại.', 'danger');
        }
    }

    const verifyForm = document.querySelector('form[data-action="verify-otp"]');
    if (verifyForm) {
        const urlParams = new URLSearchParams(window.location.search);
        const emailParam = urlParams.get('email') || '';
        const emailInput = document.getElementById('otp-email');
        const emailManualInput = document.getElementById('otp-email-input');
        const displayEmail = document.getElementById('display-email');
        const emailDisplayGroup = document.getElementById('email-display-group');
        const emailManualGroup = document.getElementById('email-manual-group');

        if (emailParam) {
            if (emailInput) emailInput.value = emailParam;
            if (displayEmail) displayEmail.textContent = maskEmail(emailParam);
            if (emailDisplayGroup) emailDisplayGroup.classList.remove('d-none');
            if (emailManualGroup) emailManualGroup.classList.add('d-none');
        } else {
            if (emailDisplayGroup) emailDisplayGroup.classList.add('d-none');
            if (emailManualGroup) emailManualGroup.classList.remove('d-none');
            if (emailManualInput && emailInput) {
                emailManualInput.addEventListener('input', function () {
                    emailInput.value = emailManualInput.value.trim();
                });
            }
        }

        verifyForm.addEventListener('submit', function (event) {
            event.preventDefault();
            verifyOtp(verifyForm);
        });

        const resendBtn = document.getElementById('resend-otp-btn');
        if (resendBtn) {
            resendBtn.addEventListener('click', function (event) {
                event.preventDefault();
                resendOtp();
            });
        }
    }

    // Navigation and helper button handlers
    document.querySelectorAll('[data-action="navigate-register"]').forEach(link => {
        link.addEventListener('click', function (event) {
            event.preventDefault();
            window.location.href = '/register';
        });
    });

    document.querySelectorAll('[data-action="navigate-login"]').forEach(link => {
        link.addEventListener('click', function (event) {
            event.preventDefault();
            window.location.href = '/login';
        });
    });

    document.querySelectorAll('[data-action="navigate-forgot-password"]').forEach(link => {
        link.addEventListener('click', function (event) {
            event.preventDefault();
            window.location.href = '/forgot-password';
        });
    });

})();
