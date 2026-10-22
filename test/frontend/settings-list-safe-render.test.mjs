/**
 * AloUTE — Settings Blocked/Muted Users List Safe Rendering Regression Tests (SEC-FE-01)
 *
 * The Blocked/Muted Users lists previously rendered API response fields
 * (displayName, username, avatarUrl, user id) by interpolating them into an
 * `innerHTML` template. A user can set a display name containing markup
 * (`UpdateProfileRequest.displayName` is only size-validated) and a username has
 * no charset rule, so the stored value was parsed as HTML in the Settings page
 * of the viewer who blocked or muted that account — a stored DOM-injection path.
 *
 * These tests assert that the lists are built with safe DOM APIs, so every
 * dynamic value is rendered as text and never interpreted as markup, and that
 * the existing UI, datasets and action behaviour are preserved.
 *
 * The DOM stub models the two security-relevant behaviours faithfully:
 *   - `textContent = value` stores the value as inert text,
 *   - `innerHTML = markup` parses the markup into child elements.
 * A regression back to `innerHTML` interpolation therefore materialises the
 * payload as real elements (and as attributes) and fails these assertions.
 *
 * Run: node --test test/frontend/settings-list-safe-render.test.mjs
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

const settingsScriptPath = path.resolve(repoRoot, 'src/main/resources/static/js/features/settings/settings.js');
const settingsScriptSource = fs.readFileSync(settingsScriptPath, 'utf-8');

// -----------------------------------------------------------------------------
// DOM stub with security-relevant textContent / innerHTML semantics
// -----------------------------------------------------------------------------

const TAG_RE = /<\s*([a-zA-Z][\w-]*)((?:\s+[^>]*?)?)\/?>/g;
const ATTR_RE = /([\w:-]+)\s*=\s*"([^"]*)"/g;

function camelToDataAttr(name) {
    return 'data-' + name.replace(/[A-Z]/g, (c) => '-' + c.toLowerCase());
}

class StubElement {
    constructor(tag = 'DIV') {
        this.tagName = tag.toUpperCase();
        this.style = {};
        this.id = '';
        this.value = '';
        this.disabled = false;
        this.hidden = false;
        this._attributes = new Map();
        this._children = [];
        this._text = '';
        this._listeners = new Map();
        this._classes = new Set();

        const self = this;
        this.dataset = new Proxy({}, {
            get(_t, prop) {
                if (typeof prop !== 'string') return undefined;
                return self.getAttribute(camelToDataAttr(prop));
            },
            set(_t, prop, value) {
                self.setAttribute(camelToDataAttr(prop), value);
                return true;
            },
            has(_t, prop) {
                return self.getAttribute(camelToDataAttr(prop)) !== null;
            }
        });
    }

    // className and classList stay in sync, as in a real DOM.
    get className() { return Array.from(this._classes).join(' '); }
    set className(value) {
        this._classes = new Set(String(value === null || value === undefined ? '' : value).split(/\s+/).filter(Boolean));
    }

    get classList() {
        const classes = this._classes;
        return {
            add: (...n) => n.forEach((x) => classes.add(x)),
            remove: (...n) => n.forEach((x) => classes.delete(x)),
            toggle: (n, force) => {
                const next = force === undefined ? !classes.has(n) : !!force;
                if (next) classes.add(n); else classes.delete(n);
                return next;
            },
            contains: (n) => classes.has(n)
        };
    }

    // --- text vs markup -----------------------------------------------------
    get textContent() {
        if (this._children.length === 0) return this._text;
        return this._text + this._children.map((c) => c.textContent).join('');
    }

    set textContent(value) {
        this._text = value === null || value === undefined ? '' : String(value);
        this._children = [];
    }

    get innerHTML() {
        return this._text;
    }

    set innerHTML(markup) {
        const html = markup === null || markup === undefined ? '' : String(markup);
        this._text = '';
        this._children = [];
        TAG_RE.lastIndex = 0;
        let match;
        while ((match = TAG_RE.exec(html)) !== null) {
            const child = new StubElement(match[1]);
            ATTR_RE.lastIndex = 0;
            let attr;
            while ((attr = ATTR_RE.exec(match[2] || '')) !== null) {
                child.setAttribute(attr[1], attr[2]);
            }
            this._children.push(child);
        }
        this._text = html.replace(/<[^>]*>/g, '');
    }

    // --- attributes ----------------------------------------------------------
    setAttribute(name, value) {
        this._attributes.set(name, value === null || value === undefined ? '' : String(value));
    }

    getAttribute(name) {
        if (this._attributes.has(name)) return this._attributes.get(name);
        // The class attribute reflects the live class list, as in a real DOM.
        if (name === 'class') return this.className;
        return null;
    }

    removeAttribute(name) { this._attributes.delete(name); }
    hasAttribute(name) { return this._attributes.has(name); }

    get type() { return this.getAttribute('type') || ''; }
    set type(v) { this.setAttribute('type', v); }
    get src() { return this.getAttribute('src') || ''; }
    set src(v) { this.setAttribute('src', v); }
    get alt() { return this.getAttribute('alt') || ''; }
    set alt(v) { this.setAttribute('alt', v); }
    get title() { return this.getAttribute('title') || ''; }
    set title(v) { this.setAttribute('title', v); }
    get width() { return this.getAttribute('width') || ''; }
    set width(v) { this.setAttribute('width', v); }
    get height() { return this.getAttribute('height') || ''; }
    set height(v) { this.setAttribute('height', v); }

    // --- tree ----------------------------------------------------------------
    appendChild(child) { this._children.push(child); return child; }
    removeChild(child) { return child; }
    insertBefore(child) { this._children.push(child); return child; }
    replaceChildren(...kids) { this._children = kids; }
    remove() {}

    descendants() {
        const out = [];
        const walk = (el) => {
            for (const child of (el._children || [])) {
                out.push(child);
                walk(child);
            }
        };
        walk(this);
        return out;
    }

    querySelector(sel) { return this.querySelectorAll(sel)[0] || null; }

    querySelectorAll(sel) {
        const parts = String(sel).split(',').map((s) => s.trim()).filter(Boolean);
        const out = [];
        for (const part of parts) {
            for (const el of this.descendants()) {
                let hit = false;
                if (part.startsWith('#')) hit = el.getAttribute('id') === part.slice(1);
                else if (part.startsWith('.')) hit = el.classList.contains(part.slice(1));
                else if (part.startsWith('[')) {
                    const m = /^\[([\w:-]+)(?:=("([^"]*)"|'([^']*)'|([^\]]*)))?\]$/.exec(part);
                    if (m) {
                        const raw = el.getAttribute(m[1]);
                        if (raw === null) hit = false;
                        else if (m[2] === undefined) hit = true;
                        else hit = raw === (m[3] ?? m[4] ?? m[5]);
                    }
                } else hit = el.tagName === part.toUpperCase();
                if (hit) out.push(el);
            }
        }
        return out;
    }

    closest() { return null; }
    getBoundingClientRect() { return { x: 0, y: 0, top: 0, left: 0, width: 0, height: 0, right: 0, bottom: 0 }; }
    focus() {}
    click() {}
    scrollIntoView() {}
    addEventListener(type, fn) {
        if (!this._listeners.has(type)) this._listeners.set(type, []);
        this._listeners.get(type).push(fn);
    }
    removeEventListener(type, fn) {
        if (!this._listeners.has(type)) return;
        this._listeners.set(type, this._listeners.get(type).filter((l) => l !== fn));
    }
    dispatchEvent(evt) {
        evt.target = evt.target || this;
        evt.currentTarget = this;
        for (const fn of this._listeners.get(evt.type) || []) fn(evt);
        return !evt.defaultPrevented;
    }
    get firstChild() { return this._children[0] || null; }
    get lastChild() { return this._children[this._children.length - 1] || null; }
    get parentNode() { return null; }
    get offsetParent() { return null; }
}

const TAG_BY_ID = {
    'blocked-users-list': 'div',
    'muted-users-list': 'div',
    'blocked-users-empty': 'div',
    'muted-users-empty': 'div',
    'blocked-users-loading': 'div',
    'muted-users-loading': 'div',
    'btn-refresh-blocked-users': 'button',
    'btn-refresh-muted-users': 'button',
    'form-quick-block': 'form',
    'form-quick-mute': 'form',
    'input-block-username': 'input',
    'input-mute-username': 'input',
    'btn-submit-quick-block': 'button',
    'btn-submit-quick-mute': 'button',
    'block-feedback': 'div',
    'mute-feedback': 'div',
    'block-user-spinner': 'span',
    'mute-user-spinner': 'span',
    'modal-confirm-unblock': 'div',
    'modal-confirm-unmute': 'div',
    'unblock-confirm-text': 'span',
    'unmute-confirm-text': 'span',
    'btn-do-unblock': 'button',
    'btn-do-unmute': 'button',
    'unblock-feedback': 'div',
    'unmute-feedback': 'div',
    'unblock-spinner': 'span',
    'unmute-spinner': 'span',
    'settings-content': 'div'
};

function createEnvironment({ pathname = '/settings/blocking', payload = [] } = {}) {
    const elements = new Map();
    const getStub = (id) => {
        if (!elements.has(id)) {
            const el = new StubElement(TAG_BY_ID[id] || 'div');
            el.setAttribute('id', id);
            elements.set(id, el);
        }
        return elements.get(id);
    };

    const documentListeners = new Map();
    const document = {
        readyState: 'complete',
        cookie: 'XSRF-TOKEN=test-csrf-token;',
        activeElement: null,
        body: new StubElement('BODY'),
        documentElement: new StubElement('HTML'),
        createElement: (tag) => new StubElement(tag),
        createTextNode: (text) => {
            const node = new StubElement('#text');
            node.textContent = String(text);
            return node;
        },
        getElementById: (id) => getStub(id),
        querySelector: (sel) => {
            const idMatch = /^#([\w-]+)$/.exec(sel || '');
            if (idMatch) return getStub(idMatch[1]);
            return null;
        },
        querySelectorAll: () => [],
        addEventListener: (type, fn) => {
            if (!documentListeners.has(type)) documentListeners.set(type, []);
            documentListeners.get(type).push(fn);
        },
        removeEventListener: (type, fn) => {
            if (!documentListeners.has(type)) return;
            documentListeners.set(type, documentListeners.get(type).filter((l) => l !== fn));
        },
        dispatchEvent: (evt) => {
            evt.target = evt.target || document;
            evt.currentTarget = document;
            for (const fn of documentListeners.get(evt.type) || []) fn(evt);
            return !evt.defaultPrevented;
        }
    };

    const httpCalls = [];
    const storage = new Map();
    const window = {
        document,
        console,
        URL: globalThis.URL,
        location: { href: 'http://localhost' + pathname, pathname, search: '', hash: '' },
        history: { replaceState: () => {} },
        setTimeout: (fn, ms) => setTimeout(fn, ms),
        clearTimeout: (id) => clearTimeout(id),
        navigator: { clipboard: { writeText: async () => {} } },
        localStorage: {
            getItem: (k) => (storage.has(k) ? storage.get(k) : null),
            setItem: (k, v) => storage.set(k, String(v)),
            removeItem: (k) => storage.delete(k)
        },
        sessionStorage: {
            getItem: (k) => (storage.has(k) ? storage.get(k) : null),
            setItem: (k, v) => storage.set(k, String(v)),
            removeItem: (k) => storage.delete(k)
        },
        alert: () => {},
        confirm: () => true,
        addEventListener: () => {},
        removeEventListener: () => {}
    };
    window.window = window;
    document.defaultView = window;

    window.alouteHttp = {
        request: async (url, opts = {}) => {
            httpCalls.push({ url, method: opts.method || 'GET' });
            return {
                ok: true,
                status: 200,
                text: async () => JSON.stringify({ success: true, data: payload, meta: null })
            };
        },
        readJson: async (response) => JSON.parse(await response.text())
    };

    const ctx = vm.createContext(window);
    vm.runInContext(settingsScriptSource, ctx);

    return { document, window, httpCalls, elements };
}

// -----------------------------------------------------------------------------
// Helpers
// -----------------------------------------------------------------------------

const XSS_DISPLAY_NAME = '<img src=x onerror="window.__xss=1">';
const XSS_USERNAME = '"><script>window.__xss=1</script>';

function rowsOf(listEl) {
    return listEl._children;
}

function findByClass(root, className) {
    return root.descendants().find((el) => el.classList.contains(className));
}

function assertNoMarkupInjected(root, label, win) {
    for (const el of root.descendants()) {
        assert.notEqual(el.tagName, 'SCRIPT', label + ': no <script> element may be created from user data');
        assert.equal(el.getAttribute('onerror'), null, label + ': no onerror handler may be created from user data');
        assert.equal(el.getAttribute('onload'), null, label + ': no onload handler may be created from user data');
    }
    assert.equal(win.__xss, undefined, label + ': injected payload must never execute');
}

const settle = () => new Promise((r) => setTimeout(r, 20));

// -----------------------------------------------------------------------------
// Tests
// -----------------------------------------------------------------------------

describe('AloUTE Settings Blocked/Muted list safe rendering (SEC-FE-01)', () => {
    describe('Blocked Users list', () => {
        it('renders normal, Unicode and Vietnamese values as text', async () => {
            const env = createEnvironment({
                pathname: '/settings/blocking',
                payload: [
                    { id: 41, username: 'nguyen.an', displayName: 'Nguyễn An', avatarUrl: 'https://cdn.example.com/a.png', isVerified: true },
                    { id: 42, username: 'tranthi.b', displayName: 'Trần Thị B', avatarUrl: null, isVerified: false }
                ]
            });
            await settle();

            const rows = rowsOf(env.elements.get('blocked-users-list'));
            assert.equal(rows.length, 2, 'both blocked users must be rendered');

            const nameSpan = findByClass(rows[0], 'fw-bold');
            assert.ok(nameSpan, 'name row must exist');
            assert.equal(nameSpan.textContent, 'Nguyễn An', 'Vietnamese display name must be preserved as text');
            assert.ok(rows[0].textContent.includes('@nguyen.an'), 'username must be rendered');
        });

        it('never interprets display name or username markup', async () => {
            const env = createEnvironment({
                pathname: '/settings/blocking',
                payload: [
                    { id: 77, username: XSS_USERNAME, displayName: XSS_DISPLAY_NAME, avatarUrl: null, isVerified: false }
                ]
            });
            await settle();

            const rows = rowsOf(env.elements.get('blocked-users-list'));
            assert.equal(rows.length, 1);
            const row = rows[0];

            assertNoMarkupInjected(row, 'blocked list', env.window);

            const nameSpan = findByClass(row, 'fw-bold');
            assert.equal(nameSpan.textContent, XSS_DISPLAY_NAME, 'display name must be rendered verbatim as text');

            const imgs = row.descendants().filter((el) => el.tagName === 'IMG');
            assert.equal(imgs.length, 1, 'exactly one avatar image may exist (no injected <img>)');
            assert.equal(imgs[0].getAttribute('src'), '/images/avatar-fallback.svg', 'missing avatar falls back to the placeholder');

            const usernameRow = row.descendants().find((el) => el.classList.contains('text-muted'));
            assert.equal(usernameRow.textContent, '@' + XSS_USERNAME, 'username must be rendered verbatim as text');
        });

        it('rejects unsafe avatar URL schemes and keeps legitimate https media URLs', async () => {
            const env = createEnvironment({
                pathname: '/settings/blocking',
                payload: [
                    { id: 1, username: 'a1', displayName: 'A1', avatarUrl: 'javascript:alert(1)', isVerified: false },
                    { id: 2, username: 'a2', displayName: 'A2', avatarUrl: 'data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=', isVerified: false },
                    { id: 3, username: 'a3', displayName: 'A3', avatarUrl: 'vbscript:msgbox(1)', isVerified: false },
                    { id: 4, username: 'a4', displayName: 'A4', avatarUrl: '//evil.example.com/x.png', isVerified: false },
                    { id: 5, username: 'a5', displayName: 'A5', avatarUrl: 'not a url', isVerified: false },
                    { id: 9, username: 'a9', displayName: 'A9', avatarUrl: 'JaVaScRiPt:alert(1)', isVerified: false },
                    { id: 10, username: 'a10', displayName: 'A10', avatarUrl: '  javascript:alert(1)  ', isVerified: false },
                    { id: 11, username: 'a11', displayName: 'A11', avatarUrl: 'java	script:alert(1)', isVerified: false },
                    { id: 12, username: 'a12', displayName: 'A12', avatarUrl: '&#106;avascript:alert(1)', isVerified: false },
                    { id: 13, username: 'a13', displayName: 'A13', avatarUrl: 'blob:https://evil.example.com/x', isVerified: false },
                    { id: 14, username: 'a14', displayName: 'A14', avatarUrl: '/' + String.fromCharCode(92) + 'evil.example.com/x.png', isVerified: false },
                    { id: 6, username: 'a6', displayName: 'A6', avatarUrl: 'https://cdn.example.com/legit.png', isVerified: false },
                    { id: 7, username: 'a7', displayName: 'A7', avatarUrl: 'https://firebasestorage.googleapis.com/avatars/7.png', isVerified: false },
                    { id: 8, username: 'a8', displayName: 'A8', avatarUrl: null, isVerified: false }
                ]
            });
            await settle();

            const rows = rowsOf(env.elements.get('blocked-users-list'));
            assert.equal(rows.length, 14);

            // Key the expectations by username so the table order cannot silently shift them.
            const srcByUsername = {};
            for (const row of rows) {
                const btn = row.descendants().find((el) => el.classList.contains('btn-unblock-trigger'));
                const img = row.descendants().find((el) => el.tagName === 'IMG');
                srcByUsername[btn.getAttribute('data-username')] = img.getAttribute('src');
            }

            const FALLBACK = '/images/avatar-fallback.svg';
            assert.equal(srcByUsername['a1'], FALLBACK, 'javascript: URL must be rejected');
            assert.equal(srcByUsername['a2'], FALLBACK, 'data: URL must be rejected');
            assert.equal(srcByUsername['a3'], FALLBACK, 'vbscript: URL must be rejected');
            assert.equal(srcByUsername['a4'], FALLBACK, 'protocol-relative URL must be rejected');
            assert.equal(srcByUsername['a5'], FALLBACK, 'malformed URL must be rejected');
            assert.equal(srcByUsername['a9'], FALLBACK, 'mixed-case javascript: URL must be rejected');
            assert.equal(srcByUsername['a10'], FALLBACK, 'padded javascript: URL must be rejected');
            assert.equal(srcByUsername['a11'], FALLBACK, 'tab-split javascript: URL must be rejected');
            assert.equal(srcByUsername['a12'], FALLBACK, 'HTML-entity javascript: URL must be rejected');
            assert.equal(srcByUsername['a13'], FALLBACK, 'blob: URL must be rejected');
            assert.equal(srcByUsername['a14'], FALLBACK, 'backslash protocol-relative URL must be rejected');
            assert.equal(srcByUsername['a6'], 'https://cdn.example.com/legit.png', 'legitimate https media URL must be preserved');
            assert.equal(srcByUsername['a7'], 'https://firebasestorage.googleapis.com/avatars/7.png', 'legitimate Firebase avatar URL must be preserved');
            assert.equal(srcByUsername['a8'], FALLBACK, 'missing avatar must fall back to the placeholder');
        });

        it('preserves the verified badge, layout classes and action datasets', async () => {
            const env = createEnvironment({
                pathname: '/settings/blocking',
                payload: [
                    { id: 91, username: 'verified.user', displayName: 'Verified User', avatarUrl: null, isVerified: true },
                    { id: 92, username: 'plain.user', displayName: 'Plain User', avatarUrl: null, isVerified: false }
                ]
            });
            await settle();

            const rows = rowsOf(env.elements.get('blocked-users-list'));
            assert.equal(rows.length, 2);

            const verifiedRow = rows[0];
            assert.ok(verifiedRow.classList.contains('p-3') && verifiedRow.classList.contains('shadow-sm'),
                'blocked row must keep its Bootstrap layout classes');

            const badge = verifiedRow.descendants().find((el) => el.tagName === 'I');
            assert.ok(badge, 'verified badge icon must be rendered');
            assert.equal(badge.getAttribute('title'), 'Đã xác thực', 'verified badge title must be preserved');

            const plainRow = rows[1];
            assert.equal(plainRow.descendants().filter((el) => el.tagName === 'I').length, 0,
                'unverified user must not render a badge');

            const btn = verifiedRow.descendants().find((el) => el.classList.contains('btn-unblock-trigger'));
            assert.ok(btn, 'unblock action button must exist');
            assert.equal(btn.tagName, 'BUTTON', 'action must be a button element');
            assert.equal(btn.getAttribute('type'), 'button', 'action button type must be preserved');
            assert.ok(btn.classList.contains('btn-outline-secondary') && btn.classList.contains('rounded-pill'),
                'action button classes must be preserved');
            assert.equal(btn.textContent, 'Bỏ chặn', 'action label must be preserved');
            assert.equal(btn.getAttribute('data-user-id'), '91', 'valid user id must be preserved in the dataset');
            assert.equal(btn.getAttribute('data-username'), 'verified.user', 'username must be preserved in the dataset');
        });

        it('never emits a non-canonical or invalid action identifier', async () => {
            const env = createEnvironment({
                pathname: '/settings/blocking',
                payload: [
                    { id: 101, username: 'ok', displayName: 'Ok', avatarUrl: null, isVerified: false },
                    { id: '1e3', username: 'exp', displayName: 'Exp', avatarUrl: null, isVerified: false },
                    { id: '0x10', username: 'hex', displayName: 'Hex', avatarUrl: null, isVerified: false },
                    { id: ' 1 ', username: 'spaced', displayName: 'Spaced', avatarUrl: null, isVerified: false },
                    { id: -5, username: 'neg', displayName: 'Neg', avatarUrl: null, isVerified: false },
                    { id: null, username: 'none', displayName: 'None', avatarUrl: null, isVerified: false }
                ]
            });
            await settle();

            const rows = rowsOf(env.elements.get('blocked-users-list'));
            assert.equal(rows.length, 6);
            const ids = rows.map((row) => row.descendants().find((el) => el.classList.contains('btn-unblock-trigger')).getAttribute('data-user-id'));
            assert.equal(ids[0], '101', 'a canonical positive id must be preserved verbatim');
            for (const bad of ids.slice(1)) {
                assert.equal(bad, '', 'non-canonical or invalid ids must not be emitted as an action identifier');
            }
        });

        it('falls back to the username when the display name is missing', async () => {
            const env = createEnvironment({
                pathname: '/settings/blocking',
                payload: [{ id: 55, username: 'only_username', displayName: null, avatarUrl: null, isVerified: false }]
            });
            await settle();

            const rows = rowsOf(env.elements.get('blocked-users-list'));
            const nameSpan = findByClass(rows[0], 'fw-bold');
            assert.equal(nameSpan.textContent, 'only_username', 'username must be used when display name is absent');
        });
    });

    describe('Muted Users list', () => {
        it('never interprets display name or username markup', async () => {
            const env = createEnvironment({
                pathname: '/settings/muted',
                payload: [
                    { id: 88, username: XSS_USERNAME, displayName: XSS_DISPLAY_NAME, avatarUrl: 'javascript:alert(1)', isVerified: false }
                ]
            });
            await settle();

            const rows = rowsOf(env.elements.get('muted-users-list'));
            assert.equal(rows.length, 1);
            const row = rows[0];

            assertNoMarkupInjected(row, 'muted list', env.window);

            const nameSpan = findByClass(row, 'fw-bold');
            assert.equal(nameSpan.textContent, XSS_DISPLAY_NAME, 'display name must be rendered verbatim as text');

            const imgs = row.descendants().filter((el) => el.tagName === 'IMG');
            assert.equal(imgs.length, 1, 'exactly one avatar image may exist (no injected <img>)');
            assert.equal(imgs[0].getAttribute('src'), '/images/avatar-fallback.svg', 'javascript: avatar must be rejected');

            const btn = row.descendants().find((el) => el.classList.contains('btn-unmute-trigger'));
            assert.ok(btn, 'unmute action button must exist');
            assert.ok(btn.classList.contains('btn-outline-warning'), 'action button classes must be preserved');
            assert.equal(btn.textContent, 'Bật lại thông báo', 'action label must be preserved');
            const icon = btn.descendants().find((el) => el.tagName === 'I');
            assert.ok(icon && icon.getAttribute('class') === 'bi bi-bell me-1', 'action icon must be preserved');
            assert.equal(btn.getAttribute('data-user-id'), '88', 'valid user id must be preserved');
            assert.equal(btn.getAttribute('data-username'), XSS_USERNAME, 'username must be preserved verbatim in the dataset');
        });

        it('preserves the verified badge and avatar size for muted users', async () => {
            const env = createEnvironment({
                pathname: '/settings/muted',
                payload: [{ id: 12, username: 'muted.user', displayName: 'Muted User', avatarUrl: 'https://cdn.example.com/m.png', isVerified: true }]
            });
            await settle();

            const rows = rowsOf(env.elements.get('muted-users-list'));
            const row = rows[0];
            const img = row.descendants().find((el) => el.tagName === 'IMG');
            assert.equal(img.getAttribute('src'), 'https://cdn.example.com/m.png');
            assert.equal(img.getAttribute('width'), '40', 'muted avatar size must stay 40');
            assert.equal(img.getAttribute('height'), '40');
            assert.equal(img.getAttribute('alt'), 'Muted User', 'avatar alt text must be preserved');
            assert.ok(row.descendants().some((el) => el.tagName === 'I' && el.getAttribute('title') === 'Đã xác thực'),
                'verified badge must be preserved');
        });
    });

    describe('Lifecycle and scope', () => {
        it('loads the muted list exactly once and never the blocked list on the muted route', async () => {
            const env = createEnvironment({
                pathname: '/settings/muted',
                payload: [{ id: 1, username: 'x', displayName: 'X', avatarUrl: null, isVerified: false }]
            });
            await settle();

            assert.equal(env.httpCalls.filter((c) => c.url === '/api/social/mutes').length, 1,
                'Muted Users list must load exactly once');
            assert.equal(env.httpCalls.filter((c) => c.url === '/api/social/blocks').length, 0,
                'Blocked Users list must not load on the muted route');
        });

        it('the avatar fallback asset referenced by the lists exists in the repository', () => {
            const fallback = path.resolve(repoRoot, 'src/main/resources/static/images/avatar-fallback.svg');
            assert.ok(fs.existsSync(fallback), 'static/images/avatar-fallback.svg must exist');
        });

        it('loads each list exactly once on first navigation (PR #55 lifecycle preserved)', async () => {
            const env = createEnvironment({
                pathname: '/settings/blocking',
                payload: [{ id: 1, username: 'x', displayName: 'X', avatarUrl: null, isVerified: false }]
            });
            await settle();

            const blockedCalls = env.httpCalls.filter((c) => c.url === '/api/social/blocks');
            assert.equal(blockedCalls.length, 1, 'Blocked Users list must load exactly once');
            assert.equal(env.httpCalls.filter((c) => c.url === '/api/social/mutes').length, 0,
                'Muted Users list must not load on the blocking route');
        });

        it('no longer renders user data through innerHTML interpolation', () => {
            for (const marker of ['function renderBlockedUsers', 'function renderMutedUsers']) {
                const start = settingsScriptSource.indexOf(marker);
                assert.ok(start > -1, marker + ' must still exist');
                const end = settingsScriptSource.indexOf('\n            }', start);
                const body = settingsScriptSource.slice(start, end);
                assert.ok(!/innerHTML\s*=\s*`/.test(body), marker + ' must not assign an HTML template');
                assert.ok(!/\$\{/.test(body), marker + ' must not interpolate template expressions');
            }
            assert.ok(settingsScriptSource.includes('textContent = '),
                'dynamic user values must be written with textContent');
        });
    });
});
