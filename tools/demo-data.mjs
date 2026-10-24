#!/usr/bin/env node
/**
 * AloUTE idempotent demo-data seeder — EXPLICITLY AUTHORIZED LOCAL DEVELOPMENT ONLY.
 *
 * Hard fail-closed rules:
 *   1. The target must be a loopback address (127.0.0.1 / localhost / ::1).
 *      Remote or production hosts are refused unconditionally.
 *   2. Demo student accounts are NOT created here. They come from
 *      DevTestDataInitializer on the local dev profile (pre-verified), so this
 *      tool contains no OTP recovery, no H2-console access and no credential
 *      bypass of any kind. If the students are missing, student-dependent
 *      steps are skipped with a BLOCKED marker.
 *
 * Every item is created through the public HTTP API (no direct DB writes) and
 * is detected by exact demo-content match before creating, so re-runs never
 * duplicate posts, polls, votes, replies, messages or reports, and never touch
 * non-demo data.
 *
 * Usage:
 *   node tools/demo-data.mjs [baseUrl]     (default http://127.0.0.1:8080)
 *
 * Accounts:
 *   admin@aloute.local / Admin@123  and  user@aloute.local / User@123
 *     (DevTestDataInitializer, dev profile)
 *   demoan@aloute.test / Test@12345  and  demobinh@aloute.test / Test@12345
 *     (DevTestDataInitializer, dev profile)
 */

const BASE = process.argv[2] || 'http://127.0.0.1:8080';

function failClosed(message) {
  console.error(`[demo-data] REFUSED: ${message}`);
  process.exit(1);
}

let targetHost;
try {
  targetHost = new URL(BASE).hostname.toLowerCase().replace(/^\[/, '').replace(/\]$/, '');
} catch {
  failClosed(`"${BASE}" is not a valid URL.`);
}
if (!['127.0.0.1', 'localhost', '::1'].includes(targetHost)) {
  failClosed(`demo seeding is restricted to explicitly authorized local development; ` +
    `"${targetHost}" is not a loopback host. Run against http://127.0.0.1:8080 (or localhost).`);
}

const DEMO_CONTENT = {
  anPost1: 'Nhóm 13 vừa hoàn thành phần backend của AloUTE! Tuần này demo tính năng bình chọn và tin nhắn trực tiếp. #hoc-tap',
  anPost2: 'Ai đã từng làm đồ án với Spring Boot chưa? Cho mình xin ít kinh nghiệm chia module với ạ.',
  binhPost: 'CLB Chúng mình tuyển thành viên kỳ mới! Tham gia để được mentor dẫn dắt dự án thực tế nha mọi người.',
  pollPost: 'Bình chọn chủ đề demo cho buổi thuyết trình CNPM nhé cả nhà!',
  pollQuestion: 'Bạn muốn xem demo tính năng nào nhất?',
  pollOptions: ['Bình chọn (Poll)', 'Tin nhắn trực tiếp', 'Kiểm duyệt nội dung'],
  reply: 'Chúc mừng nhóm 13! Poll làm hay đấy.',
  message: 'Thầy/anh admin ơi, nhóm em xin phép demo tin nhắn trực tiếp ạ!',
  reportDescription: 'Báo cáo demo cho phần kiểm duyệt của quản trị viên.',
};

const results = [];

function log(step, status, detail = '') {
  results.push({ step, status, detail });
  console.log(`[${status}] ${step}${detail ? ' — ' + detail : ''}`);
}

async function api(path, { method = 'GET', token, body } = {}) {
  const headers = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  let data = null;
  try { data = await res.json(); } catch { /* non-JSON */ }
  return { status: res.status, data };
}

// login needs the cookie; wrap fetch to capture Set-Cookie for the JWT.
async function loginWithCookie(email, password) {
  const res = await fetch(BASE + '/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const raw = res.headers.getSetCookie ? res.headers.getSetCookie() : [res.headers.get('set-cookie')];
  const jwt = (raw || []).map(c => /aloute_access_token=([^;]+)/.exec(c)?.[1]).find(Boolean);
  if (!jwt) throw new Error(`no JWT cookie for ${email} (HTTP ${res.status})`);
  return jwt;
}

async function userIdByUsername(username, token) {
  const r = await api(`/api/users/by-username/${username}`, { token });
  return r.data?.data?.id || null;
}

// Recent top-level posts of a user (public endpoint), keyed by exact content.
async function postsByContent(username, token) {
  const id = await userIdByUsername(username, token);
  if (!id) return { id: null, byContent: new Map() };
  const r = await api(`/api/users/${id}/posts?size=50`, { token });
  const list = r.data?.data || [];
  return { id, byContent: new Map(list.map(p => [p.content, p])) };
}

function pageItems(path, token) {
  return api(path, { token }).then(r => r.data?.data || []);
}

async function main() {
  console.log(`Seeding demo data on ${BASE} (local dev)\n`);

  let adminJwt, userJwt;
  try {
    adminJwt = await loginWithCookie('admin@aloute.local', 'Admin@123');
    userJwt = await loginWithCookie('user@aloute.local', 'User@123');
    log('seeded accounts login', 'OK', 'admin@aloute.local, user@aloute.local');
  } catch (e) {
    log('seeded accounts login', 'FAIL', e.message);
    process.exit(1);
  }

  // 1. Demo students — provided by DevTestDataInitializer (dev profile), never by
  //    this tool: no registration and therefore no OTP verification step here.
  let anJwt = null, binhJwt = null;
  try { anJwt = await loginWithCookie('demoan@aloute.test', 'Test@12345'); log('login demoan', 'OK'); }
  catch (e) { log('login demoan', 'BLOCKED', 'missing — start the app with the dev profile (DevTestDataInitializer)'); }
  try { binhJwt = await loginWithCookie('demobinh@aloute.test', 'Test@12345'); log('login demobinh', 'OK'); }
  catch (e) { log('login demobinh', 'BLOCKED', 'missing — start the app with the dev profile (DevTestDataInitializer)'); }

  const topicR = await api('/api/topics');
  const topic = (topicR.data?.data || []).find(t => t.slug === 'hoc-tap') || null;
  if (topic) log('topic hoc-tap', 'OK', `id=${topic.id}`); else log('topic hoc-tap', 'SKIP', 'not present');

  // 2. Follows (PUT is state-setting, hence idempotent: 200 or 409 both mean "following")
  const follow = async (jwt, targetUsername, label) => {
    const id = await userIdByUsername(targetUsername, jwt);
    if (!id) { log(`follow ${label}`, 'SKIP', 'target missing'); return; }
    const r = await api(`/api/social/follows/${id}`, { method: 'PUT', token: jwt });
    log(`follow ${label}`, r.status === 200 || r.status === 409 ? 'OK' : 'FAIL', `HTTP ${r.status}`);
  };
  if (anJwt) {
    await follow(adminJwt, 'demoan', 'admin→demoan');
    await follow(userJwt, 'demoan', 'user→demoan');
    if (binhJwt) { await follow(anJwt, 'demobinh', 'demoan→demobinh'); await follow(binhJwt, 'demoan', 'demobinh→demoan'); }
  }

  // 3. Student posts — created only if the exact demo content is absent from the
  //    author's own feed; non-demo posts are never read as demo markers.
  const mkPost = async (jwt, content, topicId) => {
    const body = { content, visibility: 'PUBLIC', allowReply: true, allowQuote: true };
    if (topicId) body.topicIds = [topicId];
    const r = await api('/api/posts', { method: 'POST', token: jwt, body });
    return r.status === 201 ? r.data.data : null;
  };
  let anPost1 = null, anPost2 = null, binhPost = null, pollPost = null;
  if (anJwt) {
    const an = await postsByContent('demoan', anJwt);
    anPost1 = an.byContent.get(DEMO_CONTENT.anPost1) || await mkPost(anJwt, DEMO_CONTENT.anPost1, topic?.id);
    anPost2 = an.byContent.get(DEMO_CONTENT.anPost2) || await mkPost(anJwt, DEMO_CONTENT.anPost2);
    log('demoan posts', anPost1 && anPost2 ? 'OK' : 'FAIL',
      an.byContent.has(DEMO_CONTENT.anPost1) && an.byContent.has(DEMO_CONTENT.anPost2) ? 'already present' : '');
    const binh = binhJwt ? await postsByContent('demobinh', binhJwt) : { byContent: new Map() };
    binhPost = binh.byContent.get(DEMO_CONTENT.binhPost) || (binhJwt ? await mkPost(binhJwt, DEMO_CONTENT.binhPost) : null);
    log('demobinh post', binhPost ? 'OK' : 'FAIL', binh.byContent.has(DEMO_CONTENT.binhPost) ? 'already present' : '');
    const existingPoll = an.byContent.get(DEMO_CONTENT.pollPost);
    if (existingPoll?.poll) {
      pollPost = existingPoll;
      log('poll post', 'OK', `id=${pollPost.id} (already present)`);
    }
  }
  if (anJwt && !pollPost) {
    const poll = await api('/api/posts', {
      method: 'POST', token: anJwt,
      body: {
        content: DEMO_CONTENT.pollPost,
        visibility: 'PUBLIC',
        poll: { question: DEMO_CONTENT.pollQuestion, allowMultiple: false, options: DEMO_CONTENT.pollOptions },
      },
    });
    if (poll.status === 201) {
      pollPost = poll.data.data;
      log('poll post', 'OK', `id=${pollPost.id}`);
    } else {
      log('poll post', 'FAIL', `HTTP ${poll.status}`);
    }
  }

  // 4. Interactions on demoan's first post (likes/bookmark are PUT = idempotent;
  //    the reply is deduped by exact content)
  if (anPost1) {
    const like = (jwt) => api(`/api/posts/${anPost1.id}/likes/me`, { method: 'PUT', token: jwt });
    if (userJwt) await like(userJwt);
    if (binhJwt) await like(binhJwt);
    log('likes on demoan post', 'OK', 'user + demobinh');
    if (userJwt) {
      const replies = await pageItems(`/api/posts/${anPost1.id}/replies?size=50`, userJwt);
      if (replies.some(p => p.content === DEMO_CONTENT.reply)) {
        log('reply from user', 'OK', 'already present');
      } else {
        const reply = await api(`/api/posts/${anPost1.id}/replies`, {
          method: 'POST', token: userJwt,
          body: { content: DEMO_CONTENT.reply, visibility: 'PUBLIC' },
        });
        log('reply from user', reply.status === 201 ? 'OK' : 'FAIL', `HTTP ${reply.status}`);
      }
    }
    if (binhJwt) {
      const bm = await api(`/api/posts/${anPost1.id}/bookmarks/me`, { method: 'PUT', token: binhJwt });
      log('bookmark from demobinh', bm.status === 200 ? 'OK' : 'FAIL', `HTTP ${bm.status}`);
    }
  }

  // 5. Poll votes — 409 POLL_ALREADY_VOTED on a re-run is the expected skip
  if (pollPost?.poll) {
    const pollId = pollPost.poll.id;
    const options = pollPost.poll.options || [];
    const vote = async (jwt, optionId) => api(`/api/polls/${pollId}/votes`, { method: 'POST', token: jwt, body: { optionId } });
    const voteStep = async (jwt, optionId, label) => {
      if (!jwt || !optionId) { log(`vote ${label}`, 'SKIP', 'unavailable'); return; }
      const v = await vote(jwt, optionId);
      if (v.status === 200) log(`vote ${label}`, 'OK', `HTTP 200`);
      else if (v.status === 409) log(`vote ${label}`, 'OK', 'already voted');
      else log(`vote ${label}`, 'FAIL', `HTTP ${v.status}`);
    };
    await voteStep(userJwt, options[0]?.id, 'user');
    await voteStep(binhJwt, options[1]?.id, 'demobinh');
  } else if (anJwt) {
    log('poll votes', 'SKIP', 'no demo poll available');
  }

  // 6. Direct message user → admin, deduped by exact message content in the
  //    existing DIRECT conversation (the conversation itself is reused)
  try {
    const adminId = await userIdByUsername('admin', adminJwt);
    const convs = await pageItems('/api/conversations?size=50', userJwt);
    let convId = null;
    for (const c of convs) {
      if (c.type !== 'DIRECT') continue;
      const other = c.otherParticipant || c.participant;
      if (other?.id === adminId) { convId = c.id; break; }
    }
    if (!convId) {
      const conv = await api('/api/conversations', {
        method: 'POST', token: userJwt, body: { type: 'DIRECT', targetUserId: adminId },
      });
      convId = conv.status === 200 || conv.status === 201 ? conv.data.data.id : null;
      if (!convId) throw new Error(`conversation create failed: HTTP ${conv.status}`);
    }
    const messages = await pageItems(`/api/conversations/${convId}/messages?size=50`, userJwt);
    if (messages.some(m => m.content === DEMO_CONTENT.message)) {
      log('direct message user→admin', 'OK', `conv=${convId} (already present)`);
    } else {
      const send = await api(`/api/conversations/${convId}/messages`, {
        method: 'POST', token: userJwt, body: { content: DEMO_CONTENT.message },
      });
      log('direct message user→admin', send.status === 201 ? 'OK' : 'FAIL', `conv=${convId}`);
    }
  } catch (e) {
    log('direct message user→admin', 'FAIL', e.message);
  }

  // 7. A report for the admin moderation demo — deduped through the admin
  //    moderation queue (reporter = user@, target = demobinh's demo post)
  if (binhPost && userJwt) {
    try {
      const reporterId = await userIdByUsername('user', adminJwt);
      const queue = await pageItems('/api/admin/reports?targetType=POST&size=50', adminJwt);
      const already = queue.some(r => r.targetId === binhPost.id && r.reporter?.id === reporterId);
      if (already) {
        log('demo report', 'OK', 'already present');
      } else {
        const rep = await api('/api/reports', {
          method: 'POST', token: userJwt,
          body: { reportedPostId: binhPost.id, reason: 'SPAM', description: DEMO_CONTENT.reportDescription },
        });
        log('demo report', rep.status === 200 || rep.status === 201 ? 'OK' : 'FAIL',
          `HTTP ${rep.status} ${JSON.stringify(rep.data?.error || {})}`);
      }
    } catch (e) {
      log('demo report', 'FAIL', e.message);
    }
  }

  const failed = results.filter(r => r.status === 'FAIL');
  console.log(`\nDone: ${results.filter(r => r.status === 'OK').length} ok, ${results.filter(r => r.status === 'SKIP').length} skipped, ${results.filter(r => r.status === 'BLOCKED').length} blocked, ${failed.length} failed.`);
  if (failed.length) { console.log('Failures:'); failed.forEach(f => console.log(` - ${f.step}: ${f.detail}`)); process.exit(2); }
}

main().catch(e => { console.error('Seeder crashed:', e); process.exit(1); });
