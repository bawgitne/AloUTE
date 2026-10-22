/**
 * AloUTE — Notifications Feature Real Production Module Tests
 *
 * Exercises src/main/resources/static/js/features/notification/notification.js
 * in conjunction with core/http-client.js and page-actions.js in real layout order.
 *
 * Coverage:
 * - Single-owner initial GET coordination (#notifications-content vs navbar)
 * - Valid unread counts (0, 99, >99, MAX_SAFE_INTEGER)
 * - Table-driven malformed envelope / body / count rejection
 * - Error persistence of last confirmed state (no fake 0)
 * - Exact HTTP 200 vs non-200 and network failure
 * - Stale out-of-order response discard (even when abort signal is ignored)
 * - Mutation coordination (Mark as read, Mark all as read, Delete)
 * - Module idempotency (repeated evaluation before/after DOMContentLoaded)
 * - Desktop & mobile badge parent link aria-labels
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

const httpClientSource = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/static/js/core/http-client.js'),
    'utf-8'
);

const pageActionsSource = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/static/js/page-actions.js'),
    'utf-8'
);

const notificationSource = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/static/js/features/notification/notification.js'),
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
        this._removed = false;
    }

    // A detached node reports isConnected === false, like a real DOM element.
    get isConnected() {
        return !this._removed;
    }


    focus() {
        this._focused = true;
    }

    get innerHTML() {
        return this._innerHTML;
    }

    set innerHTML(html) {
        this._innerHTML = String(html || '');
        this.children = [];
        if (!html || !String(html).trim()) {
            this.textContent = '';
            return;
        }
        const stack = [this];
        const regex = /<(\/)?([a-zA-Z0-9_-]+)([^>]*)>|([^<]+)/g;
        let m;
        while ((m = regex.exec(html)) !== null) {
            if (m[4]) {
                const cur = stack[stack.length - 1];
                cur.textContent = (cur.textContent || '') + m[4];
            } else if (m[1]) {
                if (stack.length > 1) stack.pop();
            } else {
                const tagName = m[2];
                const rawAttrs = m[3] || '';
                const isSelf = ['img', 'input', 'br', 'hr'].includes(tagName.toLowerCase()) || rawAttrs.trim().endsWith('/');
                const el = new MockElement(tagName, '', this.ownerDocument);
                el.parentElement = stack[stack.length - 1];
                stack[stack.length - 1].children.push(el);

                const idMatch = rawAttrs.match(/id=["']([^"']*)["']/);
                if (idMatch) el.id = idMatch[1];
                const classMatch = rawAttrs.match(/class=["']([^"']*)["']/);
                if (classMatch) el.className = classMatch[1];

                const attrRegex = /([a-zA-Z0-9_-]+)(?:=["']([^"']*)["'])?/g;
                let a;
                while ((a = attrRegex.exec(rawAttrs)) !== null) {
                    const name = a[1];
                    if (name !== 'id' && name !== 'class' && name !== '/') {
                        el.setAttribute(name, a[2] !== undefined ? a[2] : '');
                    }
                }

                if (!isSelf) {
                    stack.push(el);
                }
            }
        }
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

    remove() {
        this._removed = true;
        if (this.parentElement) {
            this.parentElement.children = this.parentElement.children.filter(c => c !== this);
            this.parentElement = null;
        }
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
    constructor(initialReadyState = 'complete') {
        this.cookie = 'XSRF-TOKEN=test-csrf-token';
        this.readyState = initialReadyState;
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

class MockAbortSignal {
    constructor() {
        this.aborted = false;
        this._listeners = [];
    }

    addEventListener(type, fn) {
        if (type === 'abort') this._listeners.push(fn);
    }
}

class MockAbortController {
    constructor() {
        this.signal = new MockAbortSignal();
    }

    abort() {
        this.signal.aborted = true;
        for (const fn of this.signal._listeners) fn();
    }
}

function createDOMEnvironment(initialReadyState = 'complete') {
    const doc = new MockDocument(initialReadyState);
    const windowListeners = new Map();

    const win = {
        document: doc,
        location: { href: 'http://localhost:8080/notifications' },
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
                text: async () => JSON.stringify({ success: true, data: { unreadCount: 0 } })
            };
        },
        CustomEvent: MockCustomEvent,
        Event: MockEvent,
        AbortController: MockAbortController,
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
        setTimeout: (cb, ms) => {
            return setTimeout(cb, ms || 0);
        },
        clearTimeout: (id) => clearTimeout(id),
        console: { log: () => {}, warn: () => {}, error: () => {} }
    };

    win.window = win;
    return { window: win, document: doc };
}

function createNotificationElements(doc) {
    // 1. Badges in Navbar & Sidebars
    const navLink = doc.createElement('A');
    navLink.id = 'navbar-notif-link';
    const navBadge = doc.createElement('SPAN');
    navBadge.id = 'navbar-notification-badge';
    navLink.appendChild(navBadge);

    const desktopLink = doc.createElement('A');
    desktopLink.id = 'desktop-notif-link';
    const desktopBadge = doc.createElement('SPAN');
    desktopBadge.className = 'sidebar-notification-badge';
    desktopLink.appendChild(desktopBadge);

    const mobileLink = doc.createElement('A');
    mobileLink.id = 'mobile-notif-link';
    const mobileBadge = doc.createElement('SPAN');
    mobileBadge.setAttribute('data-badge', 'notification');
    mobileLink.appendChild(mobileBadge);

    doc.body.appendChild(navLink);
    doc.body.appendChild(desktopLink);
    doc.body.appendChild(mobileLink);

    // 2. Notification Page Elements
    const root = doc.createElement('DIV');
    root.id = 'notifications-content';

    const list = doc.createElement('DIV');
    list.id = 'notifications-list';
    const loading = doc.createElement('DIV');
    loading.id = 'notifications-loading';
    const error = doc.createElement('DIV');
    error.id = 'notifications-error';
    const errorMsg = doc.createElement('DIV');
    errorMsg.id = 'notifications-error-msg';
    error.appendChild(errorMsg);
    const empty = doc.createElement('DIV');
    empty.id = 'notifications-empty';
    const loadMoreWrap = doc.createElement('DIV');
    loadMoreWrap.id = 'notifications-load-more-wrap';
    const btnLoadMore = doc.createElement('BUTTON');
    btnLoadMore.id = 'btn-load-more-notifications';
    loadMoreWrap.appendChild(btnLoadMore);
    const btnRetry = doc.createElement('BUTTON');
    btnRetry.id = 'btn-retry-notifications';
    const badgeUnread = doc.createElement('SPAN');
    badgeUnread.id = 'unread-count-badge';
    const btnMarkAllRead = doc.createElement('BUTTON');
    btnMarkAllRead.id = 'btn-mark-all-read';
    const btnRefresh = doc.createElement('BUTTON');
    btnRefresh.id = 'btn-refresh-notifications';
    const tabsContainer = doc.createElement('UL');
    tabsContainer.id = 'notificationFilterTabs';

    const feedbackArea = doc.createElement('DIV');
    feedbackArea.id = 'notification-feedback-area';
    const feedbackText = doc.createElement('DIV');
    feedbackText.id = 'notification-feedback-text';
    feedbackArea.appendChild(feedbackText);

    root.appendChild(list);
    root.appendChild(loading);
    root.appendChild(error);
    root.appendChild(empty);
    root.appendChild(loadMoreWrap);
    root.appendChild(btnRetry);
    root.appendChild(badgeUnread);
    root.appendChild(btnMarkAllRead);
    root.appendChild(btnRefresh);
    root.appendChild(tabsContainer);
    root.appendChild(feedbackArea);

    doc.body.appendChild(root);

    return {
        navBadge,
        navLink,
        desktopBadge,
        desktopLink,
        mobileBadge,
        mobileLink,
        root,
        list,
        loading,
        error,
        errorMsg,
        empty,
        btnLoadMore,
        btnRetry,
        badgeUnread,
        btnMarkAllRead,
        btnRefresh,
        tabsContainer,
        feedbackArea,
        feedbackText
    };
}

function runScriptsInLayoutOrder(context, options = { includeNotification: true }) {
    vm.runInContext(httpClientSource, context);
    vm.runInContext(pageActionsSource, context);
    if (options.includeNotification) {
        vm.runInContext(notificationSource, context);
    }
}

// -----------------------------------------------------------------------------
// Test Suites
// -----------------------------------------------------------------------------

describe('AloUTE Real Notification Module Integration (notification.js)', () => {
    let env;

    beforeEach(() => {
        env = createDOMEnvironment();
    });

    // -------------------------------------------------------------------------
    // Suite 1: Single Owner Coordination
    // -------------------------------------------------------------------------
    describe('Single-Owner Initial GET Coordination', () => {
        it('When #notifications-content is present, exactly ONE unread-count GET is sent (owned by notification.js)', async () => {
            const els = createNotificationElements(env.document);

            env.window.fetchHandler = async (url) => {
                if (url.includes('/unread-count')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: { unreadCount: 3 } })
                    };
                }
                if (url.includes('/api/notifications?')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: [], meta: { page: 0, hasNext: false } })
                    };
                }
                return { ok: true, status: 200, text: async () => JSON.stringify({ success: true }) };
            };

            const context = vm.createContext(env.window);
            runScriptsInLayoutOrder(context);

            await new Promise(r => setTimeout(r, 20));

            // Check calls: unread-count must be requested exactly ONCE, not twice!
            const unreadCalls = env.window.fetchCalls.filter(c => c.url.includes('/api/notifications/unread-count'));
            assert.equal(unreadCalls.length, 1);
            assert.equal(els.navBadge.textContent, '3');
            assert.equal(els.desktopBadge.textContent, '3');
            assert.equal(els.mobileBadge.textContent, '3');
        });

        it('When #notifications-content is absent on authenticated page, page-actions owns the single unread-count GET', async () => {
            // General page: only navbar & sidebar badges, user dropdown present, NO #notifications-content
            const navLink = env.document.createElement('A');
            const navBadge = env.document.createElement('SPAN');
            navBadge.id = 'navbar-notification-badge';
            navLink.appendChild(navBadge);

            const userBtn = env.document.createElement('DIV');
            userBtn.className = 'aloute-user-dropdown-btn';

            env.document.body.appendChild(navLink);
            env.document.body.appendChild(userBtn);

            env.window.fetchHandler = async (url) => {
                if (url.includes('/unread-count')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: { unreadCount: 5 } })
                    };
                }
                return { ok: true, status: 200, text: async () => JSON.stringify({ success: true }) };
            };

            const context = vm.createContext(env.window);
            // Run all scripts in layout order
            runScriptsInLayoutOrder(context);

            await new Promise(r => setTimeout(r, 20));

            const unreadCalls = env.window.fetchCalls.filter(c => c.url.includes('/api/notifications/unread-count'));
            assert.equal(unreadCalls.length, 1);
            assert.equal(navBadge.textContent, '5');
        });
    });

    // -------------------------------------------------------------------------
    // Suite 2: Valid Unread Counts & ARIA Phrasing
    // -------------------------------------------------------------------------
    describe('Valid Unread Counts (0, 99, >99, MAX_SAFE_INTEGER)', () => {
        const testCases = [
            { count: 0, expectedText: '0', isHidden: true, badgeLabel: 'Không có thông báo mới', parentLabel: 'Thông báo' },
            { count: 99, expectedText: '99', isHidden: false, badgeLabel: '99 thông báo chưa đọc', parentLabel: 'Thông báo (99 chưa đọc)' },
            { count: 100, expectedText: '99+', isHidden: false, badgeLabel: 'Hơn 99 thông báo chưa đọc', parentLabel: 'Thông báo (99+ chưa đọc)' },
            { count: Number.MAX_SAFE_INTEGER, expectedText: '99+', isHidden: false, badgeLabel: 'Hơn 99 thông báo chưa đọc', parentLabel: 'Thông báo (99+ chưa đọc)' }
        ];

        for (const tc of testCases) {
            it(`Renders count ${tc.count} as "${tc.expectedText}" with exact accessible labels`, async () => {
                const els = createNotificationElements(env.document);

                env.window.fetchHandler = async (url) => {
                    if (url.includes('/unread-count')) {
                        return {
                            ok: true,
                            status: 200,
                            text: async () => JSON.stringify({ success: true, data: { unreadCount: tc.count } })
                        };
                    }
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: [], meta: { page: 0, hasNext: false } })
                    };
                };

                const context = vm.createContext(env.window);
                runScriptsInLayoutOrder(context);

                await new Promise(r => setTimeout(r, 20));

                assert.equal(els.navBadge.textContent, tc.expectedText);
                assert.equal(els.desktopBadge.textContent, tc.expectedText);
                assert.equal(els.mobileBadge.textContent, tc.expectedText);
                assert.equal(els.badgeUnread.textContent, tc.expectedText);

                assert.equal(els.navBadge.classList.contains('d-none'), tc.isHidden);
                assert.equal(els.desktopBadge.classList.contains('d-none'), tc.isHidden);
                assert.equal(els.mobileBadge.classList.contains('d-none'), tc.isHidden);
                assert.equal(els.badgeUnread.classList.contains('d-none'), tc.isHidden);

                assert.equal(els.navBadge.getAttribute('aria-label'), tc.badgeLabel);
                assert.equal(els.desktopBadge.getAttribute('aria-label'), tc.badgeLabel);
                assert.equal(els.mobileBadge.getAttribute('aria-label'), tc.badgeLabel);

                assert.equal(els.navLink.getAttribute('aria-label'), tc.parentLabel);
                assert.equal(els.desktopLink.getAttribute('aria-label'), tc.parentLabel);
                assert.equal(els.mobileLink.getAttribute('aria-label'), tc.parentLabel);
            });
        }
    });

    // -------------------------------------------------------------------------
    // Suite 3: Table-Driven Malformed Envelopes & Error Persistence
    // -------------------------------------------------------------------------
    describe('Malformed Envelope Rejection & Error Persistence', () => {
        const malformedPayloads = [
            { desc: 'success is boolean false', payload: { success: false, data: { unreadCount: 10 } } },
            { desc: 'success is string "false"', payload: { success: "false", data: { unreadCount: 10 } } },
            { desc: 'data is null', payload: { success: true, data: null } },
            { desc: 'data is missing unreadCount', payload: { success: true, data: {} } },
            { desc: 'unreadCount is null', payload: { success: true, data: { unreadCount: null } } },
            { desc: 'unreadCount is string "12"', payload: { success: true, data: { unreadCount: "12" } } },
            { desc: 'unreadCount is negative (-1)', payload: { success: true, data: { unreadCount: -1 } } },
            { desc: 'unreadCount is fraction (4.5)', payload: { success: true, data: { unreadCount: 4.5 } } },
            { desc: 'unreadCount is boolean true', payload: { success: true, data: { unreadCount: true } } },
            { desc: 'unreadCount is boolean false', payload: { success: true, data: { unreadCount: false } } },
            { desc: 'unreadCount is array [10]', payload: { success: true, data: { unreadCount: [10] } } },
            { desc: 'unreadCount is Infinity', payload: { success: true, data: { unreadCount: Infinity } } },
            { desc: 'unreadCount is NaN', payload: { success: true, data: { unreadCount: NaN } } },
            { desc: 'unreadCount exceeds MAX_SAFE_INTEGER', payload: { success: true, data: { unreadCount: Number.MAX_SAFE_INTEGER + 2 } } }
        ];

        for (const tc of malformedPayloads) {
            it(`Discards malformed response (${tc.desc}) and preserves confirmed state`, async () => {
                const els = createNotificationElements(env.document);

                let currentUnreadPayload = { success: true, data: { unreadCount: 7 } };

                env.window.fetchHandler = async (url) => {
                    if (url.includes('/unread-count')) {
                        return {
                            ok: true,
                            status: 200,
                            text: async () => JSON.stringify(currentUnreadPayload)
                        };
                    }
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: [], meta: { page: 0, hasNext: false } })
                    };
                };

                const context = vm.createContext(env.window);
                runScriptsInLayoutOrder(context);

                await new Promise(r => setTimeout(r, 20));

                // Confirmed initial state: 7
                assert.equal(els.navBadge.textContent, '7');
                assert.equal(els.desktopBadge.textContent, '7');

                // Deliver malformed payload on refresh
                currentUnreadPayload = tc.payload;
                els.btnRefresh.click();

                await new Promise(r => setTimeout(r, 20));

                // Must NEVER overwrite with fake 0 or corrupted text! Preserves 7
                assert.equal(els.navBadge.textContent, '7');
                assert.equal(els.desktopBadge.textContent, '7');
                assert.equal(els.navBadge.classList.contains('d-none'), false);
            });
        }

        it('Network failure preserves confirmed unread state', async () => {
            const els = createNotificationElements(env.document);

            let networkOk = true;
            env.window.fetchHandler = async (url) => {
                if (url.includes('/unread-count')) {
                    if (!networkOk) {
                        throw new Error('Network failure');
                    }
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: { unreadCount: 15 } })
                    };
                }
                return {
                    ok: true,
                    status: 200,
                    text: async () => JSON.stringify({ success: true, data: [], meta: { page: 0, hasNext: false } })
                };
            };

            const context = vm.createContext(env.window);
            runScriptsInLayoutOrder(context);

            await new Promise(r => setTimeout(r, 20));
            assert.equal(els.navBadge.textContent, '15');

            // Network drops
            networkOk = false;
            els.btnRefresh.click();

            await new Promise(r => setTimeout(r, 20));

            // Preserves confirmed 15 without blowing up
            assert.equal(els.navBadge.textContent, '15');
        });

        it('HTTP 201 Created is rejected for unread-count GET (strictly requires exact 200 OK)', async () => {
            const els = createNotificationElements(env.document);

            let deliverStatus = 200;
            let countVal = 8;

            env.window.fetchHandler = async (url) => {
                if (url.includes('/unread-count')) {
                    return {
                        ok: true,
                        status: deliverStatus,
                        text: async () => JSON.stringify({ success: true, data: { unreadCount: countVal } })
                    };
                }
                return {
                    ok: true,
                    status: 200,
                    text: async () => JSON.stringify({ success: true, data: [], meta: { page: 0, hasNext: false } })
                };
            };

            const context = vm.createContext(env.window);
            runScriptsInLayoutOrder(context);

            await new Promise(r => setTimeout(r, 20));
            assert.equal(els.navBadge.textContent, '8');

            // Send 201 with count 99
            deliverStatus = 201;
            countVal = 99;
            els.btnRefresh.click();

            await new Promise(r => setTimeout(r, 20));

            // Must reject 201 and retain 8!
            assert.equal(els.navBadge.textContent, '8');
        });
    });

    // -------------------------------------------------------------------------
    // Suite 4: Stale Out-of-Order Discard
    // -------------------------------------------------------------------------
    describe('Stale Out-of-Order Response Discard', () => {
        it('Discards delayed older response even when abort signal is ignored by network', async () => {
            const els = createNotificationElements(env.document);

            let resolveFirstFetch;
            let callCount = 0;

            env.window.fetchHandler = async (url) => {
                if (url.includes('/unread-count')) {
                    callCount++;
                    if (callCount === 1) {
                        // First fetch delayed
                        return new Promise(resolve => {
                            resolveFirstFetch = () => resolve({
                                ok: true,
                                status: 200,
                                text: async () => JSON.stringify({ success: true, data: { unreadCount: 100 } })
                            });
                        });
                    }
                    if (callCount === 2) {
                        // Second fetch completes immediately with count = 2
                        return {
                            ok: true,
                            status: 200,
                            text: async () => JSON.stringify({ success: true, data: { unreadCount: 2 } })
                        };
                    }
                }
                return {
                    ok: true,
                    status: 200,
                    text: async () => JSON.stringify({ success: true, data: [], meta: { page: 0, hasNext: false } })
                };
            };

            const context = vm.createContext(env.window);
            runScriptsInLayoutOrder(context);

            // Trigger second fetch before first completes
            await new Promise(r => setTimeout(r, 10));
            els.btnRefresh.click();

            await new Promise(r => setTimeout(r, 20));

            // Count is 2 from second fetch
            assert.equal(els.navBadge.textContent, '2');

            // First delayed fetch returns now with stale count 100
            resolveFirstFetch();
            await new Promise(r => setTimeout(r, 20));

            // Must NOT overwrite with stale 100! Remains 2
            assert.equal(els.navBadge.textContent, '2');
        });
    });

    // -------------------------------------------------------------------------
    // Suite 5: Mutation Coordination (Read, Read All, Delete)
    // -------------------------------------------------------------------------
    describe('Mutation Coordination with Authoritative Count Refresh', () => {
        it('Mark As Read: decrements unread count optimistically and refreshes authoritatively', async () => {
            const els = createNotificationElements(env.document);

            let unreadCount = 3;
            env.window.fetchHandler = async (url, opts) => {
                if (url.includes('/unread-count')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: { unreadCount: unreadCount } })
                    };
                }
                if (url.includes('/api/notifications?')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [
                                {
                                    id: 101,
                                    type: 'LIKE',
                                    isRead: false,
                                    createdAt: '2026-10-12T10:00:00Z',
                                    actor: { displayName: 'User A', username: 'usera' }
                                }
                            ],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                if (url.includes('/api/notifications/101/read') && opts.method === 'PATCH') {
                    unreadCount = 2;
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true })
                    };
                }
                return { ok: true, status: 200, text: async () => JSON.stringify({ success: true }) };
            };

            const context = vm.createContext(env.window);
            runScriptsInLayoutOrder(context);

            await new Promise(r => setTimeout(r, 30));
            assert.equal(els.navBadge.textContent, '3');

            // Find item and mark-read button
            const item = els.list.querySelector('.aloute-notification-item');
            assert.ok(item);
            assert.equal(item.classList.contains('unread'), true);

            const markBtn = item.querySelector('.btn-mark-read');
            assert.ok(markBtn);

            // Click mark as read
            markBtn.click();
            await new Promise(r => setTimeout(r, 30));

            // Optimistic + authoritative update converged to 2
            assert.equal(els.navBadge.textContent, '2');
            assert.equal(item.classList.contains('unread'), false);
        });

        it('Mark All As Read: sets count to 0, hides badges, and updates items', async () => {
            const els = createNotificationElements(env.document);

            let unreadCount = 4;
            env.window.fetchHandler = async (url, opts) => {
                if (url.includes('/unread-count')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: { unreadCount: unreadCount } })
                    };
                }
                if (url.includes('/api/notifications?')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [
                                { id: 102, type: 'REPLY', isRead: false, createdAt: '2026-10-12T10:00:00Z' }
                            ],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                if (url.includes('/api/notifications/read-all') && opts.method === 'PATCH') {
                    unreadCount = 0;
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true })
                    };
                }
                return { ok: true, status: 200, text: async () => JSON.stringify({ success: true }) };
            };

            const context = vm.createContext(env.window);
            runScriptsInLayoutOrder(context);

            await new Promise(r => setTimeout(r, 30));
            assert.equal(els.navBadge.textContent, '4');

            // Click mark all read
            els.btnMarkAllRead.click();
            await new Promise(r => setTimeout(r, 30));

            // Badges hidden and text 0
            assert.equal(els.navBadge.textContent, '0');
            assert.equal(els.navBadge.classList.contains('d-none'), true);
            assert.equal(els.desktopBadge.textContent, '0');
            assert.equal(els.desktopBadge.classList.contains('d-none'), true);
        });

        it('Delete: decrements unread count when unread item is deleted', async () => {
            const els = createNotificationElements(env.document);

            let unreadCount = 1;
            env.window.fetchHandler = async (url, opts) => {
                if (url.includes('/unread-count')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: { unreadCount: unreadCount } })
                    };
                }
                if (url.includes('/api/notifications?')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [
                                { id: 103, type: 'FOLLOW', isRead: false, createdAt: '2026-10-12T10:00:00Z' }
                            ],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                if (url.includes('/api/notifications/103') && opts.method === 'DELETE') {
                    unreadCount = 0;
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true })
                    };
                }
                return { ok: true, status: 200, text: async () => JSON.stringify({ success: true }) };
            };

            const context = vm.createContext(env.window);
            runScriptsInLayoutOrder(context);

            await new Promise(r => setTimeout(r, 30));
            assert.equal(els.navBadge.textContent, '1');

            const item = els.list.querySelector('.aloute-notification-item');
            assert.ok(item);
            const delBtn = item.querySelector('.btn-delete-notif');
            assert.ok(delBtn);

            delBtn.click();
            await new Promise(r => setTimeout(r, 250));

            assert.equal(els.navBadge.textContent, '0');
            assert.equal(els.navBadge.classList.contains('d-none'), true);
        });
    });

    // -------------------------------------------------------------------------
    // Suite 5b: Rapid Mutation Guard & User-Facing Error Text
    // -------------------------------------------------------------------------
    describe('Rapid Mutation Guard & User-Facing Error Text', () => {
        it('Rapid repeated clicks on Delete issue exactly ONE DELETE and show no spurious error', async () => {
            const els = createNotificationElements(env.document);

            let deleteCalls = 0;
            env.window.fetchHandler = async (url, opts) => {
                if (url.includes('/unread-count')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: { unreadCount: 1 } })
                    };
                }
                if (url.includes('/api/notifications?')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [{ id: 501, type: 'FOLLOW', isRead: false, createdAt: '2026-10-22T10:00:00Z' }],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                if (url.includes('/api/notifications/501') && opts.method === 'DELETE') {
                    deleteCalls++;
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true })
                    };
                }
                return { ok: true, status: 200, text: async () => JSON.stringify({ success: true }) };
            };

            const context = vm.createContext(env.window);
            runScriptsInLayoutOrder(context);
            await new Promise(r => setTimeout(r, 30));

            const item = els.list.querySelector('.aloute-notification-item');
            assert.ok(item, 'notification item must be rendered');
            const delBtn = item.querySelector('.btn-delete-notif');
            assert.ok(delBtn, 'delete button must exist');

            // Three rapid clicks on the same control.
            delBtn.click();
            delBtn.click();
            delBtn.click();
            await new Promise(r => setTimeout(r, 60));

            assert.equal(deleteCalls, 1, 'rapid repeated clicks must issue exactly ONE DELETE request');

            // The redundant request used to fail and surface a raw error after a successful delete.
            const feedbackText = els.feedbackText.textContent || '';
            assert.ok(
                !/Report was modified by another transaction|another transaction/i.test(feedbackText),
                `no spurious backend error may be shown after a successful delete, got: ${feedbackText}`
            );
            assert.ok(
                !/alert-danger/.test(els.feedbackText.className || ''),
                `the feedback must not be an error after a successful delete, got class: ${els.feedbackText.className}`
            );
            assert.equal(feedbackText.trim(), 'Đã xóa thông báo.', 'the success feedback must be shown');
        });

        it('A failed delete re-enables the button so the user can retry', async () => {
            const els = createNotificationElements(env.document);

            let failNext = true;
            env.window.fetchHandler = async (url, opts) => {
                if (url.includes('/unread-count')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: { unreadCount: 1 } })
                    };
                }
                if (url.includes('/api/notifications?')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [{ id: 601, type: 'FOLLOW', isRead: false, createdAt: '2026-10-22T10:00:00Z' }],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                if (url.includes('/api/notifications/601') && opts.method === 'DELETE') {
                    if (failNext) {
                        failNext = false;
                        return {
                            ok: false,
                            status: 500,
                            text: async () => JSON.stringify({ success: false, error: { message: 'Lỗi máy chủ' } })
                        };
                    }
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true })
                    };
                }
                return { ok: true, status: 200, text: async () => JSON.stringify({ success: true }) };
            };

            const context = vm.createContext(env.window);
            runScriptsInLayoutOrder(context);
            await new Promise(r => setTimeout(r, 30));

            const item = els.list.querySelector('.aloute-notification-item');
            assert.ok(item, 'notification item must be rendered');
            const delBtn = item.querySelector('.btn-delete-notif');
            assert.ok(delBtn, 'delete button must exist');

            delBtn.click();
            await new Promise(r => setTimeout(r, 60));

            assert.equal(delBtn.disabled, false, 'a failed delete must re-enable the button for a retry');
            assert.equal(
                /alert-danger/.test(els.feedbackText.className || ''),
                true,
                'a failed delete must show an error feedback'
            );
        });

        it('Rapid repeated clicks on Mark As Read issue exactly ONE PATCH', async () => {
            const els = createNotificationElements(env.document);

            let patchCalls = 0;
            env.window.fetchHandler = async (url, opts) => {
                if (url.includes('/unread-count')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: { unreadCount: 2 } })
                    };
                }
                if (url.includes('/api/notifications?')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [{ id: 502, type: 'POST_LIKE', isRead: false, createdAt: '2026-10-22T10:00:00Z' }],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                if (url.includes('/api/notifications/502/read') && opts.method === 'PATCH') {
                    patchCalls++;
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true })
                    };
                }
                return { ok: true, status: 200, text: async () => JSON.stringify({ success: true }) };
            };

            const context = vm.createContext(env.window);
            runScriptsInLayoutOrder(context);
            await new Promise(r => setTimeout(r, 30));

            const item = els.list.querySelector('.aloute-notification-item');
            assert.ok(item, 'notification item must be rendered');
            const readBtn = item.querySelector('.btn-mark-read');
            assert.ok(readBtn, 'mark-as-read button must exist');

            readBtn.click();
            readBtn.click();
            readBtn.click();
            await new Promise(r => setTimeout(r, 60));

            assert.equal(patchCalls, 1, 'rapid repeated clicks must issue exactly ONE PATCH request');
        });

        it('A transport failure shows a user-facing message, never the raw browser exception text', async () => {
            const els = createNotificationElements(env.document);

            let failList = false;
            env.window.fetchHandler = async (url) => {
                if (url.includes('/unread-count')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: { unreadCount: 4 } })
                    };
                }
                if (url.includes('/api/notifications?')) {
                    if (failList) {
                        // The shape a browser produces for a network failure.
                        const err = new TypeError('NetworkError when attempting to fetch resource.');
                        throw err;
                    }
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: [], meta: { page: 0, hasNext: false } })
                    };
                }
                return { ok: true, status: 200, text: async () => JSON.stringify({ success: true }) };
            };

            const context = vm.createContext(env.window);
            runScriptsInLayoutOrder(context);
            await new Promise(r => setTimeout(r, 30));

            failList = true;
            els.btnRefresh.click();
            await new Promise(r => setTimeout(r, 40));

            const errorShown = els.error.classList.contains('d-none') === false;
            assert.ok(errorShown, 'the error state must be shown on a transport failure');

            const msg = els.errorMsg.textContent || '';
            assert.ok(msg.length > 0, 'the error state must carry a message');
            assert.ok(
                !/NetworkError|Failed to fetch|when attempting to fetch resource/i.test(msg),
                `the raw browser exception text must not reach the user, got: ${msg}`
            );
            assert.ok(
                /máy chủ|Không thể/i.test(msg),
                `the error message must stay in the user-facing Vietnamese wording, got: ${msg}`
            );
        });
    });

    // -------------------------------------------------------------------------
    // Suite 6: Module Idempotency
    // -------------------------------------------------------------------------
    describe('Module Idempotency', () => {
        it('Repeated evaluation of notification.js attaches listeners and fetches initial count exactly once', async () => {
            const els = createNotificationElements(env.document);

            env.window.fetchHandler = async (url) => {
                if (url.includes('/unread-count')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: { unreadCount: 1 } })
                    };
                }
                return {
                    ok: true,
                    status: 200,
                    text: async () => JSON.stringify({ success: true, data: [], meta: { page: 0, hasNext: false } })
                };
            };

            const context = vm.createContext(env.window);
            // First evaluation
            runScriptsInLayoutOrder(context);
            // Second evaluation of notification script
            vm.runInContext(notificationSource, context);
            // Third evaluation of notification script
            vm.runInContext(notificationSource, context);

            await new Promise(r => setTimeout(r, 30));

            // Count fetch must be strictly 1!
            const unreadCalls = env.window.fetchCalls.filter(c => c.url.includes('/api/notifications/unread-count'));
            assert.equal(unreadCalls.length, 1);
        });

        it('Production IIFE beginning loading: repeated evaluation before DOMContentLoaded, single DOMContentLoaded, repeated evaluation after; exactly ONE initial GET and ONE handler set', async () => {
            const loadingEnv = createDOMEnvironment('loading');
            assert.equal(loadingEnv.document.readyState, 'loading');

            const els = createNotificationElements(loadingEnv.document);

            let patchCount = 0;
            loadingEnv.window.fetchHandler = async (url, opts = {}) => {
                if (url.includes('/unread-count')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: { unreadCount: 5 }, meta: null })
                    };
                }
                if (url.includes('/api/notifications/read-all') && opts.method === 'PATCH') {
                    patchCount++;
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: { markedAllAsRead: true }, meta: null })
                    };
                }
                return {
                    ok: true,
                    status: 200,
                    text: async () => JSON.stringify({ success: true, data: [], meta: { page: 0, hasNext: false } })
                };
            };

            const context = vm.createContext(loadingEnv.window);

            // 1. First evaluation in layout order BEFORE DOMContentLoaded (readyState === 'loading')
            runScriptsInLayoutOrder(context);

            // 2. Repeat module evaluation BEFORE single DOMContentLoaded
            vm.runInContext(notificationSource, context);

            await new Promise(r => setTimeout(r, 10));

            // During loading, init() has not run yet: 0 unread-count GETs
            const loadingUnreadCalls = loadingEnv.window.fetchCalls.filter(c => c.url.includes('/api/notifications/unread-count'));
            assert.equal(loadingUnreadCalls.length, 0);

            // 3. Change readyState and dispatch single DOMContentLoaded
            loadingEnv.document.readyState = 'interactive';
            loadingEnv.document.dispatchEvent(new MockEvent('DOMContentLoaded'));

            await new Promise(r => setTimeout(r, 20));

            // Exactly ONE initial unread-count GET after DOMContentLoaded
            const postDomUnreadCalls = loadingEnv.window.fetchCalls.filter(c => c.url.includes('/api/notifications/unread-count'));
            assert.equal(postDomUnreadCalls.length, 1);

            // 4. Repeat module evaluation AFTER DOMContentLoaded
            vm.runInContext(notificationSource, context);

            await new Promise(r => setTimeout(r, 20));

            // Still strictly ONE initial unread-count GET
            const totalUnreadCalls = loadingEnv.window.fetchCalls.filter(c => c.url.includes('/api/notifications/unread-count'));
            assert.equal(totalUnreadCalls.length, 1);

            // 5. Verify ONE handler set: clicking read-all triggers exactly ONE PATCH request
            els.btnMarkAllRead.click();
            await new Promise(r => setTimeout(r, 20));

            const patchCalls = loadingEnv.window.fetchCalls.filter(c => c.url.includes('/api/notifications/read-all') && c.options.method === 'PATCH');
            assert.equal(patchCalls.length, 1);
            assert.equal(patchCount, 1);
        });
    });
});
