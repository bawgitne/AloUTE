/**
 * AloUTE — Post Interactions (Like, Bookmark, Repost) Automated Regression Tests
 *
 * Built-in Node test suite (node:test + assert/strict + vm).
 * Evaluates production IIFEs (page-actions.js, http-client.js, post-like.js)
 * loaded in actual layout order against a lightweight DOM/window/fetch harness.
 */

import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

// -----------------------------------------------------------------------------
// Path Resolution Relative to Test File
// -----------------------------------------------------------------------------

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '../..');

const pageActionsSource = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/static/js/page-actions.js'),
    'utf-8'
);

const httpClientSource = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/static/js/core/http-client.js'),
    'utf-8'
);

const postLikeSource = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/static/js/features/post/post-like.js'),
    'utf-8'
);

// -----------------------------------------------------------------------------
// Lightweight DOM Harness
// -----------------------------------------------------------------------------

function matchesCompound(el, sel) {
    if (!el || !sel) return false;
    let remaining = sel.trim();
    if (!remaining) return false;

    // Optional tag name
    const tagMatch = remaining.match(/^([a-zA-Z0-9_-]+)/);
    if (tagMatch) {
        if (el.tagName.toLowerCase() !== tagMatch[1].toLowerCase()) {
            return false;
        }
        remaining = remaining.slice(tagMatch[1].length);
    }

    // Process qualifiers: #id, .class, [attr] / [attr=val]
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
        this.children = [];
        this.parentElement = null;
        this._listeners = new Map();
        this.style = {};
        this.textContent = '';
        this._innerHTML = '';
        this.disabled = false;
        this.hidden = false;
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
            contains: (name) => {
                return this.className.split(/\s+/).filter(Boolean).includes(name);
            },
            toggle: (name, force) => {
                const shouldAdd = force !== undefined ? Boolean(force) : !this.classList.contains(name);
                if (shouldAdd) {
                    this.classList.add(name);
                    return true;
                } else {
                    this.classList.remove(name);
                    return false;
                }
            }
        };
    }

    get dataset() {
        const ds = {};
        for (const [k, v] of this._attributes.entries()) {
            if (k.startsWith('data-')) {
                const camel = k.slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase());
                ds[camel] = v;
            }
        }
        return ds;
    }

    getAttribute(name) {
        if (name === 'id') return this.id;
        if (name === 'class') return this.className;
        return this._attributes.get(name) ?? null;
    }

    setAttribute(name, val) {
        const sVal = String(val);
        if (name === 'id') this.id = sVal;
        else if (name === 'class') this.className = sVal;
        else this._attributes.set(name, sVal);
    }

    removeAttribute(name) {
        if (name === 'id') this.id = '';
        else if (name === 'class') this.className = '';
        else this._attributes.delete(name);
    }

    hasAttribute(name) {
        if (name === 'id') return Boolean(this.id);
        if (name === 'class') return Boolean(this.className);
        return this._attributes.has(name);
    }

    addEventListener(type, fn) {
        if (!this._listeners.has(type)) {
            this._listeners.set(type, []);
        }
        this._listeners.get(type).push(fn);
    }

    removeEventListener(type, fn) {
        if (this._listeners.has(type)) {
            const arr = this._listeners.get(type).filter(f => f !== fn);
            this._listeners.set(type, arr);
        }
    }

    dispatchEvent(event) {
        const ev = typeof event === 'string' ? { type: event, target: this, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; } } : event;
        if (!ev.target) ev.target = this;
        if (!ev.preventDefault) ev.preventDefault = () => { ev.defaultPrevented = true; };

        let curr = this;
        while (curr) {
            const handlers = curr._listeners.get(ev.type) || [];
            for (const h of handlers) {
                h.call(curr, ev);
            }
            if (ev.propagationStopped) break;
            if (!curr.parentElement) {
                if (curr.ownerDocument) {
                    curr.ownerDocument.dispatchEvent(ev);
                }
                break;
            }
            curr = curr.parentElement;
        }
    }

    click() {
        this.dispatchEvent({
            type: 'click',
            target: this,
            defaultPrevented: false,
            preventDefault() { this.defaultPrevented = true; }
        });
    }

    appendChild(child) {
        if (child.parentElement) {
            child.remove();
        }
        child.parentElement = this;
        if (!child.ownerDocument && this.ownerDocument) {
            child.ownerDocument = this.ownerDocument;
        }
        this.children.push(child);
        return child;
    }

    remove() {
        if (this.parentElement) {
            const idx = this.parentElement.children.indexOf(this);
            if (idx !== -1) {
                this.parentElement.children.splice(idx, 1);
            }
            this.parentElement = null;
        }
    }

    get innerHTML() {
        return this._innerHTML;
    }

    set innerHTML(html) {
        this._innerHTML = String(html);
        this.children = [];
        this.textContent = String(html);
    }

    querySelector(selector) {
        return querySelector(this, selector);
    }

    querySelectorAll(selector) {
        return querySelectorAll(this, selector);
    }

    closest(selector) {
        let curr = this;
        while (curr) {
            if (matchesSelector(curr, selector)) return curr;
            curr = curr.parentElement;
        }
        return null;
    }
}

class MockDocument {
    constructor() {
        this.cookie = '';
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
        return querySelector(this.body, selector);
    }

    querySelectorAll(selector) {
        return querySelectorAll(this.body, selector);
    }

    addEventListener(type, fn) {
        if (!this._listeners.has(type)) {
            this._listeners.set(type, []);
        }
        this._listeners.get(type).push(fn);
    }

    removeEventListener(type, fn) {
        if (this._listeners.has(type)) {
            const arr = this._listeners.get(type).filter(f => f !== fn);
            this._listeners.set(type, arr);
        }
    }

    dispatchEvent(event) {
        const ev = typeof event === 'string' ? { type: event, target: this, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; } } : event;
        if (!ev.target) ev.target = this;
        const handlers = this._listeners.get(ev.type) || [];
        for (const h of handlers) {
            h.call(this, ev);
        }
    }
}

// -----------------------------------------------------------------------------
// Controlled Fetch Harness
// -----------------------------------------------------------------------------

class FetchHarness {
    constructor() {
        this.calls = [];
        this.handlers = [];
    }

    on(matchFn, responseOrFn) {
        this.handlers.push({ matchFn, responseOrFn });
    }

    fetch(url, options = {}) {
        const callRecord = {
            url,
            method: options.method || 'GET',
            credentials: options.credentials,
            headers: options.headers || {},
            body: options.body
        };
        this.calls.push(callRecord);

        for (const handler of this.handlers) {
            if (handler.matchFn(url, callRecord)) {
                let res = handler.responseOrFn;
                if (typeof res === 'function') {
                    res = res(url, callRecord);
                }
                return Promise.resolve(res).then(resolved => {
                    const r = resolved || {};
                    return {
                        ok: r.ok !== undefined ? r.ok : (r.status >= 200 && r.status < 300),
                        status: r.status !== undefined ? r.status : 200,
                        text: async () => typeof r.body === 'string' ? r.body : JSON.stringify(r.body ?? {}),
                        json: async () => typeof r.body === 'string' ? (r.body ? JSON.parse(r.body) : null) : (r.body ?? {})
                    };
                });
            }
        }

        // Default 404 response
        return Promise.resolve({
            ok: false,
            status: 404,
            text: async () => JSON.stringify({ success: false, error: { message: 'Not found' } }),
            json: async () => ({ success: false, error: { message: 'Not found' } })
        });
    }
}

// -----------------------------------------------------------------------------
// Test Environment Setup Helper
// -----------------------------------------------------------------------------

function createEnvironment(options = {}) {
    const document = new MockDocument();
    document.cookie = options.cookie || 'XSRF-TOKEN=test-csrf-token-12345';

    const fetchHarness = new FetchHarness();

    const window = {
        document,
        fetch: fetchHarness.fetch.bind(fetchHarness),
        console,
        setTimeout,
        clearTimeout
    };

    const sandbox = {
        window,
        document,
        fetch: window.fetch,
        console,
        setTimeout,
        clearTimeout,
        Object,
        Array,
        Set,
        Map,
        Number,
        String,
        Boolean,
        Promise,
        decodeURIComponent,
        encodeURIComponent
    };

    vm.createContext(sandbox);

    if (!options.omitHttpClient) {
        vm.runInContext(pageActionsSource, sandbox);
        vm.runInContext(httpClientSource, sandbox);
        vm.runInContext(postLikeSource, sandbox);
    } else {
        // Test missing alouteHttp client: run page-actions and post-like without http-client
        vm.runInContext(pageActionsSource, sandbox);
        vm.runInContext(postLikeSource, sandbox);
    }

    return {
        window,
        document,
        fetchHarness,
        sandbox
    };
}

function createPostButton({ action = 'like', postId = '1001', active = false, count = 0, hasCount = true }, ownerDoc = null) {
    const btn = new MockElement('BUTTON', '', ownerDoc);
    btn.setAttribute('type', 'button');
    btn.setAttribute('data-action', action);
    btn.setAttribute('data-post-id', postId);
    btn.className = `btn btn-link interaction-btn interaction-btn-${action}`;

    if (active) {
        btn.classList.add('is-active');
        btn.setAttribute('aria-pressed', 'true');
    } else {
        btn.setAttribute('aria-pressed', 'false');
    }

    const icon = new MockElement('I', '', ownerDoc);
    if (action === 'like') {
        icon.className = active ? 'bi bi-heart-fill' : 'bi bi-heart';
    } else if (action === 'bookmark') {
        icon.className = active ? 'bi bi-bookmark-fill' : 'bi bi-bookmark';
    } else if (action === 'repost') {
        icon.className = 'bi bi-arrow-repeat';
    }
    btn.appendChild(icon);

    if (hasCount) {
        const countSpan = new MockElement('SPAN', '', ownerDoc);
        countSpan.className = 'interaction-count count';
        countSpan.textContent = String(count);
        btn.appendChild(countSpan);
    }

    return btn;
}

// -----------------------------------------------------------------------------
// Test Suites
// -----------------------------------------------------------------------------

describe('AloUTE Post Interactions REST UI (post-like.js & core/http-client.js)', () => {

    describe('Suite 1: Canonical Success Flows (Like, Bookmark, Repost)', () => {

        it('Like: sends PUT /api/posts/{id}/likes/me with canonical envelope, sets is-active, toggles icon, increments counter', async () => {
            const env = createEnvironment();
            const btn = createPostButton({ action: 'like', postId: '201', active: false, count: 5 }, env.document);
            env.document.body.appendChild(btn);

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/201/likes/me' && req.method === 'PUT',
                {
                    status: 200,
                    body: {
                        success: true,
                        data: { postId: 201, active: true },
                        meta: null
                    }
                }
            );

            btn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.fetchHarness.calls.length, 1);
            const call = env.fetchHarness.calls[0];
            assert.equal(call.method, 'PUT');
            assert.equal(call.url, '/api/posts/201/likes/me');
            assert.equal(call.credentials, 'include');
            assert.equal(call.headers['X-XSRF-TOKEN'], 'test-csrf-token-12345');
            assert.equal(call.headers['Accept'], 'application/json');

            assert.equal(btn.classList.contains('is-active'), true);
            assert.equal(btn.getAttribute('aria-pressed'), 'true');
            assert.equal(btn.querySelector('i').className, 'bi bi-heart-fill');
            assert.equal(btn.querySelector('.count').textContent, '6');
        });

        it('Unlike: sends DELETE /api/posts/{id}/likes/me, handles 204 No Content without body parsing, removes is-active, decrements counter', async () => {
            const env = createEnvironment();
            const btn = createPostButton({ action: 'like', postId: '201', active: true, count: 6 }, env.document);
            env.document.body.appendChild(btn);

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/201/likes/me' && req.method === 'DELETE',
                { status: 204, body: '' }
            );

            btn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.fetchHarness.calls.length, 1);
            assert.equal(env.fetchHarness.calls[0].method, 'DELETE');
            assert.equal(btn.classList.contains('is-active'), false);
            assert.equal(btn.getAttribute('aria-pressed'), 'false');
            assert.equal(btn.querySelector('i').className, 'bi bi-heart');
            assert.equal(btn.querySelector('.count').textContent, '5');
        });

        it('Bookmark: sends PUT /api/posts/{id}/bookmarks/me with canonical envelope, sets is-active without injecting counter', async () => {
            const env = createEnvironment();
            const btn = createPostButton({ action: 'bookmark', postId: '202', active: false, hasCount: false }, env.document);
            env.document.body.appendChild(btn);

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/202/bookmarks/me' && req.method === 'PUT',
                {
                    status: 200,
                    body: {
                        success: true,
                        data: { postId: 202, active: true },
                        meta: null
                    }
                }
            );

            btn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.fetchHarness.calls.length, 1);
            assert.equal(btn.classList.contains('is-active'), true);
            assert.equal(btn.getAttribute('aria-pressed'), 'true');
            assert.equal(btn.querySelector('i').className, 'bi bi-bookmark-fill');
            assert.equal(btn.querySelector('.count'), null, 'Bookmark must NOT have a counter span');
        });

        it('Unbookmark: sends DELETE /api/posts/{id}/bookmarks/me, handles 204, resets icon to bi-bookmark', async () => {
            const env = createEnvironment();
            const btn = createPostButton({ action: 'bookmark', postId: '202', active: true, hasCount: false }, env.document);
            env.document.body.appendChild(btn);

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/202/bookmarks/me' && req.method === 'DELETE',
                { status: 204, body: '' }
            );

            btn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.fetchHarness.calls.length, 1);
            assert.equal(btn.classList.contains('is-active'), false);
            assert.equal(btn.getAttribute('aria-pressed'), 'false');
            assert.equal(btn.querySelector('i').className, 'bi bi-bookmark');
        });

        it('Repost: sends PUT /api/posts/{id}/reposts/me with canonical envelope, sets is-active and increments counter', async () => {
            const env = createEnvironment();
            const btn = createPostButton({ action: 'repost', postId: '203', active: false, count: 2 }, env.document);
            env.document.body.appendChild(btn);

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/203/reposts/me' && req.method === 'PUT',
                {
                    status: 200,
                    body: {
                        success: true,
                        data: { postId: 203, active: true },
                        meta: null
                    }
                }
            );

            btn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.fetchHarness.calls.length, 1);
            assert.equal(btn.classList.contains('is-active'), true);
            assert.equal(btn.getAttribute('aria-pressed'), 'true');
            assert.equal(btn.querySelector('.count').textContent, '3');
        });

        it('Unrepost: sends DELETE /api/posts/{id}/reposts/me, handles 204, removes is-active, decrements counter', async () => {
            const env = createEnvironment();
            const btn = createPostButton({ action: 'repost', postId: '203', active: true, count: 3 }, env.document);
            env.document.body.appendChild(btn);

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/203/reposts/me' && req.method === 'DELETE',
                { status: 204, body: '' }
            );

            btn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.fetchHarness.calls.length, 1);
            assert.equal(btn.classList.contains('is-active'), false);
            assert.equal(btn.getAttribute('aria-pressed'), 'false');
            assert.equal(btn.querySelector('.count').textContent, '2');
        });
    });

    describe('Suite 2: Response Shapes & Counter Edge Cases', () => {

        it('supports actual backend PostResponse structure ({id, viewerState, likeCount, repostCount}) for authoritative count sync', async () => {
            const env = createEnvironment();
            const btn = createPostButton({ action: 'like', postId: '301', active: false, count: 0 }, env.document);
            env.document.body.appendChild(btn);

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/301/likes/me',
                {
                    status: 200,
                    body: {
                        success: true,
                        data: {
                            id: 301,
                            viewerState: { liked: true, bookmarked: false, reposted: false },
                            likeCount: 42,
                            repostCount: 0
                        },
                        meta: null
                    }
                }
            );

            btn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(btn.classList.contains('is-active'), true);
            assert.equal(btn.querySelector('.count').textContent, '42');
        });

        it('supports documented PostInteraction structure ({postId, active}) cleanly', async () => {
            const env = createEnvironment();
            const btn = createPostButton({ action: 'repost', postId: '302', active: false, count: 10 }, env.document);
            env.document.body.appendChild(btn);

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/302/reposts/me',
                {
                    status: 200,
                    body: {
                        success: true,
                        data: { postId: 302, active: true },
                        meta: null
                    }
                }
            );

            btn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(btn.classList.contains('is-active'), true);
            assert.equal(btn.querySelector('.count').textContent, '11');
        });

        it('disagreement repair: when canonical active:false disagrees with optimistic PUT and no authoritative count, preserves prior count (parent repro count 5 became 6 inactive)', async () => {
            const env = createEnvironment();
            const btn = createPostButton({ action: 'like', postId: '303', active: false, count: 5 }, env.document);
            env.document.body.appendChild(btn);

            // Server returns 200 with canonical envelope, but active: false without authoritative likeCount
            env.fetchHarness.on(
                (url, req) => url === '/api/posts/303/likes/me',
                {
                    status: 200,
                    body: {
                        success: true,
                        data: { postId: 303, active: false },
                        meta: null
                    }
                }
            );

            btn.click();
            await new Promise(r => setTimeout(r, 10));

            // Must NOT become 6 while inactive! Must remain 5!
            assert.equal(btn.classList.contains('is-active'), false);
            assert.equal(btn.getAttribute('aria-pressed'), 'false');
            assert.equal(btn.querySelector('.count').textContent, '5', 'Count must remain 5, not optimistic 6');
        });

        it('counter decrement never results in negative numbers (clamps at 0)', async () => {
            const env = createEnvironment();
            const btn = createPostButton({ action: 'like', postId: '304', active: true, count: 0 }, env.document);
            env.document.body.appendChild(btn);

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/304/likes/me' && req.method === 'DELETE',
                { status: 204, body: '' }
            );

            btn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(btn.classList.contains('is-active'), false);
            assert.equal(btn.querySelector('.count').textContent, '0');
        });

        it('unexpected 204 on PUT request is REJECTED and rolled back, never silently turning active into false', async () => {
            const env = createEnvironment();
            const btn = createPostButton({ action: 'like', postId: '305', active: false, count: 7 }, env.document);
            env.document.body.appendChild(btn);

            // Server erroneously returns 204 for a PUT request
            env.fetchHarness.on(
                (url, req) => url === '/api/posts/305/likes/me' && req.method === 'PUT',
                { status: 204, body: '' }
            );

            btn.click();
            await new Promise(r => setTimeout(r, 10));

            // Must rollback and announce error, not turn inactive silently
            assert.equal(btn.classList.contains('is-active'), false);
            assert.equal(btn.querySelector('.count').textContent, '7');
            const notice = env.document.getElementById('aloute-toast-notice');
            assert.ok(notice);
            assert.match(notice.textContent, /thao tác|thử lại/i);
        });
    });

    describe('Suite 3: Rejection of Malformed, Non-JSON, Noncanonical & Missing Identity Responses', () => {

        it('rejects invalid HTML / non-JSON 200: rolls back and announces clean error', async () => {
            const env = createEnvironment();
            const btn = createPostButton({ action: 'like', postId: '401', active: false, count: 10 }, env.document);
            env.document.body.appendChild(btn);

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/401/likes/me',
                { status: 200, body: '<html><body>502 Bad Gateway</body></html>' }
            );

            btn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(btn.classList.contains('is-active'), false);
            assert.equal(btn.querySelector('.count').textContent, '10');
            const notice = env.document.getElementById('aloute-toast-notice');
            assert.ok(notice);
            assert.match(notice.textContent, /thao tác|thử lại/i);
        });

        it('rejects raw / noncanonical 200 without ApiResponse envelope: rolls back and announces clean error', async () => {
            const env = createEnvironment();
            const btn = createPostButton({ action: 'like', postId: '402', active: false, count: 2 }, env.document);
            env.document.body.appendChild(btn);

            // Raw response missing success and data wrappers
            env.fetchHarness.on(
                (url, req) => url === '/api/posts/402/likes/me',
                { status: 200, body: { id: 402, liked: true } }
            );

            btn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(btn.classList.contains('is-active'), false);
            assert.equal(btn.querySelector('.count').textContent, '2');
            const notice = env.document.getElementById('aloute-toast-notice');
            assert.ok(notice);
            assert.match(notice.textContent, /thao tác|thử lại/i);
        });

        it('rejects HTTP 200 with success: false: rolls back and announces clean error', async () => {
            const env = createEnvironment();
            const btn = createPostButton({ action: 'like', postId: '403', active: false, count: 3 }, env.document);
            env.document.body.appendChild(btn);

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/403/likes/me',
                {
                    status: 200,
                    body: {
                        success: false,
                        error: { code: 'INVALID_STATE', message: 'Not allowed' }
                    }
                }
            );

            btn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(btn.classList.contains('is-active'), false);
            assert.equal(btn.querySelector('.count').textContent, '3');
            const notice = env.document.getElementById('aloute-toast-notice');
            assert.ok(notice);
            assert.match(notice.textContent, /thao tác|thử lại/i);
        });

        it('rejects HTTP 200 with missing post identity in payload: rolls back and announces clean error', async () => {
            const env = createEnvironment();
            const btn = createPostButton({ action: 'repost', postId: '404', active: false, count: 1 }, env.document);
            env.document.body.appendChild(btn);

            // Payload data has active: true but neither id nor postId
            env.fetchHarness.on(
                (url, req) => url === '/api/posts/404/reposts/me',
                {
                    status: 200,
                    body: {
                        success: true,
                        data: { active: true },
                        meta: null
                    }
                }
            );

            btn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(btn.classList.contains('is-active'), false);
            assert.equal(btn.querySelector('.count').textContent, '1');
            const notice = env.document.getElementById('aloute-toast-notice');
            assert.ok(notice);
            assert.match(notice.textContent, /thao tác|thử lại/i);
        });

        it('rejects HTTP 200 with mismatched post identity: rolls back and announces clean error', async () => {
            const env = createEnvironment();
            const btn = createPostButton({ action: 'repost', postId: '405', active: false, count: 1 }, env.document);
            env.document.body.appendChild(btn);

            // Server mistakenly returns data for post 999 instead of 405
            env.fetchHarness.on(
                (url, req) => url === '/api/posts/405/reposts/me',
                {
                    status: 200,
                    body: {
                        success: true,
                        data: { postId: 999, active: true },
                        meta: null
                    }
                }
            );

            btn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(btn.classList.contains('is-active'), false);
            assert.equal(btn.querySelector('.count').textContent, '1');
            const notice = env.document.getElementById('aloute-toast-notice');
            assert.ok(notice);
            assert.match(notice.textContent, /thao tác|thử lại/i);
        });

        it('rejects HTTP 200 with missing / invalid per-action boolean: rolls back and announces clean error', async () => {
            const env = createEnvironment();
            const btn = createPostButton({ action: 'bookmark', postId: '406', active: false, hasCount: false }, env.document);
            env.document.body.appendChild(btn);

            // Payload has viewerState but bookmarked is missing
            env.fetchHarness.on(
                (url, req) => url === '/api/posts/406/bookmarks/me',
                {
                    status: 200,
                    body: {
                        success: true,
                        data: {
                            id: 406,
                            viewerState: { liked: true }
                        },
                        meta: null
                    }
                }
            );

            btn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(btn.classList.contains('is-active'), false);
            const notice = env.document.getElementById('aloute-toast-notice');
            assert.ok(notice);
            assert.match(notice.textContent, /thao tác|thử lại/i);
        });

        it('missing window.alouteHttp client triggers rollback and safe notice without direct fetch fallback', async () => {
            const env = createEnvironment({ omitHttpClient: true });
            const btn = createPostButton({ action: 'like', postId: '407', active: false, count: 0 }, env.document);
            env.document.body.appendChild(btn);

            btn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.fetchHarness.calls.length, 0, 'No fetch calls should be made if client missing');
            assert.equal(btn.classList.contains('is-active'), false);
            const notice = env.document.getElementById('aloute-toast-notice');
            assert.ok(notice);
            assert.match(notice.textContent, /thao tác|thử lại/i);
        });
    });

    describe('Suite 4: Table-Driven Error Rollback, Busy-Unlock & Retry (401, 403, 404, 500, Network Rejection)', () => {

        const errorScenarios = [
            {
                name: 'HTTP 401 Unauthorized',
                status: 401,
                body: { success: false, error: { message: 'Unauthorized' } },
                expectedNotice: /đăng nhập/i
            },
            {
                name: 'HTTP 403 Forbidden',
                status: 403,
                body: { success: false, error: { message: 'Forbidden' } },
                expectedNotice: /quyền/i
            },
            {
                name: 'HTTP 404 Not Found',
                status: 404,
                body: { success: false, error: { message: 'Post not found' } },
                expectedNotice: /không tồn tại/i
            },
            {
                name: 'HTTP 500 Server Error',
                status: 500,
                body: { success: false, error: { message: 'Internal Server Error' } },
                expectedNotice: /thao tác|thử lại/i
            },
            {
                name: 'Network Failure (Fetch Rejection)',
                networkError: true,
                expectedNotice: /thao tác|thử lại/i
            }
        ];

        for (const scenario of errorScenarios) {
            it(`Activation failure (${scenario.name}): rolls back, unlocks busy/disabled, preserves count, and allows retry`, async () => {
                const env = createEnvironment();
                const btn = createPostButton({ action: 'like', postId: '501', active: false, count: 12 }, env.document);
                env.document.body.appendChild(btn);

                let attempts = 0;
                env.fetchHarness.on(
                    (url, req) => url === '/api/posts/501/likes/me' && req.method === 'PUT',
                    () => {
                        attempts++;
                        if (attempts === 1) {
                            if (scenario.networkError) {
                                return Promise.reject(new Error('Connection failed'));
                            }
                            return { status: scenario.status, body: scenario.body };
                        }
                        return { status: 200, body: { success: true, data: { postId: 501, active: true }, meta: null } };
                    }
                );

                // Attempt 1: Fails
                btn.click();
                await new Promise(r => setTimeout(r, 10));

                assert.equal(btn.classList.contains('is-active'), false);
                assert.equal(btn.getAttribute('aria-pressed'), 'false');
                assert.equal(btn.getAttribute('aria-busy'), null, 'aria-busy must be cleared');
                assert.equal(btn.disabled, false, 'Button must be unlocked (disabled=false)');
                assert.equal(btn.querySelector('.count').textContent, '12');
                assert.equal(btn.querySelector('i').className, 'bi bi-heart');

                const notice = env.document.getElementById('aloute-toast-notice');
                assert.ok(notice);
                assert.match(notice.textContent, scenario.expectedNotice);

                // Attempt 2: Retry succeeds
                btn.click();
                await new Promise(r => setTimeout(r, 10));

                assert.equal(attempts, 2);
                assert.equal(btn.classList.contains('is-active'), true);
                assert.equal(btn.querySelector('.count').textContent, '13');
                assert.equal(btn.querySelector('i').className, 'bi bi-heart-fill');
            });

            it(`Deactivation failure (${scenario.name}): rolls back to active, unlocks busy, and allows retry`, async () => {
                const env = createEnvironment();
                const btn = createPostButton({ action: 'like', postId: '502', active: true, count: 13 }, env.document);
                env.document.body.appendChild(btn);

                let attempts = 0;
                env.fetchHarness.on(
                    (url, req) => url === '/api/posts/502/likes/me' && req.method === 'DELETE',
                    () => {
                        attempts++;
                        if (attempts === 1) {
                            if (scenario.networkError) {
                                return Promise.reject(new Error('Connection failed'));
                            }
                            return { status: scenario.status, body: scenario.body };
                        }
                        return { status: 204, body: '' };
                    }
                );

                // Attempt 1: Fails
                btn.click();
                await new Promise(r => setTimeout(r, 10));

                assert.equal(btn.classList.contains('is-active'), true);
                assert.equal(btn.getAttribute('aria-pressed'), 'true');
                assert.equal(btn.getAttribute('aria-busy'), null);
                assert.equal(btn.disabled, false);
                assert.equal(btn.querySelector('.count').textContent, '13');
                assert.equal(btn.querySelector('i').className, 'bi bi-heart-fill');

                // Attempt 2: Retry succeeds
                btn.click();
                await new Promise(r => setTimeout(r, 10));

                assert.equal(attempts, 2);
                assert.equal(btn.classList.contains('is-active'), false);
                assert.equal(btn.querySelector('.count').textContent, '12');
                assert.equal(btn.querySelector('i').className, 'bi bi-heart');
            });
        }
    });

    describe('Suite 5: Concurrency, In-Flight Deduplication & Deferred DOM Re-Querying', () => {

        it('drops rapid repeated clicks on same button while mutation is in-flight', async () => {
            const env = createEnvironment();
            const btn = createPostButton({ action: 'like', postId: '601', active: false, count: 1 }, env.document);
            env.document.body.appendChild(btn);

            let resolveRequest;
            env.fetchHarness.on(
                (url, req) => url === '/api/posts/601/likes/me',
                () => new Promise(resolve => { resolveRequest = resolve; })
            );

            // Click 1
            btn.click();
            assert.equal(env.fetchHarness.calls.length, 1);
            assert.equal(btn.getAttribute('aria-busy'), 'true');

            // Rapid click 2
            btn.click();
            assert.equal(env.fetchHarness.calls.length, 1, 'Second click must be dropped while in flight');

            resolveRequest({
                status: 200,
                body: { success: true, data: { postId: 601, active: true }, meta: null }
            });
            await new Promise(r => setTimeout(r, 10));

            assert.equal(btn.getAttribute('aria-busy'), null);
            assert.equal(btn.disabled, false);
            assert.equal(btn.classList.contains('is-active'), true);
            assert.equal(btn.querySelector('.count').textContent, '2');
        });

        it('clicking a different duplicate instance of the same post while pending is suppressed by same-key lock', async () => {
            const env = createEnvironment();
            const btnA = createPostButton({ action: 'like', postId: '602', active: false, count: 5 }, env.document);
            const btnB = createPostButton({ action: 'like', postId: '602', active: false, count: 5 }, env.document);
            env.document.body.appendChild(btnA);
            env.document.body.appendChild(btnB);

            let resolveRequest;
            env.fetchHarness.on(
                (url, req) => url === '/api/posts/602/likes/me',
                () => new Promise(resolve => { resolveRequest = resolve; })
            );

            // Click instance A
            btnA.click();
            assert.equal(env.fetchHarness.calls.length, 1);

            // Click instance B while pending: must be suppressed by inFlight lock
            btnB.click();
            assert.equal(env.fetchHarness.calls.length, 1, 'Click on duplicate instance B must be suppressed');

            resolveRequest({
                status: 200,
                body: { success: true, data: { postId: 602, active: true }, meta: null }
            });
            await new Promise(r => setTimeout(r, 10));

            assert.equal(btnA.classList.contains('is-active'), true);
            assert.equal(btnB.classList.contains('is-active'), true);
            assert.equal(btnA.querySelector('.count').textContent, '6');
            assert.equal(btnB.querySelector('.count').textContent, '6');
        });

        it('late-added duplicate instance click cannot send second request while pending; all instances converge on resolve', async () => {
            const env = createEnvironment();
            const btnEarly = createPostButton({ action: 'like', postId: '603', active: false, count: 8 }, env.document);
            env.document.body.appendChild(btnEarly);

            let resolveRequest;
            env.fetchHarness.on(
                (url, req) => url === '/api/posts/603/likes/me',
                () => new Promise(resolve => { resolveRequest = resolve; })
            );

            btnEarly.click();
            assert.equal(env.fetchHarness.calls.length, 1);

            // Button late is dynamically added while request is pending
            const btnLate = createPostButton({ action: 'like', postId: '603', active: false, count: 8 }, env.document);
            env.document.body.appendChild(btnLate);

            // Clicking btnLate while request is pending cannot send 2nd request
            btnLate.click();
            assert.equal(env.fetchHarness.calls.length, 1, 'Late-added duplicate click cannot send second request');

            // Complete response
            resolveRequest({
                status: 200,
                body: {
                    success: true,
                    data: { id: 603, viewerState: { liked: true }, likeCount: 9 },
                    meta: null
                }
            });
            await new Promise(r => setTimeout(r, 10));

            assert.equal(btnEarly.classList.contains('is-active'), true);
            assert.equal(btnLate.classList.contains('is-active'), true);
            assert.equal(btnEarly.querySelector('.count').textContent, '9');
            assert.equal(btnLate.querySelector('.count').textContent, '9');
            assert.equal(btnEarly.disabled, false);
            assert.equal(btnLate.disabled, false);
        });

        it('concurrent independent mutations on like, bookmark, repost on same post execute without cross-action clobbering', async () => {
            const env = createEnvironment();
            const likeBtn = createPostButton({ action: 'like', postId: '604', active: false, count: 0 }, env.document);
            const bookmarkBtn = createPostButton({ action: 'bookmark', postId: '604', active: false, hasCount: false }, env.document);
            const repostBtn = createPostButton({ action: 'repost', postId: '604', active: false, count: 0 }, env.document);
            env.document.body.appendChild(likeBtn);
            env.document.body.appendChild(bookmarkBtn);
            env.document.body.appendChild(repostBtn);

            let resolveLike, resolveBookmark, resolveRepost;
            env.fetchHarness.on(
                (url, req) => url === '/api/posts/604/likes/me',
                () => new Promise(resolve => { resolveLike = resolve; })
            );
            env.fetchHarness.on(
                (url, req) => url === '/api/posts/604/bookmarks/me',
                () => new Promise(resolve => { resolveBookmark = resolve; })
            );
            env.fetchHarness.on(
                (url, req) => url === '/api/posts/604/reposts/me',
                () => new Promise(resolve => { resolveRepost = resolve; })
            );

            likeBtn.click();
            bookmarkBtn.click();
            repostBtn.click();

            assert.equal(env.fetchHarness.calls.length, 3);

            // Resolve out of order: Bookmark, then Repost, then Like
            resolveBookmark({
                status: 200,
                body: { success: true, data: { postId: 604, active: true }, meta: null }
            });
            await new Promise(r => setTimeout(r, 10));
            assert.equal(bookmarkBtn.classList.contains('is-active'), true);

            resolveRepost({
                status: 200,
                body: { success: true, data: { postId: 604, active: true }, meta: null }
            });
            await new Promise(r => setTimeout(r, 10));
            assert.equal(repostBtn.classList.contains('is-active'), true);
            assert.equal(repostBtn.querySelector('.count').textContent, '1');

            resolveLike({
                status: 200,
                body: { success: true, data: { postId: 604, active: true }, meta: null }
            });
            await new Promise(r => setTimeout(r, 10));
            assert.equal(likeBtn.classList.contains('is-active'), true);
            assert.equal(likeBtn.querySelector('.count').textContent, '1');

            // Verify no cross-action clobbering
            assert.equal(bookmarkBtn.classList.contains('is-active'), true);
            assert.equal(repostBtn.classList.contains('is-active'), true);
        });

        it('idempotent script re-evaluation: re-running scripts does not attach duplicate handlers or send multiple requests on single click', async () => {
            const env = createEnvironment();
            // Re-evaluate scripts in same sandbox
            vm.runInContext(httpClientSource, env.sandbox);
            vm.runInContext(postLikeSource, env.sandbox);

            const btn = createPostButton({ action: 'like', postId: '605', active: false, count: 0 }, env.document);
            env.document.body.appendChild(btn);

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/605/likes/me',
                { status: 200, body: { success: true, data: { postId: 605, active: true }, meta: null } }
            );

            btn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.fetchHarness.calls.length, 1, 'Exactly one request must be dispatched on single click');
        });
    });

    describe('Suite 6: Placeholder Bypass Proof in Actual Layout Order', () => {

        it('all 3 actions with page-actions loaded in layout order send exactly 1 request and trigger NO notice on success', async () => {
            const env = createEnvironment();
            const likeBtn = createPostButton({ action: 'like', postId: '701', active: false, count: 0 }, env.document);
            const bookmarkBtn = createPostButton({ action: 'bookmark', postId: '701', active: false, hasCount: false }, env.document);
            const repostBtn = createPostButton({ action: 'repost', postId: '701', active: false, count: 0 }, env.document);
            env.document.body.appendChild(likeBtn);
            env.document.body.appendChild(bookmarkBtn);
            env.document.body.appendChild(repostBtn);

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/701/likes/me',
                { status: 200, body: { success: true, data: { postId: 701, active: true }, meta: null } }
            );
            env.fetchHarness.on(
                (url, req) => url === '/api/posts/701/bookmarks/me',
                { status: 200, body: { success: true, data: { postId: 701, active: true }, meta: null } }
            );
            env.fetchHarness.on(
                (url, req) => url === '/api/posts/701/reposts/me',
                { status: 200, body: { success: true, data: { postId: 701, active: true }, meta: null } }
            );

            // Click Like
            likeBtn.click();
            await new Promise(r => setTimeout(r, 10));
            assert.equal(env.fetchHarness.calls.length, 1);
            assert.equal(likeBtn.classList.contains('is-active'), true);
            assert.equal(env.document.getElementById('aloute-toast-notice'), null, 'No notice on successful like');

            // Click Bookmark
            bookmarkBtn.click();
            await new Promise(r => setTimeout(r, 10));
            assert.equal(env.fetchHarness.calls.length, 2);
            assert.equal(bookmarkBtn.classList.contains('is-active'), true);
            assert.equal(env.document.getElementById('aloute-toast-notice'), null, 'No notice on successful bookmark');

            // Click Repost
            repostBtn.click();
            await new Promise(r => setTimeout(r, 10));
            assert.equal(env.fetchHarness.calls.length, 3);
            assert.equal(repostBtn.classList.contains('is-active'), true);
            assert.equal(env.document.getElementById('aloute-toast-notice'), null, 'No notice on successful repost');
        });
    });

    describe('Suite 7: Security, CSRF & Standard Transports', () => {

        it('preserves base64 padding "=" in CSRF token cookie when transmitting X-XSRF-TOKEN', async () => {
            const env = createEnvironment({
                cookie: 'foo=bar; XSRF-TOKEN=YWxvdXRlLXRva2VuPT0=; other=val'
            });
            const btn = createPostButton({ action: 'like', postId: '801', active: false, count: 0 }, env.document);
            env.document.body.appendChild(btn);

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/801/likes/me',
                { status: 200, body: { success: true, data: { postId: 801, active: true }, meta: null } }
            );

            btn.click();
            await new Promise(r => setTimeout(r, 10));

            const call = env.fetchHarness.calls[0];
            assert.equal(call.headers['X-XSRF-TOKEN'], 'YWxvdXRlLXRva2VuPT0=');
        });

        it('never reads, inspects, or propagates JWT from aloute_access_token cookie in JavaScript', async () => {
            const env = createEnvironment({
                cookie: 'aloute_access_token=secret.jwt.token; XSRF-TOKEN=safe-csrf'
            });
            const btn = createPostButton({ action: 'like', postId: '802', active: false, count: 0 }, env.document);
            env.document.body.appendChild(btn);

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/802/likes/me',
                { status: 200, body: { success: true, data: { postId: 802, active: true }, meta: null } }
            );

            btn.click();
            await new Promise(r => setTimeout(r, 10));

            const call = env.fetchHarness.calls[0];
            assert.equal(call.headers['Authorization'], undefined, 'No Authorization header injected in JS');
            assert.equal(call.credentials, 'include', 'Must rely strictly on HttpOnly cookie credentials');
        });

        it('escapes and sanitizes error messages using textContent, preventing HTML injection (XSS)', async () => {
            const env = createEnvironment();
            const btn = createPostButton({ action: 'like', postId: '803', active: false, count: 0 }, env.document);
            env.document.body.appendChild(btn);

            // Server returns raw HTML/script payload in error message
            env.fetchHarness.on(
                (url, req) => url === '/api/posts/803/likes/me',
                {
                    status: 400,
                    body: {
                        success: false,
                        error: { message: '<img src=x onerror=alert(1)><b>Hacked</b>' }
                    }
                }
            );

            btn.click();
            await new Promise(r => setTimeout(r, 10));

            const notice = env.document.getElementById('aloute-toast-notice');
            assert.ok(notice);
            assert.equal(notice.children.length, 0, 'No HTML child nodes should be parsed or executed');
            assert.equal(notice.getAttribute('role'), 'status');
            assert.equal(notice.getAttribute('aria-live'), 'polite');
        });
    });
});
