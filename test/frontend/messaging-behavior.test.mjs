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

    click() {
        this.dispatchEvent({
            type: 'click',
            target: this,
            preventDefault: () => {},
            stopPropagation: () => {}
        });
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

        it('does not auto-select the first conversation on the /messages list page (no premature read receipt)', async () => {
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
                                { id: 10, name: 'Hội thoại 10', unreadCount: 3, otherParticipant: { username: 'user10' }, lastMessage: null }
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
            vm.runInContext(messagingSource, context);

            await new Promise(r => setTimeout(r, 20));

            // Conversations list loaded
            assert.ok(fetchCalls.some(u => u.includes('/api/conversations?')), 'Expected conversations list fetch');
            // No auto-select: neither message history nor read receipt may fire
            assert.ok(!fetchCalls.some(u => /\/api\/conversations\/\d+\/messages/.test(u)), 'Must not fetch messages without explicit selection');
            assert.ok(!fetchCalls.some(u => /\/read/.test(u)), 'Must not send read receipt without explicit selection');
            // Unselected placeholder remains visible (classList 'd-none' NOT added)
            const unselected = doc.getElementById('chat-unselected-state');
            assert.ok(unselected && !unselected.classList.contains('d-none'), 'Unselected state must stay visible until the user picks a conversation');
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

    describe('STOMP Realtime & Catch-Up Behavior', () => {

        class FakeWebSocket {
            static OPEN = 1;
            static CLOSED = 3;
            constructor(url) {
                this.url = url;
                this.readyState = 1;
                this.sentFrames = [];
                FakeWebSocket.instances.push(this);
                setTimeout(() => {
                    if (this.onopen) this.onopen();
                }, 5);
            }
            send(frame) {
                this.sentFrames.push(frame);
            }
            close() {
                this.readyState = 3;
                if (this.onclose) this.onclose();
            }
            receive(data) {
                if (this.onmessage) this.onmessage({ data });
            }
        }

        function hasMessageInHistory(doc, text) {
            const historyEl = doc.getElementById('chat-history');
            if (!historyEl) return false;
            if (historyEl.innerHTML && historyEl.innerHTML.includes(text)) return true;
            return historyEl.children.some(c => (c.innerHTML && c.innerHTML.includes(text)) || (c.textContent && c.textContent.includes(text)));
        }

        function createStompSandbox(doc, mockFetch) {
            FakeWebSocket.instances = [];
            const listeners = new Map();
            const win = {
                location: { pathname: '/messages/42', search: '', protocol: 'http:', host: 'localhost:8080' },
                innerWidth: 1024,
                scrollY: 0,
                WebSocket: FakeWebSocket,
                addEventListener: (t, fn) => {
                    if (!listeners.has(t)) listeners.set(t, []);
                    listeners.get(t).push(fn);
                },
                removeEventListener: (t, fn) => {
                    if (!listeners.has(t)) return;
                    listeners.set(t, listeners.get(t).filter(l => l !== fn));
                },
                dispatchEvent: (e) => {
                    const fns = listeners.get(e.type) || [];
                    fns.forEach(fn => fn(e));
                },
                setTimeout: globalThis.setTimeout,
                clearTimeout: globalThis.clearTimeout
            };

            const sandbox = {
                document: doc,
                window: win,
                WebSocket: FakeWebSocket,
                fetch: mockFetch,
                URLSearchParams: globalThis.URLSearchParams,
                AbortController: globalThis.AbortController,
                setTimeout: globalThis.setTimeout,
                clearTimeout: globalThis.clearTimeout,
                console: console
            };

            return { sandbox, win };
        }

        it('connects STOMP with 0,0 heartbeats and subscribes to /user/queue/messages', async () => {
            const doc = new MockDocument();
            createMessagingDom(doc);

            const mockFetch = async (url) => {
                if (url.includes('/api/conversations/42/messages')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [],
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
                            data: { id: 42, name: 'Hội thoại 42' }
                        })
                    };
                }
                if (url.includes('/api/conversations?')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [{ id: 42, name: 'Hội thoại 42', lastMessage: null }],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                return { ok: true, status: 200, text: async () => '{"success":true}' };
            };

            const { sandbox } = createStompSandbox(doc, mockFetch);
            const context = vm.createContext(sandbox);
            vm.runInContext(messagingSource, context);

            await new Promise(r => setTimeout(r, 20));

            assert.equal(FakeWebSocket.instances.length, 1, 'FakeWebSocket should have 1 connection');
            const ws = FakeWebSocket.instances[0];

            // Verify CONNECT frame advertises heart-beat:0,0
            assert.ok(ws.sentFrames.some(f => f.includes('CONNECT') && f.includes('heart-beat:0,0')), 'CONNECT frame must advertise heart-beat:0,0');

            // Server responds with CONNECTED
            ws.receive('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
            await new Promise(r => setTimeout(r, 10));

            // Verify client subscribed to /user/queue/messages
            assert.ok(ws.sentFrames.some(f => f.includes('SUBSCRIBE') && f.includes('/user/queue/messages')), 'Client must subscribe to /user/queue/messages');
        });

        it('handles leading heartbeats and fragmented frames in STOMP message delivery', async () => {
            const doc = new MockDocument();
            createMessagingDom(doc);

            const mockFetch = async (url) => {
                if (url.includes('/api/conversations/42/messages')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [],
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
                            data: { id: 42, name: 'Hội thoại 42' }
                        })
                    };
                }
                if (url.includes('/api/conversations?')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [{ id: 42, name: 'Hội thoại 42', lastMessage: null }],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                return { ok: true, status: 200, text: async () => '{"success":true}' };
            };

            const { sandbox } = createStompSandbox(doc, mockFetch);
            const context = vm.createContext(sandbox);
            vm.runInContext(messagingSource, context);

            await new Promise(r => setTimeout(r, 20));
            const ws = FakeWebSocket.instances[0];
            ws.receive('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
            await new Promise(r => setTimeout(r, 10));

            // Send leading newlines (heartbeats) + fragmented frame across two chunks
            const chunk1 = '\n\nMESSAGE\ndestination:/user/queue/messages\n\n{"id":888,"conversationId":42,"content":"Chunked payload",';
            const chunk2 = '"createdAt":"2026-10-20T05:00:00Z","sender":{"id":2,"username":"bob"}}\0';

            ws.receive(chunk1);
            // Before chunk 2, message should NOT yet be rendered
            assert.ok(!hasMessageInHistory(doc, 'Chunked payload'), 'Message must not render while frame is fragmented');

            ws.receive(chunk2);
            await new Promise(r => setTimeout(r, 10));

            // After chunk 2, message must be rendered
            assert.ok(hasMessageInHistory(doc, 'Chunked payload'), 'Message must render after complete frame is received');
        });

        it('reconnect catch-up merges in-flight messages without overwriting', async () => {
            const doc = new MockDocument();
            createMessagingDom(doc);

            let fetchCounter = 0;
            const mockFetch = async (url) => {
                if (url.includes('/api/conversations/42/messages')) {
                    fetchCounter++;
                    if (fetchCounter === 1) {
                        return {
                            ok: true,
                            status: 200,
                            text: async () => JSON.stringify({
                                success: true,
                                data: [{ id: 100, content: 'Initial message', createdAt: '2026-10-20T04:00:00Z', sender: { id: 1 } }],
                                meta: { page: 0, hasNext: false }
                            })
                        };
                    } else {
                        // Reconnect catch-up fetch returns server's persisted list (missing the very latest in-flight message)
                        return {
                            ok: true,
                            status: 200,
                            text: async () => JSON.stringify({
                                success: true,
                                data: [
                                    { id: 101, content: 'Persisted msg 101', createdAt: '2026-10-20T04:30:00Z', sender: { id: 2 } },
                                    { id: 100, content: 'Initial message', createdAt: '2026-10-20T04:00:00Z', sender: { id: 1 } }
                                ],
                                meta: { page: 0, hasNext: false }
                            })
                        };
                    }
                }
                if (url.includes('/api/conversations/42')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: { id: 42, name: 'Hội thoại 42' }
                        })
                    };
                }
                if (url.includes('/api/conversations?')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [{ id: 42, name: 'Hội thoại 42', lastMessage: null }],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                return { ok: true, status: 200, text: async () => '{"success":true}' };
            };

            const { sandbox } = createStompSandbox(doc, mockFetch);
            const context = vm.createContext(sandbox);
            vm.runInContext(messagingSource, context);

            await new Promise(r => setTimeout(r, 20));
            const ws = FakeWebSocket.instances[0];
            ws.receive('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
            await new Promise(r => setTimeout(r, 10));

            // Deliver in-flight real-time message 102
            ws.receive('MESSAGE\ndestination:/user/queue/messages\n\n{"id":102,"conversationId":42,"content":"In-flight realtime msg 102","createdAt":"2026-10-20T04:45:00Z","sender":{"id":2,"username":"bob"}}\0');
            await new Promise(r => setTimeout(r, 10));

            assert.ok(hasMessageInHistory(doc, 'In-flight realtime msg 102'), 'In-flight message must be visible');

            // Trigger re-connect CONNECTED event which initiates REST catch-up
            ws.receive('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
            await new Promise(r => setTimeout(r, 25));

            // Verify both the REST catch-up msg (101) and the in-flight realtime msg (102) are present
            assert.ok(hasMessageInHistory(doc, 'Persisted msg 101'), 'REST catchup message 101 must be rendered');
            assert.ok(hasMessageInHistory(doc, 'In-flight realtime msg 102'), 'In-flight message 102 must NOT be overwritten by catchup');
        });

        it('cleans up socket and cancels timers on pagehide', async () => {
            const doc = new MockDocument();
            createMessagingDom(doc);

            const mockFetch = async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({ success: true, data: [], meta: { page: 0, hasNext: false } })
            });

            const { sandbox, win } = createStompSandbox(doc, mockFetch);
            const context = vm.createContext(sandbox);
            vm.runInContext(messagingSource, context);

            await new Promise(r => setTimeout(r, 20));
            const ws = FakeWebSocket.instances[0];
            assert.equal(ws.readyState, 1, 'Socket should be open initially');

            // Trigger pagehide event
            win.dispatchEvent({ type: 'pagehide' });

            assert.equal(ws.readyState, 3, 'Socket must be closed after pagehide');
        });

        it('sends POST read receipt when incoming STOMP message arrives in active conversation', async () => {
            const doc = new MockDocument();
            createMessagingDom(doc);

            let readReceiptCalled = false;
            let readReceiptUrl = '';

            const mockFetch = async (url, opts) => {
                if (url.includes('/read') && opts && opts.method === 'POST') {
                    readReceiptCalled = true;
                    readReceiptUrl = url;
                    return { ok: true, status: 200, text: async () => '{"success":true}' };
                }
                if (url.includes('/api/conversations/42/messages')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: [], meta: { page: 0, hasNext: false } })
                    };
                }
                if (url.includes('/api/conversations/42')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: { id: 42, name: 'Hội thoại 42' } })
                    };
                }
                if (url.includes('/api/conversations?')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [{ id: 42, name: 'Hội thoại 42', lastMessage: null }],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                return { ok: true, status: 200, text: async () => '{"success":true}' };
            };

            const { sandbox } = createStompSandbox(doc, mockFetch);
            const context = vm.createContext(sandbox);
            vm.runInContext(messagingSource, context);

            await new Promise(r => setTimeout(r, 20));
            const ws = FakeWebSocket.instances[0];
            ws.receive('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
            await new Promise(r => setTimeout(r, 10));

            // Deliver incoming message to active conversation 42
            ws.receive('MESSAGE\ndestination:/user/queue/messages\n\n{"id":777,"conversationId":42,"content":"Hello active read receipt","createdAt":"2026-10-20T05:00:00Z","sender":{"id":2,"username":"bob"}}\0');
            await new Promise(r => setTimeout(r, 20));

            assert.ok(readReceiptCalled, 'Read receipt POST must be dispatched for active incoming message');
            assert.ok(readReceiptUrl.includes('/api/conversations/42/read'), 'Read receipt must target active conversation');
        });

        it('deduplicates duplicate STOMP events and does not increment unread twice', async () => {
            const doc = new MockDocument();
            createMessagingDom(doc);

            const mockFetch = async (url) => {
                if (url.includes('/api/conversations?')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [
                                { id: 42, name: 'Hội thoại 42', lastMessage: null },
                                { id: 99, name: 'Hội thoại 99 (Inactive)', lastMessage: null, unreadCount: 0 }
                            ],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                return { ok: true, status: 200, text: async () => '{"success":true}' };
            };

            const { sandbox } = createStompSandbox(doc, mockFetch);
            const context = vm.createContext(sandbox);
            vm.runInContext(messagingSource, context);

            await new Promise(r => setTimeout(r, 20));
            const ws = FakeWebSocket.instances[0];
            ws.receive('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
            await new Promise(r => setTimeout(r, 10));

            // Deliver message 888 for inactive conversation 99
            ws.receive('MESSAGE\ndestination:/user/queue/messages\n\n{"id":888,"conversationId":99,"content":"First delivery","createdAt":"2026-10-20T05:01:00Z","sender":{"id":3,"username":"eve"}}\0');
            await new Promise(r => setTimeout(r, 10));

            // Deliver DUPLICATE of message 888
            ws.receive('MESSAGE\ndestination:/user/queue/messages\n\n{"id":888,"conversationId":99,"content":"First delivery","createdAt":"2026-10-20T05:01:00Z","sender":{"id":3,"username":"eve"}}\0');
            await new Promise(r => setTimeout(r, 10));

            // Find conversation 99 in rendered list
            const convList = doc.getElementById('conversations-list');
            assert.ok(convList, 'conversations-list must exist');
            const item99 = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '99');
            assert.ok(item99, 'Conversation 99 item must be rendered');
            // Unread badge in innerHTML should show 1, NOT 2
            assert.ok(item99.innerHTML.includes('badge'), 'Unread badge must be rendered');
            assert.ok(item99.innerHTML.includes('>1</span>'), 'Unread badge must show count 1');
            assert.ok(!item99.innerHTML.includes('>2</span>'), 'Duplicate event must NOT increment unreadCount twice');
        });

        it('reconnects and restores STOMP on pageshow when persisted from bfcache', async () => {
            const doc = new MockDocument();
            createMessagingDom(doc);

            const mockFetch = async () => ({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({ success: true, data: [], meta: { page: 0, hasNext: false } })
            });

            const { sandbox, win } = createStompSandbox(doc, mockFetch);
            const context = vm.createContext(sandbox);
            vm.runInContext(messagingSource, context);

            await new Promise(r => setTimeout(r, 20));
            const initialCount = FakeWebSocket.instances.length;
            assert.equal(initialCount, 1, 'Initial WebSocket instance created');

            // Page navigates away into bfcache (pagehide)
            win.dispatchEvent({ type: 'pagehide' });
            assert.equal(FakeWebSocket.instances[0].readyState, 3, 'Old socket closed');

            // Page restored from bfcache (pageshow with persisted: true)
            win.dispatchEvent({ type: 'pageshow', persisted: true });
            await new Promise(r => setTimeout(r, 20));

            assert.equal(FakeWebSocket.instances.length, 2, 'New WebSocket created on pageshow restore');
            assert.equal(FakeWebSocket.instances[1].readyState, 1, 'New WebSocket is open');
        });

        it('does not increment inactive unread count if HTTP summary already reflects incoming message ID', async () => {
            const doc = new MockDocument();
            createMessagingDom(doc);

            const mockFetch = async (url) => {
                if (url.includes('/api/conversations?')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [
                                { id: 42, name: 'Hội thoại 42', lastMessage: null },
                                {
                                    id: 99,
                                    name: 'Hội thoại 99 (Inactive)',
                                    lastMessage: { id: 900, content: 'Already in HTTP summary', createdAt: '2026-10-20T05:00:00Z' },
                                    unreadCount: 1
                                }
                            ],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                return { ok: true, status: 200, text: async () => '{"success":true}' };
            };

            const { sandbox } = createStompSandbox(doc, mockFetch);
            const context = vm.createContext(sandbox);
            vm.runInContext(messagingSource, context);

            await new Promise(r => setTimeout(r, 20));
            const ws = FakeWebSocket.instances[0];
            ws.receive('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
            await new Promise(r => setTimeout(r, 10));

            // Deliver message 900 which was ALREADY in HTTP summary
            ws.receive('MESSAGE\ndestination:/user/queue/messages\n\n{"id":900,"conversationId":99,"content":"Already in HTTP summary","createdAt":"2026-10-20T05:00:00Z","sender":{"id":3,"username":"eve"}}\0');
            await new Promise(r => setTimeout(r, 10));

            const convList = doc.getElementById('conversations-list');
            const item99 = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '99');
            assert.ok(item99, 'Conversation 99 item must be rendered');
            assert.ok(item99.innerHTML.includes('>1</span>'), 'Unread count must stay 1, not incremented to 2');
            assert.ok(!item99.innerHTML.includes('>2</span>'), 'Must not double count already-reflected message');
        });

        it('dispatches active read receipt even if incoming message is already present in message history', async () => {
            const doc = new MockDocument();
            createMessagingDom(doc, { initialConvId: 42 });

            let readReceiptCalled = false;
            let readReceiptUrl = '';

            const mockFetch = async (url) => {
                if (url.includes('/read')) {
                    readReceiptCalled = true;
                    readReceiptUrl = url;
                    return { ok: true, status: 200, text: async () => '{"success":true}' };
                }
                if (url.includes('/api/conversations/42/messages')) {
                    // Pre-populate message history with message 778
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [{ id: 778, conversationId: 42, content: 'Already loaded in history', createdAt: '2026-10-20T04:59:00Z', sender: { id: 2, username: 'bob' } }],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                if (url.includes('/api/conversations/42')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: { id: 42, name: 'Hội thoại 42' } })
                    };
                }
                if (url.includes('/api/conversations?')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [{ id: 42, name: 'Hội thoại 42', lastMessage: null }],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                return { ok: true, status: 200, text: async () => '{"success":true}' };
            };

            const { sandbox } = createStompSandbox(doc, mockFetch);
            const context = vm.createContext(sandbox);
            vm.runInContext(messagingSource, context);

            await new Promise(r => setTimeout(r, 25));
            const ws = FakeWebSocket.instances[0];
            ws.receive('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
            await new Promise(r => setTimeout(r, 10));

            // Deliver message 778 which is already in history
            ws.receive('MESSAGE\ndestination:/user/queue/messages\n\n{"id":778,"conversationId":42,"content":"Already loaded in history","createdAt":"2026-10-20T04:59:00Z","sender":{"id":2,"username":"bob"}}\0');
            await new Promise(r => setTimeout(r, 20));

            assert.ok(readReceiptCalled, 'Read receipt must be dispatched even if message already in history');
            assert.ok(readReceiptUrl.includes('/api/conversations/42/read'), 'Read receipt targets conversation 42');

            // History should NOT have duplicate bubbles for message 778
            const history = doc.getElementById('chat-history');
            const bubbles = history.children.filter(c => c.innerHTML && c.innerHTML.includes('Already loaded in history'));
            assert.equal(bubbles.length, 1, 'Must not render duplicate message bubble');
        });

        it('D1: summary refresh carrying lastMessage ID does not prevent first live STOMP event from appending bubble and dispatching read receipt', async () => {
            const doc = new MockDocument();
            createMessagingDom(doc, { initialConvId: 10 });

            let readReceiptCalledCount = 0;
            let convCallCount = 0;

            const mockFetch = async (url) => {
                if (url.includes('/read')) {
                    readReceiptCalledCount++;
                    return { ok: true, status: 200, text: async () => '{"success":true}' };
                }
                if (url.includes('/api/conversations/10/messages')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: [], meta: { page: 0, hasNext: false } })
                    };
                }
                if (url.includes('/api/conversations/10')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: { id: 10, name: 'Hội thoại 10' } })
                    };
                }
                if (url.includes('/api/conversations?')) {
                    convCallCount++;
                    const lastId = convCallCount > 1 ? 500 : 400;
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [{
                                id: 10,
                                name: 'Hội thoại 10',
                                lastMessage: { id: lastId, content: 'msg-' + lastId, createdAt: '2026-10-20T06:05:00Z' }
                            }],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                return { ok: true, status: 200, text: async () => '{"success":true}' };
            };

            const { sandbox } = createStompSandbox(doc, mockFetch);
            sandbox.window.location = { pathname: '/messages/10', search: '', protocol: 'http:', host: 'localhost:8080' };
            const context = vm.createContext(sandbox);
            vm.runInContext(messagingSource, context);

            await new Promise(r => setTimeout(r, 25));
            const ws = FakeWebSocket.instances[0];
            ws.receive('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
            await new Promise(r => setTimeout(r, 25));

            const historyEl = doc.getElementById('chat-history');
            const initialBubbles = historyEl ? historyEl.children.length : 0;
            const initialReceipts = readReceiptCalledCount;

            // Deliver live message 500 (same ID as refreshed summary lastMessage)
            ws.receive('MESSAGE\ndestination:/user/queue/messages\n\n{"id":500,"conversationId":10,"content":"msg-500","createdAt":"2026-10-20T06:05:00Z","sender":{"id":2,"username":"bob"}}\0');
            await new Promise(r => setTimeout(r, 25));

            const finalBubbles = historyEl ? historyEl.children.length : 0;
            const receiptDelta = readReceiptCalledCount - initialReceipts;

            assert.equal(finalBubbles, initialBubbles + 1, 'Message bubble must be appended even if summary carried this ID');
            assert.ok(receiptDelta >= 1, 'Read receipt must be dispatched for live message arrival');
        });

        it('D2: older in-flight summary response landing after newer STOMP event preserves newer preview and unread count', async () => {
            const doc = new MockDocument();
            createMessagingDom(doc);

            let resolveSummary;
            const summaryPromise = new Promise(res => { resolveSummary = res; });
            let convCall = 0;

            const initialSummary = {
                success: true,
                data: [
                    { id: 10, name: 'Hội thoại 10', lastMessage: { id: 400, content: 'msg-400', createdAt: '2026-10-20T06:00:00Z' }, unreadCount: 0 },
                    { id: 11, name: 'Hội thoại 11', lastMessage: { id: 401, content: 'msg-401', createdAt: '2026-10-20T06:00:00Z' }, unreadCount: 0 }
                ],
                meta: { page: 0, hasNext: false }
            };

            const mockFetch = async (url) => {
                if (url.includes('/api/conversations?')) {
                    convCall++;
                    if (convCall === 1) {
                        return { ok: true, status: 200, text: async () => JSON.stringify(initialSummary) };
                    } else {
                        // In-flight refresh: hold until released
                        return summaryPromise;
                    }
                }
                return { ok: true, status: 200, text: async () => '{"success":true}' };
            };

            const { sandbox } = createStompSandbox(doc, mockFetch);
            const context = vm.createContext(sandbox);
            vm.runInContext(messagingSource, context);

            await new Promise(r => setTimeout(r, 20));
            const ws = FakeWebSocket.instances[0];
            ws.receive('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
            await new Promise(r => setTimeout(r, 10));

            // Deliver newer message 450 for inactive conv 11 while refresh is in flight
            ws.receive('MESSAGE\ndestination:/user/queue/messages\n\n{"id":450,"conversationId":11,"content":"msg-450","createdAt":"2026-10-20T06:05:00Z","sender":{"id":3,"username":"eve"}}\0');
            await new Promise(r => setTimeout(r, 20));

            const convList = doc.getElementById('conversations-list');
            const item11Before = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '11');
            assert.ok(item11Before.innerHTML.includes('>1</span>'), 'Badge must be 1 after live event');

            // Release in-flight summary (which has older state: unreadCount=0, lm=401)
            resolveSummary({ ok: true, status: 200, text: async () => JSON.stringify(initialSummary) });
            await new Promise(r => setTimeout(r, 25));

            const item11After = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '11');
            assert.ok(item11After.innerHTML.includes('>1</span>'), 'Badge must remain 1; stale in-flight summary must not wipe unread to 0');
            assert.ok(item11After.innerHTML.includes('msg-450'), 'Preview must preserve newer message 450');
        });

        it('D3: late or out-of-order STOMP event older than current summary lastMessage does not double-count unread and does not regress preview', async () => {
            const doc = new MockDocument();
            createMessagingDom(doc);

            const mockFetch = async (url) => {
                if (url.includes('/api/conversations?')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [
                                { id: 10, name: 'Hội thoại 10', lastMessage: null },
                                {
                                    id: 11,
                                    name: 'Hội thoại 11',
                                    lastMessage: { id: 500, content: 'msg-500', createdAt: '2026-10-20T06:05:00Z' },
                                    unreadCount: 1
                                }
                            ],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                return { ok: true, status: 200, text: async () => '{"success":true}' };
            };

            const { sandbox } = createStompSandbox(doc, mockFetch);
            const context = vm.createContext(sandbox);
            vm.runInContext(messagingSource, context);

            await new Promise(r => setTimeout(r, 20));
            const ws = FakeWebSocket.instances[0];
            ws.receive('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
            await new Promise(r => setTimeout(r, 10));

            // Deliver older message 450 (createdAt 06:00:00 vs summary 06:05:00)
            ws.receive('MESSAGE\ndestination:/user/queue/messages\n\n{"id":450,"conversationId":11,"content":"msg-450","createdAt":"2026-10-20T06:00:00Z","sender":{"id":3,"username":"eve"}}\0');
            await new Promise(r => setTimeout(r, 20));

            const convList = doc.getElementById('conversations-list');
            let item11 = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '11');
            assert.ok(item11.innerHTML.includes('>1</span>'), 'Unread count must stay 1, not incremented to 2 for older late message');
            assert.ok(!item11.innerHTML.includes('>2</span>'), 'Must not double count older late message');
            assert.ok(item11.innerHTML.includes('msg-500'), 'Preview must preserve newer message 500');
            assert.ok(!item11.innerHTML.includes('msg-450'), 'Preview must not regress to older message 450');
        });

        it('D3b: adverse order with same timestamps uses message ID ordering', async () => {
            const doc = new MockDocument();
            createMessagingDom(doc);

            const mockFetch = async (url) => {
                if (url.includes('/api/conversations?')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [
                                {
                                    id: 11,
                                    name: 'Hội thoại 11',
                                    lastMessage: { id: 500, content: 'msg-500', createdAt: '2026-10-20T06:00:00Z' },
                                    unreadCount: 1
                                }
                            ],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                return { ok: true, status: 200, text: async () => '{"success":true}' };
            };

            const { sandbox } = createStompSandbox(doc, mockFetch);
            const context = vm.createContext(sandbox);
            vm.runInContext(messagingSource, context);

            await new Promise(r => setTimeout(r, 20));
            const ws = FakeWebSocket.instances[0];
            ws.receive('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
            await new Promise(r => setTimeout(r, 10));

            // Deliver message 450 with IDENTICAL timestamp as 500. Since 450 < 500, 450 is older.
            ws.receive('MESSAGE\ndestination:/user/queue/messages\n\n{"id":450,"conversationId":11,"content":"msg-450","createdAt":"2026-10-20T06:00:00Z","sender":{"id":3,"username":"eve"}}\0');
            await new Promise(r => setTimeout(r, 20));

            const convList = doc.getElementById('conversations-list');
            let item11 = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '11');
            assert.ok(item11.innerHTML.includes('>1</span>'), 'Unread count must stay 1 for same-timestamp lower-ID event');
            assert.ok(item11.innerHTML.includes('msg-500'), 'Preview must preserve higher-ID message 500');
        });

        it('D1b: active message older than conversation summary still renders and dispatches read receipt', async () => {
            const doc = new MockDocument();
            createMessagingDom(doc, { initialConvId: 10 });

            let readReceiptCount = 0;

            const mockFetch = async (url) => {
                if (url.includes('/read')) {
                    readReceiptCount++;
                    return { ok: true, status: 200, text: async () => '{"success":true}' };
                }
                if (url.includes('/api/conversations/10/messages')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({ success: true, data: [], meta: { page: 0, hasNext: false } })
                    };
                }
                if (url.includes('/api/conversations/10')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: {
                                id: 10,
                                name: 'Hội thoại 10',
                                lastMessage: { id: 500, content: 'msg-500', createdAt: '2026-10-20T06:05:00Z' }
                            }
                        })
                    };
                }
                if (url.includes('/api/conversations?')) {
                    return {
                        ok: true,
                        status: 200,
                        text: async () => JSON.stringify({
                            success: true,
                            data: [{
                                id: 10,
                                name: 'Hội thoại 10',
                                lastMessage: { id: 500, content: 'msg-500', createdAt: '2026-10-20T06:05:00Z' }
                            }],
                            meta: { page: 0, hasNext: false }
                        })
                    };
                }
                return { ok: true, status: 200, text: async () => '{"success":true}' };
            };

            const { sandbox } = createStompSandbox(doc, mockFetch);
            sandbox.window.location = { pathname: '/messages/10', search: '', protocol: 'http:', host: 'localhost:8080' };
            const context = vm.createContext(sandbox);
            vm.runInContext(messagingSource, context);

            await new Promise(r => setTimeout(r, 25));
            const ws = FakeWebSocket.instances[0];
            ws.receive('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
            await new Promise(r => setTimeout(r, 20));

            // Record read-receipt count after initial selection has settled
            const receiptsBeforeMessage = readReceiptCount;

            // Deliver older message 450 (createdAt 06:00:00 vs summary 06:05:00) into active conversation
            ws.receive('MESSAGE\ndestination:/user/queue/messages\n\n{"id":450,"conversationId":10,"content":"msg-450","createdAt":"2026-10-20T06:00:00Z","sender":{"id":2,"username":"bob"}}\0');
            await new Promise(r => setTimeout(r, 25));

            assert.equal(readReceiptCount - receiptsBeforeMessage, 1, 'Read receipt count DELTA must be exactly 1 upon active message injection');
            const historyEl = doc.getElementById('chat-history');
            assert.ok(historyEl.children.length >= 1, 'Message bubble must be rendered for active conversation');
        });

        it('D3c: distinct live messages arriving out of order are all counted as unread; preview stays latest; duplicates dropped', async () => {
            const doc = new MockDocument();
            createMessagingDom(doc);

            const initialSummary = {
                success: true,
                data: [
                    {
                        id: 11,
                        name: 'Hội thoại 11',
                        lastMessage: { id: 400, content: 'msg-400', createdAt: '2026-10-20T06:00:00Z' },
                        unreadCount: 0
                    }
                ],
                meta: { page: 0, hasNext: false }
            };

            const mockFetch = async (url) => {
                if (url.includes('/api/conversations?')) {
                    return { ok: true, status: 200, text: async () => JSON.stringify(initialSummary) };
                }
                return { ok: true, status: 200, text: async () => '{"success":true}' };
            };

            const { sandbox } = createStompSandbox(doc, mockFetch);
            const context = vm.createContext(sandbox);
            vm.runInContext(messagingSource, context);

            await new Promise(r => setTimeout(r, 20));
            const ws = FakeWebSocket.instances[0];
            ws.receive('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
            await new Promise(r => setTimeout(r, 10));

            // 1. Deliver event 500 (newer than summary 400) -> unread becomes 1, preview becomes msg-500
            ws.receive('MESSAGE\ndestination:/user/queue/messages\n\n{"id":500,"conversationId":11,"content":"msg-500","createdAt":"2026-10-20T06:10:00Z","sender":{"id":3,"username":"eve"}}\0');
            await new Promise(r => setTimeout(r, 15));

            const convList = doc.getElementById('conversations-list');
            let item11 = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '11');
            assert.ok(item11.innerHTML.includes('>1</span>'), 'Unread count must be 1 after msg-500');
            assert.ok(item11.innerHTML.includes('msg-500'), 'Preview must show msg-500');

            // 2. Deliver event 450 (newer than summary 400, but older than live preview 500)
            // Unread MUST become 2; preview MUST remain msg-500!
            ws.receive('MESSAGE\ndestination:/user/queue/messages\n\n{"id":450,"conversationId":11,"content":"msg-450","createdAt":"2026-10-20T06:05:00Z","sender":{"id":3,"username":"eve"}}\0');
            await new Promise(r => setTimeout(r, 15));

            item11 = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '11');
            assert.ok(item11.innerHTML.includes('>2</span>'), 'Unread count must become 2 after out-of-order distinct event msg-450');
            assert.ok(item11.innerHTML.includes('msg-500'), 'Preview must remain msg-500, not regressed by older msg-450');

            // 3. Repeat duplicate delivery of 450 -> bounded dedup drops it; unread remains 2
            ws.receive('MESSAGE\ndestination:/user/queue/messages\n\n{"id":450,"conversationId":11,"content":"msg-450","createdAt":"2026-10-20T06:05:00Z","sender":{"id":3,"username":"eve"}}\0');
            await new Promise(r => setTimeout(r, 15));

            item11 = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '11');
            assert.ok(item11.innerHTML.includes('>2</span>'), 'Unread count must remain 2; duplicate event must not double count');

            // 4. Deliver older event 300 (older than summary 400) -> not unread, does not regress preview
            ws.receive('MESSAGE\ndestination:/user/queue/messages\n\n{"id":300,"conversationId":11,"content":"msg-300","createdAt":"2026-10-20T05:50:00Z","sender":{"id":3,"username":"eve"}}\0');
            await new Promise(r => setTimeout(r, 15));

            item11 = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '11');
            assert.ok(item11.innerHTML.includes('>2</span>'), 'Unread count must remain 2 for delayed message older than summary');
            assert.ok(item11.innerHTML.includes('msg-500'), 'Preview must remain msg-500');
        });

        it('D2b: pending summary captured before local read does not restore unread; fresh subsequent request authoritatively reconciles to 0', async () => {
            const doc = new MockDocument();
            createMessagingDom(doc);
            const btnRetry = doc.createElement('button');
            btnRetry.id = 'btn-retry-conversations';
            doc.body.appendChild(btnRetry);

            let convCallCount = 0;
            let resolveFirstInFlight;
            const firstInFlightPromise = new Promise(resolve => { resolveFirstInFlight = resolve; });

            const mockFetch = async (url) => {
                if (url.includes('/read')) {
                    return { ok: true, status: 200, text: async () => '{"success":true}' };
                }
                if (url.includes('/api/conversations/11/messages') || url.includes('/api/conversations/10/messages')) {
                    return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: [], meta: { page: 0, hasNext: false } }) };
                }
                if (url.includes('/api/conversations?')) {
                    convCallCount++;
                    if (convCallCount === 1) {
                        // Initial summary: conv 11 has lm500, unread 1
                        return {
                            ok: true,
                            status: 200,
                            text: async () => JSON.stringify({
                                success: true,
                                data: [
                                    { id: 10, name: 'Hội thoại 10', lastMessage: null },
                                    {
                                        id: 11,
                                        name: 'Hội thoại 11',
                                        lastMessage: { id: 500, content: 'msg-500', createdAt: '2026-10-20T06:00:00Z' },
                                        unreadCount: 1
                                    }
                                ],
                                meta: { page: 0, hasNext: false }
                            })
                        };
                    } else if (convCallCount === 2) {
                        // In-flight refresh: hold until user selects conv 11 then conv 10
                        return firstInFlightPromise;
                    } else {
                        // Subsequent fresh request dispatched after local reads: authoritatively returns unread 0
                        return {
                            ok: true,
                            status: 200,
                            text: async () => JSON.stringify({
                                success: true,
                                data: [
                                    { id: 10, name: 'Hội thoại 10', lastMessage: null },
                                    {
                                        id: 11,
                                        name: 'Hội thoại 11',
                                        lastMessage: { id: 500, content: 'msg-500', createdAt: '2026-10-20T06:00:00Z' },
                                        unreadCount: 0
                                    }
                                ],
                                meta: { page: 0, hasNext: false }
                            })
                        };
                    }
                }
                return { ok: true, status: 200, text: async () => '{"success":true}' };
            };

            const { sandbox } = createStompSandbox(doc, mockFetch);
            const context = vm.createContext(sandbox);
            vm.runInContext(messagingSource, context);

            await new Promise(r => setTimeout(r, 20));

            const convList = doc.getElementById('conversations-list');
            let item11 = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '11');
            const item10 = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '10');
            assert.ok(item11.innerHTML.includes('>1</span>'), 'Initially conv 11 has unread badge 1');

            // Trigger second conversations fetch that will be held in flight (dispatched before local read)
            btnRetry.click();
            await new Promise(r => setTimeout(r, 10));

            // User selects conv 11 (marks it as read locally, unreadCount becomes 0)
            item11.click();
            await new Promise(r => setTimeout(r, 15));

            // User selects conv 10 (switches away from conv 11)
            item10.click();
            await new Promise(r => setTimeout(r, 15));

            // Now resolve the older in-flight request (which had unreadCount 1 and was dispatched before the click)
            resolveFirstInFlight({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({
                    success: true,
                    data: [
                        { id: 10, name: 'Hội thoại 10', lastMessage: null },
                        {
                            id: 11,
                            name: 'Hội thoại 11',
                            lastMessage: { id: 500, content: 'msg-500', createdAt: '2026-10-20T06:00:00Z' },
                            unreadCount: 1
                        }
                    ],
                    meta: { page: 0, hasNext: false }
                })
            });
            await new Promise(r => setTimeout(r, 25));

            item11 = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '11');
            assert.ok(!item11.innerHTML.includes('rounded-pill bg-primary'), 'Old pending in-flight summary must NOT restore unread after local read');

            // Simulate incoming live event while inactive -> unread becomes 1 (nonzero state)
            const ws = FakeWebSocket.instances[0];
            ws.receive('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
            await new Promise(r => setTimeout(r, 20));
            ws.receive('MESSAGE\ndestination:/user/queue/messages\n\n{"id":601,"conversationId":11,"content":"temp unread","createdAt":"2026-10-20T08:00:00Z","sender":{"id":99,"username":"eve"}}\0');
            await new Promise(r => setTimeout(r, 20));

            item11 = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '11');
            assert.ok(item11.innerHTML.includes('>1</span>'), 'Local unread is now nonzero (1)');

            // Now perform a fresh subsequent request (dispatched AFTER the live event, returning unread 0)
            btnRetry.click();
            await new Promise(r => setTimeout(r, 25));

            item11 = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '11');
            assert.ok(!item11.innerHTML.includes('rounded-pill bg-primary'), 'Fresh server snapshot authoritatively transitions unread from 1 to 0');
        });

        it('D2c: read then later live event preserves unread when held pre-read summary arrives', async () => {
            const doc = new MockDocument();
            createMessagingDom(doc);
            const btnRetry = doc.createElement('button');
            btnRetry.id = 'btn-retry-conversations';
            doc.body.appendChild(btnRetry);

            let convCallCount = 0;
            let resolveHeldSummary;
            const heldSummaryPromise = new Promise(resolve => { resolveHeldSummary = resolve; });

            const mockFetch = async (url) => {
                if (url.includes('/read')) {
                    return { ok: true, status: 200, text: async () => '{"success":true}' };
                }
                if (url.includes('/api/conversations/11/messages') || url.includes('/api/conversations/10/messages')) {
                    return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: [], meta: { page: 0, hasNext: false } }) };
                }
                if (url.includes('/api/conversations?')) {
                    convCallCount++;
                    // call 1 = init(); first CONNECTED no longer re-fetches (dedupe),
                    // so the retry click is call 2 and it is the held in-flight summary.
                    if (convCallCount <= 1) {
                        return {
                            ok: true,
                            status: 200,
                            text: async () => JSON.stringify({
                                success: true,
                                data: [
                                    { id: 10, name: 'Hội thoại 10', lastMessage: null },
                                    {
                                        id: 11,
                                        name: 'Hội thoại 11',
                                        lastMessage: { id: 500, content: 'msg-500', createdAt: '2026-10-20T06:00:00Z' },
                                        unreadCount: 1
                                    }
                                ],
                                meta: { page: 0, hasNext: false }
                            })
                        };
                    } else {
                        return heldSummaryPromise;
                    }
                }
                return { ok: true, status: 200, text: async () => '{"success":true}' };
            };

            const { sandbox } = createStompSandbox(doc, mockFetch);
            const context = vm.createContext(sandbox);
            vm.runInContext(messagingSource, context);

            await new Promise(r => setTimeout(r, 20));
            const ws = FakeWebSocket.instances[0];
            ws.receive('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
            await new Promise(r => setTimeout(r, 25));

            const convList = doc.getElementById('conversations-list');
            let item11 = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '11');
            const item10 = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '10');
            assert.ok(item11.innerHTML.includes('>1</span>'), 'Initially conv 11 has unread badge 1');

            // 1. Dispatch summary request (held in flight)
            btnRetry.click();
            await new Promise(r => setTimeout(r, 10));

            // 2. User selects conv 11 (marks it read, unreadCount becomes 0)
            item11.click();
            await new Promise(r => setTimeout(r, 15));
            item11 = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '11');
            assert.ok(!item11.innerHTML.includes('rounded-pill bg-primary'), 'Conv 11 unread badge cleared after click');

            // 3. User switches to conv 10
            item10.click();
            await new Promise(r => setTimeout(r, 15));

            // 4. New live STOMP message arrives for conv 11 while inactive
            ws.receive('MESSAGE\ndestination:/user/queue/messages\n\n{"id":600,"conversationId":11,"content":"new live after read","createdAt":"2026-10-20T07:00:00Z","sender":{"id":99,"username":"eve"}}\0');
            await new Promise(r => setTimeout(r, 20));

            item11 = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '11');
            assert.ok(item11.innerHTML.includes('>1</span>'), 'Live message for inactive conv 11 increments unread to 1');

            // 5. Held pre-read summary arrives
            resolveHeldSummary({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({
                    success: true,
                    data: [
                        { id: 10, name: 'Hội thoại 10', lastMessage: null },
                        {
                            id: 11,
                            name: 'Hội thoại 11',
                            lastMessage: { id: 500, content: 'msg-500', createdAt: '2026-10-20T06:00:00Z' },
                            unreadCount: 1
                        }
                    ],
                    meta: { page: 0, hasNext: false }
                })
            });
            await new Promise(r => setTimeout(r, 25));

            item11 = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '11');
            assert.ok(item11.innerHTML.includes('>1</span>'), 'Held pre-read summary must NOT zero unread for later live message');
            assert.ok(item11.innerHTML.includes('new live after read'), 'Preview preserves latest live message 600');
        });

        it('D2d: equal-preview response does not wipe distinct live unread count', async () => {
            const doc = new MockDocument();
            createMessagingDom(doc);
            const btnRetry = doc.createElement('button');
            btnRetry.id = 'btn-retry-conversations';
            doc.body.appendChild(btnRetry);

            let convCallCount = 0;
            let resolveHeldSummary;
            const heldSummaryPromise = new Promise(resolve => { resolveHeldSummary = resolve; });

            const mockFetch = async (url) => {
                if (url.includes('/read')) {
                    return { ok: true, status: 200, text: async () => '{"success":true}' };
                }
                if (url.includes('/api/conversations?')) {
                    convCallCount++;
                    // call 1 = init(); first CONNECTED no longer re-fetches (dedupe),
                    // so the retry click is call 2 and it is the held in-flight summary.
                    if (convCallCount <= 1) {
                        // Baseline summary: conv 11 has lm400, unread 0
                        return {
                            ok: true,
                            status: 200,
                            text: async () => JSON.stringify({
                                success: true,
                                data: [
                                    {
                                        id: 11,
                                        name: 'Hội thoại 11',
                                        lastMessage: { id: 400, content: 'msg-400', createdAt: '2026-10-20T04:00:00Z' },
                                        unreadCount: 0
                                    }
                                ],
                                meta: { page: 0, hasNext: false }
                            })
                        };
                    } else {
                        // Held summary response with lastMessage 500, unread 1
                        return heldSummaryPromise;
                    }
                }
                return { ok: true, status: 200, text: async () => '{"success":true}' };
            };

            const { sandbox } = createStompSandbox(doc, mockFetch);
            const context = vm.createContext(sandbox);
            vm.runInContext(messagingSource, context);

            await new Promise(r => setTimeout(r, 20));
            const ws = FakeWebSocket.instances[0];
            ws.receive('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
            await new Promise(r => setTimeout(r, 25));

            // 1. Receive live 500 -> unread becomes 1, preview becomes 500
            ws.receive('MESSAGE\ndestination:/user/queue/messages\n\n{"id":500,"conversationId":11,"content":"msg-500","createdAt":"2026-10-20T05:00:00Z","sender":{"id":99,"username":"eve"}}\0');
            await new Promise(r => setTimeout(r, 20));

            const convList = doc.getElementById('conversations-list');
            let item11 = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '11');
            assert.ok(item11.innerHTML.includes('>1</span>'), 'Live 500 sets unread to 1');
            assert.ok(item11.innerHTML.includes('msg-500'), 'Preview is msg-500');

            // 2. Dispatch summary request (held in flight) with lastMessage 500, unread 1
            btnRetry.click();
            await new Promise(r => setTimeout(r, 10));

            // 3. Receive distinct live 450 (older than 500, but newer than baseline 400)
            ws.receive('MESSAGE\ndestination:/user/queue/messages\n\n{"id":450,"conversationId":11,"content":"msg-450","createdAt":"2026-10-20T04:30:00Z","sender":{"id":99,"username":"eve"}}\0');
            await new Promise(r => setTimeout(r, 20));

            item11 = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '11');
            assert.ok(item11.innerHTML.includes('>2</span>'), 'Distinct live 450 increments unread to 2');
            assert.ok(item11.innerHTML.includes('msg-500'), 'Preview stays latest 500');

            // 4. Held response arrives with lastMessage 500 (equal preview), unread 1
            resolveHeldSummary({
                ok: true,
                status: 200,
                text: async () => JSON.stringify({
                    success: true,
                    data: [
                        {
                            id: 11,
                            name: 'Hội thoại 11',
                            lastMessage: { id: 500, content: 'msg-500', createdAt: '2026-10-20T05:00:00Z' },
                            unreadCount: 1
                        }
                    ],
                    meta: { page: 0, hasNext: false }
                })
            });
            await new Promise(r => setTimeout(r, 25));

            item11 = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '11');
            assert.ok(item11.innerHTML.includes('>2</span>'), 'Equal-preview held response must NOT wipe distinct unread back to 1');
            assert.ok(item11.innerHTML.includes('msg-500'), 'Preview remains msg-500');

            // 5. Subsequent duplicate 450 arrives -> dropped by dedup, unread remains 2
            ws.receive('MESSAGE\ndestination:/user/queue/messages\n\n{"id":450,"conversationId":11,"content":"msg-450-duplicate","createdAt":"2026-10-20T04:30:00Z","sender":{"id":99,"username":"eve"}}\0');
            await new Promise(r => setTimeout(r, 20));

            item11 = convList.children.find(c => c.getAttribute && c.getAttribute('data-conversation-id') === '11');
            assert.ok(item11.innerHTML.includes('>2</span>'), 'Duplicate 450 does not increment unread (stays 2)');
            assert.ok(item11.innerHTML.includes('msg-500'), 'Preview remains msg-500');
        });

        it('does not re-fetch the conversations list on the first CONNECTED; refreshes it on reconnect', async () => {
            const doc = new MockDocument();
            createMessagingDom(doc);

            let conversationsFetches = 0;
            const mockFetch = async (url) => {
                if (url.includes('/api/conversations?')) conversationsFetches++;
                return {
                    ok: true,
                    status: 200,
                    text: async () => JSON.stringify({
                        success: true,
                        data: [{ id: 42, name: 'Hội thoại 42', lastMessage: null }],
                        meta: { page: 0, hasNext: false }
                    })
                };
            };

            const { sandbox } = createStompSandbox(doc, mockFetch);
            const context = vm.createContext(sandbox);
            vm.runInContext(messagingSource, context);

            await new Promise(r => setTimeout(r, 20));
            assert.equal(conversationsFetches, 1, 'init() fetches the conversations list exactly once');

            const ws = FakeWebSocket.instances[0];
            ws.receive('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
            await new Promise(r => setTimeout(r, 10));
            assert.equal(conversationsFetches, 1, 'first CONNECTED must not duplicate the list fetch');

            ws.receive('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
            await new Promise(r => setTimeout(r, 25));
            assert.equal(conversationsFetches, 2, 'reconnect CONNECTED refreshes the list');
        });
    });
});
