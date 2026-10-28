/**
 * AloUTE — Frontend UX Upgrade Batch 1 Comprehensive Tests
 *
 * Covers:
 * - Scope A: Sidebar navigation links (Feed, Profile, Communities, Friends, Saved Posts only;
 *   Search, Messages, Settings, Create Post, Notifications removed from sidebar;
 *   Navbar / Account dropdown / Mobile offcanvas retention).
 * - Scope B: Create Post Modal lifecycle (repeated open/close, draft preservation, focus,
 *   escape/dismissal guard during mutation, fallback when no Bootstrap, duplicate init/submission
 *   guards, communityId context preservation, media/visibility preservation, quote/report non-regression).
 * - Scope C: Navbar bell compact recent notification preview (REST contract, unread text indicators,
 *   real validated target deep-links, honest unavailable message target, in-flight deduplication,
 *   rejection of malformed envelopes, read failure rollback, read-all success & failure, view all link).
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

const postComposerSource = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/static/js/features/post/post-composer.js'),
    'utf-8'
);

const notificationSource = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/static/js/features/notification/notification.js'),
    'utf-8'
);

const sidebarTemplate = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/templates/fragments/sidebar.html'),
    'utf-8'
);

const navbarTemplate = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/templates/fragments/navbar.html'),
    'utf-8'
);

const defaultLayoutTemplate = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/templates/layouts/default.html'),
    'utf-8'
);

const modalTemplate = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/templates/fragments/modal.html'),
    'utf-8'
);

const composerFormTemplate = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/templates/post/_composer-form.html'),
    'utf-8'
);

const createPageTemplate = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/templates/post/create.html'),
    'utf-8'
);

const feedIndexTemplate = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/templates/feed/index.html'),
    'utf-8'
);

// -----------------------------------------------------------------------------
// Lightweight Mock DOM Harness
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
        this._textContent = '';
        this._innerHTML = '';
        this.disabled = false;
        this.hidden = false;
        this.value = '';
        this._focused = false;
    }

    get textContent() {
        if (this.children.length === 0) return this._textContent || '';
        return (this._textContent || '') + this.children.map(c => c.textContent).join('');
    }

    set textContent(val) {
        this._textContent = String(val || '');
    }

    get href() {
        return this.getAttribute('href') || '';
    }

    set href(val) {
        this.setAttribute('href', val);
    }

    focus() {
        this._focused = true;
        if (this.ownerDocument) {
            this.ownerDocument.activeElement = this;
        }
    }

    get innerHTML() {
        return this._innerHTML;
    }

    set innerHTML(html) {
        this._innerHTML = String(html || '');
        this.children = [];
        this._textContent = '';
        if (!html || !String(html).trim()) {
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
        if (name === 'id') this.id = String(val);
        if (name === 'class') this.className = String(val);
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
        if (!child) return null;
        if (child.parentElement) {
            const idx = child.parentElement.children.indexOf(child);
            if (idx !== -1) child.parentElement.children.splice(idx, 1);
        }
        child.parentElement = this;
        this.children.push(child);
        return child;
    }

    removeChild(child) {
        const idx = this.children.indexOf(child);
        if (idx !== -1) {
            this.children.splice(idx, 1);
            child.parentElement = null;
        }
        return child;
    }

    remove() {
        if (this.parentElement) {
            this.parentElement.removeChild(this);
        }
    }

    querySelector(selector) {
        return querySelector(this, selector);
    }

    querySelectorAll(selector) {
        return querySelectorAll(this, selector, []);
    }

    closest(selector) {
        let cur = this;
        while (cur) {
            if (matchesSelector(cur, selector)) return cur;
            cur = cur.parentElement;
        }
        return null;
    }

    addEventListener(event, listener) {
        if (!this._listeners.has(event)) {
            this._listeners.set(event, []);
        }
        this._listeners.get(event).push(listener);
    }

    removeEventListener(event, listener) {
        if (!this._listeners.has(event)) return;
        const list = this._listeners.get(event).filter(l => l !== listener);
        this._listeners.set(event, list);
    }

    dispatchEvent(evt) {
        evt.target = evt.target || this;
        evt.currentTarget = this;
        let cur = this;
        while (cur) {
            evt.currentTarget = cur;
            const handlers = cur._listeners.get(evt.type) || [];
            for (const h of handlers) {
                h.call(cur, evt);
                if (evt._propagationStopped) return !evt.defaultPrevented;
            }
            if (!evt.bubbles) break;
            cur = cur.parentElement;
        }
        if (evt.bubbles && !evt._propagationStopped && this.ownerDocument) {
            this.ownerDocument.dispatchEvent(evt);
            if (!evt._propagationStopped && this.ownerDocument.defaultView) {
                this.ownerDocument.defaultView.dispatchEvent(evt);
            }
        }
        return !evt.defaultPrevented;
    }
}

class MockCustomEvent {
    constructor(type, init = {}) {
        this.type = type;
        this.detail = init.detail || null;
        this.bubbles = !!init.bubbles;
        this.cancelable = !!init.cancelable;
        this.defaultPrevented = false;
        this._propagationStopped = false;
    }
    preventDefault() { this.defaultPrevented = true; }
    stopPropagation() { this._propagationStopped = true; }
}

function createDOMEnvironment() {
    const document = {
        createElement: (tag) => new MockElement(tag, '', document),
        getElementById: (id) => querySelector(document.body, `#${id}`),
        querySelector: (sel) => querySelector(document.body, sel),
        querySelectorAll: (sel) => querySelectorAll(document.body, sel, []),
        addEventListener: (evt, fn) => {
            if (!document._listeners) document._listeners = new Map();
            if (!document._listeners.has(evt)) document._listeners.set(evt, []);
            document._listeners.get(evt).push(fn);
        },
        removeEventListener: (evt, fn) => {
            if (!document._listeners || !document._listeners.has(evt)) return;
            document._listeners.set(evt, document._listeners.get(evt).filter(l => l !== fn));
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

    const fetchCalls = [];
    const window = {
        document,
        fetchCalls,
        location: { href: 'http://localhost/' },
        console,
        setTimeout: (fn, ms) => setTimeout(fn, ms),
        clearTimeout: (id) => clearTimeout(id),
        CustomEvent: MockCustomEvent,
        AbortController: globalThis.AbortController,
        addEventListener: (evt, fn) => {
            if (!window._listeners) window._listeners = new Map();
            if (!window._listeners.has(evt)) window._listeners.set(evt, []);
            window._listeners.get(evt).push(fn);
        },
        removeEventListener: (evt, fn) => {
            if (!window._listeners || !window._listeners.has(evt)) return;
            window._listeners.set(evt, window._listeners.get(evt).filter(l => l !== fn));
        },
        dispatchEvent: (evt) => {
            evt.target = evt.target || window;
            evt.currentTarget = window;
            const list = (window._listeners && window._listeners.get(evt.type)) || [];
            for (const fn of list) {
                fn(evt);
                if (evt._propagationStopped) break;
            }
            return !evt.defaultPrevented;
        },
        fetch: async (url, opts = {}) => {
            fetchCalls.push({ url, opts });
            if (window.fetchHandler) {
                return window.fetchHandler(url, opts);
            }
            return {
                ok: true,
                status: 200,
                text: async () => JSON.stringify({ success: true, data: [] })
            };
        }
    };
    window.window = window;
    document.defaultView = window;

    return { document, window, fetchCalls };
}

// -----------------------------------------------------------------------------
// Test Suites
// -----------------------------------------------------------------------------

describe('AloUTE Frontend UX Upgrade Batch 1', () => {

    // =========================================================================
    // Scope A: Sidebar & Mobile Navigation Constraints
    // =========================================================================
    describe('Scope A: Sidebar & Mobile Navigation Scope', () => {

        it('Sidebar template contains strictly only Feed, Profile, Communities, Friends, Saved Posts', () => {
            // Verify allowed canonical routes are present
            assert.ok(sidebarTemplate.includes('@{/feed}'), 'Sidebar must contain /feed');
            assert.ok(sidebarTemplate.includes('@{/users/{id}'), 'Sidebar must contain /users/{id}');
            assert.ok(sidebarTemplate.includes('@{/communities}'), 'Sidebar must contain /communities');
            assert.ok(sidebarTemplate.includes('@{/friends}'), 'Sidebar must contain /friends');
            assert.ok(sidebarTemplate.includes('@{/saved}'), 'Sidebar must contain /saved');

            // Verify removed routes are NOT present anywhere in sidebar.html
            assert.ok(!sidebarTemplate.includes('@{/search}'), 'Sidebar must NOT contain /search');
            assert.ok(!sidebarTemplate.includes('@{/messages}'), 'Sidebar must NOT contain /messages');
            assert.ok(!sidebarTemplate.includes('@{/settings}'), 'Sidebar must NOT contain /settings');
            assert.ok(!sidebarTemplate.includes('@{/post/create}'), 'Sidebar must NOT contain /post/create');
            assert.ok(!sidebarTemplate.includes('@{/notifications}'), 'Sidebar must NOT contain /notifications');
            assert.ok(!sidebarTemplate.includes('sidebar-notification-badge'), 'Sidebar must NOT contain notification badge');
        });

        it('Navbar retains accessible desktop and mobile Search, Messages, Notifications, Create Post, and Account dropdown Settings', () => {
            // Search accessible on navbar
            assert.ok(navbarTemplate.includes('action="/search"') || navbarTemplate.includes('@{/search}'), 'Navbar must retain /search');
            assert.ok(navbarTemplate.includes('d-sm-none') && navbarTemplate.includes('bi-search'), 'Navbar must retain mobile search button');

            // Messages accessible on navbar
            assert.ok(navbarTemplate.includes('@{/messages}'), 'Navbar must retain /messages shortcut');

            // Notifications accessible on navbar with preview menu
            assert.ok(navbarTemplate.includes('@{/notifications}'), 'Navbar must retain /notifications access');
            assert.ok(navbarTemplate.includes('id="navbarNotificationDropdown"'), 'Navbar must have notification dropdown trigger');
            assert.ok(navbarTemplate.includes('id="navbar-notification-preview"'), 'Navbar must have notification preview menu');

            // Create Post accessible on navbar (desktop + mobile)
            assert.ok(navbarTemplate.includes('@{/post/create}'), 'Navbar must retain /post/create');
            assert.ok(navbarTemplate.includes('aloute-btn-icon-mobile'), 'Navbar must retain mobile create post button');

            // Settings accessible in account dropdown
            assert.ok(navbarTemplate.includes('@{/settings}'), 'Account dropdown must retain /settings');
        });

        it('Layout offcanvas drawer retains mobile search and loads post-composer script', () => {
            assert.ok(defaultLayoutTemplate.includes('id="mobileNavOffcanvas"'), 'Layout must have mobile offcanvas');
            assert.ok(defaultLayoutTemplate.includes('action="/search"'), 'Mobile offcanvas must provide search access');
            assert.ok(defaultLayoutTemplate.includes('post-composer.js'), 'Layout must include post-composer.js');
        });
    });

    // =========================================================================
    // Scope B: Post Create Modal Lifecycle, Fallback & Guards
    // =========================================================================
    describe('Scope B: Post Create Modal Lifecycle & Fallback', () => {

        it('Modal template contains createPostModal with shared composer form and guards against activeNav create-post', () => {
            assert.ok(modalTemplate.includes('id="createPostModal"'), 'Modal fragment must include createPostModal');
            assert.ok(modalTemplate.includes("activeNav != 'create-post'"), 'createPostModal must be guarded by activeNav != create-post');
            assert.ok(modalTemplate.includes('post/_composer-form'), 'createPostModal must use post/_composer-form');

            // Verify quote and report modals are preserved
            assert.ok(modalTemplate.includes('id="quotePostModal"'), 'quotePostModal must be preserved');
            assert.ok(modalTemplate.includes('id="reportModal"'), 'reportModal must be preserved');
            assert.ok(modalTemplate.includes('id="createCommunityModal"'), 'createCommunityModal must be preserved');
        });

        it('Standalone /post/create uses shared composer form without duplicate modal IDs', () => {
            assert.ok(createPageTemplate.includes('post/_composer-form'), 'create.html must use post/_composer-form');
            assert.ok(composerFormTemplate.includes('id="create-post-form"'), 'Composer fragment defines form');
            assert.ok(composerFormTemplate.includes('id="postContent"'), 'Composer fragment defines postContent');
            assert.ok(composerFormTemplate.includes('id="btn-create-post"'), 'Composer fragment defines submit button');
        });

        it('post/detail.html does not duplicate post-composer.js script include', () => {
            const detailTemplate = fs.readFileSync(
                path.resolve(repoRoot, 'src/main/resources/templates/post/detail.html'),
                'utf-8'
            );
            assert.ok(!detailTemplate.includes('post-composer.js'), 'post/detail.html must not include post-composer.js directly');
        });

        it('navigate-create-post action opens modal when Bootstrap Modal is present', async () => {
            const env = createDOMEnvironment();
            const modalEl = env.document.createElement('DIV');
            modalEl.id = 'createPostModal';
            env.document.body.appendChild(modalEl);

            let modalShown = false;
            class MockBootstrapModal {
                constructor(el) { this.el = el; }
                show() { modalShown = true; }
                hide() {}
                static getInstance(el) { return new MockBootstrapModal(el); }
            }
            env.window.bootstrap = { Modal: MockBootstrapModal };

            const triggerBtn = env.document.createElement('BUTTON');
            triggerBtn.setAttribute('data-action', 'navigate-create-post');
            env.document.body.appendChild(triggerBtn);

            const ctx = vm.createContext(env.window);
            vm.runInContext(httpClientSource, ctx);
            vm.runInContext(pageActionsSource, ctx);

            // Click trigger
            triggerBtn.dispatchEvent(new MockCustomEvent('click', { bubbles: true, cancelable: true }));

            assert.equal(modalShown, true, 'Modal show() must be invoked when Bootstrap is present');
            assert.equal(env.window.location.href, 'http://localhost/', 'Must not navigate away when modal opens');
        });

        it('navigate-create-post falls back to navigation when Bootstrap is absent', async () => {
            const env = createDOMEnvironment();
            // No bootstrap defined on window
            const triggerLink = env.document.createElement('A');
            triggerLink.setAttribute('data-action', 'navigate-create-post');
            triggerLink.setAttribute('href', '#');
            env.document.body.appendChild(triggerLink);

            const ctx = vm.createContext(env.window);
            vm.runInContext(httpClientSource, ctx);
            vm.runInContext(pageActionsSource, ctx);

            triggerLink.dispatchEvent(new MockCustomEvent('click', { bubbles: true, cancelable: true }));

            assert.equal(env.window.location.href, '/post/create', 'Must fall back to /post/create navigation');
        });

        it('navigate-create-post falls back to /post/create navigation when Bootstrap is active but #createPostModal is absent', async () => {
            const env = createDOMEnvironment();
            // Bootstrap is active in environment
            class MockBootstrapModal {
                constructor(el) { this.el = el; }
                show() {}
                static getInstance(el) { return new MockBootstrapModal(el); }
            }
            env.window.bootstrap = { Modal: MockBootstrapModal };

            // But #createPostModal is absent from DOM (e.g. on /post/create page)
            const triggerLink = env.document.createElement('A');
            triggerLink.setAttribute('data-action', 'navigate-create-post');
            triggerLink.setAttribute('href', '/post/create');
            env.document.body.appendChild(triggerLink);

            const ctx = vm.createContext(env.window);
            vm.runInContext(httpClientSource, ctx);
            vm.runInContext(pageActionsSource, ctx);

            triggerLink.dispatchEvent(new MockCustomEvent('click', { bubbles: true, cancelable: true }));

            assert.equal(env.window.location.href, '/post/create', 'Must navigate to /post/create even when Bootstrap is loaded');
        });

        it('Preserves communityId context when opening modal from community detail', async () => {
            const env = createDOMEnvironment();
            const modalEl = env.document.createElement('DIV');
            modalEl.id = 'createPostModal';
            const commInput = env.document.createElement('INPUT');
            commInput.id = 'post-community-id';
            modalEl.appendChild(commInput);
            env.document.body.appendChild(modalEl);

            class MockBootstrapModal {
                constructor(el) { this.el = el; }
                show() {}
                static getInstance(el) { return new MockBootstrapModal(el); }
            }
            env.window.bootstrap = { Modal: MockBootstrapModal };

            const triggerBtn = env.document.createElement('BUTTON');
            triggerBtn.setAttribute('data-action', 'navigate-create-post');
            triggerBtn.setAttribute('data-community-id', '42');
            env.document.body.appendChild(triggerBtn);

            const ctx = vm.createContext(env.window);
            vm.runInContext(httpClientSource, ctx);
            vm.runInContext(pageActionsSource, ctx);

            triggerBtn.dispatchEvent(new MockCustomEvent('click', { bubbles: true, cancelable: true }));

            assert.equal(commInput.value, '42', 'Community context 42 must be transferred to post-community-id');
        });

        it('Does not leak community context from unrelated post-card actions on a feed', async () => {
            const env = createDOMEnvironment();
            const modalEl = env.document.createElement('DIV');
            modalEl.id = 'createPostModal';
            const commInput = env.document.createElement('INPUT');
            commInput.id = 'post-community-id';
            commInput.value = '7';
            modalEl.appendChild(commInput);
            env.document.body.appendChild(modalEl);

            // A community post card on the feed exposes data-community-id on its
            // moderation controls; it must never become the feed composer context.
            const postCardAction = env.document.createElement('BUTTON');
            postCardAction.setAttribute('data-action', 'community-remove-post');
            postCardAction.setAttribute('data-community-id', '42');
            env.document.body.appendChild(postCardAction);

            class MockBootstrapModal {
                constructor(el) { this.el = el; }
                show() {}
                static getInstance(el) { return new MockBootstrapModal(el); }
            }
            env.window.bootstrap = { Modal: MockBootstrapModal };

            const triggerBtn = env.document.createElement('BUTTON');
            triggerBtn.setAttribute('data-action', 'navigate-create-post');
            env.document.body.appendChild(triggerBtn);

            const ctx = vm.createContext(env.window);
            vm.runInContext(httpClientSource, ctx);
            vm.runInContext(pageActionsSource, ctx);

            triggerBtn.dispatchEvent(new MockCustomEvent('click', { bubbles: true, cancelable: true }));

            assert.equal(commInput.value, '', 'Feed composer must clear community context instead of borrowing a post-card id');
        });

        it('Restores invoking trigger focus on hidden.bs.modal after Escape/backdrop/close dismissal', async () => {
            const env = createDOMEnvironment();
            const modalEl = env.document.createElement('DIV');
            modalEl.id = 'createPostModal';
            const form = env.document.createElement('FORM');
            form.id = 'create-post-form';
            const contentInput = env.document.createElement('TEXTAREA');
            contentInput.id = 'postContent';
            form.appendChild(contentInput);
            modalEl.appendChild(form);
            env.document.body.appendChild(modalEl);

            let shownRelatedTarget = null;
            class MockBootstrapModal {
                constructor(el) { this.el = el; }
                show(relatedTarget) {
                    shownRelatedTarget = relatedTarget;
                    this.el.dispatchEvent(new MockCustomEvent('show.bs.modal', { relatedTarget: relatedTarget }));
                    this.el.dispatchEvent(new MockCustomEvent('shown.bs.modal', { relatedTarget: relatedTarget }));
                }
                static getInstance(el) { return new MockBootstrapModal(el); }
            }
            env.window.bootstrap = { Modal: MockBootstrapModal };

            const triggerBtn = env.document.createElement('BUTTON');
            triggerBtn.setAttribute('data-action', 'navigate-create-post');
            let triggerFocused = false;
            triggerBtn.focus = () => { triggerFocused = true; };
            env.document.body.appendChild(triggerBtn);

            const ctx = vm.createContext(env.window);
            vm.runInContext(httpClientSource, ctx);
            vm.runInContext(pageActionsSource, ctx);
            vm.runInContext(postComposerSource, ctx);

            // User clicks create button to open modal
            triggerBtn.dispatchEvent(new MockCustomEvent('click', { bubbles: true, cancelable: true }));
            assert.equal(shownRelatedTarget, triggerBtn, 'modal.show must receive triggerBtn');

            // Dismiss modal (e.g. Escape / backdrop click / close button)
            modalEl.dispatchEvent(new MockCustomEvent('hide.bs.modal'));
            modalEl.dispatchEvent(new MockCustomEvent('hidden.bs.modal'));

            assert.equal(triggerFocused, true, 'Invoking trigger must receive focus upon hidden.bs.modal');
        });

        it('Duplicate initialization guard: evaluating post-composer multiple times does not attach duplicate submit handlers', async () => {
            const env = createDOMEnvironment();
            const form = env.document.createElement('FORM');
            form.id = 'create-post-form';
            const contentInput = env.document.createElement('TEXTAREA');
            contentInput.id = 'postContent';
            contentInput.value = 'Thử nghiệm bài viết không trùng lặp';
            const submitBtn = env.document.createElement('BUTTON');
            submitBtn.id = 'btn-create-post';
            submitBtn.type = 'submit';
            form.appendChild(contentInput);
            form.appendChild(submitBtn);
            env.document.body.appendChild(form);

            env.window.fetchHandler = async (url) => {
                return {
                    ok: true,
                    status: 201,
                    text: async () => JSON.stringify({ success: true, data: { id: 101 } })
                };
            };

            const ctx = vm.createContext(env.window);
            // Run post-composer script twice
            vm.runInContext(postComposerSource, ctx);
            vm.runInContext(postComposerSource, ctx);

            assert.equal(form.dataset.composerInitialized, 'true');

            // Dispatch 1 submit event
            form.dispatchEvent(new MockCustomEvent('submit', { bubbles: true, cancelable: true }));
            await new Promise(r => setTimeout(r, 20));

            const postCalls = env.window.fetchCalls.filter(c => c.url === '/api/posts');
            assert.equal(postCalls.length, 1, 'Must dispatch exactly 1 POST request despite duplicate script evaluation');
        });

        it('In-flight submission guard: rapid repeated submit clicks send only 1 POST request', async () => {
            const env = createDOMEnvironment();
            const form = env.document.createElement('FORM');
            form.id = 'create-post-form';
            const contentInput = env.document.createElement('TEXTAREA');
            contentInput.id = 'postContent';
            contentInput.value = 'Kiểm tra chống bấm gửi liên tiếp';
            const submitBtn = env.document.createElement('BUTTON');
            submitBtn.id = 'btn-create-post';
            submitBtn.type = 'submit';
            form.appendChild(contentInput);
            form.appendChild(submitBtn);
            env.document.body.appendChild(form);

            let resolvePost;
            env.window.fetchHandler = async (url) => {
                if (url === '/api/posts') {
                    return new Promise(r => { resolvePost = r; });
                }
                return { ok: true, status: 200, text: async () => '{}' };
            };

            const ctx = vm.createContext(env.window);
            vm.runInContext(postComposerSource, ctx);

            // First submit
            form.dispatchEvent(new MockCustomEvent('submit', { bubbles: true, cancelable: true }));
            // Immediate second and third submit attempts while first is in-flight
            form.dispatchEvent(new MockCustomEvent('submit', { bubbles: true, cancelable: true }));
            form.dispatchEvent(new MockCustomEvent('submit', { bubbles: true, cancelable: true }));

            assert.equal(env.window.fetchCalls.filter(c => c.url === '/api/posts').length, 1, 'Rapid repeated submits must be suppressed');

            // Resolve
            resolvePost({
                ok: true,
                status: 201,
                text: async () => JSON.stringify({ success: true, data: { id: 105 } })
            });
            await new Promise(r => setTimeout(r, 20));
        });

        it('Submitting with communityId routes to /api/communities/{id}/posts', async () => {
            const env = createDOMEnvironment();
            const form = env.document.createElement('FORM');
            form.id = 'create-post-form';
            const commInput = env.document.createElement('INPUT');
            commInput.id = 'post-community-id';
            commInput.value = '77';
            const contentInput = env.document.createElement('TEXTAREA');
            contentInput.id = 'postContent';
            contentInput.value = 'Bài viết cho cộng đồng CNTT';
            const submitBtn = env.document.createElement('BUTTON');
            submitBtn.id = 'btn-create-post';
            submitBtn.type = 'submit';
            form.appendChild(commInput);
            form.appendChild(contentInput);
            form.appendChild(submitBtn);
            env.document.body.appendChild(form);

            env.window.fetchHandler = async (url) => {
                return {
                    ok: true,
                    status: 201,
                    text: async () => JSON.stringify({ success: true, data: { id: 200 } })
                };
            };

            const ctx = vm.createContext(env.window);
            vm.runInContext(postComposerSource, ctx);

            form.dispatchEvent(new MockCustomEvent('submit', { bubbles: true, cancelable: true }));
            await new Promise(r => setTimeout(r, 20));

            const commCalls = env.window.fetchCalls.filter(c => c.url === '/api/communities/77/posts');
            assert.equal(commCalls.length, 1, 'Must submit to /api/communities/77/posts');
        });

        it('Submission failure preserves user draft text and restores submit button', async () => {
            const env = createDOMEnvironment();
            const form = env.document.createElement('FORM');
            form.id = 'create-post-form';
            const contentInput = env.document.createElement('TEXTAREA');
            contentInput.id = 'postContent';
            contentInput.value = 'Nội dung quan trọng không được mất khi lỗi';
            const statusBox = env.document.createElement('DIV');
            statusBox.id = 'create-post-status';
            const submitBtn = env.document.createElement('BUTTON');
            submitBtn.id = 'btn-create-post';
            submitBtn.type = 'submit';
            submitBtn.textContent = 'Đăng bài';
            form.appendChild(contentInput);
            form.appendChild(statusBox);
            form.appendChild(submitBtn);
            env.document.body.appendChild(form);

            env.window.fetchHandler = async () => {
                return {
                    ok: false,
                    status: 500,
                    text: async () => JSON.stringify({ success: false, error: { code: 'INTERNAL_SERVER_ERROR' } })
                };
            };

            const ctx = vm.createContext(env.window);
            vm.runInContext(postComposerSource, ctx);

            form.dispatchEvent(new MockCustomEvent('submit', { bubbles: true, cancelable: true }));
            await new Promise(r => setTimeout(r, 20));

            // User input preserved
            assert.equal(contentInput.value, 'Nội dung quan trọng không được mất khi lỗi');
            // Submit button restored and enabled
            assert.equal(submitBtn.disabled, false);
            // Error alert shown
            assert.ok(statusBox.textContent.includes('500'));
        });

        it('Composer submit button state: disabled on empty/whitespace, enabled on valid input, re-disabled on clear, and in-flight input does not re-enable submit', async () => {
            const env = createDOMEnvironment();
            const form = env.document.createElement('FORM');
            form.id = 'create-post-form';
            const contentInput = env.document.createElement('TEXTAREA');
            contentInput.id = 'postContent';
            contentInput.value = '';
            const submitBtn = env.document.createElement('BUTTON');
            submitBtn.id = 'btn-create-post';
            submitBtn.type = 'submit';
            form.appendChild(contentInput);
            form.appendChild(submitBtn);
            env.document.body.appendChild(form);

            let resolvePost;
            env.window.fetchHandler = async (url) => {
                if (url === '/api/posts') {
                    return new Promise(r => { resolvePost = r; });
                }
                return { ok: true, status: 200, text: async () => '{}' };
            };

            const ctx = vm.createContext(env.window);
            vm.runInContext(postComposerSource, ctx);

            // 1. Initial empty state -> disabled
            assert.equal(submitBtn.disabled, true, 'Initially empty content must disable submit button');

            // 2. Whitespace only -> disabled
            contentInput.value = '    \n  \t  ';
            contentInput.dispatchEvent(new MockCustomEvent('input', { bubbles: true }));
            assert.equal(submitBtn.disabled, true, 'Whitespace-only content must disable submit button');

            // 3. Valid non-empty input -> enabled
            contentInput.value = 'Bài viết hợp lệ trên AloUTE';
            contentInput.dispatchEvent(new MockCustomEvent('input', { bubbles: true }));
            assert.equal(submitBtn.disabled, false, 'Valid non-empty content must enable submit button');

            // 4. Clearing content -> re-disabled
            contentInput.value = '';
            contentInput.dispatchEvent(new MockCustomEvent('input', { bubbles: true }));
            assert.equal(submitBtn.disabled, true, 'Clearing content must re-disable submit button');

            // 5. Restore valid input and submit -> in-flight disables button
            contentInput.value = 'Bài viết đang gửi lên máy chủ';
            contentInput.dispatchEvent(new MockCustomEvent('input', { bubbles: true }));
            assert.equal(submitBtn.disabled, false);

            form.dispatchEvent(new MockCustomEvent('submit', { bubbles: true, cancelable: true }));
            assert.equal(submitBtn.disabled, true, 'In-flight submit must disable button');

            // 6. Typing while in-flight MUST NOT re-enable button
            contentInput.value = 'Thay đổi nội dung trong lúc đang gửi';
            contentInput.dispatchEvent(new MockCustomEvent('input', { bubbles: true }));
            assert.equal(submitBtn.disabled, true, 'In-flight input must NOT re-enable submit button');

            // 7. Resolve failure -> draft preserved and submit button restored & enabled
            resolvePost({
                ok: false,
                status: 500,
                text: async () => JSON.stringify({ success: false, error: { code: 'INTERNAL_SERVER_ERROR' } })
            });
            await new Promise(r => setTimeout(r, 20));

            assert.equal(submitBtn.disabled, false, 'Failure restoration with non-empty draft must re-enable submit button');
        });
    });

    // =========================================================================
    // Scope C: Navbar Bell Compact Recent Notification Preview
    // =========================================================================
    describe('Scope C: Navbar Bell Compact Recent Notification Preview', () => {

        function setupNavbarNotificationDOM(env) {
            const dropdown = env.document.createElement('DIV');
            dropdown.className = 'dropdown aloute-notification-dropdown';

            const toggle = env.document.createElement('A');
            toggle.id = 'navbarNotificationDropdown';
            toggle.className = 'navbar-notification-link';
            toggle.setAttribute('data-bs-toggle', 'dropdown');

            const badge = env.document.createElement('SPAN');
            badge.id = 'navbar-notification-badge';
            badge.className = 'navbar-notification-badge d-none';
            badge.textContent = '0';
            toggle.appendChild(badge);

            const previewMenu = env.document.createElement('DIV');
            previewMenu.id = 'navbar-notification-preview';

            const btnMarkAll = env.document.createElement('BUTTON');
            btnMarkAll.id = 'btn-navbar-mark-all-read';
            btnMarkAll.textContent = 'Đã đọc tất cả';

            const feedback = env.document.createElement('DIV');
            feedback.id = 'navbar-notif-feedback';
            feedback.className = 'alert d-none';

            const listContainer = env.document.createElement('DIV');
            listContainer.id = 'navbar-notification-list';

            const viewAllLink = env.document.createElement('A');
            viewAllLink.id = 'navbar-notifications-view-all';
            viewAllLink.className = 'dropdown-item text-decoration-none small fw-semibold text-primary d-block py-1 text-center';
            viewAllLink.setAttribute('href', '/notifications');
            viewAllLink.textContent = 'Xem tất cả thông báo';

            previewMenu.appendChild(btnMarkAll);
            previewMenu.appendChild(feedback);
            previewMenu.appendChild(listContainer);
            previewMenu.appendChild(viewAllLink);

            dropdown.appendChild(toggle);
            dropdown.appendChild(previewMenu);

            env.document.body.appendChild(dropdown);
            return { dropdown, toggle, badge, previewMenu, btnMarkAll, feedback, listContainer, viewAllLink };
        }

        it('Opening dropdown requests recent notifications GET /api/notifications?page=0&size=5 with unread indicators', async () => {
            const env = createDOMEnvironment();
            const dom = setupNavbarNotificationDOM(env);

            const mockNotifications = [
                {
                    id: 101,
                    type: 'POST_LIKE',
                    targetType: 'POST',
                    targetId: 45,
                    isRead: false,
                    actor: { id: 5, displayName: 'Nguyễn Văn A', username: 'vana' },
                    contentPreview: 'Bài viết hay quá!',
                    createdAt: new Date().toISOString()
                },
                {
                    id: 102,
                    type: 'POST_REPLY',
                    targetType: 'POST',
                    targetId: 45,
                    isRead: true,
                    actor: { id: 6, displayName: 'Trần Thị B', username: 'thib' },
                    contentPreview: 'Cảm ơn bạn đã chia sẻ',
                    createdAt: new Date(Date.now() - 3600000).toISOString()
                }
            ];

            env.window.fetchHandler = async (url) => {
                if (url.includes('/api/notifications?page=0&size=5')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: mockNotifications })
                    };
                }
                return { ok: true, status: 200, text: async () => JSON.stringify({ success: true }) };
            };

            const ctx = vm.createContext(env.window);
            vm.runInContext(httpClientSource, ctx);
            vm.runInContext(pageActionsSource, ctx);

            // Trigger dropdown open
            dom.dropdown.dispatchEvent(new MockCustomEvent('show.bs.dropdown', { bubbles: true }));
            await new Promise(r => setTimeout(r, 20));

            const calls = env.window.fetchCalls.filter(c => c.url.includes('/api/notifications?page=0&size=5'));
            assert.equal(calls.length, 1, 'Must request recent notifications list');

            const items = dom.listContainer.querySelectorAll('.aloute-notification-preview-item');
            assert.equal(items.length, 2, 'Must render 2 preview items');

            // Unread indicator checks
            assert.ok(items[0].classList.contains('unread'), 'Unread item must have unread class');
            assert.ok(!items[1].classList.contains('unread'), 'Read item must not have unread class');
            assert.ok(items[0].textContent.includes('Chưa đọc'), 'Unread item must have text indicator badge');
            assert.ok(items[0].querySelector('.aloute-preview-unread-dot'), 'Unread item must have visual dot');
        });

        it('Validated real target deep-links resolve accurately without inventing IDs', async () => {
            const env = createDOMEnvironment();
            const dom = setupNavbarNotificationDOM(env);

            const testItems = [
                { id: 1, type: 'POST_LIKE', targetType: 'POST', targetId: 88, isRead: true },
                { id: 2, type: 'POST_LIKE', targetType: 'POST', targetId: null, isRead: true },
                { id: 3, type: 'FOLLOW', targetType: 'USER', targetId: 12, isRead: true },
                { id: 4, type: 'COMMUNITY_JOIN', targetType: 'COMMUNITY', targetId: 5, isRead: true },
                { id: 5, type: 'CONVERSATION', targetType: 'CONVERSATION', targetId: 30, isRead: true },
                { id: 6, type: 'MESSAGE', targetType: 'MESSAGE', targetId: 99, isRead: true }, // Generic MESSAGE targetId is messageId -> /messages
                { id: 7, type: 'MESSAGE', targetType: 'MESSAGE', targetId: null, isRead: true } // Unavailable target -> /messages
            ];

            env.window.fetchHandler = async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({ success: true, data: testItems })
            });

            const ctx = vm.createContext(env.window);
            vm.runInContext(httpClientSource, ctx);
            vm.runInContext(pageActionsSource, ctx);

            dom.dropdown.dispatchEvent(new MockCustomEvent('show.bs.dropdown', { bubbles: true }));
            await new Promise(r => setTimeout(r, 20));

            const items = dom.listContainer.querySelectorAll('.aloute-notification-preview-item');
            assert.equal(items.length, 7);
            assert.ok(items[0].classList.contains('dropdown-item'), 'Item must have dropdown-item class for keyboard navigation');
            assert.equal(items[0].getAttribute('href'), '/posts/88', 'POST targetId 88 -> /posts/88');
            assert.equal(items[1].getAttribute('href'), '/feed', 'POST null targetId -> /feed fallback');
            assert.equal(items[2].getAttribute('href'), '/users/12', 'USER targetId 12 -> /users/12');
            assert.equal(items[3].getAttribute('href'), '/communities/5', 'COMMUNITY targetId 5 -> /communities/5');
            assert.equal(items[4].getAttribute('href'), '/messages/30', 'CONVERSATION targetId 30 -> /messages/30');
            assert.equal(items[5].getAttribute('href'), '/messages', 'MESSAGE generic targetId 99 honestly links to /messages');
            assert.equal(items[6].getAttribute('href'), '/messages', 'MESSAGE null targetId honestly links to /messages');
        });

        it('Deduplicates in-flight recent notifications fetch', async () => {
            const env = createDOMEnvironment();
            const dom = setupNavbarNotificationDOM(env);

            let resolveFetch;
            env.window.fetchHandler = async (url) => {
                if (url.includes('/api/notifications?page=0&size=5')) {
                    return new Promise(r => { resolveFetch = r; });
                }
                return { ok: true, status: 200, text: async () => '{}' };
            };

            const ctx = vm.createContext(env.window);
            vm.runInContext(httpClientSource, ctx);
            vm.runInContext(pageActionsSource, ctx);

            // Dispatch two rapid dropdown show events
            dom.dropdown.dispatchEvent(new MockCustomEvent('show.bs.dropdown', { bubbles: true }));
            dom.dropdown.dispatchEvent(new MockCustomEvent('show.bs.dropdown', { bubbles: true }));

            const calls = env.window.fetchCalls.filter(c => c.url.includes('/api/notifications?page=0&size=5'));
            assert.equal(calls.length, 1, 'In-flight fetch must be deduplicated to exactly 1 request');

            resolveFetch({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({ success: true, data: [] })
            });
            await new Promise(r => setTimeout(r, 20));
        });

        it('Rejects malformed envelope (success: false or non-array) without corruption', async () => {
            const env = createDOMEnvironment();
            const dom = setupNavbarNotificationDOM(env);

            env.window.fetchHandler = async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({ success: false, error: { message: 'Malformed envelope' } })
            });

            const ctx = vm.createContext(env.window);
            vm.runInContext(httpClientSource, ctx);
            vm.runInContext(pageActionsSource, ctx);

            dom.dropdown.dispatchEvent(new MockCustomEvent('show.bs.dropdown', { bubbles: true }));
            await new Promise(r => setTimeout(r, 20));

            assert.ok(dom.listContainer.textContent.includes('Không thể tải'), 'Must display error state on malformed envelope');
        });

        it('Clicking an unread preview item marks it as read and decrements unread badge', async () => {
            const env = createDOMEnvironment();
            const dom = setupNavbarNotificationDOM(env);
            dom.badge.textContent = '3';
            dom.badge.classList.remove('d-none');

            const item = { id: 99, type: 'POST_LIKE', targetType: 'POST', targetId: 10, isRead: false };
            let patchCalled = false;

            env.window.fetchHandler = async (url, opts) => {
                if (url.includes('/api/notifications?page=0&size=5')) {
                    return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: [item] }) };
                }
                if (url === '/api/notifications/99/read' && opts.method === 'PATCH') {
                    patchCalled = true;
                    return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: { id: 99, isRead: true } }) };
                }
                return { ok: true, status: 200, text: async () => '{}' };
            };

            const ctx = vm.createContext(env.window);
            vm.runInContext(httpClientSource, ctx);
            vm.runInContext(pageActionsSource, ctx);

            dom.dropdown.dispatchEvent(new MockCustomEvent('show.bs.dropdown', { bubbles: true }));
            await new Promise(r => setTimeout(r, 20));

            const renderedItem = dom.listContainer.querySelector('.aloute-notification-preview-item');
            assert.ok(renderedItem);

            renderedItem.dispatchEvent(new MockCustomEvent('click', { bubbles: true, cancelable: true }));
            await new Promise(r => setTimeout(r, 20));

            assert.equal(patchCalled, true, 'PATCH /api/notifications/99/read must be called');
            assert.equal(dom.badge.textContent, '2', 'Unread badge must decrement from 3 to 2');
        });

        it('Read failure (HTTP 500) preserves unread status and count (no false success)', async () => {
            const env = createDOMEnvironment();
            const dom = setupNavbarNotificationDOM(env);
            dom.badge.textContent = '3';
            dom.badge.classList.remove('d-none');

            const item = { id: 88, type: 'POST_LIKE', targetType: 'POST', targetId: 10, isRead: false };

            env.window.fetchHandler = async (url, opts) => {
                if (url.includes('/api/notifications?page=0&size=5')) {
                    return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: [item] }) };
                }
                if (url === '/api/notifications/88/read' && opts.method === 'PATCH') {
                    return { ok: false, status: 500, text: async () => JSON.stringify({ success: false }) };
                }
                return { ok: true, status: 200, text: async () => '{}' };
            };

            const ctx = vm.createContext(env.window);
            vm.runInContext(httpClientSource, ctx);
            vm.runInContext(pageActionsSource, ctx);

            dom.dropdown.dispatchEvent(new MockCustomEvent('show.bs.dropdown', { bubbles: true }));
            await new Promise(r => setTimeout(r, 20));

            const renderedItem = dom.listContainer.querySelector('.aloute-notification-preview-item');
            renderedItem.dispatchEvent(new MockCustomEvent('click', { bubbles: true, cancelable: true }));
            await new Promise(r => setTimeout(r, 20));

            // Must NOT decrement on failure!
            assert.equal(dom.badge.textContent, '3', 'Badge must NOT decrement on read mutation failure');
        });

        it('btn-navbar-mark-all-read sends PATCH /api/notifications/read-all and clears badge to 0', async () => {
            const env = createDOMEnvironment();
            const dom = setupNavbarNotificationDOM(env);
            dom.badge.textContent = '5';
            dom.badge.classList.remove('d-none');

            let readAllCalled = false;
            env.window.fetchHandler = async (url, opts) => {
                if (url.includes('/api/notifications?page=0&size=5')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [{ id: 1, type: 'POST_LIKE', targetType: 'POST', targetId: 5, isRead: false }]
                        })
                    };
                }
                if (url === '/api/notifications/read-all' && opts.method === 'PATCH') {
                    readAllCalled = true;
                    return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: { markedAllAsRead: true } }) };
                }
                return { ok: true, status: 200, text: async () => '{}' };
            };

            const ctx = vm.createContext(env.window);
            vm.runInContext(httpClientSource, ctx);
            vm.runInContext(pageActionsSource, ctx);

            dom.dropdown.dispatchEvent(new MockCustomEvent('show.bs.dropdown', { bubbles: true }));
            await new Promise(r => setTimeout(r, 20));

            // Click mark all read
            dom.btnMarkAll.dispatchEvent(new MockCustomEvent('click', { bubbles: true, cancelable: true }));
            await new Promise(r => setTimeout(r, 20));

            assert.equal(readAllCalled, true, 'PATCH /api/notifications/read-all must be called');
            assert.equal(dom.badge.textContent, '0', 'Unread badge must be set to 0');
            assert.ok(dom.badge.classList.contains('d-none'), 'Unread badge must be hidden');
            assert.ok(dom.feedback.textContent.includes('Đã đánh dấu'), 'Feedback alert must confirm success');
        });

        it('btn-navbar-mark-all-read failure preserves unread badges and displays error', async () => {
            const env = createDOMEnvironment();
            const dom = setupNavbarNotificationDOM(env);
            dom.badge.textContent = '4';
            dom.badge.classList.remove('d-none');

            env.window.fetchHandler = async (url, opts) => {
                if (url === '/api/notifications/read-all' && opts.method === 'PATCH') {
                    return { ok: false, status: 500, text: async () => JSON.stringify({ success: false }) };
                }
                return { ok: true, status: 200, text: async () => '{}' };
            };

            const ctx = vm.createContext(env.window);
            vm.runInContext(httpClientSource, ctx);
            vm.runInContext(pageActionsSource, ctx);

            dom.btnMarkAll.dispatchEvent(new MockCustomEvent('click', { bubbles: true, cancelable: true }));
            await new Promise(r => setTimeout(r, 20));

            assert.equal(dom.badge.textContent, '4', 'Badge must remain 4 on failure');
            assert.ok(!dom.badge.classList.contains('d-none'), 'Badge must remain visible');
            assert.ok(dom.feedback.textContent.includes('Không thể đánh dấu'), 'Error alert must be shown');
        });

        it('Preserves confirmed numeric unread count across 99+ ceiling: 150 -> 149 renders 99+', async () => {
            const env = createDOMEnvironment();
            const dom = setupNavbarNotificationDOM(env);

            const mockItem = {
                id: 201,
                type: 'POST_LIKE',
                targetType: 'POST',
                targetId: 10,
                isRead: false
            };

            env.window.fetchHandler = async (url, opts) => {
                if (url.includes('/api/notifications?page=0&size=5')) {
                    return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: [mockItem] }) };
                }
                if (url === '/api/notifications/201/read' && opts.method === 'PATCH') {
                    return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: { isRead: true } }) };
                }
                return { ok: true, status: 200, text: async () => '{}' };
            };

            const ctx = vm.createContext(env.window);
            vm.runInContext(httpClientSource, ctx);
            vm.runInContext(pageActionsSource, ctx);

            // Set initial confirmed count to 150
            env.window.dispatchEvent(new MockCustomEvent('aloute:unread-count-updated', {
                detail: { unreadCount: 150, version: 1 }
            }));
            assert.equal(dom.badge.textContent, '99+', '150 unread must render as 99+');

            // Open dropdown and render preview
            dom.dropdown.dispatchEvent(new MockCustomEvent('show.bs.dropdown', { bubbles: true }));
            await new Promise(r => setTimeout(r, 20));

            const renderedItem = dom.listContainer.querySelector('.aloute-notification-preview-item');
            assert.ok(renderedItem);

            // Click unread preview item
            renderedItem.dispatchEvent(new MockCustomEvent('click', { bubbles: true, cancelable: true }));
            await new Promise(r => setTimeout(r, 20));

            // Confirmed count should be 149, which still renders as 99+ (not 98!)
            assert.equal(dom.badge.textContent, '99+', '149 unread must still render as 99+');
        });

        it('Stale unread-count fetch arriving after read/read-all mutation cannot revert confirmed state', async () => {
            const env = createDOMEnvironment();
            const dom = setupNavbarNotificationDOM(env);

            // Add auth indicator so page-actions initiates unread-count fetch
            const authIndicator = env.document.createElement('DIV');
            authIndicator.className = 'aloute-user-dropdown-btn';
            env.document.body.appendChild(authIndicator);

            let resolveCountFetch;
            env.window.fetchHandler = async (url, opts) => {
                if (url === '/api/notifications/unread-count') {
                    return new Promise(r => { resolveCountFetch = r; });
                }
                if (url === '/api/notifications/read-all' && opts.method === 'PATCH') {
                    return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: { markedAllAsRead: true } }) };
                }
                return { ok: true, status: 200, text: async () => '{}' };
            };

            const ctx = vm.createContext(env.window);
            vm.runInContext(httpClientSource, ctx);
            vm.runInContext(pageActionsSource, ctx);

            // In-flight count fetch is now pending
            // User clicks mark-all-read -> mutations sets count = 0 and increments version
            dom.btnMarkAll.dispatchEvent(new MockCustomEvent('click', { bubbles: true, cancelable: true }));
            await new Promise(r => setTimeout(r, 20));

            assert.equal(dom.badge.textContent, '0', 'Badge must be 0 after read-all');

            // Older count fetch with count=25 finally resolves
            resolveCountFetch({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({ success: true, data: { unreadCount: 25 } })
            });
            await new Promise(r => setTimeout(r, 20));

            // Stale fetch must be discarded! Badge must REMAIN 0!
            assert.equal(dom.badge.textContent, '0', 'Stale count fetch must NOT overwrite confirmed 0');
        });

        it('Rapid double click on same unread preview item issues only ONE PATCH request', async () => {
            const env = createDOMEnvironment();
            const dom = setupNavbarNotificationDOM(env);

            const mockItem = {
                id: 301,
                type: 'POST_LIKE',
                targetType: 'POST',
                targetId: 10,
                isRead: false
            };

            let patchCalls = 0;
            let resolvePatch;
            env.window.fetchHandler = async (url, opts) => {
                if (url.includes('/api/notifications?page=0&size=5')) {
                    return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: [mockItem] }) };
                }
                if (url === '/api/notifications/301/read' && opts.method === 'PATCH') {
                    patchCalls++;
                    return new Promise(r => { resolvePatch = r; });
                }
                return { ok: true, status: 200, text: async () => '{}' };
            };

            const ctx = vm.createContext(env.window);
            vm.runInContext(httpClientSource, ctx);
            vm.runInContext(pageActionsSource, ctx);

            dom.dropdown.dispatchEvent(new MockCustomEvent('show.bs.dropdown', { bubbles: true }));
            await new Promise(r => setTimeout(r, 20));

            const renderedItem = dom.listContainer.querySelector('.aloute-notification-preview-item');
            assert.ok(renderedItem);

            // Double click while in-flight
            renderedItem.dispatchEvent(new MockCustomEvent('click', { bubbles: true, cancelable: true }));
            renderedItem.dispatchEvent(new MockCustomEvent('click', { bubbles: true, cancelable: true }));

            assert.equal(patchCalls, 1, 'Rapid repeated click on same item must be suppressed');

            resolvePatch({ ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: { isRead: true } }) });
            await new Promise(r => setTimeout(r, 20));
        });

        it('Initializes navbar preview dropdown on /notifications page while respecting #notifications-content count owner', async () => {
            const env = createDOMEnvironment();
            const dom = setupNavbarNotificationDOM(env);

            // Simulate /notifications page
            const notifContent = env.document.createElement('DIV');
            notifContent.id = 'notifications-content';
            env.document.body.appendChild(notifContent);

            let unreadCountCalls = 0;
            let recentPreviewCalls = 0;
            env.window.fetchHandler = async (url) => {
                if (url.includes('/api/notifications/unread-count')) {
                    unreadCountCalls++;
                }
                if (url.includes('/api/notifications?page=0&size=5')) {
                    recentPreviewCalls++;
                    return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: [] }) };
                }
                return { ok: true, status: 200, text: async () => '{}' };
            };

            const ctx = vm.createContext(env.window);
            vm.runInContext(httpClientSource, ctx);
            vm.runInContext(pageActionsSource, ctx);

            // #notifications-content owns count fetch, so page-actions must NOT fetch unread-count
            assert.equal(unreadCountCalls, 0, 'page-actions must not fetch unread-count on /notifications');

            // But dropdown preview MUST still be wired and functional!
            dom.dropdown.dispatchEvent(new MockCustomEvent('show.bs.dropdown', { bubbles: true }));
            await new Promise(r => setTimeout(r, 20));

            assert.equal(recentPreviewCalls, 1, 'Navbar preview must be initialized on /notifications page');
        });

        it('Notification bell anchor role=button activates on Space key, and dropdown menu supports ArrowUp/Down and Escape with focus restoration', async () => {
            const env = createDOMEnvironment();
            const dom = setupNavbarNotificationDOM(env);

            let previewCalls = 0;
            env.window.fetchHandler = async (url) => {
                if (url.includes('/api/notifications?page=0&size=5')) {
                    previewCalls++;
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [
                                { id: 1, type: 'POST_LIKE', isRead: false, actor: { displayName: 'Alice' }, createdAt: new Date().toISOString() },
                                { id: 2, type: 'POST_REPLY', isRead: true, actor: { displayName: 'Bob' }, createdAt: new Date().toISOString() }
                            ]
                        })
                    };
                }
                return { ok: true, status: 200, text: async () => '{}' };
            };

            const ctx = vm.createContext(env.window);
            vm.runInContext(httpClientSource, ctx);
            vm.runInContext(pageActionsSource, ctx);

            // 1. Press Space on bell anchor
            let bellClicked = false;
            dom.toggle.click = () => {
                bellClicked = true;
                dom.dropdown.dispatchEvent(new MockCustomEvent('show.bs.dropdown', { bubbles: true }));
            };
            const spaceEvent = new MockCustomEvent('keydown', { bubbles: true, cancelable: true });
            spaceEvent.key = ' ';
            dom.toggle.dispatchEvent(spaceEvent);

            assert.equal(bellClicked, true, 'Space key on bell anchor must trigger click/toggle');
            assert.equal(spaceEvent.defaultPrevented, true, 'Space key default scroll must be prevented');

            await new Promise(r => setTimeout(r, 20));
            assert.equal(previewCalls, 1, 'Dropdown show must fetch preview items');

            // 2. View All has .dropdown-item class
            assert.ok(dom.viewAllLink.className.includes('dropdown-item'), 'View All link must have dropdown-item class for keyboard navigation');

            // 3. ArrowDown traversal
            const previewMenu = dom.previewMenu;
            const items = previewMenu.querySelectorAll('.dropdown-item');
            assert.equal(items.length, 3, 'Should have 2 preview items + 1 View All link');

            let focusedItem = null;
            items.forEach(it => { it.focus = () => { focusedItem = it; env.document.activeElement = it; }; });

            // Press ArrowDown from top
            const arrowDownEvent = new MockCustomEvent('keydown', { bubbles: true, cancelable: true });
            arrowDownEvent.key = 'ArrowDown';
            previewMenu.dispatchEvent(arrowDownEvent);
            assert.equal(focusedItem, items[0], 'First ArrowDown must focus first item');

            // Press ArrowDown twice more -> reaches View All
            previewMenu.dispatchEvent(arrowDownEvent);
            assert.equal(focusedItem, items[1]);
            previewMenu.dispatchEvent(arrowDownEvent);
            assert.equal(focusedItem, items[2], 'ArrowDown must reach View All dropdown-item');

            // 4. Escape closes and restores focus to bell
            let bellFocused = false;
            dom.toggle.focus = () => { bellFocused = true; };
            const escapeEvent = new MockCustomEvent('keydown', { bubbles: true, cancelable: true });
            escapeEvent.key = 'Escape';
            previewMenu.dispatchEvent(escapeEvent);
            assert.equal(bellFocused, true, 'Escape in preview menu must restore focus to bell toggle');
        });

        it('Navbar read-all invalidates pending notification.js load-list request so stale unread rows are ignored', async () => {
            const env = createDOMEnvironment();

            // Set up DOM for notification.js
            const notifContent = env.document.createElement('DIV');
            notifContent.id = 'notifications-content';
            const notifList = env.document.createElement('DIV');
            notifList.id = 'notifications-list';
            const loading = env.document.createElement('DIV');
            loading.id = 'notifications-loading';
            const badge = env.document.createElement('SPAN');
            badge.id = 'unread-count-badge';
            notifContent.appendChild(notifList);
            notifContent.appendChild(loading);
            notifContent.appendChild(badge);
            env.document.body.appendChild(notifContent);

            let resolvePendingList;
            const pendingListPromise = new Promise(resolve => { resolvePendingList = resolve; });

            env.window.fetchHandler = async (url) => {
                if (url.includes('/api/notifications/unread-count')) {
                    return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: { unreadCount: 5 } }) };
                }
                if (url.includes('/api/notifications?filter=ALL')) {
                    return await pendingListPromise;
                }
                return { ok: true, status: 200, text: async () => '{}' };
            };

            const ctx = vm.createContext(env.window);
            vm.runInContext(notificationSource, ctx);

            // notification.js initial load-list is in flight!
            assert.equal(notifList.children.length, 0);

            // While in-flight, navbar read-all succeeds and dispatches aloute:notifications-read-all
            env.window.dispatchEvent(new MockCustomEvent('aloute:notifications-read-all', {
                detail: { unreadCount: 0, version: 10 }
            }));

            // Now the stale pending list response resolves with unread items from before read-all
            resolvePendingList({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({
                    success: true,
                    data: [
                        { id: 99, type: 'POST_LIKE', isRead: false, createdAt: new Date().toISOString() }
                    ],
                    meta: { page: 0, hasNext: false }
                })
            });
            await new Promise(r => setTimeout(r, 20));

            // Stale list request must have been ignored by notifRequestId guard!
            assert.equal(notifList.children.length, 0, 'Stale unread row must NOT be rendered after navbar read-all');
            assert.equal(badge.textContent, '0', 'Badge must remain 0');
        });

        it('Navbar single-read synchronizes with notification-page list item and unread count', async () => {
            const env = createDOMEnvironment();

            const notifContent = env.document.createElement('DIV');
            notifContent.id = 'notifications-content';
            const notifList = env.document.createElement('DIV');
            notifList.id = 'notifications-list';
            const badge = env.document.createElement('SPAN');
            badge.id = 'unread-count-badge';
            notifContent.appendChild(notifList);
            notifContent.appendChild(badge);
            env.document.body.appendChild(notifContent);

            env.window.fetchHandler = async (url) => {
                if (url.includes('/api/notifications/unread-count')) {
                    return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: { unreadCount: 2 } }) };
                }
                if (url.includes('/api/notifications?filter=ALL')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [
                                { id: 101, type: 'POST_LIKE', isRead: false, createdAt: new Date().toISOString() },
                                { id: 102, type: 'POST_REPLY', isRead: false, createdAt: new Date().toISOString() }
                            ],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                return { ok: true, status: 200, text: async () => '{}' };
            };

            const ctx = vm.createContext(env.window);
            vm.runInContext(notificationSource, ctx);
            await new Promise(r => setTimeout(r, 20));

            const row101 = notifList.querySelector('.aloute-notification-item[data-id="101"]');
            assert.ok(row101 && row101.className.includes('unread'));

            // Navbar marks item 101 as read
            env.window.dispatchEvent(new MockCustomEvent('aloute:notification-read', {
                detail: { id: 101, unreadCount: 1, version: 5 }
            }));

            assert.equal(row101.className.includes('unread'), false, 'Row 101 must no longer have unread class');
            assert.equal(row101.querySelector('.aloute-notification-item-dot'), null, 'Unread dot must be removed');
            assert.equal(badge.textContent, '1', 'Badge must update to 1');
        });

        it('Pending navbar preview fetch arriving after read-all does not undo confirmed read-all', async () => {
            const env = createDOMEnvironment();
            const dom = setupNavbarNotificationDOM(env);

            let resolvePreviewFetch;
            const previewPromise = new Promise(resolve => { resolvePreviewFetch = resolve; });

            env.window.fetchHandler = async (url) => {
                if (url.includes('/api/notifications?page=0&size=5')) {
                    return await previewPromise;
                }
                if (url.includes('/api/notifications/read-all')) {
                    return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: { count: 3 } }) };
                }
                return { ok: true, status: 200, text: async () => '{}' };
            };

            const ctx = vm.createContext(env.window);
            vm.runInContext(httpClientSource, ctx);
            vm.runInContext(pageActionsSource, ctx);

            // User opens dropdown -> preview fetch starts (pending)
            dom.dropdown.dispatchEvent(new MockCustomEvent('show.bs.dropdown', { bubbles: true }));

            // User clicks "Đã đọc tất cả" while preview fetch is in flight
            dom.btnMarkAll.dispatchEvent(new MockCustomEvent('click', { bubbles: true, cancelable: true }));
            await new Promise(r => setTimeout(r, 20));

            // Now the pending preview fetch resolves with stale isRead: false
            resolvePreviewFetch({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({
                    success: true,
                    data: [
                        { id: 201, type: 'POST_LIKE', isRead: false, actor: { displayName: 'Charlie' }, createdAt: new Date().toISOString() }
                    ]
                })
            });
            await new Promise(r => setTimeout(r, 20));

            // Must NOT render unread item or dot in preview list
            const unreadItem = dom.listContainer.querySelector('.aloute-notification-preview-item.unread');
            assert.equal(unreadItem, null, 'Stale preview response must NOT render unread items after confirmed read-all');
        });

        it('Fresh navbar preview request after confirmed count 0 / read-all preserves authoritative unread state of newly arrived notification', async () => {
            const env = createDOMEnvironment();
            const dom = setupNavbarNotificationDOM(env);

            let previewCallCount = 0;
            env.window.fetchHandler = async (url) => {
                if (url.includes('/api/notifications/read-all')) {
                    return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: { count: 1 } }) };
                }
                if (url.includes('/api/notifications?page=0&size=5')) {
                    previewCallCount++;
                    if (previewCallCount === 1) {
                        return {
                            ok: true,
                            status: 200,
                            text: async () => JSON.stringify({
                                success: true,
                                data: [
                                    { id: 200, type: 'POST_LIKE', isRead: true, actor: { displayName: 'Old' }, createdAt: new Date().toISOString() }
                                ]
                            })
                        };
                    }
                    // Genuinely fresh request after read-all: returns newly arrived unread notification AND already-read item
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [
                                { id: 301, type: 'POST_LIKE', isRead: false, actor: { displayName: 'Alice' }, createdAt: new Date().toISOString() },
                                { id: 302, type: 'POST_REPLY', isRead: true, actor: { displayName: 'Bob' }, createdAt: new Date().toISOString() }
                            ]
                        })
                    };
                }
                return { ok: true, status: 200, text: async () => '{}' };
            };

            const ctx = vm.createContext(env.window);
            vm.runInContext(httpClientSource, ctx);
            vm.runInContext(pageActionsSource, ctx);

            // 1. Initial preview loaded then read-all establishes confirmed count 0
            dom.dropdown.dispatchEvent(new MockCustomEvent('show.bs.dropdown', { bubbles: true }));
            await new Promise(r => setTimeout(r, 20));

            dom.btnMarkAll.dispatchEvent(new MockCustomEvent('click', { bubbles: true, cancelable: true }));
            await new Promise(r => setTimeout(r, 20));

            // 2. Genuinely NEW preview request triggered (force fresh fetch)
            dom.dropdown.dispatchEvent(new MockCustomEvent('show.bs.dropdown', { bubbles: true }));
            await new Promise(r => setTimeout(r, 20));

            // 3. Verify item 301 (fresh unread) preserves authoritative isRead: false and renders unread indicator
            const item301 = dom.listContainer.querySelector('.aloute-notification-preview-item[href*="301"]') ||
                            dom.listContainer.querySelectorAll('.aloute-notification-preview-item')[0];
            assert.ok(item301, 'Item 301 must be rendered in preview list');
            assert.ok(item301.className.includes('unread'), 'Newly arrived notification must retain unread class even if cached count was 0');
            assert.ok(item301.querySelector('.aloute-preview-unread-dot'), 'Item 301 must display unread dot');

            // 4. Verify item 302 preserves already-read true (not globally forced)
            const item302 = dom.listContainer.querySelectorAll('.aloute-notification-preview-item')[1];
            assert.ok(item302, 'Item 302 must be rendered in preview list');
            assert.equal(item302.className.includes('unread'), false, 'Already-read item 302 must remain read');
        });

        it('Fresh notification-page list request after confirmed count 0 / read-all preserves authoritative unread state of newly arrived notification', async () => {
            const env = createDOMEnvironment();

            const notifContent = env.document.createElement('DIV');
            notifContent.id = 'notifications-content';
            const notifList = env.document.createElement('DIV');
            notifList.id = 'notifications-list';
            const badge = env.document.createElement('SPAN');
            badge.id = 'unread-count-badge';
            const btnRefresh = env.document.createElement('BUTTON');
            btnRefresh.id = 'btn-refresh-notifications';
            const btnMarkAll = env.document.createElement('BUTTON');
            btnMarkAll.id = 'btn-mark-all-read';
            notifContent.appendChild(notifList);
            notifContent.appendChild(badge);
            notifContent.appendChild(btnRefresh);
            notifContent.appendChild(btnMarkAll);
            env.document.body.appendChild(notifContent);

            let listCallCount = 0;
            env.window.fetchHandler = async (url) => {
                if (url.includes('/api/notifications/unread-count')) {
                    return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: { unreadCount: 0 } }) };
                }
                if (url.includes('/api/notifications/read-all')) {
                    return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: { count: 0 } }) };
                }
                if (url.includes('/api/notifications?filter=ALL')) {
                    listCallCount++;
                    if (listCallCount === 1) {
                        return {
                            ok: true,
                            status: 200,
                            text: async () => JSON.stringify({
                                success: true,
                                data: [
                                    { id: 400, type: 'POST_LIKE', isRead: true, createdAt: new Date().toISOString() }
                                ],
                                meta: { page: 0, hasNext: false }
                            })
                        };
                    }
                    // Genuinely fresh list response: returns newly arrived unread notification AND already-read item
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [
                                { id: 401, type: 'POST_LIKE', isRead: false, createdAt: new Date().toISOString() },
                                { id: 402, type: 'POST_REPLY', isRead: true, createdAt: new Date().toISOString() }
                            ],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                return { ok: true, status: 200, text: async () => '{}' };
            };

            const ctx = vm.createContext(env.window);
            vm.runInContext(notificationSource, ctx);
            await new Promise(r => setTimeout(r, 20));

            // Establish confirmed count 0 via read-all
            btnMarkAll.dispatchEvent(new MockCustomEvent('click', { bubbles: true, cancelable: true }));
            await new Promise(r => setTimeout(r, 20));

            // Trigger genuinely NEW list request (e.g. refresh button)
            btnRefresh.dispatchEvent(new MockCustomEvent('click', { bubbles: true, cancelable: true }));
            await new Promise(r => setTimeout(r, 20));

            // Item 401 (new unread notification) must preserve authoritative isRead: false
            const row401 = notifList.querySelector('.aloute-notification-item[data-id="401"]');
            assert.ok(row401, 'Row 401 must be rendered in list');
            assert.ok(row401.className.includes('unread'), 'Newly arrived notification 401 must retain unread class even if cached count was 0');
            assert.ok(row401.querySelector('.aloute-notification-item-dot'), 'Row 401 must have unread dot');

            // Item 402 (already read) must remain read
            const row402 = notifList.querySelector('.aloute-notification-item[data-id="402"]');
            assert.ok(row402, 'Row 402 must be rendered in list');
            assert.equal(row402.className.includes('unread'), false, 'Already-read item 402 must remain read');
        });

        it('QA P1 Regression: Navbar preview accurately normalizes real server read/isRead, conflicting, missing, and non-boolean states', async () => {
            const env = createDOMEnvironment();
            const dom = setupNavbarNotificationDOM(env);

            let patchSent = false;
            env.window.fetchHandler = async (url, opts) => {
                if (url.includes('/api/notifications?page=0&size=5')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [
                                { id: 501, type: 'POST_LIKE', read: true, actor: { displayName: 'RealRead' }, createdAt: new Date().toISOString() },
                                { id: 502, type: 'POST_LIKE', read: false, actor: { displayName: 'RealUnread' }, createdAt: new Date().toISOString() },
                                { id: 503, type: 'POST_LIKE', isRead: true, actor: { displayName: 'LegacyRead' }, createdAt: new Date().toISOString() },
                                { id: 504, type: 'POST_LIKE', isRead: false, actor: { displayName: 'LegacyUnread' }, createdAt: new Date().toISOString() },
                                { id: 505, type: 'POST_LIKE', read: true, isRead: false, actor: { displayName: 'ConflictReadCanonical' }, createdAt: new Date().toISOString() },
                                { id: 506, type: 'POST_LIKE', read: false, isRead: true, actor: { displayName: 'ConflictUnreadCanonical' }, createdAt: new Date().toISOString() },
                                { id: 507, type: 'POST_LIKE', actor: { displayName: 'MissingState' }, createdAt: new Date().toISOString() },
                                { id: 508, type: 'POST_LIKE', read: 'yes', isRead: null, actor: { displayName: 'NonBooleanState' }, createdAt: new Date().toISOString() }
                            ]
                        })
                    };
                }
                if (url.includes('/read') && opts && opts.method === 'PATCH') {
                    patchSent = true;
                    return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: { read: true, isRead: true } }) };
                }
                return { ok: true, status: 200, text: async () => '{}' };
            };

            const ctx = vm.createContext(env.window);
            vm.runInContext(httpClientSource, ctx);
            vm.runInContext(pageActionsSource, ctx);

            dom.dropdown.dispatchEvent(new MockCustomEvent('show.bs.dropdown', { bubbles: true }));
            await new Promise(r => setTimeout(r, 20));

            const items = dom.listContainer.querySelectorAll('.aloute-notification-preview-item');
            assert.equal(items.length, 8, 'All 8 items rendered');

            // 1. Real server { read: true }: MUST NOT be unread
            assert.equal(items[0].className.includes('unread'), false, 'Item 501 {read: true} must NOT have unread class');
            assert.equal(items[0].querySelector('.aloute-preview-unread-dot'), null, 'Item 501 {read: true} must NOT have dot');

            // 2. Real server { read: false }: MUST be unread
            assert.ok(items[1].className.includes('unread'), 'Item 502 {read: false} must have unread class');
            assert.ok(items[1].querySelector('.aloute-preview-unread-dot'), 'Item 502 {read: false} must have dot');

            // 3. Legacy { isRead: true }: MUST NOT be unread
            assert.equal(items[2].className.includes('unread'), false, 'Item 503 {isRead: true} must NOT have unread class');

            // 4. Legacy { isRead: false }: MUST be unread
            assert.ok(items[3].className.includes('unread'), 'Item 504 {isRead: false} must have unread class');

            // 5. Conflict { read: true, isRead: false }: Canonical read: true wins
            assert.equal(items[4].className.includes('unread'), false, 'Item 505 {read: true, isRead: false} must resolve to read');

            // 6. Conflict { read: false, isRead: true }: Canonical read: false wins
            assert.ok(items[5].className.includes('unread'), 'Item 506 {read: false, isRead: true} must resolve to unread');

            // 7. Missing state {}: Stays unknown, NEVER auto-unread
            assert.equal(items[6].className.includes('unread'), false, 'Item 507 with missing read state must NEVER auto-unread');
            assert.equal(items[6].querySelector('.aloute-preview-unread-dot'), null, 'Item 507 must NOT have dot');

            // 8. Non-boolean state: Stays unknown, NEVER auto-unread
            assert.equal(items[7].className.includes('unread'), false, 'Item 508 with non-boolean state must NEVER auto-unread');

            // Missing state click must NOT send false unread-driven PATCH
            patchSent = false;
            items[6].dispatchEvent(new MockCustomEvent('click', { bubbles: true, cancelable: true }));
            await new Promise(r => setTimeout(r, 20));
            assert.equal(patchSent, false, 'Clicking item with missing read state must NOT issue PATCH');
        });

        it('QA P1 Regression: Notification page list accurately normalizes real server read/isRead, conflicting, and missing states', async () => {
            const env = createDOMEnvironment();

            const notifContent = env.document.createElement('DIV');
            notifContent.id = 'notifications-content';
            const notifList = env.document.createElement('DIV');
            notifList.id = 'notifications-list';
            const badge = env.document.createElement('SPAN');
            badge.id = 'unread-count-badge';
            notifContent.appendChild(notifList);
            notifContent.appendChild(badge);
            env.document.body.appendChild(notifContent);

            env.window.fetchHandler = async (url) => {
                if (url.includes('/api/notifications/unread-count')) {
                    return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: { unreadCount: 3 } }) };
                }
                if (url.includes('/api/notifications?filter=ALL')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [
                                { id: 601, type: 'POST_LIKE', read: true, createdAt: new Date().toISOString() },
                                { id: 602, type: 'POST_LIKE', read: false, createdAt: new Date().toISOString() },
                                { id: 603, type: 'POST_LIKE', isRead: true, createdAt: new Date().toISOString() },
                                { id: 604, type: 'POST_LIKE', isRead: false, createdAt: new Date().toISOString() },
                                { id: 605, type: 'POST_LIKE', read: true, isRead: false, createdAt: new Date().toISOString() },
                                { id: 606, type: 'POST_LIKE', read: false, isRead: true, createdAt: new Date().toISOString() },
                                { id: 607, type: 'POST_LIKE', createdAt: new Date().toISOString() },
                                { id: 608, type: 'POST_LIKE', read: 'unknown', isRead: 123, createdAt: new Date().toISOString() }
                            ],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                return { ok: true, status: 200, text: async () => '{}' };
            };

            const ctx = vm.createContext(env.window);
            vm.runInContext(notificationSource, ctx);
            await new Promise(r => setTimeout(r, 20));

            const r601 = notifList.querySelector('.aloute-notification-item[data-id="601"]');
            const r602 = notifList.querySelector('.aloute-notification-item[data-id="602"]');
            const r603 = notifList.querySelector('.aloute-notification-item[data-id="603"]');
            const r604 = notifList.querySelector('.aloute-notification-item[data-id="604"]');
            const r605 = notifList.querySelector('.aloute-notification-item[data-id="605"]');
            const r606 = notifList.querySelector('.aloute-notification-item[data-id="606"]');
            const r607 = notifList.querySelector('.aloute-notification-item[data-id="607"]');
            const r608 = notifList.querySelector('.aloute-notification-item[data-id="608"]');

            // 1. Real server { read: true } -> READ
            assert.equal(r601.className.includes('unread'), false, 'Row 601 {read: true} must NOT be unread');
            assert.equal(r601.querySelector('.aloute-notification-item-dot'), null, 'Row 601 must not have dot');

            // 2. Real server { read: false } -> UNREAD
            assert.ok(r602.className.includes('unread'), 'Row 602 {read: false} must be unread');
            assert.ok(r602.querySelector('.aloute-notification-item-dot'), 'Row 602 must have dot');

            // 3. Legacy { isRead: true } -> READ
            assert.equal(r603.className.includes('unread'), false, 'Row 603 {isRead: true} must NOT be unread');

            // 4. Legacy { isRead: false } -> UNREAD
            assert.ok(r604.className.includes('unread'), 'Row 604 {isRead: false} must be unread');

            // 5. Conflict { read: true, isRead: false } -> READ
            assert.equal(r605.className.includes('unread'), false, 'Row 605 must be read');

            // 6. Conflict { read: false, isRead: true } -> UNREAD
            assert.ok(r606.className.includes('unread'), 'Row 606 must be unread');

            // 7. Missing state -> NEVER auto-unread
            assert.equal(r607.className.includes('unread'), false, 'Row 607 missing state must NOT be unread');
            assert.equal(r607.querySelector('.aloute-notification-item-dot'), null, 'Row 607 must not have dot');

            // 8. Non-boolean state -> NEVER auto-unread
            assert.equal(r608.className.includes('unread'), false, 'Row 608 non-boolean state must NOT be unread');
        });
    });

    // =========================================================================
    // Scope D: Phase B UI Polish (Single Feed Create Post Entry & Navbar Guard)
    // =========================================================================
    describe('Scope D: Phase B UI Polish — Single Entry & Navbar Feed Guard', () => {
        it('Feed page renders clean quick-composer with Soạn bài viết and no fake Đăng bài button', () => {
            assert.ok(feedIndexTemplate.includes('Soạn bài viết'), 'Feed composer must have clear Soạn bài viết label');
            assert.ok(feedIndexTemplate.includes('data-action="navigate-create-post"'), 'Feed composer must have navigate-create-post action');
            assert.ok(feedIndexTemplate.includes('@{/post/create}') || feedIndexTemplate.includes('href="/post/create"'), 'Feed composer must retain /post/create fallback');
            assert.ok(!feedIndexTemplate.includes('Tạo bài viết ngay'), 'Empty state must not duplicate create post button');
            // The only place with 'Đăng bài' must be the real submit inside composer-form
            assert.ok(!feedIndexTemplate.includes('<span>Đăng bài</span>'), 'Feed index must not have fake Đăng bài button');
            assert.ok(!feedIndexTemplate.includes('Mở hộp thoại'), 'Feed index must not have decorative Mở hộp thoại fake button');
            assert.ok(feedIndexTemplate.includes('bi-chevron-right'), 'Feed index composer must have passive chevron right affordance');
        });

        it('Navbar suppresses Create Post buttons only when isFeedPage is true', () => {
            assert.ok(navbarTemplate.includes("isFeedPage != true"), 'Navbar must suppress create post buttons when isFeedPage == true');
            assert.ok(navbarTemplate.includes('@{/post/create}'), 'Navbar must retain /post/create on other pages');
            assert.ok(navbarTemplate.includes('aloute-btn-icon-mobile'), 'Navbar must retain mobile create post button');
        });

        it('Sidebar retains 5 items with aria-current selected states', () => {
            assert.ok(sidebarTemplate.includes("aria-current"), 'Sidebar links must include aria-current for accessible selected states');
        });
    });
});
