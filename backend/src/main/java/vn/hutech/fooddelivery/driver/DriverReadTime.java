package vn.hutech.fooddelivery.driver;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** DATETIME duoc MySQL doi thanh epoch theo gio phien DB; API doc hien gio Viet Nam. */
public final class DriverReadTime {
    public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private DriverReadTime() {
    }

    public static LocalDateTime vietnamTime(ResultSet rs, String epochColumn) throws SQLException {
        BigDecimal epoch = rs.getBigDecimal(epochColumn);
        return epoch == null ? null : LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch.longValueExact()), VIETNAM);
    }
}
