package vn.hutech.fooddelivery.tracking;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class StompAuthorizationLifecycle {
    private final StompAuthorizationSessions sessions;
    private final Map<String, WebSocketSession> transports = new ConcurrentHashMap<>();
    private final java.util.Set<String> pendingCloses = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "stomp-authorization-reaper");
        thread.setDaemon(true);
        return thread;
    });

    public StompAuthorizationLifecycle(StompAuthorizationSessions sessions) { this.sessions = sessions; }

    @PostConstruct
    void start() { worker.scheduleWithFixedDelay(this::reap, 5, 5, TimeUnit.SECONDS); }

    void reap() {
        int count = Math.min(64, sessions.size());
        long started = System.nanoTime();
        for (int index = 0; index < count; index++) {
            var state = sessions.next();
            if (state == null) break;
            boolean allowed = sessions.authorized(state, null);
            for (var registration : state.destinations.values()) {
                if (!allowed) break;
                allowed = sessions.authorized(state, registration.destination);
            }
            if (!allowed) revoke(state);
            sessions.rotate(state);
            if (System.nanoTime() - started >= TimeUnit.MILLISECONDS.toNanos(100)) break;
        }
    }

    void revoke(StompAuthorizationSessions.Session state) {
        if (state == null) return;
        state.revoked.set(true);
        if (state.closing.compareAndSet(false, true)) requestClose(state.id, state);
    }

    void missingState(String id) { if (id != null) requestClose(id, null); }

    private void requestClose(String id, StompAuthorizationSessions.Session state) {
        WebSocketSession transport = transports.get(id);
        if (transport == null || !pendingCloses.add(id)) return;
        worker.execute(() -> close(id, transport, state));
    }

    private void close(String id, WebSocketSession transport, StompAuthorizationSessions.Session state) {
        try {
            if (transport != null && transport.isOpen()) transport.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException exception) {
            // State stays revoked even if the peer or transport fails during close.
        } finally {
            if (!transport.isOpen()) sessions.disconnected(state);
            else if (state != null) state.closing.set(false);
            pendingCloses.remove(id);
        }
    }

    public WebSocketHandler decorate(WebSocketHandler delegate) {
        return new WebSocketHandlerDecorator(delegate) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                synchronized (transports) {
                    if (transports.size() >= StompAuthorizationSessions.MAX_SESSIONS
                            || transports.putIfAbsent(session.getId(), session) != null) {
                        session.close(CloseStatus.POLICY_VIOLATION);
                        return;
                    }
                }
                try { super.afterConnectionEstablished(session); }
                catch (Exception exception) {
                    transports.remove(session.getId(), session);
                    sessions.disconnected(session.getId());
                    throw exception;
                }
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                try { super.afterConnectionClosed(session, status); }
                finally {
                    if (transports.remove(session.getId(), session)) sessions.disconnected(session.getId());
                }
            }
        };
    }

    public int transportCount() { return transports.size(); }

    @PreDestroy
    void stop() { worker.shutdownNow(); }
}
