/**
 * AloUTE — Focused Unit Tests for Poll Voting & Media Attachment Controllers
 *
 * Verifies real production implementations in:
 * - src/main/resources/static/js/page-actions.js (initPollVoting, renderPollResults)
 * - src/main/resources/static/js/features/post/post-composer.js (initCreatePost media management)
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

const pageActionsSource = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/static/js/page-actions.js'),
    'utf-8'
);

const postComposerSource = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/static/js/features/post/post-composer.js'),
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
        } else if (remaining.startsWith(':checked')) {
            if (!el.checked) return false;
            remaining = remaining.slice(':checked'.length);
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
        this._textContent = '';
        this._innerHTML = '';
        this.disabled = false;
        this.hidden = false;
        this.value = '';
        this._checked = false;
        this._type = '';
        this.files = [];
    }

    get textContent() {
        if (this._textContent !== undefined && this._textContent !== '') {
            return this._textContent;
        }
        if (this.children.length > 0) {
            return this.children.map(c => c.textContent).join(' ');
        }
        return this._textContent || '';
    }

    set textContent(val) {
        this._textContent = String(val);
        this.children = [];
    }

    get type() {
        return this.getAttribute('type') || this._type || '';
    }

    set type(val) {
        this._type = val;
        this.setAttribute('type', val);
    }

    get checked() {
        return this._checked || false;
    }

    set checked(val) {
        this._checked = Boolean(val);
    }

    get title() {
        return this.getAttribute('title') || '';
    }

    set title(val) {
        this.setAttribute('title', val);
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

    remove() {
        if (this.parentElement) {
            const idx = this.parentElement.children.indexOf(this);
            if (idx !== -1) this.parentElement.children.splice(idx, 1);
            this.parentElement = null;
        }
    }

    replaceWith(newEl) {
        if (!this.parentElement) return;
        const idx = this.parentElement.children.indexOf(this);
        if (idx !== -1) {
            newEl.parentElement = this.parentElement;
            newEl.ownerDocument = this.ownerDocument;
            this.parentElement.children[idx] = newEl;
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
        console: console,
        setTimeout: setTimeout,
        clearTimeout: clearTimeout,
        document: doc,
        location: { href: 'http://localhost:8080/feed' },
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
        Event: MockEvent,
        URL: {
            createObjectURL: (file) => 'blob:http://localhost/' + (file.name || 'test')
        },
        FormData: class MockFormData {
            constructor() {
                this._fields = [];
            }
            append(name, value) {
                this._fields.push({ name, value });
            }
        },
        Blob: class MockBlob {
            constructor(parts, opts = {}) {
                this.parts = parts;
                this.type = opts.type || '';
            }
        },
        addEventListener(type, cb) {
            if (!windowListeners.has(type)) windowListeners.set(type, []);
            windowListeners.get(type).push(cb);
        },
        dispatchEvent(event) {
            const listeners = windowListeners.get(event.type) || [];
            for (const l of listeners) l.call(win, event);
            return !event.defaultPrevented;
        }
    };

    win.window = win;
    return { window: win, document: doc };
}

// -----------------------------------------------------------------------------
// Test Suites
// -----------------------------------------------------------------------------

describe('Poll Voting Controller (page-actions.js)', () => {

    it('single-choice open poll: submittal dispatches optionId and renders result DOM', async () => {
        const env = createDOMEnvironment();

        const container = env.document.createElement('div');
        container.className = 'post-poll-container';
        container.setAttribute('data-poll-id', '50');
        container.setAttribute('data-allow-multiple', 'false');
        container.setAttribute('data-is-closed', 'false');

        const form = env.document.createElement('form');
        form.className = 'poll-vote-form';
        form.setAttribute('data-poll-id', '50');
        form.setAttribute('data-allow-multiple', 'false');

        const radio1 = env.document.createElement('input');
        radio1.type = 'radio';
        radio1.value = '101';
        radio1.checked = true;

        const submitBtn = env.document.createElement('button');
        submitBtn.type = 'submit';
        submitBtn.className = 'btn-vote-poll';

        const errorEl = env.document.createElement('div');
        errorEl.className = 'poll-vote-error d-none';

        form.appendChild(radio1);
        form.appendChild(submitBtn);
        form.appendChild(errorEl);
        container.appendChild(form);
        env.document.body.appendChild(container);

        env.window.fetchHandler = async (url, opts) => {
            assert.equal(url, '/api/polls/50/vote');
            assert.equal(opts.method, 'POST');
            const parsed = JSON.parse(opts.body);
            assert.deepEqual(parsed, { optionId: 101 });
            return {
                ok: true,
                status: 200,
                text: async () => JSON.stringify({
                    success: true,
                    data: {
                        id: 50,
                        totalVotes: 10,
                        userVotedOptionId: 101,
                        options: [
                            { id: 101, text: 'Opt 1', voteCount: 6, selectedByCurrentUser: true },
                            { id: 102, text: 'Opt 2', voteCount: 4, selectedByCurrentUser: false }
                        ]
                    }
                })
            };
        };

        const ctx = vm.createContext(env.window);
        vm.runInContext(pageActionsSource, ctx);

        form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));
        await new Promise(r => setTimeout(r, 20));

        // Form replaced with .poll-results
        const resultsEl = container.querySelector('.poll-results');
        assert.ok(resultsEl, 'poll-results container must be rendered');
        assert.equal(container.querySelector('.poll-vote-form'), null);
        assert.ok(resultsEl.textContent.includes('10 lượt bình chọn'));
    });

    it('multi-choice open poll: submittal dispatches optionIds array', async () => {
        const env = createDOMEnvironment();

        const container = env.document.createElement('div');
        container.className = 'post-poll-container';
        container.setAttribute('data-poll-id', '60');
        container.setAttribute('data-allow-multiple', 'true');
        container.setAttribute('data-is-closed', 'false');

        const form = env.document.createElement('form');
        form.className = 'poll-vote-form';
        form.setAttribute('data-poll-id', '60');
        form.setAttribute('data-allow-multiple', 'true');

        const cb1 = env.document.createElement('input');
        cb1.type = 'checkbox';
        cb1.value = '201';
        cb1.checked = true;

        const cb2 = env.document.createElement('input');
        cb2.type = 'checkbox';
        cb2.value = '202';
        cb2.checked = true;

        const submitBtn = env.document.createElement('button');
        submitBtn.type = 'submit';
        submitBtn.className = 'btn-vote-poll';

        const errorEl = env.document.createElement('div');
        errorEl.className = 'poll-vote-error d-none';

        form.appendChild(cb1);
        form.appendChild(cb2);
        form.appendChild(submitBtn);
        form.appendChild(errorEl);
        container.appendChild(form);
        env.document.body.appendChild(container);

        env.window.fetchHandler = async (url, opts) => {
            const parsed = JSON.parse(opts.body);
            assert.deepEqual(parsed, { optionIds: [201, 202] });
            return {
                ok: true,
                status: 200,
                text: async () => JSON.stringify({
                    success: true,
                    data: {
                        id: 60,
                        totalVotes: 5,
                        options: [
                            { id: 201, text: 'Opt 1', voteCount: 3, selectedByCurrentUser: true },
                            { id: 202, text: 'Opt 2', voteCount: 2, selectedByCurrentUser: true }
                        ]
                    }
                })
            };
        };

        const ctx = vm.createContext(env.window);
        vm.runInContext(pageActionsSource, ctx);

        form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));
        await new Promise(r => setTimeout(r, 20));

        assert.ok(container.querySelector('.poll-results'));
    });

    it('unselected submission displays validation error without network request', async () => {
        const env = createDOMEnvironment();

        const container = env.document.createElement('div');
        container.className = 'post-poll-container';

        const form = env.document.createElement('form');
        form.className = 'poll-vote-form';
        form.setAttribute('data-poll-id', '70');
        form.setAttribute('data-allow-multiple', 'false');

        const radio1 = env.document.createElement('input');
        radio1.type = 'radio';
        radio1.value = '301';
        radio1.checked = false;

        const errorEl = env.document.createElement('div');
        errorEl.className = 'poll-vote-error d-none';

        form.appendChild(radio1);
        form.appendChild(errorEl);
        container.appendChild(form);
        env.document.body.appendChild(container);

        const ctx = vm.createContext(env.window);
        vm.runInContext(pageActionsSource, ctx);

        form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));
        await new Promise(r => setTimeout(r, 10));

        assert.equal(env.window.fetchCalls.length, 0);
        assert.equal(errorEl.classList.contains('d-none'), false);
        assert.ok(errorEl.textContent.includes('Vui lòng chọn một phương án'));
    });

    it('closed poll local gate blocks submission before network request', async () => {
        const env = createDOMEnvironment();

        const container = env.document.createElement('div');
        container.className = 'post-poll-container';
        container.setAttribute('data-poll-id', '80');
        container.setAttribute('data-is-closed', 'true');

        const form = env.document.createElement('form');
        form.className = 'poll-vote-form';
        form.setAttribute('data-poll-id', '80');

        const radio1 = env.document.createElement('input');
        radio1.type = 'radio';
        radio1.value = '401';
        radio1.checked = true;

        const errorEl = env.document.createElement('div');
        errorEl.className = 'poll-vote-error d-none';

        form.appendChild(radio1);
        form.appendChild(errorEl);
        container.appendChild(form);
        env.document.body.appendChild(container);

        const ctx = vm.createContext(env.window);
        vm.runInContext(pageActionsSource, ctx);

        form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));
        await new Promise(r => setTimeout(r, 10));

        assert.equal(env.window.fetchCalls.length, 0);
        assert.ok(errorEl.textContent.includes('Bình chọn này đã kết thúc'));
    });

    it('backend 409 POLL_ALREADY_VOTED surfaces friendly error and unlocks button', async () => {
        const env = createDOMEnvironment();

        const container = env.document.createElement('div');
        container.className = 'post-poll-container';

        const form = env.document.createElement('form');
        form.className = 'poll-vote-form';
        form.setAttribute('data-poll-id', '90');

        const radio1 = env.document.createElement('input');
        radio1.type = 'radio';
        radio1.value = '501';
        radio1.checked = true;

        const submitBtn = env.document.createElement('button');
        submitBtn.type = 'submit';
        submitBtn.className = 'btn-vote-poll';

        const errorEl = env.document.createElement('div');
        errorEl.className = 'poll-vote-error d-none';

        form.appendChild(radio1);
        form.appendChild(submitBtn);
        form.appendChild(errorEl);
        container.appendChild(form);
        env.document.body.appendChild(container);

        env.window.fetchHandler = async () => ({
            ok: false,
            status: 409,
            text: async () => JSON.stringify({
                success: false,
                error: { code: 'POLL_ALREADY_VOTED', message: 'Already voted' }
            })
        });

        const ctx = vm.createContext(env.window);
        vm.runInContext(pageActionsSource, ctx);

        form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));
        await new Promise(r => setTimeout(r, 20));

        assert.equal(errorEl.classList.contains('d-none'), false);
        assert.ok(errorEl.textContent.includes('Bạn đã tham gia bình chọn này rồi'));
        assert.equal(submitBtn.disabled, false);
    });

    it('rapid repeated submits drop second request (duplicate-submit lock)', async () => {
        const env = createDOMEnvironment();

        const container = env.document.createElement('div');
        container.className = 'post-poll-container';

        const form = env.document.createElement('form');
        form.className = 'poll-vote-form';
        form.setAttribute('data-poll-id', '100');

        const radio1 = env.document.createElement('input');
        radio1.type = 'radio';
        radio1.value = '601';
        radio1.checked = true;

        const submitBtn = env.document.createElement('button');
        submitBtn.type = 'submit';
        submitBtn.className = 'btn-vote-poll';

        const errorEl = env.document.createElement('div');
        errorEl.className = 'poll-vote-error d-none';

        form.appendChild(radio1);
        form.appendChild(submitBtn);
        form.appendChild(errorEl);
        container.appendChild(form);
        env.document.body.appendChild(container);

        let resolveVote;
        env.window.fetchHandler = () => new Promise(r => { resolveVote = r; });

        const ctx = vm.createContext(env.window);
        vm.runInContext(pageActionsSource, ctx);

        form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));
        form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));

        assert.equal(env.window.fetchCalls.length, 1);
        resolveVote({
            ok: true,
            status: 200,
            text: async () => JSON.stringify({ success: true, data: { id: 100, options: [] } })
        });
        await new Promise(r => setTimeout(r, 20));
    });
});

describe('Media Composer Controller (post-composer.js)', () => {

    function setupComposerDOM(env) {
        const form = env.document.createElement('form');
        form.id = 'create-post-form';

        const contentInput = env.document.createElement('textarea');
        contentInput.id = 'postContent';
        contentInput.value = 'Nội dung bài viết mẫu với ảnh đính kèm';

        const submitBtn = env.document.createElement('button');
        submitBtn.id = 'btn-create-post';
        submitBtn.type = 'submit';

        const statusBox = env.document.createElement('div');
        statusBox.id = 'create-post-status';

        const mediaInput = env.document.createElement('input');
        mediaInput.id = 'post-media-input';
        mediaInput.type = 'file';

        const selectMediaBtn = env.document.createElement('button');
        selectMediaBtn.id = 'btn-select-media';

        const mediaPreviewList = env.document.createElement('div');
        mediaPreviewList.id = 'media-preview-list';

        const mediaStatusBox = env.document.createElement('div');
        mediaStatusBox.id = 'media-upload-status';

        form.appendChild(contentInput);
        form.appendChild(submitBtn);
        form.appendChild(statusBox);
        form.appendChild(mediaInput);
        form.appendChild(selectMediaBtn);
        form.appendChild(mediaPreviewList);
        form.appendChild(mediaStatusBox);

        env.document.body.appendChild(form);
        return { form, contentInput, submitBtn, statusBox, mediaInput, selectMediaBtn, mediaPreviewList, mediaStatusBox };
    }

    it('pending upload prevents form submission (race direction 1a)', async () => {
        const env = createDOMEnvironment();
        const dom = setupComposerDOM(env);

        let resolveUpload;
        env.window.fetchHandler = async (url) => {
            if (url === '/api/media') {
                return new Promise(r => { resolveUpload = r; });
            }
            return { ok: true, status: 200, text: async () => '{}' };
        };

        const ctx = vm.createContext(env.window);
        vm.runInContext(postComposerSource, ctx);

        // Trigger file selection
        dom.mediaInput.files = [{ name: 'test.jpg', size: 1024 * 100 }];
        dom.mediaInput.dispatchEvent(new MockEvent('change'));

        // Form submit attempted while upload is pending
        dom.form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));

        assert.ok(dom.statusBox.textContent.includes('Vui lòng đợi quá trình tải ảnh'));
        assert.equal(env.window.fetchCalls.filter(c => c.url === '/api/posts').length, 0);

        // Resolve upload
        resolveUpload({
            ok: true,
            status: 201,
            text: async () => JSON.stringify({ success: true, data: { id: 88 } })
        });
        await new Promise(r => setTimeout(r, 20));
    });

    it('pending deletion prevents form submission (race direction 1b)', async () => {
        const env = createDOMEnvironment();
        const dom = setupComposerDOM(env);

        let resolveDelete;
        env.window.fetchHandler = async (url, opts) => {
            if (url === '/api/media') {
                return {
                    ok: true,
                    status: 201,
                    text: async () => JSON.stringify({ success: true, data: { id: 101 } })
                };
            }
            if (url === '/api/media/101' && opts.method === 'DELETE') {
                return new Promise(r => { resolveDelete = r; });
            }
            return { ok: true, status: 200, text: async () => '{}' };
        };

        const ctx = vm.createContext(env.window);
        vm.runInContext(postComposerSource, ctx);

        dom.mediaInput.files = [{ name: 'photo.jpg', size: 1024 * 50 }];
        dom.mediaInput.dispatchEvent(new MockEvent('change'));
        await new Promise(r => setTimeout(r, 20));

        const removeBtn = dom.mediaPreviewList.querySelector('button[title="Xóa ảnh"]');
        assert.ok(removeBtn, 'Remove button must exist');

        // Trigger deletion
        removeBtn.click();

        // Submit attempted while deletion is pending
        dom.form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));

        assert.ok(dom.statusBox.textContent.includes('Đang xử lý ảnh, vui lòng đợi trong giây lát'));
        assert.equal(env.window.fetchCalls.filter(c => c.url === '/api/posts').length, 0);

        // Resolve deletion
        resolveDelete({ ok: true, status: 204, text: async () => '' });
        await new Promise(r => setTimeout(r, 20));
        assert.equal(dom.mediaPreviewList.children.length, 0);
    });

    it('pending post submission freezes media controls and blocks new upload and delete attempts (race direction 2)', async () => {
        const env = createDOMEnvironment();
        const dom = setupComposerDOM(env);

        let resolvePost;
        const capturedPostsRequests = [];
        env.window.fetchHandler = async (url, opts) => {
            if (url === '/api/media') {
                return {
                    ok: true,
                    status: 201,
                    text: async () => JSON.stringify({ success: true, data: { id: 202 } })
                };
            }
            if (url === '/api/posts') {
                capturedPostsRequests.push({ url, opts, body: opts.body ? JSON.parse(opts.body) : null });
                return new Promise(r => { resolvePost = r; });
            }
            return { ok: true, status: 200, text: async () => '{}' };
        };

        const ctx = vm.createContext(env.window);
        vm.runInContext(postComposerSource, ctx);

        // Upload attachment
        dom.mediaInput.files = [{ name: 'photo202.png', size: 1024 * 30 }];
        dom.mediaInput.dispatchEvent(new MockEvent('change'));
        await new Promise(r => setTimeout(r, 20));

        const removeBtn = dom.mediaPreviewList.querySelector('button[title="Xóa ảnh"]');
        assert.ok(removeBtn);
        assert.equal(removeBtn.disabled, false);

        // Submit form -> /api/posts is now pending
        dom.form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));

        // Verify media controls and buttons are frozen in UI
        assert.equal(dom.submitBtn.disabled, true);
        assert.equal(dom.mediaInput.disabled, true);
        assert.equal(dom.selectMediaBtn.disabled, true);
        assert.equal(removeBtn.disabled, true);

        const initialMediaFetchCount = env.window.fetchCalls.filter(c => c.url.startsWith('/api/media')).length;

        // Attempt new upload via input change during pending submit
        dom.mediaInput.files = [{ name: 'in_flight.png', size: 1024 * 10 }];
        dom.mediaInput.dispatchEvent(new MockEvent('change'));

        // Attempt new upload via selectMediaBtn click during pending submit
        dom.selectMediaBtn.click();

        // Attempt delete existing media during pending submit
        removeBtn.click();

        // Attempt repeated post submit during pending submit
        dom.form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));

        // Verify no extra media fetches were dispatched
        const currentMediaFetchCount = env.window.fetchCalls.filter(c => c.url.startsWith('/api/media')).length;
        assert.equal(currentMediaFetchCount, initialMediaFetchCount);

        // Verify exactly 1 /api/posts was dispatched
        assert.equal(capturedPostsRequests.length, 1);
        assert.deepEqual(capturedPostsRequests[0].body.mediaIds, [202]);

        // Complete the post
        resolvePost({
            ok: true,
            status: 201,
            text: async () => JSON.stringify({ success: true, data: { id: 700 } })
        });
        await new Promise(r => setTimeout(r, 20));
    });

    it('confirmed-success post keeps media controls frozen through navigation', async () => {
        const env = createDOMEnvironment();
        const dom = setupComposerDOM(env);

        env.window.fetchHandler = async (url) => {
            if (url === '/api/media') {
                return {
                    ok: true,
                    status: 201,
                    text: async () => JSON.stringify({ success: true, data: { id: 303 } })
                };
            }
            if (url === '/api/posts') {
                return {
                    ok: true,
                    status: 201,
                    text: async () => JSON.stringify({ success: true, data: { id: 800 } })
                };
            }
            return { ok: true, status: 200, text: async () => '{}' };
        };

        const ctx = vm.createContext(env.window);
        vm.runInContext(postComposerSource, ctx);

        dom.mediaInput.files = [{ name: 'photo303.png', size: 1024 * 30 }];
        dom.mediaInput.dispatchEvent(new MockEvent('change'));
        await new Promise(r => setTimeout(r, 20));

        const removeBtn = dom.mediaPreviewList.querySelector('button[title="Xóa ảnh"]');

        // Submit form -> succeeds with 201
        dom.form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));
        await new Promise(r => setTimeout(r, 20));

        assert.ok(dom.statusBox.textContent.includes('thành công'));

        // Controls must remain frozen on confirmed success
        assert.equal(dom.mediaInput.disabled, true);
        assert.equal(dom.selectMediaBtn.disabled, true);
        assert.equal(removeBtn.disabled, true);

        const mediaFetchCountBefore = env.window.fetchCalls.filter(c => c.url.startsWith('/api/media')).length;

        // Any attempt to upload or remove after success is rejected
        dom.mediaInput.dispatchEvent(new MockEvent('change'));
        dom.selectMediaBtn.click();
        removeBtn.click();

        const mediaFetchCountAfter = env.window.fetchCalls.filter(c => c.url.startsWith('/api/media')).length;
        assert.equal(mediaFetchCountAfter, mediaFetchCountBefore);
    });

    it('post submission failure restores media controls and allows subsequent upload, delete, and retry', async () => {
        const env = createDOMEnvironment();
        const dom = setupComposerDOM(env);

        let postShouldFail = true;
        const capturedPostsRequests = [];
        env.window.fetchHandler = async (url, opts) => {
            if (url === '/api/media') {
                return {
                    ok: true,
                    status: 201,
                    text: async () => JSON.stringify({ success: true, data: { id: 404 } })
                };
            }
            if (url === '/api/media/404' && opts.method === 'DELETE') {
                return { ok: true, status: 204, text: async () => '' };
            }
            if (url === '/api/posts') {
                capturedPostsRequests.push({ url, opts, body: opts.body ? JSON.parse(opts.body) : null });
                if (postShouldFail) {
                    return { ok: false, status: 500, text: async () => JSON.stringify({ message: 'Internal error' }) };
                }
                return { ok: true, status: 201, text: async () => JSON.stringify({ success: true, data: { id: 900 } }) };
            }
            return { ok: true, status: 200, text: async () => '{}' };
        };

        const ctx = vm.createContext(env.window);
        vm.runInContext(postComposerSource, ctx);

        dom.mediaInput.files = [{ name: 'photo404.png', size: 1024 * 30 }];
        dom.mediaInput.dispatchEvent(new MockEvent('change'));
        await new Promise(r => setTimeout(r, 20));

        const removeBtn = dom.mediaPreviewList.querySelector('button[title="Xóa ảnh"]');

        // First submit -> fails with HTTP 500
        dom.form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));
        await new Promise(r => setTimeout(r, 20));

        assert.ok(dom.statusBox.textContent.includes('500'));
        assert.equal(capturedPostsRequests.length, 1);
        assert.deepEqual(capturedPostsRequests[0].body.mediaIds, [404]);

        // Controls must be restored after genuine failure
        assert.equal(dom.submitBtn.disabled, false);
        assert.equal(dom.mediaInput.disabled, false);
        assert.equal(dom.selectMediaBtn.disabled, false);
        assert.equal(removeBtn.disabled, false);

        // Delete the attachment
        removeBtn.click();
        await new Promise(r => setTimeout(r, 20));
        assert.equal(dom.mediaPreviewList.children.length, 0);

        // Retry post submission without attachment
        postShouldFail = false;
        dom.form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));
        await new Promise(r => setTimeout(r, 20));

        assert.ok(dom.statusBox.textContent.includes('thành công'));
        assert.equal(capturedPostsRequests.length, 2);
        assert.equal(capturedPostsRequests[1].body.mediaIds, undefined);
    });

    it('media upload 201 success attaches thumbnail and propagates mediaIds on post submit (asserted outside callback)', async () => {
        const env = createDOMEnvironment();
        const dom = setupComposerDOM(env);

        const capturedPostsRequests = [];
        env.window.fetchHandler = async (url, opts) => {
            if (url === '/api/media') {
                return {
                    ok: true,
                    status: 201,
                    text: async () => JSON.stringify({
                        success: true,
                        data: { id: 456, url: '/api/media/file/456.png' }
                    })
                };
            }
            if (url === '/api/posts') {
                capturedPostsRequests.push({ url, opts, body: opts.body ? JSON.parse(opts.body) : null });
                return {
                    ok: true,
                    status: 201,
                    text: async () => JSON.stringify({ success: true, data: { id: 999 } })
                };
            }
            return { ok: true, status: 200, text: async () => '{}' };
        };

        const ctx = vm.createContext(env.window);
        vm.runInContext(postComposerSource, ctx);

        dom.mediaInput.files = [{ name: 'photo.png', size: 1024 * 50 }];
        dom.mediaInput.dispatchEvent(new MockEvent('change'));
        await new Promise(r => setTimeout(r, 20));

        assert.equal(dom.mediaPreviewList.children.length, 1);
        const removeBtn = dom.mediaPreviewList.querySelector('button[title="Xóa ảnh"]');
        assert.ok(removeBtn, 'Remove button must be present on uploaded card');

        // Submit form
        dom.form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));
        await new Promise(r => setTimeout(r, 20));

        // Assert strictly outside production callback/catch boundaries
        assert.equal(capturedPostsRequests.length, 1);
        assert.deepEqual(capturedPostsRequests[0].body.mediaIds, [456]);
    });

    it('malformed 201 response removes temporary preview and surfaces error message', async () => {
        const env = createDOMEnvironment();
        const dom = setupComposerDOM(env);

        env.window.fetchHandler = async (url) => {
            if (url === '/api/media') {
                return {
                    ok: true,
                    status: 201,
                    text: async () => JSON.stringify({
                        success: false,
                        data: null
                    })
                };
            }
            return { ok: true, status: 200, text: async () => '{}' };
        };

        const ctx = vm.createContext(env.window);
        vm.runInContext(postComposerSource, ctx);

        dom.mediaInput.files = [{ name: 'corrupt.png', size: 1024 * 10 }];
        dom.mediaInput.dispatchEvent(new MockEvent('change'));
        await new Promise(r => setTimeout(r, 20));

        assert.equal(dom.mediaPreviewList.children.length, 0);
        assert.ok(dom.mediaStatusBox.textContent.includes('không hợp lệ'));
    });

    it('media removal: strict 204 confirmation removes card and unlinks ID (asserted outside callback)', async () => {
        const env = createDOMEnvironment();
        const dom = setupComposerDOM(env);

        const capturedPostsRequests = [];
        env.window.fetchHandler = async (url, opts) => {
            if (url === '/api/media') {
                return {
                    ok: true,
                    status: 201,
                    text: async () => JSON.stringify({ success: true, data: { id: 777 } })
                };
            }
            if (url === '/api/media/777' && opts.method === 'DELETE') {
                return { ok: true, status: 204, text: async () => '' };
            }
            if (url === '/api/posts') {
                capturedPostsRequests.push({ url, opts, body: opts.body ? JSON.parse(opts.body) : null });
                return { ok: true, status: 201, text: async () => JSON.stringify({ success: true, data: {} }) };
            }
            return { ok: true, status: 200, text: async () => '{}' };
        };

        const ctx = vm.createContext(env.window);
        vm.runInContext(postComposerSource, ctx);

        dom.mediaInput.files = [{ name: 'delete_me.png', size: 1024 * 10 }];
        dom.mediaInput.dispatchEvent(new MockEvent('change'));
        await new Promise(r => setTimeout(r, 20));

        const removeBtn = dom.mediaPreviewList.querySelector('button[title="Xóa ảnh"]');
        removeBtn.click();
        await new Promise(r => setTimeout(r, 20));

        assert.equal(dom.mediaPreviewList.children.length, 0);

        // Submit form after removal: mediaIds is empty/undefined
        dom.form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));
        await new Promise(r => setTimeout(r, 20));

        // Assert outside callback
        assert.equal(capturedPostsRequests.length, 1);
        assert.equal(capturedPostsRequests[0].body.mediaIds, undefined);
    });

    it('failed-delete followed by successful 204 retry sequence verifies attachment payload outside callbacks', async () => {
        const env = createDOMEnvironment();
        const dom = setupComposerDOM(env);

        let deleteFailuresRemaining = 1;
        const capturedPostsRequests = [];
        env.window.fetchHandler = async (url, opts) => {
            if (url === '/api/media') {
                return {
                    ok: true,
                    status: 201,
                    text: async () => JSON.stringify({ success: true, data: { id: 888 } })
                };
            }
            if (url === '/api/media/888' && opts.method === 'DELETE') {
                if (deleteFailuresRemaining > 0) {
                    deleteFailuresRemaining--;
                    return { ok: false, status: 500, text: async () => '' };
                }
                return { ok: true, status: 204, text: async () => '' };
            }
            if (url === '/api/posts') {
                capturedPostsRequests.push({ url, opts, body: opts.body ? JSON.parse(opts.body) : null });
                return { ok: true, status: 201, text: async () => JSON.stringify({ success: true, data: { id: 1000 } }) };
            }
            return { ok: true, status: 200, text: async () => '{}' };
        };

        const ctx = vm.createContext(env.window);
        vm.runInContext(postComposerSource, ctx);

        dom.mediaInput.files = [{ name: 'retry_delete.png', size: 1024 * 10 }];
        dom.mediaInput.dispatchEvent(new MockEvent('change'));
        await new Promise(r => setTimeout(r, 20));

        const removeBtn = dom.mediaPreviewList.querySelector('button[title="Xóa ảnh"]');
        assert.ok(removeBtn);

        // Step 1: First delete attempt fails
        removeBtn.click();
        await new Promise(r => setTimeout(r, 20));

        // Preview card retained in DOM for retry, button re-enabled
        assert.equal(dom.mediaPreviewList.children.length, 1);
        assert.equal(removeBtn.disabled, false);
        assert.ok(dom.mediaStatusBox.textContent.includes('Không thể xóa ảnh'));

        // Step 2: Second delete attempt on the same button succeeds with 204
        removeBtn.click();
        await new Promise(r => setTimeout(r, 20));

        // Preview card removed from DOM upon 204 confirmation
        assert.equal(dom.mediaPreviewList.children.length, 0);

        // Step 3: Form submission after successful retry has no mediaIds
        dom.form.dispatchEvent(new MockEvent('submit', { bubbles: true, cancelable: true }));
        await new Promise(r => setTimeout(r, 20));

        // Verified strictly outside production callbacks
        assert.equal(capturedPostsRequests.length, 1);
        assert.equal(capturedPostsRequests[0].body.mediaIds, undefined);
    });
});
