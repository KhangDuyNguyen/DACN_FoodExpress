package vn.hutech.fooddelivery.tracking;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static vn.hutech.fooddelivery.tracking.TrackingRouteException.Reason.*;

@ExtendWith(OutputCaptureExtension.class)
class VietmapRoutingClientTest {
    private static final String FIXTURE_KEY = "synthetic-key+with&reserved=chars";
    private static final RoutePoint ORIGIN = new RoutePoint(38.5, -120.2);
    private static final RoutePoint DESTINATION = new RoutePoint(43.252, -126.453);
    private static final String POLYLINE = "_p~iF~ps|U_ulLnnqC_mqNvxq`@";
    private static final String ROUTE = """
            {"code":"OK","paths":[{"points_encoded":true,"points":"%s","distance":888234.5,"time":43210000}]}
            """.formatted(POLYLINE);

    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> requestQuery = new AtomicReference<>();
    private final List<VietmapRoutingClient> clients = new ArrayList<>();
    private HttpServer server;
    private URI stub;
    private volatile boolean redirect;
    private volatile int responseStatus = 200;
    private volatile String responseBody = ROUTE;
    private volatile long stallMs;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/route/v4");
        server.createContext("/", exchange -> {
            try (exchange) {
                requests.incrementAndGet();
                requestQuery.set(exchange.getRequestURI().getRawQuery());
                if (redirect) {
                    exchange.getResponseHeaders().set("Location", "/redirected");
                }
                byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(responseStatus, body.length);
                if (stallMs > 0) {
                    try {
                        Thread.sleep(stallMs);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void closeStub() {
        clients.forEach(VietmapRoutingClient::close);
        server.stop(0);
    }

    @Test
    void encodesQueryAndDecodesPolylineLongitudeFirst() {
        var fix = new OrderTrackingRouteResponse.Origin(42, ORIGIN.lat(), ORIGIN.lng(), Instant.EPOCH);
        var result = client(true, FIXTURE_KEY, 4_000).getRoute("motorcycle", ORIGIN, DESTINATION, fix);

        assertThat(requests.get()).isEqualTo(1);
        assertThat(URLDecoder.decode(requestQuery.get(), StandardCharsets.UTF_8))
                .isEqualTo("apikey=" + FIXTURE_KEY
                        + "&point=38.5,-120.2&point=43.252,-126.453&points_encoded=true&vehicle=motorcycle");
        assertThat(requestQuery.get()).contains("%2B", "%26", "%3D");
        assertThat(result.geometry().type()).isEqualTo("LineString");
        assertThat(result.geometry().coordinates())
                .containsExactly(List.of(-120.2, 38.5), List.of(-120.95, 40.7), List.of(-126.453, 43.252));
        assertThat(result.origin()).isSameAs(fix);
        assertThat(result.distanceMeters()).isEqualTo(888234.5);
        assertThat(result.durationMs()).isEqualTo(43_210_000L);
    }

    @ParameterizedTest
    @CsvSource({"401,ROUTE_UNAVAILABLE", "403,ROUTE_UNAVAILABLE", "423,ROUTE_UNAVAILABLE",
            "429,ROUTE_RATE_LIMITED", "500,ROUTE_UPSTREAM_ERROR", "504,ROUTE_UPSTREAM_TIMEOUT"})
    void mapsHttpStatusBeforeReadingProviderCode(int status, TrackingRouteException.Reason reason,
                                                 CapturedOutput output) {
        responseStatus = status;
        responseBody = FIXTURE_KEY;
        assertFailure(client(true, FIXTURE_KEY, 4_000), reason);
        assertThat(requests.get()).isEqualTo(1);
        assertThat(output.getAll()).doesNotContain(FIXTURE_KEY);
    }

    @ParameterizedTest
    @CsvSource({"ZERO_RESULTS,ROUTE_NOT_FOUND", "OVER_DAILY_LIMIT,ROUTE_RATE_LIMITED",
            "INVALID_REQUEST,ROUTE_UPSTREAM_ERROR", "MAX_POINTS_EXCEED,ROUTE_UPSTREAM_ERROR",
            "ERROR_UNKNOWN,ROUTE_UPSTREAM_ERROR", "UNRECOGNIZED,ROUTE_UPSTREAM_ERROR"})
    void mapsProviderCodeEvenWithHttp200(String code, TrackingRouteException.Reason reason) {
        responseBody = "{\"code\":\"" + code + "\",\"messages\":\"" + FIXTURE_KEY + "\"}";
        assertFailure(client(true, FIXTURE_KEY, 4_000), reason);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"code\":\"OK\",\"paths\":[]}", "not JSON",
            "{\"code\":\"OK\",\"paths\":[{\"points\":\"??\",\"points_encoded\":true,\"distance\":1,\"time\":1}]}",
            "{\"code\":\"OK\",\"paths\":[{\"points\":\"~\",\"points_encoded\":true,\"distance\":1,\"time\":1}]}"})
    void rejectsMissingEmptyOrMalformedGeometry(String body) {
        responseBody = body;
        assertFailure(client(true, FIXTURE_KEY, 4_000), ROUTE_UPSTREAM_ERROR);
    }

    @Test
    void rejectsMisalignedEndpointsInvalidMetricsAndOversizedBody() {
        var client = client(true, FIXTURE_KEY, 4_000);
        assertThatThrownBy(() -> client.getRoute("car", DESTINATION, ORIGIN, null))
                .isInstanceOfSatisfying(TrackingRouteException.class,
                        error -> assertThat(error.reason).isEqualTo(ROUTE_UPSTREAM_ERROR));
        responseBody = ROUTE.replace("43210000", "-1");
        assertFailure(client, ROUTE_UPSTREAM_ERROR);
        responseBody = ROUTE.replace("888234.5", "0");
        assertFailure(client, ROUTE_UPSTREAM_ERROR);
        responseBody = " ".repeat(VietmapRoutingClient.MAX_RESPONSE_BYTES + 1);
        assertFailure(client, ROUTE_UPSTREAM_ERROR);
    }

    @Test
    void doesNotFollowRedirectOrRetry() {
        redirect = true;
        responseStatus = 302;
        assertFailure(client(true, FIXTURE_KEY, 4_000), ROUTE_UPSTREAM_ERROR);
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void wholeRequestDeadlineIncludesBodyAndDoesNotRetry() {
        stallMs = 950;
        assertFailure(client(true, FIXTURE_KEY, 350), ROUTE_UPSTREAM_TIMEOUT);
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void connectionFailureIsSanitized() {
        server.stop(0);
        assertFailure(client(true, FIXTURE_KEY, 4_000), ROUTE_UPSTREAM_ERROR);
    }

    @Test
    void featureFlagMissingKeyAndHttpDebugLoggingDisableTransport() {
        assertThat(client(false, FIXTURE_KEY, 4_000).isAvailable()).isFalse();
        assertThat(client(true, " ", 4_000).isAvailable()).isFalse();
        var available = client(true, FIXTURE_KEY, 4_000);
        assertThat(available.isAvailable()).isTrue();
        Logger logger = (Logger) LoggerFactory.getLogger("org.apache.hc.client5.http.wire");
        Level original = logger.getLevel();
        try {
            logger.setLevel(Level.DEBUG);
            assertThat(available.isAvailable()).isFalse();
        } finally {
            logger.setLevel(original);
        }
        assertThat(requests.get()).isZero();
    }

    private VietmapRoutingClient client(boolean enabled, String key, int deadlineMs) {
        var client = new VietmapRoutingClient(new ObjectMapper(), enabled, key, stub, deadlineMs);
        clients.add(client);
        return client;
    }

    private static void assertFailure(VietmapRoutingClient client, TrackingRouteException.Reason reason) {
        assertThatThrownBy(() -> client.getRoute("car", ORIGIN, DESTINATION, null))
                .isInstanceOfSatisfying(TrackingRouteException.class, error -> {
                    assertThat(error.reason).isEqualTo(reason);
                    assertThat(error.getCause()).isNull();
                    assertThat(error.getMessage()).doesNotContain(FIXTURE_KEY);
                });
    }
}
