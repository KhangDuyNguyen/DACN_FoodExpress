package vn.hutech.fooddelivery.notification;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.hutech.fooddelivery.security.AuthUser;

import java.util.Set;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasAnyRole('CUSTOMER', 'DRIVER', 'MERCHANT')")
class NotificationInboxController {
    private static final Set<String> QUERY_PARAMS = Set.of("limit", "unreadOnly", "cursor");
    private final NotificationInboxService inbox;

    NotificationInboxController(NotificationInboxService inbox) {
        this.inbox = inbox;
    }

    @GetMapping
    public ResponseEntity<NotificationInboxService.Page> list(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String unreadOnly,
            @RequestParam(required = false) String cursor,
            @RequestParam MultiValueMap<String, String> query) {
        if (query.entrySet().stream().anyMatch(param ->
                !QUERY_PARAMS.contains(param.getKey()) || param.getValue().size() != 1)) {
            throw new IllegalArgumentException("Tham số thông báo không hợp lệ");
        }
        if (limit != null && !limit.matches("[1-9][0-9]?")) {
            throw new IllegalArgumentException("limit phải nằm trong khoảng 1 đến 50");
        }
        if (unreadOnly != null && !Set.of("true", "false").contains(unreadOnly)) {
            throw new IllegalArgumentException("unreadOnly phải là true hoặc false");
        }
        var page = inbox.list(user.id(), limit == null ? 20 : Integer.parseInt(limit),
                Boolean.parseBoolean(unreadOnly), cursor);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(page);
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        inbox.markRead(user.id(), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
