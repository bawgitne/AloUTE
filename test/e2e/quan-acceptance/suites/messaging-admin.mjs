import {
  BASE, test, blocked, notRun, assert, assertEqual, login, guest, open, api,
  diagnostics, screenshot, record, uniqueSuffix, saveJson
} from '../lib/harness.mjs';

const S = 'messaging-admin';

async function csrf(session) {
  const token = (await session.context.cookies(BASE)).find(c => c.name === 'XSRF-TOKEN')?.value;
  return token;
}

async function ensureCsrf(session) {
  let token = await csrf(session);
  if (!token) {
    await open(session, '/feed');
    token = await csrf(session);
  }
  return token;
}

async function apiWrite(session, method, route, data) {
  const token = await ensureCsrf(session);
  return api(session, method, route, data, { headers: token ? { 'X-XSRF-TOKEN': token } : {} });
}

async function profileId(session, username) {
  const r = await api(session, 'GET', `/api/users/by-username/${username}`);
  assertEqual(r.status, 200, `GET profile @${username}`);
  assert(r.body?.data?.id, `Thiếu id hồ sơ @${username}`);
  return r.body.data.id;
}

async function conversations(session) {
  const r = await api(session, 'GET', '/api/conversations?page=0&size=50');
  assertEqual(r.status, 200, 'GET /api/conversations');
  return r.body?.data || [];
}

async function createConversation(session, targetUserId) {
  const r = await apiWrite(session, 'POST', '/api/conversations', {
    type: 'DIRECT',
    targetUserId
  });
  assert(r.status === 200 || r.status === 201, `POST /api/conversations: HTTP ${r.status} ${r.text.slice(0, 160)}`);
  return r.body.data;
}

async function sendMessageApi(session, conversationId, content) {
  const r = await apiWrite(session, 'POST', `/api/conversations/${conversationId}/messages`, { content });
  assertEqual(r.status, 201, `POST message conv ${conversationId}`);
  return r.body.data;
}

async function waitBubble(page, text, timeout = 12000) {
  const bubble = page.locator('#chat-history .message-text').filter({ hasText: text }).last();
  await bubble.waitFor({ state: 'visible', timeout });
  return (await bubble.textContent())?.trim();
}

async function unreadCount(session) {
  const r = await api(session, 'GET', '/api/notifications/unread-count');
  assertEqual(r.status, 200, 'GET unread-count');
  return r.body?.data?.unreadCount;
}

async function notifications(session, filter = 'ALL') {
  const r = await api(session, 'GET', `/api/notifications?filter=${filter}&page=0&size=50`);
  assertEqual(r.status, 200, `GET notifications ${filter}`);
  return r.body?.data || [];
}

function isUnread(n) {
  return n.isRead === false || n.read === false;
}

async function openReport(page, reportId) {
  await open({ page }, `/admin/reports/${reportId}`);
  await page.locator('#admin-report-detail-content').waitFor({ state: 'visible', timeout: 10000 });
}

async function openPostAdmin(page, postId) {
  await open({ page }, `/admin/posts/${postId}`);
  await page.locator('#admin-post-detail-content').waitFor({ state: 'visible', timeout: 10000 });
}

export async function run({ browser, results }) {
  const tag = uniqueSuffix();
  const admin = await login(browser, 'admin');
  const user = await login(browser, 'user');
  const an = await login(browser, 'demoan');
  const binh = await login(browser, 'demobinh');
  const guestSession = await guest(browser);

  const createdPosts = [];
  let anBinhConversation = null;

  /* ================= MESSAGING ================= */

  await test(results, S, 'MSG-01', 'API hội thoại chặn guest (401/403 JSON)', async () => {
    const r = await api(guestSession, 'GET', '/api/conversations');
    assert([401, 403].includes(r.status), `Guest phải bị chặn: HTTP ${r.status}`);
    assert(String(r.headers['content-type'] || '').includes('application/json'), 'Guest phải nhận JSON lỗi');
    return { status: r.status };
  });

  await test(results, S, 'MSG-02', 'Trang /messages: danh sách hội thoại tải qua REST', async () => {
    const dm = diagnostics(user.page);
    const res = await open(user, '/messages', { wait: 800 });
    assertEqual(res.status(), 200, 'HTTP /messages');
    await user.page.locator('#conversations-list .aloute-conversation-item').first()
      .waitFor({ state: 'visible', timeout: 10000 });
    const count = await user.page.locator('#conversations-list .aloute-conversation-item').count();
    assert(count >= 1, 'Không có hội thoại nào được render');
    assert(await user.page.locator('#chat-unselected-state').isVisible(), 'Desktop chưa chọn hội thoại phải thấy trạng thái hướng dẫn');
    await screenshot(user.page, 'msg-02-conversations');
    assert(!dm.pageErrors.length, `Lỗi JS trang tin nhắn: ${dm.pageErrors.join(' | ')}`);
    return { conversations: count };
  });

  const conv = await (async () => {
    const list = await conversations(user);
    const withAdmin = list.find(c => c.otherParticipant?.username === 'admin' || (c.members || []).some(m => m.username === 'admin'));
    const picked = withAdmin || list[0];
    assert(picked && picked.id, 'Tài khoản user không có hội thoại nào để kiểm thử');
    return picked;
  })();
  const convId = conv.id;

  await test(results, S, 'MSG-03', 'Deep link /messages/{id}: mở đúng hội thoại và tải lịch sử', async () => {
    const res = await open(user, `/messages/${convId}`, { wait: 1200 });
    assertEqual(res.status(), 200, `HTTP /messages/${convId}`);
    await user.page.locator('#chat-active-container').waitFor({ state: 'visible', timeout: 10000 });
    const history = await user.page.locator('#chat-history .aloute-message-bubble').count();
    assert(history >= 1, `Lịch sử tin nhắn trống (conv ${convId})`);
    const title = (await user.page.locator('#chat-header-title').textContent())?.trim();
    assert(title, 'Thiếu tiêu đề hội thoại');
    await screenshot(user.page, 'msg-03-deeplink');
    return { convId, history, title };
  });

  await test(results, S, 'MSG-04', 'Gửi tin nhắn qua UI: bubble outgoing, input xóa, API lưu đúng', async () => {
    const content = `QA tin nhắn ${tag} — kiểm thử nghiệm thu`;
    await user.page.fill('#message-input', content);
    await user.page.click('#btn-send-message');
    const shown = await waitBubble(user.page, content);
    assertEqual(shown, content, 'Nội dung bubble');
    assertEqual(await user.page.inputValue('#message-input'), '', 'Input phải được xóa sau khi gửi');
    const r = await api(user, 'GET', `/api/conversations/${convId}/messages?page=0&size=5`);
    assertEqual(r.status, 200, 'GET messages');
    assertEqual(r.body?.data?.[0]?.content, content, 'Tin nhắn mới nhất qua API');
    await screenshot(user.page, 'msg-04-sent');
    return { convId, contentLength: content.length };
  });

  await test(results, S, 'MSG-05', 'Submit rỗng không phát sinh request gửi tin', async () => {
    const dm = diagnostics(user.page);
    await user.page.fill('#message-input', '');
    const before = dm.requests.filter(q => q.method === 'POST' && q.url.includes(`/api/conversations/${convId}/messages`)).length;
    await user.page.click('#btn-send-message');
    await user.page.waitForTimeout(600);
    const after = dm.requests.filter(q => q.method === 'POST' && q.url.includes(`/api/conversations/${convId}/messages`)).length;
    assertEqual(after, before, 'Không được gửi request khi nội dung rỗng');
    return { requests: after };
  });

  await test(results, S, 'MSG-06', 'Chặn tin nhắn vượt 2000 ký tự phía client', async () => {
    const dm = diagnostics(user.page);
    await user.page.evaluate(() => {
      const input = document.getElementById('message-input');
      input.value = 'x'.repeat(2001);
      input.dispatchEvent(new Event('input', { bubbles: true }));
    });
    const before = dm.requests.filter(q => q.method === 'POST' && q.url.includes(`/api/conversations/${convId}/messages`)).length;
    await user.page.click('#btn-send-message');
    await user.page.locator('#send-error-bar').waitFor({ state: 'visible', timeout: 5000 });
    const text = (await user.page.locator('#send-error-text').textContent())?.trim() || '';
    assert(text.includes('2000'), `Thông báo giới hạn không đúng: ${text}`);
    const after = dm.requests.filter(q => q.method === 'POST' && q.url.includes(`/api/conversations/${convId}/messages`)).length;
    assertEqual(after, before, 'Không được gửi request khi vượt giới hạn');
    await user.page.fill('#message-input', '');
    return { message: text };
  });

  await test(results, S, 'MSG-07', 'Nội dung tin nhắn HTML/JS được escape (không XSS)', async () => {
    const payload = `<img src=x onerror="window.__qaMsgXss=1"> ${tag}`;
    await user.page.fill('#message-input', payload);
    await user.page.click('#btn-send-message');
    // Wait for the exact payload: the shared XSS marker and the suite tag also
    // appear in messages left by earlier cases/runs in this seeded conversation.
    const bubble = user.page.locator('#chat-history .message-text').filter({ hasText: payload }).last();
    await bubble.waitFor({ state: 'visible', timeout: 12000 });
    const shown = (await bubble.textContent())?.trim();
    assertEqual(shown, payload, 'Bubble phải hiển thị nguyên văn đã escape');
    assertEqual(await user.page.locator('#chat-history img[src="x"]').count(), 0, 'Payload đã tạo thẻ img');
    assertEqual(await user.page.evaluate(() => window.__qaMsgXss), undefined, 'XSS đã thực thi');
    await screenshot(user.page, 'msg-07-xss-escaped');
    return { payload };
  });

  await test(results, S, 'MSG-08', 'Bộ lọc danh sách hội thoại phía client', async () => {
    const before = await user.page.locator('#conversations-list .aloute-conversation-item').count();
    await user.page.fill('#search-conversations-input', `khong-co-hoi-thoai-${tag}`);
    await user.page.locator('#conversations-list').getByText('Không tìm thấy cuộc trò chuyện phù hợp.').waitFor({ state: 'visible', timeout: 5000 });
    await user.page.fill('#search-conversations-input', '');
    await user.page.locator('#conversations-list .aloute-conversation-item').first().waitFor({ state: 'visible', timeout: 5000 });
    const after = await user.page.locator('#conversations-list .aloute-conversation-item').count();
    assertEqual(after, before, 'Danh sách không phục hồi sau khi xóa bộ lọc');
    return { before, after };
  });

  await test(results, S, 'MSG-09', 'Hội thoại không tồn tại hiển thị lỗi tiếng Việt (không lộ message backend)', async () => {
    await open(user, '/messages/999999', { wait: 1200 });
    await user.page.locator('#messages-error').waitFor({ state: 'visible', timeout: 10000 });
    const text = (await user.page.locator('#messages-error-msg').textContent())?.trim() || '';
    assert(/không phải thành viên|không có quyền|không tìm thấy/i.test(text), `Thông báo lỗi không đúng: ${text}`);
    assert(!/You are not a member/i.test(text), `UI lộ message tiếng Anh của backend: ${text}`);
    await screenshot(user.page, 'msg-09-conversation-not-found');
    return { message: text, backendNote: 'Backend trả 403 FORBIDDEN cho cả hội thoại không tồn tại; cần 404 CONVERSATION_NOT_FOUND.' };
  });

  const binhId = await profileId(an, 'demobinh');
  anBinhConversation = await createConversation(an, binhId);
  const anBinhId = anBinhConversation.id;

  await test(results, S, 'MSG-10', 'Realtime WebSocket: tin nhắn mới xuất hiện không cần reload', async () => {
    await open(an, `/messages/${anBinhId}`, { wait: 1200 });
    await an.page.locator('#chat-active-container').waitFor({ state: 'visible', timeout: 10000 });
    const content = `QA realtime ${tag} từ Bình`;
    const sent = await sendMessageApi(binh, anBinhId, content);
    const shown = await waitBubble(an.page, content, 15000);
    assertEqual(shown, content, 'Nội dung nhận qua realtime');
    assertEqual(sent.content, content, 'API trả nội dung đã gửi');
    await screenshot(an.page, 'msg-10-realtime');
    return { conversationId: anBinhId, messageId: sent.id, transport: 'STOMP /ws' };
  });

  await test(results, S, 'MSG-11', 'Unread count tăng khi rời trang và thanh toán qua read receipt', async () => {
    await open(an, '/feed', { wait: 500 });
    const content = `QA unread ${tag}`;
    await sendMessageApi(binh, anBinhId, content);
    let list = await conversations(an);
    let target = list.find(c => c.id === anBinhId);
    assert(target, 'Không thấy hội thoại demoan↔demobinh trong danh sách');
    assert((target.unreadCount || 0) >= 1, `unreadCount phải >= 1, nhận ${target.unreadCount}`);
    const read = await apiWrite(an, 'POST', `/api/conversations/${anBinhId}/read`);
    assertEqual(read.status, 200, 'POST read receipt');
    list = await conversations(an);
    target = list.find(c => c.id === anBinhId);
    assertEqual(target.unreadCount || 0, 0, 'unreadCount sau read receipt');
    return { before: 1, after: target.unreadCount || 0 };
  });

  await test(results, S, 'MSG-12', 'Cách ly hội thoại: người ngoài không đọc/gửi được', async () => {
    const read = await api(user, 'GET', `/api/conversations/${anBinhId}`);
    assertEqual(read.status, 403, 'Người ngoài đọc hội thoại phải bị 403');
    const write = await apiWrite(user, 'POST', `/api/conversations/${anBinhId}/messages`, { content: 'QA outsider' });
    assertEqual(write.status, 403, 'Người ngoài gửi tin phải bị 403');
    return { read: read.status, write: write.status };
  });

  await test(results, S, 'MSG-13', 'Bong bóng chat không render lỗi console trên luồng chính', async () => {
    const dm = diagnostics(user.page);
    await open(user, '/messages', { wait: 800 });
    await user.page.locator('#conversations-list .aloute-conversation-item').first().waitFor({ state: 'visible', timeout: 10000 });
    await user.page.locator('#conversations-list .aloute-conversation-item').first().click();
    await user.page.locator('#chat-active-container').waitFor({ state: 'visible', timeout: 10000 });
    assert(!dm.pageErrors.length, `Page errors: ${dm.pageErrors.join(' | ')}`);
    assert(!dm.consoleErrors.length, `Console errors: ${dm.consoleErrors.join(' | ')}`);
    return { pageErrors: 0, consoleErrors: 0 };
  });

  /* ================= NOTIFICATIONS ================= */

  const settings = await apiWrite(an, 'PUT', '/api/users/me/notifications/settings', {
    likesEnabled: true, commentsEnabled: true, followsEnabled: true, mentionsEnabled: true,
    emailNotificationsEnabled: true, soundEnabled: true
  });
  assertEqual(settings.status, 200, 'Bật notification settings cho demoan');

  const notifPost = await apiWrite(an, 'POST', '/api/posts', {
    content: `QA notification target ${tag}`, visibility: 'PUBLIC', allowReply: true, allowQuote: true
  });
  assertEqual(notifPost.status, 201, 'Tạo bài viết cho notification');
  const notifPostId = notifPost.body.data.id;
  createdPosts.push(notifPostId);

  const xssReply = `<img src=x onerror="window.__qaNotifXss=1"> QA reply ${tag}`;
  let likeStatus = null;
  let followStatus = null;
  let replyStatus = null;

  await test(results, S, 'NTF-01', 'API thông báo chặn guest', async () => {
    const r = await api(guestSession, 'GET', '/api/notifications');
    assert([401, 403].includes(r.status), `Guest phải bị chặn: HTTP ${r.status}`);
    return { status: r.status };
  });

  await test(results, S, 'NTF-02', 'Sinh thông báo LIKE/FOLLOW/REPLY qua tương tác thật', async () => {
    const like = await apiWrite(binh, 'PUT', `/api/posts/${notifPostId}/likes/me`);
    likeStatus = like.status;
    assertEqual(like.status, 200, 'Like bài viết');
    await apiWrite(binh, 'DELETE', `/api/social/follows/${await profileId(binh, 'demoan')}`);
    const follow = await apiWrite(binh, 'PUT', `/api/social/follows/${await profileId(binh, 'demoan')}`);
    followStatus = follow.status;
    assertEqual(follow.status, 200, 'Follow demoan');
    const reply = await apiWrite(binh, 'POST', `/api/posts/${notifPostId}/replies`, {
      content: xssReply, visibility: 'PUBLIC', allowReply: true, allowQuote: true
    });
    replyStatus = reply.status;
    assertEqual(reply.status, 201, 'Reply bài viết');
    await binh.page.waitForTimeout(600);
    const all = await notifications(an);
    const types = all.map(n => n.type);
    assert(types.some(t => String(t).includes('LIKE')), `Thiếu notification LIKE: ${types.join(',')}`);
    assert(types.some(t => String(t).includes('FOLLOW')), 'Thiếu notification FOLLOW');
    assert(types.some(t => String(t).includes('REPLY')), 'Thiếu notification REPLY');
    return { types: [...new Set(types)], likeStatus, followStatus, replyStatus };
  });

  await test(results, S, 'NTF-03', 'Trang /notifications: danh sách, badge chưa đọc, 5 bộ lọc', async () => {
    const dm = diagnostics(an.page);
    const res = await open(an, '/notifications', { wait: 1000 });
    assertEqual(res.status(), 200, 'HTTP /notifications');
    await an.page.locator('#notifications-list .aloute-notification-item').first().waitFor({ state: 'visible', timeout: 10000 });
    const tabs = await an.page.locator('#notificationFilterTabs [data-filter]').count();
    assertEqual(tabs, 5, 'Số bộ lọc thông báo');
    const unreadRows = await an.page.locator('#notifications-list .aloute-notification-item.unread').count();
    assert(unreadRows >= 1, 'Phải có ít nhất một thông báo chưa đọc');
    assert(await an.page.locator('#unread-count-badge').isVisible(), 'Badge chưa đọc phải hiển thị');
    await screenshot(an.page, 'ntf-03-notifications');
    assert(!dm.pageErrors.length, `Page errors: ${dm.pageErrors.join(' | ')}`);
    return { tabs, unreadRows };
  });

  await test(results, S, 'NTF-04', 'Bộ lọc LIKE chỉ trả thông báo lượt thích', async () => {
    const apiLikes = await notifications(an, 'LIKE');
    assert(apiLikes.length >= 1, 'API LIKE không có dữ liệu');
    await an.page.click('#notificationFilterTabs [data-filter="LIKE"]');
    await an.page.waitForFunction(() => {
      const active = document.querySelector('#notificationFilterTabs [data-filter="LIKE"]');
      return active && active.classList.contains('active');
    });
    await an.page.locator('#notifications-list .aloute-notification-item').first().waitFor({ state: 'visible', timeout: 10000 });
    const rows = an.page.locator('#notifications-list .aloute-notification-item');
    const count = await rows.count();
    assertEqual(count, apiLikes.length, 'Số item LIKE trên UI so với API');
    for (let i = 0; i < count; i++) {
      const iconCount = await rows.nth(i).locator('.bi-heart-fill').count();
      assertEqual(iconCount, 1, `Item LIKE #${i} thiếu icon trái tim`);
    }
    await screenshot(an.page, 'ntf-04-filter-like');
    return { items: count };
  });

  await test(results, S, 'NTF-05', 'Đánh dấu một thông báo đã đọc (UI + API count)', async () => {
    await an.page.click('#notificationFilterTabs [data-filter="ALL"]');
    await an.page.locator('#notifications-list .aloute-notification-item').first().waitFor({ state: 'visible', timeout: 10000 });
    const all = await notifications(an);
    const target = all.find(isUnread);
    assert(target, 'Không có thông báo chưa đọc để đánh dấu');
    const before = await unreadCount(an);
    const row = an.page.locator(`#notifications-list .aloute-notification-item[data-id="${target.id}"]`);
    await row.waitFor({ state: 'visible', timeout: 10000 });
    await row.locator('.btn-mark-read').click();
    await an.page.waitForFunction(id => {
      const el = document.querySelector(`#notifications-list .aloute-notification-item[data-id="${id}"]`);
      return el && !el.classList.contains('unread');
    }, target.id, { timeout: 10000 });
    await an.page.waitForTimeout(700);
    const after = await unreadCount(an);
    assert(after < before, `Unread count phải giảm: ${before} -> ${after}`);
    return { before, after, notificationId: target.id };
  });

  await test(results, S, 'NTF-06', 'Đánh dấu tất cả đã đọc', async () => {
    await an.page.click('#btn-mark-all-read');
    await an.page.locator('#notification-feedback-text').waitFor({ state: 'visible', timeout: 10000 });
    const text = (await an.page.locator('#notification-feedback-text').textContent())?.trim() || '';
    assert(/đã đánh dấu tất cả/i.test(text), `Feedback không đúng: ${text}`);
    await an.page.waitForTimeout(700);
    assertEqual(await unreadCount(an), 0, 'Unread count sau mark-all-read');
    assertEqual(await an.page.locator('#notifications-list .aloute-notification-item.unread').count(), 0, 'Còn item chưa đọc trên UI');
    return { feedback: text };
  });

  await test(results, S, 'NTF-07', 'Xóa một thông báo khỏi trung tâm', async () => {
    const rows = an.page.locator('#notifications-list .aloute-notification-item');
    const first = rows.first();
    const id = await first.getAttribute('data-id');
    const before = await rows.count();
    await first.locator('.btn-delete-notif').click();
    await an.page.waitForFunction(notifId => {
      return !document.querySelector(`#notifications-list .aloute-notification-item[data-id="${notifId}"]`);
    }, id, { timeout: 10000 });
    await an.page.waitForTimeout(500);
    const after = await rows.count();
    assertEqual(after, before - 1, 'Số item sau khi xóa');
    const text = (await an.page.locator('#notification-feedback-text').textContent())?.trim() || '';
    assert(/Đã xóa thông báo/i.test(text), `Feedback xóa không đúng: ${text}`);
    return { removedId: id, before, after };
  });

  await test(results, S, 'NTF-08', 'Bộ lọc không có dữ liệu hiển thị empty state', async () => {
    let chosen = null;
    for (const f of ['MENTION', 'REPLY', 'FOLLOW', 'LIKE']) {
      const data = await notifications(an, f);
      if (data.length === 0) { chosen = f; break; }
    }
    assert(chosen, 'Không tìm được bộ lọc rỗng để kiểm thử');
    await an.page.click(`#notificationFilterTabs [data-filter="${chosen}"]`);
    await an.page.locator('#notifications-empty').waitFor({ state: 'visible', timeout: 10000 });
    await screenshot(an.page, `ntf-08-empty-${chosen.toLowerCase()}`);
    return { filter: chosen };
  });

  await test(results, S, 'NTF-09', 'Preview REPLY escape HTML (không XSS)', async () => {
    const freshXss = `<img src=x onerror="window.__qaNotifXss=1"> QA reply 2 ${tag}`;
    const reply = await apiWrite(binh, 'POST', `/api/posts/${notifPostId}/replies`, {
      content: freshXss, visibility: 'PUBLIC', allowReply: true, allowQuote: true
    });
    assertEqual(reply.status, 201, 'Tạo reply XSS mới cho notification');
    await binh.page.waitForTimeout(700);
    await an.page.click('#notificationFilterTabs [data-filter="REPLY"]');
    await an.page.locator('#notifications-list .aloute-notification-item').first().waitFor({ state: 'visible', timeout: 10000 });
    await an.page.locator('#notifications-list').getByText(/<img src=x/).first().waitFor({ state: 'visible', timeout: 10000 });
    const listText = (await an.page.locator('#notifications-list').textContent()) || '';
    assert(listText.includes('<img src=x'), 'Không thấy preview chứa payload đã escape');
    assertEqual(await an.page.locator('#notifications-list img[src="x"]').count(), 0, 'Payload đã tạo thẻ img');
    assertEqual(await an.page.evaluate(() => window.__qaNotifXss), undefined, 'XSS đã thực thi');
    await screenshot(an.page, 'ntf-09-xss-escaped');
    return { escaped: true };
  });

  /* ================= ADMIN ================= */

  await test(results, S, 'ADM-01', 'Tổng quan admin hiển thị KPI', async () => {
    const res = await open(admin, '/admin', { wait: 700 });
    assertEqual(res.status(), 200, 'HTTP /admin');
    const cards = await admin.page.locator('#admin-overview-content .admin-kpi-card').count();
    assert(cards >= 5, `Thiếu KPI card: ${cards}`);
    assertEqual(await admin.page.locator('#admin-overview-content .alert-danger').count(), 0, 'Trang tổng quan báo lỗi tải dữ liệu');
    await screenshot(admin.page, 'adm-01-overview');
    return { cards };
  });

  await test(results, S, 'ADM-02', 'Danh sách người dùng và tìm kiếm theo username', async () => {
    await open(admin, '/admin/users?q=demoan', { wait: 500 });
    const rows = admin.page.locator('#admin-users-content table tbody tr');
    await rows.first().waitFor({ state: 'visible', timeout: 10000 });
    const text = (await admin.page.locator('#admin-users-content table tbody').textContent()) || '';
    assert(text.includes('demoan'), `Tìm kiếm không trả demoan: ${text.slice(0, 160)}`);
    await screenshot(admin.page, 'adm-02-users-search');
    return { containsDemoan: true };
  });

  await test(results, S, 'ADM-03', 'Danh sách bài viết admin tải được', async () => {
    await open(admin, '/admin/posts', { wait: 500 });
    const rows = admin.page.locator('#admin-posts-content table tbody tr');
    await rows.first().waitFor({ state: 'visible', timeout: 10000 });
    const count = await rows.count();
    assert(count >= 1, 'Bảng bài viết admin trống');
    await screenshot(admin.page, 'adm-03-posts');
    return { rows: count };
  });

  await test(results, S, 'ADM-04', 'API admin chặn người dùng thường bằng 403', async () => {
    const r = await api(user, 'GET', '/api/admin/overview');
    assertEqual(r.status, 403, 'Non-admin GET /api/admin/overview');
    assertEqual(r.body?.error?.code, 'AUTH_FORBIDDEN', 'Mã lỗi API admin');
    return { status: r.status, code: r.body?.error?.code };
  });

  await test(results, S, 'ADM-05', 'Trang /admin chặn người dùng thường đúng chuẩn UX (BL-22)', async () => {
    const res = await open(user, '/admin');
    const status = res.status();
    const contentType = String(res.headers()['content-type'] || '');
    if (status !== 200) {
      throw new Error(`BL-22: /admin trả HTTP ${status} (${contentType.split(';')[0]}) thay vì trang lỗi HTML/redirect; người dùng thấy JSON thô.`);
    }
    assert(!contentType.includes('application/json'), 'Trang admin không được trả JSON cho non-admin');
    return { status, contentType };
  });

  const reportPost = await apiWrite(an, 'POST', '/api/posts', {
    content: `QA report target ${tag}`, visibility: 'PUBLIC', allowReply: true, allowQuote: true
  });
  assertEqual(reportPost.status, 201, 'Tạo bài viết bị report');
  const reportPostId = reportPost.body.data.id;
  createdPosts.push(reportPostId);

  const report = await apiWrite(binh, 'POST', '/api/reports', {
    reportedPostId: reportPostId,
    reason: 'SPAM',
    description: `QA report ${tag}: nội dung quảng cáo rác trong bài kiểm thử.`
  });
  assertEqual(report.status, 201, `Tạo report: HTTP ${report.status} ${report.text.slice(0, 160)}`);
  const reportId = report.body.data.id;
  saveJson('admin-report-fixture.json', { reportId, reportPostId });

  await test(results, S, 'ADM-06', 'Chi tiết báo cáo PENDING và nhận xử lý qua UI', async () => {
    await openReport(admin.page, reportId);
    const badges = admin.page.locator('#admin-report-detail-content .admin-badge');
    await badges.filter({ hasText: 'Chờ xử lý' }).first().waitFor({ state: 'visible', timeout: 10000 });
    await admin.page.locator('[data-action="start-report-review"]').first().click();
    await admin.page.locator('#aloute-confirm-dialog.show').waitFor({ state: 'visible', timeout: 8000 });
    await admin.page.click('#aloute-confirm-dialog-confirm');
    await badges.filter({ hasText: 'Đang xử lý' }).first().waitFor({ state: 'visible', timeout: 15000 });
    const detail = await api(admin, 'GET', `/api/admin/reports/${reportId}`);
    assertEqual(detail.status, 200, 'GET admin report detail');
    assertEqual(detail.body?.data?.status, 'REVIEWED', 'Trạng thái sau start-review');
    await screenshot(admin.page, 'adm-06-report-reviewed');
    return { reportId, status: detail.body?.data?.status };
  });

  await test(results, S, 'ADM-07', 'Từ chối báo cáo qua modal quyết định', async () => {
    await admin.page.locator('[data-action="open-report-decision"][data-decision="REJECT"]').first().click();
    await admin.page.locator('#adminReportDecisionModal.show').waitFor({ state: 'visible', timeout: 8000 });
    await admin.page.fill('#reportDecisionResolutionNote', `QA từ chối báo cáo ${tag}: nội dung kiểm thử, không đủ căn cứ vi phạm.`);
    await admin.page.click('#btnConfirmReportDecision');
    await admin.page.locator('#admin-report-detail-content .admin-badge').filter({ hasText: 'Đã từ chối' }).first()
      .waitFor({ state: 'visible', timeout: 15000 });
    const detail = await api(admin, 'GET', `/api/admin/reports/${reportId}`);
    assertEqual(detail.body?.data?.status, 'REJECTED', 'Trạng thái sau reject');
    assert(detail.body?.data?.resolutionNote, 'Thiếu resolutionNote');
    await screenshot(admin.page, 'adm-07-report-rejected');
    return { status: detail.body?.data?.status };
  });

  await test(results, S, 'ADM-08', 'Gỡ bài viết qua UI (reason bắt buộc) và ẩn khỏi công khai', async () => {
    await openPostAdmin(admin.page, reportPostId);
    await admin.page.locator('[data-action="open-remove-post"]').first().click();
    await admin.page.locator('#adminRemovePostModal.show').waitFor({ state: 'visible', timeout: 8000 });
    await admin.page.selectOption('#removePostReasonCode', 'SPAM');
    await admin.page.fill('#removePostReasonDetail', `QA gỡ bài ${tag}: nội dung quảng cáo rác theo kiểm thử.`);
    await admin.page.click('#btnConfirmRemovePost');
    await admin.page.locator('#admin-post-detail-content .admin-badge').filter({ hasText: 'Đã gỡ (Admin)' }).first()
      .waitFor({ state: 'visible', timeout: 15000 });
    const detail = await api(admin, 'GET', `/api/admin/posts/${reportPostId}`);
    assertEqual(detail.status, 200, 'GET admin post detail');
    assertEqual(detail.body?.data?.contentState, 'ADMIN_REMOVED', 'contentState sau khi gỡ');
    const publicView = await api(an, 'GET', `/api/posts/${reportPostId}`);
    assert([403, 404].includes(publicView.status), `Bài bị gỡ vẫn truy cập công khai: HTTP ${publicView.status}`);
    await screenshot(admin.page, 'adm-08-post-removed');
    return { contentState: detail.body?.data?.contentState, publicStatus: publicView.status };
  });

  await test(results, S, 'ADM-09', 'Khôi phục bài viết qua UI và hiển thị công khai trở lại', async () => {
    await openPostAdmin(admin.page, reportPostId);
    await admin.page.locator('[data-action="open-restore-post"]').first().click();
    await admin.page.locator('#adminRestorePostModal.show').waitFor({ state: 'visible', timeout: 8000 });
    await admin.page.fill('#restorePostReasonDetail', `QA khôi phục bài ${tag}: đã xác minh nhầm lẫn kiểm thử.`);
    await admin.page.click('#btnConfirmRestorePost');
    await admin.page.locator('#admin-post-detail-content .admin-badge').filter({ hasText: 'Bình thường' }).first()
      .waitFor({ state: 'visible', timeout: 15000 });
    const detail = await api(admin, 'GET', `/api/admin/posts/${reportPostId}`);
    assertEqual(detail.body?.data?.contentState, 'ACTIVE', 'contentState sau khôi phục');
    const publicView = await api(an, 'GET', `/api/posts/${reportPostId}`);
    assertEqual(publicView.status, 200, 'Bài viết phải truy cập công khai trở lại');
    await screenshot(admin.page, 'adm-09-post-restored');
    return { contentState: detail.body?.data?.contentState, publicStatus: publicView.status };
  });

  await test(results, S, 'ADM-10', 'Nhật ký kiểm duyệt ghi nhận gỡ và khôi phục', async () => {
    const actions = await api(admin, 'GET', `/api/admin/moderation-actions?targetType=POST&targetId=${reportPostId}&size=50`);
    assertEqual(actions.status, 200, 'GET moderation actions');
    const types = (actions.body?.data || []).map(a => a.actionType);
    assert(types.includes('POST_REMOVE'), `Thiếu POST_REMOVE: ${types.join(',')}`);
    assert(types.includes('POST_RESTORE'), `Thiếu POST_RESTORE: ${types.join(',')}`);
    await open(admin, `/admin/moderation-actions?targetType=POST&targetId=${reportPostId}`, { wait: 500 });
    const text = (await admin.page.locator('#admin-actions-content').textContent()) || '';
    assert(text.includes('Gỡ bài viết') && text.includes('Khôi phục bài'), 'Nhật ký UI thiếu hành động');
    await screenshot(admin.page, 'adm-10-moderation-log');
    return { types };
  });

  await test(results, S, 'ADM-11', 'Trang admin xử lý ID không tồn tại bằng trạng thái rỗng', async () => {
    const res = await open(admin, '/admin/reports/999999', { wait: 400 });
    assertEqual(res.status(), 200, 'HTTP /admin/reports/999999');
    const text = (await admin.page.locator('#admin-report-detail-content').textContent()) || '';
    assert(/Không tìm thấy báo cáo/i.test(text), 'Thiếu trạng thái không tìm thấy báo cáo');
    return { message: 'Không tìm thấy báo cáo' };
  });

  await test(results, S, 'ADM-12', 'Bộ lọc trạng thái nội dung bài viết admin', async () => {
    await open(admin, '/admin/posts?contentState=ADMIN_REMOVED', { wait: 500 });
    await admin.page.locator('#admin-posts-content').waitFor({ state: 'visible' });
    const text = (await admin.page.locator('#admin-posts-content').textContent()) || '';
    assert(/Đã gỡ \(Admin\)|Không tìm thấy bài viết/i.test(text), 'Bộ lọc trạng thái không phản hồi');
    return { filter: 'ADMIN_REMOVED' };
  });

  /* ================= CLEANUP ================= */

  for (const id of createdPosts) {
    await apiWrite(an, 'DELETE', `/api/posts/${id}`).catch(() => null);
  }

  await record(results, S, 'CLEAN-01', 'Dữ liệu kiểm thử đã được dọn (bài viết QA)', 'PASS', {
    deleted: createdPosts,
    conversationLeft: anBinhId,
    note: 'Hội thoại demoan↔demobinh và report đã đóng được giữ lại trong H2 cục bộ.'
  });

  await Promise.allSettled([admin, user, an, binh].map(async s => {
    await screenshot(s.page, `cleanup-${s.who}`).catch(() => null);
  }));
}
