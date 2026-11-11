package vn.hutech.fooddelivery.driver;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.common.PageResponse;
import vn.hutech.fooddelivery.driver.earnings.DriverEarningsReadRepository;
import vn.hutech.fooddelivery.driver.earnings.DriverEarningsResponse;
import vn.hutech.fooddelivery.driver.order.DriverOrderHistoryItem;
import vn.hutech.fooddelivery.driver.order.DriverOrderRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

@Service
@PreAuthorize("hasRole('DRIVER')")
public class DriverReadService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);
    private final DriverOrderRepository orders;
    private final DriverEarningsReadRepository earnings;

    public DriverReadService(DriverOrderRepository orders, DriverEarningsReadRepository earnings) {
        this.orders = orders;
        this.earnings = earnings;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DriverEarningsResponse earnings(long userId, String from, String to) {
        LocalDate first = parseDate(from, "from"), last = parseDate(to, "to");
        if (first != null && last != null && first.isAfter(last)) throw badRequest("from không được sau to");
        long driverId = driverId(userId);
        Long start = first == null ? null : first.atStartOfDay(DriverReadTime.VIETNAM).toEpochSecond();
        Long end = last == null ? null : last.plusDays(1).atStartOfDay(DriverReadTime.VIETNAM).toEpochSecond();
        if ((start != null || end != null) && !earnings.supportsDateBounds(start, end)) {
            throw badRequest("Khoảng ngày nằm ngoài khả năng lưu thời gian của database");
        }
        var items = earnings.findEarnings(driverId, start, end);
        BigDecimal base = BigDecimal.ZERO, bonus = BigDecimal.ZERO;
        for (var item : items) {
            base = base.add(item.amount());
            bonus = bonus.add(item.bonus());
        }
        return new DriverEarningsResponse(first, last, DriverReadTime.VIETNAM.getId(), base, bonus,
                base.add(bonus), items);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<DriverOrderHistoryItem> history(long userId, int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw badRequest("page phải >= 0 và size từ 1 đến 50");
        long driverId = driverId(userId);
        long count = orders.countDeliveredHistory(driverId);
        return PageResponse.of(orders.findDeliveredHistory(driverId, size, (long) page * size), page, size, count);
    }

    private long driverId(long userId) {
        return orders.findDriverId(userId).orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ tài xế"));
    }

    private static LocalDate parseDate(String value, String field) {
        if (value == null) return null;
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw badRequest(field + " phải có dạng yyyy-MM-dd");
        try {
            return LocalDate.parse(value, DATE);
        } catch (DateTimeParseException invalid) {
            throw badRequest(field + " không phải ngày hợp lệ");
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
