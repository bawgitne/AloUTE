/**
 * AloUTE — Topic Tagging in the Post Composer
 *
 * Verifies templates/post/_composer-form.html + js/features/post/post-composer.js:
 * - topic search loads GET /api/topics and renders selectable results
 * - selecting/deselecting topics keeps a deduplicated chip list
 * - CreatePostRequest.topicIds is sent only when topics are selected
 * - Enter inside the topic search never submits the post form
 * - empty, error and retry states are surfaced
 * - ?topicId= prefill and local-draft round-trip restore the selection
 *
 * node:test + assert/strict + vm, same harness pattern as poll-composer.test.mjs.
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
const topicDetailTemplate = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/templates/topic/detail.html'),
    'utf-8'
);
const postCardTemplate = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/templates/post/_post-card.html'),
    'utf-8'
);

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

class MockElement {
    constructor(tagName = 'DIV', ownerDocument = null) {
        this.tagName = tagName.toUpperCase();
        this.ownerDocument = ownerDocument;
        this.id = '';
        this.className = '';
        this.children = [];
        this.parentElement = null;
        this._attributes = new Map();
        this._listeners = new Map();
        this.dataset = {};
        this.style = {};
        this._textContent = '';
        this.value = '';
        this.checked = false;
        this.disabled = false;
        this.type = '';
        this._focused = false;
    }

    get parentNode() { return this.parentElement; }

    get classList() {
        const self = this;
        const classes = () => new Set(self.className.split(/\s+/).filter(Boolean));
        return {
            add(...names) {
                const set = classes();
                names.forEach((n) => set.add(n));
                self.className = Array.from(set).join(' ');
            },
            remove(...names) {
                const set = classes();
                names.forEach((n) => set.delete(n));
                self.className = Array.from(set).join(' ');
            },
            contains(name) { return classes().has(name); },
            toggle(name, force) {
                const has = classes().has(name);
                const target = force === undefined ? !has : !!force;
                target ? this.add(name) : this.remove(name);
                return target;
            }
        };
    }

    get textContent() {
        return (this._textContent || '') + this.children.map((child) => child.textContent).join('');
    }

    set textContent(value) { this._textContent = String(value == null ? '' : value); }

    get innerHTML() { return this._textContent; }

    set innerHTML(value) {
        this._textContent = String(value || '');
        this.children = [];
    }

    setAttribute(name, value) {
        this._attributes.set(name, String(value));
        if (name === 'id') this.id = String(value);
        if (name === 'class') this.className = String(value);
        if (name.startsWith('data-')) {
            const key = name.slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase());
            this.dataset[key] = String(value);
        }
    }

    getAttribute(name) { return this._attributes.has(name) ? this._attributes.get(name) : null; }
    hasAttribute(name) { return this._attributes.has(name); }

    appendChild(child) {
        child.parentElement = this;
        this.children.push(child);
        return child;
    }

    insertBefore(node, reference) {
        node.parentElement = this;
        const index = this.children.indexOf(reference);
        if (index === -1) this.children.push(node);
        else this.children.splice(index, 0, node);
        return node;
    }

    addEventListener(type, listener) {
        if (!this._listeners.has(type)) this._listeners.set(type, []);
        this._listeners.get(type).push(listener);
    }

    dispatchEvent(event) {
        event.target = event.target || this;
        event.currentTarget = this;
        for (const listener of this._listeners.get(event.type) || []) {
            listener(event);
        }
        return !event.defaultPrevented;
    }

    click() {
        this.dispatchEvent({ type: 'click', preventDefault() { this.defaultPrevented = true; } });
    }

    focus() { this._focused = true; }
    querySelectorAll() { return []; }
}

function querySelector(root, selector) {
    const match = (element) => {
        if (selector.startsWith('#')) return element.id === selector.slice(1);
        if (selector.startsWith('[data-')) {
            const body = selector.slice(1, -1);
            const [name, raw] = body.split('=');
            const expected = raw ? raw.replace(/^["']|["']$/g, '') : null;
            return expected === null
                ? element !== root && element.hasAttribute(name)
                : element.getAttribute(name) === expected;
        }
        return false;
    };
    for (const child of root.children) {
        if (match(child)) return child;
        const found = querySelector(child, selector);
        if (found) return found;
    }
    return null;
}

function createEnvironment({ topics, initialTopicId = '', locationSearch = '', topicFailure = false, draft = null } = {}) {
    const document = {
        cookie: 'XSRF-TOKEN=test-csrf-token;',
        readyState: 'complete',
        activeElement: null,
        createElement: (tag) => new MockElement(tag, document),
        getElementById: (id) => querySelector(document.body, `#${id}`),
        addEventListener: () => {}
    };
    document.body = new MockElement('BODY', document);
    document.body.dataset.currentUserId = '1';
    document.documentElement = new MockElement('HTML', document);
    document.documentElement.appendChild(document.body);

    const fetchCalls = [];
    const storage = new Map();
    if (draft) {
        storage.set('aloute.post.draft.v1.u1.croot', JSON.stringify(draft));
    }
    const responses = {
        listTopics: (query) => {
            if (topicFailure) return { ok: false, status: 500, text: async () => '' };
            const normalized = query.toLowerCase();
            const data = topics.filter((topic) => !normalized
                || topic.name.toLowerCase().includes(normalized)
                || topic.slug.toLowerCase().includes(normalized));
            return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data }) };
        },
        getTopic: (id) => {
            const topic = topics.find((item) => String(item.id) === String(id));
            return topic
                ? { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: topic }) }
                : { ok: false, status: 404, text: async () => JSON.stringify({ success: false, error: { code: 'TOPIC_NOT_FOUND' } }) };
        },
        createPost: async () => ({ ok: true, status: 201, text: async () => JSON.stringify({ success: true, data: { id: 99 } }) })
    };

    const window = {
        document,
        console,
        Date,
        URLSearchParams,
        setTimeout,
        clearTimeout,
        location: { href: 'http://localhost:8080/post/create' + locationSearch, origin: 'http://localhost:8080', search: locationSearch },
        localStorage: {
            getItem: (key) => (storage.has(key) ? storage.get(key) : null),
            setItem: (key, value) => storage.set(key, String(value)),
            removeItem: (key) => storage.delete(key)
        },
        fetch: async (url, options = {}) => {
            fetchCalls.push({ url, options });
            if (url.startsWith('/api/topics?')) {
                const query = new URLSearchParams(url.split('?')[1] || '').get('q') || '';
                return responses.listTopics(query);
            }
            if (url.startsWith('/api/topics/')) {
                return responses.getTopic(url.split('/').pop());
            }
            if (url === '/api/posts' && (options.method || 'GET') === 'POST') {
                return responses.createPost();
            }
            return { ok: false, status: 404, text: async () => '' };
        },
        bootstrap: undefined
    };
    window.window = window;

    const form = new MockElement('FORM', document);
    form.id = 'create-post-form';
    form.setAttribute('data-initial-topic-id', initialTopicId);
    const content = new MockElement('TEXTAREA', document);
    content.id = 'postContent';
    const communityId = new MockElement('INPUT', document);
    communityId.id = 'post-community-id';
    communityId.value = '';
    const visibility = new MockElement('SELECT', document);
    visibility.id = 'visibility';
    visibility.value = 'PUBLIC';
    const allowReply = new MockElement('INPUT', document);
    allowReply.id = 'allowReply';
    allowReply.checked = true;
    const allowQuote = new MockElement('INPUT', document);
    allowQuote.id = 'allowQuote';
    allowQuote.checked = true;
    const submit = new MockElement('BUTTON', document);
    submit.id = 'btn-create-post';
    submit.type = 'button';
    submit.setAttribute('data-action', 'create-post');
    const charCount = new MockElement('DIV', document);
    charCount.id = 'char-count';
    const status = new MockElement('DIV', document);
    status.id = 'create-post-status';
    const selectedTopics = new MockElement('DIV', document);
    selectedTopics.id = 'selected-topics';
    const topicSearch = new MockElement('INPUT', document);
    topicSearch.id = 'topic-search';
    const topicResults = new MockElement('DIV', document);
    topicResults.id = 'topic-results';
    topicResults.className = 'd-none';
    const topicStatus = new MockElement('DIV', document);
    topicStatus.id = 'topic-picker-status';
    const topicCount = new MockElement('SPAN', document);
    topicCount.id = 'topic-selection-count';
    const topicRetry = new MockElement('BUTTON', document);
    topicRetry.id = 'topic-retry';
    topicRetry.className = 'd-none';

    for (const element of [
        content, communityId, visibility, allowReply, allowQuote, submit, charCount, status,
        selectedTopics, topicSearch, topicResults, topicStatus, topicCount, topicRetry
    ]) {
        form.appendChild(element);
    }
    document.body.appendChild(form);

    vm.runInContext(postComposerSource, vm.createContext(window));

    return {
        window,
        document,
        form,
        content,
        submit,
        status,
        selectedTopics,
        topicSearch,
        topicResults,
        topicStatus,
        topicCount,
        topicRetry,
        fetchCalls,
        storage
    };
}

async function focusSearch(env) {
    env.topicSearch.dispatchEvent({ type: 'focus' });
    await sleep(20);
}

async function enterSearch(env, value) {
    env.topicSearch.value = value;
    env.topicSearch.dispatchEvent({ type: 'input' });
    await sleep(300);
}

async function submit(env) {
    env.content.value = 'Bài viết có gắn chủ đề học tập.';
    env.form.dispatchEvent({
        type: 'submit',
        preventDefault() { this.defaultPrevented = true; }
    });
    await sleep(60);
}

function postBodies(env) {
    return env.fetchCalls
        .filter((call) => call.url === '/api/posts' && (call.options.method || 'GET') === 'POST')
        .map((call) => JSON.parse(call.options.body));
}

describe('Topic composer — template contract', () => {
    it('renders the topic picker in the shared composer form', () => {
        for (const id of ['post-topic-picker', 'topic-search', 'selected-topics', 'topic-results', 'topic-picker-status', 'topic-selection-count', 'topic-retry']) {
            assert.ok(composerTemplate.includes(`id="${id}"`), `#${id} must exist in the composer`);
        }
        assert.ok(composerTemplate.includes('id="post-topic-picker"'), 'topic picker is present');
        assert.ok(!composerTemplate.includes('${param.'), 'avoid Thymeleaf protected param access in attributes');
        assert.ok(!composerTemplate.includes('name="topicIds"'), 'topicIds are sent in the JSON payload, not as form fields');
    });

    it('links the topic page create buttons with the topic id prefill', () => {
        const matches = topicDetailTemplate.match(/@\{\/post\/create\(topicId=\$\{topic\.id\}\)\}/g) || [];
        assert.equal(matches.length, 2, 'both topic-page create-entry links should preselect the topic');
        assert.ok(!topicDetailTemplate.includes('data-action="navigate-create-post"'),
            'topic links must navigate to the prefilled page instead of opening the context-free composer modal');
        assert.ok(topicDetailTemplate.includes('bài viết trên trang này'),
            'the visible count must not claim to be the topic total when PageMeta is unavailable');
    });

    it('builds post-card topic links through URL-aware Thymeleaf expressions', () => {
        assert.ok(postCardTemplate.includes('@{/topics/{slug}(slug=${topic.slug})}'), 'topic slugs must use the canonical route');
        assert.ok(postCardTemplate.includes('@{/search(q=${topic.name})}'), 'fallback search links must be URL-encoded');
        assert.ok(!postCardTemplate.includes("'/search?q=' + topic.name"), 'raw concatenated search URLs must not return');
    });
});

describe('Topic composer — search and selection', () => {
    const topics = [
        { id: 1, name: 'Học tập', slug: 'hoc-tap' },
        { id: 2, name: 'Nghiên cứu', slug: 'nghien-cuu' },
        { id: 3, name: 'UTE Connect', slug: 'ute-connect' }
    ];

    it('loads topics on focus and filters them by the typed query', async () => {
        const env = createEnvironment({ topics });
        await focusSearch(env);
        assert.equal(env.topicResults.children.length, 3, 'all seed topics listed');
        assert.equal(env.fetchCalls.filter((call) => call.url.startsWith('/api/topics?')).length, 1);

        await enterSearch(env, 'nghiên');
        assert.equal(env.topicResults.children.length, 1, 'only matching topic remains');
        assert.ok(env.topicResults.children[0].textContent.includes('#Nghiên cứu'));
    });

    it('selects a topic, shows a removable chip and hides it from results', async () => {
        const env = createEnvironment({ topics });
        await focusSearch(env);
        env.topicResults.children[0].click();

        const chip = querySelector(env.selectedTopics, '[data-topic-chip="1"]');
        assert.ok(chip, 'selected chip rendered');
        assert.ok(chip.textContent.includes('#Học tập'));
        assert.equal(env.topicCount.textContent, 'Đã chọn 1 chủ đề');
        assert.ok(env.topicResults.classList.contains('d-none'), 'result list closes after selection');
        assert.ok(env.topicStatus.textContent.includes('Đã gắn chủ đề'), 'selection feedback shown');
    });

    it('deduplicates repeated topic ids', async () => {
        const env = createEnvironment({ topics: [topics[0], { ...topics[0] }] });
        await focusSearch(env);
        const [first, second] = env.topicResults.children;
        first.click();
        second.click();

        const chips = env.selectedTopics.children.filter((node) => node.hasAttribute('data-topic-chip'));
        assert.equal(chips.length, 1, 'one chip per topic id');
        assert.equal(env.topicCount.textContent, 'Đã chọn 1 chủ đề');
    });

    it('removes a selected topic from the chip list', async () => {
        const env = createEnvironment({ topics });
        await focusSearch(env);
        env.topicResults.children[0].click();
        const chip = querySelector(env.selectedTopics, '[data-topic-chip="1"]');
        const remove = querySelector(chip, '[data-topic-remove="1"]');
        remove.click();

        assert.equal(querySelector(env.selectedTopics, '[data-topic-chip="1"]'), null);
        assert.equal(env.topicCount.textContent, 'Chưa chọn chủ đề');
    });

    it('shows an empty state when no topic matches', async () => {
        const env = createEnvironment({ topics });
        await focusSearch(env);
        await enterSearch(env, 'không-tồn-tại');
        assert.ok(env.topicStatus.textContent.includes('Không tìm thấy chủ đề'));
        assert.ok(env.topicResults.classList.contains('d-none'));
    });

    it('shows a retry affordance when the topic API fails, then recovers', async () => {
        const env = createEnvironment({ topics, topicFailure: true });
        await focusSearch(env);
        assert.ok(env.topicStatus.textContent.includes('Không thể tải danh sách chủ đề'));
        assert.ok(!env.topicRetry.classList.contains('d-none'), 'retry visible');

        env.window.fetch = async (url, options) => {
            env.fetchCalls.push({ url, options });
            return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: topics }) };
        };
        env.topicRetry.click();
        await sleep(20);
        assert.equal(env.topicResults.children.length, 3, 'retry loads results');
        assert.ok(env.topicRetry.classList.contains('d-none'), 'retry hidden after success');
    });

    it('never submits the post when Enter is pressed in the topic search', async () => {
        const env = createEnvironment({ topics });
        await focusSearch(env);
        const before = env.fetchCalls.filter((call) => call.url === '/api/posts').length;

        env.topicSearch.value = 'Học';
        env.topicSearch.dispatchEvent({
            type: 'keydown',
            key: 'Enter',
            preventDefault() { this.defaultPrevented = true; }
        });
        await sleep(20);

        assert.equal(env.fetchCalls.filter((call) => call.url === '/api/posts').length, before, 'no post request');
        assert.ok(env.status.textContent === '', 'no form-level status was triggered');
    });
});

describe('Topic composer — payload, prefill and drafts', () => {
    const topics = [
        { id: 1, name: 'Học tập', slug: 'hoc-tap' },
        { id: 2, name: 'Nghiên cứu', slug: 'nghien-cuu' }
    ];

    it('sends deduplicated numeric topicIds in CreatePostRequest', async () => {
        const env = createEnvironment({ topics });
        await focusSearch(env);
        env.topicResults.children[0].click();
        await submit(env);

        const bodies = postBodies(env);
        assert.equal(bodies.length, 1, 'one post request');
        assert.deepEqual(bodies[0].topicIds, [1]);
    });

    it('omits topicIds when no topic is selected', async () => {
        const env = createEnvironment({ topics });
        await submit(env);
        const bodies = postBodies(env);
        assert.equal(bodies.length, 1);
        assert.equal('topicIds' in bodies[0], false, 'topicIds must not be sent empty');
    });

    it('removes topicIds after the chip is removed', async () => {
        const env = createEnvironment({ topics });
        await focusSearch(env);
        env.topicResults.children[0].click();
        const chip = querySelector(env.selectedTopics, '[data-topic-chip="1"]');
        querySelector(chip, '[data-topic-remove="1"]').click();
        await submit(env);

        assert.equal('topicIds' in postBodies(env)[0], false);
    });

    it('prefills the topic from ?topicId= on the create page', async () => {
        const env = createEnvironment({ topics, locationSearch: '?topicId=2' });
        await sleep(20);
        assert.ok(querySelector(env.selectedTopics, '[data-topic-chip="2"]'), 'initial topic chip rendered');
        assert.ok(env.fetchCalls.some((call) => call.url === '/api/topics/2'), 'topic detail fetched by id');
    });

    it('restores drafted topics together with drafted content', async () => {
        const env = createEnvironment({
            topics,
            draft: {
                content: 'Bản nháp có chủ đề',
                visibility: 'PUBLIC',
                allowReply: true,
                allowQuote: true,
                topics: [{ id: 1, name: 'Học tập', slug: 'hoc-tap' }],
                savedAt: Date.now()
            }
        });
        await sleep(20);

        assert.equal(env.content.value, 'Bản nháp có chủ đề', 'draft content restored');
        assert.ok(querySelector(env.selectedTopics, '[data-topic-chip="1"]'), 'draft topic chip restored');
    });
});
