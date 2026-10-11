/**
 * AloUTE — Post Edit & Delete Automated Regression Tests
 *
 * Built-in Node test suite (node:test + assert/strict + vm).
 * Evaluates production IIFEs (page-actions.js, http-client.js, post-edit-delete.js)
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

const postEditDeleteSource = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/static/js/features/post/post-edit-delete.js'),
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

function querySingleSelector(root, selector) {
    for (const child of root.children) {
        if (matchesSelector(child, selector)) return child;
        const found = querySingleSelector(child, selector);
        if (found) return found;
    }
    return null;
}

function queryAllSingleSelector(root, selector, result = []) {
    for (const child of root.children) {
        if (matchesSelector(child, selector)) result.push(child);
        queryAllSingleSelector(child, selector, result);
    }
    return result;
}

function querySelector(root, selector) {
    if (!selector) return null;
    const parts = selector.trim().split(/\s+/).filter(Boolean);
    if (parts.length <= 1) {
        return querySingleSelector(root, selector);
    }
    let currentMatches = [root];
    for (const part of parts) {
        let nextMatches = [];
        for (const curr of currentMatches) {
            const found = queryAllSingleSelector(curr, part);
            nextMatches.push(...found);
        }
        if (nextMatches.length === 0) return null;
        currentMatches = nextMatches;
    }
    return currentMatches[0] || null;
}

function querySelectorAll(root, selector) {
    if (!selector) return [];
    if (selector.includes(',')) {
        const parts = selector.split(',').map(s => s.trim()).filter(Boolean);
        const results = [];
        for (const p of parts) {
            results.push(...querySelectorAll(root, p));
        }
        return Array.from(new Set(results));
    }
    const parts = selector.trim().split(/\s+/).filter(Boolean);
    if (parts.length <= 1) {
        return queryAllSingleSelector(root, selector);
    }
    let currentMatches = [root];
    for (const part of parts) {
        let nextMatches = [];
        for (const curr of currentMatches) {
            const found = queryAllSingleSelector(curr, part);
            nextMatches.push(...found);
        }
        currentMatches = nextMatches;
    }
    return currentMatches;
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
        this._textContent = '';
        this._innerHTML = '';
        this._value = '';
        this.disabled = false;
        this.hidden = false;
        this.tabIndex = 0;
        this.required = false;
        this.maxLength = -1;
        this.noValidate = false;
        this.rows = 0;
    }

    get textContent() {
        if (this.children.length === 0) {
            return this._textContent || '';
        }
        return this.children.map(c => c.textContent).join('');
    }

    set textContent(v) {
        this._textContent = String(v ?? '');
        this.children = [];
    }

    get value() {
        return this._value;
    }

    set value(v) {
        this._value = String(v ?? '');
    }

    focus() {
        this._focused = true;
        if (this.ownerDocument) {
            this.ownerDocument.activeElement = this;
        }
    }

    get isConnected() {
        let curr = this;
        while (curr) {
            if (curr.tagName === 'BODY' || curr === this.ownerDocument?.body) return true;
            curr = curr.parentElement;
        }
        return false;
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
        const target = {};
        for (const [k, v] of this._attributes.entries()) {
            if (k.startsWith('data-')) {
                const camel = k.slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase());
                target[camel] = v;
            }
        }
        return new Proxy(target, {
            set: (obj, prop, val) => {
                obj[prop] = String(val);
                const kebab = 'data-' + String(prop).replace(/([A-Z])/g, '-$1').toLowerCase();
                this.setAttribute(kebab, val);
                return true;
            },
            deleteProperty: (obj, prop) => {
                delete obj[prop];
                const kebab = 'data-' + String(prop).replace(/([A-Z])/g, '-$1').toLowerCase();
                this.removeAttribute(kebab);
                return true;
            }
        });
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
        const ev = typeof event === 'string' ? {
            type: event,
            target: this,
            defaultPrevented: false,
            preventDefault() { this.defaultPrevented = true; }
        } : event;

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
        this.activeElement = this.body;
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
        const ev = typeof event === 'string' ? {
            type: event,
            target: this,
            defaultPrevented: false,
            preventDefault() { this.defaultPrevented = true; }
        } : event;

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

        return Promise.resolve({
            ok: false,
            status: 404,
            text: async () => JSON.stringify({ success: false, error: { message: 'Not found' } }),
            json: async () => ({ success: false, error: { message: 'Not found' } })
        });
    }
}

// -----------------------------------------------------------------------------
// Test Modal Fixture Builder (Matches SSR fragments/modal.html)
// -----------------------------------------------------------------------------

function setupModalFixtures(doc) {
    // Edit Modal
    const editModal = new MockElement('DIV', 'editPostModal', doc);
    editModal.className = 'modal fade';

    const editDialog = new MockElement('DIV', '', doc);
    const editContent = new MockElement('DIV', '', doc);
    const editHeader = new MockElement('DIV', '', doc);
    const editClose = new MockElement('BUTTON', '', doc);
    editClose.className = 'btn-close';
    editClose.setAttribute('data-bs-dismiss', 'modal');
    editHeader.appendChild(editClose);

    const editForm = new MockElement('FORM', 'edit-post-form', doc);
    const editBody = new MockElement('DIV', '', doc);

    const editStatus = new MockElement('DIV', 'edit-post-status', doc);
    editStatus.className = 'alert d-none py-2 small mb-3';

    const textarea = new MockElement('TEXTAREA', 'edit-post-content', doc);
    textarea.className = 'form-control';
    textarea.name = 'content';
    textarea.maxLength = 4000;

    const charCount = new MockElement('SPAN', 'edit-post-char-count', doc);
    charCount.textContent = '0';

    const errorFeedback = new MockElement('DIV', 'edit-post-error', doc);
    errorFeedback.className = 'invalid-feedback d-none';

    editBody.appendChild(editStatus);
    editBody.appendChild(textarea);
    editBody.appendChild(charCount);
    editBody.appendChild(errorFeedback);

    const editFooter = new MockElement('DIV', '', doc);
    const cancelEditBtn = new MockElement('BUTTON', 'btn-cancel-edit-post', doc);
    cancelEditBtn.className = 'btn btn-secondary';
    cancelEditBtn.textContent = 'Hủy';
    cancelEditBtn.setAttribute('data-bs-dismiss', 'modal');

    const saveBtn = new MockElement('BUTTON', 'btn-save-post', doc);
    saveBtn.type = 'submit';
    saveBtn.className = 'btn btn-primary';
    const saveSpinner = new MockElement('SPAN', 'save-post-spinner', doc);
    saveSpinner.className = 'spinner-border spinner-border-sm me-1 d-none';
    const saveBtnText = new MockElement('SPAN', 'save-post-btn-text', doc);
    saveBtnText.textContent = 'Lưu thay đổi';
    saveBtn.appendChild(saveSpinner);
    saveBtn.appendChild(saveBtnText);

    editFooter.appendChild(cancelEditBtn);
    editFooter.appendChild(saveBtn);

    editForm.appendChild(editBody);
    editForm.appendChild(editFooter);
    editContent.appendChild(editHeader);
    editContent.appendChild(editForm);
    editDialog.appendChild(editContent);
    editModal.appendChild(editDialog);
    doc.body.appendChild(editModal);

    // Delete Modal
    const deleteModal = new MockElement('DIV', 'deletePostModal', doc);
    deleteModal.className = 'modal fade';

    const delDialog = new MockElement('DIV', '', doc);
    const delContent = new MockElement('DIV', '', doc);
    const delHeader = new MockElement('DIV', '', doc);
    const delClose = new MockElement('BUTTON', '', doc);
    delClose.className = 'btn-close';
    delClose.setAttribute('data-bs-dismiss', 'modal');
    delHeader.appendChild(delClose);

    const delBody = new MockElement('DIV', '', doc);
    const delStatus = new MockElement('DIV', 'delete-post-status', doc);
    delStatus.className = 'alert d-none py-2 small mt-3 mb-0';
    delBody.appendChild(delStatus);

    const delFooter = new MockElement('DIV', '', doc);
    const cancelDelBtn = new MockElement('BUTTON', 'btn-cancel-delete-post', doc);
    cancelDelBtn.className = 'btn btn-secondary';
    cancelDelBtn.textContent = 'Hủy';
    cancelDelBtn.setAttribute('data-bs-dismiss', 'modal');

    const confirmDelBtn = new MockElement('BUTTON', 'btn-confirm-delete-post', doc);
    confirmDelBtn.type = 'button';
    confirmDelBtn.className = 'btn btn-danger';
    const delSpinner = new MockElement('SPAN', 'delete-post-spinner', doc);
    delSpinner.className = 'spinner-border spinner-border-sm me-1 d-none';
    const delBtnText = new MockElement('SPAN', 'delete-post-btn-text', doc);
    delBtnText.textContent = 'Xóa bài viết';
    confirmDelBtn.appendChild(delSpinner);
    confirmDelBtn.appendChild(delBtnText);

    delFooter.appendChild(cancelDelBtn);
    delFooter.appendChild(confirmDelBtn);

    delContent.appendChild(delHeader);
    delContent.appendChild(delBody);
    delContent.appendChild(delFooter);
    delDialog.appendChild(delContent);
    deleteModal.appendChild(delDialog);
    doc.body.appendChild(deleteModal);
}

// -----------------------------------------------------------------------------
// Test Environment Setup Helper
// -----------------------------------------------------------------------------

function createEnvironment(options = {}) {
    const document = new MockDocument();
    document.cookie = options.cookie || 'XSRF-TOKEN=test-csrf-token-12345';

    const mainContent = new MockElement('MAIN', 'main-content', document);
    mainContent.setAttribute('tabindex', '-1');
    document.body.appendChild(mainContent);

    if (!options.omitModals) {
        setupModalFixtures(document);
    }

    const fetchHarness = new FetchHarness();

    class MockBootstrapModal {
        constructor(el) {
            this.el = el;
            this.isShown = false;
            this.isTransitioning = false;
            this.manualTransitions = options.manualTransitions || false;
            MockBootstrapModal._instances.set(el, this);
        }
        static getOrCreateInstance(el) {
            return MockBootstrapModal._instances.get(el) || new MockBootstrapModal(el);
        }
        static getInstance(el) {
            return MockBootstrapModal._instances.get(el) || null;
        }
        show() {
            if (this.isShown || this.isTransitioning) return;
            this.isTransitioning = true;
            this.el.classList.add('show');
            this.el.style.display = 'block';
            this.el.dispatchEvent('show.bs.modal');
            if (!this.manualTransitions) {
                this.completeShowTransition();
            }
        }
        completeShowTransition() {
            if (!this.isTransitioning || this.isShown) return;
            this.isTransitioning = false;
            this.isShown = true;
            this.el.dispatchEvent('shown.bs.modal');
        }
        hide() {
            // Real Bootstrap 5 Modal: ignores hide() if not shown or currently transitioning
            if (!this.isShown || this.isTransitioning) {
                return;
            }
            const ev = {
                type: 'hide.bs.modal',
                target: this.el,
                defaultPrevented: false,
                preventDefault() { this.defaultPrevented = true; }
            };
            this.el.dispatchEvent(ev);
            if (ev.defaultPrevented) {
                return;
            }
            this.isTransitioning = true;
            if (!this.manualTransitions) {
                this.completeHideTransition();
            }
        }
        completeHideTransition() {
            if (!this.isTransitioning && !this.isShown) return;
            this.isTransitioning = false;
            this.isShown = false;
            this.el.classList.remove('show');
            this.el.style.display = 'none';
            this.el.dispatchEvent('hidden.bs.modal');
        }
    }
    MockBootstrapModal._instances = new Map();

    const window = {
        document,
        fetch: fetchHarness.fetch.bind(fetchHarness),
        console,
        setTimeout,
        clearTimeout,
        location: {
            pathname: options.pathname || '/feed',
            href: options.pathname || '/feed'
        },
        bootstrap: options.omitBootstrap ? undefined : {
            Modal: MockBootstrapModal
        }
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
        Proxy,
        decodeURIComponent,
        encodeURIComponent
    };

    vm.createContext(sandbox);

    if (!options.omitHttpClient) {
        vm.runInContext(pageActionsSource, sandbox);
        vm.runInContext(httpClientSource, sandbox);
        vm.runInContext(postEditDeleteSource, sandbox);
    } else {
        vm.runInContext(pageActionsSource, sandbox);
        vm.runInContext(postEditDeleteSource, sandbox);
    }

    return {
        window,
        document,
        fetchHarness,
        sandbox,
        MockBootstrapModal
    };
}

function createPostCard({ postId = '101', content = 'Bài viết kiểm thử số 1', canEdit = true, canDelete = true }, ownerDoc = null) {
    const article = new MockElement('ARTICLE', '', ownerDoc);
    article.className = 'post-card';
    article.setAttribute('data-post-id', postId);
    article.setAttribute('data-can-edit', canEdit ? 'true' : 'false');
    article.setAttribute('data-can-delete', canDelete ? 'true' : 'false');

    const header = new MockElement('HEADER', '', ownerDoc);
    header.className = 'post-card-header';

    const dropdown = new MockElement('DIV', '', ownerDoc);
    dropdown.className = 'dropdown';

    const toggleBtn = new MockElement('BUTTON', '', ownerDoc);
    toggleBtn.className = 'btn btn-link text-muted p-1';
    toggleBtn.setAttribute('type', 'button');
    toggleBtn.setAttribute('data-bs-toggle', 'dropdown');
    toggleBtn.setAttribute('aria-expanded', 'false');
    toggleBtn.setAttribute('aria-label', 'Thao tác bài viết');
    dropdown.appendChild(toggleBtn);

    const menu = new MockElement('UL', '', ownerDoc);
    menu.className = 'dropdown-menu dropdown-menu-end shadow';

    if (canEdit) {
        const liEdit = new MockElement('LI', '', ownerDoc);
        const editBtn = new MockElement('BUTTON', '', ownerDoc);
        editBtn.className = 'dropdown-item d-flex align-items-center gap-2';
        editBtn.setAttribute('data-action', 'edit-post');
        editBtn.setAttribute('data-post-id', postId);
        editBtn.setAttribute('data-can-edit', 'true');
        editBtn.textContent = 'Chỉnh sửa bài viết';
        liEdit.appendChild(editBtn);
        menu.appendChild(liEdit);
    }

    if (canDelete) {
        const liDelete = new MockElement('LI', '', ownerDoc);
        const deleteBtn = new MockElement('BUTTON', '', ownerDoc);
        deleteBtn.className = 'dropdown-item text-danger d-flex align-items-center gap-2';
        deleteBtn.setAttribute('data-action', 'delete-post');
        deleteBtn.setAttribute('data-post-id', postId);
        deleteBtn.setAttribute('data-can-delete', 'true');
        deleteBtn.textContent = 'Xóa bài viết';
        liDelete.appendChild(deleteBtn);
        menu.appendChild(liDelete);
    }

    dropdown.appendChild(menu);

    header.appendChild(dropdown);
    article.appendChild(header);

    const contentDiv = new MockElement('DIV', '', ownerDoc);
    contentDiv.className = 'post-card-content';
    const p = new MockElement('P', '', ownerDoc);
    p.textContent = content;
    contentDiv.appendChild(p);
    article.appendChild(contentDiv);

    return article;
}

// -----------------------------------------------------------------------------
// Test Suites
// -----------------------------------------------------------------------------

describe('AloUTE Post Edit & Delete (post-edit-delete.js & core/http-client.js)', () => {

    describe('Suite 1: Edit Post - Canonical Success & Prefilling & Body Shape & Duplicates Synchronization', () => {

        it('prefills modal with exact card content and sends PATCH with ONLY modified content {content}', async () => {
            const env = createEnvironment();
            const card = createPostCard({ postId: '101', content: 'Nội dung ban đầu của bài viết' }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            assert.ok(editBtn, 'Edit button must exist');

            // Click Edit button
            editBtn.click();

            const modal = env.document.getElementById('editPostModal');
            const textarea = env.document.getElementById('edit-post-content');
            assert.ok(modal, 'Modal must exist');
            assert.equal(textarea.value, 'Nội dung ban đầu của bài viết', 'Textarea must be prefilled with exact content');

            // Setup mock endpoint
            env.fetchHarness.on(
                (url, req) => url === '/api/posts/101' && req.method === 'PATCH',
                {
                    status: 200,
                    body: {
                        success: true,
                        data: {
                            id: 101,
                            content: 'Nội dung mới đã được cập nhật thành công!'
                        },
                        meta: null
                    }
                }
            );

            // Change content in textarea
            textarea.value = 'Nội dung mới đã được cập nhật thành công!';

            // Submit form
            const form = env.document.getElementById('edit-post-form');
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });
            await new Promise(r => setTimeout(r, 10));

            // Verify API call
            assert.equal(env.fetchHarness.calls.length, 1);
            const call = env.fetchHarness.calls[0];
            assert.equal(call.method, 'PATCH');
            assert.equal(call.url, '/api/posts/101');
            assert.equal(call.credentials, 'include');
            assert.equal(call.headers['X-XSRF-TOKEN'], 'test-csrf-token-12345');
            assert.equal(call.headers['Content-Type'], 'application/json');

            // Verify payload has ONLY content: never overwrite visibility, allowReply, allowQuote
            const parsedBody = JSON.parse(call.body);
            assert.deepEqual(Object.keys(parsedBody), ['content']);
            assert.equal(parsedBody.content, 'Nội dung mới đã được cập nhật thành công!');

            // Verify DOM synchronization
            const updatedP = card.querySelector('.post-card-content p');
            assert.equal(updatedP.textContent, 'Nội dung mới đã được cập nhật thành công!');

            // Verify success announcement
            const notice = env.document.getElementById('aloute-toast-notice');
            assert.ok(notice);
            assert.match(notice.textContent, /cập nhật bài viết thành công/i);
        });

        it('prefill preserves raw p.textContent without trimming, including whitespace and newlines', async () => {
            const env = createEnvironment();
            const rawContent = '  Dòng 1 có khoảng trắng đầu\n  Dòng 2  ';
            const card = createPostCard({ postId: '103', content: rawContent }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            editBtn.click();

            const textarea = env.document.getElementById('edit-post-content');
            assert.equal(textarea.value, rawContent, 'Must preserve exact raw text without trimming');
        });

        it('synchronizes all duplicate cards (previews/reposts) with the same postId across DOM', async () => {
            const env = createEnvironment();
            const card1 = createPostCard({ postId: '102', content: 'Nội dung cũ' }, env.document);
            const card2 = createPostCard({ postId: '102', content: 'Nội dung cũ' }, env.document);
            env.document.body.appendChild(card1);
            env.document.body.appendChild(card2);

            const editBtn = card1.querySelector('[data-action="edit-post"]');
            editBtn.click();

            const textarea = env.document.getElementById('edit-post-content');
            textarea.value = 'Nội dung đồng bộ cho cả hai thẻ';

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/102' && req.method === 'PATCH',
                {
                    status: 200,
                    body: {
                        success: true,
                        data: { id: 102, content: 'Nội dung đồng bộ cho cả hai thẻ' },
                        meta: null
                    }
                }
            );

            const form = env.document.getElementById('edit-post-form');
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });
            await new Promise(r => setTimeout(r, 10));

            assert.equal(card1.querySelector('.post-card-content p').textContent, 'Nội dung đồng bộ cho cả hai thẻ');
            assert.equal(card2.querySelector('.post-card-content p').textContent, 'Nội dung đồng bộ cho cả hai thẻ');
        });
    });

    describe('Suite 2: Content Length Edge Cases & Unchanged Content', () => {

        it('unchanged content: submittal with identical content dispatches 0 API calls and informs user', async () => {
            const env = createEnvironment();
            const card = createPostCard({ postId: '201', content: 'Nội dung không đổi' }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            editBtn.click();

            // Submit directly without modifying content
            const form = env.document.getElementById('edit-post-form');
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });
            await new Promise(r => setTimeout(r, 10));

            // Must NOT dispatch any fetch call
            assert.equal(env.fetchHarness.calls.length, 0, 'No API call should be sent for unchanged content');

            const notice = env.document.getElementById('aloute-toast-notice');
            assert.ok(notice);
            assert.match(notice.textContent, /không thay đổi/i);
        });

        it('4000 characters: valid boundary length is accepted and dispatched', async () => {
            const env = createEnvironment();
            const card = createPostCard({ postId: '202', content: 'Nội dung ngắn' }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            editBtn.click();

            const exact4000 = 'A'.repeat(4000);
            const textarea = env.document.getElementById('edit-post-content');
            textarea.value = exact4000;

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/202' && req.method === 'PATCH',
                {
                    status: 200,
                    body: { success: true, data: { id: 202, content: exact4000 }, meta: null }
                }
            );

            const form = env.document.getElementById('edit-post-form');
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.fetchHarness.calls.length, 1);
            const body = JSON.parse(env.fetchHarness.calls[0].body);
            assert.equal(body.content.length, 4000);
        });

        it('4001 characters: exceeds maximum length, rejected client-side before sending', async () => {
            const env = createEnvironment();
            const card = createPostCard({ postId: '203', content: 'Nội dung cũ' }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            editBtn.click();

            const length4001 = 'B'.repeat(4001);
            const textarea = env.document.getElementById('edit-post-content');
            textarea.value = length4001;

            const form = env.document.getElementById('edit-post-form');
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });
            await new Promise(r => setTimeout(r, 10));

            // Must NOT dispatch API call
            assert.equal(env.fetchHarness.calls.length, 0);

            // Error feedback displayed
            const statusBox = env.document.getElementById('edit-post-status');
            assert.ok(statusBox);
            assert.match(statusBox.textContent, /4000 ký tự/i);
            assert.equal(textarea.classList.contains('is-invalid'), true);
        });

        it('empty or whitespace-only content: permitted by @Size(max=4000) and dispatched when modified from original', async () => {
            const env = createEnvironment();
            const card = createPostCard({ postId: '204', content: 'Nội dung ban đầu' }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            editBtn.click();

            const textarea = env.document.getElementById('edit-post-content');
            textarea.value = '   '; // whitespace only

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/204' && req.method === 'PATCH',
                {
                    status: 200,
                    body: { success: true, data: { id: 204, content: '   ' }, meta: null }
                }
            );

            const form = env.document.getElementById('edit-post-form');
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });
            await new Promise(r => setTimeout(r, 10));

            // Must dispatch API call
            assert.equal(env.fetchHarness.calls.length, 1);
            const parsed = JSON.parse(env.fetchHarness.calls[0].body);
            assert.equal(parsed.content, '   ');
        });
    });

    describe('Suite 3: Rejection of Malformed, Non-JSON, Noncanonical & Missing Identity Responses', () => {

        it('rejects invalid HTML / non-JSON 200: preserves card and input', async () => {
            const env = createEnvironment();
            const card = createPostCard({ postId: '301', content: 'Nguyên bản' }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            editBtn.click();

            const textarea = env.document.getElementById('edit-post-content');
            textarea.value = 'Chỉnh sửa mới nhưng server lỗi';

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/301' && req.method === 'PATCH',
                { status: 200, body: '<html><body>502 Bad Gateway</body></html>' }
            );

            const form = env.document.getElementById('edit-post-form');
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });
            await new Promise(r => setTimeout(r, 10));

            // Card content must remain unchanged
            assert.equal(card.querySelector('.post-card-content p').textContent, 'Nguyên bản');
            // User input in textarea must be preserved
            assert.equal(textarea.value, 'Chỉnh sửa mới nhưng server lỗi');
            // Error notice shown
            const notice = env.document.getElementById('aloute-toast-notice');
            assert.match(notice.textContent, /thử lại/i);
        });

        it('rejects response with mismatched post identity: preserves card and input', async () => {
            const env = createEnvironment();
            const card = createPostCard({ postId: '302', content: 'Bài viết 302' }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            editBtn.click();

            const textarea = env.document.getElementById('edit-post-content');
            textarea.value = 'Nội dung cập nhật 302';

            // Server erroneously returns data for post 999
            env.fetchHarness.on(
                (url, req) => url === '/api/posts/302' && req.method === 'PATCH',
                {
                    status: 200,
                    body: {
                        success: true,
                        data: { id: 999, content: 'Nội dung của post 999' },
                        meta: null
                    }
                }
            );

            const form = env.document.getElementById('edit-post-form');
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });
            await new Promise(r => setTimeout(r, 10));

            // Card must remain unchanged
            assert.equal(card.querySelector('.post-card-content p').textContent, 'Bài viết 302');
            assert.equal(textarea.value, 'Nội dung cập nhật 302');
        });

        it('rejects PATCH 200 with non-string data.content (null, number, object, missing): never substitutes rawValue, preserves draft and cards', async () => {
            const env = createEnvironment();
            const card = createPostCard({ postId: '304', content: 'Nội dung ban đầu của thẻ' }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            editBtn.click();

            const textarea = env.document.getElementById('edit-post-content');
            textarea.value = 'Nội dung mới đang sửa dở';

            // Server returns 200 but data.content is an object instead of string
            env.fetchHarness.on(
                (url, req) => url === '/api/posts/304' && req.method === 'PATCH',
                {
                    status: 200,
                    body: {
                        success: true,
                        data: {
                            id: 304,
                            content: { text: 'object content' } // INVALID non-string!
                        }
                    }
                }
            );

            const form = env.document.getElementById('edit-post-form');
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });
            await new Promise(r => setTimeout(r, 10));

            // Card MUST remain unchanged (NEVER substitute rawValue on malformed response)
            assert.equal(card.querySelector('.post-card-content p').textContent, 'Nội dung ban đầu của thẻ');
            // Textarea draft MUST be preserved
            assert.equal(textarea.value, 'Nội dung mới đang sửa dở');

            const notice = env.document.getElementById('aloute-toast-notice');
            assert.ok(notice);
            assert.match(notice.textContent, /thử lại/i);
        });

        it('missing window.alouteHttp client triggers safe error without throwing unhandled exception', async () => {
            const env = createEnvironment({ omitHttpClient: true });
            const card = createPostCard({ postId: '303', content: 'Bài viết' }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            editBtn.click();

            const textarea = env.document.getElementById('edit-post-content');
            textarea.value = 'Thay đổi khi không có http client';

            const form = env.document.getElementById('edit-post-form');
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });
            await new Promise(r => setTimeout(r, 10));

            assert.equal(card.querySelector('.post-card-content p').textContent, 'Bài viết');
            const notice = env.document.getElementById('aloute-toast-notice');
            assert.match(notice.textContent, /thử lại/i);
        });
    });

    describe('Suite 4: Table-Driven Error Rollback & Input Preservation (400, 401, 403, 404, 422, 500, Network Rejection)', () => {

        const errorCases = [
            { status: 400, name: 'HTTP 400 Bad Request', expectedMsg: /dữ liệu|không hợp lệ/i },
            { status: 401, name: 'HTTP 401 Unauthorized', expectedMsg: /đăng nhập/i },
            { status: 403, name: 'HTTP 403 Forbidden', expectedMsg: /không có quyền/i },
            { status: 404, name: 'HTTP 404 Not Found', expectedMsg: /không tồn tại|bị xóa/i },
            { status: 422, name: 'HTTP 422 Unprocessable', expectedMsg: /không hợp lệ/i },
            { status: 500, name: 'HTTP 500 Server Error', expectedMsg: /sự cố|thử lại/i },
            { status: 'network', name: 'Network Rejection', expectedMsg: /thử lại/i }
        ];

        for (const ec of errorCases) {
            it(`Edit failure (${ec.name}): preserves card, modal and textarea input, displays safe feedback`, async () => {
                const env = createEnvironment();
                const card = createPostCard({ postId: '401', content: 'Nội dung gốc' }, env.document);
                env.document.body.appendChild(card);

                const editBtn = card.querySelector('[data-action="edit-post"]');
                editBtn.click();

                const textarea = env.document.getElementById('edit-post-content');
                textarea.value = 'Nội dung thử nghiệm thất bại';

                if (ec.status === 'network') {
                    env.fetchHarness.on(
                        (url, req) => url === '/api/posts/401' && req.method === 'PATCH',
                        () => Promise.reject(new Error('Network offline'))
                    );
                } else {
                    env.fetchHarness.on(
                        (url, req) => url === '/api/posts/401' && req.method === 'PATCH',
                        {
                            status: ec.status,
                            body: { success: false, error: { message: 'Operation failed' } }
                        }
                    );
                }

                const form = env.document.getElementById('edit-post-form');
                form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });
                await new Promise(r => setTimeout(r, 15));

                // Card content must NOT be modified
                assert.equal(card.querySelector('.post-card-content p').textContent, 'Nội dung gốc');
                // User input must be preserved
                assert.equal(textarea.value, 'Nội dung thử nghiệm thất bại');
                // Submit button must be re-enabled
                const submitBtn = env.document.getElementById('btn-save-post');
                assert.equal(submitBtn.disabled, false);

                const notice = env.document.getElementById('aloute-toast-notice');
                assert.match(notice.textContent, ec.expectedMsg);
            });
        }
    });

    describe('Suite 5: Delete Post - Confirmation, Cancel, & 204 No Content Success', () => {

        it('cancel confirmation: closes modal, sends 0 API calls, post card remains in DOM', async () => {
            const env = createEnvironment();
            const card = createPostCard({ postId: '501', content: 'Bài viết không bị xóa' }, env.document);
            env.document.body.appendChild(card);

            const deleteBtn = card.querySelector('[data-action="delete-post"]');
            deleteBtn.click();

            const modal = env.document.getElementById('deletePostModal');
            assert.ok(modal);
            assert.equal(modal.dataset.postId, '501');

            // Click Cancel
            const cancelBtn = env.document.getElementById('btn-cancel-delete-post');
            cancelBtn.click();

            // 0 API calls
            assert.equal(env.fetchHarness.calls.length, 0);
            // Card remains in DOM
            assert.ok(env.document.querySelector('.post-card[data-post-id="501"]'));
        });

        it('confirm delete: sends DELETE request, handles 204 No Content without body parsing, removes card and duplicates', async () => {
            const env = createEnvironment();
            const card1 = createPostCard({ postId: '502', content: 'Bài viết 502' }, env.document);
            const card2 = createPostCard({ postId: '502', content: 'Bản sao bài viết 502' }, env.document);
            env.document.body.appendChild(card1);
            env.document.body.appendChild(card2);

            const deleteBtn = card1.querySelector('[data-action="delete-post"]');
            deleteBtn.click();

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/502' && req.method === 'DELETE',
                { status: 204, body: '' }
            );

            const confirmBtn = env.document.getElementById('btn-confirm-delete-post');
            confirmBtn.click();
            await new Promise(r => setTimeout(r, 10));

            // Verify API call
            assert.equal(env.fetchHarness.calls.length, 1);
            const call = env.fetchHarness.calls[0];
            assert.equal(call.method, 'DELETE');
            assert.equal(call.url, '/api/posts/502');
            assert.equal(call.credentials, 'include');
            assert.equal(call.headers['X-XSRF-TOKEN'], 'test-csrf-token-12345');

            // Both instances must be removed from the DOM
            assert.equal(env.document.querySelectorAll('.post-card[data-post-id="502"]').length, 0);

            const notice = env.document.getElementById('aloute-toast-notice');
            assert.match(notice.textContent, /xóa bài viết thành công/i);
        });

        it('delete failure (HTTP 403 Forbidden): preserves card in DOM, unlocks confirm button, announces error', async () => {
            const env = createEnvironment();
            const card = createPostCard({ postId: '503', content: 'Bài viết được bảo vệ' }, env.document);
            env.document.body.appendChild(card);

            const deleteBtn = card.querySelector('[data-action="delete-post"]');
            deleteBtn.click();

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/503' && req.method === 'DELETE',
                { status: 403, body: { success: false, error: { message: 'Access Denied' } } }
            );

            const confirmBtn = env.document.getElementById('btn-confirm-delete-post');
            confirmBtn.click();
            await new Promise(r => setTimeout(r, 10));

            // Card must NOT be removed
            assert.ok(env.document.querySelector('.post-card[data-post-id="503"]'));
            // Button unlocked
            assert.equal(confirmBtn.disabled, false);

            const notice = env.document.getElementById('aloute-toast-notice');
            assert.match(notice.textContent, /không có quyền/i);
        });
    });

    describe('Suite 6: Safe Post Detail Deletion & Reply Behavior', () => {

        it('deleting post while on Post Detail page disables reply form, sets alert, and triggers feed navigation', async () => {
            const env = createEnvironment({ pathname: '/posts/601' });

            const card = createPostCard({ postId: '601', content: 'Chi tiết bài viết 601' }, env.document);
            env.document.body.appendChild(card);

            // Add reply form for post 601
            const replyForm = new MockElement('FORM', 'reply-form', env.document);
            replyForm.setAttribute('data-post-id', '601');

            const replyTextarea = new MockElement('TEXTAREA', 'commentContent', env.document);
            const replySubmit = new MockElement('BUTTON', 'btn-submit-reply', env.document);
            const replyStatus = new MockElement('DIV', 'reply-status', env.document);
            replyStatus.className = 'alert d-none';

            replyForm.appendChild(replyStatus);
            replyForm.appendChild(replyTextarea);
            replyForm.appendChild(replySubmit);
            env.document.body.appendChild(replyForm);

            const deleteBtn = card.querySelector('[data-action="delete-post"]');
            deleteBtn.click();

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/601' && req.method === 'DELETE',
                { status: 204, body: '' }
            );

            const confirmBtn = env.document.getElementById('btn-confirm-delete-post');
            confirmBtn.click();
            await new Promise(r => setTimeout(r, 10));

            // Card removed
            assert.equal(env.document.querySelector('.post-card[data-post-id="601"]'), null);

            // Reply form safely disabled
            assert.equal(replyTextarea.disabled, true);
            assert.equal(replySubmit.disabled, true);

            // Reply status alert updated
            assert.match(replyStatus.textContent, /đã bị xóa/i);
            assert.equal(replyStatus.classList.contains('d-none'), false);
        });

        it('id-prefix regression: deleting post 1 while on /posts/10 does NOT disable reply form on /posts/10 and does NOT navigate to feed', async () => {
            const env = createEnvironment({ pathname: '/posts/10' });

            // Embedded child/card for post 1 on the detail page of post 10
            const card1 = createPostCard({ postId: '1', content: 'Bài viết 1' }, env.document);
            env.document.body.appendChild(card1);

            // Reply form belongs to post 10
            const replyForm = new MockElement('FORM', 'reply-form', env.document);
            replyForm.setAttribute('data-post-id', '10');
            const replyTextarea = new MockElement('TEXTAREA', 'commentContent', env.document);
            const replySubmit = new MockElement('BUTTON', 'btn-submit-reply', env.document);
            replyForm.appendChild(replyTextarea);
            replyForm.appendChild(replySubmit);
            env.document.body.appendChild(replyForm);

            const deleteBtn = card1.querySelector('[data-action="delete-post"]');
            deleteBtn.click();

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/1' && req.method === 'DELETE',
                { status: 204, body: '' }
            );

            const confirmBtn = env.document.getElementById('btn-confirm-delete-post');
            confirmBtn.click();
            await new Promise(r => setTimeout(r, 10));

            // Card 1 removed
            assert.equal(env.document.querySelector('.post-card[data-post-id="1"]'), null);

            // Detail reply form of post 10 must NOT be disabled
            assert.equal(replyTextarea.disabled, false);
            assert.equal(replySubmit.disabled, false);

            // Pathname must NOT be changed to /feed
            assert.equal(env.window.location.href, '/posts/10');
        });
    });

    describe('Suite 7: Permissions & Capability Enforcement', () => {

        it('disallows edit action when data-can-edit is false', async () => {
            const env = createEnvironment();
            const card = createPostCard({ postId: '701', content: 'Bài viết không có quyền sửa', canEdit: false }, env.document);
            env.document.body.appendChild(card);

            // Manually inject button with data-action="edit-post"
            const unauthorizedBtn = new MockElement('BUTTON', '', env.document);
            unauthorizedBtn.setAttribute('data-action', 'edit-post');
            unauthorizedBtn.setAttribute('data-post-id', '701');
            unauthorizedBtn.setAttribute('data-can-edit', 'false');
            card.appendChild(unauthorizedBtn);

            unauthorizedBtn.click();

            const notice = env.document.getElementById('aloute-toast-notice');
            assert.ok(notice);
            assert.match(notice.textContent, /không có quyền chỉnh sửa/i);

            // Modal must NOT be open
            const modal = env.document.getElementById('editPostModal');
            if (modal) {
                assert.notEqual(modal.style.display, 'block');
            }
        });

        it('disallows delete action when data-can-delete is false', async () => {
            const env = createEnvironment();
            const card = createPostCard({ postId: '702', content: 'Bài viết không có quyền xóa', canDelete: false }, env.document);
            env.document.body.appendChild(card);

            const unauthorizedBtn = new MockElement('BUTTON', '', env.document);
            unauthorizedBtn.setAttribute('data-action', 'delete-post');
            unauthorizedBtn.setAttribute('data-post-id', '702');
            unauthorizedBtn.setAttribute('data-can-delete', 'false');
            card.appendChild(unauthorizedBtn);

            unauthorizedBtn.click();

            const notice = env.document.getElementById('aloute-toast-notice');
            assert.ok(notice);
            assert.match(notice.textContent, /không có quyền xóa/i);
        });
    });

    describe('Suite 8: Concurrency, Pending Locks & Per-Post Serialization', () => {

        it('drops rapid repeated submittals on save button while edit request is in-flight', async () => {
            const env = createEnvironment();
            const card = createPostCard({ postId: '801', content: 'Nội dung ban đầu' }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            editBtn.click();

            const textarea = env.document.getElementById('edit-post-content');
            textarea.value = 'Nội dung sửa đổi';

            let resolveRequest;
            env.fetchHarness.on(
                (url, req) => url === '/api/posts/801' && req.method === 'PATCH',
                () => new Promise(res => { resolveRequest = res; })
            );

            const form = env.document.getElementById('edit-post-form');

            // Dispatch first submit
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });

            // Rapid second and third submits
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });

            assert.equal(env.fetchHarness.calls.length, 1, 'Only 1 request must be dispatched while in-flight');

            resolveRequest({
                status: 200,
                body: { success: true, data: { id: 801, content: 'Nội dung sửa đổi' }, meta: null }
            });
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.fetchHarness.calls.length, 1);
        });

        it('drops rapid repeated clicks on confirm delete while delete is in-flight', async () => {
            const env = createEnvironment();
            const card = createPostCard({ postId: '802', content: 'Bài viết' }, env.document);
            env.document.body.appendChild(card);

            const deleteBtn = card.querySelector('[data-action="delete-post"]');
            deleteBtn.click();

            let resolveRequest;
            env.fetchHarness.on(
                (url, req) => url === '/api/posts/802' && req.method === 'DELETE',
                () => new Promise(res => { resolveRequest = res; })
            );

            const confirmBtn = env.document.getElementById('btn-confirm-delete-post');

            // Click multiple times rapidly
            confirmBtn.click();
            confirmBtn.click();
            confirmBtn.click();

            assert.equal(env.fetchHarness.calls.length, 1);

            resolveRequest({ status: 204, body: '' });
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.fetchHarness.calls.length, 1);
        });

        it('serializes EDIT and DELETE per post: blocks concurrent edit and delete, and guards opening action while pending', async () => {
            const env = createEnvironment();
            const card = createPostCard({ postId: '803', content: 'Nội dung ban đầu' }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            const deleteBtn = card.querySelector('[data-action="delete-post"]');

            // Open edit modal
            editBtn.click();

            const textarea = env.document.getElementById('edit-post-content');
            textarea.value = 'Nội dung sửa đổi đang chờ server';

            let resolveEdit;
            env.fetchHarness.on(
                (url, req) => url === '/api/posts/803' && req.method === 'PATCH',
                () => new Promise(res => { resolveEdit = res; })
            );

            const form = env.document.getElementById('edit-post-form');
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });

            // Request is in-flight: inFlight has '803'
            assert.equal(env.fetchHarness.calls.length, 1);

            // Attempting to open delete action while edit is pending must be blocked
            deleteBtn.click();
            const notice = env.document.getElementById('aloute-toast-notice');
            assert.ok(notice);
            assert.match(notice.textContent, /đang được xử lý/i);

            // Delete modal should not have post id 803 active
            const deleteModal = env.document.getElementById('deletePostModal');
            assert.notEqual(deleteModal.getAttribute('data-post-id'), '803');

            // Resolve edit
            resolveEdit({
                status: 200,
                body: { success: true, data: { id: 803, content: 'Nội dung sửa đổi đang chờ server' }, meta: null }
            });
            await new Promise(r => setTimeout(r, 10));

            // Now edit is complete, delete can be opened
            deleteBtn.click();
            assert.equal(deleteModal.getAttribute('data-post-id'), '803');
        });

        it('prevents modal dismissal (hide.bs.modal and cancel button) while mutation is in-flight', async () => {
            const env = createEnvironment();
            const card = createPostCard({ postId: '804', content: 'Nội dung' }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            editBtn.click();

            const textarea = env.document.getElementById('edit-post-content');
            textarea.value = 'Nội dung mới';

            let resolveEdit;
            env.fetchHarness.on(
                (url, req) => url === '/api/posts/804' && req.method === 'PATCH',
                () => new Promise(res => { resolveEdit = res; })
            );

            const form = env.document.getElementById('edit-post-form');
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });

            const editModal = env.document.getElementById('editPostModal');
            const modalInstance = env.MockBootstrapModal.getInstance(editModal);
            assert.equal(modalInstance.isShown, true);

            // User tries to dismiss modal (e.g. click Cancel button) while in-flight
            const cancelBtn = env.document.getElementById('btn-cancel-edit-post');
            cancelBtn.click();

            // Modal instance must STILL be shown because dismissal is guarded
            assert.equal(modalInstance.isShown, true);

            // Resolve request
            resolveEdit({
                status: 200,
                body: { success: true, data: { id: 804, content: 'Nội dung mới' }, meta: null }
            });
            await new Promise(r => setTimeout(r, 10));

            // After success, modal is safely closed
            assert.equal(modalInstance.isShown, false);
        });
    });

    describe('Suite 9: Accessibility Focus Management & Bootstrap Graceful Fallback', () => {

        it('focuses textarea on shown.bs.modal, restores focus to visible dropdown toggle on hidden.bs.modal (even after dropdown closure)', async () => {
            const env = createEnvironment();
            const card = createPostCard({ postId: '901', content: 'Test focus' }, env.document);
            env.document.body.appendChild(card);

            const toggleBtn = card.querySelector('[data-bs-toggle="dropdown"]');
            const menu = card.querySelector('.dropdown-menu');
            const editBtn = card.querySelector('[data-action="edit-post"]');
            assert.ok(toggleBtn, 'Visible dropdown toggle must exist');
            assert.ok(menu, 'Dropdown menu must exist');
            assert.ok(editBtn, 'Edit button must exist');

            // Simulate dropdown opened by user
            toggleBtn.setAttribute('aria-expanded', 'true');
            menu.classList.add('show');

            // User clicks edit-post inside dropdown
            editBtn.click();

            // Bootstrap auto-closes dropdown before modal finishes opening/closing
            toggleBtn.setAttribute('aria-expanded', 'false');
            menu.classList.remove('show');
            menu.style.display = 'none';

            const textarea = env.document.getElementById('edit-post-content');
            assert.equal(env.document.activeElement, textarea, 'Textarea must be focused on modal show');

            // Dismiss modal (e.g. click Cancel)
            const cancelBtn = env.document.getElementById('btn-cancel-edit-post');
            cancelBtn.click();

            // Focus must return to visible dropdown toggle, NOT the hidden editBtn
            assert.equal(env.document.activeElement, toggleBtn, 'Focus must be restored to visible dropdown toggle');
            assert.notEqual(env.document.activeElement, editBtn, 'Must NOT attempt to focus hidden dropdown item');
        });

        it('safe focus fallback to connected, focusable main container when initiating card was deleted', async () => {
            const env = createEnvironment();
            const card = createPostCard({ postId: '902', content: 'Test focus delete' }, env.document);
            env.document.body.appendChild(card);

            const deleteBtn = card.querySelector('[data-action="delete-post"]');
            deleteBtn.click();

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/902' && req.method === 'DELETE',
                { status: 204, body: '' }
            );

            const confirmBtn = env.document.getElementById('btn-confirm-delete-post');
            confirmBtn.click();
            await new Promise(r => setTimeout(r, 10));

            const mainEl = env.document.getElementById('main-content');
            assert.ok(mainEl, 'Main content container must exist as fallback target');
            assert.equal(env.document.activeElement, mainEl, 'Focus must fall back to main content container');
            assert.equal(env.document.activeElement.isConnected, true, 'Fallback target must be connected');
            assert.equal(env.document.activeElement.getAttribute('tabindex'), '-1', 'Fallback target must be focusable (tabindex="-1")');
        });

        it('displays safe Vietnamese feedback when Bootstrap 5 is unavailable without rendering broken fallback modal', async () => {
            const env = createEnvironment({ omitBootstrap: true });
            const card = createPostCard({ postId: '903', content: 'Bài viết khi không có BS5' }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            editBtn.click();

            const notice = env.document.getElementById('aloute-toast-notice');
            assert.ok(notice);
            assert.match(notice.textContent, /chưa sẵn sàng|tải lại trang/i);

            // Modal must NOT be forced into display: block
            const modal = env.document.getElementById('editPostModal');
            assert.notEqual(modal.style.display, 'block');
        });
    });

    describe('Suite 10: Coexistence with page-actions.js in Actual Layout Order', () => {

        it('edit-post and delete-post are NOT intercepted by page-actions placeholder notices', async () => {
            const env = createEnvironment();
            const card = createPostCard({ postId: '1001', content: 'Bài viết thử layout order' }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            editBtn.click();

            // Must NOT show "API sửa bài viết chưa được ghép" placeholder!
            const noticeAfterEdit = env.document.getElementById('aloute-toast-notice');
            if (noticeAfterEdit && !noticeAfterEdit.hidden) {
                assert.doesNotMatch(noticeAfterEdit.textContent, /chưa được ghép/i);
            }

            const deleteBtn = card.querySelector('[data-action="delete-post"]');
            deleteBtn.click();

            // Must NOT show "API xóa bài viết chưa được ghép" placeholder!
            const noticeAfterDelete = env.document.getElementById('aloute-toast-notice');
            if (noticeAfterDelete && !noticeAfterDelete.hidden) {
                assert.doesNotMatch(noticeAfterDelete.textContent, /chưa được ghép/i);
            }
        });
    });

    describe('Suite 11: Security, CSRF & XSS Sanitization', () => {

        it('preserves base64 padding "=" in CSRF cookie header on PATCH and DELETE mutations', async () => {
            const env = createEnvironment({ cookie: 'XSRF-TOKEN=YWJjZGVmZ2hpamsxbTIzNDU2Nzg5MA==' });
            const card = createPostCard({ postId: '1101', content: 'Bài viết test CSRF' }, env.document);
            env.document.body.appendChild(card);

            const deleteBtn = card.querySelector('[data-action="delete-post"]');
            deleteBtn.click();

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/1101' && req.method === 'DELETE',
                { status: 204, body: '' }
            );

            const confirmBtn = env.document.getElementById('btn-confirm-delete-post');
            confirmBtn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.fetchHarness.calls[0].headers['X-XSRF-TOKEN'], 'YWJjZGVmZ2hpamsxbTIzNDU2Nzg5MA==');
        });

        it('escapes and sanitizes HTML content in updated cards using textContent (preventing XSS)', async () => {
            const env = createEnvironment();
            const card = createPostCard({ postId: '1102', content: 'Bài viết ban đầu' }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            editBtn.click();

            const xssPayload = '<img src=x onerror=alert(1)><strong>bold</strong>';
            const textarea = env.document.getElementById('edit-post-content');
            textarea.value = xssPayload;

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/1102' && req.method === 'PATCH',
                {
                    status: 200,
                    body: { success: true, data: { id: 1102, content: xssPayload }, meta: null }
                }
            );

            const form = env.document.getElementById('edit-post-form');
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });
            await new Promise(r => setTimeout(r, 10));

            const p = card.querySelector('.post-card-content p');
            assert.equal(p.textContent, xssPayload, 'Must be stored as plain text');
            assert.equal(p.children.length, 0, 'Must NOT create inner elements (no XSS execution)');
        });
    });

    describe('Suite 12: Real Asynchronous Bootstrap Modal Lifecycle & Ignored Hide Race Protection (QA F-1)', () => {

        it('early Cancel click during show transition is queued and automatically dismisses modal upon shown.bs.modal', async () => {
            const env = createEnvironment({ manualTransitions: true });
            const card = createPostCard({ postId: '1201', content: 'Bài viết test early cancel' }, env.document);
            env.document.body.appendChild(card);

            const toggleBtn = card.querySelector('[data-bs-toggle="dropdown"]');
            const editBtn = card.querySelector('[data-action="edit-post"]');

            // Open modal: initiates show transition
            editBtn.click();

            const modalEl = env.document.getElementById('editPostModal');
            const modalInst = env.window.bootstrap.Modal.getInstance(modalEl);
            assert.ok(modalInst);
            assert.equal(modalInst.isTransitioning, true, 'Modal must be transitioning into view');
            assert.equal(modalInst.isShown, false, 'Modal is not yet shown');

            // Early Cancel click: in real Bootstrap, hide() is ignored while transitioning
            const cancelBtn = env.document.getElementById('btn-cancel-edit-post');
            cancelBtn.click();
            assert.equal(modalInst.isTransitioning, true, 'Modal must still be transitioning (hide was ignored by Bootstrap)');

            // Show transition completes (shown.bs.modal fires)
            modalInst.completeShowTransition();

            // Lifecycle coordinator intercepts shown and immediately triggers hide
            assert.equal(modalInst.isTransitioning, true, 'Modal must now be transitioning out');

            // Hide transition completes (hidden.bs.modal fires)
            modalInst.completeHideTransition();
            assert.equal(modalInst.isShown, false, 'Modal must be fully hidden');
            assert.equal(modalEl.style.display, 'none');

            // Focus must return to visible dropdown toggle
            assert.equal(env.document.activeElement, toggleBtn);
        });

        it('early close button (.btn-close) during show transition is queued and dismisses modal upon shown', async () => {
            const env = createEnvironment({ manualTransitions: true });
            const card = createPostCard({ postId: '1202', content: 'Bài viết test close button' }, env.document);
            env.document.body.appendChild(card);

            const toggleBtn = card.querySelector('[data-bs-toggle="dropdown"]');
            const deleteBtn = card.querySelector('[data-action="delete-post"]');

            deleteBtn.click();

            const modalEl = env.document.getElementById('deletePostModal');
            const modalInst = env.window.bootstrap.Modal.getInstance(modalEl);
            assert.equal(modalInst.isTransitioning, true);

            // User clicks .btn-close during show transition
            const closeBtn = modalEl.querySelector('.btn-close');
            closeBtn.click();

            // Transition finishes: modal closes
            modalInst.completeShowTransition();
            modalInst.completeHideTransition();

            assert.equal(modalInst.isShown, false);
            assert.equal(env.document.activeElement, toggleBtn);
        });

        it('early Escape key during show transition is queued and dismisses modal upon shown', async () => {
            const env = createEnvironment({ manualTransitions: true });
            const card = createPostCard({ postId: '1203', content: 'Bài viết test early ESC' }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            editBtn.click();

            const modalEl = env.document.getElementById('editPostModal');
            const modalInst = env.window.bootstrap.Modal.getInstance(modalEl);
            assert.equal(modalInst.isTransitioning, true);

            // Escape key pressed during show transition
            modalEl.dispatchEvent({ type: 'keydown', key: 'Escape' });

            modalInst.completeShowTransition();
            modalInst.completeHideTransition();

            assert.equal(modalInst.isShown, false);
        });

        it('early backdrop click during show transition is queued and dismisses modal upon shown', async () => {
            const env = createEnvironment({ manualTransitions: true });
            const card = createPostCard({ postId: '1204', content: 'Bài viết test early backdrop' }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            editBtn.click();

            const modalEl = env.document.getElementById('editPostModal');
            const modalInst = env.window.bootstrap.Modal.getInstance(modalEl);
            assert.equal(modalInst.isTransitioning, true);

            // Backdrop click during show transition
            modalEl.dispatchEvent({ type: 'click', target: modalEl });

            modalInst.completeShowTransition();
            modalInst.completeHideTransition();

            assert.equal(modalInst.isShown, false);
        });

        it('fast PATCH 200 arriving during show transition dismisses modal cleanly once shown without hanging', async () => {
            const env = createEnvironment({ manualTransitions: true });
            const card = createPostCard({ postId: '1205', content: 'Nội dung ban đầu' }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            editBtn.click();

            const modalEl = env.document.getElementById('editPostModal');
            const modalInst = env.window.bootstrap.Modal.getInstance(modalEl);
            assert.equal(modalInst.isTransitioning, true);

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/1205' && req.method === 'PATCH',
                {
                    status: 200,
                    body: { success: true, data: { id: 1205, content: 'Nội dung cập nhật siêu tốc' }, meta: null }
                }
            );

            // Submit edit while show transition is in progress
            const textarea = env.document.getElementById('edit-post-content');
            textarea.value = 'Nội dung cập nhật siêu tốc';

            const form = env.document.getElementById('edit-post-form');
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });
            await new Promise(r => setTimeout(r, 10));

            // Card DOM is already synchronized
            const p = card.querySelector('.post-card-content p');
            assert.equal(p.textContent, 'Nội dung cập nhật siêu tốc');

            // Modal completes show transition, which automatically triggers hide
            modalInst.completeShowTransition();
            assert.equal(modalInst.isTransitioning, true, 'Hide transition must be triggered');

            modalInst.completeHideTransition();
            assert.equal(modalInst.isShown, false, 'Modal must be cleanly hidden without hanging');
        });

        it('fast DELETE 204 arriving during show transition strictly awaits hidden.bs.modal before removing cards and navigating', async () => {
            const env = createEnvironment({ manualTransitions: true, pathname: '/posts/1206' });
            const card = createPostCard({ postId: '1206', content: 'Bài viết sắp bị xóa' }, env.document);
            env.document.body.appendChild(card);

            const replyForm = new MockElement('FORM', 'reply-form', env.document);
            replyForm.setAttribute('data-post-id', '1206');
            const replyTextarea = new MockElement('TEXTAREA', 'commentContent', env.document);
            const replySubmit = new MockElement('BUTTON', 'btn-submit-reply', env.document);
            const replyStatus = new MockElement('DIV', 'reply-status', env.document);
            replyForm.appendChild(replyTextarea);
            replyForm.appendChild(replySubmit);
            replyForm.appendChild(replyStatus);
            env.document.body.appendChild(replyForm);

            const deleteBtn = card.querySelector('[data-action="delete-post"]');
            deleteBtn.click();

            const modalEl = env.document.getElementById('deletePostModal');
            const modalInst = env.window.bootstrap.Modal.getInstance(modalEl);
            assert.equal(modalInst.isTransitioning, true);

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/1206' && req.method === 'DELETE',
                { status: 204, body: '' }
            );

            // Fast delete confirmation during show transition
            const confirmBtn = env.document.getElementById('btn-confirm-delete-post');
            confirmBtn.click();
            await new Promise(r => setTimeout(r, 10));

            // CRITICAL CHECK 1: Card must NOT be removed before hidden.bs.modal
            assert.ok(env.document.querySelector('.post-card[data-post-id="1206"]'), 'Post card must still exist before hidden');
            assert.equal(env.window.location.href, '/posts/1206', 'Must NOT navigate before hidden');

            // Complete show transition
            modalInst.completeShowTransition();

            // CRITICAL CHECK 2: During hide transition, card must STILL not be removed
            assert.ok(env.document.querySelector('.post-card[data-post-id="1206"]'), 'Post card must still exist during hide transition');

            // Complete hide transition (fires hidden.bs.modal)
            modalInst.completeHideTransition();

            // CRITICAL CHECK 3: Card is removed strictly on hidden.bs.modal
            assert.equal(env.document.querySelector('.post-card[data-post-id="1206"]'), null, 'Post card must be removed on hidden');
            assert.equal(env.window.location.href, '/feed', 'Must navigate to /feed on hidden without guess timer');
            assert.equal(replyTextarea.disabled, true);
            assert.equal(replySubmit.disabled, true);

            // Focus falls back to accessible main content
            assert.equal(env.document.activeElement.id, 'main-content');
        });

        it('no stale dismissal on reopen: reopening modal after early dismissal stays open on shown', async () => {
            const env = createEnvironment({ manualTransitions: true });
            const card = createPostCard({ postId: '1207', content: 'Bài viết test reopen' }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            editBtn.click();

            const modalEl = env.document.getElementById('editPostModal');
            const modalInst = env.window.bootstrap.Modal.getInstance(modalEl);

            // Early cancel and close
            const cancelBtn = env.document.getElementById('btn-cancel-edit-post');
            cancelBtn.click();
            modalInst.completeShowTransition();
            modalInst.completeHideTransition();
            assert.equal(modalInst.isShown, false);

            // Reopen modal
            editBtn.click();
            assert.equal(modalInst.isTransitioning, true);

            // Complete show transition on reopen
            modalInst.completeShowTransition();

            // MUST stay open (no stale dismissOnShown flag!)
            assert.equal(modalInst.isShown, true, 'Modal must stay open on reopen');
            assert.equal(modalInst.isTransitioning, false);
            assert.equal(modalEl.style.display, 'block');

            const textarea = env.document.getElementById('edit-post-content');
            assert.equal(env.document.activeElement, textarea, 'Textarea must be focused');
        });

        it('pending mutation blocks early dismiss intent (Cancel, ESC, backdrop) and prevents accidental dismissal', async () => {
            const env = createEnvironment({ manualTransitions: true });
            const card = createPostCard({ postId: '1208', content: 'Bài viết đang lưu' }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            editBtn.click();

            const modalEl = env.document.getElementById('editPostModal');
            const modalInst = env.window.bootstrap.Modal.getInstance(modalEl);

            let resolveRequest;
            env.fetchHarness.on(
                (url, req) => url === '/api/posts/1208' && req.method === 'PATCH',
                () => new Promise(res => { resolveRequest = res; })
            );

            const textarea = env.document.getElementById('edit-post-content');
            textarea.value = 'Nội dung đang lưu dở';

            const form = env.document.getElementById('edit-post-form');
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });

            // In-flight: test Cancel click, Escape key, backdrop click
            const cancelBtn = env.document.getElementById('btn-cancel-edit-post');
            const cancelEv = { type: 'click', target: cancelBtn, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; } };
            cancelBtn.dispatchEvent(cancelEv);
            assert.equal(cancelEv.defaultPrevented, true, 'Cancel click must be prevented during in-flight mutation');

            const escEv = { type: 'keydown', key: 'Escape', defaultPrevented: false, preventDefault() { this.defaultPrevented = true; } };
            modalEl.dispatchEvent(escEv);
            assert.equal(escEv.defaultPrevented, true, 'Escape must be prevented during in-flight mutation');

            // Complete show transition: modal remains OPEN
            modalInst.completeShowTransition();
            assert.equal(modalInst.isShown, true, 'Modal must remain open despite dismiss attempts while in-flight');

            // Resolve mutation
            resolveRequest({
                status: 200,
                body: { success: true, data: { id: 1208, content: 'Nội dung đang lưu dở' }, meta: null }
            });
            await new Promise(r => setTimeout(r, 10));

            // Now modal dismisses cleanly
            modalInst.completeHideTransition();
            assert.equal(modalInst.isShown, false, 'Modal dismisses upon successful completion');
        });

        it('mutation failure preserves input, keeps modal open, and allows user dismissal', async () => {
            const env = createEnvironment({ manualTransitions: true });
            const card = createPostCard({ postId: '1209', content: 'Bài viết test lỗi' }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            editBtn.click();

            const modalEl = env.document.getElementById('editPostModal');
            const modalInst = env.window.bootstrap.Modal.getInstance(modalEl);
            modalInst.completeShowTransition();

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/1209' && req.method === 'PATCH',
                { status: 500, body: { message: 'Internal Server Error' } }
            );

            const textarea = env.document.getElementById('edit-post-content');
            textarea.value = 'Bản nháp quý giá';

            const form = env.document.getElementById('edit-post-form');
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });
            await new Promise(r => setTimeout(r, 10));

            // Modal remains open and draft is preserved
            assert.equal(modalInst.isShown, true, 'Modal must remain open on server error');
            assert.equal(textarea.value, 'Bản nháp quý giá', 'User input must be preserved');

            const statusBox = env.document.getElementById('edit-post-status');
            assert.match(statusBox.textContent, /sự cố/i);

            // User can now cancel
            const cancelBtn = env.document.getElementById('btn-cancel-edit-post');
            cancelBtn.click();
            modalInst.completeHideTransition();
            assert.equal(modalInst.isShown, false);
        });

        it('after PATCH 200 during remaining SHOW and HIDE phases, second Save/form submit dispatches exactly 1 request, preserves disabled/busy, and unlocks on hidden', async () => {
            const env = createEnvironment({ manualTransitions: true });
            const card = createPostCard({ postId: '1210', content: 'Nội dung gốc 1210' }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            editBtn.click();

            const modalEl = env.document.getElementById('editPostModal');
            const modalInst = env.window.bootstrap.Modal.getInstance(modalEl);
            assert.equal(modalInst.isTransitioning, true, 'Modal is in SHOW transition');
            assert.equal(modalInst.isShown, false);

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/1210' && req.method === 'PATCH',
                {
                    status: 200,
                    body: { success: true, data: { id: 1210, content: 'Nội dung cập nhật 1210' }, meta: null }
                }
            );

            const textarea = env.document.getElementById('edit-post-content');
            textarea.value = 'Nội dung cập nhật 1210';
            const submitBtn = env.document.getElementById('btn-save-post');
            const form = env.document.getElementById('edit-post-form');

            // 1st submit while still in SHOW transition
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.fetchHarness.calls.length, 1, 'Exactly 1 PATCH dispatched');
            assert.equal(submitBtn.disabled, true, 'Submit button must stay disabled after success during transition');
            assert.equal(submitBtn.getAttribute('aria-busy'), 'true', 'Submit button must preserve aria-busy');

            // 2nd submit while still in remaining SHOW transition
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });
            await new Promise(r => setTimeout(r, 10));
            assert.equal(env.fetchHarness.calls.length, 1, 'Second submit in SHOW transition must NOT dispatch second request');

            // Complete SHOW transition -> enters HIDE transition
            modalInst.completeShowTransition();
            assert.equal(modalInst.isTransitioning, true, 'Modal must transition to hide');
            assert.equal(submitBtn.disabled, true, 'Submit button must stay disabled in HIDE transition');

            // 3rd submit during HIDE transition
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });
            await new Promise(r => setTimeout(r, 10));
            assert.equal(env.fetchHarness.calls.length, 1, 'Third submit in HIDE transition must NOT dispatch second request');

            // Complete HIDE transition (hidden.bs.modal)
            modalInst.completeHideTransition();
            assert.equal(modalInst.isShown, false);
            assert.equal(submitBtn.disabled, false, 'Submit button must reset to enabled on hidden');

            // Next legitimate modal open unlocks cleanly
            editBtn.click();
            assert.equal(modalInst.isTransitioning, true);
            modalInst.completeShowTransition();
            assert.equal(modalInst.isShown, true);
            assert.equal(submitBtn.disabled, false);
        });

        it('after PATCH 200 during HIDE phase after normal show, second Save dispatches exactly 1 request and unlocks on hidden', async () => {
            const env = createEnvironment({ manualTransitions: true });
            const card = createPostCard({ postId: '1211', content: 'Nội dung gốc 1211' }, env.document);
            env.document.body.appendChild(card);

            const editBtn = card.querySelector('[data-action="edit-post"]');
            editBtn.click();

            const modalEl = env.document.getElementById('editPostModal');
            const modalInst = env.window.bootstrap.Modal.getInstance(modalEl);
            // Normal complete show transition first
            modalInst.completeShowTransition();
            assert.equal(modalInst.isShown, true);

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/1211' && req.method === 'PATCH',
                {
                    status: 200,
                    body: { success: true, data: { id: 1211, content: 'Nội dung cập nhật 1211' }, meta: null }
                }
            );

            const textarea = env.document.getElementById('edit-post-content');
            textarea.value = 'Nội dung cập nhật 1211';
            const submitBtn = env.document.getElementById('btn-save-post');
            const form = env.document.getElementById('edit-post-form');

            // 1st submit when fully shown
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.fetchHarness.calls.length, 1, 'First submit dispatches exactly 1 request');
            assert.equal(modalInst.isTransitioning, true, 'Modal is in HIDE transition');
            assert.equal(submitBtn.disabled, true, 'Submit button must stay disabled during HIDE transition');

            // 2nd submit during HIDE transition
            form.dispatchEvent({ type: 'submit', target: form, preventDefault() {} });
            await new Promise(r => setTimeout(r, 10));
            assert.equal(env.fetchHarness.calls.length, 1, 'Second submit during HIDE transition must NOT dispatch request');

            // Complete HIDE transition
            modalInst.completeHideTransition();
            assert.equal(modalInst.isShown, false);
            assert.equal(submitBtn.disabled, false, 'Submit button must unlock on hidden');
        });

        it('after DELETE 204 during remaining SHOW and HIDE phases, second Confirm dispatches exactly 1 request, defers cleanup, and executes exactly 1 cleanup on hidden', async () => {
            const env = createEnvironment({ manualTransitions: true, pathname: '/posts/1212' });
            const card = createPostCard({ postId: '1212', content: 'Bài viết test delete 1212' }, env.document);
            env.document.body.appendChild(card);

            const replyForm = new MockElement('FORM', 'reply-form', env.document);
            replyForm.setAttribute('data-post-id', '1212');
            const replyTextarea = new MockElement('TEXTAREA', 'commentContent', env.document);
            const replySubmit = new MockElement('BUTTON', 'btn-submit-reply', env.document);
            const replyStatus = new MockElement('DIV', 'reply-status', env.document);
            replyForm.appendChild(replyTextarea);
            replyForm.appendChild(replySubmit);
            replyForm.appendChild(replyStatus);
            env.document.body.appendChild(replyForm);

            const deleteBtn = card.querySelector('[data-action="delete-post"]');
            deleteBtn.click();

            const modalEl = env.document.getElementById('deletePostModal');
            const modalInst = env.window.bootstrap.Modal.getInstance(modalEl);
            assert.equal(modalInst.isTransitioning, true, 'Delete modal is in SHOW transition');
            assert.equal(modalInst.isShown, false);

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/1212' && req.method === 'DELETE',
                { status: 204, body: '' }
            );

            const confirmBtn = env.document.getElementById('btn-confirm-delete-post');

            // 1st confirm delete while in SHOW transition
            confirmBtn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.fetchHarness.calls.length, 1, 'First confirm dispatches exactly 1 request');
            assert.equal(confirmBtn.disabled, true, 'Confirm button must stay disabled after success');
            assert.equal(confirmBtn.getAttribute('aria-busy'), 'true', 'Confirm button must preserve aria-busy');

            // Verify no cleanup/nav happened yet
            assert.ok(env.document.querySelector('.post-card[data-post-id="1212"]'), 'Card must remain during SHOW transition');
            assert.equal(env.window.location.href, '/posts/1212', 'Must NOT navigate during SHOW transition');

            // 2nd confirm click while in SHOW transition
            confirmBtn.click();
            await new Promise(r => setTimeout(r, 10));
            assert.equal(env.fetchHarness.calls.length, 1, 'Second confirm in SHOW transition must NOT dispatch request');
            assert.ok(env.document.querySelector('.post-card[data-post-id="1212"]'), 'Card must still remain');

            // Complete SHOW transition -> enters HIDE transition
            modalInst.completeShowTransition();
            assert.equal(modalInst.isTransitioning, true, 'Modal is in HIDE transition');
            assert.equal(confirmBtn.disabled, true, 'Confirm button stays disabled during HIDE transition');

            // 3rd confirm click during HIDE transition
            confirmBtn.click();
            await new Promise(r => setTimeout(r, 10));
            assert.equal(env.fetchHarness.calls.length, 1, 'Third confirm in HIDE transition must NOT dispatch request');
            assert.ok(env.document.querySelector('.post-card[data-post-id="1212"]'), 'Card must still remain before hidden');

            // Complete HIDE transition (hidden.bs.modal)
            modalInst.completeHideTransition();

            // Strictly exactly 1 cleanup and navigation on hidden
            assert.equal(env.document.querySelector('.post-card[data-post-id="1212"]'), null, 'Card must be removed on hidden');
            assert.equal(env.window.location.href, '/feed', 'Must navigate to /feed on hidden');
            assert.equal(replyTextarea.disabled, true);
            assert.equal(replySubmit.disabled, true);
            assert.equal(confirmBtn.disabled, false, 'Confirm button resets on hidden');
        });

        it('after DELETE 204 during HIDE phase after normal show, second Confirm dispatches exactly 1 request and cleans up on hidden', async () => {
            const env = createEnvironment({ manualTransitions: true, pathname: '/posts/1213' });
            const card = createPostCard({ postId: '1213', content: 'Bài viết test delete 1213' }, env.document);
            env.document.body.appendChild(card);

            const deleteBtn = card.querySelector('[data-action="delete-post"]');
            deleteBtn.click();

            const modalEl = env.document.getElementById('deletePostModal');
            const modalInst = env.window.bootstrap.Modal.getInstance(modalEl);
            // Complete normal show transition first
            modalInst.completeShowTransition();
            assert.equal(modalInst.isShown, true);

            env.fetchHarness.on(
                (url, req) => url === '/api/posts/1213' && req.method === 'DELETE',
                { status: 204, body: '' }
            );

            const confirmBtn = env.document.getElementById('btn-confirm-delete-post');

            // 1st confirm delete when fully shown
            confirmBtn.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.fetchHarness.calls.length, 1, 'Dispatches exactly 1 request');
            assert.equal(modalInst.isTransitioning, true, 'Modal is in HIDE transition');
            assert.equal(confirmBtn.disabled, true, 'Confirm button stays disabled during HIDE transition');

            // Card must still be present before hidden
            assert.ok(env.document.querySelector('.post-card[data-post-id="1213"]'));

            // 2nd confirm click during HIDE transition
            confirmBtn.click();
            await new Promise(r => setTimeout(r, 10));
            assert.equal(env.fetchHarness.calls.length, 1, 'Second confirm during HIDE transition must NOT dispatch request');
            assert.ok(env.document.querySelector('.post-card[data-post-id="1213"]'), 'Card still present');

            // Complete HIDE transition
            modalInst.completeHideTransition();
            assert.equal(env.document.querySelector('.post-card[data-post-id="1213"]'), null, 'Card removed on hidden');
            assert.equal(env.window.location.href, '/feed', 'Navigated on hidden');
            assert.equal(confirmBtn.disabled, false, 'Button unlocked on hidden');
        });
    });
});
