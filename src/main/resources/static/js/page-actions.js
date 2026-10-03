(function () {
    function notice(message, type) {
        let box = document.getElementById('aloute-dev-notice');
        if (!box) {
            box = document.createElement('div');
            box.id = 'aloute-dev-notice';
            box.className = 'position-fixed bottom-0 end-0 m-3 alert shadow';
            box.style.zIndex = '2000';
            document.body.appendChild(box);
        }
        box.className = 'position-fixed bottom-0 end-0 m-3 alert alert-' + (type || 'warning') + ' shadow';
        box.textContent = message;
        box.hidden = false;
        clearTimeout(window.__alouteNoticeTimer);
        window.__alouteNoticeTimer = setTimeout(() => { box.hidden = true; }, 3200);
    }

    function csrfHeaders(extra) {
        const xsrf = document.cookie.split('; ').find(v => v.startsWith('XSRF-TOKEN='));
        const headers = Object.assign({}, extra || {});
        if (xsrf) headers['X-XSRF-TOKEN'] = decodeURIComponent(xsrf.split('=').slice(1).join('='));
        return headers;
    }

    async function readJson(response) {
        const text = await response.text();
        if (!text) return null;
        try { return JSON.parse(text); } catch (_) { return null; }
    }

    async function editProfile() {
        const nameEl = document.querySelector('.profile-identity h1');
        const bioEl = document.querySelector('.profile-bio p');
        const currentName = nameEl ? nameEl.textContent.trim() : '';
        const currentBio = bioEl ? bioEl.textContent.trim() : '';
        const displayName = window.prompt('Tên hiển thị:', currentName);
        if (displayName === null) return;
        const bio = window.prompt('Giới thiệu ngắn:', currentBio);
        if (bio === null) return;
        try {
            const response = await fetch('/api/users/me/profile', {
                method: 'PATCH',
                credentials: 'include',
                headers: csrfHeaders({ 'Content-Type': 'application/json' }),
                body: JSON.stringify({ displayName: displayName.trim(), bio: bio.trim() })
            });
            const body = await readJson(response);
            if (!response.ok) {
                notice((body && body.error && body.error.message) || ('Cập nhật thất bại. HTTP ' + response.status), 'danger');
                return;
            }
            notice('Đã cập nhật hồ sơ.', 'success');
            setTimeout(() => window.location.reload(), 400);
        } catch (error) {
            notice('Không gọi được API cập nhật hồ sơ: ' + error.message, 'danger');
        }
    }

    document.addEventListener('click', async function (event) {
        const target = event.target.closest('[data-action]');
        if (!target) return;
        const action = target.dataset.action;

        if (action === 'navigate-login') { event.preventDefault(); window.location.href = '/login'; return; }
        if (action === 'navigate-register') { event.preventDefault(); window.location.href = '/register'; return; }
        if (action === 'navigate-create-post') { event.preventDefault(); window.location.href = '/post/create'; return; }
        if (action === 'navigate-profile') {
            const href = target.getAttribute('href');
            if (href && href !== '#') { event.preventDefault(); window.location.href = href; }
            return;
        }
        if (action === 'edit-profile') { event.preventDefault(); await editProfile(); return; }
        if (action === 'logout') {
            event.preventDefault();
            try {
                const response = await fetch('/api/auth/logout', {
                    method: 'POST', credentials: 'include', headers: csrfHeaders()
                });
                if (response.ok) window.location.href = '/login?logout=1';
                else notice('Đăng xuất chưa thành công. HTTP ' + response.status, 'danger');
            } catch (error) { notice('Không gọi được API đăng xuất: ' + error.message, 'danger'); }
            return;
        }

        event.preventDefault();
        const labels = {
            'create-post': 'API tạo bài viết thuộc module Post chưa được ghép.',
            'submit-reply': 'API Reply thuộc module Post chưa được ghép.',
            'edit-post': 'API sửa bài viết chưa được ghép.',
            'delete-post': 'API xóa bài viết chưa được ghép.',
            'message': 'Messaging/WebSocket thuộc phần Bằng chưa được ghép.',
            'topic': 'Topic/Community thuộc phần Ngọc chưa được ghép.',
            'community': 'Trang Community chưa được ghép runtime.',
            'messages': 'Trang Messaging chưa được ghép runtime.',
            'notifications': 'Trang Notification chưa được ghép runtime.',
            'bookmarks': 'Bookmark chưa được ghép backend.',
            'search': 'Search chưa được ghép backend.'
        };
        notice(labels[action] || ('Nút hoạt động. Chức năng "' + action + '" chưa được ghép backend trong phạm vi hiện tại.'));
    });

    const search = document.querySelector('.aloute-search-input');
    if (search) {
        search.addEventListener('keydown', function (event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                notice('Ô tìm kiếm hoạt động; API Search chưa được ghép backend.', 'info');
            }
        });
    }
})();
