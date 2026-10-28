/**
 * AloUTE — Guest UX, accessibility and asset-integrity regression tests (Quan 2026-10-27)
 *
 * Locks the frontend-only hardening for audit findings BL-17, BL-20, BL-21, BL-23
 * and BL-25. These are static contract tests: the browser-level verification lives
 * in test-results/quan-frontend-hardening (Playwright harness), not here.
 *
 * Run: node --test test/frontend/guest-ux-accessibility.test.mjs
 */

import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '../..');
const read = rel => fs.readFileSync(path.resolve(repoRoot, rel), 'utf-8');

const navbar = read('src/main/resources/templates/fragments/navbar.html');
const sidebar = read('src/main/resources/templates/fragments/sidebar.html');
const layout = read('src/main/resources/templates/layouts/default.html');
const singleLayout = read('src/main/resources/templates/layouts/single.html');
const feed = read('src/main/resources/templates/feed/index.html');
const postDetail = read('src/main/resources/templates/post/detail.html');
const postCard = read('src/main/resources/templates/post/_post-card.html');
const interactionBar = read('src/main/resources/templates/post/_interaction-bar.html');
const topicDetail = read('src/main/resources/templates/topic/detail.html');
const communityDetail = read('src/main/resources/templates/community/detail.html');
const pageActions = read('src/main/resources/static/js/page-actions.js');
const modal = read('src/main/resources/templates/fragments/modal.html');
const search = read('src/main/resources/templates/search/index.html');
const appCss = read('src/main/resources/static/css/app.css');
const applicationYml = read('src/main/resources/application.yml');

function cssRule(selector) {
    const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const match = new RegExp(escaped + '\\s*\\{[^}]*\\}').exec(appCss);
    return match ? match[0] : null;
}

function enclosingMedia(selector) {
    const idx = appCss.indexOf(selector);
    if (idx < 0) return null;
    const mediaIdx = appCss.lastIndexOf('@media', idx);
    if (mediaIdx < 0) return null;
    const open = appCss.indexOf('{', mediaIdx);
    let depth = 0;
    let end = open;
    for (; end < appCss.length; end += 1) {
        if (appCss[end] === '{') depth += 1;
        else if (appCss[end] === '}') {
            depth -= 1;
            if (depth === 0) break;
        }
    }
    if (idx > end) return null;
    return { header: appCss.slice(mediaIdx, open), body: appCss.slice(open, end + 1) };
}

describe('BL-20 guest auth links keep a real href fallback', () => {
    it('navbar guest actions point at /login and /register', () => {
        assert.match(navbar, /href="\/login"[^>]*data-action="navigate-login"/);
        assert.match(navbar, /href="\/register"[^>]*data-action="navigate-register"/);
    });

    it('sidebar welcome actions point at /login and /register', () => {
        assert.match(sidebar, /href="\/login"[^>]*data-action="navigate-login"/);
        assert.match(sidebar, /href="\/register"[^>]*data-action="navigate-register"/);
    });

    it('no auth action still uses the dead href="#" fallback', () => {
        for (const [name, template] of [['navbar.html', navbar], ['sidebar.html', sidebar]]) {
            assert.ok(!/href="#"[\s\S]{0,120}data-action="navigate-(login|register)"/.test(template),
                `${name} must not use href="#" for login/register`);
        }
    });

    it('guest sidebar profile shortcut falls back to /login', () => {
        assert.match(sidebar, /:\s*@\{\/login\}/);
    });
});

describe('BL-23 skip link and page headings', () => {
    it('layout exposes a skip link as the first focusable element', () => {
        assert.match(layout, /class="aloute-skip-link" href="#main-content"/);
        const skipIndex = layout.indexOf('aloute-skip-link');
        const headerIndex = layout.indexOf('<header');
        assert.ok(skipIndex > -1 && skipIndex < headerIndex,
            'the skip link must appear before the global header');
    });

    it('skip link target exists and is focusable', () => {
        assert.match(layout, /<main[^>]*id="main-content"[^>]*tabindex="-1"/);
    });

    it('single-column layout (settings) also exposes the skip link', () => {
        assert.match(singleLayout, /class="aloute-skip-link" href="#main-content"/);
        const skipIndex = singleLayout.indexOf('aloute-skip-link');
        const headerIndex = singleLayout.indexOf('<header');
        assert.ok(skipIndex > -1 && skipIndex < headerIndex,
            'the skip link must appear before the global header');
        assert.match(singleLayout, /<main[^>]*id="main-content"[^>]*tabindex="-1"/);
    });

    it('skip link styling is token-based and appears on focus', () => {
        const rule = cssRule('.aloute-skip-link');
        assert.ok(rule, 'app.css must style .aloute-skip-link');
        assert.match(rule, /position:\s*fixed/);
        assert.match(rule, /var\(--aloute-accent\)/);
        assert.match(appCss, /\.aloute-skip-link:focus[^{]*\{[^}]*top:\s*var\(--aloute-space-sm\)/);
    });

    it('search page has exactly one h1', () => {
        const h1s = search.match(/<h1\b/g) || [];
        assert.equal(h1s.length, 1, 'search/index.html must contain exactly one h1');
        assert.match(search, /<h1 class="visually-hidden">Tìm kiếm trên AloUTE<\/h1>/);
    });
});

describe('BL-21 guest interaction surfaces are gated in presentation', () => {
    it('feed composer is only rendered for authenticated users', () => {
        assert.match(feed, /<section th:if="\$\{currentUser != null\}" class="composer-box"/);
    });

    it('guests get a login CTA instead of the feed composer', () => {
        const guestBlock = feed.slice(feed.indexOf('th:if="${currentUser == null}"'));
        assert.match(guestBlock, /href="\/login"/);
        assert.match(guestBlock, /href="\/register"/);
        assert.ok(!/composer-text-block/.test(guestBlock.slice(0, 1200)),
            'the guest CTA must not duplicate the composer form');
    });

    it('post reply form is only rendered for authenticated users', () => {
        assert.match(postDetail, /<div th:if="\$\{post\.allowReply != false[^"]*and currentUser != null\}" class="mb-4">/);
        assert.match(postDetail, /currentUser == null[\s\S]{0,1200}href="\/login"/,
            'guests must see a login prompt in place of the reply form');
    });

    it('post action dropdown (edit/delete/report) is hidden from guests', () => {
        assert.match(postCard, /<div th:if="\$\{post\.id != null and currentUser != null\}" class="dropdown">/);
    });

    it('quote modal trigger is hidden from guests', () => {
        assert.match(interactionBar, /<button th:if="\$\{currentUser != null and post\.allowQuote == true/);
    });

    it('navbar create action and global composer modal are authenticated-only', () => {
        const createLinks = navbar.match(/<a[^>]*data-action="navigate-create-post"[^>]*>/g) || [];
        assert.equal(createLinks.length, 2, 'navbar must have exactly two create-post actions');
        for (const link of createLinks) {
            assert.match(link, /currentUser != null/, 'create-post action must require currentUser');
        }
        assert.match(modal, /<div th:if="\$\{currentUser != null and activeNav != 'create-post'\}"/);
    });

    it('write actions on public topic and community pages are authenticated-only', () => {
        // /topics/** is a public whitelist route and TopicPageController leaves
        // currentUser null for guests, so both entries must be presentation-gated.
        // Topic entries navigate to the server-rendered composer with ?topicId=
        // so the modal cannot drop the context; they therefore must not carry
        // the global navigate-create-post action.
        const createLinks = topicDetail.match(/<a[^>]*th:href="@\{[^"]*topicId[^"]*"[^>]*>/g) || [];
        assert.equal(createLinks.length, 2, 'topic detail must keep exactly two create-post entries');
        for (const link of createLinks) {
            assert.match(link, /th:if="\$\{currentUser != null\}"/, 'create-post action must require currentUser');
            assert.match(link, /aloute-auth-cta/, 'create-post action must keep the 44px CTA class');
            assert.doesNotMatch(link, /data-action="navigate-create-post"/,
                'topic entries must navigate to the prefilled composer instead of opening the modal');
        }
        assert.match(topicDetail, /currentUser == null[^>]*href="\/login"[^>]*aloute-auth-cta/,
            'guests must get a 44px login CTA in the empty state');

        const communityCreate = communityDetail.match(/<a[^>]*data-action="navigate-create-post"[^>]*>/g) || [];
        assert.equal(communityCreate.length, 1, 'community detail must keep one create-post entry');
        assert.match(communityCreate[0], /th:if="\$\{currentUser != null\}"/,
            'community create-post action must require currentUser');
        assert.match(communityCreate[0], /aloute-auth-cta/,
            'community create-post action must keep the 44px CTA class');
        const communityGuestCta = (communityDetail.match(/<a[^>]*data-action="navigate-login"[^>]*>/g) || [])
            .find(tag => /currentUser == null/.test(tag));
        assert.ok(communityGuestCta, 'community guest login CTA must keep the currentUser == null guard');
        assert.match(communityGuestCta, /aloute-auth-cta/,
            'community guest login CTA must keep the 44px CTA class');
        const communityJoinBtn = /<button[^>]*id="btn-join-community"[^>]*>/.exec(communityDetail);
        assert.ok(communityJoinBtn, 'community join action must exist');
        assert.match(communityJoinBtn[0], /aloute-auth-cta/,
            'community join action must keep the 44px CTA class');
        const communityLeaveBtn = /<button[^>]*id="btn-leave-community"[^>]*>/.exec(communityDetail);
        assert.ok(communityLeaveBtn, 'community leave action must exist');
        assert.match(communityLeaveBtn[0], /aloute-auth-cta/,
            'community leave action must keep the 44px CTA class');
        assert.match(pageActions, /className = 'btn btn-sm btn-outline-danger rounded-pill px-3 aloute-auth-cta'/,
            'leave-community JS must keep the 44px CTA class');
        assert.match(pageActions, /className = 'btn btn-sm btn-primary rounded-pill px-3 aloute-auth-cta'/,
            'join-community JS must keep the 44px CTA class');
    });
});

describe('BL-17 touch targets', () => {
    it('navbar controls keep a 44px floor', () => {
        const rule = cssRule('.aloute-navbar .btn');
        assert.ok(rule, 'the navbar control rule must exist');
        assert.match(rule, /min-height:\s*44px/);
    });

    it('interaction bar buttons keep a 44px floor', () => {
        const rule = cssRule('.interaction-btn');
        assert.match(rule, /min-height:\s*44px/);
        assert.match(rule, /min-width:\s*44px/);
    });

    it('post overflow menu, poll vote and reply submit reach 44px', () => {
        assert.match(appCss, /\.post-card-header \.dropdown > \.btn\s*\{[^}]*min-height:\s*44px[^}]*\}/);
        assert.match(appCss, /\.poll-vote-form \.btn-vote-poll,\s*#btn-submit-reply\s*\{\s*min-height:\s*44px/);
    });

    it('search filter tabs reach 44px on mobile', () => {
        const media = enclosingMedia('#search-tab-list .nav-link');
        assert.ok(media, 'the search tab rule must live inside a media query');
        assert.match(media.header, /max-width:\s*575\.98px/);
        assert.match(media.body, /#search-tab-list \.nav-link\s*\{[^}]*min-height:\s*44px/);
    });

    it('guest auth CTAs outside the navbar reach 44px', () => {
        const rule = cssRule('.aloute-auth-cta');
        assert.ok(rule, 'app.css must define .aloute-auth-cta');
        assert.match(rule, /min-height:\s*44px/);
        const expectations = [
            ['feed/index.html', feed, 2],
            ['post/detail.html', postDetail, 1],
            ['sidebar.html', sidebar, 2],
            ['topic/detail.html', topicDetail, 1]
        ];
        for (const [name, template, expected] of expectations) {
            const ctas = template.match(/<a[^>]*href="\/(login|register)"[^>]*>/g) || [];
            assert.equal(ctas.length, expected, `${name} must keep ${expected} auth CTA link(s)`);
            for (const tag of ctas) {
                assert.match(tag, /aloute-auth-cta/, `${name} auth CTA must carry .aloute-auth-cta`);
            }
        }
    });

    it('dense navbar dropdown text actions stay compact', () => {
        assert.match(appCss, /\.aloute-navbar \.dropdown-menu \.btn\s*\{[^}]*min-height:\s*0/);
    });
});

describe('BL-25 design tokens and asset integrity', () => {
    it('Bootstrap Icons stylesheet is pinned with SRI everywhere', () => {
        const templatesDir = path.resolve(repoRoot, 'src/main/resources/templates');
        const files = [];
        const walk = dir => {
            for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
                const full = path.join(dir, entry.name);
                if (entry.isDirectory()) walk(full);
                else if (entry.name.endsWith('.html')) files.push(full);
            }
        };
        walk(templatesDir);
        let links = 0;
        for (const file of files) {
            const html = fs.readFileSync(file, 'utf-8');
            const tags = html.match(/<link\b[^>]*bootstrap-icons[^>]*>/g) || [];
            for (const tag of tags) {
                links += 1;
                const rel = path.relative(repoRoot, file);
                assert.match(tag, /bootstrap-icons@1\.11\.3/, `${rel} must pin the version`);
                assert.match(tag, /integrity="sha384-XGjxtQfXaH2tnPFa9x\+ruJTuLE3Aa6LhHSWRr1XeTyhezb4abCG4ccI5AkVDxqC\+"/, `${rel} must carry SRI`);
                assert.match(tag, /crossorigin="anonymous"/, `${rel} must set crossorigin`);
            }
        }
        assert.equal(links, 2, 'head and admin layouts must each declare Bootstrap Icons');
    });

    it('static asset cache keeps the short safe default', () => {
        // Hardcoded /images/... URLs in JS bypass the content-hash rewriter,
        // so a year-long global cache would serve stale files after a release.
        assert.match(applicationYml, /period:\s*12h/);
        assert.doesNotMatch(applicationYml, /period:\s*365d/);
        assert.match(applicationYml, /strategy:\s*[\s\S]{0,80}content:/);
    });

    it('the new guest/accessibility CSS section introduces no raw hex colors', () => {
        const start = appCss.indexOf('Guest Experience & Accessibility Hardening');
        assert.ok(start > -1, 'the hardening CSS section must exist');
        const section = appCss.slice(start);
        assert.ok(!/#[0-9a-fA-F]{3,8}\b/.test(section),
            'new hardening rules must use design tokens instead of raw hex colors');
    });
});
