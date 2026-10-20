package com.aloute.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebSocketSessionRegistry {

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(WebSocketSession session) {
        if (session != null && session.getId() != null) {
            sessions.put(session.getId(), session);
            log.debug("WebSocket transport session registered: {}", session.getId());
        }
    }

    public void unregister(WebSocketSession session) {
        if (session != null && session.getId() != null) {
            sessions.remove(session.getId());
            log.debug("WebSocket transport session unregistered: {}", session.getId());
        }
    }

    public void closeSession(String sessionId) {
        if (sessionId == null) return;
        WebSocketSession session = sessions.remove(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
                log.info("WebSocket transport session {} closed for policy violation", sessionId);
            } catch (Exception ex) {
                log.warn("Error closing WebSocket transport session {}: {}", sessionId, ex.getMessage());
            }
        }
    }

    public WebSocketSession getSession(String sessionId) {
        if (sessionId == null) return null;
        return sessions.get(sessionId);
    }
}
