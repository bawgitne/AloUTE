/**
 * AloUTE — Settings Page Initialization Lifecycle Regression Tests
 *
 * Covers the verified frontend defect F-01:
 *   templates/settings/index.html loads features/settings/settings.js with `defer`, while the
 *   shared transport client core/http-client.js is loaded later in templates/layouts/single.html
 *   (also `defer`). Deferred scripts execute in document order, so settings.js used to run its
 *   initializer while `document.readyState` was already 'interactive' and `window.alouteHttp`
 *   did not exist yet — the Muted/Blocked Users lists then failed with a misleading
 *   "Lỗi kết nối khi tải danh sách ..." banner on every first load.
 *
 * Also covers F-03: the cover fallback asset referenced by the Settings template and by the
 * settings bundle must exist, so the server-rendered and the JavaScript fallback paths resolve.
 *
 * Run: node --test test/frontend/settings-init-lifecycle.test.mjs
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
const settingsTemplatePath = path.resolve(repoRoot, 'src/main/resources/templates/settings/index.html');
const coverFallbackPath = path.resolve(repoRoot, 'src/main/resources/static/images/cover-fallback.svg');

const settingsScriptSource = fs.readFileSync(settingsScriptPath, 'utf-8');
const settingsTemplateSource = fs.readFileSync(settingsTemplatePath, 'utf-8');

// -----------------------------------------------------------------------------
// Lenient DOM stub — permissive enough for the whole settings bundle to boot
// -----------------------------------------------------------------------------

class StubElement {
    constructor(tag = 'DIV') {
        this.tagName = tag.toUpperCase();
        this.id = '';
        this.className = '';
        this.dataset = {};
        this.style = {};
        this.attributes = {};
        this.children = [];
        this.value = '';
        this.textContent = '';
        this.innerHTML = '';
        this.disabled = false;
        this.hidden = false;
        this.type = '';
        this.checked = false;
        this.files = [];
        this._classes = new Set();
        this._listeners = new Map();
    }

    get classList() {
        const classes = this._classes;
        return {
            add: (...names) => names.forEach((n) => classes.add(n)),
            remove: (...names) => names.forEach((n) => classes.delete(n)),
            toggle: (name, force) => {
                const next = force === undefined ? !classes.has(name) : !!force;
                if (next) classes.add(name); else classes.delete(name);
                return next;
            },
            contains: (name) => classes.has(name)
        };
    }

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
        const list = this._listeners.get(evt.type) || [];
        for (const fn of list) fn(evt);
        return !evt.defaultPrevented;
    }

    appendChild(child) { this.children.push(child); return child; }
    removeChild(child) { return child; }
    insertBefore(child) { this.children.push(child); return child; }
    replaceChildren() { this.children = []; }
    remove() {}
    focus() {}
    click() {}
    scrollIntoView() {}
    closest() { return null; }
    querySelector() { return null; }
    querySelectorAll() { return []; }
    getBoundingClientRect() { return { x: 0, y: 0, top: 0, left: 0, width: 0, height: 0, right: 0, bottom: 0 }; }
    setAttribute(name, value) { this.attributes[name] = value; }
    getAttribute(name) { return name in this.attributes ? this.attributes[name] : ''; }
    removeAttribute(name) { delete this.attributes[name]; }
    hasAttribute(name) { return name in this.attributes; }
    get firstChild() { return this.children[0] || null; }
    get lastChild() { return this.children[this.children.length - 1] || null; }
    get parentNode() { return null; }
    get offsetParent() { return null; }
}

function createSettingsEnvironment({ readyState = 'interactive', pathname = '/settings/muted' } = {}) {
    const elements = new Map();
    const getStub = (id) => {
        if (!elements.has(id)) {
            const el = new StubElement('DIV');
            el.id = id;
            elements.set(id, el);
        }
        return elements.get(id);
    };

    const documentListeners = new Map();
    const document = {
        readyState,
        cookie: 'XSRF-TOKEN=test-csrf-token;',
        activeElement: null,
        body: new StubElement('BODY'),
        documentElement: new StubElement('HTML'),
        createElement: (tag) => new StubElement(tag),
        createTextNode: (text) => ({ textContent: text }),
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
            const list = documentListeners.get(evt.type) || [];
            for (const fn of list) fn(evt);
            return !evt.defaultPrevented;
        }
    };

    const httpCalls = [];
    const storage = new Map();
    const window = {
        document,
        console,
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

    // The shared transport client is a SEPARATE deferred script. Before F-01 it was not yet
    // defined while settings.js initialized; the fix must make settings.js wait for it.
    function installHttpClient() {
        window.alouteHttp = {
            request: async (url, opts = {}) => {
                httpCalls.push({ url, method: opts.method || 'GET' });
                return {
                    ok: true,
                    status: 200,
                    text: async () => JSON.stringify({
                        success: true,
                        data: [],
                        meta: { page: 0, size: 50, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false }
                    })
                };
            },
            readJson: async (response) => JSON.parse(await response.text())
        };
    }

    return { document, window, httpCalls, elements, installHttpClient };
}

function fireDOMContentLoaded(document) {
    document.dispatchEvent({ type: 'DOMContentLoaded' });
}

function runSettingsScript(env) {
    const ctx = vm.createContext(env.window);
    vm.runInContext(settingsScriptSource, ctx);
    return ctx;
}

// -----------------------------------------------------------------------------
// Tests
// -----------------------------------------------------------------------------

describe('AloUTE Settings Page Initialization Lifecycle', () => {
    it('F-01: does not initialize while the deferred bundle runs before the shared HTTP client', async () => {
        const env = createSettingsEnvironment({ readyState: 'interactive', pathname: '/settings/muted' });

        // No window.alouteHttp yet — exactly the state of the old, broken ordering.
        assert.equal(env.window.alouteHttp, undefined, 'precondition: shared HTTP client must not exist yet');
        runSettingsScript(env);

        assert.equal(
            env.httpCalls.length,
            0,
            'settings.js must not issue requests before the shared HTTP client is available'
        );

        env.installHttpClient();
        fireDOMContentLoaded(env.document);
        await new Promise((r) => setTimeout(r, 20));

        const muteCalls = env.httpCalls.filter((c) => c.url === '/api/social/mutes');
        assert.equal(muteCalls.length, 1, 'Muted Users list must load exactly once on first navigation');

        const listHtml = env.elements.get('muted-users-list').innerHTML;
        assert.ok(
            !/L\u1ED7i k\u1EBFt n\u1ED1i/i.test(listHtml),
            'Muted Users list must not render a connection error when the HTTP client is available'
        );
    });

    it('F-01: initializes immediately when the document has already finished loading', async () => {
        const env = createSettingsEnvironment({ readyState: 'complete', pathname: '/settings/muted' });
        env.installHttpClient();

        runSettingsScript(env);
        await new Promise((r) => setTimeout(r, 20));

        const muteCalls = env.httpCalls.filter((c) => c.url === '/api/social/mutes');
        assert.equal(muteCalls.length, 1, 'Already-loaded documents must still initialize exactly once');
    });

    it('F-01: Blocked Users list loads on first navigation without a manual refresh', async () => {
        const env = createSettingsEnvironment({ readyState: 'interactive', pathname: '/settings/blocking' });
        runSettingsScript(env);

        env.installHttpClient();
        fireDOMContentLoaded(env.document);
        await new Promise((r) => setTimeout(r, 20));

        const blockCalls = env.httpCalls.filter((c) => c.url === '/api/social/blocks');
        assert.equal(blockCalls.length, 1, 'Blocked Users list must load exactly once on first navigation');

        const listHtml = env.elements.get('blocked-users-list').innerHTML;
        assert.ok(
            !/L\u1ED7i k\u1EBFt n\u1ED1i/i.test(listHtml),
            'Blocked Users list must not render a connection error when the HTTP client is available'
        );
    });

    it('F-01: repeated script evaluation and repeated DOMContentLoaded do not duplicate loads', async () => {
        const env = createSettingsEnvironment({ readyState: 'interactive', pathname: '/settings/muted' });
        env.installHttpClient();

        runSettingsScript(env);
        runSettingsScript(env);
        fireDOMContentLoaded(env.document);
        fireDOMContentLoaded(env.document);
        await new Promise((r) => setTimeout(r, 20));

        const muteCalls = env.httpCalls.filter((c) => c.url === '/api/social/mutes');
        assert.equal(muteCalls.length, 1, 'Duplicate initialization must not duplicate list requests');
    });

    it('F-01: the settings bundle is loaded with defer and never initializes synchronously', () => {
        assert.ok(
            /addEventListener\('DOMContentLoaded', initSettings/.test(settingsScriptSource),
            'settings.js must register initSettings on DOMContentLoaded'
        );
        assert.ok(
            !/else\s*\{\s*initSettings\(\);\s*\}/.test(settingsScriptSource),
            'settings.js must not fall back to synchronous initialization'
        );
        assert.ok(
            settingsTemplateSource.includes('settings.js'),
            'settings/index.html must keep loading the settings bundle'
        );
    });

    it('F-03: the cover fallback asset referenced by Settings exists and is a valid SVG', () => {
        assert.ok(fs.existsSync(coverFallbackPath), 'static/images/cover-fallback.svg must exist');

        const svg = fs.readFileSync(coverFallbackPath, 'utf-8');
        assert.ok(svg.includes('<svg'), 'cover fallback must be an SVG document');
        assert.ok(svg.includes('viewBox'), 'cover fallback must declare a viewBox so it scales responsively');
        assert.ok(svg.length < 4096, 'cover fallback must stay a lightweight local asset');

        // XML forbids a double hyphen inside a comment: such a document is served with HTTP 200 but
        // the browser refuses to decode it, which renders a broken image instead of the placeholder.
        const comments = svg.match(/<!--[\s\S]*?-->/g) || [];
        for (const comment of comments) {
            const body = comment.replace(/^<!--/, '').replace(/-->$/, '');
            assert.ok(
                !body.includes('--'),
                `cover fallback SVG comment must not contain a double hyphen: ${comment}`
            );
        }

        assert.ok(
            settingsTemplateSource.includes('/images/cover-fallback.svg'),
            'settings/index.html must reference /images/cover-fallback.svg'
        );
        assert.ok(
            settingsScriptSource.includes('/images/cover-fallback.svg'),
            'settings.js must reference the same /images/cover-fallback.svg path'
        );
    });
});
