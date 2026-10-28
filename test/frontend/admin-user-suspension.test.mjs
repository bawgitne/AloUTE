/**
 * AloUTE — System-Admin Account Suspension UI Regression Tests
 *
 * Verifies the suspend/reactivate controls wired into the admin dashboard:
 * 1. admin/users/detail.html exposes real (non-disabled) suspend/reactivate
 *    actions instead of the old placeholder button.
 * 2. fragments/admin/modals.html carries the moderation modal (reasonCode +
 *    reasonDetail).
 * 3. admin-actions.js POSTs to /api/admin/users/{id}/suspend or /reactivate
 *    with the reason payload, and blocks a too-short reason client-side.
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
const read = (rel) => fs.readFileSync(path.resolve(repoRoot, rel), 'utf-8');

const detailTemplate = read('src/main/resources/templates/admin/users/detail.html');
const modalsTemplate = read('src/main/resources/templates/fragments/admin/modals.html');
const adminActionsSource = read('src/main/resources/static/js/features/admin/admin-actions.js');
const adminCss = read('src/main/resources/static/css/admin/admin.css');

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ---------------------------------------------------------------- DOM harness
class MockElement {
    constructor(tagName = 'DIV', id = '') {
        this.tagName = tagName.toUpperCase();
        this.id = id;
        this.className = '';
        this._attributes = new Map();
        this.dataset = {};
        this.children = [];
        this.parentElement = null;
        this._listeners = new Map();
        this._textContent = '';
        this._innerHTML = '';
        this.value = '';
        this.disabled = false;
    }
    get classList() {
        const self = this;
        return {
            add: (...n) => { const s = new Set(self.className.split(/\s+/).filter(Boolean)); n.forEach((x) => s.add(x)); self.className = Array.from(s).join(' '); },
            remove: (...n) => { const s = new Set(self.className.split(/\s+/).filter(Boolean)); n.forEach((x) => s.delete(x)); self.className = Array.from(s).join(' '); },
            contains: (n) => self.className.split(/\s+/).filter(Boolean).includes(n),
            toggle: (n, on) => { if (on === undefined) on = !self.classList.contains(n); on ? self.classList.add(n) : self.classList.remove(n); }
        };
    }
    get textContent() { return this._textContent; }
    set textContent(v) { this._textContent = String(v == null ? '' : v); }
    get innerHTML() { return this._innerHTML; }
    set innerHTML(v) { this._innerHTML = String(v || ''); }
    setAttribute(n, v) { this._attributes.set(n, String(v)); }
    getAttribute(n) { return this._attributes.has(n) ? this._attributes.get(n) : null; }
    hasAttribute(n) { return this._attributes.has(n); }
    appendChild(c) { c.parentElement = this; this.children.push(c); return c; }
    addEventListener(e, fn) { if (!this._listeners.has(e)) this._listeners.set(e, []); this._listeners.get(e).push(fn); }
    dispatchEvent(e) { e.target = e.target || this; for (const fn of this._listeners.get(e.type) || []) fn.call(this, e); return !e.defaultPrevented; }
    closest(sel) { let n = this; while (n) { if (sel.startsWith('[') && n.hasAttribute(sel.slice(1, -1))) return n; n = n.parentElement; } return null; }
    reset() { this.value = ''; }
}

function createEnv() {
    const byId = new Map();
    const all = [];
    const document = {
        createElement: (tag) => { const el = new MockElement(tag); all.push(el); return el; },
        getElementById: (id) => byId.get(id) || null,
        querySelectorAll: (sel) => {
            if (sel.startsWith('[') && sel.endsWith(']')) {
                const inner = sel.slice(1, -1);
                if (inner.includes('=')) {
                    const [k, raw] = inner.split('=');
                    const v = raw.replace(/["']/g, '');
                    return all.filter((e) => e.getAttribute(k) === v);
                }
                return all.filter((e) => e.hasAttribute(inner));
            }
            if (sel.startsWith('.')) return all.filter((e) => e.classList.contains(sel.slice(1)));
            return [];
        },
        querySelector: () => null,
        addEventListener: () => {},
        body: new MockElement('BODY'),
        cookie: 'XSRF-TOKEN=t;',
        readyState: 'complete'
    };
    const requests = [];
    const window = {
        document,
        console,
        setTimeout: (fn, ms) => setTimeout(fn, ms),
        Date,
        location: { href: 'http://localhost/admin', reload() {} },
        bootstrap: { Toast: class { show() {} addEventListener() {} }, Modal: { getOrCreateInstance: () => ({ show() {} }) } },
        alouteHttp: {
            request: async (url, opts) => { requests.push({ url, opts }); return { ok: true, status: 200 }; },
            readJson: async () => ({ success: true, data: { active: false } })
        }
    };
    window.window = window;
    return { document, window, requests, register: (el) => { all.push(el); if (el.id) byId.set(el.id, el); return el; }, byId };
}

function mk(env, tag, id, value) {
    const el = new MockElement(tag, id);
    if (value !== undefined) el.value = value;
    env.document.body.appendChild(el);
    env.byId.set(id, el);
    return el;
}

function buildModerationDom(env) {
    mk(env, 'div', 'adminUserModerationModal');
    const form = mk(env, 'form', 'adminUserModerationForm');
    mk(env, 'button', 'btnConfirmUserModeration');
    mk(env, 'div', 'userModerationErrorAlert');
    mk(env, 'span', 'userModerationTargetName');
    mk(env, 'span', 'userModerationTargetId');
    mk(env, 'span', 'userModerationModalTitle');
    mk(env, 'p', 'userModerationHint');
    mk(env, 'span', 'btnConfirmUserModerationLabel');
    mk(env, 'select', 'userModerationReasonCode');
    mk(env, 'textarea', 'userModerationReasonDetail');
    mk(env, 'div', 'userModerationReasonCounter');
    return form;
}

function addTrigger(env, action, userId, name) {
    const btn = new MockElement('button');
    btn.setAttribute('data-action', action);
    btn.setAttribute('data-user-id', String(userId));
    btn.setAttribute('data-user-name', name || 'User');
    env.document.body.appendChild(btn);
    env.register(btn);
    return btn;
}

describe('Admin suspension — templates', () => {
    it('user detail page exposes real suspend/reactivate actions', () => {
        assert.ok(detailTemplate.includes('data-action="open-user-suspend"'), 'suspend action present');
        assert.ok(detailTemplate.includes('data-action="open-user-reactivate"'), 'reactivate action present');
        assert.ok(!/Đình chỉ tài khoản<\/button>\s*<button[^>]*disabled/.test(detailTemplate));
        assert.ok(!detailTemplate.includes('đình chỉ chưa khả dụng'), 'placeholder suspend button removed');
    });

    it('moderation modal carries both reason fields', () => {
        assert.ok(modalsTemplate.includes('id="adminUserModerationModal"'));
        assert.ok(modalsTemplate.includes('id="userModerationReasonCode"'));
        assert.ok(modalsTemplate.includes('id="userModerationReasonDetail"'));
    });
});

describe('Admin suspension — action controller', () => {
    it('suspends a user via POST /api/admin/users/{id}/suspend with the reason payload', async () => {
        const env = createEnv();
        const form = buildModerationDom(env);
        addTrigger(env, 'open-user-suspend', 42, 'Target User');
        vm.runInContext(adminActionsSource, vm.createContext(env.window));

        env.document.querySelectorAll('[data-action="open-user-suspend"]')[0].dispatchEvent({ type: 'click' });
        env.byId.get('userModerationReasonCode').value = 'HARASSMENT';
        env.byId.get('userModerationReasonDetail').value = 'Quấy rối nhiều thành viên trong cộng đồng.';
        form.dispatchEvent({ type: 'submit', preventDefault() {} });
        await sleep(20);

        assert.equal(env.requests.length, 1, 'exactly one moderation request');
        assert.equal(env.requests[0].url, '/api/admin/users/42/suspend');
        assert.equal(env.requests[0].opts.method, 'POST');
        assert.equal(env.requests[0].opts.body.reasonCode, 'HARASSMENT');
        assert.ok(env.requests[0].opts.body.reasonDetail.length >= 10);
    });

    it('reactivates a user via POST /api/admin/users/{id}/reactivate', async () => {
        const env = createEnv();
        const form = buildModerationDom(env);
        addTrigger(env, 'open-user-reactivate', 7, 'Suspended User');
        vm.runInContext(adminActionsSource, vm.createContext(env.window));

        env.document.querySelectorAll('[data-action="open-user-reactivate"]')[0].dispatchEvent({ type: 'click' });
        env.byId.get('userModerationReasonCode').value = 'OTHER';
        env.byId.get('userModerationReasonDetail').value = 'Đã xem xét lại khiếu nại và khôi phục tài khoản.';
        form.dispatchEvent({ type: 'submit', preventDefault() {} });
        await sleep(20);

        assert.equal(env.requests.length, 1);
        assert.equal(env.requests[0].url, '/api/admin/users/7/reactivate');
    });

    it('blocks submission when the reason detail is too short', async () => {
        const env = createEnv();
        const form = buildModerationDom(env);
        addTrigger(env, 'open-user-suspend', 9, 'User');
        vm.runInContext(adminActionsSource, vm.createContext(env.window));

        env.document.querySelectorAll('[data-action="open-user-suspend"]')[0].dispatchEvent({ type: 'click' });
        env.byId.get('userModerationReasonCode').value = 'SPAM';
        env.byId.get('userModerationReasonDetail').value = 'ngan';
        form.dispatchEvent({ type: 'submit', preventDefault() {} });
        await sleep(10);

        assert.equal(env.requests.length, 0, 'no request sent for invalid reason');
        assert.ok(!env.byId.get('userModerationErrorAlert').classList.contains('d-none'));
    });
});

// ---------------------------------------------------------------- account actions
const notificationSource = read('src/main/resources/static/js/features/notification/notification.js');

function addAccountTrigger(env, action, userId, name, extra = {}) {
    const btn = new MockElement('button');
    btn.setAttribute('data-action', action);
    btn.setAttribute('data-user-id', String(userId));
    btn.setAttribute('data-user-name', name || 'User');
    Object.entries(extra).forEach(([key, value]) => btn.setAttribute('data-' + key, String(value)));
    env.document.body.appendChild(btn);
    env.register(btn);
    return btn;
}

function buildWarnDom(env) {
    mk(env, 'div', 'adminUserWarnModal');
    const form = mk(env, 'form', 'adminUserWarnForm');
    mk(env, 'button', 'btnConfirmUserWarn');
    mk(env, 'div', 'userWarnErrorAlert');
    mk(env, 'span', 'userWarnTargetName');
    mk(env, 'span', 'userWarnTargetId');
    mk(env, 'select', 'userWarnReasonCode');
    mk(env, 'textarea', 'userWarnReasonDetail');
    mk(env, 'div', 'userWarnReasonCounter');
    return form;
}

function buildResetDom(env) {
    mk(env, 'div', 'adminUserResetProfileModal');
    const form = mk(env, 'form', 'adminUserResetProfileForm');
    mk(env, 'button', 'btnConfirmUserReset');
    mk(env, 'div', 'userResetErrorAlert');
    mk(env, 'span', 'userResetTargetName');
    mk(env, 'span', 'userResetTargetId');
    mk(env, 'span', 'userResetExpectedUsername');
    mk(env, 'div', 'userResetConfirmHint');
    mk(env, 'input', 'userResetConfirmUsername');
    mk(env, 'select', 'userResetReasonCode');
    mk(env, 'textarea', 'userResetReasonDetail');
    mk(env, 'div', 'userResetReasonCounter');
    return form;
}

function buildRoleDom(env) {
    mk(env, 'div', 'adminUserRoleModal');
    const form = mk(env, 'form', 'adminUserRoleForm');
    mk(env, 'button', 'btnConfirmUserRole');
    mk(env, 'div', 'userRoleErrorAlert');
    mk(env, 'span', 'userRoleTargetName');
    mk(env, 'span', 'userRoleTargetId');
    mk(env, 'span', 'userRoleCurrentRole');
    mk(env, 'select', 'userRoleNewRole');
    mk(env, 'select', 'userRoleReasonCode');
    mk(env, 'textarea', 'userRoleReasonDetail');
    mk(env, 'div', 'userRoleReasonCounter');
    return form;
}

describe('Admin account actions — templates', () => {
    it('detail page has an accessible back-to-dashboard link and real warn/reset/role actions', () => {
        assert.ok(detailTemplate.includes('id="btn-back-admin-dashboard"'), 'back link present');
        assert.ok(detailTemplate.includes('href="/admin"'), 'back link targets dashboard');
        assert.ok(detailTemplate.includes('aria-label="Về Admin Dashboard"'), 'back link is labelled');
        assert.ok(detailTemplate.includes('data-action="open-user-warn"'), 'warn action present');
        assert.ok(detailTemplate.includes('data-action="open-user-reset"'), 'reset action present');
        assert.ok(detailTemplate.includes('data-action="open-user-role"'), 'role action present');
        assert.ok(detailTemplate.includes('data-user-username='), 'reset action carries username');
        assert.ok(!detailTemplate.includes('Tính năng nâng cao (P2/P3) chưa khả dụng'), 'old non-MVP label removed');
        assert.ok(!detailTemplate.includes('Chức năng cảnh báo chưa khả dụng'), 'placeholder warn button removed');
    });

    it('modals expose warning, confirmed reset and role fields', () => {
        assert.ok(modalsTemplate.includes('id="adminUserWarnModal"'));
        assert.ok(modalsTemplate.includes('id="userWarnReasonCode"'));
        assert.ok(modalsTemplate.includes('id="adminUserResetProfileModal"'));
        assert.ok(modalsTemplate.includes('id="userResetConfirmUsername"'));
        assert.ok(modalsTemplate.includes('Thao tác này làm mất dữ liệu hiển thị'), 'reset data-loss warning');
        assert.ok(modalsTemplate.includes('id="adminUserRoleModal"'));
        assert.ok(modalsTemplate.includes('value="USER"'));
        assert.ok(modalsTemplate.includes('value="SYSTEM_ADMIN"'));
    });

    it('notification UI maps the canonical USER_WARN type', () => {
        assert.ok(notificationSource.includes("t.includes('USER_WARN')"), 'USER_WARN branch present');
        assert.ok(notificationSource.includes('cảnh báo chính thức'), 'warning phrasing present');
    });
});

describe('Admin account actions — controller', () => {
    it('warns a user via POST /api/admin/users/{id}/warn', async () => {
        const env = createEnv();
        const form = buildWarnDom(env);
        addAccountTrigger(env, 'open-user-warn', 42, 'Target User');
        vm.runInContext(adminActionsSource, vm.createContext(env.window));

        env.document.querySelectorAll('[data-action="open-user-warn"]')[0].dispatchEvent({ type: 'click' });
        env.byId.get('userWarnReasonCode').value = 'HARASSMENT';
        env.byId.get('userWarnReasonDetail').value = 'Quấy rối nhiều thành viên trong cộng đồng.';
        form.dispatchEvent({ type: 'submit', preventDefault() {} });
        await sleep(20);

        assert.equal(env.requests.length, 1);
        assert.equal(env.requests[0].url, '/api/admin/users/42/warn');
        assert.equal(env.requests[0].opts.method, 'POST');
        assert.equal(env.requests[0].opts.body.reasonCode, 'HARASSMENT');
    });

    it('requires the exact username before resetting a profile', async () => {
        const env = createEnv();
        const form = buildResetDom(env);
        addAccountTrigger(env, 'open-user-reset', 7, 'Target', { 'user-username': 'target_user' });
        vm.runInContext(adminActionsSource, vm.createContext(env.window));

        env.document.querySelectorAll('[data-action="open-user-reset"]')[0].dispatchEvent({ type: 'click' });
        env.byId.get('userResetConfirmUsername').value = 'wrong_user';
        env.byId.get('userResetConfirmUsername').dispatchEvent({ type: 'input' });
        env.byId.get('userResetReasonCode').value = 'OTHER';
        env.byId.get('userResetReasonDetail').value = 'Đặt lại hồ sơ theo yêu cầu kiểm duyệt.';
        form.dispatchEvent({ type: 'submit', preventDefault() {} });
        await sleep(10);
        assert.equal(env.requests.length, 0, 'mismatched username must not call the API');
        assert.ok(!env.byId.get('userResetErrorAlert').classList.contains('d-none'));

        env.byId.get('userResetConfirmUsername').value = 'target_user';
        env.byId.get('userResetConfirmUsername').dispatchEvent({ type: 'input' });
        form.dispatchEvent({ type: 'submit', preventDefault() {} });
        await sleep(20);
        assert.equal(env.requests.length, 1);
        assert.equal(env.requests[0].url, '/api/admin/users/7/reset-profile');
        assert.equal(env.requests[0].opts.body.reasonCode, 'OTHER');
        assert.equal(env.requests[0].opts.body.confirmUsername, 'target_user', 'canonical reset payload includes username confirmation');
    });

    it('changes only USER/SYSTEM_ADMIN and surfaces 409 role conflicts', async () => {
        const env = createEnv();
        const form = buildRoleDom(env);
        addAccountTrigger(env, 'open-user-role', 9, 'Target', { 'current-role': 'USER' });
        vm.runInContext(adminActionsSource, vm.createContext(env.window));

        env.document.querySelectorAll('[data-action="open-user-role"]')[0].dispatchEvent({ type: 'click' });
        const roleSelect = env.byId.get('userRoleNewRole');
        roleSelect.value = 'SUPERUSER';
        env.byId.get('userRoleReasonCode').value = 'OTHER';
        env.byId.get('userRoleReasonDetail').value = 'Thay đổi vai trò theo quyết định quản trị.';
        form.dispatchEvent({ type: 'submit', preventDefault() {} });
        await sleep(10);
        assert.equal(env.requests.length, 0, 'unknown role is blocked client-side');

        env.window.alouteHttp.request = async (url, opts) => {
            env.requests.push({ url, opts });
            return { ok: false, status: 409 };
        };
        env.window.alouteHttp.readJson = async () => ({ success: false, error: { code: 'USER_ROLE_UNCHANGED' } });
        roleSelect.value = 'USER';
        form.dispatchEvent({ type: 'submit', preventDefault() {} });
        await sleep(20);
        assert.equal(env.requests.length, 1);
        assert.equal(env.requests[0].url, '/api/admin/users/9/role');
        assert.equal(env.requests[0].opts.body.newRole, 'USER');
        assert.ok(env.byId.get('userRoleErrorAlert').textContent.includes('đã có vai trò'));
    });

    it('keeps warn/reset/role submits locked while the success refresh is pending', async () => {
        async function submitTwice(buildDom, triggerAction, prepare) {
            const env = createEnv();
            const form = buildDom(env);
            addAccountTrigger(env, triggerAction.action, 42, 'Target User', triggerAction.extra || {});
            vm.runInContext(adminActionsSource, vm.createContext(env.window));
            env.document.querySelectorAll(`[data-action="${triggerAction.action}"]`)[0].dispatchEvent({ type: 'click' });
            prepare(env);
            form.dispatchEvent({ type: 'submit', preventDefault() {} });
            await sleep(20);
            assert.equal(env.requests.length, 1, `${triggerAction.action} sends one request`);
            const submitBtn = env.byId.get(triggerAction.button);
            assert.equal(submitBtn.disabled, true, `${triggerAction.action} button stays disabled after success`);
            form.dispatchEvent({ type: 'submit', preventDefault() {} });
            await sleep(20);
            assert.equal(env.requests.length, 1, `${triggerAction.action} cannot double-submit`);
        }

        await submitTwice(buildWarnDom, { action: 'open-user-warn', button: 'btnConfirmUserWarn' }, (env) => {
            env.byId.get('userWarnReasonCode').value = 'HARASSMENT';
            env.byId.get('userWarnReasonDetail').value = 'Quấy rối nhiều thành viên trong cộng đồng.';
        });
        await submitTwice(buildResetDom, { action: 'open-user-reset', button: 'btnConfirmUserReset', extra: { 'user-username': 'target_user' } }, (env) => {
            env.byId.get('userResetConfirmUsername').value = 'target_user';
            env.byId.get('userResetConfirmUsername').dispatchEvent({ type: 'input' });
            env.byId.get('userResetReasonCode').value = 'OTHER';
            env.byId.get('userResetReasonDetail').value = 'Đặt lại hồ sơ theo yêu cầu kiểm duyệt.';
        });
        await submitTwice(buildRoleDom, { action: 'open-user-role', button: 'btnConfirmUserRole', extra: { 'current-role': 'USER' } }, (env) => {
            env.byId.get('userRoleNewRole').value = 'SYSTEM_ADMIN';
            env.byId.get('userRoleReasonCode').value = 'OTHER';
            env.byId.get('userRoleReasonDetail').value = 'Bổ nhiệm theo quyết định quản trị.';
        });
    });

    it('keeps 44px admin action targets from being overridden by the base 38px rule', () => {
        const baseRule = adminCss.indexOf('.admin-user-actions > .btn {');
        const mobileRule = adminCss.indexOf('min-height: 44px;');
        const baseMinHeight = adminCss.indexOf('min-height: 38px;');
        assert.ok(baseRule >= 0 && baseMinHeight >= 0 && mobileRule >= 0, 'admin action sizing rules exist');
        assert.ok(baseRule < mobileRule, 'base rule must precede the mobile override');
        assert.ok(baseMinHeight < mobileRule, 'mobile 44px override must come after the base 38px rule');
    });
});
