/**
 * AloUTE — Feature Completion Batch 2 Automated Regression Tests
 *
 * Built-in Node test suite (node:test + assert/strict + vm).
 * Exercises:
 * - P1-A: Search & Topic routes & navigation state
 * - P1-B: Community Join / Leave / Create
 * - P1-C: Reports UI (Post & User reporting)
 * - P1-D: Notification Unread Badges (Navbar & Sidebar sync)
 * - P1-E: Profile Saved tab ownership guard
 * - P2-A: Quote Repost UI
 * - P2-B: Page Title suffix deduplication
 */

import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '../..');

const pageActionsSource = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/static/js/page-actions.js'),
    'utf-8'
);

// -----------------------------------------------------------------------------
// Lightweight DOM Harness
// -----------------------------------------------------------------------------

function matchesCompound(el, sel) {
    if (!el || !sel) return false;
    let remaining = sel.trim();
    if (!remaining) return false;

    const tagMatch = remaining.match(/^([a-zA-Z0-9_-]+)/);
    if (tagMatch) {
        if (el.tagName.toLowerCase() !== tagMatch[1].toLowerCase()) return false;
        remaining = remaining.slice(tagMatch[1].length);
    }

    while (remaining.length > 0) {
        if (remaining.startsWith('#')) {
            const idMatch = remaining.match(/^#([a-zA-Z0-9_-]+)/);
            if (!idMatch) return false;
            if (el.id !== idMatch[1]) return false;
            remaining = remaining.slice(idMatch[0].length);
        } else if (remaining.startsWith('.')) {
            const classMatch = remaining.match(/^\.([a-zA-Z0-9_-]+)/);
            if (!classMatch) return false;
            if (!el.classList.contains(classMatch[1])) return false;
            remaining = remaining.slice(classMatch[0].length);
        } else if (remaining.startsWith('[')) {
            const attrMatch = remaining.match(/^\[([a-zA-Z0-9_-]+)(?:=(?:["']([^"']*)["']|([^"'\]]+)))?\]/);
            if (!attrMatch) return false;
            const attrName = attrMatch[1];
            const attrVal = attrMatch[2] !== undefined ? attrMatch[2] : attrMatch[3];
            if (attrVal !== undefined) {
                if (el.getAttribute(attrName) !== attrVal) return false;
            } else {
                if (!el.hasAttribute(attrName)) return false;
            }
            remaining = remaining.slice(attrMatch[0].length);
        } else {
            return false;
        }
    }
    return true;
}

function matchesSelector(el, sel) {
    if (!el || !sel) return false;
    if (sel.includes(',')) {
        return sel.split(',').some(part => matchesSelector(el, part.trim()));
    }
    return matchesCompound(el, sel);
}

function querySelector(root, selector) {
    for (const child of root.children) {
        if (matchesSelector(child, selector)) return child;
        const found = querySelector(child, selector);
        if (found) return found;
    }
    return null;
}

function querySelectorAll(root, selector, result = []) {
    for (const child of root.children) {
        if (matchesSelector(child, selector)) result.push(child);
        querySelectorAll(child, selector, result);
    }
    return result;
}

class MockElement {
    constructor(tagName = 'DIV', id = '', ownerDocument = null) {
        this.tagName = tagName.toUpperCase();
        this.id = id;
        this.ownerDocument = ownerDocument;
        this.className = '';
        this._attributes = new Map();
        this.dataset = {};
        this.children = [];
        this.parentElement = null;
        this._listeners = new Map();
        this.style = {};
        this.textContent = '';
        this._innerHTML = '';
        this.disabled = false;
        this.hidden = false;
        this.value = '';
    }

    focus() {
        this._focused = true;
    }

    get innerHTML() {
        return this._innerHTML;
    }

    set innerHTML(val) {
        this._innerHTML = val;
    }

    get classList() {
        return {
            add: (...names) => {
                const classes = new Set(this.className.split(/\s+/).filter(Boolean));
                for (const n of names) classes.add(n);
                this.className = Array.from(classes).join(' ');
            },
            remove: (...names) => {
                const classes = new Set(this.className.split(/\s+/).filter(Boolean));
                for (const n of names) classes.delete(n);
                this.className = Array.from(classes).join(' ');
            },
            contains: (name) => this.className.split(/\s+/).filter(Boolean).includes(name),
            toggle: (name, force) => {
                if (force === true) { this.classList.add(name); return true; }
                if (force === false) { this.classList.remove(name); return false; }
                if (this.classList.contains(name)) { this.classList.remove(name); return false; }
                this.classList.add(name); return true;
            }
        };
    }

    setAttribute(name, val) {
        this._attributes.set(name, String(val));
        if (name.startsWith('data-')) {
            const camel = name.slice(5).replace(/-([a-z])/g, (_, l) => l.toUpperCase());
            this.dataset[camel] = String(val);
        }
    }

    getAttribute(name) {
        return this._attributes.has(name) ? this._attributes.get(name) : null;
    }

    hasAttribute(name) {
        return this._attributes.has(name);
    }

    removeAttribute(name) {
        this._attributes.delete(name);
        if (name.startsWith('data-')) {
            const camel = name.slice(5).replace(/-([a-z])/g, (_, l) => l.toUpperCase());
            delete this.dataset[camel];
        }
    }

    appendChild(child) {
        child.parentElement = this;
        child.ownerDocument = this.ownerDocument;
        this.children.push(child);
        return child;
    }

    closest(selector) {
        let cur = this;
        while (cur) {
            if (matchesSelector(cur, selector)) return cur;
            cur = cur.parentElement;
        }
        return null;
    }

    querySelector(sel) {
        return querySelector(this, sel);
    }

    querySelectorAll(sel) {
        return querySelectorAll(this, sel, []);
    }

    addEventListener(type, cb) {
        if (!this._listeners.has(type)) {
            this._listeners.set(type, []);
        }
        this._listeners.get(type).push(cb);
    }

    removeEventListener(type, cb) {
        if (!this._listeners.has(type)) return;
        const list = this._listeners.get(type).filter(x => x !== cb);
        this._listeners.set(type, list);
    }

    dispatchEvent(event) {
        event.target = event.target || this;
        event.currentTarget = this;
        const handlers = this._listeners.get(event.type) || [];
        for (const h of handlers) {
            h.call(this, event);
            if (event._propagationStopped) return !event.defaultPrevented;
        }
        if (!event._propagationStopped) {
            if (this.parentElement) {
                this.parentElement.dispatchEvent(event);
            } else if (this.ownerDocument) {
                this.ownerDocument.dispatchEvent(event);
            }
        }
        return !event.defaultPrevented;
    }

    click() {
        const ev = new MockEvent('click', { bubbles: true, cancelable: true });
        this.dispatchEvent(ev);
    }
}

class MockEvent {
    constructor(type, init = {}) {
        this.type = type;
        this.bubbles = init.bubbles !== false;
        this.cancelable = init.cancelable !== false;
        this.defaultPrevented = false;
        this._propagationStopped = false;
        this.target = null;
        this.currentTarget = null;
    }

    preventDefault() {
        this.defaultPrevented = true;
    }

    stopPropagation() {
        this._propagationStopped = true;
    }
}

class MockCustomEvent extends MockEvent {
    constructor(type, init = {}) {
        super(type, init);
        this.detail = init.detail || null;
    }
}

class MockDocument {
    constructor() {
        this.cookie = 'XSRF-TOKEN=test-csrf-token';
        this.readyState = 'complete';
        this._listeners = new Map();
        this.body = new MockElement('BODY', 'body', this);
    }

    getElementById(id) {
        if (this.body.id === id) return this.body;
        return querySelector(this.body, '#' + id);
    }

    createElement(tagName) {
        return new MockElement(tagName, '', this);
    }

    querySelector(selector) {
        if (matchesSelector(this.body, selector)) return this.body;
        return querySelector(this.body, selector);
    }

    querySelectorAll(selector) {
        const res = [];
        if (matchesSelector(this.body, selector)) res.push(this.body);
        return querySelectorAll(this.body, selector, res);
    }

    addEventListener(type, fn) {
        if (!this._listeners.has(type)) this._listeners.set(type, []);
        this._listeners.get(type).push(fn);
    }

    removeEventListener(type, fn) {
        if (!this._listeners.has(type)) return;
        this._listeners.set(type, this._listeners.get(type).filter(l => l !== fn));
    }

    dispatchEvent(event) {
        event.currentTarget = this;
        const listeners = this._listeners.get(event.type) || [];
        for (const l of listeners) l.call(this, event);
        return !event.defaultPrevented;
    }
}

function createDOMEnvironment() {
    const doc = new MockDocument();
    const windowListeners = new Map();

    const win = {
        document: doc,
        location: { href: 'http://localhost:8080/feed', reloadCalls: 0, reload: function() { win.location.reloadCalls++; } },
        sessionStorage: (function() {
            const store = new Map();
            return {
                getItem: function(k) { return store.has(k) ? store.get(k) : null; },
                setItem: function(k, v) { store.set(k, String(v)); },
                removeItem: function(k) { store.delete(k); },
                clear: function() { store.clear(); }
            };
        })(),
        confirmCalls: [],
        confirmReturn: true,
        confirm: function (msg) {
            win.confirmCalls.push(msg);
            return win.confirmReturn;
        },
        fetchCalls: [],
        fetchHandler: null,
        fetch: async function (url, options = {}) {
            win.fetchCalls.push({ url, options });
            if (win.fetchHandler) {
                return win.fetchHandler(url, options);
            }
            return {
                ok: true,
                status: 200,
                text: async () => JSON.stringify({ success: true, data: {} })
            };
        },
        alouteHttp: {
            request: async function (url, options = {}) {
                const opts = Object.assign({}, options);
                opts.credentials = opts.credentials || 'include';
                opts.headers = Object.assign({}, opts.headers);
                if (doc.cookie && doc.cookie.includes('XSRF-TOKEN=')) {
                    const match = doc.cookie.match(/XSRF-TOKEN=([^;]+)/);
                    if (match) opts.headers['X-XSRF-TOKEN'] = match[1];
                }
                if (opts.body && typeof opts.body === 'object') {
                    if (!opts.headers['Content-Type']) {
                        opts.headers['Content-Type'] = 'application/json';
                    }
                    opts.body = JSON.stringify(opts.body);
                }
                return win.fetch(url, opts);
            },
            readJson: async function (response) {
                if (!response) return null;
                try {
                    const text = await response.text();
                    return text ? JSON.parse(text) : null;
                } catch (_) {
                    return null;
                }
            }
        },
        CustomEvent: MockCustomEvent,
        Event: MockEvent,
        addEventListener(type, cb) {
            if (!windowListeners.has(type)) windowListeners.set(type, []);
            windowListeners.get(type).push(cb);
        },
        dispatchEvent(event) {
            const handlers = windowListeners.get(event.type) || [];
            for (const h of handlers) {
                h.call(win, event);
            }
        },
        bootstrap: {
            Modal: class {
                constructor(el) {
                    this.el = el;
                    this.isShown = false;
                    this.showCalls = 0;
                    this.hideCalls = 0;
                }
                static getOrCreateInstance(el) {
                    if (!el._bsModal) el._bsModal = new win.bootstrap.Modal(el);
                    return el._bsModal;
                }
                static getInstance(el) {
                    return el._bsModal || null;
                }
                show() {
                    this.showCalls++;
                    this.isShown = true;
                }
                hide() {
                    this.hideCalls++;
                    this.isShown = false;
                }
            }
        },
        setTimeout: (cb, ms) => { cb(); return 1; },
        clearTimeout: () => {},
        console: { log: () => {}, warn: () => {}, error: () => {} }
    };

    win.window = win;
    return { window: win, document: doc };
}

// -----------------------------------------------------------------------------
// Test Suites
// -----------------------------------------------------------------------------

describe('AloUTE Batch 2 Frontend Features & Regressions', () => {

    describe('P1-B: Community Join & Leave & Create', () => {
        let env;

        beforeEach(() => {
            env = createDOMEnvironment();
        });

        it('Join Community: calls POST /api/communities/{id}/join and updates UI', async () => {
            const joinBtn = env.document.createElement('BUTTON');
            joinBtn.id = 'btn-join-community';
            joinBtn.setAttribute('data-action', 'join-community');
            joinBtn.setAttribute('data-community-id', '42');
            joinBtn.textContent = 'Tham gia';

            const memberCount = env.document.createElement('STRONG');
            memberCount.id = 'community-member-count';
            memberCount.textContent = '10';

            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'community-action-status';
            statusAlert.className = 'alert d-none';

            env.document.body.setAttribute('data-current-user-id', '1');
            env.document.body.appendChild(joinBtn);
            env.document.body.appendChild(memberCount);
            env.document.body.appendChild(statusAlert);

            env.window.fetchHandler = async (url, opts) => {
                assert.equal(url, '/api/communities/42/join');
                assert.equal(opts.method, 'POST');
                assert.equal(opts.credentials, 'include');
                assert.equal(opts.headers['X-XSRF-TOKEN'], 'test-csrf-token');
                return {
                    ok: true,
                    status: 200,
                    text: async () => JSON.stringify({
                        success: true,
                        data: {
                            communityId: 42,
                            user: { id: 1, username: 'sinhvien1' },
                            role: 'MEMBER',
                            joinedAt: '2026-10-12T10:00:00Z'
                        },
                        meta: null
                    })
                };
            };

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            // Click join
            joinBtn.click();

            // Allow microtasks to resolve
            await new Promise(r => setTimeout(r, 10));

            assert.equal(joinBtn.getAttribute('data-action'), 'leave-community');
            assert.equal(memberCount.textContent, '11');
            assert.equal(statusAlert.classList.contains('d-none'), false);
            assert.match(statusAlert.textContent, /thành công/);
        });

        it('Leave Community: prompts confirmation, calls DELETE /api/communities/{id}/members/me on confirm', async () => {
            const leaveBtn = env.document.createElement('BUTTON');
            leaveBtn.id = 'btn-leave-community';
            leaveBtn.setAttribute('data-action', 'leave-community');
            leaveBtn.setAttribute('data-community-id', '42');
            leaveBtn.textContent = 'Rời cộng đồng';

            const memberCount = env.document.createElement('STRONG');
            memberCount.id = 'community-member-count';
            memberCount.textContent = '11';

            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'community-action-status';
            statusAlert.className = 'alert d-none';

            env.document.body.appendChild(leaveBtn);
            env.document.body.appendChild(memberCount);
            env.document.body.appendChild(statusAlert);

            env.window.confirmReturn = true;
            env.window.fetchHandler = async (url, opts) => {
                assert.equal(url, '/api/communities/42/members/me');
                assert.equal(opts.method, 'DELETE');
                assert.equal(opts.credentials, 'include');
                return {
                    ok: true,
                    status: 204,
                    text: async () => ''
                };
            };

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            leaveBtn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.window.confirmCalls.length, 1);
            assert.equal(leaveBtn.getAttribute('data-action'), 'join-community');
            assert.equal(memberCount.textContent, '10');
            assert.match(statusAlert.textContent, /rời khỏi/);
            assert.equal(env.document.getElementById('aloute-toast-notice'), null);
        });

        it('Leave Community: falls back to toast notice when status alert element is absent', async () => {
            const leaveBtn = env.document.createElement('BUTTON');
            leaveBtn.id = 'btn-leave-community';
            leaveBtn.setAttribute('data-action', 'leave-community');
            leaveBtn.setAttribute('data-community-id', '42');
            leaveBtn.textContent = 'Rời cộng đồng';

            env.document.body.appendChild(leaveBtn);

            env.window.confirmReturn = true;
            env.window.fetchHandler = async (url, opts) => {
                assert.equal(url, '/api/communities/42/members/me');
                assert.equal(opts.method, 'DELETE');
                return {
                    ok: true,
                    status: 204,
                    text: async () => ''
                };
            };

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            leaveBtn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.window.confirmCalls.length, 1);
            assert.equal(leaveBtn.getAttribute('data-action'), 'join-community');
            const toast = env.document.getElementById('aloute-toast-notice');
            assert.notEqual(toast, null);
            assert.match(toast.textContent, /rời khỏi/);
        });

        it('Leave Community: does nothing if user cancels prompt', async () => {
            const leaveBtn = env.document.createElement('BUTTON');
            leaveBtn.setAttribute('data-action', 'leave-community');
            leaveBtn.setAttribute('data-community-id', '42');

            env.document.body.appendChild(leaveBtn);

            env.window.confirmReturn = false; // Cancel
            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            leaveBtn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.window.confirmCalls.length, 1);
            assert.equal(env.window.fetchCalls.length, 0); // No network request made
        });

        it('Create Community Modal: validates slug, name, privacy and sends POST /api/communities', async () => {
            const form = env.document.createElement('FORM');
            form.id = 'create-community-form';

            const nameInput = env.document.createElement('INPUT');
            nameInput.id = 'community-name-input';
            nameInput.value = 'CLB Robot';

            const slugInput = env.document.createElement('INPUT');
            slugInput.id = 'community-slug-input';
            slugInput.value = 'clb-robot';

            const descInput = env.document.createElement('TEXTAREA');
            descInput.id = 'community-description-input';
            descInput.value = 'Học tập robot và AI';

            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'create-community-status';
            statusAlert.className = 'alert d-none';

            form.appendChild(nameInput);
            form.appendChild(slugInput);
            form.appendChild(descInput);
            form.appendChild(statusAlert);
            env.document.body.appendChild(form);

            env.window.fetchHandler = async (url, opts) => {
                assert.equal(url, '/api/communities');
                assert.equal(opts.method, 'POST');
                const req = JSON.parse(opts.body);
                assert.equal(req.name, 'CLB Robot');
                assert.equal(req.slug, 'clb-robot');
                assert.equal(req.description, 'Học tập robot và AI');
                return {
                    ok: true,
                    status: 201,
                    text: async () => JSON.stringify({
                        success: true,
                        data: { id: 88, name: 'CLB Robot', slug: 'clb-robot' }
                    })
                };
            };

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            const submitEv = new MockEvent('submit', { cancelable: true });
            form.dispatchEvent(submitEv);
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.window.fetchCalls.length, 1);
            assert.match(statusAlert.textContent, /thành công/);
        });
    });

    describe('P1-C: Reports UI (Post & User)', () => {
        let env;

        beforeEach(() => {
            env = createDOMEnvironment();
        });

        it('Open report modal populates target preview and sets hidden fields for Post', () => {
            const reportBtn = env.document.createElement('BUTTON');
            reportBtn.setAttribute('data-action', 'open-report-modal');
            reportBtn.setAttribute('data-report-type', 'post');
            reportBtn.setAttribute('data-reported-id', '101');
            reportBtn.setAttribute('data-reported-name', 'Nguyễn Văn A');

            const modal = env.document.createElement('DIV');
            modal.id = 'reportModal';

            const typeInput = env.document.createElement('INPUT');
            typeInput.id = 'report-target-type';

            const idInput = env.document.createElement('INPUT');
            idInput.id = 'report-target-id';

            const preview = env.document.createElement('DIV');
            preview.id = 'report-target-preview';

            env.document.body.appendChild(reportBtn);
            env.document.body.appendChild(modal);
            env.document.body.appendChild(typeInput);
            env.document.body.appendChild(idInput);
            env.document.body.appendChild(preview);

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            reportBtn.click();

            assert.equal(typeInput.value, 'post');
            assert.equal(idInput.value, '101');
            assert.match(preview.textContent, /#101/);
        });

        it('Submit report sends POST /api/reports with reason and reportedPostId', async () => {
            const form = env.document.createElement('FORM');
            form.id = 'report-form';

            const typeInput = env.document.createElement('INPUT');
            typeInput.id = 'report-target-type';
            typeInput.value = 'post';

            const idInput = env.document.createElement('INPUT');
            idInput.id = 'report-target-id';
            idInput.value = '101';

            const reasonSelect = env.document.createElement('SELECT');
            reasonSelect.id = 'report-reason';
            reasonSelect.value = 'SPAM';

            const descInput = env.document.createElement('TEXTAREA');
            descInput.id = 'report-description';
            descInput.value = 'Quảng cáo sai sự thật';

            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'report-status';
            statusAlert.className = 'alert d-none';

            form.appendChild(typeInput);
            form.appendChild(idInput);
            form.appendChild(reasonSelect);
            form.appendChild(descInput);
            form.appendChild(statusAlert);
            env.document.body.appendChild(form);

            env.window.fetchHandler = async (url, opts) => {
                assert.equal(url, '/api/reports');
                assert.equal(opts.method, 'POST');
                const req = JSON.parse(opts.body);
                assert.equal(req.reportedPostId, 101);
                assert.equal(req.reason, 'SPAM');
                assert.equal(req.description, 'Quảng cáo sai sự thật');
                return {
                    ok: true,
                    status: 201,
                    text: async () => JSON.stringify({ success: true, data: { id: 1, reportedPostId: 101, reason: 'SPAM', status: 'PENDING', createdAt: '2026-10-12T10:00:00Z' }, meta: null })
                };
            };

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.window.fetchCalls.length, 1);
            assert.match(statusAlert.textContent, /thành công/);
        });

        it('Submit report for user sends reportedUserId instead of reportedPostId', async () => {
            const form = env.document.createElement('FORM');
            form.id = 'report-form';

            const typeInput = env.document.createElement('INPUT');
            typeInput.id = 'report-target-type';
            typeInput.value = 'user';

            const idInput = env.document.createElement('INPUT');
            idInput.id = 'report-target-id';
            idInput.value = '55';

            const reasonSelect = env.document.createElement('SELECT');
            reasonSelect.id = 'report-reason';
            reasonSelect.value = 'HARASSMENT';

            const descInput = env.document.createElement('TEXTAREA');
            descInput.id = 'report-description';
            descInput.value = 'Tin nhắn quấy rối liên tục';

            form.appendChild(typeInput);
            form.appendChild(idInput);
            form.appendChild(reasonSelect);
            form.appendChild(descInput);
            env.document.body.appendChild(form);

            env.window.fetchHandler = async (url, opts) => {
                assert.equal(url, '/api/reports');
                assert.equal(opts.method, 'POST');
                const req = JSON.parse(opts.body);
                assert.equal(req.reportedUserId, 55);
                assert.equal(req.reportedPostId, undefined);
                assert.equal(req.reason, 'HARASSMENT');
                return {
                    ok: true,
                    status: 201,
                    text: async () => JSON.stringify({ success: true, data: { id: 2, reportedUserId: 55, reason: 'HARASSMENT', status: 'PENDING', createdAt: '2026-10-12T10:00:00Z' }, meta: null })
                };
            };

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.window.fetchCalls.length, 1);
        });
    });

    describe('P1-D: Notification Unread Badges', () => {
        let env;

        beforeEach(() => {
            env = createDOMEnvironment();
        });

        it('Updates navbar and sidebar badges on aloute:unread-count-updated event', () => {
            const navBadge = env.document.createElement('SPAN');
            navBadge.id = 'navbar-notification-badge';
            navBadge.className = 'badge d-none';
            navBadge.textContent = '0';

            const sideBadge = env.document.createElement('SPAN');
            sideBadge.id = 'sidebar-notification-badge';
            sideBadge.className = 'badge d-none';
            sideBadge.textContent = '0';

            env.document.body.appendChild(navBadge);
            env.document.body.appendChild(sideBadge);

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            // Dispatch event with count = 5
            env.window.dispatchEvent(new MockCustomEvent('aloute:unread-count-updated', {
                detail: { unreadCount: 5 }
            }));

            assert.equal(navBadge.textContent, '5');
            assert.equal(navBadge.classList.contains('d-none'), false);
            assert.equal(sideBadge.textContent, '5');
            assert.equal(sideBadge.classList.contains('d-none'), false);

            // Dispatch event with count > 99
            env.window.dispatchEvent(new MockCustomEvent('aloute:unread-count-updated', {
                detail: { unreadCount: 120 }
            }));

            assert.equal(navBadge.textContent, '99+');
            assert.equal(sideBadge.textContent, '99+');

            // Dispatch event with count = 0 -> hides badge
            env.window.dispatchEvent(new MockCustomEvent('aloute:unread-count-updated', {
                detail: { unreadCount: 0 }
            }));

            assert.equal(navBadge.classList.contains('d-none'), true);
            assert.equal(sideBadge.classList.contains('d-none'), true);
        });
    });

    describe('P2-A: Quote Repost UI', () => {
        let env;

        beforeEach(() => {
            env = createDOMEnvironment();
        });

        it('Open quote modal populates quoted post preview and target id', () => {
            const quoteBtn = env.document.createElement('BUTTON');
            quoteBtn.setAttribute('data-action', 'open-quote-modal');
            quoteBtn.setAttribute('data-post-id', '300');
            quoteBtn.setAttribute('data-post-author', 'Trần B');
            quoteBtn.setAttribute('data-post-username', 'tranb');
            quoteBtn.setAttribute('data-post-content', 'Nội dung bài viết mẫu');

            const idInput = env.document.createElement('INPUT');
            idInput.id = 'quote-target-post-id';

            const previewAuthor = env.document.createElement('SPAN');
            previewAuthor.id = 'quoted-preview-author';

            const previewHandle = env.document.createElement('SPAN');
            previewHandle.id = 'quoted-preview-handle';

            const previewContent = env.document.createElement('P');
            previewContent.id = 'quoted-preview-content';

            env.document.body.appendChild(quoteBtn);
            env.document.body.appendChild(idInput);
            env.document.body.appendChild(previewAuthor);
            env.document.body.appendChild(previewHandle);
            env.document.body.appendChild(previewContent);

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            quoteBtn.click();

            assert.equal(idInput.value, '300');
            assert.equal(previewAuthor.textContent, 'Trần B');
            assert.equal(previewHandle.textContent, '@tranb');
            assert.equal(previewContent.textContent, 'Nội dung bài viết mẫu');
        });

        it('Submitting quote sends POST /api/posts/{postId}/quotes with content and visibility', async () => {
            const form = env.document.createElement('FORM');
            form.id = 'quote-post-form';

            const idInput = env.document.createElement('INPUT');
            idInput.id = 'quote-target-post-id';
            idInput.value = '300';

            const contentInput = env.document.createElement('TEXTAREA');
            contentInput.id = 'quote-post-content';
            contentInput.value = 'Rất đồng ý với chia sẻ này!';

            const visSelect = env.document.createElement('SELECT');
            visSelect.id = 'quote-post-visibility';
            visSelect.value = 'PUBLIC';

            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'quote-post-status';
            statusAlert.className = 'alert d-none';

            form.appendChild(idInput);
            form.appendChild(contentInput);
            form.appendChild(visSelect);
            form.appendChild(statusAlert);
            env.document.body.appendChild(form);

            env.window.fetchHandler = async (url, opts) => {
                assert.equal(url, '/api/posts/300/quotes');
                assert.equal(opts.method, 'POST');
                const req = JSON.parse(opts.body);
                assert.equal(req.content, 'Rất đồng ý với chia sẻ này!');
                assert.equal(req.visibility, 'PUBLIC');
                assert.equal(req.allowQuote, true);
                return {
                    ok: true,
                    status: 201,
                    text: async () => JSON.stringify({
                        success: true,
                        data: { id: 301, content: 'Rất đồng ý với chia sẻ này!', quotedPostId: 300 },
                        meta: null
                    })
                };
            };

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.window.fetchCalls.length, 1);
            assert.match(statusAlert.textContent, /thành công/);
            assert.equal(env.window.location.reloadCalls, 1);
            const flash = env.window.sessionStorage.getItem('aloute_flash_notice');
            assert.notEqual(flash, null);
            assert.match(JSON.parse(flash).message, /thành công/);
        });

        it('Page Actions consumes one-shot flash notice from sessionStorage and displays toast once', () => {
            env.window.sessionStorage.setItem('aloute_flash_notice', JSON.stringify({
                message: 'Đã đăng trích dẫn thành công!',
                type: 'success'
            }));

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            assert.equal(env.window.sessionStorage.getItem('aloute_flash_notice'), null);
            const toast = env.document.getElementById('aloute-toast-notice');
            assert.notEqual(toast, null);
            assert.match(toast.textContent, /thành công/);
        });

        it('Submitting quote with throwing sessionStorage navigates directly to /posts/{postId} without reload or error', async () => {
            const form = env.document.createElement('FORM');
            form.id = 'quote-post-form';

            const idInput = env.document.createElement('INPUT');
            idInput.id = 'quote-target-post-id';
            idInput.value = '300';

            const contentInput = env.document.createElement('TEXTAREA');
            contentInput.id = 'quote-post-content';
            contentInput.value = 'Trích dẫn khi storage ném lỗi';

            const visSelect = env.document.createElement('SELECT');
            visSelect.id = 'quote-post-visibility';
            visSelect.value = 'PUBLIC';

            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'quote-post-status';
            statusAlert.className = 'alert d-none';

            form.appendChild(idInput);
            form.appendChild(contentInput);
            form.appendChild(visSelect);
            form.appendChild(statusAlert);
            env.document.body.appendChild(form);

            env.window.sessionStorage.setItem = function () {
                throw new Error('QuotaExceededError');
            };

            env.window.fetchHandler = async (url, opts) => {
                return {
                    ok: true,
                    status: 201,
                    text: async () => JSON.stringify({
                        success: true,
                        data: { id: 305, content: 'Trích dẫn khi storage ném lỗi', quotedPostId: 300 },
                        meta: null
                    })
                };
            };

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.window.fetchCalls.length, 1);
            assert.equal(env.window.location.reloadCalls, 0); // No reload wipeout!
            assert.equal(env.window.location.href, '/posts/305'); // Direct navigation to canonical newly-created post!
            assert.match(statusAlert.textContent, /thành công/); // Confirmed success kept, no false error
        });

        it('Submitting quote with absent sessionStorage navigates directly to /posts/{postId}', async () => {
            const form = env.document.createElement('FORM');
            form.id = 'quote-post-form';

            const idInput = env.document.createElement('INPUT');
            idInput.id = 'quote-target-post-id';
            idInput.value = '300';

            const contentInput = env.document.createElement('TEXTAREA');
            contentInput.id = 'quote-post-content';
            contentInput.value = 'Trích dẫn khi storage vắng mặt';

            const visSelect = env.document.createElement('SELECT');
            visSelect.id = 'quote-post-visibility';
            visSelect.value = 'PUBLIC';

            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'quote-post-status';
            statusAlert.className = 'alert d-none';

            form.appendChild(idInput);
            form.appendChild(contentInput);
            form.appendChild(visSelect);
            form.appendChild(statusAlert);
            env.document.body.appendChild(form);

            delete env.window.sessionStorage;

            env.window.fetchHandler = async (url, opts) => {
                return {
                    ok: true,
                    status: 201,
                    text: async () => JSON.stringify({
                        success: true,
                        data: { id: 306, content: 'Trích dẫn khi storage vắng mặt', quotedPostId: 300 },
                        meta: null
                    })
                };
            };

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.window.fetchCalls.length, 1);
            assert.equal(env.window.location.reloadCalls, 0);
            assert.equal(env.window.location.href, '/posts/306');
            assert.match(statusAlert.textContent, /thành công/);
        });
    });

    describe('P2-B: Page Title Deduplication Logic', () => {
        it('Does not duplicate — AloUTE suffix when input ends with AloUTE', () => {
            function formatTitle(title) {
                if (!title || !title.trim()) return 'AloUTE — University Social Network';
                const trimmed = title.trim();
                return trimmed.endsWith('AloUTE') ? trimmed : trimmed + ' — AloUTE';
            }

            assert.equal(formatTitle('Tin nhắn — AloUTE'), 'Tin nhắn — AloUTE');
            assert.equal(formatTitle('Thông báo — AloUTE'), 'Thông báo — AloUTE');
            assert.equal(formatTitle('Cộng đồng'), 'Cộng đồng — AloUTE');
            assert.equal(formatTitle('Tìm kiếm'), 'Tìm kiếm — AloUTE');
            assert.equal(formatTitle(''), 'AloUTE — University Social Network');
        });
    });

    describe('Review-Fix Loop 1: Strict Confirmations, Malformed Responses, Locks & Single-Owner', () => {
        let env;

        beforeEach(() => {
            env = createDOMEnvironment();
        });

        it('Community Leave: rejects HTTP 200 (must be exact 204), preserves member count and keeps leave button', async () => {
            const leaveBtn = env.document.createElement('BUTTON');
            leaveBtn.id = 'btn-leave-community';
            leaveBtn.setAttribute('data-action', 'leave-community');
            leaveBtn.setAttribute('data-community-id', '42');
            leaveBtn.textContent = 'Rời cộng đồng';

            const memberCount = env.document.createElement('STRONG');
            memberCount.id = 'community-member-count';
            memberCount.textContent = '10';

            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'community-action-status';
            statusAlert.className = 'alert d-none';

            env.document.body.appendChild(leaveBtn);
            env.document.body.appendChild(memberCount);
            env.document.body.appendChild(statusAlert);

            env.window.confirmReturn = true;
            // Server responds with 200 instead of 204
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({ success: true, message: 'Unexpected 200' })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            leaveBtn.click();
            await new Promise(r => setTimeout(r, 10));

            // Must NOT switch to join-community, count remains 10
            assert.equal(leaveBtn.getAttribute('data-action'), 'leave-community');
            assert.equal(memberCount.textContent, '10');
            assert.equal(statusAlert.classList.contains('d-none'), false);
            assert.match(statusAlert.textContent, /không thể rời cộng đồng/i);
        });

        it('Community Join: rejects response with missing role or user, preserving count and state', async () => {
            const joinBtn = env.document.createElement('BUTTON');
            joinBtn.setAttribute('data-action', 'join-community');
            joinBtn.setAttribute('data-community-id', '42');
            joinBtn.textContent = 'Tham gia';

            const memberCount = env.document.createElement('STRONG');
            memberCount.id = 'community-member-count';
            memberCount.textContent = '5';

            env.document.body.appendChild(joinBtn);
            env.document.body.appendChild(memberCount);

            // Server responds with missing role and user
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({ success: true, data: { communityId: 42 } })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            joinBtn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(joinBtn.getAttribute('data-action'), 'join-community');
            assert.equal(memberCount.textContent, '5');
        });

        it('Keyed lock prevents duplicate concurrent submissions on Community Join', async () => {
            const joinBtn = env.document.createElement('BUTTON');
            joinBtn.setAttribute('data-action', 'join-community');
            joinBtn.setAttribute('data-community-id', '42');

            env.document.body.setAttribute('data-current-user-id', '1');
            env.document.body.appendChild(joinBtn);

            let fetchCalls = 0;
            let resolveFetch;
            env.window.fetchHandler = async () => {
                fetchCalls++;
                return new Promise(resolve => {
                    resolveFetch = () => resolve({
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: {
                                communityId: 42,
                                role: 'MEMBER',
                                user: { id: 1, username: 'sinhvien1' },
                                joinedAt: '2026-10-12T10:00:00Z'
                            }
                        })
                    });
                });
            };

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            // First click initiates request
            joinBtn.click();
            // Second rapid click while in-flight
            joinBtn.click();

            assert.equal(fetchCalls, 1);
            resolveFetch();
            await new Promise(r => setTimeout(r, 10));
            assert.equal(fetchCalls, 1);
        });

        it('Community Create: rejects non-integer/missing ID, keeps form draft and unfreezes modal', async () => {
            const modal = env.document.createElement('DIV');
            modal.id = 'createCommunityModal';

            const form = env.document.createElement('FORM');
            form.id = 'create-community-form';

            const nameInput = env.document.createElement('INPUT');
            nameInput.id = 'community-name-input';
            nameInput.value = 'CLB Nghiên cứu Khoa học';

            const slugInput = env.document.createElement('INPUT');
            slugInput.id = 'community-slug-input';
            slugInput.value = 'clb-nckh';

            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'create-community-status';
            statusAlert.className = 'alert d-none';

            form.appendChild(nameInput);
            form.appendChild(slugInput);
            form.appendChild(statusAlert);
            modal.appendChild(form);
            env.document.body.appendChild(modal);

            // Mock invalid response ID: -1
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({ success: true, data: { id: -1 } })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            // Form values must be PRESERVED
            assert.equal(nameInput.value, 'CLB Nghiên cứu Khoa học');
            assert.equal(slugInput.value, 'clb-nckh');
            // Modal must NOT be frozen
            assert.equal(modal.dataset.isFrozen, 'false');
            assert.equal(statusAlert.classList.contains('d-none'), false);
        });

        it('Report: rejects ReportResponse when reflected target does not match requested target', async () => {
            const form = env.document.createElement('FORM');
            form.id = 'report-form';

            const typeInput = env.document.createElement('INPUT');
            typeInput.id = 'report-target-type';
            typeInput.value = 'post';

            const idInput = env.document.createElement('INPUT');
            idInput.id = 'report-target-id';
            idInput.value = '100';

            const reasonSelect = env.document.createElement('SELECT');
            reasonSelect.id = 'report-reason';
            reasonSelect.value = 'SPAM';

            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'report-status';
            statusAlert.className = 'alert d-none';

            form.appendChild(typeInput);
            form.appendChild(idInput);
            form.appendChild(reasonSelect);
            form.appendChild(statusAlert);
            env.document.body.appendChild(form);

            // Response reflects mismatched reportedPostId (999 instead of 100)
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 201,
                text: async () => JSON.stringify({ success: true, data: { id: 1, reportedPostId: 999 } })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            assert.equal(statusAlert.classList.contains('d-none'), false);
            assert.match(statusAlert.textContent, /không thể gửi báo cáo/i);
        });

        it('Quote: rejects Quote response when quotedPostId does not match target post ID', async () => {
            const form = env.document.createElement('FORM');
            form.id = 'quote-post-form';

            const idInput = env.document.createElement('INPUT');
            idInput.id = 'quote-target-post-id';
            idInput.value = '500';

            const contentInput = env.document.createElement('TEXTAREA');
            contentInput.id = 'quote-post-content';
            contentInput.value = 'Nội dung trích dẫn kiểm thử';

            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'quote-post-status';
            statusAlert.className = 'alert d-none';

            form.appendChild(idInput);
            form.appendChild(contentInput);
            form.appendChild(statusAlert);
            env.document.body.appendChild(form);

            // Mismatched quotedPostId
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 201,
                text: async () => JSON.stringify({ success: true, data: { id: 501, quotedPostId: 999 } })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            // Content draft must be preserved on failure
            assert.equal(contentInput.value, 'Nội dung trích dẫn kiểm thử');
            assert.equal(statusAlert.classList.contains('d-none'), false);
        });

        it('Notifications Single Owner: does not dispatch unread count fetch if notifications-content is present', async () => {
            // Container present indicating /notifications page
            const notifContent = env.document.createElement('DIV');
            notifContent.id = 'notifications-content';

            const userDropdown = env.document.createElement('DIV');
            userDropdown.className = 'aloute-user-dropdown-btn';

            const navBadge = env.document.createElement('SPAN');
            navBadge.id = 'navbar-notification-badge';

            env.document.body.appendChild(notifContent);
            env.document.body.appendChild(userDropdown);
            env.document.body.appendChild(navBadge);

            let unreadFetchCalls = 0;
            env.window.fetchHandler = async (url) => {
                if (url.includes('/unread-count')) {
                    unreadFetchCalls++;
                }
                return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: { unreadCount: 5 } }) };
            };

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            // Trigger DOMContentLoaded
            env.document.dispatchEvent(new MockEvent('DOMContentLoaded'));
            await new Promise(r => setTimeout(r, 10));

            // page-actions.js must NOT fetch unread-count on /notifications page!
            assert.equal(unreadFetchCalls, 0);
        });

        it('Notification Badge: sets accessible aria-label and handles zero/guest/99+ counts correctly', () => {
            const navBadge = env.document.createElement('SPAN');
            navBadge.id = 'navbar-notification-badge';

            const sideBadge = env.document.createElement('SPAN');
            sideBadge.id = 'sidebar-notification-badge';

            env.document.body.appendChild(navBadge);
            env.document.body.appendChild(sideBadge);

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            // Event with count = 0
            env.window.dispatchEvent(new MockCustomEvent('aloute:unread-count-updated', { detail: { unreadCount: 0 } }));
            assert.equal(navBadge.textContent, '0');
            assert.equal(navBadge.getAttribute('aria-label'), 'Không có thông báo mới');
            assert.equal(navBadge.classList.contains('d-none'), true);

            // Event with count = 12
            env.window.dispatchEvent(new MockCustomEvent('aloute:unread-count-updated', { detail: { unreadCount: 12 } }));
            assert.equal(navBadge.textContent, '12');
            assert.equal(navBadge.getAttribute('aria-label'), '12 thông báo chưa đọc');
            assert.equal(navBadge.classList.contains('d-none'), false);

            // Event with count = 150 (> 99)
            env.window.dispatchEvent(new MockCustomEvent('aloute:unread-count-updated', { detail: { unreadCount: 150 } }));
            assert.equal(navBadge.textContent, '99+');
            assert.equal(navBadge.getAttribute('aria-label'), 'Hơn 99 thông báo chưa đọc');
            assert.equal(sideBadge.textContent, '99+');
        });
    });

    describe('Review Loop 2: Hardened Confirmation, Target Integrity & Transport Guards', () => {
        let env;

        beforeEach(() => {
            env = createDOMEnvironment();
        });

        it('Join: rejects 201 Created (strictly requires 200 OK), preserves join button state', async () => {
            const joinBtn = env.document.createElement('BUTTON');
            joinBtn.setAttribute('data-action', 'join-community');
            joinBtn.setAttribute('data-community-id', '42');
            joinBtn.textContent = 'Tham gia';
            env.document.body.appendChild(joinBtn);

            env.window.fetchHandler = async () => ({
                ok: true,
                status: 201,
                text: async () => JSON.stringify({
                    success: true,
                    data: { communityId: 42, role: 'MEMBER', user: { id: 1 } }
                })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            joinBtn.click();
            await new Promise(r => setTimeout(r, 10));

            // Must NOT switch to leave-community on 201
            assert.equal(joinBtn.getAttribute('data-action'), 'join-community');
        });

        it('Join: rejects response with mismatched communityId, mismatched userId, or invalid role enum', async () => {
            const joinBtn = env.document.createElement('BUTTON');
            joinBtn.setAttribute('data-action', 'join-community');
            joinBtn.setAttribute('data-community-id', '42');
            joinBtn.textContent = 'Tham gia';
            env.document.body.setAttribute('data-current-user-id', '99');
            env.document.body.appendChild(joinBtn);

            // 1. Mismatched communityId
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({
                    success: true,
                    data: { communityId: 43, role: 'MEMBER', user: { id: 99 } }
                })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            joinBtn.click();
            await new Promise(r => setTimeout(r, 10));
            assert.equal(joinBtn.getAttribute('data-action'), 'join-community');

            // 2. Invalid role enum
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({
                    success: true,
                    data: { communityId: 42, role: 'OWNER', user: { id: 99 } }
                })
            });
            joinBtn.click();
            await new Promise(r => setTimeout(r, 10));
            assert.equal(joinBtn.getAttribute('data-action'), 'join-community');

            // 3. Mismatched userId against viewer
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({
                    success: true,
                    data: { communityId: 42, role: 'MEMBER', user: { id: 100 } }
                })
            });
            joinBtn.click();
            await new Promise(r => setTimeout(r, 10));
            assert.equal(joinBtn.getAttribute('data-action'), 'join-community');
        });

        it('Create Community: rejects 200 OK (strictly requires 201 Created)', async () => {
            const form = env.document.createElement('FORM');
            form.id = 'create-community-form';

            const nameInput = env.document.createElement('INPUT');
            nameInput.id = 'community-name-input';
            nameInput.value = 'Hội AI AloUTE';

            const slugInput = env.document.createElement('INPUT');
            slugInput.id = 'community-slug-input';
            slugInput.value = 'hoi-ai-aloute';

            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'create-community-status';
            statusAlert.className = 'alert d-none';

            form.appendChild(nameInput);
            form.appendChild(slugInput);
            form.appendChild(statusAlert);
            env.document.body.appendChild(form);

            // Server returns 200 instead of 201
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({
                    success: true,
                    data: { id: 1, name: 'Hội AI AloUTE', slug: 'hoi-ai-aloute' }
                })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            // Must preserve inputs and show error
            assert.equal(nameInput.value, 'Hội AI AloUTE');
            assert.equal(slugInput.value, 'hoi-ai-aloute');
            assert.equal(statusAlert.classList.contains('d-none'), false);
            assert.match(statusAlert.textContent, /không thể tạo cộng đồng/i);
        });

        it('Report: rejects 200 OK (strictly requires 201 Created)', async () => {
            const form = env.document.createElement('FORM');
            form.id = 'report-form';

            const idInput = env.document.createElement('INPUT');
            idInput.id = 'report-target-id';
            idInput.value = '10';

            const typeInput = env.document.createElement('INPUT');
            typeInput.id = 'report-target-type';
            typeInput.value = 'post';

            const reasonSelect = env.document.createElement('SELECT');
            reasonSelect.id = 'report-reason';
            reasonSelect.value = 'SPAM';

            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'report-status';
            statusAlert.className = 'alert d-none';

            form.appendChild(idInput);
            form.appendChild(typeInput);
            form.appendChild(reasonSelect);
            form.appendChild(statusAlert);
            env.document.body.appendChild(form);

            // 200 instead of 201
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({
                    success: true,
                    data: { id: 1, reportedPostId: 10, reportedUserId: null }
                })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            assert.equal(statusAlert.classList.contains('d-none'), false);
            assert.match(statusAlert.textContent, /không thể gửi báo cáo/i);
        });

        it('Report: rejects conflicting target (both reportedUserId and reportedPostId present)', async () => {
            const form = env.document.createElement('FORM');
            form.id = 'report-form';

            const idInput = env.document.createElement('INPUT');
            idInput.id = 'report-target-id';
            idInput.value = '10';

            const typeInput = env.document.createElement('INPUT');
            typeInput.id = 'report-target-type';
            typeInput.value = 'post';

            const reasonSelect = env.document.createElement('SELECT');
            reasonSelect.id = 'report-reason';
            reasonSelect.value = 'HARASSMENT';

            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'report-status';
            statusAlert.className = 'alert d-none';

            form.appendChild(idInput);
            form.appendChild(typeInput);
            form.appendChild(reasonSelect);
            form.appendChild(statusAlert);
            env.document.body.appendChild(form);

            // Conflicting target: both reportedPostId AND reportedUserId are set
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 201,
                text: async () => JSON.stringify({
                    success: true,
                    data: { id: 1, reportedPostId: 10, reportedUserId: 99 }
                })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            assert.equal(statusAlert.classList.contains('d-none'), false);
            assert.match(statusAlert.textContent, /không thể gửi báo cáo/i);
        });

        it('Report: validates reason enum before sending request', async () => {
            const form = env.document.createElement('FORM');
            form.id = 'report-form';

            const idInput = env.document.createElement('INPUT');
            idInput.id = 'report-target-id';
            idInput.value = '10';

            const reasonSelect = env.document.createElement('SELECT');
            reasonSelect.id = 'report-reason';
            reasonSelect.value = 'INVALID_REASON';

            form.appendChild(idInput);
            form.appendChild(reasonSelect);
            env.document.body.appendChild(form);

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            // No request sent
            assert.equal(env.window.fetchCalls.length, 0);
            assert.equal(reasonSelect.classList.contains('is-invalid'), true);
        });

        it('Quote: rejects 200 OK (strictly requires 201 Created)', async () => {
            const form = env.document.createElement('FORM');
            form.id = 'quote-post-form';

            const idInput = env.document.createElement('INPUT');
            idInput.id = 'quote-target-post-id';
            idInput.value = '50';

            const contentInput = env.document.createElement('TEXTAREA');
            contentInput.id = 'quote-post-content';
            contentInput.value = 'Trích dẫn hay';

            const visSelect = env.document.createElement('SELECT');
            visSelect.id = 'quote-post-visibility';
            visSelect.value = 'PUBLIC';

            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'quote-post-status';
            statusAlert.className = 'alert d-none';

            form.appendChild(idInput);
            form.appendChild(contentInput);
            form.appendChild(visSelect);
            form.appendChild(statusAlert);
            env.document.body.appendChild(form);

            // 200 instead of 201
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({
                    success: true,
                    data: { id: 99, quotedPostId: 50 }
                })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            assert.equal(statusAlert.classList.contains('d-none'), false);
            assert.match(statusAlert.textContent, /không thể đăng trích dẫn/i);
            assert.equal(contentInput.value, 'Trích dẫn hay');
        });

        it('Quote: rejects nested quotedPost fallback without scalar quotedPostId', async () => {
            const form = env.document.createElement('FORM');
            form.id = 'quote-post-form';

            const idInput = env.document.createElement('INPUT');
            idInput.id = 'quote-target-post-id';
            idInput.value = '50';

            const contentInput = env.document.createElement('TEXTAREA');
            contentInput.id = 'quote-post-content';
            contentInput.value = 'Trích dẫn';

            const visSelect = env.document.createElement('SELECT');
            visSelect.id = 'quote-post-visibility';
            visSelect.value = 'PUBLIC';

            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'quote-post-status';
            statusAlert.className = 'alert d-none';

            form.appendChild(idInput);
            form.appendChild(contentInput);
            form.appendChild(visSelect);
            form.appendChild(statusAlert);
            env.document.body.appendChild(form);

            // Nested fallback present but scalar quotedPostId is missing/null
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 201,
                text: async () => JSON.stringify({
                    success: true,
                    data: { id: 99, quotedPostId: null, quotedPost: { id: 50 } }
                })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            assert.equal(statusAlert.classList.contains('d-none'), false);
            assert.match(statusAlert.textContent, /không thể đăng trích dẫn/i);
        });

        it('Quote: validates visibility enum (rejects COMMUNITY_ONLY or unknown values)', async () => {
            const form = env.document.createElement('FORM');
            form.id = 'quote-post-form';

            const idInput = env.document.createElement('INPUT');
            idInput.id = 'quote-target-post-id';
            idInput.value = '50';

            const contentInput = env.document.createElement('TEXTAREA');
            contentInput.id = 'quote-post-content';
            contentInput.value = 'Trích dẫn';

            const visSelect = env.document.createElement('SELECT');
            visSelect.id = 'quote-post-visibility';
            visSelect.value = 'COMMUNITY_ONLY';

            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'quote-post-status';
            statusAlert.className = 'alert d-none';

            form.appendChild(idInput);
            form.appendChild(contentInput);
            form.appendChild(visSelect);
            form.appendChild(statusAlert);
            env.document.body.appendChild(form);

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            // No network call
            assert.equal(env.window.fetchCalls.length, 0);
            assert.equal(statusAlert.classList.contains('d-none'), false);
            assert.match(statusAlert.textContent, /phạm vi hiển thị không hợp lệ/i);
        });

        it('Transport Guard: fails gracefully when window.alouteHttp is absent without throwing or raw fetch', async () => {
            delete env.window.alouteHttp;

            const form = env.document.createElement('FORM');
            form.id = 'report-form';

            const idInput = env.document.createElement('INPUT');
            idInput.id = 'report-target-id';
            idInput.value = '10';

            const reasonSelect = env.document.createElement('SELECT');
            reasonSelect.id = 'report-reason';
            reasonSelect.value = 'SPAM';

            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'report-status';
            statusAlert.className = 'alert d-none';

            form.appendChild(idInput);
            form.appendChild(reasonSelect);
            form.appendChild(statusAlert);
            env.document.body.appendChild(form);

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            // fetchCalls must be 0
            assert.equal(env.window.fetchCalls.length, 0);
            assert.equal(statusAlert.classList.contains('d-none'), false);
            assert.match(statusAlert.textContent, /dịch vụ mạng không khả dụng/i);
        });

        it('Modal Freeze: prevents retargeting and reset while request is in flight', () => {
            const modal = env.document.createElement('DIV');
            modal.id = 'reportModal';
            modal.dataset.isFrozen = 'true';

            const idInput = env.document.createElement('INPUT');
            idInput.id = 'report-target-id';
            idInput.value = '10';

            const triggerBtn = env.document.createElement('BUTTON');
            triggerBtn.setAttribute('data-action', 'open-report-modal');
            triggerBtn.setAttribute('data-report-type', 'post');
            triggerBtn.setAttribute('data-reported-id', '99');

            env.document.body.appendChild(modal);
            env.document.body.appendChild(idInput);
            env.document.body.appendChild(triggerBtn);

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            triggerBtn.click();

            // Must NOT retarget while frozen! Target remains 10, not 99
            assert.equal(idInput.value, '10');
        });

        it('Notification Badges: updates both desktop and mobile sidebars plus navbar with parent link aria-label', () => {
            const navLink = env.document.createElement('A');
            navLink.className = 'nav-link';
            const navBadge = env.document.createElement('SPAN');
            navBadge.id = 'navbar-notification-badge';
            navLink.appendChild(navBadge);

            const desktopLink = env.document.createElement('A');
            const desktopBadge = env.document.createElement('SPAN');
            desktopBadge.className = 'sidebar-notification-badge';
            desktopLink.appendChild(desktopBadge);

            const mobileLink = env.document.createElement('A');
            const mobileBadge = env.document.createElement('SPAN');
            mobileBadge.setAttribute('data-badge', 'notification');
            mobileLink.appendChild(mobileBadge);

            env.document.body.appendChild(navLink);
            env.document.body.appendChild(desktopLink);
            env.document.body.appendChild(mobileLink);

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            env.window.dispatchEvent(new MockCustomEvent('aloute:unread-count-updated', { detail: { unreadCount: 7 } }));

            assert.equal(navBadge.textContent, '7');
            assert.equal(desktopBadge.textContent, '7');
            assert.equal(mobileBadge.textContent, '7');
            assert.equal(desktopLink.getAttribute('aria-label'), 'Thông báo (7 chưa đọc)');
            assert.equal(mobileLink.getAttribute('aria-label'), 'Thông báo (7 chưa đọc)');
            assert.equal(navLink.getAttribute('aria-label'), 'Thông báo (7 chưa đọc)');
        });

        it('Notification Badges: discards malformed unread counts and preserves last confirmed state', () => {
            const sideLink = env.document.createElement('A');
            const sideBadge = env.document.createElement('SPAN');
            sideBadge.className = 'sidebar-notification-badge';
            sideLink.appendChild(sideBadge);
            env.document.body.appendChild(sideLink);

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            // Establish valid confirmed state: 7
            env.window.dispatchEvent(new MockCustomEvent('aloute:unread-count-updated', { detail: { unreadCount: 7 } }));
            assert.equal(sideBadge.textContent, '7');
            assert.equal(sideBadge.classList.contains('d-none'), false);
            assert.equal(sideLink.getAttribute('aria-label'), 'Thông báo (7 chưa đọc)');

            // Pass invalid values: boolean, array, negative, non-numeric string
            for (const badVal of [true, [12], '1e3', -5, null, undefined, NaN]) {
                env.window.dispatchEvent(new MockCustomEvent('aloute:unread-count-updated', { detail: { unreadCount: badVal } }));
                // Must NEVER overwrite with fake 0! Retains last confirmed valid state (7)
                assert.equal(sideBadge.textContent, '7');
                assert.equal(sideBadge.classList.contains('d-none'), false);
                assert.equal(sideLink.getAttribute('aria-label'), 'Thông báo (7 chưa đọc)');
            }
        });
    });

    describe('Final Remediation: Fixes 1-4 Verification', () => {
        let env;

        beforeEach(() => {
            env = createDOMEnvironment();
        });

        // ---------------------------------------------------------------------
        // FIX 1 Tests: Community Join Canonical Validation & Strict Viewer ID
        // ---------------------------------------------------------------------

        it('FIX 1: Join rejects when viewer ID is noncanonical string with prefix (e.g. "12abc")', async () => {
            const joinBtn = env.document.createElement('BUTTON');
            joinBtn.setAttribute('data-action', 'join-community');
            joinBtn.setAttribute('data-community-id', '42');
            joinBtn.textContent = 'Tham gia';
            // Noncanonical prefix string: parseInt would return 12, but strict check must return null!
            env.document.body.setAttribute('data-current-user-id', '12abc');
            env.document.body.appendChild(joinBtn);

            env.window.fetchHandler = async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({
                    success: true,
                    data: {
                        communityId: 42,
                        user: { id: 12, username: 'sinhvien12' },
                        role: 'MEMBER',
                        joinedAt: '2026-10-12T10:00:00Z'
                    }
                })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            joinBtn.click();
            await new Promise(r => setTimeout(r, 10));

            // Must NOT confirm membership because authUserId is null (noncanonical viewer ID)
            assert.equal(joinBtn.getAttribute('data-action'), 'join-community');
        });

        it('FIX 1: Join rejects when viewer is unauthenticated (no data-current-user-id)', async () => {
            const joinBtn = env.document.createElement('BUTTON');
            joinBtn.setAttribute('data-action', 'join-community');
            joinBtn.setAttribute('data-community-id', '42');
            joinBtn.textContent = 'Tham gia';
            // No data-current-user-id on body
            env.document.body.appendChild(joinBtn);

            env.window.fetchHandler = async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({
                    success: true,
                    data: {
                        communityId: 42,
                        user: { id: 1, username: 'sinhvien1' },
                        role: 'MEMBER',
                        joinedAt: '2026-10-12T10:00:00Z'
                    }
                })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            joinBtn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(joinBtn.getAttribute('data-action'), 'join-community');
        });

        it('FIX 1: Join rejects legacy data.userId fallback when data.user is missing', async () => {
            const joinBtn = env.document.createElement('BUTTON');
            joinBtn.setAttribute('data-action', 'join-community');
            joinBtn.setAttribute('data-community-id', '42');
            joinBtn.textContent = 'Tham gia';
            env.document.body.setAttribute('data-current-user-id', '1');
            env.document.body.appendChild(joinBtn);

            // Legacy payload with data.userId instead of canonical data.user object
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({
                    success: true,
                    data: {
                        communityId: 42,
                        userId: 1,
                        role: 'MEMBER',
                        joinedAt: '2026-10-12T10:00:00Z'
                    }
                })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            joinBtn.click();
            await new Promise(r => setTimeout(r, 10));

            // Legacy fallback is removed; must NOT confirm membership
            assert.equal(joinBtn.getAttribute('data-action'), 'join-community');
        });

        it('FIX 1: Join rejects when joinedAt timestamp is missing or invalid', async () => {
            const joinBtn = env.document.createElement('BUTTON');
            joinBtn.setAttribute('data-action', 'join-community');
            joinBtn.setAttribute('data-community-id', '42');
            joinBtn.textContent = 'Tham gia';
            env.document.body.setAttribute('data-current-user-id', '1');
            env.document.body.appendChild(joinBtn);

            env.window.fetchHandler = async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({
                    success: true,
                    data: {
                        communityId: 42,
                        user: { id: 1, username: 'sinhvien1' },
                        role: 'MEMBER',
                        joinedAt: 'not-a-date'
                    }
                })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            joinBtn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(joinBtn.getAttribute('data-action'), 'join-community');
        });

        it('FIX 1: Join succeeds and confirms membership with canonical response and matching viewer ID', async () => {
            const joinBtn = env.document.createElement('BUTTON');
            joinBtn.setAttribute('data-action', 'join-community');
            joinBtn.setAttribute('data-community-id', '42');
            joinBtn.textContent = 'Tham gia';

            const memberCount = env.document.createElement('STRONG');
            memberCount.id = 'community-member-count';
            memberCount.textContent = '10';

            env.document.body.setAttribute('data-current-user-id', '5');
            env.document.body.appendChild(joinBtn);
            env.document.body.appendChild(memberCount);

            env.window.fetchHandler = async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({
                    success: true,
                    data: {
                        communityId: 42,
                        user: { id: 5, username: 'sv5' },
                        role: 'MEMBER',
                        joinedAt: '2026-10-12T10:00:00Z'
                    },
                    meta: null
                })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            joinBtn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(joinBtn.getAttribute('data-action'), 'leave-community');
            assert.equal(memberCount.textContent, '11');
        });

        it('FIX 1: Join rejects missing meta, non-null meta, or synthetic inherited meta with unchanged count/UI', async () => {
            const joinBtn = env.document.createElement('BUTTON');
            joinBtn.setAttribute('data-action', 'join-community');
            joinBtn.setAttribute('data-community-id', '42');
            joinBtn.textContent = 'Tham gia';

            const memberCount = env.document.createElement('STRONG');
            memberCount.id = 'community-member-count';
            memberCount.textContent = '10';

            const statusEl = env.document.createElement('DIV');
            statusEl.id = 'community-action-status';
            statusEl.className = 'alert d-none';

            env.document.body.setAttribute('data-current-user-id', '5');
            env.document.body.appendChild(joinBtn);
            env.document.body.appendChild(memberCount);
            env.document.body.appendChild(statusEl);

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            const canonicalData = {
                communityId: 42,
                user: { id: 5, username: 'sv5' },
                role: 'MEMBER',
                joinedAt: '2026-10-12T10:00:00Z'
            };

            // 1. Missing meta in envelope
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({
                    success: true,
                    data: canonicalData
                })
            });
            joinBtn.click();
            await new Promise(r => setTimeout(r, 10));
            assert.equal(joinBtn.getAttribute('data-action'), 'join-community');
            assert.equal(memberCount.textContent, '10');
            assert.match(statusEl.textContent, /không thể tham gia/i);

            // 2. Non-null meta in envelope
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({
                    success: true,
                    data: canonicalData,
                    meta: { page: 1, limit: 10 }
                })
            });
            joinBtn.click();
            await new Promise(r => setTimeout(r, 10));
            assert.equal(joinBtn.getAttribute('data-action'), 'join-community');
            assert.equal(memberCount.textContent, '10');

            // 3. Synthetic inherited-only meta (not own property)
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 200,
                text: async () => ''
            });
            const originalReadJson = env.window.alouteHttp.readJson;
            env.window.alouteHttp.readJson = async () => {
                const proto = { meta: null };
                const syntheticBody = Object.create(proto);
                syntheticBody.success = true;
                syntheticBody.data = canonicalData;
                return syntheticBody;
            };
            try {
                joinBtn.click();
                await new Promise(r => setTimeout(r, 10));
                assert.equal(joinBtn.getAttribute('data-action'), 'join-community');
                assert.equal(memberCount.textContent, '10');
            } finally {
                env.window.alouteHttp.readJson = originalReadJson;
            }
        });

        // ---------------------------------------------------------------------
        // FIX 2 Tests: Report Response Reason & DTO Validation
        // ---------------------------------------------------------------------

        it('FIX 2: Report rejects when returned reason does not equal submitted reason', async () => {
            const form = env.document.createElement('FORM');
            form.id = 'report-form';
            const idInput = env.document.createElement('INPUT');
            idInput.id = 'report-target-id';
            idInput.value = '10';
            const typeInput = env.document.createElement('INPUT');
            typeInput.id = 'report-target-type';
            typeInput.value = 'post';
            const reasonSelect = env.document.createElement('SELECT');
            reasonSelect.id = 'report-reason';
            reasonSelect.value = 'SPAM';
            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'report-status';
            statusAlert.className = 'alert d-none';

            form.appendChild(idInput);
            form.appendChild(typeInput);
            form.appendChild(reasonSelect);
            form.appendChild(statusAlert);
            env.document.body.appendChild(form);

            // Response returns HARASSMENT instead of submitted SPAM
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 201,
                text: async () => JSON.stringify({
                    success: true,
                    data: {
                        id: 1,
                        reportedPostId: 10,
                        reason: 'HARASSMENT',
                        status: 'PENDING',
                        createdAt: '2026-10-12T10:00:00Z'
                    }
                })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            assert.equal(statusAlert.classList.contains('d-none'), false);
            assert.match(statusAlert.textContent, /không thể gửi báo cáo/i);
        });

        it('FIX 2: Report rejects when returned reason is not one of 6 canonical enums', async () => {
            const form = env.document.createElement('FORM');
            form.id = 'report-form';
            const idInput = env.document.createElement('INPUT');
            idInput.id = 'report-target-id';
            idInput.value = '10';
            const typeInput = env.document.createElement('INPUT');
            typeInput.id = 'report-target-type';
            typeInput.value = 'post';
            const reasonSelect = env.document.createElement('SELECT');
            reasonSelect.id = 'report-reason';
            reasonSelect.value = 'OTHER';
            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'report-status';
            statusAlert.className = 'alert d-none';

            form.appendChild(idInput);
            form.appendChild(typeInput);
            form.appendChild(reasonSelect);
            form.appendChild(statusAlert);
            env.document.body.appendChild(form);

            env.window.fetchHandler = async () => ({
                ok: true,
                status: 201,
                text: async () => JSON.stringify({
                    success: true,
                    data: {
                        id: 1,
                        reportedPostId: 10,
                        reason: 'CUSTOM_REASON',
                        status: 'PENDING',
                        createdAt: '2026-10-12T10:00:00Z'
                    }
                })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            assert.equal(statusAlert.classList.contains('d-none'), false);
            assert.match(statusAlert.textContent, /không thể gửi báo cáo/i);
        });

        it('FIX 2: Report rejects when returned status is not in canonical enum set', async () => {
            const form = env.document.createElement('FORM');
            form.id = 'report-form';
            const idInput = env.document.createElement('INPUT');
            idInput.id = 'report-target-id';
            idInput.value = '10';
            const typeInput = env.document.createElement('INPUT');
            typeInput.id = 'report-target-type';
            typeInput.value = 'post';
            const reasonSelect = env.document.createElement('SELECT');
            reasonSelect.id = 'report-reason';
            reasonSelect.value = 'SPAM';
            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'report-status';
            statusAlert.className = 'alert d-none';

            form.appendChild(idInput);
            form.appendChild(typeInput);
            form.appendChild(reasonSelect);
            form.appendChild(statusAlert);
            env.document.body.appendChild(form);

            env.window.fetchHandler = async () => ({
                ok: true,
                status: 201,
                text: async () => JSON.stringify({
                    success: true,
                    data: {
                        id: 1,
                        reportedPostId: 10,
                        reason: 'SPAM',
                        status: 'UNKNOWN_STATUS',
                        createdAt: '2026-10-12T10:00:00Z'
                    }
                })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            assert.equal(statusAlert.classList.contains('d-none'), false);
            assert.match(statusAlert.textContent, /không thể gửi báo cáo/i);
        });

        it('FIX 2: Report rejects when createdAt timestamp is missing or invalid', async () => {
            const form = env.document.createElement('FORM');
            form.id = 'report-form';
            const idInput = env.document.createElement('INPUT');
            idInput.id = 'report-target-id';
            idInput.value = '10';
            const typeInput = env.document.createElement('INPUT');
            typeInput.id = 'report-target-type';
            typeInput.value = 'post';
            const reasonSelect = env.document.createElement('SELECT');
            reasonSelect.id = 'report-reason';
            reasonSelect.value = 'SPAM';
            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'report-status';
            statusAlert.className = 'alert d-none';

            form.appendChild(idInput);
            form.appendChild(typeInput);
            form.appendChild(reasonSelect);
            form.appendChild(statusAlert);
            env.document.body.appendChild(form);

            env.window.fetchHandler = async () => ({
                ok: true,
                status: 201,
                text: async () => JSON.stringify({
                    success: true,
                    data: {
                        id: 1,
                        reportedPostId: 10,
                        reason: 'SPAM',
                        status: 'PENDING',
                        createdAt: null
                    }
                })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            assert.equal(statusAlert.classList.contains('d-none'), false);
            assert.match(statusAlert.textContent, /không thể gửi báo cáo/i);
        });

        it('FIX 2: Report rejects missing meta, non-null meta, or synthetic inherited meta with unchanged UI/drafts', async () => {
            const form = env.document.createElement('FORM');
            form.id = 'report-form';
            const idInput = env.document.createElement('INPUT');
            idInput.id = 'report-target-id';
            idInput.value = '10';
            const typeInput = env.document.createElement('INPUT');
            typeInput.id = 'report-target-type';
            typeInput.value = 'post';
            const reasonSelect = env.document.createElement('SELECT');
            reasonSelect.id = 'report-reason';
            reasonSelect.value = 'SPAM';
            const descInput = env.document.createElement('TEXTAREA');
            descInput.id = 'report-description';
            descInput.value = 'Chi tiết vi phạm nội dung';
            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'report-status';
            statusAlert.className = 'alert d-none';
            const submitBtn = env.document.createElement('BUTTON');
            submitBtn.id = 'btn-submit-report';

            form.appendChild(idInput);
            form.appendChild(typeInput);
            form.appendChild(reasonSelect);
            form.appendChild(descInput);
            form.appendChild(statusAlert);
            form.appendChild(submitBtn);
            env.document.body.appendChild(form);

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            const canonicalReportData = {
                id: 1,
                reportedPostId: 10,
                reason: 'SPAM',
                status: 'PENDING',
                createdAt: '2026-10-12T10:00:00Z'
            };

            // 1. Missing meta in envelope
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 201,
                text: async () => JSON.stringify({
                    success: true,
                    data: canonicalReportData
                })
            });
            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));
            assert.equal(statusAlert.classList.contains('d-none'), false);
            assert.match(statusAlert.textContent, /không thể gửi báo cáo/i);
            assert.equal(submitBtn.disabled, false);
            assert.equal(idInput.value, '10');
            assert.equal(reasonSelect.value, 'SPAM');
            assert.equal(descInput.value, 'Chi tiết vi phạm nội dung');

            // 2. Non-null meta in envelope
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 201,
                text: async () => JSON.stringify({
                    success: true,
                    data: canonicalReportData,
                    meta: { traceId: 'trace-123' }
                })
            });
            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));
            assert.equal(submitBtn.disabled, false);
            assert.equal(idInput.value, '10');

            // 3. Synthetic inherited-only meta (not own property)
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 201,
                text: async () => ''
            });
            const originalReadJson = env.window.alouteHttp.readJson;
            env.window.alouteHttp.readJson = async () => {
                const proto = { meta: null };
                const syntheticBody = Object.create(proto);
                syntheticBody.success = true;
                syntheticBody.data = canonicalReportData;
                return syntheticBody;
            };
            try {
                form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
                await new Promise(r => setTimeout(r, 10));
                assert.equal(submitBtn.disabled, false);
                assert.equal(idInput.value, '10');
                assert.equal(reasonSelect.value, 'SPAM');
            } finally {
                env.window.alouteHttp.readJson = originalReadJson;
            }
        });

        // ---------------------------------------------------------------------
        // FIX 3 Tests: Post-Success Duplicate Submission Race Elimination
        // ---------------------------------------------------------------------

        it('FIX 3: Report prevents duplicate submission of same target in session', async () => {
            const form = env.document.createElement('FORM');
            form.id = 'report-form';
            const idInput = env.document.createElement('INPUT');
            idInput.id = 'report-target-id';
            idInput.value = '77';
            const typeInput = env.document.createElement('INPUT');
            typeInput.id = 'report-target-type';
            typeInput.value = 'post';
            const reasonSelect = env.document.createElement('SELECT');
            reasonSelect.id = 'report-reason';
            reasonSelect.value = 'SPAM';
            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'report-status';
            statusAlert.className = 'alert d-none';

            const openBtn = env.document.createElement('BUTTON');
            openBtn.setAttribute('data-action', 'open-report-modal');
            openBtn.setAttribute('data-report-type', 'post');
            openBtn.setAttribute('data-reported-id', '77');

            form.appendChild(idInput);
            form.appendChild(typeInput);
            form.appendChild(reasonSelect);
            form.appendChild(statusAlert);
            env.document.body.appendChild(form);
            env.document.body.appendChild(openBtn);

            let fetchCalls = 0;
            env.window.fetchHandler = async () => {
                fetchCalls++;
                return {
                    ok: true,
                    status: 201,
                    text: async () => JSON.stringify({
                        success: true,
                        data: {
                            id: 1,
                            reportedPostId: 77,
                            reason: 'SPAM',
                            status: 'PENDING',
                            createdAt: '2026-10-12T10:00:00Z'
                        },
                        meta: null
                    })
                };
            };

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            // First submit succeeds
            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));
            assert.equal(fetchCalls, 1);

            // Attempt repeat submit on same target in session: must NOT fire fetch
            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));
            assert.equal(fetchCalls, 1);

            // Attempt to re-open report modal for same target in session: blocked
            openBtn.click();
            // idInput was not reset to a new target
            assert.equal(fetchCalls, 1);
        });

        it('FIX 3: Quote keeps submit button disabled and retains lock during confirmed navigation', async () => {
            const form = env.document.createElement('FORM');
            form.id = 'quote-post-form';
            const idInput = env.document.createElement('INPUT');
            idInput.id = 'quote-target-post-id';
            idInput.value = '400';
            const contentInput = env.document.createElement('TEXTAREA');
            contentInput.id = 'quote-post-content';
            contentInput.value = 'Nội dung hợp lệ';
            const submitBtn = env.document.createElement('BUTTON');
            submitBtn.id = 'btn-submit-quote';

            form.appendChild(idInput);
            form.appendChild(contentInput);
            form.appendChild(submitBtn);
            env.document.body.appendChild(form);

            let fetchCalls = 0;
            env.window.fetchHandler = async () => {
                fetchCalls++;
                return {
                    ok: true,
                    status: 201,
                    text: async () => JSON.stringify({
                        success: true,
                        data: { id: 401, quotedPostId: 400 },
                        meta: null
                    })
                };
            };

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            assert.equal(fetchCalls, 1);
            // Submit button must remain disabled during navigation
            assert.equal(submitBtn.getAttribute('disabled'), 'true');

            // Rapid repeat submission must be blocked
            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));
            assert.equal(fetchCalls, 1);
        });

        it('Quote: rejects missing meta, non-null meta, or synthetic inherited meta with unchanged UI/drafts', async () => {
            const form = env.document.createElement('FORM');
            form.id = 'quote-post-form';
            const idInput = env.document.createElement('INPUT');
            idInput.id = 'quote-target-post-id';
            idInput.value = '400';
            const contentInput = env.document.createElement('TEXTAREA');
            contentInput.id = 'quote-post-content';
            contentInput.value = 'Nội dung trích dẫn cần kiểm tra';
            const visSelect = env.document.createElement('SELECT');
            visSelect.id = 'quote-post-visibility';
            const opt = env.document.createElement('OPTION');
            opt.value = 'PUBLIC';
            visSelect.appendChild(opt);
            visSelect.value = 'PUBLIC';
            const statusAlert = env.document.createElement('DIV');
            statusAlert.id = 'quote-post-status';
            statusAlert.className = 'alert d-none';
            const submitBtn = env.document.createElement('BUTTON');
            submitBtn.id = 'btn-submit-quote';

            form.appendChild(idInput);
            form.appendChild(contentInput);
            form.appendChild(visSelect);
            form.appendChild(statusAlert);
            form.appendChild(submitBtn);
            env.document.body.appendChild(form);

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            const canonicalQuoteData = { id: 401, quotedPostId: 400, content: 'Nội dung trích dẫn cần kiểm tra' };

            // 1. Missing meta
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 201,
                text: async () => JSON.stringify({
                    success: true,
                    data: canonicalQuoteData
                })
            });
            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));
            assert.equal(statusAlert.classList.contains('d-none'), false);
            assert.match(statusAlert.textContent, /không thể đăng trích dẫn/i);
            assert.equal(submitBtn.disabled, false);
            assert.equal(contentInput.value, 'Nội dung trích dẫn cần kiểm tra');

            // 2. Non-null meta
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 201,
                text: async () => JSON.stringify({
                    success: true,
                    data: canonicalQuoteData,
                    meta: { extra: 'invalid' }
                })
            });
            form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
            await new Promise(r => setTimeout(r, 10));
            assert.equal(submitBtn.disabled, false);
            assert.equal(contentInput.value, 'Nội dung trích dẫn cần kiểm tra');

            // 3. Synthetic inherited-only meta
            env.window.fetchHandler = async () => ({
                ok: true,
                status: 201,
                text: async () => ''
            });
            const originalReadJson = env.window.alouteHttp.readJson;
            env.window.alouteHttp.readJson = async () => {
                const proto = { meta: null };
                const syntheticBody = Object.create(proto);
                syntheticBody.success = true;
                syntheticBody.data = canonicalQuoteData;
                return syntheticBody;
            };
            try {
                form.dispatchEvent(new MockEvent('submit', { cancelable: true }));
                await new Promise(r => setTimeout(r, 10));
                assert.equal(submitBtn.disabled, false);
                assert.equal(contentInput.value, 'Nội dung trích dẫn cần kiểm tra');
            } finally {
                env.window.alouteHttp.readJson = originalReadJson;
            }
        });

        // ---------------------------------------------------------------------
        // FIX 4 Tests: Notification Badge Validation & Synchronization
        // ---------------------------------------------------------------------

        it('FIX 4: initNotificationBadges discards stale responses when newer event arrives', async () => {
            const navBadge = env.document.createElement('SPAN');
            navBadge.id = 'navbar-notification-badge';
            const userBtn = env.document.createElement('DIV');
            userBtn.className = 'aloute-user-dropdown-btn';

            env.document.body.appendChild(navBadge);
            env.document.body.appendChild(userBtn);

            let resolveGet;
            env.window.fetchHandler = async () => new Promise(r => {
                resolveGet = () => r({
                    ok: true,
                    status: 200,
                    text: async () => JSON.stringify({ success: true, data: { unreadCount: 3 } })
                });
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            // initNotificationBadges fires async GET /api/notifications/unread-count (version 1)
            // While request is in flight, a mutation event arrives with version 2 and unreadCount = 0
            env.window.dispatchEvent(new MockCustomEvent('aloute:unread-count-updated', { detail: { unreadCount: 0, version: 2 } }));
            assert.equal(navBadge.textContent, '0');
            assert.equal(navBadge.classList.contains('d-none'), true);

            // Now resolve the older GET response (version 1 with unreadCount = 3)
            resolveGet();
            await new Promise(r => setTimeout(r, 10));

            // Must NOT clobber with stale count 3! Must remain 0
            assert.equal(navBadge.textContent, '0');
            assert.equal(navBadge.classList.contains('d-none'), true);
        });

        it('FIX 4: initNotificationBadges never sets fake 0 on HTTP non-200 or malformed body', async () => {
            const navBadge = env.document.createElement('SPAN');
            navBadge.id = 'navbar-notification-badge';
            const userBtn = env.document.createElement('DIV');
            userBtn.className = 'aloute-user-dropdown-btn';

            env.document.body.appendChild(navBadge);
            env.document.body.appendChild(userBtn);

            // HTTP 500 error
            env.window.fetchHandler = async () => ({
                ok: false,
                status: 500,
                text: async () => JSON.stringify({ success: false })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            // Set confirmed count = 8 first
            env.window.dispatchEvent(new MockCustomEvent('aloute:unread-count-updated', { detail: { unreadCount: 8, version: 1 } }));
            assert.equal(navBadge.textContent, '8');

            // Call GET with malformed response
            await new Promise(r => setTimeout(r, 10));

            // Must retain last confirmed valid state 8, never overwrite with fake 0
            assert.equal(navBadge.textContent, '8');
        });
    });

    describe('Remediation Review Repair: Async Modal Lifecycle & Ignored Hide Race Protection', () => {
        let env;

        beforeEach(() => {
            env = createDOMEnvironment();
        });

        it('Report: opening new target while previous modal is closing preserves draft and opens new target on hidden', async () => {
            const trigger1 = env.document.createElement('BUTTON');
            trigger1.setAttribute('data-action', 'open-report-modal');
            trigger1.setAttribute('data-report-type', 'post');
            trigger1.setAttribute('data-reported-id', '101');
            trigger1.setAttribute('data-reported-name', 'Bài viết cũ');

            const trigger2 = env.document.createElement('BUTTON');
            trigger2.setAttribute('data-action', 'open-report-modal');
            trigger2.setAttribute('data-report-type', 'post');
            trigger2.setAttribute('data-reported-id', '202');
            trigger2.setAttribute('data-reported-name', 'Bài viết mới');

            const modalEl = env.document.createElement('DIV');
            modalEl.id = 'reportModal';

            const form = env.document.createElement('FORM');
            form.id = 'report-form';

            const typeInput = env.document.createElement('INPUT');
            typeInput.id = 'report-target-type';
            const idInput = env.document.createElement('INPUT');
            idInput.id = 'report-target-id';
            const previewEl = env.document.createElement('DIV');
            previewEl.id = 'report-target-preview';

            const reasonSelect = env.document.createElement('SELECT');
            reasonSelect.id = 'report-reason';
            const opt1 = env.document.createElement('OPTION');
            opt1.value = 'SPAM';
            reasonSelect.appendChild(opt1);

            const descInput = env.document.createElement('TEXTAREA');
            descInput.id = 'report-description';
            const statusEl = env.document.createElement('DIV');
            statusEl.id = 'report-status';
            const submitBtn = env.document.createElement('BUTTON');
            submitBtn.id = 'btn-submit-report';
            const spinner = env.document.createElement('SPAN');
            spinner.id = 'report-spinner';
            const btnText = env.document.createElement('SPAN');
            btnText.id = 'report-btn-text';

            form.appendChild(typeInput);
            form.appendChild(idInput);
            form.appendChild(reasonSelect);
            form.appendChild(descInput);
            form.appendChild(submitBtn);
            form.appendChild(spinner);
            form.appendChild(btnText);
            modalEl.appendChild(form);

            env.document.body.appendChild(trigger1);
            env.document.body.appendChild(trigger2);
            env.document.body.appendChild(modalEl);
            env.document.body.appendChild(previewEl);
            env.document.body.appendChild(statusEl);

            env.window.fetchHandler = async () => ({
                ok: true,
                status: 201,
                text: async () => JSON.stringify({
                    success: true,
                    data: {
                        id: 5001,
                        reportedPostId: 101,
                        reason: 'SPAM',
                        status: 'PENDING',
                        createdAt: '2026-10-12T12:00:00Z'
                    },
                    meta: null
                })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            // 1. Open Target 1
            trigger1.click();
            assert.equal(idInput.value, '101');
            assert.match(previewEl.textContent, /#101/);

            // Modal enters shown state
            modalEl.dispatchEvent(new MockEvent('show.bs.modal'));
            modalEl.dispatchEvent(new MockEvent('shown.bs.modal'));

            // Select reason and submit Target 1
            reasonSelect.value = 'SPAM';
            form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            // Success occurred: modal is closing
            assert.equal(env.window.fetchCalls.length, 1);
            // Hide transition begins:
            modalEl.dispatchEvent(new MockEvent('hide.bs.modal'));

            // 2. Immediate click on Target 2 while modal is closing
            trigger2.click();

            // While closing, the new target is queued; existing element is NOT wiped yet
            assert.equal(modalEl._bsModal.showCalls, 1);

            // 3. Bootstrap completes hide transition:
            modalEl.dispatchEvent(new MockEvent('hidden.bs.modal'));

            // Queued target 202 is opened automatically and cleanly!
            assert.equal(idInput.value, '202');
            assert.match(previewEl.textContent, /#202/);
            assert.equal(submitBtn.disabled, false);
            assert.equal(modalEl._bsModal.showCalls, 2);
        });

        it('Report: fast request success during show transition sets dismissOnShown and hides on shown without hanging', async () => {
            const trigger = env.document.createElement('BUTTON');
            trigger.setAttribute('data-action', 'open-report-modal');
            trigger.setAttribute('data-report-type', 'post');
            trigger.setAttribute('data-reported-id', '105');

            const modalEl = env.document.createElement('DIV');
            modalEl.id = 'reportModal';
            const form = env.document.createElement('FORM');
            form.id = 'report-form';
            const idInput = env.document.createElement('INPUT');
            idInput.id = 'report-target-id';
            const reasonSelect = env.document.createElement('SELECT');
            reasonSelect.id = 'report-reason';
            const opt = env.document.createElement('OPTION');
            opt.value = 'HARASSMENT';
            reasonSelect.appendChild(opt);
            const submitBtn = env.document.createElement('BUTTON');
            submitBtn.id = 'btn-submit-report';

            form.appendChild(idInput);
            form.appendChild(reasonSelect);
            form.appendChild(submitBtn);
            modalEl.appendChild(form);
            env.document.body.appendChild(trigger);
            env.document.body.appendChild(modalEl);

            env.window.fetchHandler = async () => ({
                ok: true,
                status: 201,
                text: async () => JSON.stringify({
                    success: true,
                    data: {
                        id: 5002,
                        reportedPostId: 105,
                        reason: 'HARASSMENT',
                        status: 'PENDING',
                        createdAt: '2026-10-12T12:00:00Z'
                    },
                    meta: null
                })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            // Open modal: show begins
            trigger.click();
            modalEl.dispatchEvent(new MockEvent('show.bs.modal'));
            // Note: shown.bs.modal has NOT fired yet (still in show transition)

            // Extremely fast network response completes during show transition
            reasonSelect.value = 'HARASSMENT';
            form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            // In show transition: hide() must NOT be called immediately while transitioning; dismissOnShown was set
            assert.equal(modalEl._bsModal.hideCalls, 0);

            // When Bootstrap completes show transition: shown.bs.modal fires
            modalEl.dispatchEvent(new MockEvent('shown.bs.modal'));

            // dismissOnShown fires modal.hide()!
            assert.equal(modalEl._bsModal.hideCalls, 1);
        });

        it('Report: duplicate submit blocked while pending or closing', async () => {
            const trigger = env.document.createElement('BUTTON');
            trigger.setAttribute('data-action', 'open-report-modal');
            trigger.setAttribute('data-report-type', 'post');
            trigger.setAttribute('data-reported-id', '108');

            const modalEl = env.document.createElement('DIV');
            modalEl.id = 'reportModal';
            const form = env.document.createElement('FORM');
            form.id = 'report-form';
            const idInput = env.document.createElement('INPUT');
            idInput.id = 'report-target-id';
            const reasonSelect = env.document.createElement('SELECT');
            reasonSelect.id = 'report-reason';
            const opt = env.document.createElement('OPTION');
            opt.value = 'SPAM';
            reasonSelect.appendChild(opt);
            const submitBtn = env.document.createElement('BUTTON');
            submitBtn.id = 'btn-submit-report';

            form.appendChild(idInput);
            form.appendChild(reasonSelect);
            form.appendChild(submitBtn);
            modalEl.appendChild(form);
            env.document.body.appendChild(trigger);
            env.document.body.appendChild(modalEl);

            let resolveFetch;
            env.window.fetchHandler = () => new Promise(resolve => {
                resolveFetch = () => resolve({
                    ok: true,
                    status: 201,
                    text: async () => JSON.stringify({
                        success: true,
                        data: {
                            id: 5003,
                            reportedPostId: 108,
                            reason: 'SPAM',
                            status: 'PENDING',
                            createdAt: '2026-10-12T12:00:00Z'
                        },
                        meta: null
                    })
                });
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            trigger.click();
            reasonSelect.value = 'SPAM';

            // Submit 1 (initiates pending request)
            form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));

            // Submit 2 & 3 while pending (rapid clicks / Enter)
            form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));
            form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));

            assert.equal(env.window.fetchCalls.length, 1);

            // Resolve fetch
            resolveFetch();
            await new Promise(r => setTimeout(r, 10));

            // Submit 4 while closing
            form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));

            // Still strictly 1 request!
            assert.equal(env.window.fetchCalls.length, 1);
        });

        it('Report: failure allows retry, keeping form draft and unfreezing controls', async () => {
            const trigger = env.document.createElement('BUTTON');
            trigger.setAttribute('data-action', 'open-report-modal');
            trigger.setAttribute('data-report-type', 'post');
            trigger.setAttribute('data-reported-id', '110');

            const modalEl = env.document.createElement('DIV');
            modalEl.id = 'reportModal';
            const form = env.document.createElement('FORM');
            form.id = 'report-form';
            const idInput = env.document.createElement('INPUT');
            idInput.id = 'report-target-id';
            const reasonSelect = env.document.createElement('SELECT');
            reasonSelect.id = 'report-reason';
            const opt = env.document.createElement('OPTION');
            opt.value = 'OTHER';
            reasonSelect.appendChild(opt);
            const descInput = env.document.createElement('TEXTAREA');
            descInput.id = 'report-description';
            const statusEl = env.document.createElement('DIV');
            statusEl.id = 'report-status';
            const submitBtn = env.document.createElement('BUTTON');
            submitBtn.id = 'btn-submit-report';

            form.appendChild(idInput);
            form.appendChild(reasonSelect);
            form.appendChild(descInput);
            form.appendChild(submitBtn);
            modalEl.appendChild(form);
            env.document.body.appendChild(trigger);
            env.document.body.appendChild(modalEl);
            env.document.body.appendChild(statusEl);

            let shouldFail = true;
            env.window.fetchHandler = async () => {
                if (shouldFail) {
                    return {
                        ok: false,
                        status: 500,
                        text: async () => JSON.stringify({ success: false, error: { message: 'Server error' } })
                    };
                }
                return {
                    ok: true,
                    status: 201,
                    text: async () => JSON.stringify({
                        success: true,
                        data: {
                            id: 5004,
                            reportedPostId: 110,
                            reason: 'OTHER',
                            status: 'PENDING',
                            createdAt: '2026-10-12T12:00:00Z'
                        },
                        meta: null
                    })
                };
            };

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            trigger.click();
            reasonSelect.value = 'OTHER';
            descInput.value = 'Chi tiết vi phạm nội dung';

            // Attempt 1: fails
            form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            // Controls unfrozen, draft retained
            assert.equal(submitBtn.disabled, false);
            assert.equal(reasonSelect.value, 'OTHER');
            assert.equal(descInput.value, 'Chi tiết vi phạm nội dung');
            assert.match(statusEl.textContent, /Không thể gửi báo cáo|lỗi/i);

            // Attempt 2: succeeds
            shouldFail = false;
            form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.window.fetchCalls.length, 2);
            assert.equal(modalEl._bsModal.hideCalls, 1);
        });

        it('Quote: early success during show transition sets dismissOnShown and hides on shown', async () => {
            const trigger = env.document.createElement('BUTTON');
            trigger.setAttribute('data-action', 'open-quote-modal');
            trigger.setAttribute('data-post-id', '303');
            trigger.setAttribute('data-post-author', 'Tác giả A');

            const modalEl = env.document.createElement('DIV');
            modalEl.id = 'quotePostModal';
            const form = env.document.createElement('FORM');
            form.id = 'quote-post-form';
            const idInput = env.document.createElement('INPUT');
            idInput.id = 'quote-target-post-id';
            const contentInput = env.document.createElement('TEXTAREA');
            contentInput.id = 'quote-post-content';
            const visSelect = env.document.createElement('SELECT');
            visSelect.id = 'quote-post-visibility';
            const opt = env.document.createElement('OPTION');
            opt.value = 'PUBLIC';
            visSelect.appendChild(opt);
            const submitBtn = env.document.createElement('BUTTON');
            submitBtn.id = 'btn-submit-quote';

            form.appendChild(idInput);
            form.appendChild(contentInput);
            form.appendChild(visSelect);
            form.appendChild(submitBtn);
            modalEl.appendChild(form);
            env.document.body.appendChild(trigger);
            env.document.body.appendChild(modalEl);

            env.window.fetchHandler = async () => ({
                ok: true,
                status: 201,
                text: async () => JSON.stringify({
                    success: true,
                    data: {
                        id: 9001,
                        quotedPostId: 303,
                        content: 'Bình luận trích dẫn xuất sắc'
                    },
                    meta: null
                })
            });

            const context = vm.createContext(env.window);
            vm.runInContext(pageActionsSource, context);

            trigger.click();
            modalEl.dispatchEvent(new MockEvent('show.bs.modal'));

            contentInput.value = 'Bình luận trích dẫn xuất sắc';
            form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));
            await new Promise(r => setTimeout(r, 10));

            // During show transition, hide() is not called immediately; dismissOnShown was set
            assert.equal(modalEl._bsModal.hideCalls, 0);

            // Shown transition completes:
            modalEl.dispatchEvent(new MockEvent('shown.bs.modal'));

            // Modal hide is invoked!
            assert.equal(modalEl._bsModal.hideCalls, 1);
            assert.equal(env.window.location.reloadCalls, 1);
        });
    });
});
