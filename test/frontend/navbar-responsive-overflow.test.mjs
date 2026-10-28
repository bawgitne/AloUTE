/**
 * AloUTE — Navbar Responsive Overflow Regression Tests
 *
 * The global navbar needs ~410px of content (hamburger + brand + search on the left,
 * create post + messages + notifications + account on the right). On viewports below
 * ~420px that row no longer fits and the right-hand controls were pushed past the
 * right edge of the viewport:
 *
 *   320px -> 90px past the edge   360px -> 50px
 *   375px -> 35px past the edge   390px -> 21px
 *
 * The page body already hides horizontal overflow, so those controls were clipped
 * rather than scrolled: at 320px the account dropdown button sat at x=357..410 and
 * was entirely outside the 320px viewport, leaving the account menu unreachable.
 *
 * The fix keeps every control reachable and instead removes the parts of the row that
 * are redundant on small screens, so the row fits from 320px upwards.
 *
 * These tests lock that contract so the wide navbar cannot be reintroduced.
 *
 * Run: node --test test/frontend/navbar-responsive-overflow.test.mjs
 */

import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '../..');

const navbarTemplate = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/templates/fragments/navbar.html'), 'utf-8');
const appCss = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/static/css/app.css'), 'utf-8');

/**
 * Return the enclosing `@media (max-width: X)` block of the occurrence of `marker`.
 * When `marker` occurs several times, the first occurrence that actually lives inside a
 * max-width media query wins.
 */
function mediaBlock(marker) {
    let searchFrom = 0;
    while (true) {
        const markerIndex = appCss.indexOf(marker, searchFrom);
        assert.ok(markerIndex > -1, `app.css must contain "${marker}"`);

        const before = appCss.slice(0, markerIndex);
        const open = before.lastIndexOf('@media');
        if (open > -1) {
            const header = appCss.slice(open, appCss.indexOf('{', open));
            const maxWidth = /max-width:\s*([\d.]+)px/.exec(header);
            if (maxWidth) {
                let depth = 0;
                let i = appCss.indexOf('{', open);
                const start = i;
                for (; i < appCss.length; i++) {
                    if (appCss[i] === '{') depth++;
                    else if (appCss[i] === '}') {
                        depth--;
                        if (depth === 0) break;
                    }
                }
                return { maxWidth: Number(maxWidth[1]), body: appCss.slice(start, i + 1) };
            }
        }
        searchFrom = markerIndex + marker.length;
    }
}

/** Return the body of the `@media (max-width: maxWidth)` block, located by its header. */
function mediaBlockByMaxWidth(maxWidth) {
    const header = '@media (max-width: ' + maxWidth + 'px)';
    const open = appCss.indexOf(header);
    assert.ok(open > -1, `app.css must contain "${header}"`);

    let depth = 0;
    let i = appCss.indexOf('{', open);
    const start = i;
    for (; i < appCss.length; i++) {
        if (appCss[i] === '{') depth++;
        else if (appCss[i] === '}') {
            depth--;
            if (depth === 0) break;
        }
    }
    return { maxWidth, body: appCss.slice(start, i + 1) };
}

/** Return the CSS rule body that targets `selector` inside the given media block. */
function ruleFor(block, selector) {
    const rule = new RegExp('[^}]*' + selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\s*\\{[^}]*\\}').exec(block.body);
    return rule ? rule[0] : null;
}

describe('AloUTE navbar responsive overflow contract', () => {
    describe('Navbar template', () => {
        it('marks the brand wordmark and the account chevron so the stylesheet can target them', () => {
            assert.ok(
                /<span class="[^"]*aloute-navbar-wordmark[^"]*">Alo<span class="text-primary">UTE<\/span><\/span>/.test(navbarTemplate),
                'the brand wordmark must carry the aloute-navbar-wordmark marker class'
            );
            assert.ok(
                /<i class="[^"]*aloute-user-dropdown-chevron[^"]*" aria-hidden="true"><\/i>/.test(navbarTemplate),
                'the account dropdown chevron must carry the aloute-user-dropdown-chevron marker class'
            );
        });

        it('keeps the brand link, its logo alt text and the account button label', () => {
            assert.ok(/<a class="navbar-brand[^"]*"[^>]*href="\/feed"/.test(navbarTemplate),
                'the brand must stay a link to /feed');
            assert.ok(/alt="AloUTE"/.test(navbarTemplate),
                'the brand logo must keep alt="AloUTE" so the brand keeps its accessible name');
            const accountButton = /id="userNavbarDropdown"[\s\S]{0,400}/.exec(navbarTemplate)?.[0] || '';
            assert.ok(
                accountButton.includes('aria-label="Menu tài khoản"')
                || accountButton.includes(`th:attr="aria-label=\${'Menu tài khoản của '`),
                'the account dropdown button must keep an accessible label');
        });

        it('keeps every navbar control hook the mobile layout relies on', () => {
            for (const hook of [
                'aloute-nav-toggle-btn',
                'aloute-mobile-search-btn',
                'navbarNotificationDropdown',
                'userNavbarDropdown',
                'data-action="logout"',
                'data-bs-target="#mobileNavOffcanvas"',
                'th:action="@{/search}"'
            ]) {
                assert.ok(navbarTemplate.includes(hook), `navbar.html must keep "${hook}"`);
            }
        });
    });

    describe('Stylesheet', () => {
        it('hides the redundant account chevron on small screens', () => {
            const block = mediaBlock('.aloute-user-dropdown-chevron');
            assert.ok(block.maxWidth <= 575.98,
                `the account chevron rule must apply to small screens, got max-width ${block.maxWidth}px`);
            const rule = ruleFor(block, '.aloute-navbar .aloute-user-dropdown-chevron');
            assert.ok(rule, 'the account chevron rule must exist inside the small-screen media query');
            assert.ok(/display:\s*none/.test(rule),
                'the account chevron itself must be hidden on small screens');
        });

        it('hides the decorative Campus badge on the small-tablet band', () => {
            // The badge is already hidden by a pre-existing rule inside the 575.98px block.
            // This asserts the ADDITIONAL rule that covers the 576-639.98px band, where the
            // baseline navbar was still 30px too wide.
            const block = mediaBlockByMaxWidth(639.98);
            const rule = ruleFor(block, '.aloute-navbar .badge-brand');
            assert.ok(rule, 'the 640px badge rule must exist inside the 639.98px media query');
            assert.ok(/display:\s*none/.test(rule),
                'the Campus badge must be hidden below 640px');
        });

        it('drops the brand wordmark and tightens the navbar padding on very narrow screens', () => {
            const block = mediaBlock('.aloute-navbar-wordmark');
            assert.ok(block.maxWidth <= 419.98,
                `the wordmark rule must apply below 420px, got max-width ${block.maxWidth}px`);
            const rule = ruleFor(block, '.aloute-navbar .aloute-navbar-wordmark');
            assert.ok(rule, 'the wordmark rule must exist inside the narrow-screen media query');
            assert.ok(/display:\s*none/.test(rule),
                'the brand wordmark must be hidden below 420px');
            assert.ok(/padding-left:\s*0\.25rem/.test(block.body) && /padding-right:\s*0\.25rem/.test(block.body),
                'the navbar padding must be tightened below 420px');
        });

        it('lets the navbar search box shrink instead of forcing the row wider than the viewport', () => {
            const block = mediaBlock('.aloute-navbar .aloute-search-box');
            assert.ok(block.maxWidth <= 767.98,
                `the search box rule must stay inside the tablet band, got max-width ${block.maxWidth}px`);
            const rule = ruleFor(block, '.aloute-navbar .aloute-search-box');
            assert.ok(rule, 'the search box rule must exist inside the tablet media query');
            assert.ok(/min-width:\s*0/.test(rule),
                'the search box itself must be allowed to shrink');
            assert.ok(/flex:\s*1 1 180px/.test(rule),
                'the search box must keep a 180px preferred width');
        });

        it('does not clip the navbar row itself instead of making it fit', () => {
            // The page body already carries a pre-existing `overflow-x: hidden` rule, so a
            // navbar row that is still too wide is silently cut off instead of scrolling.
            // No navbar rule may hide its own overflow: the row has to fit the viewport.
            const navbarRules = appCss.match(/\.aloute-navbar[^{}]*\{[^}]*\}/g) || [];
            assert.ok(navbarRules.length > 0, 'the navbar must still have CSS rules');
            for (const rule of navbarRules) {
                assert.ok(!/overflow(-x)?:\s*hidden/.test(rule),
                    `the navbar must not clip its own overflow: ${rule}`);
            }
        });

        it('keeps the 44px touch target floor for the mobile icon buttons', () => {
            const block = mediaBlock('.aloute-navbar .navbar-notification-link');
            assert.ok(block.maxWidth <= 575.98,
                `the mobile icon button rules must apply below 576px, got max-width ${block.maxWidth}px`);
            assert.ok(/min-width:\s*44px/.test(block.body) && /min-height:\s*44px/.test(block.body),
                'the mobile navbar icon buttons must keep their 44px touch target');
        });
    });
});
