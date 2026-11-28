package vn.hutech.fooddelivery.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.common.NotFoundException;

import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class NotificationInboxService {
    private static final Pattern EVENT_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,149}");
    private static final Pattern EVENT_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern ORDER_CODE = Pattern.compile("FD[0-9]{12}");

    private final NotificationInboxRepository repository;
    private final ObjectMapper json;
    private final ZoneId storageZone;

    NotificationInboxService(NotificationInboxRepository repository, ObjectMapper json,
                             @Value("${app.notification.storage-zone:Asia/Ho_Chi_Minh}") String storageZone) {
        this.repository = repository;
        this.json = json;
        this.storageZone = ZoneId.of(storageZone);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page list(long userId, int limit, boolean unreadOnly, String cursorToken) {
        requirePositive(userId, "userId");
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("limit phải nằm trong khoảng 1 đến 50");
        }
        NotificationCursor cursor = cursorToken == null ? null : NotificationCursor.decode(cursorToken);
        var rows = repository.page(userId, unreadOnly, cursor, limit + 1);
        String nextCursor = null;
        if (rows.size() > limit) {
            var last = rows.get(limit - 1);
            nextCursor = new NotificationCursor(last.createdAt(), last.id()).encode();
        }
        var items = rows.stream().limit(limit).map(this::toItem).toList();
        return new Page(items, nextCursor, repository.unreadCount(userId));
    }

    @Transactional
    public void markRead(long userId, long id) {
        requirePositive(userId, "userId");
        requirePositive(id, "id");
        if (!repository.markRead(userId, id)) {
            throw new NotFoundException("Không tìm thấy thông báo");
        }
    }

    /** Records one recipient's inbox entry. Callers own event delivery and transaction propagation. */
    @Transactional
    public Recorded record(Draft draft) {
        requirePositive(draft.userId(), "userId");
        if (draft.type() == null || !matches(EVENT_KEY, draft.eventKey()) || !matches(EVENT_TYPE, draft.eventType())) {
            throw new IllegalArgumentException("Loại hoặc mã sự kiện thông báo không hợp lệ");
        }
        requireText(draft.title(), "title", 150);
        requireText(draft.body(), "body", 500);
        String dataJson = serializePayload(draft.eventType(), draft.data());
        String key = draft.eventKey().toLowerCase(Locale.ROOT);
        var createdAt = LocalDateTime.ofInstant(Instant.now(), storageZone).truncatedTo(ChronoUnit.SECONDS);
        try {
            long id = repository.insert(draft.userId(), draft.type().name(), key, draft.title(), draft.body(),
                    dataJson, createdAt);
            return new Recorded(id, true);
        } catch (DuplicateKeyException ex) {
            // MySQL duplicate entry. Only a matching recipient and event can count as a replay.
            if (ex.getMostSpecificCause() instanceof SQLException sql && sql.getErrorCode() == 1062) {
                return repository.existingEvent(draft.userId(), key)
                        .map(id -> new Recorded(id, false)).orElseThrow(() -> ex);
            }
            throw ex;
        }
    }

    private String serializePayload(String eventType, Data data) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("eventType", eventType);
        if (data != null) {
            if (data.orderId() != null) {
                requirePositive(data.orderId(), "orderId");
                payload.put("orderId", data.orderId());
            }
            if (data.orderCode() != null) {
                if (!matches(ORDER_CODE, data.orderCode())) {
                    throw new IllegalArgumentException("orderCode không hợp lệ");
                }
                payload.put("orderCode", data.orderCode());
            }
            if (data.offerId() != null) {
                requirePositive(data.offerId(), "offerId");
                payload.put("offerId", data.offerId());
            }
            if (data.expiresAt() != null) {
                if (data.expiresAt().toString().length() > 30) {
                    throw new IllegalArgumentException("expiresAt không hợp lệ");
                }
                payload.put("expiresAt", data.expiresAt().toString());
            }
        }
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không thể mã hóa dữ liệu thông báo", ex);
        }
    }

    private Item toItem(NotificationInboxRepository.Row row) {
        JsonNode payload = readPayload(row.dataJson());
        var data = new LinkedHashMap<String, Object>();
        String eventType = textField(payload, "eventType");
        addPositiveId(data, payload, "orderId");
        addPositiveId(data, payload, "offerId");
        String orderCode = textField(payload, "orderCode");
        if (matches(ORDER_CODE, orderCode)) {
            data.put("orderCode", orderCode);
        }
        String expiresAt = textField(payload, "expiresAt");
        if (expiresAt != null && expiresAt.length() <= 30) {
            try {
                data.put("expiresAt", Instant.parse(expiresAt));
            } catch (DateTimeException ignored) {
                // Ignore an invalid legacy field without dropping the notification.
            }
        }
        return new Item(row.id(), row.type(), matches(EVENT_TYPE, eventType) ? eventType : null,
                row.title(), row.body(), row.isRead(), row.createdAt().atZone(storageZone).toInstant(), data);
    }

    private JsonNode readPayload(String dataJson) {
        if (dataJson != null) {
            try {
                JsonNode parsed = json.readTree(dataJson);
                if (parsed != null && parsed.isObject()) {
                    return parsed;
                }
            } catch (JsonProcessingException ignored) {
                // A legacy payload must not prevent access to the rest of the inbox.
            }
        }
        return json.createObjectNode();
    }

    private static String textField(JsonNode payload, String field) {
        JsonNode value = payload.path(field);
        return value.isTextual() ? value.textValue() : null;
    }

    private static void addPositiveId(Map<String, Object> data, JsonNode payload, String field) {
        JsonNode value = payload.path(field);
        if (value.isIntegralNumber() && value.canConvertToLong() && value.longValue() > 0) {
            data.put(field, value.longValue());
        }
    }

    private static boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }

    private static void requirePositive(long value, String field) {
        if (value < 1) {
            throw new IllegalArgumentException(field + " phải lớn hơn 0");
        }
    }

    private static void requireText(String value, String field, int limit) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > limit) {
            throw new IllegalArgumentException(field + " không hợp lệ");
        }
    }

    public enum Type { ORDER, PROMOTION, SYSTEM }
    public record Data(Long orderId, String orderCode, Long offerId, Instant expiresAt) {}
    public record Draft(long userId, Type type, String eventKey, String eventType, String title, String body, Data data) {}
    public record Recorded(long id, boolean created) {}
    public record Item(long id, String type, String eventType, String title, String body, boolean isRead,
                       Instant createdAt, Map<String, Object> data) {}
    public record Page(List<Item> items, String nextCursor, long unreadCount) {}
}
