/**
 * AloUTE — Community Moderation & Member Management Frontend Tests
 *
 * Built-in Node test suite (node:test + assert/strict + vm).
 * Verifies community-manage.js together with core/http-client.js:
 * - CSRF header + credentials on moderation mutations
 * - Approve flow (confirm → POST → redirect with ?done=approved)
 * - Reject modal flow (reason → POST body → ?done=rejected; 409 conflict surfaces friendly message)
 * - Remove published post modal flow (?done=removed)
 * - Remove member flow (?done=member-removed)
 * - Role change flow (PATCH body role, cancel restores previous selection)
 * - Approval settings toggle (PATCH requirePostApproval, failure reverts switch)
 * - Double-submit locks (one in-flight request per target)
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
const communityManageSource = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/static/js/features/community/community-manage.js'),
    'utf-8'
);

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
            add(...names) { names.forEach(n => self._classList.add(n)); self.className = Array.from(self._classList).join(' '); },
            remove(...names) { names.forEach(n => self._classList.delete(n)); self.className = Array.from(self._classList).join(' '); },
            contains(name) { return self._classList.has(name); },
            toggle(name, force) {
                if (force === undefined) { self.classList.contains(name) ? self.classList.remove(name) : self.classList.add(name); }
                else if (force) self.classList.add(name); else self.classList.remove(name);
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

    getAttribute(name) { return this._attributes.get(name) ?? null; }
    hasAttribute(name) { return this._attributes.has(name); }
    removeAttribute(name) { this._attributes.delete(name); }

    get textContent() { return this._textContent; }
    set textContent(val) { this._textContent = String(val); }
    get innerHTML() { return this._textContent; }
    set innerHTML(val) { this._textContent = String(val); }

    appendChild(child) {
        child.parentElement = this;
        this.children.push(child);
        return child;
    }

    closest(selector) {
        const attr = selector.startsWith('[') ? selector.slice(1, -1) : null;
        let node = this;
        while (node) {
            if (attr) {
                if (node.hasAttribute && node.hasAttribute(attr)) return node;
            }
            node = node.parentElement;
        }
        return null;
    }

    addEventListener(event, fn) {
        if (!this._listeners.has(event)) this._listeners.set(event, []);
        this._listeners.get(event).push(fn);
    }

    dispatchEvent(event) {
        const fns = this._listeners.get(event.type) || [];
        for (const fn of fns) fn.call(this, event);
    }

    querySelector(selector) {
        const cls = selector.startsWith('.') ? selector.slice(1) : null;
        const search = (el) => {
            for (const child of el.children) {
                if (cls && child.classList.contains(cls)) return child;
                const found = search(child);
                if (found) return found;
            }
            return null;
        };
        return search(this);
    }
}

function createTestEnvironment() {
    const documentBody = new MockElement('BODY');
    const elementsById = new Map();
    const allElements = [];
    const documentListeners = new Map();

    function register(el) {
        if (el.id) elementsById.set(el.id, el);
        allElements.push(el);
        for (const child of el.children) register(child);
    }

    const mockDocument = {
        body: documentBody,
        cookie: 'XSRF-TOKEN=test-csrf-token-123',
        readyState: 'complete',
        createElement(tag) { const el = new MockElement(tag); allElements.push(el); return el; },
        getElementById(id) { return elementsById.get(id) || null; },
        querySelectorAll(selector) {
            if (selector.startsWith('#')) {
                const el = elementsById.get(selector.slice(1));
                return el ? [el] : [];
            }
            if (selector.startsWith('.')) {
                const cls = selector.slice(1);
                return allElements.filter(e => e.classList.contains(cls));
            }
            return [];
        },
        querySelector(selector) {
            const r = this.querySelectorAll(selector);
            return r.length ? r[0] : null;
        },
        addEventListener(event, fn) {
            if (!documentListeners.has(event)) documentListeners.set(event, []);
            documentListeners.get(event).push(fn);
        }
    };

    let fetchRequests = [];
    let fetchResponseFactory = async () => ({
        ok: true,
        status: 200,
        text: async () => JSON.stringify({ success: true, data: { id: 1 } })
    });

    const locationAssignments = [];
    const mockWindow = {
        document: mockDocument,
        location: {
            pathname: '/communities/7/manage/pending',
            href: 'http://localhost/communities/7/manage/pending',
            assign(url) { locationAssignments.push(String(url)); }
        },
        confirm() { return true; },
        alouteConfirm: async () => true,
        alert() {},
        setTimeout: (fn) => setTimeout(fn, 1),
        fetch: async (url, opts) => {
            fetchRequests.push({ url: String(url), opts });
            return fetchResponseFactory(String(url), opts);
        },
        bootstrap: {
            Modal: {
                getOrCreateInstance(el) {
                    return {
                        show() { el.classList.remove('d-none'); el._shown = true; },
                        hide() { el.classList.add('d-none'); el._shown = false; }
                    };
                }
            }
        }
    };

    const context = vm.createContext({
        window: mockWindow,
        document: mockDocument,
        Element: MockElement,
        URL: URL,
        setTimeout: mockWindow.setTimeout,
        fetch: mockWindow.fetch,
        confirm: mockWindow.confirm,
        alert: mockWindow.alert,
        Date: Date,
        JSON: JSON,
        Promise: Promise,
        console: console
    });

    return {
        context,
        mockWindow,
        mockDocument,
        elementsById,
        locationAssignments,
        register(el) { documentBody.appendChild(el); register(el); },
        getFetchRequests: () => fetchRequests,
        setFetchResponse: (fn) => { fetchResponseFactory = fn; },
        fireDocumentEvent(type, event) {
            const fns = documentListeners.get(type) || [];
            for (const fn of fns) fn.call(mockDocument, event);
        }
    };
}

function elementWithAttrs(tag, attrs, id) {
    const el = new MockElement(tag, id || '');
    for (const [k, v] of Object.entries(attrs || {})) el.setAttribute(k, v);
    return el;
}

function click(env, el) {
    env.fireDocumentEvent('click', { type: 'click', target: el, preventDefault() {} });
}

async function tick() {
    await new Promise(resolve => setTimeout(resolve, 5));
}

function buildRejectModal(env) {
    const modal = new MockElement('DIV', 'communityRejectPostModal');
    const reason = new MockElement('TEXTAREA', 'community-reject-reason');
    const counter = new MockElement('SPAN', 'community-reject-char-count');
    const status = new MockElement('DIV', 'community-reject-status');
    const confirmBtn = elementWithAttrs('BUTTON', { 'data-action': 'community-reject-post-confirm' }, 'btn-confirm-community-reject');
    const spinner = new MockElement('SPAN');
    spinner.classList.add('spinner-border', 'd-none');
    confirmBtn.appendChild(spinner);
    modal._listeners.set('hidden.bs.modal', []);
    modal.addEventListener('hidden.bs.modal', () => {});
    env.register(modal);
    env.register(reason);
    env.register(counter);
    env.register(status);
    env.register(confirmBtn);
    return { modal, reason, counter, status, confirmBtn };
}

function buildRemoveModal(env) {
    const modal = new MockElement('DIV', 'communityRemovePostModal');
    const reason = new MockElement('TEXTAREA', 'community-remove-reason');
    const counter = new MockElement('SPAN', 'community-remove-char-count');
    const status = new MockElement('DIV', 'community-remove-status');
    const communityName = new MockElement('STRONG', 'community-remove-community-name');
    const confirmBtn = elementWithAttrs('BUTTON', { 'data-action': 'community-remove-post-confirm' }, 'btn-confirm-community-remove');
    const spinner = new MockElement('SPAN');
    spinner.classList.add('spinner-border', 'd-none');
    confirmBtn.appendChild(spinner);
    modal.addEventListener('hidden.bs.modal', () => {});
    env.register(modal);
    env.register(reason);
    env.register(counter);
    env.register(status);
    env.register(communityName);
    env.register(confirmBtn);
    return { modal, reason, counter, status, communityName, confirmBtn };
}

function buildRemoveMemberModal(env) {
    const modal = new MockElement('DIV', 'communityRemoveMemberModal');
    const name = new MockElement('SPAN', 'community-remove-member-name');
    const confirmBtn = elementWithAttrs('BUTTON', { 'data-action': 'community-remove-member-confirm' }, 'btn-confirm-community-remove-member');
    const spinner = new MockElement('SPAN');
    spinner.classList.add('spinner-border', 'd-none');
    confirmBtn.appendChild(spinner);
    modal.addEventListener('hidden.bs.modal', () => {});
    env.register(modal);
    env.register(name);
    env.register(confirmBtn);
    return { modal, name, confirmBtn };
}

describe('AloUTE Community Moderation Frontend Tests', () => {

    describe('1. Approve flow', () => {
        it('sends POST with CSRF header and redirects with done=approved', async () => {
            const env = createTestEnvironment();
            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(communityManageSource, env.context);

            const btn = elementWithAttrs('BUTTON', {
                'data-action': 'community-approve-post',
                'data-post-id': '55',
                'data-community-id': '7'
            });
            env.register(btn);

            click(env, btn);
            await tick();

            const reqs = env.getFetchRequests();
            assert.equal(reqs.length, 1);
            assert.equal(reqs[0].url, '/api/communities/7/posts/55/approve');
            assert.equal(reqs[0].opts.method, 'POST');
            assert.equal(reqs[0].opts.headers['X-XSRF-TOKEN'], 'test-csrf-token-123');
            assert.equal(reqs[0].opts.credentials, 'include');

            await tick();
            assert.equal(env.locationAssignments.length, 1);
            assert.ok(env.locationAssignments[0].includes('done=approved'));
        });

        it('skips the request when the confirmation dialog is dismissed', async () => {
            const env = createTestEnvironment();
            env.mockWindow.alouteConfirm = async () => false;
            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(communityManageSource, env.context);

            const btn = elementWithAttrs('BUTTON', {
                'data-action': 'community-approve-post',
                'data-post-id': '56',
                'data-community-id': '7'
            });
            env.register(btn);

            click(env, btn);
            await tick(); await tick();

            assert.equal(env.getFetchRequests().length, 0);
            assert.equal(env.locationAssignments.length, 0);
        });
    });

    describe('2. Reject flow', () => {
        it('opens the modal and submits the trimmed reason in the POST body', async () => {
            const env = createTestEnvironment();
            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(communityManageSource, env.context);
            const m = buildRejectModal(env);

            const btn = elementWithAttrs('BUTTON', {
                'data-action': 'community-reject-post',
                'data-post-id': '77',
                'data-community-id': '7'
            });
            env.register(btn);

            click(env, btn);
            assert.equal(m.modal._shown, true);

            m.reason.value = '  Nội dung không phù hợp  ';
            click(env, m.confirmBtn);
            await tick(); await tick();

            const reqs = env.getFetchRequests();
            assert.equal(reqs.length, 1);
            assert.equal(reqs[0].url, '/api/communities/7/posts/77/reject');
            assert.equal(reqs[0].opts.method, 'POST');
            assert.deepEqual(JSON.parse(reqs[0].opts.body), { reason: 'Nội dung không phù hợp' });
            assert.ok(env.locationAssignments[0].includes('done=rejected'));
        });

        it('maps 409 POST_MODERATION_STATE_INVALID to a friendly Vietnamese message without redirect', async () => {
            const env = createTestEnvironment();
            env.setFetchResponse(async () => ({
                ok: false,
                status: 409,
                text: async () => JSON.stringify({
                    success: false,
                    error: { code: 'POST_MODERATION_STATE_INVALID', message: 'conflict' }
                })
            }));
            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(communityManageSource, env.context);
            const m = buildRejectModal(env);

            const btn = elementWithAttrs('BUTTON', {
                'data-action': 'community-reject-post',
                'data-post-id': '78',
                'data-community-id': '7'
            });
            env.register(btn);

            click(env, btn);
            m.reason.value = 'x';
            click(env, m.confirmBtn);
            await tick(); await tick();

            assert.equal(env.locationAssignments.length, 0);
            assert.ok(m.status.textContent.includes('quản trị viên khác'));
            assert.equal(m.status.classList.contains('d-none'), false);
        });
    });

    describe('3. Remove published post flow', () => {
        it('submits remove with reason and redirects with done=removed', async () => {
            const env = createTestEnvironment();
            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(communityManageSource, env.context);
            const m = buildRemoveModal(env);

            const btn = elementWithAttrs('BUTTON', {
                'data-action': 'community-remove-post',
                'data-post-id': '88',
                'data-community-id': '7',
                'data-community-name': 'CLB Tin học'
            });
            env.register(btn);

            click(env, btn);
            assert.equal(m.modal._shown, true);
            assert.equal(env.elementsById.get('community-remove-community-name').textContent, 'CLB Tin học');

            m.reason.value = 'Vi phạm nội quy';
            click(env, m.confirmBtn);
            await tick(); await tick();

            const reqs = env.getFetchRequests();
            assert.equal(reqs.length, 1);
            assert.equal(reqs[0].url, '/api/communities/7/posts/88/remove');
            assert.deepEqual(JSON.parse(reqs[0].opts.body), { reason: 'Vi phạm nội quy' });
            assert.ok(env.locationAssignments[0].includes('done=removed'));
        });
    });

    describe('4. Member management', () => {
        it('removes a member via DELETE and redirects with done=member-removed', async () => {
            const env = createTestEnvironment();
            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(communityManageSource, env.context);
            const m = buildRemoveMemberModal(env);

            const btn = elementWithAttrs('BUTTON', {
                'data-action': 'community-remove-member',
                'data-user-id': '12',
                'data-community-id': '7',
                'data-member-name': 'Nguyen Van A'
            });
            env.register(btn);

            click(env, btn);
            assert.equal(m.name.textContent, 'Nguyen Van A');
            click(env, m.confirmBtn);
            await tick(); await tick();

            const reqs = env.getFetchRequests();
            assert.equal(reqs.length, 1);
            assert.equal(reqs[0].url, '/api/communities/7/members/12');
            assert.equal(reqs[0].opts.method, 'DELETE');
            assert.ok(env.locationAssignments[0].includes('done=member-removed'));
        });

        it('PATCHes the selected role and redirects with done=role-updated', async () => {
            const env = createTestEnvironment();
            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(communityManageSource, env.context);

            const select = elementWithAttrs('SELECT', {
                'class': 'community-role-select',
                'data-community-id': '7',
                'data-user-id': '13',
                'data-member-name': 'Tran Thi B'
            }, 'role-select-13');
            select.value = 'MEMBER';
            select.setAttribute('data-previous-value', 'MEMBER');
            env.register(select);

            // Simulate user choosing ADMIN (after init captured previous value)
            select.value = 'ADMIN';
            env.fireDocumentEvent('change', { type: 'change', target: select });
            await tick(); await tick();

            const reqs = env.getFetchRequests();
            assert.equal(reqs.length, 1);
            assert.equal(reqs[0].url, '/api/communities/7/members/13/role');
            assert.equal(reqs[0].opts.method, 'PATCH');
            assert.deepEqual(JSON.parse(reqs[0].opts.body), { role: 'ADMIN' });
            assert.ok(env.locationAssignments[0].includes('done=role-updated'));
        });

        it('restores the previous role when the confirmation dialog is dismissed', async () => {
            const env = createTestEnvironment();
            env.mockWindow.alouteConfirm = async () => false;
            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(communityManageSource, env.context);

            const select = elementWithAttrs('SELECT', {
                'class': 'community-role-select',
                'data-community-id': '7',
                'data-user-id': '14'
            }, 'role-select-14');
            select.value = 'MEMBER';
            select.setAttribute('data-previous-value', 'MEMBER');
            env.register(select);

            select.value = 'ADMIN';
            env.fireDocumentEvent('change', { type: 'change', target: select });
            await tick(); await tick();

            assert.equal(env.getFetchRequests().length, 0);
            assert.equal(select.value, 'MEMBER');
        });
    });

    describe('5. Settings toggle', () => {
        it('PATCHes requirePostApproval and shows a success status', async () => {
            const env = createTestEnvironment();
            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(communityManageSource, env.context);

            const status = new MockElement('DIV', 'community-settings-status');
            status.classList.add('d-none');
            env.register(status);
            const sw = elementWithAttrs('INPUT', { 'data-community-id': '7' }, 'require-post-approval-switch');
            sw.type = 'checkbox';
            sw.checked = false;
            env.register(sw);

            sw.checked = true;
            env.fireDocumentEvent('change', { type: 'change', target: sw });
            await tick(); await tick();

            const reqs = env.getFetchRequests();
            assert.equal(reqs.length, 1);
            assert.equal(reqs[0].url, '/api/communities/7');
            assert.equal(reqs[0].opts.method, 'PATCH');
            assert.deepEqual(JSON.parse(reqs[0].opts.body), { requirePostApproval: true });
            assert.ok(status.textContent.includes('Đã bật'));
        });

        it('reverts the switch when the server rejects the change with COMMUNITY_OWNER_REQUIRED', async () => {
            const env = createTestEnvironment();
            env.setFetchResponse(async () => ({
                ok: false,
                status: 403,
                text: async () => JSON.stringify({
                    success: false,
                    error: { code: 'COMMUNITY_OWNER_REQUIRED', message: 'forbidden' }
                })
            }));
            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(communityManageSource, env.context);

            const status = new MockElement('DIV', 'community-settings-status');
            status.classList.add('d-none');
            env.register(status);
            const sw = elementWithAttrs('INPUT', { 'data-community-id': '7' }, 'require-post-approval-switch');
            sw.checked = false;
            env.register(sw);

            sw.checked = true;
            env.fireDocumentEvent('change', { type: 'change', target: sw });
            await tick(); await tick();

            assert.equal(sw.checked, false);
            assert.ok(status.textContent.includes('chủ sở hữu'));
            assert.equal(sw.disabled, false);
        });
    });

    describe('6. Double-submit protection', () => {
        it('does not fire a second request while one is in flight for the same post', async () => {
            const env = createTestEnvironment();
            let releaseFetch;
            env.setFetchResponse(() => new Promise(resolve => {
                releaseFetch = () => resolve({
                    ok: true, status: 200,
                    text: async () => JSON.stringify({ success: true, data: {} })
                });
            }));
            vm.runInContext(httpClientSource, env.context);
            vm.runInContext(communityManageSource, env.context);

            const btn = elementWithAttrs('BUTTON', {
                'data-action': 'community-approve-post',
                'data-post-id': '99',
                'data-community-id': '7'
            });
            env.register(btn);

            click(env, btn);
            await tick();
            // Second click while first is pending must not enqueue another request
            click(env, btn);
            await tick();
            assert.equal(env.getFetchRequests().length, 1);

            releaseFetch();
            await tick(); await tick();
        });
    });
});
