package vn.hutech.fooddelivery.notification;

/** Shared destination and identity rules for the STOMP adapter and Notification publisher. */
public final class NotificationRealtimeChannel {
    public static final String USER_PREFIX = "/user";
    public static final String QUEUE_PREFIX = "/queue";
    public static final String QUEUE = QUEUE_PREFIX + "/notifications";
    public static final String SUBSCRIPTION = USER_PREFIX + QUEUE;

    private NotificationRealtimeChannel() {}

    public static String principalName(long userId) {
        return "user:" + userId;
    }

    public static boolean supportsRole(String role) {
        return "CUSTOMER".equals(role) || "MERCHANT".equals(role);
    }
}
