import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import vm from 'node:vm';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const pageActionsSource = readFileSync(path.join(root, 'src/main/resources/static/js/page-actions.js'), 'utf8');

function createEnvironment() {
    const elements = new Map();
    const fetchCalls = [];
    let fetchHandler = async () => ({ ok: false, status: 500, text: async () => '' });
    const documentListeners = new Map();

    function mkEl(tag) {
        const el = {
            tagName: (tag || 'DIV').toUpperCase(),
            className: '',
            innerHTML: '',
            disabled: false,
            style: {},
            dataset: {},
            attrs: {},
            children: [],
            setAttribute(k, v) {
                this.attrs[k] = String(v);
                if (k === 'id') this.id = v;
                if (String(k).startsWith('data-')) {
                    const key = String(k).slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase());
                    el.dataset[key] = String(v);
                }
            },
            getAttribute(k) { return this.attrs[k] ?? null; },
            hasAttribute(k) { return k in this.attrs; },
            removeAttribute(k) { delete this.attrs[k]; },
            appendChild() {},
            closest(selector) {
                if (selector.includes('data-action')) {
                    return this.attrs['data-action'] ? this : null;
                }
                return null;
            },
            querySelectorAll() { return []; },
            addEventListener(type, fn) { (this.listeners ||= {})[type] ||= []; this.listeners[type].push(fn); },
            listeners: {},
            classList: {
                _set: new Set(),
                add(...cs) { for (const c of cs) this._set.add(c); },
                remove(...cs) { for (const c of cs) this._set.delete(c); },
                contains(c) { return this._set.has(c); },
            },
        };
        return el;
    }

    const document = {
        readyState: 'complete',
        getElementById(id) { return elements.get(id) || null; },
        querySelector() { return null; },
        querySelectorAll() { return []; },
        addEventListener(type, fn) {
            if (!documentListeners.has(type)) documentListeners.set(type, []);
            documentListeners.get(type).push(fn);
        },
        createElement: (tag) => mkEl(tag),
        body: mkEl('BODY'),
        documentElement: mkEl('HTML'),
    };

    const window = {
        fetch: async (url, options) => {
            fetchCalls.push({ url: String(url), options: options || {} });
            return fetchHandler(String(url), options || {});
        },
        localStorage: { getItem: () => null, setItem() {}, removeItem() {} },
        addEventListener() {},
    };

    const sandbox = {
        window,
        document,
        fetch: window.fetch,
        localStorage: window.localStorage,
        notice() {},
        csrfHeaders: (extra) => ({ ...(extra || {}) }),
        translateError: (err, fallback) => fallback,
        readJson: async (resp) => { try { return await resp.json(); } catch { return null; } },
        console,
        setTimeout,
        clearTimeout,
    };
    sandbox.window.document = document;
    sandbox.window.fetchHandlerSetter = (fn) => { fetchHandler = fn; };

    async function click(el) {
        const listeners = documentListeners.get('click') || [];
        for (const fn of listeners) {
            await fn({
                target: el,
                stopPropagation() {},
                preventDefault() {},
            });
        }
    }

    return { window, document, elements, fetchCalls, sandbox, click };
}

function el(env, id) {
    const created = env.document.createElement('button');
    created.setAttribute('id', id);
    env.elements.set(id, created);
    return created;
}

function runScript(source, sandbox) {
    vm.runInNewContext(source, sandbox, { filename: 'page-actions.js' });
}

test('private-account follow renders the pending state from the relations payload', async () => {
    const env = createEnvironment();
    env.window.fetchHandlerSetter(async (url) => {
        if (url.startsWith('/api/social/relations/')) {
            return {
                ok: true,
                status: 200,
                json: async () => ({ success: true, data: { targetUserId: 7, following: false, followRequestPending: true } }),
            };
        }
        return { ok: true, status: 200, json: async () => ({ success: true, data: null }) };
    });

    const btn = el(env, 'btn-follow');
    btn.setAttribute('data-user-id', '7');
    btn.setAttribute('data-current-user-id', '1');

    runScript(pageActionsSource, env.sandbox);
    await new Promise((r) => setTimeout(r, 30));

    assert.equal(btn.dataset.state, 'ready');
    assert.equal(btn.getAttribute('aria-pressed'), 'false', 'pending is not following');
    assert.match(btn.innerHTML, /person-clock/, 'pending state uses the person-clock icon');
    assert.match(btn.innerHTML, /gửi yêu cầu/, 'pending state label is the request wording');
});

test('a follow PUT on a private account repaints into the pending state via the click flow', async () => {
    const env = createEnvironment();
    let relationCalls = 0;
    env.window.fetchHandlerSetter(async (url, options) => {
        if (url.startsWith('/api/social/relations/')) {
            relationCalls += 1;
            const pending = relationCalls > 1;
            return {
                ok: true,
                status: 200,
                json: async () => ({ success: true, data: { targetUserId: 7, following: false, followRequestPending: pending } }),
            };
        }
        if (options && options.method === 'PUT' && url.startsWith('/api/social/follows/')) {
            return {
                ok: true,
                status: 200,
                json: async () => ({ success: true, data: { targetUserId: 7, active: false, followRequestPending: true } }),
            };
        }
        return { ok: true, status: 200, json: async () => ({ success: true, data: null }) };
    });

    const btn = el(env, 'btn-follow');
    btn.setAttribute('data-user-id', '7');
    btn.setAttribute('data-current-user-id', '1');
    btn.setAttribute('data-action', 'toggle-follow');

    runScript(pageActionsSource, env.sandbox);
    await new Promise((r) => setTimeout(r, 30));
    assert.equal(relationCalls, 1);
    assert.match(btn.innerHTML, /Theo dõi/, 'initial state is the follow button');

    await env.click(btn);
    await new Promise((r) => setTimeout(r, 30));

    assert.equal(relationCalls, 2, 'state is re-resolved after the mutation');
    assert.match(btn.innerHTML, /person-clock/, 'button repaints as pending after the PUT');
});
