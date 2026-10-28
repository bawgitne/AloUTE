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
