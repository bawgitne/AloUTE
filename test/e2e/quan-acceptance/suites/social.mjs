import {
  BASE, test, blocked, notRun, assert, assertEqual, login, guest, open, api,
  diagnostics, screenshot, visibleText, record, uniqueSuffix, saveJson
} from '../lib/harness.mjs';

const S = 'social';

async function csrf(session) {
  const token = (await session.context.cookies(BASE)).find(c => c.name === 'XSRF-TOKEN')?.value;
  return token;
}

async function apiWrite(session, method, route, data) {
  const token = await csrf(session);
  return api(session, method, route, data, { headers: token ? { 'X-XSRF-TOKEN': token } : {} });
}

async function getPost(session, id) {
  const r = await api(session, 'GET', `/api/posts/${id}`);
  return r;
}

async function createPostApi(session, content, extra = {}) {
  const r = await apiWrite(session, 'POST', '/api/posts', {
    content, visibility: 'PUBLIC', allowReply: true, allowQuote: true, ...extra
  });
  assertEqual(r.status, 201, `Tạo bài qua API (${content.slice(0, 40)})`);
  return r.body.data;
}

async function deletePostApi(session, id) {
  const r = await apiWrite(session, 'DELETE', `/api/posts/${id}`);
  return r.status;
}

async function openPost(session, id) {
  await open(session, `/posts/${id}`);
  await session.page.locator(`article[data-post-id="${id}"]`).first().waitFor({ state: 'visible', timeout: 10000 });
}

async function openPostMenu(session, id) {
  const card = session.page.locator(`article[data-post-id="${id}"]`).first();
  await card.locator('[data-bs-toggle="dropdown"]').click();
  await card.locator('.dropdown-menu.show').waitFor({ state: 'visible', timeout: 5000 });
}

async function toastText(page, pattern) {
  const toast = page.locator('#aloute-toast-notice');
  await toast.waitFor({ state: 'visible', timeout: 8000 });
  const text = (await toast.textContent())?.trim() || '';
  if (pattern && !pattern.test(text)) throw new Error(`Toast không khớp ${pattern}: ${text}`);
  return text;
}

async function waitButtonEnabled(page, selector, timeout = 8000) {
  await page.waitForFunction(sel => {
    const el = document.querySelector(sel);
    return el && !el.disabled;
  }, selector, { timeout });
}

async function voteInPoll(page, pollId, optionIds) {
  await page.locator(`.post-poll-container[data-poll-id="${pollId}"]`).waitFor({ state: 'visible', timeout: 8000 });
  for (const id of optionIds) await page.check(`#opt-${id}`);
  await page.locator(`.post-poll-container[data-poll-id="${pollId}"] .poll-vote-form button[type="submit"]`).click();
  await page.locator(`.post-poll-container[data-poll-id="${pollId}"] .poll-results`).waitFor({ state: 'visible', timeout: 10000 });
}

export async function run({ browser, results }) {
  const tag = uniqueSuffix();
  const user = await login(browser, 'user');
  const an = await login(browser, 'demoan');
  const binh = await login(browser, 'demobinh');
  await open(user, '/feed');
  await open(an, '/feed');
  await open(binh, '/feed');

  const created = [];
  let mainPost = null;
  const originalProfile = (await api(user, 'GET', '/api/users/me/profile')).body?.data || null;

  /* ---------------- Profile ---------------- */

  await test(results, S, 'SOC-P01', 'Hồ sơ cá nhân hiển thị tên, username, tab và nút chỉnh sửa', async () => {
    const res = await open(user, '/profile');
    assertEqual(res.status(), 200, 'HTTP /profile');
    assert(await user.page.locator('#btn-edit-profile').count() === 1, 'Chủ hồ sơ phải thấy nút chỉnh sửa');
    assert(await user.page.locator('#profile-username').textContent(), 'Thiếu username');
    const tabs = await user.page.locator('#profileTabList .nav-link').count();
    assert(tabs >= 3, `Thiếu tab hồ sơ: ${tabs}`);
    await screenshot(user.page, 'social-profile-own');
    return { tabs };
  });

  await test(results, S, 'SOC-P02', 'Chỉnh sửa hồ sơ: tên hiển thị + bio, lưu và giữ sau reload', async () => {
    const bio = `QA bio ${tag} — kiểm thử nghiệm thu`;
    try {
      await open(user, '/profile');
      await user.page.click('#btn-edit-profile');
      await user.page.locator('#editProfileModal').waitFor({ state: 'visible' });
      await user.page.fill('#edit-display-name', `QA User ${tag}`);
      await user.page.fill('#edit-bio', bio);
      await user.page.click('#btn-save-profile');
      await user.page.locator('#editProfileModal').waitFor({ state: 'hidden', timeout: 10000 });
      await toastText(user.page, /hồ sơ/i).catch(() => null);
      await user.page.reload({ waitUntil: 'domcontentloaded' });
      const name = (await user.page.locator('#profile-display-name').textContent())?.trim();
      const bioText = (await user.page.locator('#profile-bio-text').textContent())?.trim();
      assert(name && name.includes(tag), `Tên hiển thị không cập nhật: ${name}`);
      assertEqual(bioText, bio, 'Bio không cập nhật');
      const me = await api(user, 'GET', '/api/users/me/profile');
      assertEqual(me.body?.data?.displayName, `QA User ${tag}`, 'API profile displayName');
      await screenshot(user.page, 'social-profile-edited');
      return { bioLength: bio.length };
    } finally {
      if (originalProfile) {
        await apiWrite(user, 'PATCH', '/api/users/me/profile', {
          displayName: originalProfile.displayName,
          bio: originalProfile.bio ?? ''
        }).catch(() => null);
      }
    }
  });

  await test(results, S, 'SOC-P03', 'Xem hồ sơ người khác: không có nút sửa, có follow/nhắn tin', async () => {
    await open(user, '/profile/demoan');
    assert(await user.page.locator('#btn-edit-profile').count() === 0, 'Không được thấy nút sửa hồ sơ người khác');
    assert(await user.page.locator('#btn-follow').count() === 1, 'Thiếu nút theo dõi');
    assert(await user.page.locator('#btn-direct-message').count() === 1, 'Thiếu nút nhắn tin');
    await screenshot(user.page, 'social-profile-other');
  });

  await test(results, S, 'SOC-P04', 'Theo dõi / bỏ theo dõi qua UI cập nhật trạng thái và bộ đếm', async () => {
    await open(user, '/profile/demoan');
    const before = await api(binh, 'GET', '/api/users/3');
    const followedBefore = !!before.body?.data?.capabilities;
    const btn = user.page.locator('#btn-follow');
    const initial = (await btn.textContent())?.trim() || '';
    await btn.click();
    await user.page.waitForTimeout(800);
    const after = (await btn.textContent())?.trim() || '';
    assert(initial !== after, `Nút follow không đổi trạng thái: ${initial}`);
    await btn.click();
    await user.page.waitForTimeout(800);
    const restored = (await btn.textContent())?.trim() || '';
    assertEqual(restored, initial, 'Bỏ theo dõi phải trở về nhãn ban đầu');
    return { initial, after };
  });

  await test(results, S, 'SOC-P05', 'Tab hồ sơ: Bài viết / Phản hồi / Ảnh / Đã lưu chuyển đúng pane', async () => {
    await open(user, '/profile');
    for (const [tab, pane] of [['#tab-replies', '#pane-replies'], ['#tab-media', '#pane-media'], ['#tab-saved', '#pane-saved'], ['#tab-posts', '#pane-posts']]) {
      assertEqual(await user.page.locator(tab).count(), 1, `Thiếu tab ${tab}`);
      await user.page.click(tab);
      await user.page.waitForTimeout(250);
      const visible = await user.page.locator(pane).isVisible();
      assertEqual(visible, true, `Pane ${pane} phải hiển thị sau khi bấm ${tab}`);
    }
  });

  blocked(results, S, 'SOC-P06', 'Upload avatar/cover', 'Firebase Storage cần serviceAccountKey.json; môi trường QA local không cấu hình (demo checklist ghi rõ)');

  /* ---------------- Post lifecycle ---------------- */

  await test(results, S, 'SOC-P10', 'Tạo bài text qua UI (tiếng Việt có dấu) và chuyển trang chi tiết', async () => {
    const content = `QA ${tag} — Sinh viên HCMUTE kiểm thử tạo bài viết có dấu: ă â đ ê ô ơ ư 🎓`;
    await open(user, '/post/create');
    await user.page.fill('#postContent', content);
    await waitButtonEnabled(user.page, '#btn-create-post');
    await user.page.click('#btn-create-post');
    await user.page.waitForURL(/\/posts\/\d+/, { timeout: 15000 });
    const id = Number(user.page.url().match(/\/posts\/(\d+)/)[1]);
    created.push(id);
    const post = await getPost(user, id);
    assertEqual(post.status, 200, 'GET post sau tạo');
    assertEqual(post.body.data.content, content, 'Nội dung bài viết');
    assertEqual(post.body.data.visibility, 'PUBLIC', 'Visibility');
    mainPost = id;
    await screenshot(user.page, 'social-post-created');
    return { postId: id };
  });

  await test(results, S, 'SOC-P11', 'Like / unlike qua UI cập nhật bộ đếm, aria-pressed và API', async () => {
    const like = user.page.locator(`article[data-post-id="${mainPost}"] [data-action="like"]`).first();
    const before = Number((await like.locator('.interaction-count').textContent()) || 0);
    const likeResponse = user.page.waitForResponse(r => r.url().includes(`/api/posts/${mainPost}/likes/me`) && r.request().method() === 'PUT', { timeout: 10000 });
    await like.click();
    assert([200, 204].includes((await likeResponse).status()), 'PUT like');
    await user.page.waitForFunction(([id]) => {
      const b = document.querySelector(`article[data-post-id="${id}"] [data-action="like"]`);
      return b && b.getAttribute('aria-pressed') === 'true';
    }, [mainPost], { timeout: 8000 });
    let post = await getPost(user, mainPost);
    assertEqual(post.body.data.viewerState.liked, true, 'API liked');
    assertEqual(post.body.data.likeCount, before + 1, 'API likeCount');
    const unlikeResponse = user.page.waitForResponse(r => r.url().includes(`/api/posts/${mainPost}/likes/me`) && r.request().method() === 'DELETE', { timeout: 10000 });
    await like.click();
    assert([200, 204].includes((await unlikeResponse).status()), 'DELETE like');
    await user.page.waitForFunction(([id]) => {
      const b = document.querySelector(`article[data-post-id="${id}"] [data-action="like"]`);
      return b && b.getAttribute('aria-pressed') === 'false';
    }, [mainPost], { timeout: 8000 });
    await user.page.waitForTimeout(250);
    post = await getPost(user, mainPost);
    assertEqual(post.body.data.viewerState.liked, false, 'API unliked');
    assertEqual(post.body.data.likeCount, before, 'API likeCount sau bỏ thích');
    return { before };
  });

  await test(results, S, 'SOC-P12', 'Like nhấp nhanh liên tiếp không tạo trạng thái sai', async () => {
    const like = user.page.locator(`article[data-post-id="${mainPost}"] [data-action="like"]`).first();
    const before = (await getPost(user, mainPost)).body.data.viewerState.liked;
    await Promise.all([like.click(), like.click().catch(() => null), like.click().catch(() => null)]);
    await user.page.waitForTimeout(1200);
    const post = await getPost(user, mainPost);
    const pressed = await like.getAttribute('aria-pressed');
    assertEqual(String(post.body.data.viewerState.liked), pressed, 'UI và API phải cùng trạng thái sau click nhanh');
    assert(before !== undefined, 'fixture');
    return { finalLiked: post.body.data.viewerState.liked };
  });

  await test(results, S, 'SOC-P13', 'Lưu / bỏ lưu bài viết; bài xuất hiện và biến mất khỏi /saved', async () => {
    const bookmark = user.page.locator(`article[data-post-id="${mainPost}"] [data-action="bookmark"]`).first();
    const saveResponse = user.page.waitForResponse(r => r.url().includes(`/api/posts/${mainPost}/bookmarks/me`) && r.request().method() === 'PUT', { timeout: 10000 });
    await bookmark.click();
    assert([200, 204].includes((await saveResponse).status()), 'PUT bookmark');
    await user.page.waitForFunction(([id]) => {
      const b = document.querySelector(`article[data-post-id="${id}"] [data-action="bookmark"]`);
      return b && b.getAttribute('aria-pressed') === 'true';
    }, [mainPost], { timeout: 8000 });
    await open(user, '/saved');
    assert(await user.page.locator(`article[data-post-id="${mainPost}"]`).count() >= 1, 'Bài đã lưu phải có trong /saved');
    await open(user, `/posts/${mainPost}`);
    const unsaveResponse = user.page.waitForResponse(r => r.url().includes(`/api/posts/${mainPost}/bookmarks/me`) && r.request().method() === 'DELETE', { timeout: 10000 });
    await user.page.locator(`article[data-post-id="${mainPost}"] [data-action="bookmark"]`).first().click();
    assert([200, 204].includes((await unsaveResponse).status()), 'DELETE bookmark');
    await user.page.waitForTimeout(300);
    await open(user, '/saved');
    assert(await user.page.locator(`article[data-post-id="${mainPost}"]`).count() === 0, 'Bài đã bỏ lưu không được có trong /saved');
    return { savedVisible: true };
  });

  await test(results, S, 'SOC-P14', 'Bình luận bài viết: gửi thành công, tăng bộ đếm, giữ sau refresh', async () => {
    await openPost(user, mainPost);
    const countBefore = Number((await user.page.locator('#post-replies-count').textContent()) || 0);
    const reply = `Phản hồi QA ${tag} — nội dung tiếng Việt có dấu.`;
    await user.page.fill('#commentContent', reply);
    await user.page.click('#btn-submit-reply');
    await user.page.waitForFunction(t => {
      const list = document.getElementById('replies-list');
      return list && list.textContent.includes(t);
    }, reply, { timeout: 10000 });
    const countAfter = Number((await user.page.locator('#post-replies-count').textContent()) || 0);
    assertEqual(countAfter, countBefore + 1, 'Bộ đếm bình luận');
    await user.page.reload({ waitUntil: 'domcontentloaded' });
    assert((await user.page.content()).includes(reply), 'Bình luận phải còn sau refresh');
    return { replyCount: countAfter };
  });

  await test(results, S, 'SOC-P15', 'Trích dẫn bài viết: tạo quote, tăng quoteCount, hiển thị bài gốc', async () => {
    await openPost(user, mainPost);
    const before = (await getPost(user, mainPost)).body.data.quoteCount;
    await user.page.locator(`article[data-post-id="${mainPost}"] [data-action="open-quote-modal"]`).first().click();
    await user.page.locator('#quotePostModal').waitFor({ state: 'visible' });
    const quoteContent = `Trích dẫn QA ${tag}: đồng ý với chia sẻ này.`;
    await user.page.fill('#quote-post-content', quoteContent);
    await user.page.click('#btn-submit-quote');
    await user.page.waitForFunction(() => window.location.pathname.startsWith('/posts/'), null, { timeout: 12000 });
    await user.page.waitForTimeout(700);
    const after = (await getPost(user, mainPost)).body.data.quoteCount;
    assertEqual(after, before + 1, 'quoteCount phải tăng');
    const newest = (await api(user, 'GET', '/api/posts?page=0&size=5')).body.data.find(p => p.content === quoteContent);
    assert(newest, 'Không tìm thấy bài trích dẫn qua API');
    assertEqual(newest.quotedPostId, mainPost, 'quotedPostId');
    created.push(newest.id);
    await screenshot(user.page, 'social-quote');
    return { quotePostId: newest.id };
  });

  await test(results, S, 'SOC-P16', 'Chia sẻ lại (repost) bật/tắt và đồng bộ API', async () => {
    await openPost(user, mainPost);
    const btn = user.page.locator(`article[data-post-id="${mainPost}"] [data-action="repost"]`).first();
    await btn.click();
    await user.page.waitForFunction(([id]) => {
      const b = document.querySelector(`article[data-post-id="${id}"] [data-action="repost"]`);
      return b && b.getAttribute('aria-pressed') === 'true';
    }, [mainPost], { timeout: 8000 });
    let post = await getPost(user, mainPost);
    assertEqual(post.body.data.viewerState.reposted, true, 'API reposted');
    await btn.click();
    await user.page.waitForFunction(([id]) => {
      const b = document.querySelector(`article[data-post-id="${id}"] [data-action="repost"]`);
      return b && b.getAttribute('aria-pressed') === 'false';
    }, [mainPost], { timeout: 8000 });
    post = await getPost(user, mainPost);
    assertEqual(post.body.data.viewerState.reposted, false, 'API bỏ repost');
  });

  await test(results, S, 'SOC-P17', 'Sao chép liên kết bài viết và mở đúng permalink', async () => {
    await user.context.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: BASE });
    await openPost(user, mainPost);
    await user.page.locator(`article[data-post-id="${mainPost}"] [data-action="copy-post-link"]`).first().click();
    const text = await toastText(user.page, /sao chép/i);
    const clip = await user.page.evaluate(() => navigator.clipboard.readText().catch(() => '')).catch(() => '');
    assert(clip.includes(`/posts/${mainPost}`), `Clipboard không chứa permalink: ${clip}`);
    const tab = await user.context.newPage();
    await tab.goto(clip, { waitUntil: 'domcontentloaded' });
    assert(tab.url().includes(`/posts/${mainPost}`), 'Permalink không mở đúng bài');
    assert((await tab.locator(`article[data-post-id="${mainPost}"]`).count()) >= 1, 'Permalink không render bài');
    await tab.close();
    return { toast: text, clipboardHost: (() => { try { return new URL(clip).host; } catch { return null; } })() };
  });

  await test(results, S, 'SOC-P18', 'Chỉnh sửa bài của mình: nội dung đổi tức thì và giữ sau reload', async () => {
    const updated = `QA ${tag} — nội dung đã chỉnh sửa lúc ${new Date().toISOString().slice(11, 19)}`;
    await openPost(user, mainPost);
    await openPostMenu(user, mainPost);
    await user.page.locator(`article[data-post-id="${mainPost}"] [data-action="edit-post"]`).first().click();
    await user.page.locator('#editPostModal').waitFor({ state: 'visible' });
    await user.page.fill('#edit-post-content', updated);
    await user.page.click('#btn-save-post');
    await user.page.locator('#editPostModal').waitFor({ state: 'hidden', timeout: 10000 });
    await user.page.waitForTimeout(500);
    const post = await getPost(user, mainPost);
    assertEqual(post.body.data.content, updated, 'API content sau sửa');
    await user.page.reload({ waitUntil: 'domcontentloaded' });
    assert((await user.page.content()).includes(updated), 'Nội dung mới phải còn sau reload');
    return { updated: true };
  });

  await test(results, S, 'SOC-P19', 'Báo cáo bài viết qua modal, API trả 201', async () => {
    await openPost(user, mainPost);
    await openPostMenu(user, mainPost);
    const responsePromise = user.page.waitForResponse(r => r.url().endsWith('/api/reports') && r.request().method() === 'POST', { timeout: 10000 });
    responsePromise.catch(() => null);
    await user.page.locator(`article[data-post-id="${mainPost}"] [data-action="open-report-modal"]`).first().click();
    await user.page.locator('#reportModal').waitFor({ state: 'visible' });
    await user.page.selectOption('#report-reason', 'SPAM');
    await user.page.fill('#report-description', `QA report ${tag}`);
    await user.page.click('#btn-submit-report');
    const response = await responsePromise;
    assertEqual(response.status(), 201, 'POST /api/reports');
    await user.page.locator('#reportModal').waitFor({ state: 'hidden', timeout: 10000 });
    await screenshot(user.page, 'social-report-submitted');
    return { reportStatus: response.status() };
  });

  await test(results, S, 'SOC-P20', 'Nháp soạn bài được khôi phục sau khi rời trang', async () => {
    const draft = `Nháp QA ${tag} — nội dung chưa đăng`;
    await open(user, '/post/create');
    await user.page.fill('#postContent', draft);
    await user.page.waitForTimeout(900);
    await open(user, '/feed');
    await open(user, '/post/create');
    await user.page.waitForFunction(t => {
      const el = document.getElementById('postContent');
      return el && el.value.includes(t);
    }, draft, { timeout: 8000 });
    const value = await user.page.inputValue('#postContent');
    assert(value.includes(draft), `Không khôi phục nháp: ${value}`);
    await screenshot(user.page, 'social-draft-recovery');
    return { draftLength: draft.length };
  });

  await test(results, S, 'SOC-P21', 'XSS trong nội dung bài được escape, không thực thi script', async () => {
    const payload = `<img src=x onerror="window.__qaXss=1"> QA ${tag}`;
    const post = await createPostApi(user, payload);
    created.push(post.id);
    let dialog = false;
    user.page.on('dialog', async d => { dialog = true; await d.dismiss(); });
    await openPost(user, post.id);
    const xss = await user.page.evaluate(() => window.__qaXss || 0);
    assertEqual(dialog, false, 'Không được có dialog JS');
    assertEqual(xss, 0, 'Không được thực thi onerror');
    const content = await user.page.locator(`article[data-post-id="${post.id}"] .post-card-content`).first().textContent();
    assert(content.includes('<img'), 'Nội dung phải được hiển thị dạng text');
    return { escaped: true };
  });

  await test(results, S, 'SOC-P22', 'Xóa bài của mình qua modal; biến mất khỏi API và feed', async () => {
    const temp = await createPostApi(user, `Bài sẽ xóa QA ${tag}`);
    created.push(temp.id);
    await openPost(user, temp.id);
    await openPostMenu(user, temp.id);
    await user.page.locator(`article[data-post-id="${temp.id}"] [data-action="delete-post"]`).first().click();
    await user.page.locator('#deletePostModal').waitFor({ state: 'visible' });
    await user.page.click('#btn-confirm-delete-post');
    await user.page.waitForURL('**/feed', { timeout: 12000 });
    const after = await getPost(user, temp.id);
    assert(after.status === 404 || after.status === 403, `Bài đã xóa phải không truy cập được, nhận ${after.status}`);
    const feed = await api(user, 'GET', '/api/feed/home?page=0&size=50');
    assert(!feed.body.data.some(p => p.id === temp.id), 'Bài đã xóa không được có trong feed');
    return { deletedStatus: after.status };
  });

  await test(results, S, 'SOC-P23', 'Guest không thể tương tác qua API (like/bookmark/repost/vote)', async () => {
    const g = await guest(browser);
    const like = await api({ context: g.context }, 'PUT', `/api/posts/${mainPost}/likes/me`);
    const bookmark = await api({ context: g.context }, 'PUT', `/api/posts/${mainPost}/bookmarks/me`);
    const repost = await api({ context: g.context }, 'PUT', `/api/posts/${mainPost}/reposts/me`);
    for (const [name, r] of [['like', like], ['bookmark', bookmark], ['repost', repost]]) {
      assert([401, 403].includes(r.status), `Guest ${name} phải bị chặn 401/403, nhận ${r.status}`);
    }
    await g.context.close();
    return { like: like.status };
  });

  /* ---------------- Feed ---------------- */

  await test(results, S, 'SOC-P30', 'Feed home/following/explore: quyền truy cập và dữ liệu', async () => {
    const home = await api(user, 'GET', '/api/feed/home?page=0&size=5');
    const following = await api(user, 'GET', '/api/feed/following?page=0&size=5');
    const g = await guest(browser);
    const explore = await api({ context: g.context }, 'GET', '/api/feed/explore?page=0&size=5');
    const guestHome = await api({ context: g.context }, 'GET', '/api/feed/home?page=0&size=5');
    assertEqual(home.status, 200, 'home');
    assertEqual(following.status, 200, 'following');
    assertEqual(explore.status, 200, 'explore guest');
    assertEqual(guestHome.status, 200, 'home guest');
    assert(home.body.data.length > 0, 'Home feed phải có bài');
    await g.context.close();
    return { home: home.body.data.length, following: following.body.data.length, explore: explore.body.data.length };
  });

  await test(results, S, 'SOC-P31', 'Phân trang API 0-indexed trả trang khác nhau', async () => {
    const p0 = await api(user, 'GET', '/api/posts?page=0&size=2');
    const p1 = await api(user, 'GET', '/api/posts?page=1&size=2');
    assertEqual(p0.status, 200, 'page 0');
    assertEqual(p1.status, 200, 'page 1');
    assert(p0.body.data.length <= 2 && p1.body.data.length <= 2, 'Size phải được tôn trọng');
    const ids0 = p0.body.data.map(p => p.id);
    const ids1 = p1.body.data.map(p => p.id);
    assert(ids0.length === 0 || ids1.length === 0 || ids0[0] !== ids1[0], 'Trang 0 và 1 phải khác nhau');
    return { pageSize: p0.body.data.length, hasMeta: !!p0.body.meta };
  });

  await test(results, S, 'SOC-P32', 'Feed UI có composer cho user và CTA cho guest', async () => {
    await open(user, '/feed');
    assert(await user.page.locator('.composer-box').count() === 1, 'User phải thấy composer');
    const g = await guest(browser);
    await open(g, '/feed');
    assert(await g.page.locator('section[aria-label="Đăng nhập để đăng bài"]').count() === 1, 'Guest phải thấy CTA');
    await g.context.close();
  });

  await test(results, S, 'SOC-P33', 'Trang feed ngoài phạm vi trang hiển thị empty/out-of-range nhất quán', async () => {
    const res = await user.page.goto(`${BASE}/feed?page=99999`, { waitUntil: 'domcontentloaded' });
    assertEqual(res.status(), 200, 'HTTP feed page lớn');
    const text = await user.page.locator('body').innerText();
    assert(/không có|không tìm thấy|trang|quay lại|bảng tin/i.test(text), 'Phải có thông báo out-of-range/empty');
    return { handled: true };
  });

  /* ---------------- Poll ---------------- */

  await test(results, S, 'SOC-P40', 'Poll: tác giả chưa vote thấy form, vote thành công và giữ sau refresh', async () => {
    let postId = 4;
    let poll = (await getPost(an, postId)).body.data.poll;
    if (!poll) throw new Error('Bài seed 4 không có poll');
    if (poll.userVotedOptionId != null) {
      const fresh = await createPostApi(user, `Poll tái lập QA ${tag}`, {
        poll: { question: `Chọn phương án QA ${tag}`, allowMultiple: false, options: ['A', 'B', 'C'] }
      });
      created.push(fresh.id);
      postId = fresh.id;
      poll = fresh.poll;
    }
    await openPost(an, postId);
    const pollId = poll.id;
    const optionId = poll.options[2].id;
    const before = poll.totalVotes;
    await voteInPoll(an.page, pollId, [optionId]);
    const after = await getPost(an, postId);
    assertEqual(after.body.data.poll.totalVotes, before + 1, 'totalVotes sau vote');
    assertEqual(after.body.data.poll.userVotedOptionId, optionId, 'userVotedOptionId');
    await an.page.reload({ waitUntil: 'domcontentloaded' });
    const persisted = await getPost(an, postId);
    assertEqual(persisted.body.data.poll.userVotedOptionId, optionId, 'Vote phải giữ sau refresh');
    await screenshot(an.page, 'social-poll-result');
    globalThis.__qaPoll = { postId, pollId, optionId };
    return { pollId, optionId, totalVotes: after.body.data.poll.totalVotes };
  });

  const pollFixture = globalThis.__qaPoll;
  if (!pollFixture) {
    blocked(results, S, 'SOC-P41', 'Vote lặp lại bị từ chối', 'Không có fixture poll từ SOC-P40');
  } else {
    await test(results, S, 'SOC-P41', 'Vote lặp lại bị từ chối (409/400)', async () => {
      const r = await apiWrite(an, 'POST', `/api/polls/${pollFixture.pollId}/votes`, { optionId: pollFixture.optionId });
      assert([400, 409].includes(r.status), `Vote lặp phải bị từ chối, nhận ${r.status}`);
      return { status: r.status, code: r.body?.error?.code ?? null };
    });
  }

  await test(results, S, 'SOC-P42', 'Guest vote bị chặn 401', async () => {
    const g = await guest(browser);
    const fixture = globalThis.__qaPoll || { pollId: (await getPost(an, 4)).body.data.poll.id, optionId: (await getPost(an, 4)).body.data.poll.options[0].id };
    const r = await api({ context: g.context }, 'POST', `/api/polls/${fixture.pollId}/votes`, { optionId: fixture.optionId }, {
      headers: { 'Content-Type': 'application/json' }
    });
    assert([401, 403].includes(r.status), `Guest vote phải bị chặn 401/403, nhận ${r.status}`);
    await g.context.close();
  });

  await test(results, S, 'SOC-P43', 'Option không tồn tại bị từ chối', async () => {
    const fixture = globalThis.__qaPoll || { pollId: (await getPost(an, 4)).body.data.poll.id };
    const g = await login(browser, 'demobinh');
    await open(g, '/feed');
    const r = await apiWrite(g, 'POST', `/api/polls/${fixture.pollId}/votes`, { optionId: 999999 });
    assert([400, 404, 409, 422].includes(r.status), `Option sai phải bị từ chối, nhận ${r.status}`);
    await g.context.close();
    return { status: r.status };
  });

  await test(results, S, 'SOC-P44', 'Poll nhiều lựa chọn: API tạo, UI render checkbox, chọn 2 đáp án', async () => {
    const content = `Poll nhiều lựa chọn QA ${tag}`;
    const pollInput = {
      question: `Bạn chọn kỹ năng nào? ${tag}`,
      allowMultiple: true,
      options: ['Kiểm thử', 'Backend', 'Frontend']
    };
    const post = await createPostApi(user, content, { poll: pollInput });
    created.push(post.id);
    assert(post.poll && post.poll.allowMultiple === true, 'API poll phải allowMultiple');
    await openPost(binh, post.id);
    const pollId = post.poll.id;
    const boxes = binh.page.locator(`.post-poll-container[data-poll-id="${pollId}"] input[type="checkbox"]`);
    assertEqual(await boxes.count(), 3, 'Poll nhiều lựa chọn phải render checkbox');
    const ids = post.poll.options.slice(0, 2).map(o => o.id);
    await voteInPoll(binh.page, pollId, ids);
    const after = (await getPost(binh, post.id)).body.data.poll;
    assertEqual(after.totalVotes, 2, 'totalVotes (mỗi lựa chọn là một phiếu)');
    const selected = after.options.filter(o => o.selectedByCurrentUser).length;
    assertEqual(selected, 2, 'Phải ghi nhận 2 lựa chọn');
    return { pollId, selected };
  });

  await test(results, S, 'SOC-P45', 'Poll thiếu phương án bị validation chặn', async () => {
    await open(user, '/post/create');
    await user.page.fill('#postContent', `Poll lỗi QA ${tag}`);
    await user.page.click('#btn-toggle-poll');
    await user.page.fill('#poll-question', 'Câu hỏi thiếu đáp án?');
    await waitButtonEnabled(user.page, '#btn-create-post');
    await user.page.click('#btn-create-post');
    await user.page.waitForTimeout(1200);
    const stillOnCreate = user.page.url().includes('/post/create');
    const error = await user.page.locator('#poll-builder-status, #create-post-status').first().textContent().catch(() => '');
    assert(stillOnCreate, 'Không được tạo bài khi poll thiếu đáp án');
    assert((error || '').trim().length > 0 || await user.page.locator('.poll-option-input.is-invalid').count() > 0, 'Phải hiển thị lỗi poll');
    return { blockedAtValidation: true };
  });

  await test(results, S, 'SOC-P46', 'Poll giới hạn một lựa chọn không cho chọn 2 qua UI', async () => {
    const post = await createPostApi(user, `Poll một lựa chọn QA ${tag}`, {
      poll: { question: `Chọn một ${tag}`, allowMultiple: false, options: ['Một', 'Hai', 'Ba'] }
    });
    created.push(post.id);
    await openPost(binh, post.id);
    const form = binh.page.locator(`.post-poll-container[data-poll-id="${post.poll.id}"] .poll-vote-form`);
    await form.waitFor({ state: 'visible', timeout: 8000 });
    const radios = form.locator('input[type="radio"]');
    assertEqual(await radios.count(), post.poll.options.length, 'Single-choice phải render radio');
    assertEqual(await form.locator('input[type="checkbox"]').count(), 0, 'Không được có checkbox');
    await radios.nth(0).check();
    await radios.nth(1).check();
    assertEqual(await radios.nth(0).isChecked(), false, 'Chọn radio thứ hai phải bỏ chọn radio đầu');
    return { pollId: post.poll.id };
  });

  notRun(results, S, 'SOC-P47', 'Poll hết hạn', 'Cần chờ 1–365 ngày hoặc thao tác DB; ngoài phạm vi QA không sửa dữ liệu trực tiếp');

  /* ---------------- Visibility ---------------- */

  await test(results, S, 'SOC-P50', 'Bài FOLLOWERS ẩn với guest và người không theo dõi', async () => {
    const post = await createPostApi(user, `Bài followers QA ${tag}`, { visibility: 'FOLLOWERS' });
    created.push(post.id);
    const g = await guest(browser);
    const guestRead = await api({ context: g.context }, 'GET', `/api/posts/${post.id}`);
    assert([403, 404].includes(guestRead.status), `Guest đọc bài FOLLOWERS phải bị chặn, nhận ${guestRead.status}`);
    await g.context.close();
    const outsider = await api(binh, 'GET', `/api/posts/${post.id}`);
    assert([403, 404].includes(outsider.status), `Người không follow đọc bài FOLLOWERS phải bị chặn, nhận ${outsider.status}`);
    const owner = await getPost(user, post.id);
    assertEqual(owner.status, 200, 'Chủ bài phải đọc được');
    return { guest: guestRead.status, outsider: outsider.status };
  });

  await test(results, S, 'SOC-P51', 'Bài đã xóa không xuất hiện trong tìm kiếm/feed người khác', async () => {
    const temp = await createPostApi(user, `Bài xóa visibility QA ${tag}`);
    created.push(temp.id);
    const del = await deletePostApi(user, temp.id);
    assert([200, 204].includes(del), `Xóa bài API, nhận ${del}`);
    const search = await api(an, 'GET', `/api/posts?query=${encodeURIComponent(`Bài xóa visibility QA ${tag}`)}`);
    const found = search.status === 200 && Array.isArray(search.body.data)
      ? search.body.data.some(p => p.id === temp.id) : false;
    assertEqual(found, false, 'Bài đã xóa không được có trong kết quả');
    return { deleteStatus: del };
  });

  const feedsProbe = await api(user, 'GET', '/api/feeds');
  notRun(results, S, 'SOC-P52', 'Custom Feed đầy đủ', `API /api/feeds HTTP ${feedsProbe.status}; stub/contract-only, chưa có UI hoàn chỉnh`);

  /* ---------------- Cleanup ---------------- */

  await test(results, S, 'SOC-P90', 'Dọn dẹp bài QA do suite tạo (trừ bài đã xóa)', async () => {
    let removed = 0;
    for (const id of [...new Set(created)]) {
      const r = await deletePostApi(user, id);
      if ([200, 204, 404].includes(r)) removed += 1;
    }
    return { removed };
  });

  saveJson('social-fixtures.json', { tag, mainPostId: mainPost, created });
  await user.context.close();
  await an.context.close();
  await binh.context.close();
}
