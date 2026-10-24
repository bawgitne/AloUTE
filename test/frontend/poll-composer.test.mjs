/**
 * AloUTE — Poll Composer Regression Tests
 *
 * Covers the poll builder wired into the shared post composer
 * (templates/post/_composer-form.html + js/features/post/post-composer.js):
 * - toggle visibility, option add/remove clamps (2..10) and renumbering
 * - submit-time validation (question required, ≥2 non-blank options)
 * - CreatePostRequest.poll payload shape (trimmed options, allowMultiple, durationDays)
 * - payload omits poll when the builder is inactive
 * - draft round-trip including poll fields; discard resets the builder
 *
 * node:test + assert/strict + vm, same harness pattern as final-web-polish.test.mjs.
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

const postComposerSource = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/static/js/features/post/post-composer.js'),
    'utf-8'
);

const composerTemplate = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/templates/post/_composer-form.html'),
    'utf-8'
);

// -----------------------------------------------------------------------------
// Lightweight DOM harness (same subset as final-web-polish.test.mjs)
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
        this.type = '';
        this.maxLength = -1;
        this.placeholder = '';
        this.tabIndex = 0;
        this._focused = false;
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
    set innerHTML(html) {
        this._innerHTML = String(html || '');
        this.children = [];
        this._textContent = '';
    }

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

    focus() { this._focused = true; }
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

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function createEnvironment() {
    const document = {
        createElement: (tag) => new MockElement(tag, '', document),
        getElementById: (id) => querySelector(document.body, `#${id}`),
        querySelector: (sel) => querySelector(document.body, sel),
        querySelectorAll: (sel) => querySelectorAll(document.body, sel, []),
        addEventListener: () => {},
        removeEventListener: () => {},
        cookie: 'XSRF-TOKEN=test-csrf-token;',
        readyState: 'complete',
        activeElement: null
    };
    document.body = new MockElement('BODY', '', document);
    document.documentElement = new MockElement('HTML', '', document);
    document.documentElement.appendChild(document.body);
    document.body.dataset.currentUserId = '1';

    const storage = new Map();
    const fetchCalls = [];

    const window = {
        document,
        fetchCalls,
        location: { href: 'http://localhost:8080/', origin: 'http://localhost:8080', pathname: '/' },
        console,
        setTimeout: (fn, ms) => setTimeout(fn, ms),
        clearTimeout: (id) => clearTimeout(id),
        Date,
        localStorage: {
            getItem: (k) => (storage.has(k) ? storage.get(k) : null),
            setItem: (k, v) => storage.set(k, String(v)),
            removeItem: (k) => storage.delete(k),
            _map: storage
        },
        fetch: async (url, opts = {}) => {
            fetchCalls.push({ url, opts });
            if (window.fetchHandler) return window.fetchHandler(url, opts);
            return { ok: true, status: 201, text: async () => JSON.stringify({ success: true, data: { id: 5 } }) };
        }
    };
    window.window = window;
    document.defaultView = window;
    window.Event = MockEvent;
    window.CustomEvent = class extends MockEvent {};
    return { document, window, fetchCalls, storage };
}

function buildComposerDom(document, { withPoll = true } = {}) {
    const form = document.createElement('form');
    form.id = 'create-post-form';

    const content = document.createElement('textarea');
    content.id = 'postContent';
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

    let poll = null;
    if (withPoll) {
        const toggle = document.createElement('button');
        toggle.id = 'btn-toggle-poll';
        const builder = document.createElement('div');
        builder.id = 'poll-builder';
        builder.className = 'd-none';
        const pollStatus = document.createElement('div');
        pollStatus.id = 'poll-builder-status';
        pollStatus.className = 'd-none';
        const question = document.createElement('input');
        question.id = 'poll-question';
        const optionsList = document.createElement('div');
        optionsList.id = 'poll-options-list';
        for (let i = 0; i < 2; i++) {
            const row = document.createElement('div');
            row.className = 'poll-option-row';
            const badge = document.createElement('span');
            badge.className = 'input-group-text';
            badge.textContent = String(i + 1);
            const input = document.createElement('input');
            input.className = 'poll-option-input';
            const removeBtn = document.createElement('button');
            removeBtn.className = 'poll-option-remove d-none';
            row.appendChild(badge);
            row.appendChild(input);
            row.appendChild(removeBtn);
            optionsList.appendChild(row);
        }
        const addBtn = document.createElement('button');
        addBtn.id = 'btn-add-poll-option';
        const allowMultiple = document.createElement('input');
        allowMultiple.id = 'poll-allow-multiple';
        allowMultiple.checked = false;
        const duration = document.createElement('select');
        duration.id = 'poll-duration';
        duration.value = '';
        const countLabel = document.createElement('span');
        countLabel.id = 'poll-option-count';

        builder.appendChild(pollStatus);
        builder.appendChild(question);
        builder.appendChild(optionsList);
        builder.appendChild(addBtn);
        builder.appendChild(allowMultiple);
        builder.appendChild(duration);
        builder.appendChild(countLabel);
        form.appendChild(toggle);
        form.appendChild(builder);

        poll = { toggle, builder, pollStatus, question, optionsList, addBtn, allowMultiple, duration, countLabel };
    }

    document.body.appendChild(form);
    return { form, content, visibility, allowReply, allowQuote, submit, status, poll };
}

function runComposer(window) {
    vm.runInContext(postComposerSource, vm.createContext(window));
}

// -----------------------------------------------------------------------------
// Suites
// -----------------------------------------------------------------------------

describe('Poll composer — builder UI', () => {
    it('renders the poll builder markup in the shared composer form', () => {
        for (const id of ['btn-toggle-poll', 'poll-builder', 'poll-question', 'poll-options-list',
            'btn-add-poll-option', 'poll-allow-multiple', 'poll-duration', 'poll-option-count']) {
            assert.ok(composerTemplate.includes(`id="${id}"`), `${id} present in template`);
        }
        // Two starter option rows ship in markup
        const rows = composerTemplate.match(/poll-option-row/g) || [];
        assert.ok(rows.length >= 2, 'template starts with two option rows');
    });

    it('toggles the builder via the button and clamps options between 2 and 10', async () => {
        const env = createEnvironment();
        const dom = buildComposerDom(env.document);
        runComposer(env.window);

        assert.ok(dom.poll.builder.classList.contains('d-none'), 'builder hidden initially');
        dom.poll.toggle.click();
        assert.ok(!dom.poll.builder.classList.contains('d-none'), 'builder shown after toggle');
        assert.equal(dom.poll.toggle.getAttribute('aria-pressed'), 'true');

        // Add up to 10 rows total
        for (let i = 0; i < 8; i++) dom.poll.addBtn.click();
        assert.equal(dom.poll.optionsList.querySelectorAll('.poll-option-row').length, 10, 'capped at 10');
        assert.equal(dom.poll.addBtn.disabled, true, 'add button disabled at cap');
        assert.equal(dom.poll.countLabel.textContent, '10 / 10 phương án');

        // Remove buttons visible beyond the minimum; removing renumbers badges
        const removeBtns = dom.poll.optionsList.querySelectorAll('.poll-option-remove');
        assert.equal(removeBtns.filter((b) => !b.classList.contains('d-none')).length, 10, 'every row removable above minimum');
        removeBtns[9].click(); // remove last row
        assert.equal(dom.poll.optionsList.querySelectorAll('.poll-option-row').length, 9);
        const badges = dom.poll.optionsList.querySelectorAll('.input-group-text').map((b) => b.textContent);
        assert.deepEqual(badges.slice(-2), ['8', '9'], 'badges renumbered');
    });
});

describe('Poll composer — submit validation and payload', () => {
    it('blocks submit when the poll is active without a question or with fewer than 2 filled options', async () => {
        const env = createEnvironment();
        const dom = buildComposerDom(env.document);
        runComposer(env.window);

        dom.poll.toggle.click();
        dom.content.value = 'Bai viet kem binh chon';
        dom.content.dispatchEvent(new MockEvent('input'));

        dom.submit.click(); // no question, no options
        await sleep(50);
        assert.equal(env.fetchCalls.length, 0, 'no request sent');
        assert.equal(dom.poll.pollStatus.textContent, 'Vui lòng nhập câu hỏi bình chọn.');

        dom.poll.question.value = 'Chu de nao ban thich?';
        dom.poll.question.dispatchEvent(new MockEvent('input'));
        dom.submit.click();
        await sleep(50);
        assert.equal(env.fetchCalls.length, 0, 'still no request');
        assert.ok(dom.poll.pollStatus.textContent.includes('ít nhất 2 phương án'), 'option count error shown');
    });

    it('omits the poll key when the builder is inactive', async () => {
        const env = createEnvironment();
        const dom = buildComposerDom(env.document);
        runComposer(env.window);

        dom.content.value = 'Bai viet thuong';
        dom.content.dispatchEvent(new MockEvent('input'));
        dom.submit.click();
        await sleep(50);
        assert.equal(env.fetchCalls.length, 1);
        assert.equal(JSON.parse(env.fetchCalls[0].opts.body).poll, undefined, 'no poll when inactive');
    });

    it('sends a trimmed PollCreateInput payload when the builder is active', async () => {
        const env = createEnvironment();
        const dom = buildComposerDom(env.document);
        runComposer(env.window);

        dom.poll.toggle.click();
        dom.poll.question.value = '  Chon mon hoc  ';
        const optionInputs = dom.poll.optionsList.querySelectorAll('.poll-option-input');
        optionInputs[0].value = ' CNPM ';
        optionInputs[1].value = 'KTLT';
        dom.poll.allowMultiple.checked = true;
        dom.poll.allowMultiple.dispatchEvent(new MockEvent('change'));
        dom.poll.duration.value = '3';
        dom.poll.duration.dispatchEvent(new MockEvent('change'));
        dom.content.value = 'Bai viet kem binh chon';
        dom.content.dispatchEvent(new MockEvent('input'));
        dom.submit.click();
        await sleep(50);

        assert.equal(env.fetchCalls.length, 1);
        const body = JSON.parse(env.fetchCalls[0].opts.body);
        assert.deepEqual(body.poll, {
            question: 'Chon mon hoc',
            options: ['CNPM', 'KTLT'],
            allowMultiple: true,
            durationDays: 3
        });
    });
});

describe('Poll composer — draft recovery integration', () => {
    it('round-trips an active poll through the draft and resets it on discard', async () => {
        // Session 1: type content + poll, let the debounce persist the draft
        const env = createEnvironment();
        const dom = buildComposerDom(env.document);
        runComposer(env.window);

        dom.content.value = 'Bai viet kem binh chon';
        dom.content.dispatchEvent(new MockEvent('input'));
        dom.poll.toggle.click();
        dom.poll.question.value = 'Mon nao kho nhat?';
        const optionInputs = dom.poll.optionsList.querySelectorAll('.poll-option-input');
        optionInputs[0].value = 'KTLT';
        optionInputs[1].value = 'CSDL';
        optionInputs[1].dispatchEvent(new MockEvent('input'));
        dom.poll.duration.value = '7';
        dom.poll.duration.dispatchEvent(new MockEvent('change'));
        await sleep(600);

        const raw = env.window.localStorage.getItem('aloute.post.draft.v1.u1.croot');
        assert.ok(raw, 'draft persisted');
        assert.equal(JSON.parse(raw).poll.question, 'Mon nao kho nhat?');

        // Session 2 (fresh page, same browser profile): restore
        const env2 = createEnvironment();
        env2.window.localStorage._map.set('aloute.post.draft.v1.u1.croot', raw);
        const dom2 = buildComposerDom(env2.document);
        runComposer(env2.window);
        await sleep(10);

        assert.ok(!dom2.poll.builder.classList.contains('d-none'), 'builder restored as active');
        assert.equal(dom2.poll.question.value, 'Mon nao kho nhat?');
        assert.equal(dom2.poll.duration.value, '7');
        const restored = dom2.poll.optionsList.querySelectorAll('.poll-option-input').map((i) => i.value);
        assert.deepEqual(restored, ['KTLT', 'CSDL']);

        // Discard resets the whole composer including the poll builder
        const discard = env2.document.getElementById('btn-discard-draft');
        assert.ok(discard, 'discard affordance rendered');
        discard.click();
        assert.ok(dom2.poll.builder.classList.contains('d-none'), 'builder hidden after discard');
        assert.equal(dom2.poll.question.value, '', 'question cleared');
        assert.equal(env2.window.localStorage.getItem('aloute.post.draft.v1.u1.croot'), null, 'draft removed');
    });
});
