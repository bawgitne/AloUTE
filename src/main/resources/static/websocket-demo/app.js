/**
 * AloUTE — WebSocket STOMP Dual-Client Simulator & Inspector
 * Tác giả: Antigravity Pair Programmer
 */

(function () {
  'use strict';

  // State Management
  const state = {
    mode: 'simulator', // 'simulator' | 'live'
    liveUrl: 'http://localhost:8080/ws',
    conversationId: 10,
    u1: {
      id: 1,
      name: 'Ngô Anh Bằng',
      token: 'jwt.token.bang.user-1',
      connected: false,
      client: null,
      typingTimer: null
    },
    u2: {
      id: 2,
      name: 'Phạm Minh Quân',
      token: 'jwt.token.quan.user-2',
      connected: false,
      client: null,
      typingTimer: null
    }
  };

  // DOM Elements
  const el = {
    modeSelect: document.getElementById('mode-select'),
    // User 1
    u1Dot: document.getElementById('u1-dot'),
    u1StatusText: document.getElementById('u1-status-text'),
    u1BtnConnect: document.getElementById('u1-btn-connect'),
    u1Messages: document.getElementById('u1-messages'),
    u1Input: document.getElementById('u1-input'),
    u1BtnSend: document.getElementById('u1-btn-send'),
    u1Typing: document.getElementById('u1-typing'),
    // User 2
    u2Dot: document.getElementById('u2-dot'),
    u2StatusText: document.getElementById('u2-status-text'),
    u2BtnConnect: document.getElementById('u2-btn-connect'),
    u2Messages: document.getElementById('u2-messages'),
    u2Input: document.getElementById('u2-input'),
    u2BtnSend: document.getElementById('u2-btn-send'),
    u2Typing: document.getElementById('u2-typing'),
    // Inspector Terminal
    logTerminal: document.getElementById('log-terminal'),
    btnClearLogs: document.getElementById('btn-clear-logs')
  };

  function nowTime() {
    const d = new Date();
    return d.toTimeString().split(' ')[0] + '.' + String(d.getMilliseconds()).padStart(3, '0');
  }

  // Log STOMP Frame to Inspector
  function logFrame(type, dir, tag, title, rawFrame) {
    const div = document.createElement('div');
    div.className = `log-entry ${type}`;
    div.innerHTML = `
      <div>
        <span class="log-time">[${nowTime()}]</span>
        <span class="log-tag ${dir}">${tag}</span>
        <strong>${title}</strong>
      </div>
      <div class="log-body">${escapeHtml(rawFrame)}</div>
    `;
    el.logTerminal.appendChild(div);
    el.logTerminal.scrollTop = el.logTerminal.scrollHeight;
  }

  function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  // =========================================================================
  // 1. CONNECTION LIFECYCLE (SIMULATOR & LIVE)
  // =========================================================================

  function toggleConnection(userKey) {
    const user = state[userKey];
    if (user.connected) {
      disconnectUser(userKey);
    } else {
      connectUser(userKey);
    }
  }

  function connectUser(userKey) {
    const user = state[userKey];
    const isU1 = userKey === 'u1';
    const otherKey = isU1 ? 'u2' : 'u1';

    // 1. Log CONNECT Frame
    const connectFrame = 
`CONNECT
accept-version:1.1,1.2
heart-beat:10000,10000
Authorization:Bearer ${user.token}
aloute_access_token:${user.token}

^@`;
    logFrame('connect', 'out', 'SEND ➔', `STOMP CONNECT (${user.name})`, connectFrame);

    if (state.mode === 'simulator') {
      setTimeout(() => {
        // 2. Log CONNECTED Frame
        const connectedFrame = 
`CONNECTED
version:1.2
heart-beat:10000,10000
user-name:${user.id}

^@`;
        logFrame('connected', 'in', 'RECV ⬅', `STOMP CONNECTED (Session Authenticated for User ${user.id})`, connectedFrame);

        // 3. Log SUBSCRIBE Frames
        const subFrames = 
`SUBSCRIBE
id:sub-msg-${user.id}
destination:/user/queue/messages

SUBSCRIBE
id:sub-typing-${user.id}
destination:/user/queue/typing

SUBSCRIBE
id:sub-read-${user.id}
destination:/user/queue/read-receipts

SUBSCRIBE
id:sub-notif-${user.id}
destination:/user/queue/notifications

^@`;
        logFrame('subscribe', 'out', 'SUB ➔', `${user.name} Subscribed to Private Queues`, subFrames);

        user.connected = true;
        updateUserUI(userKey);
      }, 300);

    } else {
      // Live Mode with SockJS + Stomp
      if (typeof Stomp === 'undefined' || typeof SockJS === 'undefined') {
        alert('Thư viện SockJS/Stomp chưa sẵn sàng. Vui lòng thử lại hoặc dùng Simulator Mode.');
        return;
      }
      try {
        const socket = new SockJS(state.liveUrl);
        const stompClient = Stomp.over(socket);
        stompClient.debug = null; // suppress internal console noise

        const headers = {
          'Authorization': 'Bearer ' + user.token,
          'aloute_access_token': user.token
        };

        stompClient.connect(headers, function (frame) {
          logFrame('connected', 'in', 'RECV ⬅', `STOMP CONNECTED (Live WebSocket for ${user.name})`, String(frame));

          // Subscriptions
          stompClient.subscribe('/user/queue/messages', function (msg) {
            handleIncomingMessage(userKey, JSON.parse(msg.body));
          });
          stompClient.subscribe('/user/queue/typing', function (msg) {
            handleIncomingTyping(userKey, JSON.parse(msg.body));
          });
          stompClient.subscribe('/user/queue/read-receipts', function (msg) {
            handleIncomingRead(userKey, JSON.parse(msg.body));
          });
          stompClient.subscribe('/user/queue/notifications', function (msg) {
            handleIncomingNotification(userKey, JSON.parse(msg.body));
          });

          user.client = stompClient;
          user.connected = true;
          updateUserUI(userKey);
        }, function (error) {
          logFrame('connect', 'in', 'ERR ✖', `Lỗi kết nối Live WebSocket: ${error}`, String(error));
          disconnectUser(userKey);
        });

      } catch (err) {
        logFrame('connect', 'in', 'ERR ✖', `Lỗi kết nối: ${err.message}`, String(err));
      }
    }
  }

  function disconnectUser(userKey) {
    const user = state[userKey];
    if (user.client) {
      try { user.client.disconnect(); } catch (_) {}
      user.client = null;
    }
    user.connected = false;
    updateUserUI(userKey);

    const disFrame = `DISCONNECT\n\n^@`;
    logFrame('connect', 'out', 'SEND ➔', `STOMP DISCONNECT (${user.name})`, disFrame);
  }

  function updateUserUI(userKey) {
    const user = state[userKey];
    const isU1 = userKey === 'u1';
    const dot = isU1 ? el.u1Dot : el.u2Dot;
    const text = isU1 ? el.u1StatusText : el.u2StatusText;
    const btn = isU1 ? el.u1BtnConnect : el.u2BtnConnect;
    const input = isU1 ? el.u1Input : el.u2Input;
    const btnSend = isU1 ? el.u1BtnSend : el.u2BtnSend;

    if (user.connected) {
      dot.className = 'status-dot connected';
      text.textContent = 'Đã kết nối STOMP';
      btn.textContent = 'Ngắt kết nối';
      input.disabled = false;
      btnSend.disabled = false;
    } else {
      dot.className = 'status-dot';
      text.textContent = 'Chưa kết nối';
      btn.textContent = 'Kết nối STOMP';
      input.disabled = true;
      btnSend.disabled = true;
    }
  }

  // =========================================================================
  // 2. CHAT MESSAGING (SEND & RECEIVE)
  // =========================================================================

  function sendMessage(fromKey) {
    const fromUser = state[fromKey];
    if (!fromUser.connected) return;

    const isU1 = fromKey === 'u1';
    const toKey = isU1 ? 'u2' : 'u1';
    const toUser = state[toKey];
    const input = isU1 ? el.u1Input : el.u2Input;
    const content = input.value.trim();
    if (!content) return;

    input.value = '';

    // Step 1: Client gửi STOMP frame SEND
    const destination = `/app/chat.send/${state.conversationId}`;
    const payload = JSON.stringify({ content: content });
    const sendFrame = 
`SEND
destination:${destination}
content-type:application/json

${payload}
^@`;
    logFrame('send', 'out', 'SEND ➔', `${fromUser.name} gửi tin nhắn ➔ ${destination}`, sendFrame);

    // Step 2: Render message on sender UI immediately (Outgoing)
    renderMessage(fromKey, 'outgoing', content, 'Vừa xong', true);

    // Step 3: Server xử lý Persist-Before-Publish và phát tới người nhận
    const messageDto = {
      id: Date.now(),
      conversationId: state.conversationId,
      senderId: fromUser.id,
      senderName: fromUser.name,
      content: content,
      createdAt: new Date().toISOString(),
      isRead: false
    };

    if (state.mode === 'simulator') {
      setTimeout(() => {
        if (toUser.connected) {
          const recvFrame = 
`MESSAGE
destination:/user/queue/messages
content-type:application/json
message-id:msg-${messageDto.id}

${JSON.stringify(messageDto, null, 2)}
^@`;
          logFrame('message', 'in', 'RECV ⬅', `Spring Broker đẩy tin nhắn tới ${toUser.name} via /user/queue/messages`, recvFrame);
          renderMessage(toKey, 'incoming', content, 'Vừa xong');

          // Tự động gửi Read Receipt từ người nhận sau 1s
          setTimeout(() => {
            triggerReadReceipt(toKey);
          }, 1000);
        } else {
          logFrame('message', 'sys', 'INFO ℹ', `Người nhận ${toUser.name} đang Offline. Tin nhắn đã lưu an toàn vào DB SQL!`, 'Persisted in database, waiting for recipient login.');
        }
      }, 250);

    } else {
      if (fromUser.client) {
        fromUser.client.send(destination, {}, payload);
      }
    }
  }

  function renderMessage(userKey, type, text, time, showStatus) {
    const list = userKey === 'u1' ? el.u1Messages : el.u2Messages;
    const bubble = document.createElement('div');
    bubble.className = `message-bubble ${type}`;
    bubble.innerHTML = `
      <div class="message-text">${escapeHtml(text)}</div>
      <div class="message-meta">
        <span>${time}</span>
        ${showStatus ? '<span class="status-check">✓ Đã gửi</span>' : ''}
      </div>
    `;
    list.appendChild(bubble);
    list.scrollTop = list.scrollHeight;
  }

  // =========================================================================
  // 3. TYPING INDICATOR
  // =========================================================================

  function sendTyping(fromKey) {
    const fromUser = state[fromKey];
    if (!fromUser.connected) return;

    const isU1 = fromKey === 'u1';
    const toKey = isU1 ? 'u2' : 'u1';
    const toUser = state[toKey];

    const dest = `/app/chat.typing/${state.conversationId}`;
    const frame = 
`SEND
destination:${dest}

{}
^@`;
    logFrame('send', 'out', 'TYPE ➔', `${fromUser.name} phát sự kiện đang gõ phím`, frame);

    if (state.mode === 'simulator') {
      if (toUser.connected) {
        const typingEl = isU1 ? el.u2Typing : el.u1Typing;
        typingEl.textContent = `✎ ${fromUser.name} đang gõ tin nhắn...`;

        clearTimeout(toUser.typingTimer);
        toUser.typingTimer = setTimeout(() => {
          typingEl.textContent = '';
        }, 2000);
      }
    } else {
      if (fromUser.client) {
        fromUser.client.send(dest, {}, JSON.stringify({}));
      }
    }
  }

  // =========================================================================
  // 4. READ RECEIPTS
  // =========================================================================

  function triggerReadReceipt(readerKey) {
    const reader = state[readerKey];
    if (!reader.connected) return;

    const isU1 = readerKey === 'u1';
    const senderKey = isU1 ? 'u2' : 'u1';
    const senderUser = state[senderKey];

    const dest = `/app/chat.read/${state.conversationId}`;
    const frame = 
`SEND
destination:${dest}

{}
^@`;
    logFrame('send', 'out', 'READ ➔', `${reader.name} gửi xác nhận đã đọc`, frame);

    if (state.mode === 'simulator') {
      const recvFrame = 
`MESSAGE
destination:/user/queue/read-receipts
content-type:application/json

{"conversationId":${state.conversationId},"readByUserId":${reader.id}}
^@`;
      logFrame('message', 'in', 'RECV ⬅', `${senderUser.name} nhận thông báo Đã xem từ ${reader.name}`, recvFrame);

      // Cập nhật nhãn "✓✓ Đã xem" trên khung chat người gửi
      const senderMessages = isU1 ? el.u2Messages : el.u1Messages;
      const lastCheck = senderMessages.querySelector('.message-bubble.outgoing:last-child .status-check');
      if (lastCheck) {
        lastCheck.textContent = '✓✓ Đã xem';
        lastCheck.style.color = 'var(--accent-cyan)';
      }
    } else {
      if (reader.client) {
        reader.client.send(dest, {}, JSON.stringify({}));
      }
    }
  }

  // =========================================================================
  // 5. NOTIFICATION TRIGGER
  // =========================================================================

  function triggerNotification(actorKey, type, desc) {
    const actor = state[actorKey];
    if (!actor.connected) return;

    const isU1 = actorKey === 'u1';
    const targetKey = isU1 ? 'u2' : 'u1';
    const target = state[targetKey];

    const notif = {
      id: Date.now(),
      type: type,
      content: `${actor.name} ${desc}`,
      actorId: actor.id,
      actorName: actor.name,
      createdAt: new Date().toISOString()
    };

    const frame = 
`MESSAGE
destination:/user/queue/notifications
content-type:application/json

${JSON.stringify(notif, null, 2)}
^@`;
    logFrame('message', 'in', 'NOTIF 🔔', `Push Notification tới ${target.name} via /user/queue/notifications`, frame);

    // Hiển thị toast thông báo trên góc của Target User
    renderMessage(targetKey, 'incoming', `🔔 [THÔNG BÁO MỚI]: ${notif.content}`, 'Vừa xong');
  }

  // =========================================================================
  // 6. EVENT LISTENERS SETUP
  // =========================================================================

  function init() {
    // Mode switcher
    el.modeSelect.addEventListener('change', (e) => {
      state.mode = e.target.value;
      disconnectUser('u1');
      disconnectUser('u2');
      logFrame('connected', 'sys', 'MODE ⚙', `Chuyển sang chế độ: ${state.mode.toUpperCase()}`, `Current Mode: ${state.mode}`);
    });

    // User 1 Controls
    el.u1BtnConnect.addEventListener('click', () => toggleConnection('u1'));
    el.u1BtnSend.addEventListener('click', () => sendMessage('u1'));
    el.u1Input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') sendMessage('u1');
      else sendTyping('u1');
    });

    // User 2 Controls
    el.u2BtnConnect.addEventListener('click', () => toggleConnection('u2'));
    el.u2BtnSend.addEventListener('click', () => sendMessage('u2'));
    el.u2Input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') sendMessage('u2');
      else sendTyping('u2');
    });

    // Quick Trigger Buttons
    document.querySelectorAll('[data-trigger]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const actor = btn.dataset.user;
        const type = btn.dataset.trigger;
        if (type === 'like') {
          triggerNotification(actor, 'POST_LIKE', 'đã thích bài viết của bạn.');
        } else if (type === 'reply') {
          triggerNotification(actor, 'POST_REPLY', 'đã trả lời bình luận của bạn.');
        } else if (type === 'read') {
          triggerReadReceipt(actor);
        }
      });
    });

    // Clear Console
    el.btnClearLogs.addEventListener('click', () => {
      el.logTerminal.innerHTML = '';
      logFrame('connected', 'sys', 'CLEAR ⌫', 'Đã xóa trắng STOMP Console Inspector', '');
    });

    // Tự động kết nối cả 2 người dùng khi mở trang để xem ngay
    setTimeout(() => {
      connectUser('u1');
      setTimeout(() => connectUser('u2'), 250);
    }, 400);
  }

  window.addEventListener('DOMContentLoaded', init);

})();
