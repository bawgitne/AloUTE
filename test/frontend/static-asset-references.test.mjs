/**
 * AloUTE — Static asset reference integrity (JS-referenced /images/ paths)
 *
 * JS files reference fallback images by string literal (e.g. the web-push
 * notification icon). A dead path like the historical /images/default-avatar.png
 * 404s silently: notification icons fail to render without any console error.
 * This test scans every JS file served from /static for quoted '/images/...'
 * literals and asserts each target exists on disk under static/images.
 */

import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '../..');
const staticDir = path.resolve(repoRoot, 'src/main/resources/static');

function walkJsFiles(dir) {
    const out = [];
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) out.push(...walkJsFiles(full));
        else if (entry.isFile() && entry.name.endsWith('.js')) out.push(full);
    }
    return out;
}

function referencedImagePaths(js) {
    // quoted string literals starting with /images/ (single, double or backtick)
    const matches = js.match(/['"`](\/images\/[^'"`\s]+)['"`]/g) || [];
    return matches.map(m => m.slice(1, -1));
}

describe('static asset references from JS', () => {
    it('every /images/... path referenced from JS exists under static/images', () => {
        const jsFiles = walkJsFiles(staticDir);
        assert.ok(jsFiles.length > 0, 'expected JS files under static/');
        const missing = [];
        const checked = new Map();

        for (const file of jsFiles) {
            const rel = path.relative(repoRoot, file).split(path.sep).join('/');
            const refs = referencedImagePaths(fs.readFileSync(file, 'utf-8'));
            for (const ref of refs) {
                if (!checked.has(ref)) {
                    const target = path.join(staticDir, ref.replace(/^\//, ''));
                    checked.set(ref, fs.existsSync(target));
                }
                if (!checked.get(ref)) missing.push(`${rel} -> ${ref}`);
            }
        }

        assert.deepStrictEqual(missing, [],
            `JS files reference images that do not exist on disk: ${missing.join(', ')}`);
        // sanity: the historical offender must not silently come back
        assert.ok(checked.has('/images/avatar-fallback.svg'),
            'expected at least one /images/avatar-fallback.svg reference (web-push notification icon)');
    });

    it('web-push notification icons point at an existing asset in both foreground and service-worker paths', () => {
        const fg = fs.readFileSync(
            path.resolve(staticDir, 'js/features/fcm-notification.js'), 'utf-8');
        const sw = fs.readFileSync(
            path.resolve(staticDir, 'firebase-messaging-sw.js'), 'utf-8');

        for (const [name, source] of [['fcm-notification.js', fg], ['firebase-messaging-sw.js', sw]]) {
            const iconMatch = source.match(/icon:\s*['"`]([^'"`]+)['"`]/);
            assert.ok(iconMatch, `${name} must set a notification icon`);
            const target = path.join(staticDir, iconMatch[1].replace(/^\//, ''));
            assert.ok(fs.existsSync(target),
                `${name} notification icon ${iconMatch[1]} must exist on disk`);
        }
    });
});
