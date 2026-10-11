/**
 * AloUTE — Messaging Behavior and Direct Conversation Automated Regression Tests
 *
 * Built-in Node test suite (node:test + assert/strict + vm).
 * Evaluates production IIFEs (messaging.js, page-actions.js)
 * against a lightweight DOM/window/fetch harness.
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

const messagingSource = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/static/js/features/messaging/messaging.js'),
    'utf-8'
);

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
        this.scrollTop = 0;
        this.scrollHeight = 100;
        this.focusCalls = [];
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

    get innerHTML() {
        return this._innerHTML;
    }

    set innerHTML(html) {
        this._innerHTML = String(html);
        this.children = [];
        this.textContent = String(html);
    }

    focus(options) {
        this.focusCalls.push(options || null);
        if (this.ownerDocument) {
            this.ownerDocument.activeElement = this;
        }
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
        event.target = event.target || this;
        event.currentTarget = this;
        const listeners = this._listeners.get(event.type) || [];
        for (const l of listeners) l.call(this, event);
        if (!event._propagationStopped) {
            if (this.parentElement) {
                this.parentElement.dispatchEvent(event);
            } else if (this.ownerDocument) {
                this.ownerDocument.dispatchEvent(event);
            }
        }
        return !event._defaultPrevented;
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
        this.documentElement = new MockElement('HTML', 'html', this);
        this.documentElement.scrollTop = 0;
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
        if (!this._listeners.has(type)) this._listeners.set(type, []);
        this._listeners.get(type).push(fn);
    }

    removeEventListener(type, fn) {
        if (!this._listeners.has(type)) return;
        this._listeners.set(type, this._listeners.get(type).filter(l => l !== fn));
    }

    dispatchEvent(event) {
        const listeners = this._listeners.get(event.type) || [];
        for (const l of listeners) l.call(this, event);
        return !event._defaultPrevented;
    }
}

function createMessagingDom(doc, { initialConvId = null } = {}) {
    const root = doc.createElement('div');
    root.id = 'messaging-content';
    root.setAttribute('data-current-user-id', '1');
    root.setAttribute('data-current-username', 'testuser');
    if (initialConvId != null) {
        root.setAttribute('data-initial-conversation-id', String(initialConvId));
    }
    doc.body.appendChild(root);

    const card = doc.createElement('div');
    card.id = 'messaging-card';
    root.appendChild(card);

    const list = doc.createElement('ul');
    list.id = 'conversations-list';
    card.appendChild(list);

    const loading = doc.createElement('div');
    loading.id = 'conversations-loading';
    loading.classList.add('d-none');
    card.appendChild(loading);

    const convErr = doc.createElement('div');
    convErr.id = 'conversations-error';
    convErr.classList.add('d-none');
    const convErrMsg = doc.createElement('p');
    convErrMsg.id = 'conversations-error-msg';
    convErr.appendChild(convErrMsg);
    card.appendChild(convErr);

    const convEmpty = doc.createElement('div');
    convEmpty.id = 'conversations-empty';
    convEmpty.classList.add('d-none');
    card.appendChild(convEmpty);

    const unselected = doc.createElement('div');
    unselected.id = 'chat-unselected-state';
    card.appendChild(unselected);

    const activeContainer = doc.createElement('div');
    activeContainer.id = 'chat-active-container';
    activeContainer.classList.add('d-none');
    card.appendChild(activeContainer);

    const headerTitle = doc.createElement('h2');
    headerTitle.id = 'chat-header-title';
    activeContainer.appendChild(headerTitle);

    const history = doc.createElement('div');
    history.id = 'chat-history';
    activeContainer.appendChild(history);

    const msgLoading = doc.createElement('div');
    msgLoading.id = 'messages-loading';
    msgLoading.classList.add('d-none');
    activeContainer.appendChild(msgLoading);

    const msgErr = doc.createElement('div');
    msgErr.id = 'messages-error';
    msgErr.classList.add('d-none');
    activeContainer.appendChild(msgErr);

    const msgEmpty = doc.createElement('div');
    msgEmpty.id = 'messages-empty';
    msgEmpty.classList.add('d-none');
    activeContainer.appendChild(msgEmpty);

    const composer = doc.createElement('footer');
    composer.id = 'chat-composer';
    const form = doc.createElement('form');
    form.id = 'message-send-form';
    const textarea = doc.createElement('textarea');
    textarea.id = 'message-input';
    const btnSend = doc.createElement('button');
    btnSend.id = 'btn-send-message';
    form.appendChild(textarea);
    form.appendChild(btnSend);
    composer.appendChild(form);
    activeContainer.appendChild(composer);

    return { root, card, list, textarea, history };
}

describe('Messaging Frontend & Behavior Regression Tests', () => {

    describe('Startup and DOM initialization', () => {

        it('initializes on /messages without ReferenceError and loads conversations list', async () => {
            const doc = new MockDocument();
            createMessagingDom(doc);

            const fetchCalls = [];
            const mockFetch = async (url) => {
                fetchCalls.push(url);
                if (url.includes('/api/conversations?')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [
                                { id: 10, name: 'Hội thoại 10', otherParticipant: { username: 'user10' }, lastMessage: null }
                            ],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                return { ok: true, status: 200, text: async () => '{}' };
            };

            const sandbox = {
                document: doc,
                window: {
                    location: { pathname: '/messages', search: '' },
                    innerWidth: 1024,
                    scrollY: 0
                },
                fetch: mockFetch,
                URLSearchParams: globalThis.URLSearchParams,
                AbortController: globalThis.AbortController,
                console: console
            };

            const context = vm.createContext(sandbox);
            // Must not throw ReferenceError: elWrapper is not defined
            assert.doesNotThrow(() => {
                vm.runInContext(messagingSource, context);
            });

            // Allow microtasks to complete
            await new Promise(r => setTimeout(r, 20));

            // Conversations fetch must have fired
            assert.ok(fetchCalls.some(u => u.includes('/api/conversations')), 'Expected conversations API call');
            // Outer scroll must remain at 0
            assert.equal(sandbox.window.scrollY, 0, 'Outer scroll position must remain 0 on startup');
            // messageInput must NOT be auto-focused on startup
            assert.notEqual(doc.activeElement && doc.activeElement.id, 'message-input', 'message-input must not be autofocus on startup');
        });

        it('initializes on /messages/42 and selects conversation 42 from path', async () => {
            const doc = new MockDocument();
            createMessagingDom(doc);

            const fetchCalls = [];
            const mockFetch = async (url) => {
                fetchCalls.push(url);
                if (url.includes('/api/conversations/42/messages')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [
                                { id: 101, content: 'Chào bạn', createdAt: '2026-10-11T10:00:00Z', sender: { id: 2 } }
                            ],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                if (url.includes('/api/conversations/42')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: { id: 42, name: 'Nhóm 42' }
                        })
                    };
                }
                if (url.includes('/api/conversations?')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [{ id: 42, name: 'Nhóm 42' }],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                return { ok: true, status: 200, text: async () => '{}' };
            };

            const sandbox = {
                document: doc,
                window: {
                    location: { pathname: '/messages/42', search: '' },
                    innerWidth: 1024,
                    scrollY: 0
                },
                fetch: mockFetch,
                URLSearchParams: globalThis.URLSearchParams,
                AbortController: globalThis.AbortController,
                console: console
            };

            const context = vm.createContext(sandbox);
            assert.doesNotThrow(() => {
                vm.runInContext(messagingSource, context);
            });

            await new Promise(r => setTimeout(r, 20));

            // Messages for conversation 42 must have been requested
            assert.ok(fetchCalls.some(u => u.includes('/api/conversations/42/messages')), 'Expected messages fetch for 42');
            // Inner history scroll updated
            const history = doc.getElementById('chat-history');
            assert.equal(history.scrollTop, history.scrollHeight, 'Inner history should scroll to newest message');
            // Outer window scroll unaffected
            assert.equal(sandbox.window.scrollY, 0, 'Outer scroll position must remain 0');
        });

        it('initializes from data-initial-conversation-id attribute on wrapper', async () => {
            const doc = new MockDocument();
            createMessagingDom(doc, { initialConvId: 88 });

            const fetchCalls = [];
            const mockFetch = async (url) => {
                fetchCalls.push(url);
                return {
                    ok: true,
                    status: 200,
                    text: async () => JSON.stringify({
                        success: true,
                        data: [],
                        meta: { page: 0, hasNext: false }
                    })
                };
            };

            const sandbox = {
                document: doc,
                window: {
                    location: { pathname: '/messages', search: '' },
                    innerWidth: 1024,
                    scrollY: 0
                },
                fetch: mockFetch,
                URLSearchParams: globalThis.URLSearchParams,
                AbortController: globalThis.AbortController,
                console: console
            };

            const context = vm.createContext(sandbox);
            assert.doesNotThrow(() => {
                vm.runInContext(messagingSource, context);
            });

            await new Promise(r => setTimeout(r, 20));
            assert.ok(fetchCalls.some(u => u.includes('/api/conversations/88/messages')), 'Expected messages fetch for 88');
        });
    });

    describe('Direct Conversation Creation in page-actions.js', () => {

        function createPageActionsEnv({ fetchHandler, targetDataset = { targetUserId: '5' } }) {
            const doc = new MockDocument();
            let redirectedTo = null;
            const notices = [];

            const btn = doc.createElement('button');
            btn.setAttribute('data-action', 'create-direct-conversation');
            btn.setAttribute('data-target-user-id', targetDataset.targetUserId || '5');
            btn.innerHTML = '<span>Nhắn tin</span>';
            doc.body.appendChild(btn);

            const sandbox = {
                document: doc,
                window: {
                    location: {
                        get href() { return redirectedTo || '/profile/5'; },
                        set href(v) { redirectedTo = v; }
                    },
                    console: console
                },
                fetch: fetchHandler,
                URLSearchParams: globalThis.URLSearchParams,
                encodeURIComponent: globalThis.encodeURIComponent,
                Number: globalThis.Number,
                setTimeout: globalThis.setTimeout,
                clearTimeout: globalThis.clearTimeout
            };

            const context = vm.createContext(sandbox);
            vm.runInContext(pageActionsSource, context);

            return {
                doc,
                btn,
                getRedirectedTo: () => redirectedTo,
                click: () => {
                    btn.dispatchEvent({
                        type: 'click',
                        target: btn,
                        preventDefault: () => {},
                        stopPropagation: () => {}
                    });
                }
            };
        }

        it('navigates to /messages/15 on valid positive integer ID', async () => {
            const env = createPageActionsEnv({
                fetchHandler: async () => ({
                    ok: true,
                    status: 200,
                    text: async () => JSON.stringify({
                        success: true,
                        data: { id: 15, type: 'DIRECT' }
                    })
                })
            });

            env.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.getRedirectedTo(), '/messages/15');
        });

        it('navigates to /messages/42 on valid string integer ID', async () => {
            const env = createPageActionsEnv({
                fetchHandler: async () => ({
                    ok: true,
                    status: 201,
                    text: async () => JSON.stringify({
                        success: true,
                        data: { id: '42', type: 'DIRECT' }
                    })
                })
            });

            env.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.getRedirectedTo(), '/messages/42');
        });

        it('rejects boolean id: true without navigation and restores button', async () => {
            const env = createPageActionsEnv({
                fetchHandler: async () => ({
                    ok: true,
                    status: 200,
                    text: async () => JSON.stringify({
                        success: true,
                        data: { id: true }
                    })
                })
            });

            env.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.getRedirectedTo(), null, 'Must NOT navigate on boolean true id');
            assert.equal(env.btn.hasAttribute('disabled'), false, 'Button must be re-enabled for retry');
            assert.ok(env.btn.innerHTML.includes('Nhắn tin'), 'Button HTML must be restored');
        });

        it('rejects array id: [12] without navigation and restores button', async () => {
            const env = createPageActionsEnv({
                fetchHandler: async () => ({
                    ok: true,
                    status: 200,
                    text: async () => JSON.stringify({
                        success: true,
                        data: { id: [12] }
                    })
                })
            });

            env.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.getRedirectedTo(), null, 'Must NOT navigate on array [12] id');
            assert.equal(env.btn.hasAttribute('disabled'), false, 'Button must be re-enabled for retry');
        });

        it('rejects scientific notation "1e3" without navigation and restores button', async () => {
            const env = createPageActionsEnv({
                fetchHandler: async () => ({
                    ok: true,
                    status: 200,
                    text: async () => JSON.stringify({
                        success: true,
                        data: { id: '1e3' }
                    })
                })
            });

            env.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.getRedirectedTo(), null, 'Must NOT navigate on "1e3" id');
            assert.equal(env.btn.hasAttribute('disabled'), false, 'Button must be re-enabled for retry');
        });

        it('rejects whitespace string " 12 " without navigation and restores button', async () => {
            const env = createPageActionsEnv({
                fetchHandler: async () => ({
                    ok: true,
                    status: 200,
                    text: async () => JSON.stringify({
                        success: true,
                        data: { id: ' 12 ' }
                    })
                })
            });

            env.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.getRedirectedTo(), null, 'Must NOT navigate on " 12 " id');
            assert.equal(env.btn.hasAttribute('disabled'), false, 'Button must be re-enabled for retry');
        });

        it('rejects negative id and zero without navigation', async () => {
            for (const badId of [0, -5, '0', '-10']) {
                const env = createPageActionsEnv({
                    fetchHandler: async () => ({
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: { id: badId }
                        })
                    })
                });

                env.click();
                await new Promise(r => setTimeout(r, 10));

                assert.equal(env.getRedirectedTo(), null, `Must NOT navigate on id=${badId}`);
                assert.equal(env.btn.hasAttribute('disabled'), false);
            }
        });

        it('handles HTTP error 404/500 without navigation and restores button for retry', async () => {
            const env = createPageActionsEnv({
                fetchHandler: async () => ({
                    ok: false,
                    status: 404,
                    text: async () => JSON.stringify({
                        success: false,
                        error: { code: 'USER_NOT_FOUND', message: 'User not found' }
                    })
                })
            });

            env.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.getRedirectedTo(), null, 'Must not navigate on HTTP 404');
            assert.equal(env.btn.hasAttribute('disabled'), false, 'Button must be restored for retry');
        });

        it('handles network failure (rejected fetch) without navigation and restores button for retry', async () => {
            const env = createPageActionsEnv({
                fetchHandler: async () => {
                    throw new Error('Network error');
                }
            });

            env.click();
            await new Promise(r => setTimeout(r, 10));

            assert.equal(env.getRedirectedTo(), null, 'Must not navigate on network failure');
            assert.equal(env.btn.hasAttribute('disabled'), false, 'Button must be restored for retry');
        });
    });
});
