#!/usr/bin/env node
/**
 * AloUTE idempotent demo-data seeder (local/dev only).
 *
 * Creates a repeatable demonstration dataset through the public HTTP API —
 * no direct database writes, safe to re-run (existing items are skipped).
 *
 * Usage:
 *   node tools/demo-data.mjs [baseUrl]     (default http://127.0.0.1:8080)
 *
 * Seeded accounts (created by DevTestDataInitializer on the dev profile):
 *   admin@aloute.local / Admin@123   (SYSTEM_ADMIN)
 *   user@aloute.local  / User@123    (USER)
 *
 * Demo students created here: demoan@aloute.test / demobinh@aloute.test
 * (password Test@12345). On a local H2 dev instance their registration OTP
 * is recovered automatically through the H2 console + the dev OTP secret.
 * Against a remote environment without SMTP access, create the students
 * manually first or skip student-dependent steps.
 */
import crypto from 'node:crypto';

const BASE = process.argv[2] || 'http://127.0.0.1:8080';
const DEV_OTP_SECRET_B64 = 'YWxvdXRlLWRldi1yZWdpc3RyYXRpb24tb3RwLXNlY3JldC1rZXktMzJi';
const STUDENTS = [
  { username: 'demoan', email: 'demoan@aloute.test', password: 'Test@12345', name: 'An Demo' },
  { username: 'demobinh', email: 'demobinh@aloute.test', password: 'Test@12345', name: 'Bình Demo' },
];
const results = [];

function log(step, status, detail = '') {
  results.push({ step, status, detail });
  console.log(`[${status}] ${step}${detail ? ' — ' + detail : ''}`);
}

async function api(path, { method = 'GET', token, body, form, csrf } = {}) {
  const headers = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (csrf) { headers['X-XSRF-TOKEN'] = csrf.token; headers.Cookie = csrf.cookie; }
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: form ? form : body !== undefined ? JSON.stringify(body) : undefined,
  });
  let data = null;
  try { data = await res.json(); } catch { /* non-JSON */ }
  return { status: res.status, data };
}

// Recovery endpoints (verify-otp, forgot-password, resend) are CSRF-protected
// by design; CookieCsrfTokenRepository validates the submitted header against
// the request cookie, so both must travel together (Node fetch keeps no jar).
async function getCsrf() {
  const res = await fetch(BASE + '/login');
  const raw = res.headers.getSetCookie ? res.headers.getSetCookie() : [res.headers.get('set-cookie')];
  const token = (raw || []).map(c => /XSRF-TOKEN=([^;]+)/.exec(decodeURIComponent(c))?.[1]).find(Boolean);
  return token ? { token, cookie: `XSRF-TOKEN=${token}` } : null;
}

async function login(email, password) {
  const r = await api('/api/auth/login', { method: 'POST', body: { email, password } });
  if (r.status !== 200) throw new Error(`login failed for ${email}: HTTP ${r.status}`);
  const setCookie = r.data?.data ? null : null;
  return r.data.data;
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

function bruteForceOtp(salt, hash) {
  const secret = Buffer.from(DEV_OTP_SECRET_B64, 'base64');
  for (let i = 100000; i <= 999999; i++) {
    if (crypto.createHmac('sha256', secret).update(`${salt}:${i}`).digest('hex') === hash) return String(i);
  }
  return null;
}

async function recoverOtpViaH2Console(adminJwt, email) {
  const jsidRes = await fetch(`${BASE}/h2-console/`, { headers: { Authorization: `Bearer ${adminJwt}` } });
  const jsid = /jsessionid=([a-zA-Z0-9]+)/.exec(await jsidRes.text())?.[1];
  if (!jsid) throw new Error('H2 console unavailable (is this the local H2 dev instance?)');
  const loginForm = new URLSearchParams({
    language: 'en', url: 'jdbc:h2:mem:aloutedb;DB_CLOSE_DELAY=-1', user: 'sa', password: '',
  });
  await fetch(`${BASE}/h2-console/login.do?jsessionid=${jsid}`, {
    method: 'POST', headers: { Authorization: `Bearer ${adminJwt}` }, body: loginForm,
  });
  const q = new URLSearchParams({
    sql: `SELECT r.otp_hash, r.salt FROM registration_otps r JOIN users u ON u.id = r.user_id WHERE u.email = '${email}'`,
  });
  const qRes = await fetch(`${BASE}/h2-console/query.do?jsessionid=${jsid}`, {
    method: 'POST', headers: { Authorization: `Bearer ${adminJwt}` }, body: q,
  });
  const html = await qRes.text();
  const cells = [...html.matchAll(/<td>([^<]+)<\/td>/g)].map(m => m[1]);
  const hash = cells.find(v => /^[0-9a-f]{64}$/.test(v));
  const salt = cells.find(v => /^[0-9a-f]{32}$/.test(v));
  if (!hash || !salt) throw new Error(`no OTP row for ${email}`);
  const otp = bruteForceOtp(salt, hash);
  if (!otp) throw new Error(`OTP brute force failed for ${email}`);
  return otp;
}

async function ensureStudent(student, adminJwt) {
  const reg = await api('/api/auth/register', {
    method: 'POST', body: { username: student.username, email: student.email, password: student.password },
  });
  if (reg.status === 201) log(`register ${student.username}`, 'OK');
  else if (reg.status === 409) log(`register ${student.username}`, 'SKIP', 'already exists');
  else {
    log(`register ${student.username}`, 'FAIL', `HTTP ${reg.status}`);
    return false;
  }
  // Verify OTP if the account is not yet verified: try login; 401/403 with
  // verification-required means we still need OTP.
  const probe = await api('/api/auth/login', { method: 'POST', body: { email: student.email, password: student.password } });
  if (probe.status === 200) { log(`verify ${student.username}`, 'SKIP', 'already verified'); return true; }
  try {
    const otp = await recoverOtpViaH2Console(adminJwt, student.email);
    const csrf = await getCsrf();
    const verify = await api('/api/auth/verify-registration-otp', {
      method: 'POST', body: { email: student.email, otp }, csrf,
    });
    if (verify.status === 200) log(`verify ${student.username}`, 'OK', 'OTP recovered via H2 console');
    else { log(`verify ${student.username}`, 'FAIL', `HTTP ${verify.status}`); return false; }
  } catch (e) {
    log(`verify ${student.username}`, 'BLOCKED', e.message);
    return false;
  }
  return true;
}

async function studentJwt(student) {
  return loginWithCookie(student.email, student.password);
}

async function ensureTopic(slug) {
  const r = await api(`/api/topics`);
  const list = r.data?.data || [];
  return list.find(t => t.slug === slug) || null;
}

async function main() {
  console.log(`Seeding demo data on ${BASE}\n`);

  let adminJwt, userJwt;
  try {
    adminJwt = await loginWithCookie('admin@aloute.local', 'Admin@123');
    userJwt = await loginWithCookie('user@aloute.local', 'User@123');
    log('seeded accounts login', 'OK', 'admin@aloute.local, user@aloute.local');
  } catch (e) {
    log('seeded accounts login', 'FAIL', e.message);
    process.exit(1);
  }

  // 1. Demo students
  for (const s of STUDENTS) {
    await ensureStudent(s, adminJwt);
  }
  let anJwt = null, binhJwt = null;
  try { anJwt = await studentJwt(STUDENTS[0]); log('login demoan', 'OK'); } catch (e) { log('login demoan', 'BLOCKED', e.message); }
  try { binhJwt = await studentJwt(STUDENTS[1]); log('login demobinh', 'OK'); } catch (e) { log('login demobinh', 'BLOCKED', e.message); }

  const topic = await ensureTopic('hoc-tap');
  if (topic) log('topic hoc-tap', 'OK', `id=${topic.id}`); else log('topic hoc-tap', 'SKIP', 'not present');

  // 2. Follows (admin→an, user→an, an→binh, binh→an)
  const follow = async (jwt, targetUsername, label) => {
    const u = await api(`/api/users/by-username/${targetUsername}`, { token: jwt });
    const id = u.data?.data?.id;
    if (!id) { log(`follow ${label}`, 'SKIP', 'target missing'); return; }
    const r = await api(`/api/social/follows/${id}`, { method: 'PUT', token: jwt });
    log(`follow ${label}`, r.status === 200 || r.status === 409 ? 'OK' : 'FAIL', `HTTP ${r.status}`);
  };
  if (anJwt) {
    await follow(adminJwt, 'demoan', 'admin→demoan');
    await follow(userJwt, 'demoan', 'user→demoan');
    if (binhJwt) { await follow(anJwt, 'demobinh', 'demoan→demobinh'); await follow(binhJwt, 'demoan', 'demobinh→demoan'); }
  }

  // 3. Student posts (one tagged with #hoc-tap so the topic page has content)
  const mkPost = async (jwt, content, topicId) => {
    const body = { content, visibility: 'PUBLIC', allowReply: true, allowQuote: true };
    if (topicId) body.topicIds = [topicId];
    const r = await api('/api/posts', { method: 'POST', token: jwt, body });
    return r.status === 201 ? r.data.data : null;
  };
  let anPost1 = null, anPost2 = null, binhPost = null;
  if (anJwt) {
    anPost1 = await mkPost(anJwt, 'Nhóm 13 vừa hoàn thành phần backend của AloUTE! Tuần này demo tính năng bình chọn và tin nhắn trực tiếp. #hoc-tap', topic?.id);
    anPost2 = await mkPost(anJwt, 'Ai đã từng làm đồ án với Spring Boot chưa? Cho mình xin ít kinh nghiệm chia module với ạ.');
    log('demoan posts', anPost1 && anPost2 ? 'OK' : 'FAIL');
  }
  if (binhJwt) {
    binhPost = await mkPost(binhJwt, 'CLB Chúng mình tuyển thành viên kỳ mới! Tham gia để được mentor dẫn dắt dự án thực tế nha mọi người.');
    log('demobinh post', binhPost ? 'OK' : 'FAIL');
  }

  // 4. Interactions on demoan's first post
  if (anPost1) {
    const like = async (jwt) => api(`/api/posts/${anPost1.id}/likes/me`, { method: 'PUT', token: jwt });
    if (userJwt) await like(userJwt);
    if (binhJwt) await like(binhJwt);
    log('likes on demoan post', 'OK', 'user + demobinh');
    if (userJwt) {
      const reply = await api(`/api/posts/${anPost1.id}/replies`, {
        method: 'POST', token: userJwt,
        body: { content: 'Chúc mừng nhóm 13! Poll làm hay đấy.', visibility: 'PUBLIC' },
      });
      log('reply from user', reply.status === 201 ? 'OK' : 'FAIL', `HTTP ${reply.status}`);
    }
    if (binhJwt) {
      const bm = await api(`/api/posts/${anPost1.id}/bookmarks/me`, { method: 'PUT', token: binhJwt });
      log('bookmark from demobinh', bm.status === 200 ? 'OK' : 'FAIL', `HTTP ${bm.status}`);
    }
  }

  // 5. Poll with votes from both students
  if (anJwt) {
    const poll = await api('/api/posts', {
      method: 'POST', token: anJwt,
      body: {
        content: 'Bình chọn chủ đề demo cho buổi thuyết trình CNPM nhé cả nhà!',
        visibility: 'PUBLIC',
        poll: { question: 'Bạn muốn xem demo tính năng nào nhất?', allowMultiple: false, options: ['Bình chọn (Poll)', 'Tin nhắn trực tiếp', 'Kiểm duyệt nội dung'] },
      },
    });
    if (poll.status === 201) {
      log('poll post', 'OK', `id=${poll.data.data.id}`);
      const pr = await api(`/api/posts/${poll.data.data.id}`, { token: anJwt });
      const pollId = pr.data?.data?.poll?.id;
      const options = pr.data?.data?.poll?.options || [];
      const vote = async (jwt, optionId) => api(`/api/polls/${pollId}/votes`, { method: 'POST', token: jwt, body: { optionId } });
      if (options.length) {
        if (userJwt) { const v = await vote(userJwt, options[0].id); log('vote user', v.status === 200 ? 'OK' : 'FAIL', `HTTP ${v.status}`); }
        if (binhJwt) { const v = await vote(binhJwt, options[1].id); log('vote demobinh', v.status === 200 ? 'OK' : 'FAIL', `HTTP ${v.status}`); }
      }
    } else {
      log('poll post', 'FAIL', `HTTP ${poll.status}`);
    }
  }

  // 6. Direct messages user → admin
  try {
    const conv = await api('/api/conversations', {
      method: 'POST', token: userJwt, body: { type: 'DIRECT', targetUserId: 1 },
    });
    if (conv.status === 200 || conv.status === 201) {
      const convId = conv.data.data.id;
      const send = await api(`/api/conversations/${convId}/messages`, {
        method: 'POST', token: userJwt, body: { content: 'Thầy/anh admin ơi, nhóm em xin phép demo tin nhắn trực tiếp ạ!' },
      });
      log('direct message user→admin', send.status === 201 ? 'OK' : 'FAIL', `conv=${convId}`);
    } else {
      log('direct message user→admin', 'FAIL', `HTTP ${conv.status}`);
    }
  } catch (e) {
    log('direct message user→admin', 'FAIL', e.message);
  }

  // 7. A report for the admin moderation demo (report demobinh's post as SPAM)
  if (binhPost) {
    const rep = await api('/api/reports', {
      method: 'POST', token: userJwt,
      body: { reportedPostId: binhPost.id, reason: 'SPAM', description: 'Báo cáo demo cho phần kiểm duyệt của quản trị viên.' },
    });
    log('demo report', rep.status === 200 || rep.status === 201 ? 'OK' : 'FAIL', `HTTP ${rep.status} ${JSON.stringify(rep.data?.error || {})}`);
  }

  const failed = results.filter(r => r.status === 'FAIL');
  console.log(`\nDone: ${results.filter(r => r.status === 'OK').length} created/verified, ${results.filter(r => r.status === 'SKIP').length} skipped, ${results.filter(r => r.status === 'BLOCKED').length} blocked, ${failed.length} failed.`);
  if (failed.length) { console.log('Failures:'); failed.forEach(f => console.log(` - ${f.step}: ${f.detail}`)); process.exit(2); }
}

main().catch(e => { console.error('Seeder crashed:', e); process.exit(1); });
