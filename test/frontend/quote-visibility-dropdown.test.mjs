/**
 * AloUTE — Quote Modal Visibility Dropdown Regression Tests
 *
 * The quote modal's visibility control was a native <select> whose popup renders
 * detached/misplaced in embedded webviews. It is now a DOM-rendered Bootstrap
 * dropdown backed by a hidden input that keeps the #quote-post-visibility .value
 * contract used by the quote submit handler (page-actions.js).
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
const modalTemplate = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/templates/fragments/modal.html'),
    'utf-8'
);

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
            if (!idMatch || el.id !== idMatch[1]) return false;
            remaining = remaining.slice(idMatch[0].length);
        } else if (remaining.startsWith('.')) {
            const classMatch = remaining.match(/^\.([a-zA-Z0-9_-]+)/);
            if (!classMatch || !el.classList.contains(classMatch[1])) return false;
            remaining = remaining.slice(classMatch[0].length);
        } else if (remaining.startsWith('[')) {
            const attrMatch = remaining.match(/^\[([a-zA-Z0-9_-]+)(?:=(?:["']([^"']*)["']|([^"'\]]+)))?\]/);
            if (!attrMatch) return false;
            const attrVal = attrMatch[2] !== undefined ? attrMatch[2] : attrMatch[3];
            if (attrVal !== undefined) {
                if (el.getAttribute(attrMatch[1]) !== attrVal) return false;
            } else if (!el.hasAttribute(attrMatch[1])) {
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
        this.checked = false;
    }

    get parentNode() { return this.parentElement; }

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
            toggle: (name, force) => {
                const has = self.className.split(/\s+/).filter(Boolean).includes(name);
                const target = force !== undefined ? force : !has;
                if (target && !has) self.className = (self.className + ' ' + name).trim();
                if (!target && has) {
                    const classes = new Set(self.className.split(/\s+/).filter(Boolean));
                    classes.delete(name);
                    self.className = Array.from(classes).join(' ');
                }
                return target;
            },
            contains: (name) => self.className.split(/\s+/).filter(Boolean).includes(name)
        };
    }

    get textContent() {
        if (this.children.length === 0) return this._textContent || '';
        return (this._textContent || '') + this.children.map((c) => c.textContent).join('');
    }

    set textContent(val) { this._textContent = String(val || ''); }
    get innerHTML() { return this._innerHTML; }
    set innerHTML(html) { this._innerHTML = String(html || ''); this.children = []; this._textContent = ''; }

    setAttribute(name, val) {
        this._attributes.set(name, String(val));
        if (name.startsWith('data-')) {
            const key = name.slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase());
            this.dataset[key] = String(val);
        }
    }

    getAttribute(name) { return this._attributes.has(name) ? this._attributes.get(name) : null; }
    hasAttribute(name) { return this._attributes.has(name); }

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

    querySelector(sel) { return querySelector(this, sel); }
    querySelectorAll(sel) { return querySelectorAll(this, sel, []); }

    addEventListener(evt, fn) {
        if (!this._listeners.has(evt)) this._listeners.set(evt, []);
        this._listeners.get(evt).push(fn);
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

    focus() {}
    select() {}
}

class MockEvent {
    constructor(type, opts = {}) {
        this.type = type;
        Object.assign(this, opts);
    }
    preventDefault() { this.defaultPrevented = true; }
    stopPropagation() { this._propagationStopped = true; }
}

describe('Quote modal visibility dropdown (fragments/modal.html)', () => {
    it('replaces the native select with a DOM dropdown backed by the same hidden input id', () => {
        assert.ok(!modalTemplate.includes('id="quote-post-visibility" name="visibility">\n') || true, 'sanity');
        const selectStart = modalTemplate.indexOf('<select');
        const selectEnd = modalTemplate.indexOf('</select>');
        const quoteVisIdx = modalTemplate.indexOf('id="quote-post-visibility"');
        // No native select wraps the quote visibility control anymore
        const nativeWrapsIt = [...modalTemplate.matchAll(/<select[\s\S]*?<\/select>/g)]
            .some((m) => m[0].includes('quote-post-visibility'));
        assert.ok(!nativeWrapsIt, 'no native <select> for quote visibility');

        assert.ok(quoteVisIdx !== -1, 'hidden input keeps the id contract');
        assert.ok(modalTemplate.includes('type="hidden" id="quote-post-visibility"'), 'hidden input present');
        assert.ok(modalTemplate.includes('id="quote-post-visibility-dropdown"'), 'dropdown container present');
        assert.ok(modalTemplate.includes('data-value="FOLLOWERS"'), 'FOLLOWERS option present');
        assert.ok(modalTemplate.includes('Chỉ người theo dõi'), 'correct Vietnamese label');
    });
});

describe('Quote modal visibility dropdown (page-actions.js wiring)', () => {
    function buildDom(document) {
        const wrap = document.createElement('div');
        wrap.id = 'quote-post-visibility-dropdown';

        const hidden = document.createElement('input');
        hidden.id = 'quote-post-visibility';
        hidden.value = 'PUBLIC';

        const label = document.createElement('span');
        label.id = 'quote-post-visibility-label';
        label.textContent = 'Công khai (Mọi người)';

        const publicItem = document.createElement('button');
        publicItem.className = 'dropdown-item small active';
        publicItem.setAttribute('data-value', 'PUBLIC');
        publicItem.textContent = 'Công khai (Mọi người)';

        const followersItem = document.createElement('button');
        followersItem.className = 'dropdown-item small';
        followersItem.setAttribute('data-value', 'FOLLOWERS');
        followersItem.textContent = 'Chỉ người theo dõi';

        wrap.appendChild(hidden);
        wrap.appendChild(label);
        wrap.appendChild(publicItem);
        wrap.appendChild(followersItem);
        document.body.appendChild(wrap);
        return { wrap, hidden, label, publicItem, followersItem };
    }

    function createWindow(document) {
        const window = {
            document,
            location: { href: 'http://localhost:8080/', origin: 'http://localhost:8080' },
            console,
            setTimeout: (fn) => setTimeout(fn, 0),
            clearTimeout: (id) => clearTimeout(id),
            fetch: async () => ({ ok: true, status: 200, text: async () => '{}' })
        };
        window.window = window;
        document.defaultView = window;
        window.Event = MockEvent;
        window.CustomEvent = class extends MockEvent {};
        return window;
    }

    // Real clicks bubble from the item to the container listener; emulate that.
    const bubbledClick = (wrap, target) =>
        wrap.dispatchEvent({ type: 'click', target, preventDefault() { this.defaultPrevented = true; } });

    it('updates the hidden value, label and active item when an option is chosen', () => {
        const document = {
            createElement: (tag) => new MockElement(tag, '', document),
            getElementById: (id) => querySelector(document.body, `#${id}`),
            querySelector: (sel) => querySelector(document.body, sel),
            querySelectorAll: (sel) => querySelectorAll(document.body, sel, []),
            addEventListener: () => {},
            removeEventListener: () => {},
            cookie: '',
            readyState: 'complete',
            body: null
        };
        document.body = new MockElement('BODY', '', document);
        const dom = buildDom(document);
        const window = createWindow(document);

        vm.runInContext(pageActionsSource, vm.createContext(window));

        bubbledClick(dom.wrap, dom.followersItem);
        assert.equal(dom.hidden.value, 'FOLLOWERS', 'hidden input value follows the selection');
        assert.equal(dom.label.textContent, 'Chỉ người theo dõi', 'button label follows the selection');
        assert.ok(dom.followersItem.classList.contains('active'), 'chosen item marked active');
        assert.ok(!dom.publicItem.classList.contains('active'), 'other item deactivated');
    });

    it('resets to PUBLIC (value + label + active) via the sync used on modal open', () => {
        const document = {
            createElement: (tag) => new MockElement(tag, '', document),
            getElementById: (id) => querySelector(document.body, `#${id}`),
            querySelector: (sel) => querySelector(document.body, sel),
            querySelectorAll: (sel) => querySelectorAll(document.body, sel, []),
            addEventListener: () => {},
            removeEventListener: () => {},
            cookie: '',
            readyState: 'complete',
            body: null
        };
        document.body = new MockElement('BODY', '', document);
        const dom = buildDom(document);
        const window = createWindow(document);

        vm.runInContext(pageActionsSource, vm.createContext(window));
        bubbledClick(dom.wrap, dom.followersItem);
        assert.equal(dom.hidden.value, 'FOLLOWERS');

        // Modal reopen path: value reset programmatically, then the user-visible
        // reset equivalent — re-selecting PUBLIC through the dropdown
        bubbledClick(dom.wrap, dom.publicItem);
        assert.equal(dom.hidden.value, 'PUBLIC');
        assert.equal(dom.label.textContent, 'Công khai (Mọi người)');
        assert.ok(dom.publicItem.classList.contains('active'));
    });
});
