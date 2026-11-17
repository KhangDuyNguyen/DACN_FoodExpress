package vn.hutech.fooddelivery.assignment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Kiem tra contract JDBC; khong dung mock de ket luan SQL loc dung tren MySQL. */
@ExtendWith(MockitoExtension.class)
class AssignmentRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 12, 0, 0);

    @Mock
    NamedParameterJdbcTemplate jdbc;

    @Test
    void missingBackendTimeFailsBeforeAccessingDatabase() {
        var repository = new AssignmentRepository(jdbc);

        assertThrows(NullPointerException.class, () -> repository.findEligibleCandidates(null));
        verifyNoInteractions(jdbc);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 500_000_000, 999_999_999})
    void sqlBoundsAndSelectorAgreeForSecondPrecisionGpsEvenWithNanosecondNow(int nanos) {
        when(jdbc.query(anyString(), any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<DriverCandidate>>any())).thenReturn(List.of());
        var repository = new AssignmentRepository(jdbc);
        var now = NOW.plusNanos(nanos);
        repository.findEligibleCandidates(now);
        var parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(anyString(), parameters.capture(),
                org.mockito.ArgumentMatchers.<RowMapper<DriverCandidate>>any());
        var lower = ((Timestamp) parameters.getValue().getValue("oldestLocationAt")).toLocalDateTime();
        var upper = ((Timestamp) parameters.getValue().getValue("latestLocationAt")).toLocalDateTime();

        assertEquals(0, lower.getNano());
        assertEquals(0, upper.getNano());
        // Thu tat ca timestamp nguyen giay quanh hai bien, doi chieu voi thuat toan NV2.1 that.
        for (int offset = -62; offset <= 2; offset++) {
            var at = NOW.plusSeconds(offset);
            var candidate = new DriverCandidate(7, "APPROVED", true, 10.0, 106.0, at, false, false);
            boolean acceptedByBounds = !at.isBefore(lower) && !at.isAfter(upper);
            boolean acceptedBySelector = new NearestDriverSelector()
                    .selectNearest(10, 106, List.of(candidate), now).isPresent();
            assertEquals(acceptedBySelector, acceptedByBounds, "timestamp=" + at + ", now=" + now);
        }
    }

    @Test
    void mapsDriverIdCoordinatesAndBackendTimestampToExistingCandidate() throws Exception {
        var rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(7L);
        when(rs.getString("status")).thenReturn("APPROVED");
        when(rs.getBoolean("is_online")).thenReturn(true);
        when(rs.getDouble("current_lat")).thenReturn(10.7769123);
        when(rs.getDouble("current_lng")).thenReturn(106.7009123);
        when(rs.getTimestamp("last_location_at")).thenReturn(Timestamp.valueOf(NOW));
        when(jdbc.query(anyString(), any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<DriverCandidate>>any())).thenAnswer(invocation -> {
                    RowMapper<DriverCandidate> mapper = invocation.getArgument(2);
                    return List.of(mapper.mapRow(rs, 0));
                });

        var candidates = new AssignmentRepository(jdbc).findEligibleCandidates(NOW);

        assertEquals(List.of(new DriverCandidate(7, "APPROVED", true,
                10.7769123, 106.7009123, NOW, false, false)), candidates);
    }

    @Test
    void databaseFailureIsPropagatedInsteadOfReportedAsNoAvailableDriver() {
        var failure = new DataAccessResourceFailureException("Test database unavailable");
        when(jdbc.query(anyString(), any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<DriverCandidate>>any())).thenThrow(failure);

        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> new AssignmentRepository(jdbc).findEligibleCandidates(NOW)));
    }
}
