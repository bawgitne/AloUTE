/**
 * AloUTE Admin Dashboard — Action Controller & Client Interactivity
 * Progressive enhancement with Vanilla JS.
 * Reuses window.alouteHttp (Cookie auth + X-XSRF-TOKEN).
 * Handles in-flight locks, robust 401/403/404/409/422 errors, character counters,
 * and safe textContent rendering.
 */
(function () {
    'use strict';

    if (typeof window !== 'undefined' && window.__alouteAdminInitialized) return;
    if (typeof window !== 'undefined') window.__alouteAdminInitialized = true;

    // Toast Notification Utility
    function showToast(message, type) {
        if (typeof document === 'undefined') return;
        let container = document.getElementById('adminToastContainer');
        if (!container) {
            container = document.createElement('div');
            container.id = 'adminToastContainer';
            container.className = 'admin-toast-container toast-container';
            document.body.appendChild(container);
        }

        const toastEl = document.createElement('div');
        toastEl.className = 'toast align-items-center text-white border-0 ' + (type === 'error' ? 'bg-danger' : 'bg-success');
        toastEl.setAttribute('role', 'alert');
        toastEl.setAttribute('aria-live', 'assertive');
        toastEl.setAttribute('aria-atomic', 'true');

        const flexDiv = document.createElement('div');
        flexDiv.className = 'd-flex';

        const bodyDiv = document.createElement('div');
        bodyDiv.className = 'toast-body';
        bodyDiv.textContent = message;

        const closeBtn = document.createElement('button');
        closeBtn.type = 'button';
        closeBtn.className = 'btn-close btn-close-white me-2 m-auto';
        closeBtn.setAttribute('data-bs-dismiss', 'toast');
        closeBtn.setAttribute('aria-label', 'Đóng thông báo');

        flexDiv.appendChild(bodyDiv);
        flexDiv.appendChild(closeBtn);
        toastEl.appendChild(flexDiv);
        container.appendChild(toastEl);

        if (window.bootstrap && window.bootstrap.Toast) {
            const toast = new window.bootstrap.Toast(toastEl, { delay: 4000 });
            toast.show();
            toastEl.addEventListener('hidden.bs.toast', function () {
                toastEl.remove();
            });
        } else {
            setTimeout(function () { toastEl.remove(); }, 4000);
        }
    }

    // Format UTC Dates
    function formatUtcDates() {
        if (typeof document === 'undefined') return;
        const elements = document.querySelectorAll('.utc-date, [data-utc-date]');
        elements.forEach(function (el) {
            const raw = el.getAttribute('data-utc-date') || el.textContent;
            if (!raw || !raw.trim()) return;
            try {
                const date = new Date(raw.trim());
                if (!isNaN(date.getTime())) {
                    const day = String(date.getDate()).padStart(2, '0');
                    const month = String(date.getMonth() + 1).padStart(2, '0');
                    const year = date.getFullYear();
                    const hours = String(date.getHours()).padStart(2, '0');
                    const minutes = String(date.getMinutes()).padStart(2, '0');
                    el.textContent = day + '/' + month + '/' + year + ' ' + hours + ':' + minutes;
                    el.title = date.toISOString();
                }
            } catch (_) {}
        });
    }

    // Modal Character Counters
    function initCharacterCounters() {
        if (typeof document === 'undefined') return;
        const inputs = document.querySelectorAll('[data-char-counter]');
        inputs.forEach(function (input) {
            const counterId = input.getAttribute('data-char-counter');
            const counterEl = document.getElementById(counterId);
            const minLen = parseInt(input.getAttribute('data-min-len') || '0', 10);
            const maxLen = parseInt(input.getAttribute('maxlength') || input.getAttribute('data-max-len') || '1000', 10);

            function updateCounter() {
                if (!counterEl) return;
                const len = input.value.trim().length;
                counterEl.textContent = len + ' / ' + maxLen + (minLen > 0 ? ' (Tối thiểu ' + minLen + ' ký tự)' : '');
                if (len < minLen || len > maxLen) {
                    counterEl.classList.add('invalid');
                } else {
                    counterEl.classList.remove('invalid');
                }
            }

            input.addEventListener('input', updateCounter);
            updateCounter();
        });
    }

    // Safe Error Message Extractor
    function extractErrorMessage(json, status) {
        if (json && json.error && json.error.message) {
            return json.error.message;
        }
        if (json && json.message) {
            return json.message;
        }
        switch (status) {
            case 401: return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
            case 403: return 'Bạn không có quyền thực hiện thao tác này (yêu cầu SYSTEM_ADMIN).';
            case 404: return 'Không tìm thấy tài nguyên được yêu cầu.';
            case 409: return 'Xung đột dữ liệu: Đối tượng đã bị thay đổi hoặc xử lý bởi quản trị viên khác.';
            case 422: return 'Dữ liệu không hợp lệ. Vui lòng kiểm tra lại các trường thông tin.';
            default: return 'Đã xảy ra lỗi trên hệ thống (' + status + '). Vui lòng thử lại.';
        }
    }

    // Post Moderation: Remove
    function initPostRemoveModal() {
        const modal = document.getElementById('adminRemovePostModal');
        if (!modal) return;

        const form = document.getElementById('adminRemovePostForm');
        const submitBtn = document.getElementById('btnConfirmRemovePost');
        const errorAlert = document.getElementById('removePostErrorAlert');
        let inFlight = false;

        document.querySelectorAll('[data-action="open-remove-post"]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                const postId = btn.getAttribute('data-post-id');
                const authorName = btn.getAttribute('data-author-name') || '';
                const reportId = btn.getAttribute('data-report-id') || '';

                form.reset();
                if (errorAlert) {
                    errorAlert.classList.add('d-none');
                    errorAlert.textContent = '';
                }

                document.getElementById('removePostTargetId').value = postId;
                document.getElementById('removePostDisplayId').textContent = '#' + postId;
                const authorEl = document.getElementById('removePostDisplayAuthor');
                if (authorEl) authorEl.textContent = authorName;
                const reportInput = document.getElementById('removePostRelatedReportId');
                if (reportInput) reportInput.value = reportId;

                const counterEl = document.getElementById('removePostReasonCounter');
                if (counterEl) counterEl.textContent = '0 / 1000 (Tối thiểu 10 ký tự)';

                if (window.bootstrap && window.bootstrap.Modal) {
                    const bsModal = window.bootstrap.Modal.getOrCreateInstance(modal);
                    bsModal.show();
                }
            });
        });

        if (form) {
            form.addEventListener('submit', async function (e) {
                e.preventDefault();
                if (inFlight) return;

                const postId = document.getElementById('removePostTargetId').value;
                const reasonCode = document.getElementById('removePostReasonCode').value;
                const reasonDetail = document.getElementById('removePostReasonDetail').value.trim();
                const publicNotice = document.getElementById('removePostPublicNotice') ? document.getElementById('removePostPublicNotice').value.trim() : '';
                const relatedReportId = document.getElementById('removePostRelatedReportId') && document.getElementById('removePostRelatedReportId').value
                    ? parseInt(document.getElementById('removePostRelatedReportId').value, 10) : null;

                if (!reasonCode) {
                    if (errorAlert) {
                        errorAlert.textContent = 'Vui lòng chọn danh mục lý do gỡ bài.';
                        errorAlert.classList.remove('d-none');
                    }
                    return;
                }
                if (reasonDetail.length < 10 || reasonDetail.length > 1000) {
                    if (errorAlert) {
                        errorAlert.textContent = 'Chi tiết lý do bắt buộc từ 10 đến 1000 ký tự.';
                        errorAlert.classList.remove('d-none');
                    }
                    return;
                }
                if (publicNotice && publicNotice.length > 500) {
                    if (errorAlert) {
                        errorAlert.textContent = 'Thông báo công khai không được vượt quá 500 ký tự.';
                        errorAlert.classList.remove('d-none');
                    }
                    return;
                }

                inFlight = true;
                if (submitBtn) {
                    submitBtn.disabled = true;
                    submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span> Đang xử lý...';
                }
                if (errorAlert) errorAlert.classList.add('d-none');

                try {
                    const payload = {
                        reasonCode: reasonCode,
                        reasonDetail: reasonDetail,
                        publicNotice: publicNotice || null,
                        relatedReportId: relatedReportId
                    };

                    const response = await window.alouteHttp.request('/api/admin/posts/' + postId + '/remove', {
                        method: 'POST',
                        body: payload
                    });

                    const json = await window.alouteHttp.readJson(response);

                    if (response.ok && json && json.success) {
                        showToast('Đã gỡ bài viết #' + postId + ' thành công.', 'success');
                        setTimeout(function () { window.location.reload(); }, 600);
                    } else {
                        const errMsg = extractErrorMessage(json, response.status);
                        if (errorAlert) {
                            errorAlert.textContent = errMsg;
                            errorAlert.classList.remove('d-none');
                        }
                    }
                } catch (err) {
                    if (errorAlert) {
                        errorAlert.textContent = 'Lỗi kết nối mạng: Không thể gửi yêu cầu gỡ bài.';
                        errorAlert.classList.remove('d-none');
                    }
                } finally {
                    inFlight = false;
                    if (submitBtn) {
                        submitBtn.disabled = false;
                        submitBtn.innerHTML = '<i class="bi bi-trash3 me-1"></i> Xác nhận gỡ bài';
                    }
                }
            });
        }
    }

    // Post Moderation: Restore
    function initPostRestoreModal() {
        const modal = document.getElementById('adminRestorePostModal');
        if (!modal) return;

        const form = document.getElementById('adminRestorePostForm');
        const submitBtn = document.getElementById('btnConfirmRestorePost');
        const errorAlert = document.getElementById('restorePostErrorAlert');
        let inFlight = false;

        document.querySelectorAll('[data-action="open-restore-post"]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                const postId = btn.getAttribute('data-post-id');
                const removalId = btn.getAttribute('data-removal-id');

                form.reset();
                if (errorAlert) {
                    errorAlert.classList.add('d-none');
                    errorAlert.textContent = '';
                }

                document.getElementById('restorePostTargetId').value = postId;
                document.getElementById('restorePostExpectedRemovalId').value = removalId;
                document.getElementById('restorePostDisplayId').textContent = '#' + postId;
                document.getElementById('restorePostDisplayRemovalId').textContent = '#' + removalId;

                const counterEl = document.getElementById('restorePostReasonCounter');
                if (counterEl) counterEl.textContent = '0 / 1000 (Tối thiểu 10 ký tự)';

                if (window.bootstrap && window.bootstrap.Modal) {
                    const bsModal = window.bootstrap.Modal.getOrCreateInstance(modal);
                    bsModal.show();
                }
            });
        });

        if (form) {
            form.addEventListener('submit', async function (e) {
                e.preventDefault();
                if (inFlight) return;

                const postId = document.getElementById('restorePostTargetId').value;
                const expectedRemovalId = parseInt(document.getElementById('restorePostExpectedRemovalId').value, 10);
                const reasonDetail = document.getElementById('restorePostReasonDetail').value.trim();

                if (reasonDetail.length < 10 || reasonDetail.length > 1000) {
                    if (errorAlert) {
                        errorAlert.textContent = 'Chi tiết lý do khôi phục bắt buộc từ 10 đến 1000 ký tự.';
                        errorAlert.classList.remove('d-none');
                    }
                    return;
                }

                inFlight = true;
                if (submitBtn) {
                    submitBtn.disabled = true;
                    submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span> Đang xử lý...';
                }
                if (errorAlert) errorAlert.classList.add('d-none');

                try {
                    const payload = {
                        expectedRemovalId: expectedRemovalId,
                        reasonDetail: reasonDetail
                    };

                    const response = await window.alouteHttp.request('/api/admin/posts/' + postId + '/restore', {
                        method: 'POST',
                        body: payload
                    });

                    const json = await window.alouteHttp.readJson(response);

                    if (response.ok && json && json.success) {
                        showToast('Đã khôi phục bài viết #' + postId + ' thành công.', 'success');
                        setTimeout(function () { window.location.reload(); }, 600);
                    } else {
                        const errMsg = extractErrorMessage(json, response.status);
                        if (errorAlert) {
                            errorAlert.textContent = errMsg;
                            errorAlert.classList.remove('d-none');
                        }
                    }
                } catch (err) {
                    if (errorAlert) {
                        errorAlert.textContent = 'Lỗi kết nối mạng: Không thể gửi yêu cầu khôi phục.';
                        errorAlert.classList.remove('d-none');
                    }
                } finally {
                    inFlight = false;
                    if (submitBtn) {
                        submitBtn.disabled = false;
                        submitBtn.innerHTML = '<i class="bi bi-arrow-counterclockwise me-1"></i> Xác nhận khôi phục';
                    }
                }
            });
        }
    }

    // Report Actions: Start Review
    function initReportStartReview() {
        document.querySelectorAll('[data-action="start-report-review"]').forEach(function (btn) {
            btn.addEventListener('click', async function () {
                const reportId = btn.getAttribute('data-report-id');
                const expectedVersion = parseInt(btn.getAttribute('data-expected-version') || '0', 10);

                const confirmed = await window.alouteConfirm(
                    'Nhận xử lý báo cáo #' + reportId + '? Báo cáo sẽ chuyển sang trạng thái "Đang xem xét" và được gán cho bạn.',
                    { title: 'Nhận xử lý báo cáo', confirmText: 'Nhận xử lý', danger: false }
                );
                if (!confirmed) {
                    return;
                }

                btn.disabled = true;
                const originalHtml = btn.innerHTML;
                btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span> Đang nhận...';

                try {
                    const response = await window.alouteHttp.request('/api/admin/reports/' + reportId + '/start-review', {
                        method: 'POST',
                        body: { expectedVersion: expectedVersion }
                    });

                    const json = await window.alouteHttp.readJson(response);

                    if (response.ok && json && json.success) {
                        showToast('Đã nhận xử lý báo cáo #' + reportId + '.', 'success');
                        setTimeout(function () { window.location.reload(); }, 500);
                    } else {
                        const errMsg = extractErrorMessage(json, response.status);
                        showToast(errMsg, 'danger');
                        if (response.status === 409) {
                            setTimeout(function () { window.location.reload(); }, 1200);
                        }
                    }
                } catch (err) {
                    showToast('Lỗi kết nối mạng: Không thể nhận xử lý báo cáo.', 'danger');
                } finally {
                    btn.disabled = false;
                    btn.innerHTML = originalHtml;
                }
            });
        });
    }

    // Report Decision: Resolve / Reject Modal
    function initReportDecisionModal() {
        const modal = document.getElementById('adminReportDecisionModal');
        if (!modal) return;

        const form = document.getElementById('adminReportDecisionForm');
        const submitBtn = document.getElementById('btnConfirmReportDecision');
        const errorAlert = document.getElementById('reportDecisionErrorAlert');
        const postRemoveCheckbox = document.getElementById('reportDecisionAtomicRemovePost');
        const postRemoveFields = document.getElementById('reportDecisionPostRemoveFields');
        let inFlight = false;

        if (postRemoveCheckbox && postRemoveFields) {
            postRemoveCheckbox.addEventListener('change', function () {
                if (postRemoveCheckbox.checked) {
                    postRemoveFields.classList.remove('d-none');
                } else {
                    postRemoveFields.classList.add('d-none');
                }
            });
        }

        document.querySelectorAll('[data-action="open-report-decision"]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                const reportId = btn.getAttribute('data-report-id');
                const version = btn.getAttribute('data-expected-version') || '0';
                const decision = btn.getAttribute('data-decision'); // "RESOLVE" or "REJECT"
                const targetType = btn.getAttribute('data-target-type') || '';
                const targetId = btn.getAttribute('data-target-id') || '';
                const targetCanAtomicRemove = btn.getAttribute('data-target-can-atomic-remove') === 'true';

                form.reset();
                if (errorAlert) {
                    errorAlert.classList.add('d-none');
                    errorAlert.textContent = '';
                }

                document.getElementById('reportDecisionTargetId').value = reportId;
                document.getElementById('reportDecisionExpectedVersion').value = version;
                document.getElementById('reportDecisionType').value = decision;
                document.getElementById('reportDecisionDisplayId').textContent = '#' + reportId;

                const titleEl = document.getElementById('reportDecisionModalTitle');
                if (titleEl) {
                    titleEl.textContent = decision === 'RESOLVE' ? 'Giải quyết báo cáo #' + reportId : 'Từ chối báo cáo #' + reportId;
                }

                const postRemoveContainer = document.getElementById('reportDecisionPostRemoveContainer');
                if (postRemoveContainer) {
                    if (decision === 'RESOLVE' && targetType === 'POST' && targetCanAtomicRemove) {
                        postRemoveContainer.classList.remove('d-none');
                        const removePostTargetLabel = document.getElementById('reportDecisionPostRemoveId');
                        if (removePostTargetLabel) removePostTargetLabel.textContent = '#' + targetId;
                    } else {
                        postRemoveContainer.classList.add('d-none');
                        if (postRemoveCheckbox) postRemoveCheckbox.checked = false;
                    }
                }

                if (postRemoveFields) postRemoveFields.classList.add('d-none');

                const counterEl = document.getElementById('reportDecisionNoteCounter');
                if (counterEl) counterEl.textContent = '0 / 1000 (Tối thiểu 10 ký tự)';

                if (submitBtn) {
                    if (decision === 'RESOLVE') {
                        submitBtn.className = 'btn btn-success';
                        submitBtn.innerHTML = '<i class="bi bi-check-circle me-1"></i> Xác nhận giải quyết';
                    } else {
                        submitBtn.className = 'btn btn-danger';
                        submitBtn.innerHTML = '<i class="bi bi-x-circle me-1"></i> Xác nhận từ chối';
                    }
                }

                if (window.bootstrap && window.bootstrap.Modal) {
                    const bsModal = window.bootstrap.Modal.getOrCreateInstance(modal);
                    bsModal.show();
                }
            });
        });

        if (form) {
            form.addEventListener('submit', async function (e) {
                e.preventDefault();
                if (inFlight) return;

                const reportId = document.getElementById('reportDecisionTargetId').value;
                const expectedVersion = parseInt(document.getElementById('reportDecisionExpectedVersion').value, 10);
                const decision = document.getElementById('reportDecisionType').value;
                const resolutionNote = document.getElementById('reportDecisionResolutionNote').value.trim();

                if (resolutionNote.length < 10 || resolutionNote.length > 1000) {
                    if (errorAlert) {
                        errorAlert.textContent = 'Ghi chú quyết định bắt buộc từ 10 đến 1000 ký tự.';
                        errorAlert.classList.remove('d-none');
                    }
                    return;
                }

                let resolutionAction = null;
                if (decision === 'RESOLVE' && postRemoveCheckbox && postRemoveCheckbox.checked) {
                    const reasonCode = document.getElementById('reportDecisionRemoveReasonCode').value;
                    const reasonDetail = document.getElementById('reportDecisionRemoveReasonDetail').value.trim();
                    const publicNotice = document.getElementById('reportDecisionRemovePublicNotice').value.trim();

                    if (!reasonCode) {
                        if (errorAlert) {
                            errorAlert.textContent = 'Vui lòng chọn lý do gỡ bài viết vi phạm.';
                            errorAlert.classList.remove('d-none');
                        }
                        return;
                    }
                    if (reasonDetail && (reasonDetail.length < 10 || reasonDetail.length > 1000)) {
                        if (errorAlert) {
                            errorAlert.textContent = 'Chi tiết lý do gỡ bài nếu nhập phải từ 10 đến 1000 ký tự.';
                            errorAlert.classList.remove('d-none');
                        }
                        return;
                    }
                    if (publicNotice && publicNotice.length > 500) {
                        if (errorAlert) {
                            errorAlert.textContent = 'Thông báo gửi tác giả không được vượt quá 500 ký tự.';
                            errorAlert.classList.remove('d-none');
                        }
                        return;
                    }

                    resolutionAction = {
                        type: 'POST_REMOVE',
                        reasonCode: reasonCode,
                        reasonDetail: reasonDetail || resolutionNote,
                        publicNotice: publicNotice || null
                    };
                }

                inFlight = true;
                if (submitBtn) {
                    submitBtn.disabled = true;
                    submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span> Đang xử lý...';
                }
                if (errorAlert) errorAlert.classList.add('d-none');

                try {
                    const payload = {
                        expectedVersion: expectedVersion,
                        decision: decision,
                        resolutionNote: resolutionNote,
                        resolutionAction: resolutionAction
                    };

                    const response = await window.alouteHttp.request('/api/admin/reports/' + reportId + '/decisions', {
                        method: 'POST',
                        body: payload
                    });

                    const json = await window.alouteHttp.readJson(response);

                    if (response.ok && json && json.success) {
                        showToast('Đã ' + (decision === 'RESOLVE' ? 'giải quyết' : 'từ chối') + ' báo cáo #' + reportId + ' thành công.', 'success');
                        setTimeout(function () { window.location.reload(); }, 600);
                    } else {
                        const errMsg = extractErrorMessage(json, response.status);
                        if (errorAlert) {
                            errorAlert.textContent = errMsg;
                            errorAlert.classList.remove('d-none');
                        }
                        if (response.status === 409) {
                            errorAlert.textContent = errMsg + ' (Vui lòng tải lại trang để xem trạng thái mới nhất).';
                        }
                    }
                } catch (err) {
                    if (errorAlert) {
                        errorAlert.textContent = 'Lỗi kết nối mạng: Không thể gửi quyết định báo cáo.';
                        errorAlert.classList.remove('d-none');
                    }
                } finally {
                    inFlight = false;
                    if (submitBtn) {
                        submitBtn.disabled = false;
                        if (decision === 'RESOLVE') {
                            submitBtn.innerHTML = '<i class="bi bi-check-circle me-1"></i> Xác nhận giải quyết';
                        } else {
                            submitBtn.innerHTML = '<i class="bi bi-x-circle me-1"></i> Xác nhận từ chối';
                        }
                    }
                }
            });
        }
    }

    // Moderation Action: View Details Modal
    let actionDetailRequestId = 0;

    function initModerationActionDetailModal() {
        const modal = document.getElementById('adminActionDetailModal');
        if (!modal) return;

        const loadingEl = document.getElementById('actionDetailLoading');
        const errorEl = document.getElementById('actionDetailError');
        const errorMsgEl = document.getElementById('actionDetailErrorMessage');
        const containerEl = document.getElementById('actionDetailContainer');

        const idEl = document.getElementById('actionDetailId');
        const actorEl = document.getElementById('actionDetailActor');
        const typeEl = document.getElementById('actionDetailType');
        const targetEl = document.getElementById('actionDetailTarget');
        const createdEl = document.getElementById('actionDetailCreated');
        const reasonEl = document.getElementById('actionDetailReason');
        const detailEl = document.getElementById('actionDetailDetail');
        const metadataEl = document.getElementById('actionDetailMetadata');

        function clearStaleDetails() {
            if (idEl) idEl.textContent = '';
            if (actorEl) actorEl.textContent = '';
            if (typeEl) typeEl.textContent = '';
            if (targetEl) targetEl.textContent = '';
            if (createdEl) createdEl.textContent = '';
            if (reasonEl) reasonEl.textContent = '';
            if (detailEl) detailEl.textContent = '';
            if (metadataEl) {
                metadataEl.textContent = '';
                if (metadataEl.parentElement) {
                    metadataEl.parentElement.classList.add('d-none');
                }
            }
        }

        function showDetailError(message) {
            if (loadingEl) loadingEl.classList.add('d-none');
            if (containerEl) containerEl.classList.add('d-none');
            if (errorMsgEl) errorMsgEl.textContent = message;
            if (errorEl) errorEl.classList.remove('d-none');
        }

        document.querySelectorAll('[data-action="view-action-detail"]').forEach(function (btn) {
            btn.addEventListener('click', async function () {
                const actionId = btn.getAttribute('data-action-id');
                const requestId = ++actionDetailRequestId;

                // 1. Reset state: clear stale values, show loading, hide error & content
                clearStaleDetails();
                if (errorEl) errorEl.classList.add('d-none');
                if (containerEl) containerEl.classList.add('d-none');
                if (loadingEl) loadingEl.classList.remove('d-none');

                if (window.bootstrap && window.bootstrap.Modal) {
                    const bsModal = window.bootstrap.Modal.getOrCreateInstance(modal);
                    bsModal.show();
                }

                try {
                    if (!window.alouteHttp) {
                        showDetailError('Trình điều khiển HTTP alouteHttp không khả dụng.');
                        return;
                    }

                    const response = await window.alouteHttp.request('/api/admin/moderation-actions/' + actionId, {
                        method: 'GET'
                    });
                    const json = await window.alouteHttp.readJson(response);

                    // Discard if a newer request was dispatched
                    if (requestId !== actionDetailRequestId) return;

                    if (!response || !response.ok) {
                        const msg = extractErrorMessage(json, response ? response.status : 0);
                        showDetailError(msg);
                        return;
                    }

                    // Enforce envelope structure and required fields: success === true and non-null data.id
                    if (!json || json.success !== true || !json.data || json.data.id === null || typeof json.data.id === 'undefined') {
                        showDetailError('Dữ liệu chi tiết hành động không hợp lệ hoặc thiếu thông tin.');
                        return;
                    }

                    const d = json.data;
                    if (loadingEl) loadingEl.classList.add('d-none');
                    if (errorEl) errorEl.classList.add('d-none');
                    if (containerEl) containerEl.classList.remove('d-none');

                    if (idEl) idEl.textContent = '#' + d.id;
                    if (actorEl) actorEl.textContent = d.actor ? (d.actor.displayName || d.actor.username || 'Admin') : 'Hệ thống';
                    if (typeEl) typeEl.textContent = d.actionType || '';
                    if (targetEl) targetEl.textContent = (d.targetType || '') + ' #' + (d.targetId || '');
                    if (reasonEl) reasonEl.textContent = d.reasonCode || 'N/A';
                    if (detailEl) detailEl.textContent = d.reasonDetail || 'Không có ghi chú thêm.';
                    if (createdEl) createdEl.textContent = d.createdAt ? new Date(d.createdAt).toLocaleString('vi-VN') : '';

                    if (metadataEl) {
                        if (d.metadata && typeof d.metadata === 'object' && Object.keys(d.metadata).length > 0) {
                            metadataEl.textContent = JSON.stringify(d.metadata, null, 2);
                            if (metadataEl.parentElement) metadataEl.parentElement.classList.remove('d-none');
                        } else {
                            if (metadataEl.parentElement) metadataEl.parentElement.classList.add('d-none');
                        }
                    }
                } catch (err) {
                    if (requestId !== actionDetailRequestId) return;
                    showDetailError('Lỗi kết nối khi tải chi tiết kiểm duyệt: ' + (err.message || 'Network error'));
                }
            });
        });
    }

    // Filter Form Auto-reset Helper
    function initFilterFormReset() {
        document.querySelectorAll('[data-action="reset-filter"]').forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                e.preventDefault();
                const targetUrl = btn.getAttribute('data-target-url') || window.location.pathname;
                window.location.href = targetUrl;
            });
        });
    }

    // Admin Topbar Logout Handler
    function initLogout() {
        const form = document.getElementById('adminLogoutForm');
        if (!form) return;

        form.addEventListener('submit', async function (e) {
            e.preventDefault();
            const btn = document.getElementById('adminBtnLogout') || form.querySelector('button[type="submit"]');
            if (btn) {
                btn.disabled = true;
                btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span> Đang đăng xuất...';
            }
            try {
                if (window.alouteHttp) {
                    await window.alouteHttp.request('/api/auth/logout', { method: 'POST' });
                } else {
                    await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' });
                }
            } catch (_) {
                // Ignore network error on logout, proceed to redirect
            } finally {
                window.location.href = '/login';
            }
        });
    }

    // Initialize all modules on DOM ready
    function init() {
        formatUtcDates();
        initCharacterCounters();
        initPostRemoveModal();
        initPostRestoreModal();
        initReportStartReview();
        initReportDecisionModal();
        initModerationActionDetailModal();
        initFilterFormReset();
        initLogout();
    }

    if (typeof document !== 'undefined') {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', init);
        } else {
            init();
        }
    }

    if (typeof window !== 'undefined') {
        window.alouteAdmin = {
            init: init,
            formatUtcDates: formatUtcDates,
            showToast: showToast
        };
    }
})();
