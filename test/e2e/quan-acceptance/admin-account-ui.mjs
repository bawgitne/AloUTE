import { chromium } from 'playwright';
import {
  BASE, test, assert, assertEqual, login, guest, open, api, diagnostics,
  screenshot, createRun, writeResults, saveJson, uniqueSuffix
} from './lib/harness.mjs';

const S = 'admin-account-ui';
const tag = uniqueSuffix();
const run = createRun([S]);
const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL || 'chrome', headless: true });
const admin = await login(browser, 'admin');
const target = await login(browser, 'demobinh');
const normal = await login(browser, 'user');
const adminId = admin.me?.id;
const targetId = target.me?.id;
const targetUsername = target.me?.username;

async function csrf(session) {
  return (await session.context.cookies(BASE)).find(c => c.name === 'XSRF-TOKEN')?.value;
}

async function apiWrite(session, method, route, data) {
  const token = await csrf(session);
  return api(session, method, route, data, { headers: token ? { 'X-XSRF-TOKEN': token } : {} });
}

async function openDetail(session, id = targetId) {
  const response = await open(session, `/admin/users/${id}`, { wait: 500 });
  assertEqual(response.status(), 200, `GET /admin/users/${id}`);
  return response;
}

async function openModal(page, action, modalId, expectedFocus) {
  await page.locator(`[data-action="${action}"]`).click();
  await page.locator(`#${modalId}`).waitFor({ state: 'visible' });
  await page.waitForTimeout(600);
  const focused = await page.evaluate(() => document.activeElement?.id || '');
  assertEqual(focused, expectedFocus, `Focus sau khi mở ${modalId}`);
}

async function closeModal(page, modalId) {
  await page.keyboard.press('Escape');
  await page.locator(`#${modalId}`).waitFor({ state: 'hidden', timeout: 5000 });
}

async function waitActionResponse(page, id, action) {
  return page.waitForResponse(r =>
    r.url().endsWith(`/api/admin/users/${id}/${action}`) && r.request().method() === 'POST',
    { timeout: 15000 }
  );
}

try {
  await test(run, S, 'ADM-UI-01', 'Về Admin Dashboard, capabilities và keyboard trên trang chi tiết', async () => {
    const diag = diagnostics(admin.page);
    await openDetail(admin);
    const back = admin.page.locator('#btn-back-admin-dashboard');
    assert(await back.isVisible(), 'Thiếu nút Về Admin Dashboard');
    assertEqual(await back.getAttribute('href'), '/admin', 'href dashboard');
    assertEqual(await back.getAttribute('aria-label'), 'Về Admin Dashboard', 'aria-label');
    await back.focus();
    assertEqual(await admin.page.evaluate(() => document.activeElement?.id), 'btn-back-admin-dashboard', 'Focus back link');
    await admin.page.keyboard.press('Enter');
    await admin.page.waitForURL('**/admin', { timeout: 10000 });
    await openDetail(admin);
    assertEqual(diag.pageErrors.length, 0, `Page error: ${diag.pageErrors.join(' | ')}`);
    await screenshot(admin.page, 'adm-ui-01-back-link');

    await openDetail(admin, adminId);
    assert(await admin.page.locator('#btn-back-admin-dashboard').isVisible(), 'Back link trên self detail');
    assertEqual(await admin.page.locator('[data-action="open-user-warn"]').count(), 0, 'Self không có nút cảnh báo');
    assertEqual(await admin.page.locator('[data-action="open-user-role"]').count(), 0, 'Self không có nút đổi role');
    assertEqual(await admin.page.locator('[data-action="open-user-reset"]').count(), 1, 'Self có nút reset theo capabilities #104');
    assert((await admin.page.locator('body').innerText()).includes('Không thể thực hiện thao tác cảnh báo, đình chỉ hoặc đổi vai trò'), 'Thiếu ghi chú self-action');
    return { adminId, selfResetVisible: true };
  });

  await test(run, S, 'ADM-UI-02', 'Warn qua modal: audit, notification USER_WARN và trạng thái tài khoản không đổi', async () => {
    await openDetail(admin);
    await openModal(admin.page, 'open-user-warn', 'adminUserWarnModal', 'userWarnReasonCode');
    await admin.page.selectOption('#userWarnReasonCode', 'HARASSMENT');
    await admin.page.fill('#userWarnReasonDetail', `Cảnh báo E2E ${tag}: quấy rối trong bình luận.`);
    const responsePromise = waitActionResponse(admin.page, targetId, 'warn');
    const reloadPromise = admin.page.waitForEvent('load', { timeout: 15000 }).catch(() => null);
    await admin.page.click('#btnConfirmUserWarn');
    const response = await responsePromise;
    assertEqual(response.status(), 200, 'POST warn');
    const body = await response.json();
    assertEqual(body?.data?.actionType, 'USER_WARN', 'actionType');
    assert(body?.data?.notificationId, 'Response thiếu notificationId');
    await reloadPromise;
    if (!admin.page.url().includes(`/admin/users/${targetId}`)) await openDetail(admin);
    const history = await admin.page.locator('table[aria-label="Lịch sử kiểm duyệt liên quan đến người dùng"]').innerText();
    assert(history.includes('USER_WARN'), 'Audit USER_WARN không hiển thị');

    const me = await api(target, 'GET', '/api/auth/me');
    assertEqual(me.status, 200, 'Target session phải còn hiệu lực sau warn');
    assertEqual(me.body?.data?.isActive, true, 'Warn không được đình chỉ tài khoản');
    assertEqual(me.body?.data?.role, 'USER', 'Warn không được đổi vai trò');
    const notifications = await api(target, 'GET', '/api/notifications?filter=ALL&page=0&size=50');
    assertEqual(notifications.status, 200, 'GET notifications');
    const found = (notifications.body?.data || []).find(n => n.type === 'USER_WARN' && n.targetId === targetId);
    assert(found, 'Không thấy notification USER_WARN');
    assertEqual(found.actorId, adminId, 'Notification actor phải là admin');
    assert(found.contentPreview && found.contentPreview.length > 0, 'Notification thiếu nội dung');

    await open(target, '/notifications');
    await target.page.waitForTimeout(500);
    const notifText = await target.page.locator('body').innerText();
    assert(/cảnh báo chính thức/i.test(notifText), 'Notification UI không hiển thị nhãn cảnh báo');
    await screenshot(target.page, 'adm-ui-02-notification');
    return { actionId: body.data.actionId, notificationId: body.data.notificationId };
  });

  await test(run, S, 'ADM-UI-03', 'Reset hồ sơ: cảnh báo mất dữ liệu, username confirmation, field scope và session preservation', async () => {
    const seed = await apiWrite(target, 'PATCH', '/api/users/me/profile', {
      displayName: `E2E Target ${tag}`,
      bio: `Bio E2E ${tag}`,
      website: `https://e2e-${tag}.example.test`
    });
    assertEqual(seed.status, 200, 'Seed profile');
    const before = await api(target, 'GET', '/api/users/me/profile');
    assertEqual(before.body?.data?.displayName, `E2E Target ${tag}`, 'Seed displayName');

    await openDetail(admin);
    await openModal(admin.page, 'open-user-reset', 'adminUserResetProfileModal', 'userResetConfirmUsername');
    const modalText = await admin.page.locator('#adminUserResetProfileModal').innerText();
    assert(/mất dữ liệu hiển thị/i.test(modalText), 'Thiếu cảnh báo mất dữ liệu');
    const confirmBtn = admin.page.locator('#btnConfirmUserReset');
    assertEqual(await confirmBtn.isDisabled(), true, 'Nút reset phải disabled trước xác nhận username');
    await admin.page.fill('#userResetConfirmUsername', 'wrong-' + targetUsername);
    await admin.page.selectOption('#userResetReasonCode', 'OTHER');
    await admin.page.fill('#userResetReasonDetail', `Đặt lại hồ sơ E2E ${tag} theo yêu cầu kiểm duyệt.`);
    assertEqual(await confirmBtn.isDisabled(), true, 'Nút reset phải disabled khi username sai');
    await admin.page.fill('#userResetConfirmUsername', targetUsername);
    assertEqual(await confirmBtn.isDisabled(), false, 'Nút reset phải bật khi username đúng');

    const responsePromise = waitActionResponse(admin.page, targetId, 'reset-profile');
    const reloadPromise = admin.page.waitForEvent('load', { timeout: 15000 }).catch(() => null);
    await confirmBtn.click();
    const response = await responsePromise;
    assertEqual(response.status(), 200, 'POST reset-profile');
    const responseBody = await response.json();
    assertEqual(responseBody?.data?.actionType, 'USER_PROFILE_RESET', 'actionType');
    assertEqual(responseBody?.data?.displayName, targetUsername, 'displayName response');
    await reloadPromise;
    if (!admin.page.url().includes(`/admin/users/${targetId}`)) await openDetail(admin);
    const history = await admin.page.locator('table[aria-label="Lịch sử kiểm duyệt liên quan đến người dùng"]').innerText();
    assert(history.includes('USER_PROFILE_RESET'), 'Audit USER_PROFILE_RESET không hiển thị');

    const after = await api(target, 'GET', '/api/users/me/profile');
    assertEqual(after.status, 200, 'Target session phải còn sau reset');
    assertEqual(after.body?.data?.displayName, targetUsername, 'displayName phải về username');
    assertEqual(after.body?.data?.bio, null, 'bio phải bị xóa');
    assertEqual(after.body?.data?.website, null, 'website phải bị xóa');
    assertEqual(after.body?.data?.avatarUrl, null, 'avatarUrl phải bị xóa');
    assertEqual(after.body?.data?.coverUrl, null, 'coverUrl phải bị xóa');
    const me = await api(target, 'GET', '/api/auth/me');
    assertEqual(me.status, 200, 'JWT target không được thu hồi bởi reset');
    assertEqual(me.body?.data?.role, 'USER', 'Reset không được đổi vai trò');
    assertEqual(me.body?.data?.isActive, true, 'Reset không được đình chỉ tài khoản');
    await screenshot(admin.page, 'adm-ui-03-reset-history');
    return { resetFields: ['displayName', 'bio', 'website', 'avatarUrl', 'coverUrl'] };
  });

  await test(run, S, 'ADM-UI-04', 'Role change: promote/demote, JWT revocation, 409 no-op', async () => {
    await openDetail(admin);
    assert((await admin.page.locator('body').innerText()).includes('Vai trò: USER'), 'Thiếu role USER');
    await openModal(admin.page, 'open-user-role', 'adminUserRoleModal', 'userRoleNewRole');
    assert((await admin.page.locator('#userRoleCurrentRole').innerText()).includes('USER'), 'Current role USER');
    await admin.page.selectOption('#userRoleNewRole', 'SYSTEM_ADMIN');
    await admin.page.selectOption('#userRoleReasonCode', 'OTHER');
    await admin.page.fill('#userRoleReasonDetail', `Bổ nhiệm SYSTEM_ADMIN E2E ${tag} theo quyết định quản trị.`);
    const promotePromise = waitActionResponse(admin.page, targetId, 'role');
    const promoteReload = admin.page.waitForEvent('load', { timeout: 15000 }).catch(() => null);
    await admin.page.click('#btnConfirmUserRole');
    const promote = await promotePromise;
    assertEqual(promote.status(), 200, 'POST role promote');
    const promoteBody = await promote.json();
    assertEqual(promoteBody?.data?.newRole, 'SYSTEM_ADMIN', 'newRole response');
    await promoteReload;
    if (!admin.page.url().includes(`/admin/users/${targetId}`)) await openDetail(admin);
    assert((await admin.page.locator('body').innerText()).includes('Vai trò: SYSTEM_ADMIN'), 'UI role chưa cập nhật');
    const promoteHistory = await admin.page.locator('table[aria-label="Lịch sử kiểm duyệt liên quan đến người dùng"]').innerText();
    assert(promoteHistory.includes('USER_ROLE_CHANGE'), 'Audit USER_ROLE_CHANGE sau promote không hiển thị');

    const oldToken = await api(target, 'GET', '/api/auth/me');
    assertEqual(oldToken.status, 401, 'JWT cũ phải bị thu hồi sau role change');
    const promoted = await login(browser, 'demobinh');
    const adminAccess = await api(promoted, 'GET', '/api/admin/users');
    assertEqual(adminAccess.status, 200, 'SYSTEM_ADMIN mới phải vào được admin');

    await openDetail(admin);
    await openModal(admin.page, 'open-user-role', 'adminUserRoleModal', 'userRoleNewRole');
    await admin.page.selectOption('#userRoleNewRole', 'USER');
    await admin.page.selectOption('#userRoleReasonCode', 'OTHER');
    await admin.page.fill('#userRoleReasonDetail', `Hạ quyền E2E ${tag} theo quyết định quản trị.`);
    const demotePromise = waitActionResponse(admin.page, targetId, 'role');
    const demoteReload = admin.page.waitForEvent('load', { timeout: 15000 }).catch(() => null);
    await admin.page.click('#btnConfirmUserRole');
    const demote = await demotePromise;
    assertEqual(demote.status(), 200, 'POST role demote');
    await demoteReload;
    if (!admin.page.url().includes(`/admin/users/${targetId}`)) await openDetail(admin);
    assert((await admin.page.locator('body').innerText()).includes('Vai trò: USER'), 'UI role sau demote');
    const demoteHistory = await admin.page.locator('table[aria-label="Lịch sử kiểm duyệt liên quan đến người dùng"]').innerText();
    assert(demoteHistory.includes('USER_ROLE_CHANGE'), 'Audit USER_ROLE_CHANGE sau demote không hiển thị');

    const promotedTokenAfterDemote = await api(promoted, 'GET', '/api/auth/me');
    assertEqual(promotedTokenAfterDemote.status, 401, 'JWT cũ của SYSTEM_ADMIN phải bị thu hồi sau demote');
    await promoted.context.close();
    const demoted = await login(browser, 'demobinh');
    try {
      const adminAccess = await api(demoted, 'GET', '/api/admin/users');
      assertEqual(adminAccess.status, 403, 'USER sau demote không được vào admin');
    } finally {
      await demoted.context.close();
    }

    await openDetail(admin);
    await openModal(admin.page, 'open-user-role', 'adminUserRoleModal', 'userRoleNewRole');
    await admin.page.selectOption('#userRoleNewRole', 'USER');
    await admin.page.selectOption('#userRoleReasonCode', 'OTHER');
    await admin.page.fill('#userRoleReasonDetail', `No-op role E2E ${tag} để kiểm tra 409 theo contract.`);
    const noopPromise = waitActionResponse(admin.page, targetId, 'role');
    await admin.page.click('#btnConfirmUserRole');
    const noop = await noopPromise;
    assertEqual(noop.status(), 409, 'No-op role phải 409');
    await admin.page.locator('#userRoleErrorAlert').waitFor({ state: 'visible', timeout: 5000 });
    const noopText = (await admin.page.locator('#userRoleErrorAlert').innerText()).trim();
    assert(/đã có vai trò/i.test(noopText), `Thông báo 409 chưa đúng: ${noopText}`);
    await screenshot(admin.page, 'adm-ui-04-role-409');
    return { promote: promote.status(), demote: demote.status(), noop: noop.status() };
  });

  await test(run, S, 'ADM-UI-05', 'Auth boundaries: guest 401, normal user 403, validation 422', async () => {
    const g = await guest(browser);
    try {
      const guestDetail = await api(g, 'GET', `/api/admin/users/${targetId}`);
      assertEqual(guestDetail.status, 401, 'Guest admin API phải 401');
      const guestWarn = await api(g, 'POST', `/api/admin/users/${targetId}/warn`, {
        reasonCode: 'OTHER', reasonDetail: 'Guest không được cảnh báo tài khoản.'
      });
      assert([401, 403].includes(guestWarn.status), `Guest warn phải 401/403, nhận ${guestWarn.status}`);
    } finally {
      await g.context.close();
    }

    const forbidden = await normal.page.goto(`${BASE}/admin/users/${targetId}`, { waitUntil: 'domcontentloaded' });
    assertEqual(forbidden.status(), 403, 'Normal user vào admin detail phải 403');
    const forbiddenBody = await normal.page.content();
    assert(!/"error"\s*:\s*\{/.test(forbiddenBody), '403 page không được là JSON API');

    const token = await csrf(admin);
    const invalid = await api(admin, 'POST', `/api/admin/users/${targetId}/warn`, {
      reasonCode: 'OTHER', reasonDetail: 'ngan'
    }, { headers: token ? { 'X-XSRF-TOKEN': token } : {} });
    assertEqual(invalid.status, 422, 'Reason quá ngắn phải 422');
    assertEqual(invalid.body?.error?.code, 'VALIDATION_FAILED', 'Mã lỗi validation');
    return { guest: 401, normalUser: 403, validation: 422 };
  });

  await test(run, S, 'ADM-UI-06', 'Responsive 360/768/1440 và keyboard focus cho 3 modal', async () => {
    const checks = [];
    for (const viewport of [{ width: 360, height: 800 }, { width: 768, height: 1024 }, { width: 1440, height: 900 }]) {
      await admin.page.setViewportSize(viewport);
      await openDetail(admin);
      const overflow = await admin.page.evaluate(() => ({
        scrollWidth: document.documentElement.scrollWidth,
        clientWidth: document.documentElement.clientWidth
      }));
      assert(overflow.scrollWidth <= overflow.clientWidth + 2, `Tràn ngang ${viewport.width}: ${JSON.stringify(overflow)}`);
      assert(await admin.page.locator('#btn-back-admin-dashboard').isVisible(), `Back link ẩn ${viewport.width}`);
      checks.push({ viewport: viewport.width, ...overflow });
    }

    await admin.page.setViewportSize({ width: 360, height: 800 });
    await openDetail(admin);
    await openModal(admin.page, 'open-user-warn', 'adminUserWarnModal', 'userWarnReasonCode');
    const modalBox = await admin.page.locator('#adminUserWarnModal .modal-dialog').boundingBox();
    assert(modalBox && modalBox.width <= 360, `Modal tràn mobile: ${JSON.stringify(modalBox)}`);
    await closeModal(admin.page, 'adminUserWarnModal');
    await openModal(admin.page, 'open-user-reset', 'adminUserResetProfileModal', 'userResetConfirmUsername');
    await closeModal(admin.page, 'adminUserResetProfileModal');
    await openModal(admin.page, 'open-user-role', 'adminUserRoleModal', 'userRoleNewRole');
    await closeModal(admin.page, 'adminUserRoleModal');
    await screenshot(admin.page, 'adm-ui-06-mobile');
    await admin.page.setViewportSize({ width: 1440, height: 900 });
    return { checks };
  });
} finally {
  await admin.context.close();
  await target.context.close();
  await normal.context.close();
  await browser.close();
}

const summary = writeResults(run);
saveJson('admin-account-ui-environment.json', { baseUrl: BASE, targetId, adminId, counts: summary.counts });
console.log(`Results: ${summary.file}`);
console.log(`Counts: ${JSON.stringify(summary.counts)} — overall ${summary.overall}`);
if (summary.counts.FAIL) process.exitCode = 1;
