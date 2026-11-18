package vn.hutech.fooddelivery.tracking;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class OrderTrackingLocationPublisherTest {
    private static final long ORDER_ID = 73;
    private static final BigDecimal LAT = new BigDecimal("10.7801234");
    private static final BigDecimal LNG = new BigDecimal("106.7005678");
    private static final LocalDateTime RECORDED_AT = LocalDateTime.parse("2026-11-18T10:15:30.123");

    @Mock
    SimpMessagingTemplate messaging;

    @InjectMocks
    OrderTrackingLocationPublisher publisher;

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-18T10:15:00", "2026-11-18T10:15:30.123"})
    void publishesStoredCoordinatesAndIsoLocalTimestampToOrderTopic(String recordedAt) {
        publisher.publish(ORDER_ID, new OrderTrackingResponse.Location(LAT, LNG, LocalDateTime.parse(recordedAt)));

        verify(messaging).convertAndSend("/topic/orders/73/location",
                new OrderTrackingLocationPublisher.LocationMessage(LAT, LNG, recordedAt));
        verifyNoMoreInteractions(messaging);
    }

    @ParameterizedTest
    @MethodSource("invalidLocations")
    void invalidInputDoesNotPublish(Long orderId, OrderTrackingResponse.Location location) {
        publisher.publish(orderId, location);

        verifyNoInteractions(messaging);
    }

    private static Stream<Arguments> invalidLocations() {
        var location = new OrderTrackingResponse.Location(LAT, LNG, RECORDED_AT);
        return Stream.of(
                Arguments.of(null, location),
                Arguments.of(0L, location),
                Arguments.of(-1L, location),
                Arguments.of(ORDER_ID, null),
                Arguments.of(ORDER_ID, new OrderTrackingResponse.Location(null, LNG, RECORDED_AT)),
                Arguments.of(ORDER_ID, new OrderTrackingResponse.Location(LAT, null, RECORDED_AT)),
                Arguments.of(ORDER_ID, new OrderTrackingResponse.Location(LAT, LNG, null)),
                Arguments.of(ORDER_ID, new OrderTrackingResponse.Location(new BigDecimal("90.01"), LNG, RECORDED_AT)),
                Arguments.of(ORDER_ID, new OrderTrackingResponse.Location(LAT, new BigDecimal("-180.01"), RECORDED_AT))
        );
    }
}
