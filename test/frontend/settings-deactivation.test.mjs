/**
 * AloUTE — Settings Deactivation & Navigation Lifecycle Regression Tests
 *
 * Covers Phase B and Review Loop 1 requirements:
 *   - Synchronous in-flight guard on deactivation form
 *   - Terminal success lock, modal hide, and password clearing on settlement/hide
 *   - Modal dismissal while pending clears password but retains in-flight lock
 *   - Re-enable before focusing on unsuccessful settlement, including network errors
 *   - MockElement.focus() ignores disabled elements to verify focus ordering
 *   - Parameterized canonical 401/403/404/409/422 envelope handling
 *   - Current-password autocomplete & accessible busy/alert states
 *   - Replace-based redirect for deactivation and logout
 *   - Persisted pageshow cache revalidation
 *   - Template checks for role-based defense in depth (SYSTEM_ADMIN / missing accountUser)
 *
 * Run: node --test test/frontend/settings-deactivation.test.mjs
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

const settingsScriptPath = path.resolve(repoRoot, 'src/main/resources/static/js/features/settings/settings.js');
const settingsTemplatePath = path.resolve(repoRoot, 'src/main/resources/templates/settings/index.html');

const settingsScriptSource = fs.readFileSync(settingsScriptPath, 'utf-8');
const settingsTemplateSource = fs.readFileSync(settingsTemplatePath, 'utf-8');

class MockElement {
    constructor(tag = 'DIV', id = '') {
        this.tagName = tag.toUpperCase();
        this.id = id;
        this.className = '';
        this.dataset = {};
        this.attributes = {};
        this.children = [];
        this.value = '';
        this.textContent = '';
        this.innerHTML = '';
        this.disabled = false;
        this.focused = false;
        this._classes = new Set();
        this._listeners = new Map();
    }

    get classList() {
        const classes = this._classes;
        return {
            add: (...names) => names.forEach(n => classes.add(n)),
            remove: (...names) => names.forEach(n => classes.delete(n)),
            toggle: (name, force) => {
                const next = force === undefined ? !classes.has(name) : !!force;
                if (next) classes.add(name); else classes.delete(name);
                return next;
            },
            contains: (name) => classes.has(name)
        };
    }

    addEventListener(type, fn) {
        if (!this._listeners.has(type)) this._listeners.set(type, []);
        this._listeners.get(type).push(fn);
    }

    removeEventListener(type, fn) {
        if (!this._listeners.has(type)) return;
        this._listeners.set(type, this._listeners.get(type).filter(l => l !== fn));
    }

    dispatchEvent(evt) {
        evt.target = evt.target || this;
        evt.currentTarget = this;
        const list = this._listeners.get(evt.type) || [];
        for (const fn of list) fn(evt);
        return !evt.defaultPrevented;
    }

    appendChild(child) { this.children.push(child); return child; }

    focus() {
        // Real DOM behavior: element.focus() is ignored when disabled
        if (!this.disabled) {
            this.focused = true;
        }
    }

    scrollIntoView() {}
    setAttribute(name, val) { this.attributes[name] = String(val); }
    getAttribute(name) { return name in this.attributes ? this.attributes[name] : null; }
    removeAttribute(name) { delete this.attributes[name]; }
    hasAttribute(name) { return name in this.attributes; }
}

function createDeactivationEnvironment() {
    const elements = new Map();
    const getOrCreate = (id, tag = 'DIV') => {
        if (!elements.has(id)) {
            const el = new MockElement(tag, id);
            elements.set(id, el);
        }
        return elements.get(id);
    };

    const docListeners = new Map();
    const mockDocument = {
        readyState: 'complete',
        cookie: 'XSRF-TOKEN=test-csrf;',
        body: new MockElement('BODY'),
        documentElement: new MockElement('HTML'),
        createElement: (tag) => new MockElement(tag),
        createTextNode: (t) => ({ textContent: t }),
        getElementById: (id) => getOrCreate(id),
        querySelector: (sel) => {
            const m = /^#([\w-]+)$/.exec(sel || '');
            if (m) return getOrCreate(m[1]);
            return null;
        },
        querySelectorAll: (sel) => {
            if (sel.includes('data-action="logout"')) {
                return [getOrCreate('settings-btn-logout', 'BUTTON')];
            }
            return [];
        },
        addEventListener: (t, fn) => {
            if (!docListeners.has(t)) docListeners.set(t, []);
            docListeners.get(t).push(fn);
        },
        removeEventListener: (t, fn) => {
            if (!docListeners.has(t)) return;
            docListeners.set(t, docListeners.get(t).filter(l => l !== fn));
        }
    };

    let modalShown = false;
    let modalHidden = false;
    const modalInstance = {
        show() { modalShown = true; modalHidden = false; },
        hide() { modalHidden = true; modalShown = false; }
    };

    const windowListeners = new Map();
    const mockWindow = {
        document: mockDocument,
        console,
        localStorage: {
            getItem: () => null,
            setItem: () => {},
            removeItem: () => {}
        },
        sessionStorage: {
            getItem: () => null,
            setItem: () => {},
            removeItem: () => {}
        },
        location: {
            href: 'http://localhost/settings/privacy',
            pathname: '/settings/privacy',
            search: '',
            reloadCalled: false,
            replaced: null,
            reload() { this.reloadCalled = true; },
            replace(url) { this.href = url; this.replaced = url; }
        },
        history: { replaceState: () => {} },
        setTimeout: (fn, ms) => {
            fn();
            return 1;
        },
        clearTimeout: () => {},
        addEventListener: (t, fn) => {
            if (!windowListeners.has(t)) windowListeners.set(t, []);
            windowListeners.get(t).push(fn);
        },
        removeEventListener: (t, fn) => {
            if (!windowListeners.has(t)) return;
            windowListeners.set(t, windowListeners.get(t).filter(l => l !== fn));
        },
        dispatchEvent: (evt) => {
            const list = windowListeners.get(evt.type) || [];
            for (const fn of list) fn(evt);
        },
        bootstrap: {
            Modal: function () { return modalInstance; }
        },
        alouteConfirm: async () => true,
        alouteAlert: () => {},
        showAlert: () => {}
    };
    mockWindow.window = mockWindow;
    mockDocument.defaultView = mockWindow;

    const httpRequests = [];
    let responseHandler = async () => ({
        ok: true,
        status: 200,
        text: async () => JSON.stringify({ success: true, data: null })
    });

    mockWindow.alouteHttp = {
        request: async (url, opts = {}) => {
            httpRequests.push({ url, opts });
            return responseHandler(url, opts);
        },
        readJson: async (resp) => JSON.parse(await resp.text())
    };

    const context = vm.createContext(mockWindow);

    return {
        context,
        mockWindow,
        mockDocument,
        elements,
        getOrCreate,
        httpRequests,
        setResponse: (fn) => { responseHandler = fn; },
        getModalState: () => ({ modalShown, modalHidden })
    };
}

describe('Settings Deactivation & Lifecycle Hardening (Phase B & Review Loop 1)', () => {

    describe('1. Static Template Invariants', () => {
        it('requires current-password autocomplete attribute on password input', () => {
            assert.match(
                settingsTemplateSource,
                /id="deactivate-password-input"[^>]*autocomplete="current-password"/s,
                'Password input must specify autocomplete="current-password"'
            );
            assert.ok(
                settingsTemplateSource.includes('aria-describedby="deactivate-feedback"'),
                'Password input must point at the feedback region'
            );
            assert.match(
                settingsTemplateSource,
                /id="deactivate-feedback"[^>]*role="alert"[^>]*aria-live="assertive"/s,
                'Feedback must be announced as an alert'
            );
        });

        it('replaces deactivation copy with non-misleading Vietnamese notice', () => {
            assert.ok(
                settingsTemplateSource.includes('(inactive)'),
                'Must explain inactive account status'
            );
            // Must NOT contain old misleading claims
            assert.ok(
                !settingsTemplateSource.includes('bằng cách đăng nhập lại') &&
                !settingsTemplateSource.includes('b\u1EB1ng c\u00E1ch \u0111\u0103ng nh\u1EADp l\u1EA1i') &&
                !settingsTemplateSource.includes('k\u00EDch ho\u1EA1t l\u1EA1i t\u00E0i kho\u1EA3n b\u1EA5t k\u1EF3'),
                'Must not promise auto-reactivation on login'
            );
            assert.ok(
                !settingsTemplateSource.includes('ngay lập tức') &&
                !settingsTemplateSource.includes('ngay l\u1EADp t\u1EE9c') &&
                !settingsTemplateSource.includes('To\u00E0n b\u1ED9 phi\u00EAn \u0111\u0103ng nh\u1EADp'),
                'Must not promise immediate all-device transport closure'
            );
        });

        it('keeps the action available for SYSTEM_ADMIN and warns about last-admin protection', () => {
            assert.ok(
                settingsTemplateSource.includes('Nếu bạn là quản trị viên hệ thống cuối cùng'),
                'Must explain that the backend protects the last system administrator'
            );
            assert.ok(
                settingsTemplateSource.includes('th:disabled="${accountUser == null}"'),
                'Must fail closed only when account identity cannot be loaded'
            );
            assert.ok(
                !settingsTemplateSource.includes('accountUser == null or (accountUser.role'),
                'Must not disable all SYSTEM_ADMIN self-deactivation in the UI'
            );
        });
    });

    describe('2. Form Validation & In-Flight Guard', () => {
        it('validates blank password without triggering HTTP mutation', async () => {
            const env = createDeactivationEnvironment();
            vm.runInContext(settingsScriptSource, env.context);

            const form = env.getOrCreate('form-confirm-deactivate', 'FORM');
            const passInput = env.getOrCreate('deactivate-password-input', 'INPUT');
            const feedback = env.getOrCreate('deactivate-feedback', 'DIV');

            passInput.value = '';
            form.dispatchEvent({ type: 'submit', preventDefault() {} });

            assert.strictEqual(env.httpRequests.length, 0, 'No HTTP request when password is blank');
            assert.strictEqual(feedback.classList.contains('d-none'), false, 'Shows validation feedback');
            assert.strictEqual(passInput.focused, true, 'Focuses password input');
        });

        it('focuses the password input when the modal is shown', async () => {
            const env = createDeactivationEnvironment();
            vm.runInContext(settingsScriptSource, env.context);

            const modalEl = env.getOrCreate('modal-deactivate-account', 'DIV');
            const passInput = env.getOrCreate('deactivate-password-input', 'INPUT');
            modalEl.dispatchEvent({ type: 'shown.bs.modal' });

            assert.strictEqual(passInput.focused, true, 'Keyboard users land on the password field');
        });

        it('engages in-flight busy state and drops synchronous duplicate submissions', async () => {
            const env = createDeactivationEnvironment();

            let resolveRequest;
            const pendingPromise = new Promise(resolve => { resolveRequest = resolve; });
            env.setResponse(async () => {
                await pendingPromise;
                return {
                    ok: true,
                    status: 200,
                    text: async () => JSON.stringify({ success: true })
                };
            });

            vm.runInContext(settingsScriptSource, env.context);

            const form = env.getOrCreate('form-confirm-deactivate', 'FORM');
            const passInput = env.getOrCreate('deactivate-password-input', 'INPUT');
            const submitBtn = env.getOrCreate('btn-confirm-deactivate-submit', 'BUTTON');
            const spinner = env.getOrCreate('deactivate-spinner', 'SPAN');

            passInput.value = 'Secret123!';

            // Submit 1 (initiates in-flight)
            form.dispatchEvent({ type: 'submit', preventDefault() {} });

            assert.strictEqual(submitBtn.disabled, true, 'Submit button disabled during in-flight');
            assert.strictEqual(submitBtn.getAttribute('aria-busy'), 'true', 'aria-busy set to true');
            assert.strictEqual(passInput.disabled, true, 'Password input disabled during in-flight');
            assert.strictEqual(spinner.classList.contains('d-none'), false, 'Spinner visible');
            assert.strictEqual(env.httpRequests.length, 1, 'First request dispatched');

            // Submit 2 (dispatched while request 1 is pending; proves synchronous in-flight guard)
            form.dispatchEvent({ type: 'submit', preventDefault() {} });
            assert.strictEqual(env.httpRequests.length, 1, 'Duplicate submit suppressed by in-flight guard');

            // Settle request
            resolveRequest();
            await new Promise(r => setTimeout(r, 10));
        });

        it('clears password on modal dismissal even while request is pending, retaining locks', async () => {
            const env = createDeactivationEnvironment();

            let resolveRequest;
            const pendingPromise = new Promise(resolve => { resolveRequest = resolve; });
            env.setResponse(async () => {
                await pendingPromise;
                return {
                    ok: true,
                    status: 200,
                    text: async () => JSON.stringify({ success: true })
                };
            });

            vm.runInContext(settingsScriptSource, env.context);

            const form = env.getOrCreate('form-confirm-deactivate', 'FORM');
            const passInput = env.getOrCreate('deactivate-password-input', 'INPUT');
            const modalEl = env.getOrCreate('modal-deactivate-account', 'DIV');
            const btnOpen = env.getOrCreate('btn-open-deactivate-modal', 'BUTTON');
            const submitBtn = env.getOrCreate('btn-confirm-deactivate-submit', 'BUTTON');

            passInput.value = 'PendingSecret123!';
            form.dispatchEvent({ type: 'submit', preventDefault() {} });

            assert.strictEqual(env.httpRequests.length, 1, 'First request dispatched');
            assert.strictEqual(submitBtn.disabled, true, 'Submit button disabled while pending');
            assert.strictEqual(passInput.disabled, true, 'Password input disabled while pending');

            // Dismiss modal while pending
            modalEl.dispatchEvent({ type: 'hidden.bs.modal' });

            // Password must be cleared in DOM even while request is pending
            assert.strictEqual(passInput.value, '', 'DOM password value cleared on dismissal while pending');

            // Reopen and resubmit are blocked while request is pending
            btnOpen.dispatchEvent({ type: 'click' });
            form.dispatchEvent({ type: 'submit', preventDefault() {} });
            assert.strictEqual(env.httpRequests.length, 1, 'No duplicate request dispatched');

            // Settle request
            resolveRequest();
            await new Promise(r => setTimeout(r, 10));
            assert.strictEqual(btnOpen.disabled, true, 'Terminal lock preserved on success');
        });
    });

    describe('3. Error Rollback, Password Clearing & Re-enable Before Focus', () => {
        it('clears password, re-enables inputs before focus on 400 Bad Credentials', async () => {
            const env = createDeactivationEnvironment();
            const expectedMsg = 'Mật khẩu xác nhận không chính xác.';
            env.setResponse(async () => ({
                ok: false,
                status: 400,
                text: async () => JSON.stringify({
                    success: false,
                    error: { code: 'INVALID_PASSWORD', message: expectedMsg }
                })
            }));

            vm.runInContext(settingsScriptSource, env.context);

            const form = env.getOrCreate('form-confirm-deactivate', 'FORM');
            const passInput = env.getOrCreate('deactivate-password-input', 'INPUT');
            const submitBtn = env.getOrCreate('btn-confirm-deactivate-submit', 'BUTTON');
            const feedback = env.getOrCreate('deactivate-feedback', 'DIV');

            passInput.value = 'WrongPassword123';
            form.dispatchEvent({ type: 'submit', preventDefault() {} });
            await new Promise(r => setTimeout(r, 10));

            assert.strictEqual(passInput.value, '', 'Password cleared on settlement failure');
            assert.strictEqual(submitBtn.disabled, false, 'Submit button unlocked for retry');
            assert.strictEqual(submitBtn.hasAttribute('aria-busy'), false, 'aria-busy cleared');
            assert.strictEqual(passInput.disabled, false, 'Password input re-enabled for retry');
            assert.strictEqual(passInput.focused, true, 'Retry focus engaged after input was re-enabled');
            assert.strictEqual(feedback.textContent, expectedMsg);
            assert.strictEqual(feedback.classList.contains('d-none'), false);
        });

        it('clears password, re-enables inputs before focus on network failure', async () => {
            const env = createDeactivationEnvironment();
            env.setResponse(async () => {
                throw new Error('Network offline');
            });

            vm.runInContext(settingsScriptSource, env.context);

            const form = env.getOrCreate('form-confirm-deactivate', 'FORM');
            const passInput = env.getOrCreate('deactivate-password-input', 'INPUT');
            const submitBtn = env.getOrCreate('btn-confirm-deactivate-submit', 'BUTTON');
            const feedback = env.getOrCreate('deactivate-feedback', 'DIV');

            passInput.value = 'MyPassword';
            form.dispatchEvent({ type: 'submit', preventDefault() {} });
            await new Promise(r => setTimeout(r, 10));

            assert.strictEqual(passInput.value, '', 'Password wiped on network error');
            assert.strictEqual(submitBtn.disabled, false, 'Submit button re-enabled');
            assert.strictEqual(passInput.disabled, false, 'Password input re-enabled');
            assert.strictEqual(passInput.focused, true, 'Retry focus engaged after network error re-enable');
            assert.strictEqual(feedback.textContent, 'Lỗi kết nối máy chủ. Vui lòng thử lại sau.');
            assert.strictEqual(feedback.classList.contains('d-none'), false);
        });

        // Canonical transport envelopes: every status gets stable Vietnamese copy;
        // unknown backend codes never leak raw English messages into the modal.
        const transportCases = [
            { status: 401, expected: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.', redirect: '/login?expired=true' },
            { status: 403, expected: 'Bạn không có quyền vô hiệu hóa tài khoản này.', redirect: null },
            { status: 404, expected: 'Không tìm thấy tài khoản cần vô hiệu hóa.', redirect: null },
            { status: 409, expected: 'Không thể vô hiệu hóa tài khoản do xung đột trạng thái. Vui lòng tải lại trang và thử lại.', redirect: null },
            { status: 422, expected: 'Dữ liệu xác nhận không hợp lệ. Vui lòng kiểm tra lại mật khẩu.', redirect: null },
            { status: 500, expected: 'Hệ thống đang gặp sự cố. Vui lòng thử lại sau.', redirect: null }
        ];
        for (const { status: statusCode, expected, redirect } of transportCases) {
            it(`parameterized transport: handles canonical ${statusCode} envelope with safe message, password clear, spinner unlock, and retry focus`, async () => {
                const env = createDeactivationEnvironment();
                env.setResponse(async () => ({
                    ok: false,
                    status: statusCode,
                    text: async () => JSON.stringify({
                        success: false,
                        error: { code: 'ERR_' + statusCode, message: 'Safe message for ' + statusCode }
                    })
                }));

                vm.runInContext(settingsScriptSource, env.context);

                const form = env.getOrCreate('form-confirm-deactivate', 'FORM');
                const passInput = env.getOrCreate('deactivate-password-input', 'INPUT');
                const submitBtn = env.getOrCreate('btn-confirm-deactivate-submit', 'BUTTON');
                const spinner = env.getOrCreate('deactivate-spinner', 'SPAN');
                const feedback = env.getOrCreate('deactivate-feedback', 'DIV');
                const alertMessage = env.getOrCreate('settings-alert-message', 'SPAN');

                passInput.value = 'SecretToClear';
                form.dispatchEvent({ type: 'submit', preventDefault() {} });
                await new Promise(r => setTimeout(r, 10));

                assert.strictEqual(feedback.textContent, expected, 'Displays mapped safe error message via textContent');
                assert.strictEqual(feedback.classList.contains('d-none'), false, 'Feedback visible');
                assert.strictEqual(passInput.value, '', 'Password cleared');
                assert.strictEqual(passInput.disabled, false, 'Password input re-enabled');
                assert.strictEqual(passInput.focused, true, 'Retry focus engaged');
                assert.strictEqual(submitBtn.disabled, false, 'Submit button re-enabled');
                assert.strictEqual(submitBtn.hasAttribute('aria-busy'), false, 'aria-busy removed');
                assert.strictEqual(spinner.classList.contains('d-none'), true, 'Spinner hidden');
                assert.strictEqual(env.mockWindow.location.replaced, redirect, 'Only a 401 leaves for the login page');
                assert.strictEqual(alertMessage.textContent, '', 'No success message on failure');
            });
        }

        it('maps the last-admin 409 to a specific actionable message without success feedback', async () => {
            const env = createDeactivationEnvironment();
            env.setResponse(async () => ({
                ok: false,
                status: 409,
                text: async () => JSON.stringify({
                    success: false,
                    error: {
                        code: 'USER_LAST_ADMIN_PROTECTED',
                        message: 'Cannot suspend or demote the last remaining system administrator'
                    }
                })
            }));

            vm.runInContext(settingsScriptSource, env.context);

            const form = env.getOrCreate('form-confirm-deactivate', 'FORM');
            const passInput = env.getOrCreate('deactivate-password-input', 'INPUT');
            const feedback = env.getOrCreate('deactivate-feedback', 'DIV');
            const alertMessage = env.getOrCreate('settings-alert-message', 'SPAN');

            passInput.value = 'AdminPassword';
            form.dispatchEvent({ type: 'submit', preventDefault() {} });
            await new Promise(r => setTimeout(r, 10));

            assert.strictEqual(
                feedback.textContent,
                'Không thể vô hiệu hóa tài khoản quản trị viên hệ thống cuối cùng. Vui lòng chuyển quyền quản trị cho tài khoản khác trước.'
            );
            assert.strictEqual(feedback.classList.contains('d-none'), false);
            assert.strictEqual(env.mockWindow.location.replaced, null, 'Last-admin protection must not navigate');
            assert.strictEqual(alertMessage.textContent, '', 'No success message');
        });

        it('adds a deactivated banner on the login page after a successful deactivation', () => {
            const loginTemplate = fs.readFileSync(
                path.resolve(repoRoot, 'src/main/resources/templates/auth/login.html'),
                'utf-8'
            );
            const loginScript = fs.readFileSync(
                path.resolve(repoRoot, 'src/main/resources/static/js/auth.js'),
                'utf-8'
            );
            assert.ok(loginTemplate.includes('param.deactivated != null'), 'Login must acknowledge a deactivated account');
            assert.ok(loginTemplate.includes('param.expired != null'), 'Login must acknowledge an expired session');
            assert.ok(loginTemplate.includes('Liên hệ ban quản trị nếu bạn muốn mở lại tài khoản'), 'Deactivation banner explains recovery');
            assert.ok(loginScript.includes("AUTH_ACCOUNT_DEACTIVATED"), 'Login error mapping must recognise a deactivated account');
        });
    });

    describe('4. Success Terminal Lock & History Navigation', () => {
        it('clears password, hides modal, locks terminal state, and calls location.replace', async () => {
            const env = createDeactivationEnvironment();
            env.setResponse(async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({ success: true, data: null })
            }));

            vm.runInContext(settingsScriptSource, env.context);

            const form = env.getOrCreate('form-confirm-deactivate', 'FORM');
            const passInput = env.getOrCreate('deactivate-password-input', 'INPUT');
            const openBtn = env.getOrCreate('btn-open-deactivate-modal', 'BUTTON');

            passInput.value = 'CorrectSecret123';
            form.dispatchEvent({ type: 'submit', preventDefault() {} });
            await new Promise(r => setTimeout(r, 10));

            assert.strictEqual(passInput.value, '', 'Password cleared on terminal success');
            assert.strictEqual(env.getModalState().modalHidden, true, 'Modal hidden on success');
            assert.strictEqual(openBtn.disabled, true, 'Open button permanently locked');
            assert.strictEqual(
                env.mockWindow.location.replaced,
                '/login?deactivated=true',
                'Uses window.location.replace to prevent back-button re-entry'
            );

            // Attempt to submit again after success is rejected
            form.dispatchEvent({ type: 'submit', preventDefault() {} });
            assert.strictEqual(env.httpRequests.length, 1, 'No second request permitted after terminal success');
        });

        it('modal hidden.bs.modal event clears password when dismissed', async () => {
            const env = createDeactivationEnvironment();
            vm.runInContext(settingsScriptSource, env.context);

            const modalEl = env.getOrCreate('modal-deactivate-account', 'DIV');
            const passInput = env.getOrCreate('deactivate-password-input', 'INPUT');
            const feedback = env.getOrCreate('deactivate-feedback', 'DIV');

            passInput.value = 'UnsubmittedPassword';
            feedback.classList.remove('d-none');

            modalEl.dispatchEvent({ type: 'hidden.bs.modal' });

            assert.strictEqual(passInput.value, '', 'Password cleared on modal dismissal');
            assert.strictEqual(feedback.classList.contains('d-none'), true, 'Feedback hidden on modal dismissal');
        });
    });

    describe('5. Navigation Lifecycle & Session Revocation Proof', () => {
        it('reloads window when persisted pageshow is dispatched', () => {
            const env = createDeactivationEnvironment();
            vm.runInContext(settingsScriptSource, env.context);

            assert.strictEqual(env.mockWindow.location.reloadCalled, false);
            env.mockWindow.dispatchEvent({ type: 'pageshow', persisted: true });
            assert.strictEqual(env.mockWindow.location.reloadCalled, true);
        });

        it('uses location.replace on Settings logout', async () => {
            const env = createDeactivationEnvironment();
            vm.runInContext(settingsScriptSource, env.context);

            const logoutBtn = env.getOrCreate('settings-btn-logout', 'BUTTON');
            logoutBtn.dispatchEvent({ type: 'click', preventDefault() {} });
            await new Promise(r => setTimeout(r, 10));

            assert.strictEqual(
                env.mockWindow.location.replaced,
                '/login',
                'Uses window.location.replace for logout'
            );
        });
    });
});
