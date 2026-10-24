/**
 * AloUTE — Shared Confirmation Dialog
 * Replaces native confirm()/alert() with an accessible, Bootstrap-styled modal
 * so destructive-action confirmations match the rest of the design system.
 *
 * Usage:
 *   const ok = await window.alouteConfirm('Xóa bài viết này?', {
 *       title: 'Xác nhận xóa',
 *       confirmText: 'Xóa',
 *       danger: true
 *   });
 */
(function () {
    'use strict';

    var MODAL_ID = 'aloute-confirm-dialog';
    var pendingResolve = null;
    var hidingGuard = false;

    function ensureModal() {
        var modal = document.getElementById(MODAL_ID);
        if (modal) return modal;

        modal = document.createElement('div');
        modal.className = 'modal fade';
        modal.id = MODAL_ID;
        modal.tabIndex = -1;
        modal.setAttribute('role', 'dialog');
        modal.setAttribute('aria-labelledby', MODAL_ID + '-title');
        modal.setAttribute('aria-hidden', 'true');
        modal.innerHTML =
            '<div class="modal-dialog modal-dialog-centered modal-sm" role="document">' +
            '  <div class="modal-content">' +
            '    <div class="modal-header py-2">' +
            '      <h2 class="modal-title fs-6" id="' + MODAL_ID + '-title">Xác nhận</h2>' +
            '      <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Đóng"></button>' +
            '    </div>' +
            '    <div class="modal-body"><p class="mb-0 small" id="' + MODAL_ID + '-message"></p></div>' +
            '    <div class="modal-footer py-2">' +
            '      <button type="button" class="btn btn-secondary btn-sm" data-bs-dismiss="modal" id="' + MODAL_ID + '-cancel">Hủy</button>' +
            '      <button type="button" class="btn btn-primary btn-sm" id="' + MODAL_ID + '-confirm">Xác nhận</button>' +
            '    </div>' +
            '  </div>' +
            '</div>';
        document.body.appendChild(modal);

        modal.addEventListener('hidden.bs.modal', function () {
            if (!hidingGuard && pendingResolve) {
                var resolve = pendingResolve;
                pendingResolve = null;
                resolve(false);
            }
            hidingGuard = false;
        });

        modal.querySelector('#' + MODAL_ID + '-confirm').addEventListener('click', function () {
            if (!pendingResolve) return;
            var resolve = pendingResolve;
            pendingResolve = null;
            hidingGuard = true;
            hideModal(modal);
            resolve(true);
        });

        return modal;
    }

    function hideModal(modal) {
        if (window.bootstrap && window.bootstrap.Modal) {
            var instance = window.bootstrap.Modal.getInstance(modal) || new window.bootstrap.Modal(modal);
            instance.hide();
        } else if (pendingResolve) {
            // Without Bootstrap JS the modal was shown manually; hide it the same way.
            modal.classList.remove('show');
            modal.style.display = 'none';
            modal.setAttribute('aria-hidden', 'true');
            document.body.classList.remove('modal-open');
            var backdrop = document.querySelector('.modal-backdrop');
            if (backdrop) backdrop.remove();
            modal.dispatchEvent(new window.CustomEvent('hidden.bs.modal'));
        }
    }

    window.alouteConfirm = function (message, options) {
        options = options || {};

        if (!window.bootstrap || !window.bootstrap.Modal) {
            // Graceful degradation for contexts without Bootstrap bundle.
            return Promise.resolve(window.confirm(message));
        }

        return new Promise(function (resolve) {
            if (pendingResolve) {
                // A confirmation is already open; refuse the new request.
                resolve(false);
                return;
            }

            var modal = ensureModal();
            modal.querySelector('#' + MODAL_ID + '-title').textContent = options.title || 'Xác nhận';
            modal.querySelector('#' + MODAL_ID + '-message').textContent = message;

            var confirmBtn = modal.querySelector('#' + MODAL_ID + '-confirm');
            confirmBtn.textContent = options.confirmText || 'Xác nhận';
            confirmBtn.className = 'btn btn-sm ' + (options.danger ? 'btn-danger' : 'btn-primary');
            var cancelBtn = modal.querySelector('#' + MODAL_ID + '-cancel');
            cancelBtn.textContent = options.cancelText || 'Hủy';

            pendingResolve = resolve;
            var instance = window.bootstrap.Modal.getInstance(modal) || new window.bootstrap.Modal(modal);
            instance.show();
        });
    };
})();
