/**
 * AloUTE — Community post header accessibility
 *
 * The community-name link above a post is rendered at 0.8rem, which left its
 * pointer target under the WCAG 2.2 AA 24×24 minimum (axe target-size).
 * Guards the enlarged hit area so feed pages stay axe-clean.
 */

import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '../..');
const appCss = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/static/css/app.css'),
    'utf-8'
);

describe('Community post header — target size', () => {
    it('gives the community-name link a 24px minimum hit area', () => {
        const rule = appCss.match(/\.post-community-name\s*\{[^}]*\}/);
        assert.ok(rule, '.post-community-name rule present');
        assert.ok(rule[0].includes('display: inline-flex'), 'link keeps inline layout');
        assert.ok(rule[0].includes('min-height: 24px'), 'link meets the WCAG 2.2 AA 24px minimum');
        assert.ok(rule[0].includes('align-items: center'), 'label stays vertically centred');
    });

    it('keeps the link reachable and labelled in the post card', () => {
        const card = fs.readFileSync(
            path.resolve(repoRoot, 'src/main/resources/templates/post/_post-card.html'),
            'utf-8'
        );
        assert.ok(card.includes('class="post-community-name text-decoration-none"'), 'community link uses the sized class');
        assert.ok(card.includes('title="Xem cộng đồng"'), 'community link has a descriptive title');
    });
});
