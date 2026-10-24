#!/usr/bin/env node
// Minimal SMTP sink for local E2E testing: accepts mail on port 2525 and logs
// the full conversation (including message body) to stdout / a log file.
import net from 'node:net';
import fs from 'node:fs';

const PORT = Number(process.env.SMTP_SINK_PORT || 2525);
const LOG = process.env.SMTP_SINK_LOG || 'target/smtp-sink.log';

const server = net.createServer((socket) => {
  let buffer = [];
  let inData = false;
  socket.write('220 smtp-sink.local ESMTP ready\r\n');
  socket.on('data', (chunk) => {
    const lines = chunk.toString().split('\r\n');
    for (const line of lines) {
      if (line === '') continue;
      if (!inData) {
        const cmd = line.toUpperCase();
        if (cmd.startsWith('EHLO') || cmd.startsWith('HELO')) socket.write('250-smtp-sink.local\r\n250 AUTH PLAIN\r\n');
        else if (cmd.startsWith('MAIL FROM') || cmd.startsWith('RCPT TO')) socket.write('250 OK\r\n');
        else if (cmd.startsWith('DATA')) { inData = true; socket.write('354 End data with <CR><LF>.<CR><LF>\r\n'); }
        else if (cmd.startsWith('QUIT')) { socket.write('221 Bye\r\n'); socket.end(); }
        else if (cmd.startsWith('AUTH')) socket.write('235 Authentication successful\r\n');
        else socket.write('250 OK\r\n');
      } else {
        if (line === '.') {
          inData = false;
          const msg = buffer.join('\n');
          fs.appendFileSync(LOG, `\n===== ${new Date().toISOString()} =====\n${msg}\n`);
          console.log(`[smtp-sink] captured message (${msg.length} bytes)`);
          buffer = [];
          socket.write('250 OK queued\r\n');
        } else {
          buffer.push(line);
        }
      }
    }
  });
  socket.on('error', () => {});
});

server.listen(PORT, '127.0.0.1', () => console.log(`[smtp-sink] listening on 127.0.0.1:${PORT}, log: ${LOG}`));
