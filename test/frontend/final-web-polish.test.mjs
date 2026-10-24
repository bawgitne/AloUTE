/**
 * AloUTE — Final Web Polish Regression Tests
 *
 * Covers the 2026-10-24 improvement set:
 * 1. Shared Bootstrap confirm dialog (core/confirm-dialog.js) replacing native confirm().
 * 2. Copy post permalink action (page-actions.js + post/_interaction-bar.html).
 * 3. Follow-state resolution via single GET /api/social/relations/{id} (page-actions.js).
 * 4. Composer local draft recovery (post-composer.js).
 * 5. Layout wiring: confirm-dialog.js included by default/single/admin layouts.
 *
 * Built-in Node test suite (node:test + assert/strict + vm), same pattern as
 * post-interactions.test.mjs: production IIFEs evaluated against a lightweight
 * DOM/window/fetch harness.
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

const confirmDialogSource = read('src/main/resources/static/js/core/confirm-dialog.js');
const httpClientSource = read('src/main/resources/static/js/core/http-client.js');
const pageActionsSource = read('src/main/resources/static/js/page-actions.js');
const postComposerSource = read('src/main/resources/static/js/features/post/post-composer.js');
const interactionBarTemplate = read('src/main/resources/templates/post/_interaction-bar.html');
const defaultLayout = read('src/main/resources/templates/layouts/default.html');
const singleLayout = read('src/main/resources/templates/layouts/single.html');
const adminLayout = read('src/main/resources/templates/layouts/admin.html');

// -----------------------------------------------------------------------------
// Lightweight DOM harness (subset of the post-interactions harness)
// -----------------------------------------------------------------------------

function matchesCompound(el, sel) {
    if (!el || !sel) return false;
    let remaining = sel.trim();
    if (!remaining) return false;
    const tagMatch = remaining.match(/^([a-zA-Z0-9_-]+)/);
    if (tagMatch) {
        if (el.tagName.toLowerCase() !== tagMatch[1].toLowerCase()) return false;
        remaining = remaining.slice(tagMatch[0].length);
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
            } else if (!el.hasAttribute(attrName)) {
                return false;
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
    if (sel.includes(',')) return sel.split(',').some((p) => matchesSelector(el, p.trim()));
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
        this._textContent = '';
        this._innerHTML = '';
        this.disabled = false;
        this.hidden = false;
        this.value = '';
        this.checked = true;
        this._focused = false;
    }

    get parentNode() {
        return this.parentElement;
    }

    get classList() {
        const self = this;
        return {
            add: (...names) => {
                const classes = new Set(self.className.split(/\s+/).filter(Boolean));
                for (const n of names) classes.add(n);
                self.className = Array.from(classes).join(' ');
            },
            remove: (...names) => {
                const classes = new Set(self.className.split(/\s+/).filter(Boolean));
                for (const n of names) classes.delete(n);
                self.className = Array.from(classes).join(' ');
            },
            contains: (name) => self.className.split(/\s+/).filter(Boolean).includes(name)
        };
    }

    get textContent() {
        if (this.children.length === 0) return this._textContent || '';
        return (this._textContent || '') + this.children.map((c) => c.textContent).join('');
    }

    set textContent(val) {
        this._textContent = String(val || '');
    }

    get innerHTML() {
        return this._innerHTML;
    }

    set innerHTML(html) {
        this._innerHTML = String(html || '');
        this.children = [];
        this._textContent = '';
        if (!html || !String(html).trim()) return;
        // Minimal parse: only enough for confirm-dialog's static markup.
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
                const el = new MockElement(m[2], '', this.ownerDocument);
                el.parentElement = stack[stack.length - 1];
                stack[stack.length - 1].children.push(el);
                const idMatch = (m[3] || '').match(/id=["']([^"']*)["']/);
                if (idMatch) el.id = idMatch[1];
                const clsMatch = (m[3] || '').match(/class=["']([^"']*)["']/);
                if (clsMatch) el.className = clsMatch[1];
                const selfClosing = /\/>$/.test(m[3] || '');
                if (!selfClosing) stack.push(el);
            }
        }
    }

    setAttribute(name, val) {
        this._attributes.set(name, String(val));
        if (name.startsWith('data-')) {
            const key = name.slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase());
            this.dataset[key] = String(val);
        }
    }

    getAttribute(name) {
        return this._attributes.has(name) ? this._attributes.get(name) : null;
    }

    hasAttribute(name) {
        return this._attributes.has(name);
    }

    appendChild(child) {
        child.parentElement = this;
        this.children.push(child);
        return child;
    }

    remove() {
        if (!this.parentElement) return;
        this.parentElement.children = this.parentElement.children.filter((c) => c !== this);
        this.parentElement = null;
    }

    insertBefore(node, ref) {
        node.parentElement = this;
        const idx = this.children.indexOf(ref);
        if (idx === -1) {
            this.children.push(node);
        } else {
            this.children.splice(idx, 0, node);
        }
        return node;
    }

    querySelector(sel) {
        return querySelector(this, sel);
    }

    querySelectorAll(sel) {
        return querySelectorAll(this, sel, []);
    }

    addEventListener(evt, fn) {
        if (!this._listeners.has(evt)) this._listeners.set(evt, []);
        this._listeners.get(evt).push(fn);
    }

    removeEventListener(evt, fn) {
        if (!this._listeners.has(evt)) return;
        this._listeners.set(evt, this._listeners.get(evt).filter((l) => l !== fn));
    }

    dispatchEvent(evt) {
        evt.target = evt.target || this;
        evt.currentTarget = this;
        const list = this._listeners.get(evt.type) || [];
        for (const fn of list) {
            fn(evt);
            if (evt._propagationStopped) break;
        }
        return !evt.defaultPrevented;
    }

    click() {
        this.dispatchEvent({ type: 'click', target: this, preventDefault() { this.defaultPrevented = true; } });
    }

    closest(sel) {
        let node = this;
        while (node) {
            if (matchesSelector(node, sel)) return node;
            node = node.parentElement;
        }
        return null;
    }

    focus() {
        this._focused = true;
        if (this.ownerDocument) this.ownerDocument.activeElement = this;
    }

    select() {}
}

class MockEvent {
    constructor(type, opts = {}) {
        this.type = type;
        Object.assign(this, opts);
        this._propagationStopped = false;
    }
    preventDefault() {
        this.defaultPrevented = true;
    }
    stopPropagation() {
        this._propagationStopped = true;
    }
    stopImmediatePropagation() {
        this._propagationStopped = true;
    }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function createEnvironment({ withBootstrap = true, withLocalStorage = true, origin = 'http://localhost:8080' } = {}) {
    const document = {
        createElement: (tag) => new MockElement(tag, '', document),
        getElementById: (id) => querySelector(document.body, `#${id}`),
        querySelector: (sel) => querySelector(document.body, sel),
        querySelectorAll: (sel) => querySelectorAll(document.body, sel, []),
        addEventListener: (evt, fn) => {
            document._listeners = document._listeners || new Map();
            if (!document._listeners.has(evt)) document._listeners.set(evt, []);
            document._listeners.get(evt).push(fn);
        },
        removeEventListener: (evt, fn) => {
            if (!document._listeners || !document._listeners.has(evt)) return;
            document._listeners.set(evt, document._listeners.get(evt).filter((l) => l !== fn));
        },
        dispatchEvent: (evt) => {
            evt.target = evt.target || document;
            evt.currentTarget = document;
            const list = (document._listeners && document._listeners.get(evt.type)) || [];
            for (const fn of list) {
                fn(evt);
                if (evt._propagationStopped) break;
            }
            return !evt.defaultPrevented;
        },
        cookie: 'XSRF-TOKEN=test-csrf-token;',
        readyState: 'complete',
        activeElement: null
    };
    document.body = new MockElement('BODY', '', document);
    document.documentElement = new MockElement('HTML', '', document);
    document.documentElement.appendChild(document.body);

    const storage = new Map();
    const localStorageStub = {
        getItem: (k) => (storage.has(k) ? storage.get(k) : null),
        setItem: (k, v) => storage.set(k, String(v)),
        removeItem: (k) => storage.delete(k),
        clear: () => storage.clear(),
        _map: storage
    };

    const fetchCalls = [];
    const clipboardWrites = [];

    // Minimal Bootstrap Modal stub: show/hide dispatch the lifecycle events the
    // confirm dialog relies on.
    class MockModal {
        constructor(el) {
            this.el = el;
        }
        static getInstance(el) {
            return el._modalInstance || null;
        }
        show() {
            this.el._modalInstance = this;
            this.el.setAttribute('aria-hidden', 'false');
        }
        hide() {
            this.el.setAttribute('aria-hidden', 'true');
            this.el.dispatchEvent(new MockEvent('hidden.bs.modal'));
        }
    }

    const confirmDialogCalls = [];

    const window = {
        document,
        fetchCalls,
        clipboardWrites,
        location: {
            href: origin + '/',
            origin,
            pathname: '/'
        },
        console,
        setTimeout: (fn, ms) => setTimeout(fn, ms),
        clearTimeout: (id) => clearTimeout(id),
        setInterval: (fn, ms) => setInterval(fn, ms),
        clearInterval: (id) => clearInterval(id),
        Date,
        URL: globalThis.URL,
        URLSearchParams: globalThis.URLSearchParams,
        Promise,
        navigator: {
            clipboard: {
                writeText: async (text) => {
                    clipboardWrites.push(text);
                }
            }
        },
        confirm: (msg) => {
            confirmDialogCalls.push(msg);
            return true;
        },
        fetch: async (url, opts = {}) => {
            fetchCalls.push({ url, opts });
            const raw = window.fetchHandler
                ? await window.fetchHandler(url, opts)
                : { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: {} }) };
            // Normalize every response to expose both text() and json().
            const text = typeof raw.text === 'function' ? await raw.text() : '';
            const status = raw.status || 200;
            const ok = raw.ok !== undefined ? raw.ok : (status >= 200 && status < 300);
            return {
                ok,
                status,
                text: async () => text,
                json: async () => JSON.parse(text)
            };
        }
    };
    if (withLocalStorage) {
        window.localStorage = localStorageStub;
    }
    if (withBootstrap) {
        window.bootstrap = { Modal: MockModal };
    }
    window.window = window;
    document.defaultView = window;
    window.confirmDialogCalls = confirmDialogCalls;
    window.MockEvent = MockEvent;
    window.Event = MockEvent;
    window.CustomEvent = class extends MockEvent {};

    return { document, window, fetchCalls, clipboardWrites, storage };
}

function runScript(source, window) {
    const ctx = vm.createContext(window);
    vm.runInContext(source, ctx);
    return ctx;
}

// Convenience: build the create-post composer DOM. currentUserId mirrors the
// body[data-current-user-id] attribute the layouts render for the session user.
function buildComposerDom(document, { currentUserId = '1', communityId = null } = {}) {
    document.body.dataset.currentUserId = currentUserId;

    const form = document.createElement('form');
    form.id = 'create-post-form';

    if (communityId !== null) {
        const comm = document.createElement('input');
        comm.id = 'post-community-id';
        comm.value = String(communityId);
        form.appendChild(comm);
    }

    const content = document.createElement('textarea');
    content.id = 'postContent';
    content.value = '';

    const visibility = document.createElement('select');
    visibility.id = 'visibility';
    visibility.value = 'PUBLIC';

    const allowReply = document.createElement('input');
    allowReply.id = 'allowReply';
    allowReply.checked = true;

    const allowQuote = document.createElement('input');
    allowQuote.id = 'allowQuote';
    allowQuote.checked = true;

    const submit = document.createElement('button');
    submit.id = 'btn-create-post';
    submit.setAttribute('data-action', 'create-post');

    const charCount = document.createElement('div');
    charCount.id = 'char-count';

    const status = document.createElement('div');
    status.id = 'create-post-status';

    for (const el of [content, visibility, allowReply, allowQuote, submit, charCount, status]) {
        form.appendChild(el);
    }
    document.body.appendChild(form);
    return { form, content, visibility, allowReply, allowQuote, submit, charCount, status };
}

const draftKeyFor = (userId, context = 'root') => `aloute.post.draft.v1.u${userId}.c${context}`;

// -----------------------------------------------------------------------------
// Suites
// -----------------------------------------------------------------------------

describe('Final web polish — shared confirm dialog (confirm-dialog.js)', () => {
    it('resolves true when the confirm button is clicked', async () => {
        const env = createEnvironment();
        runScript(confirmDialogSource, env.window);
        const promise = env.window.alouteConfirm('Nhận xử lý báo cáo #1?', { title: 'Nhận xử lý báo cáo' });
        await sleep(10);
        const confirmBtn = env.document.getElementById('aloute-confirm-dialog-confirm');
        assert.ok(confirmBtn, 'confirm button rendered inside shared modal');
        confirmBtn.click();
        assert.equal(await promise, true);
    });

    it('resolves false when cancelled (modal hidden without confirm)', async () => {
        const env = createEnvironment();
        runScript(confirmDialogSource, env.window);
        const promise = env.window.alouteConfirm('Đăng xuất?');
        await sleep(10);
        const modal = env.document.getElementById('aloute-confirm-dialog');
        // ESC / backdrop path: bootstrap hides the modal without pressing confirm.
        modal._modalInstance.hide();
        assert.equal(await promise, false);
    });

    it('uses a danger-styled confirm button when danger=true', async () => {
        const env = createEnvironment();
        runScript(confirmDialogSource, env.window);
        const promise = env.window.alouteConfirm('Xóa?', { danger: true, confirmText: 'Xóa' });
        await sleep(10);
        const confirmBtn = env.document.getElementById('aloute-confirm-dialog-confirm');
        assert.ok(confirmBtn.classList.contains('btn-danger'), 'danger variant applied');
        assert.equal(confirmBtn.textContent, 'Xóa');
        confirmBtn.click();
        await promise;
    });

    it('falls back to native confirm when Bootstrap is unavailable', async () => {
        const env = createEnvironment({ withBootstrap: false });
        runScript(confirmDialogSource, env.window);
        const result = await env.window.alouteConfirm('fallback?');
        assert.equal(result, true);
        assert.equal(env.window.confirmDialogCalls.length, 1, 'native confirm consulted once');
    });
});

describe('Final web polish — copy post permalink (page-actions.js + interaction bar)', () => {
    it('copies origin + /posts/{id} to the clipboard and toasts success', async () => {
        const env = createEnvironment();
        runScript(httpClientSource, env.window);
        runScript(pageActionsSource, env.window);

        const btn = env.document.createElement('button');
        btn.setAttribute('data-action', 'copy-post-link');
        btn.setAttribute('data-post-id', '42');
        env.document.body.appendChild(btn);
        // Page-actions uses document-level delegation; dispatch on document with
        // the button as target, mirroring how a real bubbled click arrives.
        env.document.dispatchEvent({ type: 'click', target: btn, preventDefault() { this.defaultPrevented = true; } });
        await sleep(20);

        assert.deepEqual(env.clipboardWrites, ['http://localhost:8080/posts/42']);
        const toast = env.document.getElementById('aloute-toast-notice');
        assert.ok(toast && toast.textContent.includes('liên kết bài viết'), 'success toast shown');
    });

    it('shows a warning and never touches the clipboard when post id is missing', async () => {
        const env = createEnvironment();
        runScript(httpClientSource, env.window);
        runScript(pageActionsSource, env.window);

        const btn = env.document.createElement('button');
        btn.setAttribute('data-action', 'copy-post-link');
        env.document.body.appendChild(btn);
        env.document.dispatchEvent({ type: 'click', target: btn, preventDefault() { this.defaultPrevented = true; } });
        await sleep(20);

        assert.equal(env.clipboardWrites.length, 0);
        const toast = env.document.getElementById('aloute-toast-notice');
        assert.ok(toast && toast.textContent.includes('Không thể sao chép'), 'warning toast shown');
    });

    it('renders the copy-post-link button in the interaction bar fragment (post id present only)', () => {
        assert.ok(interactionBarTemplate.includes("data-action='copy-post-link'"), 'copy action present');
        const copyIdx = interactionBarTemplate.indexOf("data-action='copy-post-link'");
        const guardIdx = interactionBarTemplate.lastIndexOf('th:if="${post.id != null}"', copyIdx);
        assert.ok(guardIdx !== -1, 'copy button guarded on post.id != null');
    });
});

describe('Final web polish — follow state via GET /api/social/relations (page-actions.js)', () => {
    it('resolves the follow button with a single relations request and no /following paging', async () => {
        const env = createEnvironment();
        env.window.fetchHandler = async (url) => {
            if (url.startsWith('/api/social/relations/')) {
                return {
                    ok: true,
                    status: 200,
                    text: async () => JSON.stringify({ success: true, data: { targetUserId: 5, following: true, blocked: false, muted: false, restricted: false } })
                };
            }
            return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: [] }) };
        };

        const btn = env.document.createElement('button');
        btn.id = 'btn-follow';
        btn.setAttribute('data-user-id', '5');
        btn.setAttribute('data-current-user-id', '1');
        env.document.body.appendChild(btn);

        runScript(httpClientSource, env.window);
        runScript(pageActionsSource, env.window);
        await sleep(30);

        const relationCalls = env.fetchCalls.filter((c) => c.url.startsWith('/api/social/relations/'));
        assert.equal(relationCalls.length, 1, 'exactly one relations call');
        assert.equal(relationCalls[0].url, '/api/social/relations/5');
        assert.equal(env.fetchCalls.filter((c) => c.url.includes('/following')).length, 0, 'no paged /following traversal');
        assert.equal(btn.getAttribute('aria-pressed'), 'true', 'button rendered as following');
    });

    it('renders the retry state when the relations endpoint fails', async () => {
        const env = createEnvironment();
        env.window.fetchHandler = async () => ({ ok: false, status: 500, text: async () => '' });

        const btn = env.document.createElement('button');
        btn.id = 'btn-follow';
        btn.setAttribute('data-user-id', '5');
        btn.setAttribute('data-current-user-id', '1');
        env.document.body.appendChild(btn);

        runScript(httpClientSource, env.window);
        runScript(pageActionsSource, env.window);
        await sleep(30);

        assert.equal(btn.dataset.state, 'error', 'error state surfaced for retry');
    });
});

describe('Final web polish — composer draft recovery (post-composer.js)', () => {
    it('restores a persisted draft into a fresh composer with a discard control', async () => {
        const env = createEnvironment();
        env.window.localStorage._map.set(draftKeyFor('1'), JSON.stringify({
            content: 'Bản nháp kiểm thử chưa hoàn tất',
            visibility: 'FOLLOWERS',
            allowReply: false,
            allowQuote: true,
            savedAt: Date.now()
        }));
        const dom = buildComposerDom(env.document, { currentUserId: '1' });
        runScript(postComposerSource, env.window);
        await sleep(10);

        assert.equal(dom.content.value, 'Bản nháp kiểm thử chưa hoàn tất', 'content restored');
        assert.equal(dom.visibility.value, 'FOLLOWERS', 'visibility restored');
        assert.equal(dom.allowReply.checked, false, 'allowReply restored');
        assert.ok(dom.status.textContent.includes('khôi phục'), 'restore notice shown: ' + dom.status.textContent);
        const discard = env.document.getElementById('btn-discard-draft');
        assert.ok(discard, 'discard button rendered');

        discard.click();
        assert.equal(dom.content.value, '', 'discard clears content');
        assert.equal(env.window.localStorage.getItem(draftKeyFor('1')), null, 'discard removes stored draft');
    });

    it('saves the draft to localStorage after typing (debounced) and clears it on publish', async () => {
        const env = createEnvironment();
        const dom = buildComposerDom(env.document, { currentUserId: '1' });
        runScript(postComposerSource, env.window);

        dom.content.value = 'Nội dung bản nháp sẽ được đăng';
        dom.content.dispatchEvent(new MockEvent('input'));
        await sleep(600); // debounce is 400ms

        const raw = env.window.localStorage.getItem(draftKeyFor('1'));
        assert.ok(raw, 'draft persisted under the user-scoped key');
        const draft = JSON.parse(raw);
        assert.equal(draft.content, 'Nội dung bản nháp sẽ được đăng');

        // Publish: submit returns 201 → draft must be removed and the pending
        // debounce timer must not resurrect it afterwards.
        env.window.fetchHandler = async () => ({
            ok: true,
            status: 201,
            text: async () => JSON.stringify({ success: true, data: { id: 9 } })
        });
        dom.submit.click();
        await sleep(700);
        assert.equal(env.window.localStorage.getItem(draftKeyFor('1')), null, 'draft cleared after successful publish (no timer resurrection)');
    });

    it('removes the draft when the composer is emptied again', async () => {
        const env = createEnvironment();
        env.window.localStorage._map.set(draftKeyFor('1'), JSON.stringify({ content: 'cũ', visibility: 'PUBLIC', allowReply: true, allowQuote: true, savedAt: 1 }));
        const dom = buildComposerDom(env.document, { currentUserId: '1' });
        runScript(postComposerSource, env.window);

        dom.content.value = '';
        dom.content.dispatchEvent(new MockEvent('input'));
        await sleep(600);
        assert.equal(env.window.localStorage.getItem(draftKeyFor('1')), null, 'empty composer discards draft');
    });
});

describe('Draft privacy — cross-account and cross-context isolation (post-composer.js)', () => {
    it('does not restore another user\'s draft after an account switch on the same browser', async () => {
        const env = createEnvironment();
        // User 1 (admin) saved a draft earlier in this browser profile.
        env.window.localStorage._map.set(draftKeyFor('1'), JSON.stringify({
            content: 'MAT KHAU DU AN: aloute-secret-2026',
            visibility: 'PUBLIC', allowReply: true, allowQuote: true, savedAt: Date.now()
        }));
        const dom = buildComposerDom(env.document, { currentUserId: '2' }); // user 2 now logged in
        runScript(postComposerSource, env.window);
        await sleep(10);

        assert.equal(dom.content.value, '', 'user 2 composer starts empty');
        assert.ok(!dom.status.textContent.includes('khôi phục'), 'no restore notice for foreign draft');
        assert.ok(!env.document.getElementById('btn-discard-draft'), 'no discard affordance for foreign draft');
        assert.ok(env.window.localStorage.getItem(draftKeyFor('1')), 'user 1 draft untouched (recovers on re-login)');
    });

    it('fails closed (never persists or restores) when no authenticated user id is present', async () => {
        const env = createEnvironment();
        // Legacy/anonymous key from an older build must not be picked up either.
        env.window.localStorage._map.set('aloute.post.draft.v1', JSON.stringify({ content: 'legacy', visibility: 'PUBLIC', allowReply: true, allowQuote: true, savedAt: 1 }));
        const dom = buildComposerDom(env.document, { currentUserId: '' });
        runScript(postComposerSource, env.window);

        dom.content.value = 'ghi chú ẩn danh';
        dom.content.dispatchEvent(new MockEvent('input'));
        await sleep(600);

        const written = Array.from(env.window.localStorage._map.keys());
        assert.deepEqual(written, ['aloute.post.draft.v1'], 'anonymous session writes nothing new');
        assert.equal(dom.content.value, 'ghi chú ẩn danh', 'typing still works, just not persisted');
        assert.ok(!dom.status.textContent.includes('khôi phục'), 'no restore in anonymous session');
    });

    it('scopes drafts by composition context: community composer and standalone composer do not share drafts', async () => {
        // Community 7 composer saves its own draft.
        const commEnv = createEnvironment();
        const commDom = buildComposerDom(commEnv.document, { currentUserId: '1', communityId: '7' });
        runScript(postComposerSource, commEnv.window);
        commDom.content.value = 'Chỉ dành cho cộng đồng 7';
        commDom.content.dispatchEvent(new MockEvent('input'));
        await sleep(600);
        assert.ok(commEnv.window.localStorage.getItem(draftKeyFor('1', '7')), 'community draft stored under community-scoped key');

        // Same user opens the standalone composer → community draft must not appear.
        const rootEnv = createEnvironment();
        rootEnv.window.localStorage._map.set(draftKeyFor('1', '7'), commEnv.window.localStorage.getItem(draftKeyFor('1', '7')));
        const rootDom = buildComposerDom(rootEnv.document, { currentUserId: '1' });
        runScript(postComposerSource, rootEnv.window);
        await sleep(10);
        assert.equal(rootDom.content.value, '', 'standalone composer does not restore the community draft');
    });

    it('publishing clears only the active user+context draft; other scoped drafts survive', async () => {
        const env = createEnvironment();
        env.window.localStorage._map.set(draftKeyFor('2'), JSON.stringify({ content: 'draft của user 2', visibility: 'PUBLIC', allowReply: true, allowQuote: true, savedAt: 1 }));
        env.window.localStorage._map.set(draftKeyFor('1', '7'), JSON.stringify({ content: 'draft cộng đồng 7', visibility: 'PUBLIC', allowReply: true, allowQuote: true, savedAt: 1 }));
        const dom = buildComposerDom(env.document, { currentUserId: '1' });
        runScript(postComposerSource, env.window);

        dom.content.value = 'Bài sẽ đăng ngay';
        dom.content.dispatchEvent(new MockEvent('input'));
        await sleep(600);
        assert.ok(env.window.localStorage.getItem(draftKeyFor('1', 'root')), 'user 1 root draft saved');

        env.window.fetchHandler = async () => ({
            ok: true,
            status: 201,
            text: async () => JSON.stringify({ success: true, data: { id: 30 } })
        });
        dom.submit.click();
        await sleep(100);

        assert.equal(env.window.localStorage.getItem(draftKeyFor('1', 'root')), null, 'active draft cleared on publish');
        assert.ok(env.window.localStorage.getItem(draftKeyFor('2')), 'user 2 draft untouched');
        assert.ok(env.window.localStorage.getItem(draftKeyFor('1', '7')), 'user 1 community draft untouched');
    });
});

describe('Final web polish — layout wiring for confirm-dialog.js', () => {
    it('default, single and admin layouts load confirm-dialog.js before dependent scripts', () => {
        for (const [name, layout] of [['default', defaultLayout], ['single', singleLayout], ['admin', adminLayout]]) {
            const dialogIdx = layout.indexOf('/js/core/confirm-dialog.js');
            assert.ok(dialogIdx !== -1, `${name} layout includes confirm-dialog.js`);
            const actionsIdx = layout.indexOf('page-actions.js') !== -1
                ? layout.indexOf('page-actions.js')
                : layout.indexOf('admin-actions.js');
            assert.ok(actionsIdx === -1 || dialogIdx < actionsIdx, `${name} loads confirm-dialog before dependent scripts`);
        }
    });
});
