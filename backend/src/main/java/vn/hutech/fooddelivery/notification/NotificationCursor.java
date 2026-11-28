package vn.hutech.fooddelivery.notification;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Base64;
import java.util.regex.Pattern;

record NotificationCursor(LocalDateTime createdAt, long id) {
    private static final DateTimeFormatter DATE = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{28,52}");
    private static final Pattern CONTENT = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}:[1-9][0-9]{0,18}");

    String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((DATE.format(createdAt) + ":" + id).getBytes(StandardCharsets.US_ASCII));
    }

    static NotificationCursor decode(String token) {
        if (!TOKEN.matcher(token).matches()) {
            throw invalidCursor();
        }
        try {
            String content = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.US_ASCII);
            if (!CONTENT.matcher(content).matches()) {
                throw invalidCursor();
            }
            LocalDateTime createdAt = LocalDateTime.parse(content.substring(0, 19), DATE);
            var cursor = new NotificationCursor(createdAt, Long.parseLong(content.substring(20)));
            if (createdAt.getYear() < 1000 || !cursor.encode().equals(token)) {
                throw invalidCursor();
            }
            return cursor;
        } catch (IllegalArgumentException | DateTimeException ex) {
            throw invalidCursor();
        }
    }

    private static IllegalArgumentException invalidCursor() {
        return new IllegalArgumentException("Cursor thông báo không hợp lệ");
    }
}
