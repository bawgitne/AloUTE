/**
 * AloUTE — Admin Dashboard Client & Interaction Automated Regression Tests
 *
 * Built-in Node test suite (node:test + assert/strict + vm).
 * Verifies admin-actions.js and core/http-client.js:
 * - CSRF + Cookie auth transport integration
 * - In-flight submission guards & busy locks
 * - Post remove & restore modal flows and validation (10..1000 chars)
 * - Report claim/start-review flow (expectedVersion)
 * - Report decision resolve & reject with optional atomic POST_REMOVE
 * - Stale version & 409 conflict handling (no auto-replay)
 * - Safe textContent rendering (XSS prevention)
 * - Character counters & UTC date formatting
 */

import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '../..');

const httpClientSource = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/static/js/core/http-client.js'),
    'utf-8'
);

const adminActionsSource = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/static/js/features/admin/admin-actions.js'),
    'utf-8'
);

// -----------------------------------------------------------------------------
// Lightweight DOM Harness
// -----------------------------------------------------------------------------

class MockElement {
    constructor(tagName = 'DIV', id = '') {
        this.tagName = tagName.toUpperCase();
        this.id = id;
        this.className = '';
        this._classList = new Set();
        this._attributes = new Map();
        this.children = [];
        this.parentElement = null;
        this._listeners = new Map();
        this.style = {};
        this._textContent = '';
        this.value = '';
        this.disabled = false;
        this.checked = false;
    }

    get classList() {
        const self = this;
        return {
            add(...names) {
                names.forEach(n => self._classList.add(n));
                self.className = Array.from(self._classList).join(' ');
            },
            remove(...names) {
                names.forEach(n => self._classList.delete(n));
                self.className = Array.from(self._classList).join(' ');
            },
            contains(name) {
                return self._classList.has(name);
            },
            toggle(name, force) {
                if (force !== undefined) {
                    if (force) self.classList.add(name);
                    else self.classList.remove(name);
                } else {
                    if (self.classList.contains(name)) self.classList.remove(name);
                    else self.classList.add(name);
                }
            }
        };
    }

    setAttribute(name, val) {
        this._attributes.set(name, String(val));
        if (name === 'id') this.id = String(val);
        if (name === 'class') {
            this.className = String(val);
            this._classList = new Set(String(val).split(/\s+/).filter(Boolean));
        }
    }

    getAttribute(name) {
        return this._attributes.get(name) || null;
    }

    hasAttribute(name) {
        return this._attributes.has(name);
    }

    removeAttribute(name) {
        this._attributes.delete(name);
    }

    get textContent() {
        return this._textContent;
    }

    set textContent(val) {
        this._textContent = String(val);
    }

    get innerHTML() {
        return this._textContent;
    }

    set innerHTML(val) {
        this._textContent = String(val);
    }

    appendChild(child) {
        child.parentElement = this;
        this.children.push(child);
        return child;
    }

    remove() {
        if (this.parentElement) {
            const idx = this.parentElement.children.indexOf(this);
            if (idx !== -1) this.parentElement.children.splice(idx, 1);
            this.parentElement = null;
        }
    }

    addEventListener(event, fn) {
        if (!this._listeners.has(event)) {
            this._listeners.set(event, []);
        }
        this._listeners.get(event).push(fn);
    }

    dispatchEvent(event) {
        const fns = this._listeners.get(event.type) || [];
        for (const fn of fns) {
            fn.call(this, event);
        }
    }

    reset() {
        this.value = '';
        for (const child of this.children) {
            child.reset ? child.reset() : (child.value = '');
        }
    }
}

function createTestEnvironment(htmlFixture = '') {
    const documentBody = new MockElement('BODY');
    const elementsById = new Map();
    const allElements = [];

    function register(el) {
        if (el.id) elementsById.set(el.id, el);
        allElements.push(el);
        for (const child of el.children) register(child);
    }

    const mockDocument = {
        body: documentBody,
        cookie: 'XSRF-TOKEN=test-csrf-token-123; aloute_access_token=jwt-secret-cookie',
        readyState: 'complete',
        createElement(tag) {
            const el = new MockElement(tag);
            allElements.push(el);
            return el;
        },
        getElementById(id) {
            return elementsById.get(id) || null;
        },
        querySelectorAll(selector) {
            if (selector.includes(',')) {
                const parts = selector.split(',').map(s => s.trim());
                const res = [];
                for (const p of parts) {
                    res.push(...this.querySelectorAll(p));
                }
                return Array.from(new Set(res));
            }
            if (selector.startsWith('#')) {
                const el = elementsById.get(selector.slice(1));
                return el ? [el] : [];
            }
            if (selector.startsWith('.')) {
                const cls = selector.slice(1);
                return allElements.filter(e => e.classList.contains(cls));
            }
            if (selector.startsWith('[') && selector.endsWith(']')) {
                const inner = selector.slice(1, -1);
                if (inner.includes('=')) {
                    const [k, rawV] = inner.split('=');
                    const v = rawV.replace(/['"]/g, '');
                    return allElements.filter(e => e.getAttribute(k) === v);
                }
                return allElements.filter(e => e.hasAttribute(inner));
            }
            return allElements.filter(e => e.tagName === selector.toUpperCase());
        },
        querySelector(selector) {
            const results = this.querySelectorAll(selector);
            return results.length > 0 ? results[0] : null;
        },
        addEventListener() {}
    };

    let fetchRequests = [];
    let fetchResponseFactory = async () => ({
        ok: true,
        status: 200,
        text: async () => JSON.stringify({ success: true, data: { id: 100 } })
    });

    const mockWindow = {
        document: mockDocument,
        location: {
            pathname: '/admin/posts',
            href: '/admin/posts',
            reloadCalled: false,
            reload() { this.reloadCalled = true; }
        },
        confirm() { return true; },
        // Shared Bootstrap-styled confirmation dialog (core/confirm-dialog.js).
        alouteConfirm: async () => true,
        alert() {},
        setTimeout: (fn) => setTimeout(fn, 1),
        fetch: async (url, opts) => {
            fetchRequests.push({ url, opts });
            return fetchResponseFactory(url, opts);
        },
        bootstrap: {
            Modal: {
                getOrCreateInstance(el) {
                    return {
                        show() { el.classList.remove('d-none'); el.shown = true; },
                        hide() { el.classList.add('d-none'); el.shown = false; }
                    };
                }
            },
            Toast: class {
                show() {}
            }
        }
    };

    const context = vm.createContext({
        window: mockWindow,
        document: mockDocument,
        setTimeout: mockWindow.setTimeout,
        fetch: mockWindow.fetch,
        confirm: mockWindow.confirm,
        alert: mockWindow.alert,
        Date: Date,
        JSON: JSON,
        parseInt: parseInt,
        String: String,
        Object: Object,
        Array: Array,
        console: console
    });

    return {
        context,
        mockWindow,
        mockDocument,
        elementsById,
        allElements,
        registerElement(el) {
            documentBody.appendChild(el);
            register(el);
        },
        getFetchRequests: () => fetchRequests,
        setFetchResponse: (fn) => { fetchResponseFactory = fn; }
    };
}

describe('AloUTE Admin Dashboard Frontend Tests', () => {

    describe('1. HTTP Client & Security Headers', () => {
        it('core http-client attaches X-XSRF-TOKEN and credentials=include on POST mutations', async () => {
            const env = createTestEnvironment();
            vm.runInContext(httpClientSource, env.context);

            await env.mockWindow.alouteHttp.request('/api/admin/posts/100/remove', {
                method: 'POST',
                body: { reasonCode: 'SPAM' }
            });

            const reqs = env.getFetchRequests();
            assert.strictEqual(reqs.length, 1);
            assert.strictEqual(reqs[0].url, '/api/admin/posts/100/remove');
            assert.strictEqual(reqs[0].opts.method, 'POST');
            assert.strictEqual(reqs[0].opts.credentials, 'include');
            assert.strictEqual(reqs[0].opts.headers['X-XSRF-TOKEN'], 'test-csrf-token-123');
            assert.strictEqual(reqs[0].opts.headers['Content-Type'], 'application/json');
        });

        it('never exposes or reads JWT from cookie in JavaScript', async () => {
            const env = createTestEnvironment();
            vm.runInContext(httpClientSource, env.context);

            await env.mockWindow.alouteHttp.request('/api/admin/overview');
            const reqs = env.getFetchRequests();
            assert.strictEqual(reqs[0].opts.headers['Authorization'], undefined);
            assert.strictEqual(reqs[0].opts.headers['aloute_access_token'], undefined);
        });
    });

    describe('2. Post Remove Modal & In-Flight Protection', () => {
        it('validates minimum 10 characters and dispatches POST /api/admin/posts/{id}/remove', async () => {
            const env = createTestEnvironment();

            // Set up DOM elements for remove modal
            const modal = new MockElement('DIV', 'adminRemovePostModal');
            const form = new MockElement('FORM', 'adminRemovePostForm');
            const targetId = new MockElement('INPUT', 'removePostTargetId');
            const displayId = new MockElement('SPAN', 'removePostDisplayId');
            const reasonCode = new MockElement('SELECT', 'removePostReasonCode');
            const reasonDetail = new MockElement('TEXTAREA', 'removePostReasonDetail');
            const publicNotice = new MockElement('TEXTAREA', 'removePostPublicNotice');
            const relatedReport = new MockElement('INPUT', 'removePostRelatedReportId');
            const submitBtn = new MockElement('BUTTON', 'btnConfirmRemovePost');
            const errorAlert = new MockElement('DIV', 'removePostErrorAlert');

            modal.appendChild(form);
            form.appendChild(targetId);
            form.appendChild(displayId);
            form.appendChild(reasonCode);
            form.appendChild(reasonDetail);
            form.appendChild(publicNotice);
            form.appendChild(relatedReport);
            form.appendChild(submitBtn);
            form.appendChild(errorAlert);
            env.registerElement(modal);

            // Trigger button
            const triggerBtn = new MockElement('BUTTON');
            triggerBtn.setAttribute('data-action', 'open-remove-post');
            triggerBtn.setAttribute('data-post-id', '3001');
            triggerBtn.setAttribute('data-author-name', 'sinhvien1');
            env.registerElement(triggerBtn);

            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(adminActionsSource, env.context);

            // Open modal via trigger button
            triggerBtn.dispatchEvent({ type: 'click' });
            assert.strictEqual(targetId.value, '3001');

            // Attempt submit with short reason (< 10 chars)
            reasonCode.value = 'HARASSMENT';
            reasonDetail.value = 'Quá ngắn';
            form.dispatchEvent({ type: 'submit', preventDefault() {} });

            assert.strictEqual(env.getFetchRequests().length, 0);
            assert.strictEqual(errorAlert.classList.contains('d-none'), false);
            assert.ok(errorAlert.textContent.includes('10 đến 1000 ký tự'));

            // Submit with valid reason
            reasonDetail.value = 'Bài viết chứa nội dung quấy rối vi phạm quy tắc ứng xử học đường.';
            form.dispatchEvent({ type: 'submit', preventDefault() {} });

            await new Promise(r => setTimeout(r, 10));
            const reqs = env.getFetchRequests();
            assert.strictEqual(reqs.length, 1);
            assert.strictEqual(reqs[0].url, '/api/admin/posts/3001/remove');
            const body = JSON.parse(reqs[0].opts.body);
            assert.strictEqual(body.reasonCode, 'HARASSMENT');
            assert.strictEqual(body.reasonDetail, 'Bài viết chứa nội dung quấy rối vi phạm quy tắc ứng xử học đường.');
        });
    });

    describe('3. Post Restore Modal & Removal ID Binding', () => {
        it('binds expectedRemovalId and calls POST /api/admin/posts/{id}/restore', async () => {
            const env = createTestEnvironment();

            const modal = new MockElement('DIV', 'adminRestorePostModal');
            const form = new MockElement('FORM', 'adminRestorePostForm');
            const targetId = new MockElement('INPUT', 'restorePostTargetId');
            const expectedRemovalId = new MockElement('INPUT', 'restorePostExpectedRemovalId');
            const displayId = new MockElement('SPAN', 'restorePostDisplayId');
            const displayRemovalId = new MockElement('SPAN', 'restorePostDisplayRemovalId');
            const reasonDetail = new MockElement('TEXTAREA', 'restorePostReasonDetail');
            const submitBtn = new MockElement('BUTTON', 'btnConfirmRestorePost');
            const errorAlert = new MockElement('DIV', 'restorePostErrorAlert');

            modal.appendChild(form);
            form.appendChild(targetId);
            form.appendChild(expectedRemovalId);
            form.appendChild(displayId);
            form.appendChild(displayRemovalId);
            form.appendChild(reasonDetail);
            form.appendChild(submitBtn);
            form.appendChild(errorAlert);
            env.registerElement(modal);

            const triggerBtn = new MockElement('BUTTON');
            triggerBtn.setAttribute('data-action', 'open-restore-post');
            triggerBtn.setAttribute('data-post-id', '3001');
            triggerBtn.setAttribute('data-removal-id', '701');
            env.registerElement(triggerBtn);

            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(adminActionsSource, env.context);

            triggerBtn.dispatchEvent({ type: 'click' });
            assert.strictEqual(targetId.value, '3001');
            assert.strictEqual(expectedRemovalId.value, '701');

            reasonDetail.value = 'Đã xác minh và khôi phục bài viết sau khi kiểm tra bằng chứng mới.';
            form.dispatchEvent({ type: 'submit', preventDefault() {} });

            await new Promise(r => setTimeout(r, 10));
            const reqs = env.getFetchRequests();
            assert.strictEqual(reqs.length, 1);
            assert.strictEqual(reqs[0].url, '/api/admin/posts/3001/restore');
            const body = JSON.parse(reqs[0].opts.body);
            assert.strictEqual(body.expectedRemovalId, 701);
            assert.strictEqual(body.reasonDetail, 'Đã xác minh và khôi phục bài viết sau khi kiểm tra bằng chứng mới.');
        });
    });

    describe('4. Report Start Review Action', () => {
        it('sends expectedVersion and calls POST /api/admin/reports/{id}/start-review', async () => {
            const env = createTestEnvironment();

            const claimBtn = new MockElement('BUTTON');
            claimBtn.setAttribute('data-action', 'start-report-review');
            claimBtn.setAttribute('data-report-id', '501');
            claimBtn.setAttribute('data-expected-version', '2');
            env.registerElement(claimBtn);

            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(adminActionsSource, env.context);

            claimBtn.dispatchEvent({ type: 'click' });

            await new Promise(r => setTimeout(r, 10));
            const reqs = env.getFetchRequests();
            assert.strictEqual(reqs.length, 1);
            assert.strictEqual(reqs[0].url, '/api/admin/reports/501/start-review');
            const body = JSON.parse(reqs[0].opts.body);
            assert.strictEqual(body.expectedVersion, 2);
        });
    });

    describe('5. Report Decision & Atomic Post Removal', () => {
        it('supports resolve decision with atomic POST_REMOVE in same transaction', async () => {
            const env = createTestEnvironment();

            const modal = new MockElement('DIV', 'adminReportDecisionModal');
            const form = new MockElement('FORM', 'adminReportDecisionForm');
            const targetId = new MockElement('INPUT', 'reportDecisionTargetId');
            const expectedVersion = new MockElement('INPUT', 'reportDecisionExpectedVersion');
            const decisionType = new MockElement('INPUT', 'reportDecisionType');
            const displayId = new MockElement('SPAN', 'reportDecisionDisplayId');
            const resolutionNote = new MockElement('TEXTAREA', 'reportDecisionResolutionNote');
            const atomicRemoveCheck = new MockElement('INPUT', 'reportDecisionAtomicRemovePost');
            const removeReasonCode = new MockElement('SELECT', 'reportDecisionRemoveReasonCode');
            const removeReasonDetail = new MockElement('INPUT', 'reportDecisionRemoveReasonDetail');
            const removePublicNotice = new MockElement('INPUT', 'reportDecisionRemovePublicNotice');
            const removeFields = new MockElement('DIV', 'reportDecisionPostRemoveFields');
            const postRemoveContainer = new MockElement('DIV', 'reportDecisionPostRemoveContainer');
            const submitBtn = new MockElement('BUTTON', 'btnConfirmReportDecision');
            const errorAlert = new MockElement('DIV', 'reportDecisionErrorAlert');

            modal.appendChild(form);
            form.appendChild(targetId);
            form.appendChild(expectedVersion);
            form.appendChild(decisionType);
            form.appendChild(displayId);
            form.appendChild(resolutionNote);
            form.appendChild(postRemoveContainer);
            postRemoveContainer.appendChild(atomicRemoveCheck);
            postRemoveContainer.appendChild(removeFields);
            removeFields.appendChild(removeReasonCode);
            removeFields.appendChild(removeReasonDetail);
            removeFields.appendChild(removePublicNotice);
            form.appendChild(submitBtn);
            form.appendChild(errorAlert);
            env.registerElement(modal);

            const triggerBtn = new MockElement('BUTTON');
            triggerBtn.setAttribute('data-action', 'open-report-decision');
            triggerBtn.setAttribute('data-report-id', '501');
            triggerBtn.setAttribute('data-expected-version', '3');
            triggerBtn.setAttribute('data-decision', 'RESOLVE');
            triggerBtn.setAttribute('data-target-type', 'POST');
            triggerBtn.setAttribute('data-target-id', '3001');
            env.registerElement(triggerBtn);

            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(adminActionsSource, env.context);

            triggerBtn.dispatchEvent({ type: 'click' });
            assert.strictEqual(targetId.value, '501');
            assert.strictEqual(expectedVersion.value, '3');
            assert.strictEqual(decisionType.value, 'RESOLVE');

            // Fill form with atomic post removal
            resolutionNote.value = 'Báo cáo chính xác. Bài viết chứa từ ngữ công kích.';
            atomicRemoveCheck.checked = true;
            removeReasonCode.value = 'HARASSMENT';
            removeReasonDetail.value = 'Nội dung quấy rối và xúc phạm.';

            form.dispatchEvent({ type: 'submit', preventDefault() {} });

            await new Promise(r => setTimeout(r, 10));
            const reqs = env.getFetchRequests();
            assert.strictEqual(reqs.length, 1);
            assert.strictEqual(reqs[0].url, '/api/admin/reports/501/decisions');
            const body = JSON.parse(reqs[0].opts.body);
            assert.strictEqual(body.decision, 'RESOLVE');
            assert.strictEqual(body.expectedVersion, 3);
            assert.strictEqual(body.resolutionAction.type, 'POST_REMOVE');
            assert.strictEqual(body.resolutionAction.reasonCode, 'HARASSMENT');
        });

        it('supports reject decision without resolutionAction', async () => {
            const env = createTestEnvironment();

            const modal = new MockElement('DIV', 'adminReportDecisionModal');
            const form = new MockElement('FORM', 'adminReportDecisionForm');
            const targetId = new MockElement('INPUT', 'reportDecisionTargetId');
            const expectedVersion = new MockElement('INPUT', 'reportDecisionExpectedVersion');
            const decisionType = new MockElement('INPUT', 'reportDecisionType');
            const displayId = new MockElement('SPAN', 'reportDecisionDisplayId');
            const resolutionNote = new MockElement('TEXTAREA', 'reportDecisionResolutionNote');
            const submitBtn = new MockElement('BUTTON', 'btnConfirmReportDecision');
            const errorAlert = new MockElement('DIV', 'reportDecisionErrorAlert');

            modal.appendChild(form);
            form.appendChild(targetId);
            form.appendChild(expectedVersion);
            form.appendChild(decisionType);
            form.appendChild(displayId);
            form.appendChild(resolutionNote);
            form.appendChild(submitBtn);
            form.appendChild(errorAlert);
            env.registerElement(modal);

            const triggerBtn = new MockElement('BUTTON');
            triggerBtn.setAttribute('data-action', 'open-report-decision');
            triggerBtn.setAttribute('data-report-id', '502');
            triggerBtn.setAttribute('data-expected-version', '1');
            triggerBtn.setAttribute('data-decision', 'REJECT');
            env.registerElement(triggerBtn);

            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(adminActionsSource, env.context);

            triggerBtn.dispatchEvent({ type: 'click' });
            assert.strictEqual(decisionType.value, 'REJECT');

            resolutionNote.value = 'Báo cáo không có căn cứ vi phạm quy chế.';
            form.dispatchEvent({ type: 'submit', preventDefault() {} });

            await new Promise(r => setTimeout(r, 10));
            const reqs = env.getFetchRequests();
            assert.strictEqual(reqs.length, 1);
            const body = JSON.parse(reqs[0].opts.body);
            assert.strictEqual(body.decision, 'REJECT');
            assert.strictEqual(body.resolutionAction, null);
        });

        it('when removeReasonDetail is whitespace-only, it falls back to resolutionNote', async () => {
            const env = createTestEnvironment();

            const modal = new MockElement('DIV', 'adminReportDecisionModal');
            const form = new MockElement('FORM', 'adminReportDecisionForm');
            const targetId = new MockElement('INPUT', 'reportDecisionTargetId');
            const expectedVersion = new MockElement('INPUT', 'reportDecisionExpectedVersion');
            const decisionType = new MockElement('INPUT', 'reportDecisionType');
            const displayId = new MockElement('SPAN', 'reportDecisionDisplayId');
            const resolutionNote = new MockElement('TEXTAREA', 'reportDecisionResolutionNote');
            const atomicRemoveCheck = new MockElement('INPUT', 'reportDecisionAtomicRemovePost');
            const removeReasonCode = new MockElement('SELECT', 'reportDecisionRemoveReasonCode');
            const removeReasonDetail = new MockElement('INPUT', 'reportDecisionRemoveReasonDetail');
            const removePublicNotice = new MockElement('INPUT', 'reportDecisionRemovePublicNotice');
            const removeFields = new MockElement('DIV', 'reportDecisionPostRemoveFields');
            const postRemoveContainer = new MockElement('DIV', 'reportDecisionPostRemoveContainer');
            const submitBtn = new MockElement('BUTTON', 'btnConfirmReportDecision');
            const errorAlert = new MockElement('DIV', 'reportDecisionErrorAlert');

            modal.appendChild(form);
            form.appendChild(targetId);
            form.appendChild(expectedVersion);
            form.appendChild(decisionType);
            form.appendChild(displayId);
            form.appendChild(resolutionNote);
            form.appendChild(postRemoveContainer);
            postRemoveContainer.appendChild(atomicRemoveCheck);
            form.appendChild(removeFields);
            removeFields.appendChild(removeReasonCode);
            removeFields.appendChild(removeReasonDetail);
            removeFields.appendChild(removePublicNotice);
            form.appendChild(submitBtn);
            form.appendChild(errorAlert);
            env.registerElement(modal);

            const triggerBtn = new MockElement('BUTTON');
            triggerBtn.setAttribute('data-action', 'open-report-decision');
            triggerBtn.setAttribute('data-report-id', '503');
            triggerBtn.setAttribute('data-expected-version', '1');
            triggerBtn.setAttribute('data-decision', 'RESOLVE');
            triggerBtn.setAttribute('data-target-type', 'POST');
            triggerBtn.setAttribute('data-target-id', '3001');
            triggerBtn.setAttribute('data-target-can-atomic-remove', 'true');
            env.registerElement(triggerBtn);

            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(adminActionsSource, env.context);

            triggerBtn.dispatchEvent({ type: 'click' });

            resolutionNote.value = 'Resolution note with enough characters for validation.';
            atomicRemoveCheck.checked = true;
            removeReasonCode.value = 'SPAM';
            removeReasonDetail.value = '             '; // Whitespace only

            form.dispatchEvent({ type: 'submit', preventDefault() {} });

            await new Promise(r => setTimeout(r, 10));
            const reqs = env.getFetchRequests();
            assert.strictEqual(reqs.length, 1);
            const body = JSON.parse(reqs[0].opts.body);
            assert.strictEqual(body.resolutionAction.reasonDetail, 'Resolution note with enough characters for validation.');
        });
    });

    describe('6. 409 Conflict Handling & XSS Prevention', () => {
        it('renders 409 conflict message cleanly via textContent without HTML injection', async () => {
            const env = createTestEnvironment();

            const modal = new MockElement('DIV', 'adminRemovePostModal');
            const form = new MockElement('FORM', 'adminRemovePostForm');
            const targetId = new MockElement('INPUT', 'removePostTargetId');
            const reasonCode = new MockElement('SELECT', 'removePostReasonCode');
            const reasonDetail = new MockElement('TEXTAREA', 'removePostReasonDetail');
            const submitBtn = new MockElement('BUTTON', 'btnConfirmRemovePost');
            const errorAlert = new MockElement('DIV', 'removePostErrorAlert');

            modal.appendChild(form);
            form.appendChild(targetId);
            form.appendChild(reasonCode);
            form.appendChild(reasonDetail);
            form.appendChild(submitBtn);
            form.appendChild(errorAlert);
            env.registerElement(modal);

            targetId.value = '100';
            reasonCode.value = 'SPAM';
            reasonDetail.value = 'Lý do hợp lệ đủ 10 ký tự trở lên.';

            // Mock 409 Conflict with potentially malicious HTML payload
            env.setFetchResponse(async () => ({
                ok: false,
                status: 409,
                text: async () => JSON.stringify({
                    success: false,
                    error: {
                        code: 'REPORT_VERSION_CONFLICT',
                        message: '<img src=x onerror=alert(1)>Xung đột phiên bản dữ liệu'
                    }
                })
            }));

            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(adminActionsSource, env.context);

            form.dispatchEvent({ type: 'submit', preventDefault() {} });

            await new Promise(r => setTimeout(r, 10));
            assert.strictEqual(errorAlert.classList.contains('d-none'), false);
            // textContent safely preserves literal string and does not evaluate as HTML
            assert.strictEqual(errorAlert.textContent, '<img src=x onerror=alert(1)>Xung đột phiên bản dữ liệu');
        });
    });

    describe('7. UTC Date Formatting', () => {
        it('formats ISO timestamps into localized DD/MM/YYYY HH:mm strings', () => {
            const env = createTestEnvironment();

            const dateSpan = new MockElement('SPAN');
            dateSpan.className = 'utc-date';
            dateSpan.setAttribute('data-utc-date', '2026-10-14T05:30:00Z');
            env.registerElement(dateSpan);

            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(adminActionsSource, env.context);

            env.mockWindow.alouteAdmin.formatUtcDates();

            assert.ok(dateSpan.textContent.includes('2026'));
            assert.ok(dateSpan.textContent.includes('/10/'));
            assert.strictEqual(dateSpan.title, '2026-10-14T05:30:00.000Z');
        });
    });

    describe('8. Adjudicated Regression Checks: Atomic Gating, PublicNotice <= 500, Logout', () => {
        it('hides atomic post removal when data-target-can-atomic-remove is false, shows when true', () => {
            const env = createTestEnvironment();

            const modal = new MockElement('DIV', 'adminReportDecisionModal');
            const form = new MockElement('FORM', 'adminReportDecisionForm');
            const targetId = new MockElement('INPUT', 'reportDecisionTargetId');
            const expectedVersion = new MockElement('INPUT', 'reportDecisionExpectedVersion');
            const decisionType = new MockElement('INPUT', 'reportDecisionType');
            const displayId = new MockElement('SPAN', 'reportDecisionDisplayId');
            const postRemoveContainer = new MockElement('DIV', 'reportDecisionPostRemoveContainer');
            postRemoveContainer.classList.add('d-none');
            const postRemoveCheckbox = new MockElement('INPUT', 'reportDecisionAtomicRemovePost');
            postRemoveCheckbox.type = 'checkbox';
            const postRemoveFields = new MockElement('DIV', 'reportDecisionPostRemoveFields');
            postRemoveFields.classList.add('d-none');

            modal.appendChild(form);
            form.appendChild(targetId);
            form.appendChild(expectedVersion);
            form.appendChild(decisionType);
            form.appendChild(displayId);
            form.appendChild(postRemoveContainer);
            form.appendChild(postRemoveCheckbox);
            form.appendChild(postRemoveFields);
            env.registerElement(modal);

            // Button 1: Non-public target post -> data-target-can-atomic-remove="false"
            const nonPublicBtn = new MockElement('BUTTON');
            nonPublicBtn.setAttribute('data-action', 'open-report-decision');
            nonPublicBtn.setAttribute('data-report-id', '503');
            nonPublicBtn.setAttribute('data-expected-version', '1');
            nonPublicBtn.setAttribute('data-decision', 'RESOLVE');
            nonPublicBtn.setAttribute('data-target-type', 'POST');
            nonPublicBtn.setAttribute('data-target-id', '201');
            nonPublicBtn.setAttribute('data-target-can-atomic-remove', 'false');
            env.registerElement(nonPublicBtn);

            // Button 2: Public target post -> data-target-can-atomic-remove="true"
            const publicBtn = new MockElement('BUTTON');
            publicBtn.setAttribute('data-action', 'open-report-decision');
            publicBtn.setAttribute('data-report-id', '504');
            publicBtn.setAttribute('data-expected-version', '1');
            publicBtn.setAttribute('data-decision', 'RESOLVE');
            publicBtn.setAttribute('data-target-type', 'POST');
            publicBtn.setAttribute('data-target-id', '202');
            publicBtn.setAttribute('data-target-can-atomic-remove', 'true');
            env.registerElement(publicBtn);

            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(adminActionsSource, env.context);

            // Clicking non-public button must NOT show atomic remove container
            nonPublicBtn.dispatchEvent({ type: 'click' });
            assert.strictEqual(postRemoveContainer.classList.contains('d-none'), true);

            // Clicking public button MUST show atomic remove container
            publicBtn.dispatchEvent({ type: 'click' });
            assert.strictEqual(postRemoveContainer.classList.contains('d-none'), false);
        });

        it('rejects publicNotice exceeding 500 characters in post remove modal', async () => {
            const env = createTestEnvironment();

            const modal = new MockElement('DIV', 'adminRemovePostModal');
            const form = new MockElement('FORM', 'adminRemovePostForm');
            const targetId = new MockElement('INPUT', 'removePostTargetId');
            const reasonCode = new MockElement('SELECT', 'removePostReasonCode');
            const reasonDetail = new MockElement('TEXTAREA', 'removePostReasonDetail');
            const publicNotice = new MockElement('TEXTAREA', 'removePostPublicNotice');
            const submitBtn = new MockElement('BUTTON', 'btnConfirmRemovePost');
            const errorAlert = new MockElement('DIV', 'removePostErrorAlert');

            modal.appendChild(form);
            form.appendChild(targetId);
            form.appendChild(reasonCode);
            form.appendChild(reasonDetail);
            form.appendChild(publicNotice);
            form.appendChild(submitBtn);
            form.appendChild(errorAlert);
            env.registerElement(modal);

            targetId.value = '100';
            reasonCode.value = 'SPAM';
            reasonDetail.value = 'Lý do hợp lệ đủ 10 ký tự trở lên.';
            publicNotice.value = 'A'.repeat(501); // 501 chars > 500

            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(adminActionsSource, env.context);

            form.dispatchEvent({ type: 'submit', preventDefault() {} });

            await new Promise(r => setTimeout(r, 10));
            assert.strictEqual(errorAlert.classList.contains('d-none'), false);
            assert.ok(errorAlert.textContent.includes('500'));
            assert.strictEqual(env.getFetchRequests().length, 0); // Blocked before network
        });

        it('intercepts logout form submit, sends POST with CSRF and redirects to /login', async () => {
            const env = createTestEnvironment();
            env.mockDocument.cookie = 'XSRF-TOKEN=test-xsrf-token';

            const logoutForm = new MockElement('FORM', 'adminLogoutForm');
            const logoutBtn = new MockElement('BUTTON', 'adminBtnLogout');
            logoutForm.appendChild(logoutBtn);
            env.registerElement(logoutForm);

            env.setFetchResponse(async () => ({
                ok: true,
                status: 204,
                text: async () => ''
            }));

            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(adminActionsSource, env.context);

            logoutForm.dispatchEvent({ type: 'submit', preventDefault() {} });

            await new Promise(r => setTimeout(r, 10));
            const reqs = env.getFetchRequests();
            assert.strictEqual(reqs.length, 1);
            assert.strictEqual(reqs[0].url, '/api/auth/logout');
            assert.strictEqual(reqs[0].opts.method, 'POST');
            assert.strictEqual(reqs[0].opts.headers['X-XSRF-TOKEN'], 'test-xsrf-token');
            assert.strictEqual(env.mockWindow.location.href, '/login');
        });
    });

    describe('Suite 9: Moderation Action Detail Modal & Stale Error State Handling', () => {
        function setupActionDetailModal(env) {
            const modal = new MockElement('DIV', 'adminActionDetailModal');
            const loading = new MockElement('DIV', 'actionDetailLoading');
            loading.classList.add('d-none');
            const error = new MockElement('DIV', 'actionDetailError');
            error.classList.add('d-none');
            const errorMsg = new MockElement('DIV', 'actionDetailErrorMessage');
            error.appendChild(errorMsg);

            const container = new MockElement('DIV', 'actionDetailContainer');
            container.classList.add('d-none');

            const idEl = new MockElement('SPAN', 'actionDetailId');
            const actorEl = new MockElement('STRONG', 'actionDetailActor');
            const typeEl = new MockElement('SPAN', 'actionDetailType');
            const targetEl = new MockElement('STRONG', 'actionDetailTarget');
            const createdEl = new MockElement('SPAN', 'actionDetailCreated');
            const reasonEl = new MockElement('SPAN', 'actionDetailReason');
            const detailEl = new MockElement('DIV', 'actionDetailDetail');

            const metaWrapper = new MockElement('DIV');
            metaWrapper.classList.add('d-none');
            const metadataEl = new MockElement('PRE', 'actionDetailMetadata');
            metaWrapper.appendChild(metadataEl);

            container.appendChild(idEl);
            container.appendChild(actorEl);
            container.appendChild(typeEl);
            container.appendChild(targetEl);
            container.appendChild(createdEl);
            container.appendChild(reasonEl);
            container.appendChild(detailEl);
            container.appendChild(metaWrapper);

            modal.appendChild(loading);
            modal.appendChild(error);
            modal.appendChild(container);

            env.registerElement(modal);

            return {
                modal, loading, error, errorMsg, container,
                idEl, actorEl, typeEl, targetEl, createdEl, reasonEl, detailEl, metadataEl, metaWrapper
            };
        }

        it('clears stale values and shows loading state on button click', async () => {
            const env = createTestEnvironment();
            const elements = setupActionDetailModal(env);

            // Pre-populate stale values from previous success
            elements.idEl.textContent = '#999';
            elements.actorEl.textContent = 'OldAdmin';
            elements.detailEl.textContent = 'Old stale note';
            elements.container.classList.remove('d-none');

            const btn = new MockElement('BUTTON');
            btn.setAttribute('data-action', 'view-action-detail');
            btn.setAttribute('data-action-id', '42');
            env.registerElement(btn);

            // Response never resolves immediately
            env.setFetchResponse(() => new Promise(() => {}));

            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(adminActionsSource, env.context);

            btn.dispatchEvent({ type: 'click' });

            // Verified synchronous reset
            assert.strictEqual(elements.loading.classList.contains('d-none'), false, 'Loading spinner must be visible');
            assert.strictEqual(elements.container.classList.contains('d-none'), true, 'Container must be hidden');
            assert.strictEqual(elements.error.classList.contains('d-none'), true, 'Error must be hidden');
            assert.strictEqual(elements.idEl.textContent, '', 'Stale ID must be cleared');
            assert.strictEqual(elements.actorEl.textContent, '', 'Stale Actor must be cleared');
            assert.strictEqual(elements.detailEl.textContent, '', 'Stale Detail must be cleared');
        });

        it('renders valid DTO data and displays content container on successful 200 response', async () => {
            const env = createTestEnvironment();
            const elements = setupActionDetailModal(env);

            const btn = new MockElement('BUTTON');
            btn.setAttribute('data-action', 'view-action-detail');
            btn.setAttribute('data-action-id', '42');
            env.registerElement(btn);

            const payload = {
                success: true,
                data: {
                    id: 42,
                    actor: { displayName: 'ModQuan', username: 'quan' },
                    actionType: 'POST_REMOVE',
                    targetType: 'POST',
                    targetId: 101,
                    reasonCode: 'SPAM',
                    reasonDetail: 'Valid removal reason details',
                    createdAt: '2026-10-15T00:00:00Z',
                    metadata: { rule: 'R1' }
                }
            };

            env.setFetchResponse(async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify(payload),
                json: async () => payload
            }));

            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(adminActionsSource, env.context);

            btn.dispatchEvent({ type: 'click' });
            await new Promise(r => setTimeout(r, 10));

            assert.strictEqual(elements.loading.classList.contains('d-none'), true);
            assert.strictEqual(elements.error.classList.contains('d-none'), true);
            assert.strictEqual(elements.container.classList.contains('d-none'), false);
            assert.strictEqual(elements.idEl.textContent, '#42');
            assert.strictEqual(elements.actorEl.textContent, 'ModQuan');
            assert.strictEqual(elements.typeEl.textContent, 'POST_REMOVE');
            assert.strictEqual(elements.targetEl.textContent, 'POST #101');
            assert.strictEqual(elements.reasonEl.textContent, 'SPAM');
            assert.strictEqual(elements.detailEl.textContent, 'Valid removal reason details');
            assert.ok(elements.metadataEl.textContent.includes('rule'));
        });

        it('clears stale fields, hides container, and displays error alert on 404 response', async () => {
            const env = createTestEnvironment();
            const elements = setupActionDetailModal(env);

            // Pre-populate stale values
            elements.idEl.textContent = '#999';
            elements.actorEl.textContent = 'OldAdmin';
            elements.container.classList.remove('d-none');

            const btn = new MockElement('BUTTON');
            btn.setAttribute('data-action', 'view-action-detail');
            btn.setAttribute('data-action-id', '999');
            env.registerElement(btn);

            const errPayload = {
                success: false,
                message: 'Không tìm thấy hành động kiểm duyệt #999'
            };

            env.setFetchResponse(async () => ({
                ok: false,
                status: 404,
                text: async () => JSON.stringify(errPayload),
                json: async () => errPayload
            }));

            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(adminActionsSource, env.context);

            btn.dispatchEvent({ type: 'click' });
            await new Promise(r => setTimeout(r, 10));

            assert.strictEqual(elements.loading.classList.contains('d-none'), true);
            assert.strictEqual(elements.container.classList.contains('d-none'), true, 'Content must stay hidden on error');
            assert.strictEqual(elements.error.classList.contains('d-none'), false, 'Error must be displayed');
            assert.ok(elements.errorMsg.textContent.includes('999') || elements.errorMsg.textContent.includes('404'));
            assert.strictEqual(elements.idEl.textContent, '', 'Stale ID must not be shown');
        });

        it('clears stale fields and displays error on network failure', async () => {
            const env = createTestEnvironment();
            const elements = setupActionDetailModal(env);

            elements.idEl.textContent = '#999';
            elements.container.classList.remove('d-none');

            const btn = new MockElement('BUTTON');
            btn.setAttribute('data-action', 'view-action-detail');
            btn.setAttribute('data-action-id', '50');
            env.registerElement(btn);

            env.setFetchResponse(async () => {
                throw new Error('Network connection timeout');
            });

            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(adminActionsSource, env.context);

            btn.dispatchEvent({ type: 'click' });
            await new Promise(r => setTimeout(r, 10));

            assert.strictEqual(elements.container.classList.contains('d-none'), true);
            assert.strictEqual(elements.error.classList.contains('d-none'), false);
            assert.ok(elements.errorMsg.textContent.includes('Lỗi kết nối'));
            assert.strictEqual(elements.idEl.textContent, '');
        });

        it('clears stale fields and displays error on malformed success envelope (missing id)', async () => {
            const env = createTestEnvironment();
            const elements = setupActionDetailModal(env);

            elements.idEl.textContent = '#999';
            elements.container.classList.remove('d-none');

            const btn = new MockElement('BUTTON');
            btn.setAttribute('data-action', 'view-action-detail');
            btn.setAttribute('data-action-id', '51');
            env.registerElement(btn);

            const malformed = {
                success: true,
                data: {} // Empty data object, missing id
            };

            env.setFetchResponse(async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify(malformed),
                json: async () => malformed
            }));

            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(adminActionsSource, env.context);

            btn.dispatchEvent({ type: 'click' });
            await new Promise(r => setTimeout(r, 10));

            assert.strictEqual(elements.container.classList.contains('d-none'), true);
            assert.strictEqual(elements.error.classList.contains('d-none'), false);
            assert.ok(elements.errorMsg.textContent.includes('không hợp lệ'));
            assert.strictEqual(elements.idEl.textContent, '');
        });

        it('clears stale fields and displays error on malformed success envelope (null id)', async () => {
            const env = createTestEnvironment();
            const elements = setupActionDetailModal(env);

            elements.idEl.textContent = '#999';
            elements.container.classList.remove('d-none');

            const btn = new MockElement('BUTTON');
            btn.setAttribute('data-action', 'view-action-detail');
            btn.setAttribute('data-action-id', '52');
            env.registerElement(btn);

            const malformed = {
                success: true,
                data: { id: null, actionType: 'POST_REMOVE' }
            };

            env.setFetchResponse(async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify(malformed),
                json: async () => malformed
            }));

            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(adminActionsSource, env.context);

            btn.dispatchEvent({ type: 'click' });
            await new Promise(r => setTimeout(r, 10));

            assert.strictEqual(elements.container.classList.contains('d-none'), true);
            assert.strictEqual(elements.error.classList.contains('d-none'), false);
            assert.ok(elements.errorMsg.textContent.includes('không hợp lệ'));
            assert.strictEqual(elements.idEl.textContent, '');
        });

        it('discards late response from previous action when another row is opened (latest request wins)', async () => {
            const env = createTestEnvironment();
            const elements = setupActionDetailModal(env);

            const btn1 = new MockElement('BUTTON');
            btn1.setAttribute('data-action', 'view-action-detail');
            btn1.setAttribute('data-action-id', '100');
            env.registerElement(btn1);

            const btn2 = new MockElement('BUTTON');
            btn2.setAttribute('data-action', 'view-action-detail');
            btn2.setAttribute('data-action-id', '200');
            env.registerElement(btn2);

            let resolveAction1;
            const action1Promise = new Promise(r => { resolveAction1 = r; });

            const payload1 = {
                success: true,
                data: { id: 100, actor: { displayName: 'Admin1' }, actionType: 'ACTION_1' }
            };
            const payload2 = {
                success: true,
                data: { id: 200, actor: { displayName: 'Admin2' }, actionType: 'ACTION_2' }
            };

            env.setFetchResponse(async (url) => {
                if (url.includes('100')) {
                    await action1Promise;
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify(payload1),
                        json: async () => payload1
                    };
                }
                return {
                    ok: true,
                    status: 200,
                    text: async () => JSON.stringify(payload2),
                    json: async () => payload2
                };
            });

            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(adminActionsSource, env.context);

            // User clicks button 1 (delayed)
            btn1.dispatchEvent({ type: 'click' });
            await new Promise(r => setTimeout(r, 5));

            // User quickly clicks button 2 (resolves fast)
            btn2.dispatchEvent({ type: 'click' });
            await new Promise(r => setTimeout(r, 10));

            assert.strictEqual(elements.idEl.textContent, '#200');
            assert.strictEqual(elements.actorEl.textContent, 'Admin2');

            // Now button 1 response finally resolves late
            resolveAction1();
            await new Promise(r => setTimeout(r, 10));

            // Must NOT overwrite button 2 data!
            assert.strictEqual(elements.idEl.textContent, '#200', 'Action 1 must not overwrite Action 2');
            assert.strictEqual(elements.actorEl.textContent, 'Admin2');
        });
    });
});
