/**
 * AloUTE — Authentication Module Client Script
 * Handles semantic form submissions, DTO-aligned validation,
 * exact REST API integration (/api/auth/login, /api/auth/register),
 * duplicate-submit prevention, and accessible status/error rendering.
 */
(function () {
    'use strict';

    function statusBox(message, type) {
        let box = document.getElementById('auth-status');
        if (!box) {
            box = document.createElement('div');
            box.id = 'auth-status';
            box.setAttribute('role', 'alert');
            box.setAttribute('aria-live', 'polite');
            const form = document.querySelector('form[data-action="login"], form[data-action="register"]');
            if (form) form.prepend(box);
            else document.body.prepend(box);
        }
        box.className = 'alert alert-' + (type || 'info') + ' py-2 small mb-3';
        box.textContent = message;
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
                const msg = (body && body.error && body.error.message) || 'Email hoặc mật khẩu không chính xác.';
                statusBox(msg, 'danger');
                if (passwordInput) {
                    passwordInput.value = '';
                    passwordInput.focus();
                }
            } else if (response.status === 422) {
                if (body && body.error && body.error.fieldErrors) {
                    for (const [field, msgs] of Object.entries(body.error.fieldErrors)) {
                        setFieldError(field, Array.isArray(msgs) ? msgs.join(', ') : String(msgs));
                    }
                }
                const msg = (body && body.error && body.error.message) || 'Thông tin đăng nhập không hợp lệ.';
                statusBox(msg, 'danger');
            } else {
                const msg = (body && body.error && body.error.message) || ('Đăng nhập thất bại (HTTP ' + response.status + ').');
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
            setFieldError('username', 'Tên người dùng phải có từ 3 đến 50 ký tự.');
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
                statusBox('Đăng ký tài khoản thành công! Đang chuyển sang trang đăng nhập...', 'success');
                window.setTimeout(function () {
                    window.location.href = '/login?registered=1&email=' + encodeURIComponent(email);
                }, 400);
                return;
            }

            setSubmitting(submitBtn, false, 'Đang tạo tài khoản...', 'Tạo tài khoản', 'register-icon', 'register-spinner');

            if (response.status === 409) {
                const code = body && body.error && body.error.code;
                const msg = (body && body.error && body.error.message) || 'Tài khoản hoặc email đã tồn tại.';
                if (code === 'USER_USERNAME_ALREADY_EXISTS') {
                    setFieldError('username', msg);
                } else if (code === 'USER_EMAIL_ALREADY_EXISTS') {
                    setFieldError('email', msg);
                }
                statusBox(msg, 'danger');
            } else if (response.status === 422) {
                if (body && body.error && body.error.fieldErrors) {
                    for (const [field, msgs] of Object.entries(body.error.fieldErrors)) {
                        setFieldError(field, Array.isArray(msgs) ? msgs.join(', ') : String(msgs));
                    }
                }
                const msg = (body && body.error && body.error.message) || 'Dữ liệu đăng ký không hợp lệ.';
                statusBox(msg, 'danger');
            } else {
                const msg = (body && body.error && body.error.message) || ('Đăng ký thất bại (HTTP ' + response.status + ').');
                statusBox(msg, 'danger');
            }
        } catch (error) {
            setSubmitting(submitBtn, false, 'Đang tạo tài khoản...', 'Tạo tài khoản', 'register-icon', 'register-spinner');
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

    document.querySelectorAll('[data-action="forgot-password"]').forEach(link => {
        link.addEventListener('click', function (event) {
            event.preventDefault();
            statusBox('Chức năng Quên mật khẩu chưa được triển khai trong phạm vi hiện tại.', 'warning');
        });
    });

})();
