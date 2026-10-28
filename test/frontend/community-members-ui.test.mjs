/**
 * AloUTE — Community member management UI contract
 *
 * Guards the fixes for /communities/{id}/manage/members:
 * - the member table collapses into cards on phones so the action column is
 *   not clipped at 360px (feature CSS is scoped to .community-member-table)
 * - remove buttons expose an accessible name that includes the member name
 * - owner-only role controls keep a member-specific accessible label
 * - pagination keeps a URL-encoded search query instead of concatenating it raw
 * - owners may remove admins; community admins still may not
 */

import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '../..');
const template = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/templates/community/manage/members.html'),
    'utf-8'
);
const css = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/static/css/features/community.css'),
    'utf-8'
);
const manageJs = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/static/js/features/community/community-manage.js'),
    'utf-8'
);

describe('Community members — responsive action column', () => {
    it('loads the scoped community stylesheet', () => {
        assert.ok(template.includes('@{/css/features/community.css}'), 'members page links the community CSS');
    });

    it('collapses the member table at phone widths', () => {
        assert.ok(css.includes('@media (max-width: 575.98px)'), 'phone breakpoint present');
        for (const selector of ['.community-member-table thead', '.community-member-table tr', '.community-member-table td']) {
            assert.ok(css.includes(selector), `${selector} participates in the card layout`);
        }
        assert.ok(css.includes('.community-member-table td:last-child .btn'), 'action button is sized for the card layout');
        assert.ok(!css.includes('body {'), 'community CSS must not leak into global element selectors');
    });
});

describe('Community members — accessibility', () => {
    it('names each remove button after the member it removes', () => {
        assert.ok(template.includes("aria-label=${'Xóa ' +"), 'remove button has a dynamic aria-label');
        assert.ok(template.includes("' khỏi cộng đồng'"), 'aria-label explains the destructive action');
        assert.ok(template.includes('<span>Xóa</span>'), 'the visible action label stays on phones');
    });

    it('names each role select after the member it changes', () => {
        assert.ok(template.includes("th:text=\"'Đổi vai trò của ' +"), 'role label includes the member name');
    });

    it('keeps the action controls reachable from the keyboard', () => {
        assert.ok(template.includes('type="button"'), 'remove control is a native button');
        assert.ok(template.includes('<select class="form-select form-select-sm w-auto community-role-select"'), 'role control is a native select');
    });
});

describe('Community members — search and pagination', () => {
    it('encodes the search query in pagination links', () => {
        assert.ok(template.includes('#uris.escapeQueryParam(searchQuery)'), 'q is URL-encoded');
        assert.ok(!template.includes("'&amp;q=' + ${searchQuery}"), 'raw q concatenation must not return');
    });

    it('lets the search controls wrap on narrow screens', () => {
        const form = template.match(/<form class="aloute-card[^"]*"[^>]*role="search"/);
        assert.ok(form, 'search form present');
        assert.ok(form[0].includes('flex-wrap'), 'search form wraps instead of overflowing');
        assert.ok(form[0].includes('flex-grow-1') || template.includes('input-group-sm flex-grow-1'), 'search input takes the free space');
    });
});

describe('Community members — error handling', () => {
    it('maps session-expiry and permission errors to actionable Vietnamese messages', () => {
        assert.ok(manageJs.includes("'AUTH_UNAUTHENTICATED': 'Phiên đăng nhập đã hết hạn"), '401 unauthenticated message');
        assert.ok(manageJs.includes("'AUTH_TOKEN_EXPIRED': 'Phiên đăng nhập đã hết hạn"), '401 expired-token message');
        assert.ok(manageJs.includes("'AUTH_FORBIDDEN':"), '403 auth-forbidden message');
        assert.ok(manageJs.includes("'COMMUNITY_MEMBER_NOT_FOUND':"), '404 member-not-found message');
        assert.ok(manageJs.includes("'COMMUNITY_ROLE_CHANGE_NOT_ALLOWED':"), 'role-change denial message');
    });
});

describe('Community members — permission presentation', () => {
    it('shows role changes to the owner only', () => {
        assert.ok(template.includes('th:if="${isOwner and !isOwnerRow}"'), 'role selector is owner-only');
    });

    it('hides destructive controls for the owner row', () => {
        assert.ok(template.includes('th:if="${!isOwnerRow and (isOwner or member.role == null or member.role.name() != \'ADMIN\')}"'),
            'owner row is protected while owners can still remove admins');
    });

    it('keeps the admin-removal restriction visible to non-owner managers', () => {
        assert.ok(template.includes('Chỉ chủ sở hữu cộng đồng mới được thay đổi vai trò và xóa quản trị viên.'),
            'non-owner managers are told which actions are owner-only');
    });
});
