(function () {
    const form = document.querySelector('form[data-action="login"], form[data-action="register"]');
    if (!form) return;

    function feedback(message, type) {
        let box = document.getElementById('auth-client-feedback');
        if (!box) {
            box = document.createElement('div');
            box.id = 'auth-client-feedback';
            form.prepend(box);
        }
        box.className = 'alert alert-' + (type || 'info') + ' py-2 small mb-3';
        box.textContent = message;
        box.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }

    async function readBody(response) {
        const text = await response.text();
        if (!text) return null;
        try { return JSON.parse(text); } catch (_) { return { raw: text }; }
    }

    function errorMessage(body, fallback) {
        if (body && body.error) {
            if (body.error.message) return body.error.message;
            if (body.error.fieldErrors) return JSON.stringify(body.error.fieldErrors);
        }
        return fallback;
    }

    async function login() {
        const email = document.getElementById('email').value.trim();
        const password = document.getElementById('password').value;
        if (!email || !password) {
            feedback('Vui lòng nhập email và mật khẩu.', 'warning');
            return;
        }

        feedback('Đang đăng nhập...', 'info');
        try {
            const response = await fetch('/api/auth/login', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, password })
            });
            const body = await readBody(response);
            if (!response.ok) {
                feedback(errorMessage(body, 'Đăng nhập không thành công.'), 'danger');
                return;
            }
            feedback('Đăng nhập thành công. Đang mở bảng tin...', 'success');
            window.setTimeout(() => { window.location.href = '/feed'; }, 350);
        } catch (error) {
            feedback('Không kết nối được tới API đăng nhập: ' + error.message, 'danger');
        }
    }

    async function register() {
        const username = document.getElementById('username').value.trim();
        const email = document.getElementById('email').value.trim();
        const password = document.getElementById('password').value;
        const confirmPassword = document.getElementById('confirmPassword').value;

        if (!username || !email || !password) {
            feedback('Vui lòng nhập đầy đủ username, email và mật khẩu.', 'warning');
            return;
        }
        if (password !== confirmPassword) {
            feedback('Mật khẩu xác nhận không khớp.', 'warning');
            return;
        }

        feedback('Đang tạo tài khoản...', 'info');
        try {
            const response = await fetch('/api/auth/register', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, email, password })
            });
            const body = await readBody(response);
            if (!response.ok) {
                feedback(errorMessage(body, 'Đăng ký không thành công.'), 'danger');
                return;
            }
            feedback('Đăng ký thành công. Đang chuyển sang đăng nhập...', 'success');
            window.setTimeout(() => {
                window.location.href = '/login?registered=1&email=' + encodeURIComponent(email);
            }, 500);
        } catch (error) {
            feedback('Không kết nối được tới API đăng ký: ' + error.message, 'danger');
        }
    }

    form.addEventListener('submit', function (event) {
        event.preventDefault();
        if (form.dataset.action === 'login') login();
        if (form.dataset.action === 'register') register();
    });

    document.querySelectorAll('[data-action="navigate-register"]').forEach(link => {
        link.addEventListener('click', event => { event.preventDefault(); window.location.href = '/register'; });
    });
    document.querySelectorAll('[data-action="navigate-login"]').forEach(link => {
        link.addEventListener('click', event => { event.preventDefault(); window.location.href = '/login'; });
    });

    document.querySelectorAll('[data-test-account]').forEach(button => {
        button.addEventListener('click', function () {
            const email = this.getAttribute('data-email');
            const password = this.getAttribute('data-password');
            document.getElementById('email').value = email;
            document.getElementById('password').value = password;
            feedback('Đã điền tài khoản test ' + this.getAttribute('data-test-account') + '. Bấm Đăng nhập để kiểm tra.', 'info');
        });
    });

    document.querySelectorAll('[data-action="forgot-password"]').forEach(link => {
        link.addEventListener('click', event => {
            event.preventDefault();
            feedback('Chức năng Quên mật khẩu chưa được triển khai trong phạm vi hiện tại.', 'warning');
        });
    });
})();
