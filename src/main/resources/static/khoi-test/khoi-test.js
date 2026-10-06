(() => {
    const output = document.getElementById('output');

    const csrfHeaders = (extra = {}) => {
        const item = document.cookie.split('; ').find(v => v.startsWith('XSRF-TOKEN='));
        const headers = { ...extra };
        if (item) headers['X-XSRF-TOKEN'] = decodeURIComponent(item.substring('XSRF-TOKEN='.length));
        return headers;
    };

    const readBody = async (response) => {
        const text = await response.text();
        if (!text) return null;
        try { return JSON.parse(text); } catch (_) { return text; }
    };

    const call = async (method, url, body) => {
        const options = { method, credentials: 'include', headers: csrfHeaders() };
        if (body !== undefined) {
            options.headers['Content-Type'] = 'application/json';
            options.body = JSON.stringify(body);
        }
        const response = await fetch(url, options);
        const data = await readBody(response);
        output.textContent = JSON.stringify({ method, url, status: response.status, ok: response.ok, body: data }, null, 2);
        return { response, data };
    };

    const value = id => document.getElementById(id).value.trim();
    const numberOrNull = id => {
        const raw = value(id);
        return raw ? Number(raw) : null;
    };

    document.querySelectorAll('[data-fill]').forEach(button => {
        button.addEventListener('click', () => {
            const admin = button.dataset.fill === 'admin';
            document.getElementById('loginEmail').value = admin ? 'admin@aloute.local' : 'user@aloute.local';
            document.getElementById('loginPassword').value = admin ? 'Admin@123' : 'User@123';
        });
    });

    document.getElementById('clearOutput').addEventListener('click', () => output.textContent = 'Chưa gửi request.');

    document.addEventListener('click', async event => {
        const button = event.target.closest('[data-api]');
        if (!button) return;
        button.disabled = true;
        try {
            switch (button.dataset.api) {
                case 'login':
                    await call('POST', '/api/auth/login', { email: value('loginEmail'), password: value('loginPassword') });
                    break;
                case 'register':
                    await call('POST', '/api/auth/register', { username: value('registerUsername'), email: value('registerEmail'), password: value('registerPassword') });
                    break;
                case 'me':
                    await call('GET', '/api/auth/me');
                    break;
                case 'logout':
                    await call('POST', '/api/auth/logout');
                    break;
                case 'profile':
                    await call('GET', `/api/users/${numberOrNull('profileUserId')}`);
                    break;
                case 'followers':
                    await call('GET', `/api/users/${numberOrNull('profileUserId')}/followers?page=0&size=20`);
                    break;
                case 'following':
                    await call('GET', `/api/users/${numberOrNull('profileUserId')}/following?page=0&size=20`);
                    break;
                case 'updateProfile':
                    await call('PATCH', '/api/users/me/profile', {
                        displayName: value('displayName'),
                        bio: value('bio'),
                        isPrivate: document.getElementById('privateAccount').checked
                    });
                    break;
                case 'follow':
                    await call('PUT', `/api/social/follows/${numberOrNull('followUserId')}`);
                    break;
                case 'unfollow':
                    await call('DELETE', `/api/social/follows/${numberOrNull('followUserId')}`);
                    break;
                case 'report':
                    await call('POST', '/api/reports', {
                        reportedUserId: numberOrNull('reportedUserId'),
                        reportedPostId: numberOrNull('reportedPostId'),
                        reason: value('reportReason'),
                        description: value('reportDescription')
                    });
                    break;
            }
        } catch (error) {
            output.textContent = JSON.stringify({ error: error.message }, null, 2);
        } finally {
            button.disabled = false;
        }
    });
})();
