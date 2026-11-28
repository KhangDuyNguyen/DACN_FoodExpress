package vn.hutech.fooddelivery.tracking;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.stereotype.Component;

@Component
public class TrackingStompOutboundInterceptor implements ExecutorChannelInterceptor {
    private final StompAuthorizationSessions sessions;
    private final StompAuthorizationLifecycle lifecycle;

    public TrackingStompOutboundInterceptor(StompAuthorizationSessions sessions, StompAuthorizationLifecycle lifecycle) {
        this.sessions = sessions;
        this.lifecycle = lifecycle;
    }

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        // Runs on the outbound executor after its queue, immediately before the transport message handler.
        if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.MESSAGE) return message;
        String physical = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
        String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        String subscriptionId = SimpMessageHeaderAccessor.getSubscriptionId(message.getHeaders());
        var state = sessions.get(sessionId);
        var registration = state == null || subscriptionId == null ? null : state.destinations.get(subscriptionId);
        String logical = registration == null ? null : registration.destination;
        if (!StompAuthorizationSessions.protectedDestination(physical)
                && !StompAuthorizationSessions.protectedDestination(logical)) return message;
        boolean matchingDestination = logical != null && (logical.equals(physical)
                || ("/user/queue/notifications".equals(logical)
                    && ("/queue/notifications-user" + sessionId).equals(physical)));
        if (state == null || !matchingDestination || !sessions.authorized(state, logical)) {
            lifecycle.revoke(state);
            if (state == null) lifecycle.missingState(sessionId);
            return null;
        }
        return message;
    }
}
