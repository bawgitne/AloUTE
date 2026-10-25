/**
 * Community moderation & member management UI.
 * Vanilla JS, event delegation, CSRF via window.alouteHttp (X-XSRF-TOKEN header).
 * Surfaces:
 *  - post cards anywhere (approve/reject pending submissions, remove published posts)
 *  - /communities/{id}/manage/members (remove member, change role)
 *  - /communities/{id}/manage/settings (approval toggle)
 */
(function () {
    'use strict';

    var locks = Object.create(null);
    function acquireLock(key) {
        if (locks[key]) return false;
        locks[key] = true;
        return true;
    }
    function releaseLock(key) { delete locks[key]; }

    function getHttp() {
        if (window.alouteHttp && window.alouteHttp.request) return window.alouteHttp;
        return null;
    }

    var ERROR_MESSAGES = {
        'COMMUNITY_ADMIN_REQUIRED': 'Bạn phải là chủ sở hữu hoặc quản trị viên của cộng đồng này.',
        'COMMUNITY_OWNER_REQUIRED': 'Chỉ chủ sở hữu cộng đồng mới được thực hiện thao tác này.',
        'COMMUNITY_NOT_FOUND': 'Cộng đồng không tồn tại hoặc đã bị xóa.',
        'COMMUNITY_MEMBER_NOT_FOUND': 'Thành viên không tồn tại hoặc đã rời cộng đồng.',
        'COMMUNITY_MEMBER_REMOVE_NOT_ALLOWED': 'Bạn không thể xóa thành viên này.',
        'COMMUNITY_ROLE_CHANGE_NOT_ALLOWED': 'Chỉ chủ sở hữu cộng đồng mới được đổi vai trò.',
        'POST_NOT_FOUND': 'Bài viết không tồn tại hoặc đã bị xóa.',
        'POST_MODERATION_STATE_INVALID': 'Bài viết đã được quản trị viên khác xử lý.',
        'POST_ALREADY_DELETED': 'Bài viết đã bị xóa trước đó.',
        'VALIDATION_FAILED': 'Dữ liệu không hợp lệ, vui lòng kiểm tra lại.',
        'FORBIDDEN': 'Bạn không có quyền thực hiện thao tác này.'
    };

    function friendlyError(payload, fallback) {
        if (payload && payload.code && ERROR_MESSAGES[payload.code]) {
            return ERROR_MESSAGES[payload.code];
        }
        return fallback || 'Đã có lỗi xảy ra. Vui lòng thử lại.';
    }

    /**
     * alouteHttp.request is a thin fetch wrapper: resolve with {ok, payload}
     * where payload is the ErrorResponse's ApiError on failure (or null).
     */
    function send(url, options) {
        var http = getHttp();
        return http.request(url, options).then(function (response) {
            return http.readJson(response).catch(function () { return null; }).then(function (json) {
                if (response.ok && !(json && json.success === false)) {
                    return { ok: true };
                }
                return { ok: false, payload: json && json.error ? json.error : null, status: response.status };
            });
        });
    }

    function setBusy(button, busy) {
        if (!button) return;
        button.disabled = busy;
        var spinner = button.querySelector('.spinner-border');
        if (spinner) spinner.classList.toggle('d-none', !busy);
    }

    function showStatus(elementId, message, type) {
        var el = document.getElementById(elementId);
        if (!el) return;
        el.textContent = message;
        el.className = 'alert py-2 px-3 small mb-0' + (type === 'error' ? ' alert-danger' : ' alert-success');
        el.classList.remove('d-none');
    }

    function reloadWithDone(code) {
        var url = new URL(window.location.href);
        url.searchParams.delete('done');
        if (code) url.searchParams.set('done', code);
        window.location.assign(url.toString());
    }

    function getModal(id) {
        var el = document.getElementById(id);
        if (!el) return null;
        if (window.bootstrap && window.bootstrap.Modal) {
            return { el: el, instance: window.bootstrap.Modal.getOrCreateInstance(el) };
        }
        return { el: el, instance: null };
    }

    function openModal(id) {
        var modal = getModal(id);
        if (!modal) return;
        if (modal.instance) {
            modal.instance.show();
        } else {
            modal.el.classList.add('show');
            modal.el.style.display = 'block';
            document.body.classList.add('modal-open');
        }
    }

    function closeModal(id) {
        var modal = getModal(id);
        if (!modal) return;
        if (modal.instance) {
            modal.instance.hide();
        } else {
            modal.el.classList.remove('show');
            modal.el.style.display = 'none';
            document.body.classList.remove('modal-open');
        }
    }

    var rejectCtx = null;
    var removeCtx = null;
    var removeMemberCtx = null;

    var ROLE_LABELS = { MEMBER: 'Thành viên', MODERATOR: 'Kiểm duyệt viên', ADMIN: 'Quản trị viên' };

    // --------------------------------------------------------------
    // Post moderation
    // --------------------------------------------------------------

    function approvePost(button, communityId, postId) {
        if (!getHttp()) return;
        var lockKey = 'approve-' + postId;
        if (!acquireLock(lockKey)) return;
        setBusy(button, true);
        var confirmed = window.alouteConfirm
            ? window.alouteConfirm('Phê duyệt và hiển thị bài viết này trong cộng đồng?', {
                title: 'Phê duyệt bài viết',
                confirmText: 'Phê duyệt',
                cancelText: 'Hủy'
            })
            : Promise.resolve(window.confirm('Phê duyệt bài viết này?'));
        confirmed.then(function (ok) {
            if (!ok) { releaseLock(lockKey); setBusy(button, false); return; }
            return send('/api/communities/' + communityId + '/posts/' + postId + '/approve', { method: 'POST' })
                .then(function (result) {
                    if (result.ok) {
                        reloadWithDone('approved');
                    } else {
                        releaseLock(lockKey);
                        setBusy(button, false);
                        window.alert(friendlyError(result.payload, 'Không phê duyệt được bài viết.'));
                    }
                });
        }).catch(function () {
            releaseLock(lockKey);
            setBusy(button, false);
            window.alert('Lỗi kết nối: không phê duyệt được bài viết.');
        });
    }

    function confirmReject() {
        var button = document.getElementById('btn-confirm-community-reject');
        if (!rejectCtx || !getHttp()) return;
        if (!acquireLock('reject-' + rejectCtx.postId)) return;
        var reasonInput = document.getElementById('community-reject-reason');
        var reason = reasonInput ? reasonInput.value.trim() : '';
        if (reason.length > 500) {
            showStatus('community-reject-status', 'Lý do không được vượt quá 500 ký tự.', 'error');
            releaseLock('reject-' + rejectCtx.postId);
            return;
        }
        setBusy(button, true);
        send('/api/communities/' + rejectCtx.communityId + '/posts/' + rejectCtx.postId + '/reject', {
            method: 'POST',
            body: { reason: reason || null }
        }).then(function (result) {
            if (result.ok) {
                releaseLock('reject-' + rejectCtx.postId);
                closeModal('communityRejectPostModal');
                reloadWithDone('rejected');
            } else {
                releaseLock('reject-' + rejectCtx.postId);
                setBusy(button, false);
                showStatus('community-reject-status', friendlyError(result.payload, 'Không từ chối được bài viết.'), 'error');
            }
        }).catch(function () {
            releaseLock('reject-' + rejectCtx.postId);
            setBusy(button, false);
            showStatus('community-reject-status', 'Lỗi kết nối: không từ chối được bài viết.', 'error');
        });
    }

    function confirmRemovePost() {
        var button = document.getElementById('btn-confirm-community-remove');
        if (!removeCtx || !getHttp()) return;
        if (!acquireLock('remove-' + removeCtx.postId)) return;
        var reasonInput = document.getElementById('community-remove-reason');
        var reason = reasonInput ? reasonInput.value.trim() : '';
        setBusy(button, true);
        send('/api/communities/' + removeCtx.communityId + '/posts/' + removeCtx.postId + '/remove', {
            method: 'POST',
            body: { reason: reason || null }
        }).then(function (result) {
            if (result.ok) {
                releaseLock('remove-' + removeCtx.postId);
                closeModal('communityRemovePostModal');
                reloadWithDone('removed');
            } else {
                releaseLock('remove-' + removeCtx.postId);
                setBusy(button, false);
                showStatus('community-remove-status', friendlyError(result.payload, 'Không gỡ được bài viết.'), 'error');
            }
        }).catch(function () {
            releaseLock('remove-' + removeCtx.postId);
            setBusy(button, false);
            showStatus('community-remove-status', 'Lỗi kết nối: không gỡ được bài viết.', 'error');
        });
    }

    // --------------------------------------------------------------
    // Member management
    // --------------------------------------------------------------

    function confirmRemoveMember() {
        var button = document.getElementById('btn-confirm-community-remove-member');
        if (!removeMemberCtx || !getHttp()) return;
        if (!acquireLock('rm-member-' + removeMemberCtx.userId)) return;
        setBusy(button, true);
        send('/api/communities/' + removeMemberCtx.communityId + '/members/' + removeMemberCtx.userId, {
            method: 'DELETE'
        }).then(function (result) {
            releaseLock('rm-member-' + removeMemberCtx.userId);
            if (result.ok) {
                closeModal('communityRemoveMemberModal');
                reloadWithDone('member-removed');
            } else {
                setBusy(button, false);
                closeModal('communityRemoveMemberModal');
                showStatus('community-manage-status', friendlyError(result.payload, 'Không xóa được thành viên.'), 'error');
            }
        }).catch(function () {
            releaseLock('rm-member-' + removeMemberCtx.userId);
            setBusy(button, false);
            showStatus('community-manage-status', 'Lỗi kết nối: không xóa được thành viên.', 'error');
        });
    }

    function changeRole(select) {
        if (!getHttp()) return;
        var communityId = select.getAttribute('data-community-id');
        var userId = select.getAttribute('data-user-id');
        var memberName = select.getAttribute('data-member-name') || 'thành viên này';
        var previousValue = select.getAttribute('data-previous-value') || 'MEMBER';
        var nextRole = select.value;
        if (nextRole === previousValue) return;

        var proceed = function () { sendRoleChange(select, communityId, userId, nextRole, previousValue); };
        var cancel = function () { select.value = previousValue; };

        if (window.alouteConfirm) {
            window.alouteConfirm('Đổi vai trò của ' + memberName + ' thành ' + (ROLE_LABELS[nextRole] || nextRole) + '?', {
                title: 'Đổi vai trò thành viên',
                confirmText: 'Đồng ý',
                cancelText: 'Hủy'
            }).then(function (ok) { if (ok) { proceed(); } else { cancel(); } });
        } else if (window.confirm('Đổi vai trò thành ' + (ROLE_LABELS[nextRole] || nextRole) + '?')) {
            proceed();
        } else {
            cancel();
        }
    }

    function sendRoleChange(select, communityId, userId, nextRole, previousValue) {
        if (!acquireLock('role-' + userId)) { select.value = previousValue; return; }
        select.disabled = true;
        send('/api/communities/' + communityId + '/members/' + userId + '/role', {
            method: 'PATCH',
            body: { role: nextRole }
        }).then(function (result) {
            releaseLock('role-' + userId);
            if (result.ok) {
                reloadWithDone('role-updated');
            } else {
                select.disabled = false;
                select.value = previousValue;
                showStatus('community-manage-status', friendlyError(result.payload, 'Không đổi được vai trò thành viên.'), 'error');
            }
        }).catch(function () {
            releaseLock('role-' + userId);
            select.disabled = false;
            select.value = previousValue;
            showStatus('community-manage-status', 'Lỗi kết nối: không đổi được vai trò.', 'error');
        });
    }

    // --------------------------------------------------------------
    // Settings: approval toggle
    // --------------------------------------------------------------

    function toggleApproval(switchInput) {
        if (!getHttp()) { switchInput.checked = !switchInput.checked; return; }
        var communityId = switchInput.getAttribute('data-community-id');
        var next = switchInput.checked;
        if (!acquireLock('approval-' + communityId)) { switchInput.checked = !next; return; }
        switchInput.disabled = true;
        send('/api/communities/' + communityId, {
            method: 'PATCH',
            body: { requirePostApproval: next }
        }).then(function (result) {
            releaseLock('approval-' + communityId);
            switchInput.disabled = false;
            if (result.ok) {
                showStatus('community-settings-status',
                    next ? 'Đã bật yêu cầu phê duyệt bài viết của thành viên.' : 'Đã tắt yêu cầu phê duyệt bài viết của thành viên.',
                    'success');
            } else {
                switchInput.checked = !next;
                showStatus('community-settings-status', friendlyError(result.payload, 'Không lưu được cài đặt.'), 'error');
            }
        }).catch(function () {
            releaseLock('approval-' + communityId);
            switchInput.disabled = false;
            switchInput.checked = !next;
            showStatus('community-settings-status', 'Lỗi kết nối: không lưu được cài đặt.', 'error');
        });
    }

    // --------------------------------------------------------------
    // Wiring
    // --------------------------------------------------------------

    function wireCharCounter(inputId, counterId) {
        var input = document.getElementById(inputId);
        var counter = document.getElementById(counterId);
        if (!input || !counter) return;
        input.addEventListener('input', function () {
            counter.textContent = String(input.value.length);
        });
    }

    function onHidden(modalId, resetFn) {
        var el = document.getElementById(modalId);
        if (!el) return;
        el.addEventListener('hidden.bs.modal', resetFn);
    }

    function init() {
        document.addEventListener('click', function (event) {
            var target = event.target instanceof Element ? event.target.closest('[data-action]') : null;
            if (!target) return;
            var action = target.getAttribute('data-action');
            if (action === 'community-approve-post') {
                event.preventDefault();
                approvePost(target, target.getAttribute('data-community-id'), target.getAttribute('data-post-id'));
            } else if (action === 'community-reject-post') {
                event.preventDefault();
                rejectCtx = {
                    postId: target.getAttribute('data-post-id'),
                    communityId: target.getAttribute('data-community-id')
                };
                setBusy(document.getElementById('btn-confirm-community-reject'), false);
                var rejectStatus = document.getElementById('community-reject-status');
                if (rejectStatus) rejectStatus.classList.add('d-none');
                openModal('communityRejectPostModal');
            } else if (action === 'community-remove-post') {
                event.preventDefault();
                removeCtx = {
                    postId: target.getAttribute('data-post-id'),
                    communityId: target.getAttribute('data-community-id')
                };
                setBusy(document.getElementById('btn-confirm-community-remove'), false);
                var removeStatus = document.getElementById('community-remove-status');
                if (removeStatus) removeStatus.classList.add('d-none');
                var nameEl = document.getElementById('community-remove-community-name');
                if (nameEl) nameEl.textContent = target.getAttribute('data-community-name') || '';
                openModal('communityRemovePostModal');
            } else if (action === 'community-remove-member') {
                event.preventDefault();
                removeMemberCtx = {
                    userId: target.getAttribute('data-user-id'),
                    communityId: target.getAttribute('data-community-id')
                };
                setBusy(document.getElementById('btn-confirm-community-remove-member'), false);
                var memberName = document.getElementById('community-remove-member-name');
                if (memberName) memberName.textContent = target.getAttribute('data-member-name') || '';
                openModal('communityRemoveMemberModal');
            } else if (action === 'community-reject-post-confirm') {
                event.preventDefault();
                confirmReject();
            } else if (action === 'community-remove-post-confirm') {
                event.preventDefault();
                confirmRemovePost();
            } else if (action === 'community-remove-member-confirm') {
                event.preventDefault();
                confirmRemoveMember();
            }
        });

        document.addEventListener('change', function (event) {
            var target = event.target instanceof Element ? event.target : null;
            if (!target) return;
            if (target.classList && target.classList.contains('community-role-select')) {
                changeRole(target);
            } else if (target.id === 'require-post-approval-switch') {
                toggleApproval(target);
            }
        });

        // Remember previous role so cancel/error can restore the selection.
        var selects = document.querySelectorAll('.community-role-select');
        for (var i = 0; i < selects.length; i++) {
            selects[i].setAttribute('data-previous-value', selects[i].value);
        }

        wireCharCounter('community-reject-reason', 'community-reject-char-count');
        wireCharCounter('community-remove-reason', 'community-remove-char-count');

        onHidden('communityRejectPostModal', function () {
            var input = document.getElementById('community-reject-reason');
            if (input) input.value = '';
            var counter = document.getElementById('community-reject-char-count');
            if (counter) counter.textContent = '0';
            rejectCtx = null;
        });
        onHidden('communityRemovePostModal', function () {
            var input = document.getElementById('community-remove-reason');
            if (input) input.value = '';
            var counter = document.getElementById('community-remove-char-count');
            if (counter) counter.textContent = '0';
            removeCtx = null;
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    window.alouteCommunityManage = {
        approvePost: approvePost,
        confirmReject: confirmReject,
        confirmRemovePost: confirmRemovePost,
        confirmRemoveMember: confirmRemoveMember,
        toggleApproval: toggleApproval,
        friendlyError: friendlyError
    };
})();
