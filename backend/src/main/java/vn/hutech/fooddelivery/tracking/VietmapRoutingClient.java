package vn.hutech.fooddelivery.tracking;

import ch.qos.logback.classic.LoggerContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.entity.AbstractBinAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static vn.hutech.fooddelivery.tracking.TrackingRouteException.Reason.*;

@Component
class VietmapRoutingClient {
    private static final URI VIETMAP_ENDPOINT = URI.create("https://maps.vietmap.vn/api/route/v4");
    static final int MAX_RESPONSE_BYTES = 1_048_576;
    private static final String HTTP_LOGGER_PREFIX = "org.apache.hc";

    private final ObjectMapper mapper;
    private final String key;
    private final URI endpoint;
    private final int deadlineMs;
    private final CloseableHttpAsyncClient http;

    @Autowired
    VietmapRoutingClient(ObjectMapper mapper,
                         @Value("${app.tracking.route.enabled:false}") boolean enabled,
                         @Value("${VIETMAP_SERVICES_KEY:}") String key) {
        this(mapper, enabled, key, VIETMAP_ENDPOINT, 4_000);
    }

    // Allows a loopback stub without making the production host configurable.
    VietmapRoutingClient(ObjectMapper mapper, boolean enabled, String key, URI endpoint, int deadlineMs) {
        this.mapper = mapper;
        this.key = key.strip();
        this.endpoint = endpoint;
        this.deadlineMs = deadlineMs;
        this.http = enabled && !this.key.isEmpty() ? startClient() : null;
    }

    private static CloseableHttpAsyncClient startClient() {
        var manager = PoolingAsyncClientConnectionManagerBuilder.create()
                .setMaxConnTotal(TrackingRouteQuota.MAX_CONCURRENT)
                .setMaxConnPerRoute(TrackingRouteQuota.MAX_CONCURRENT)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(1))
                        .setSocketTimeout(Timeout.ofSeconds(3))
                        .build())
                .build();
        var client = HttpAsyncClients.custom()
                .setConnectionManager(manager)
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
                .setIOReactorConfig(IOReactorConfig.custom().setIoThreadCount(2).build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.ofSeconds(3))
                        .setConnectionRequestTimeout(Timeout.ofSeconds(1))
                        .setAuthenticationEnabled(false)
                        .build())
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .disableCookieManagement()
                .build();
        client.start();
        return client;
    }

    boolean isAvailable() {
        // Apache debug/wire logs can include query parameters required by VIETMAP.
        return http != null && LoggerFactory.getILoggerFactory() instanceof LoggerContext context
                && !context.getLogger(HTTP_LOGGER_PREFIX).isDebugEnabled()
                && context.getLoggerList().stream().noneMatch(logger ->
                        logger.getName().startsWith(HTTP_LOGGER_PREFIX + ".") && logger.isDebugEnabled());
    }

    RouteData getRoute(String profile, RoutePoint origin, RoutePoint destination,
                       OrderTrackingRouteResponse.Origin fix) {
        try {
            URI uri = UriComponentsBuilder.fromUri(endpoint)
                    .queryParam("apikey", "{key}")
                    .queryParam("point", "{origin}")
                    .queryParam("point", "{destination}")
                    .queryParam("points_encoded", "true")
                    .queryParam("vehicle", "{profile}")
                    .encode()
                    .buildAndExpand(key, origin.asVietmapPoint(), destination.asVietmapPoint(), profile)
                    .toUri();
            var request = new BasicHttpRequest(Method.GET, uri);
            request.setHeader(HttpHeaders.ACCEPT, "application/json");
            var pending = http.execute(new BasicRequestProducer(request, null),
                    new BasicResponseConsumer<>(new BoundedBody()), null);
            try {
                var response = pending.get(deadlineMs, TimeUnit.MILLISECONDS);
                checkStatus(response.getHead().getCode());
                return parse(response.getBody(), origin, destination, fix);
            } catch (TimeoutException ex) {
                pending.cancel(true);
                throw new TrackingRouteException(ROUTE_UPSTREAM_TIMEOUT);
            } catch (InterruptedException ex) {
                pending.cancel(true);
                Thread.currentThread().interrupt();
                throw new TrackingRouteException(ROUTE_UNAVAILABLE);
            } catch (ExecutionException ex) {
                if (ex.getCause() instanceof SocketTimeoutException) {
                    throw new TrackingRouteException(ROUTE_UPSTREAM_TIMEOUT);
                }
                throw new TrackingRouteException(ROUTE_UPSTREAM_ERROR);
            }
        } catch (TrackingRouteException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // Do not propagate transport exceptions. Their messages can include the upstream URI.
            throw new TrackingRouteException(ROUTE_UPSTREAM_ERROR);
        }
    }

    private static void checkStatus(int status) {
        if (status == 200) {
            return;
        }
        throw new TrackingRouteException(switch (status) {
            case 401, 403, 423 -> ROUTE_UNAVAILABLE;
            case 429 -> ROUTE_RATE_LIMITED;
            case 408, 504 -> ROUTE_UPSTREAM_TIMEOUT;
            default -> ROUTE_UPSTREAM_ERROR;
        });
    }

    private RouteData parse(byte[] body, RoutePoint origin, RoutePoint destination,
                            OrderTrackingRouteResponse.Origin fix) {
        if (body == null) {
            throw new TrackingRouteException(ROUTE_UPSTREAM_ERROR);
        }
        JsonNode root;
        try {
            root = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);
        } catch (IOException ex) {
            throw new TrackingRouteException(ROUTE_UPSTREAM_ERROR);
        }
        switch (root.path("code").asText()) {
            case "OK" -> { }
            case "ZERO_RESULTS" -> throw new TrackingRouteException(ROUTE_NOT_FOUND);
            case "OVER_DAILY_LIMIT" -> throw new TrackingRouteException(ROUTE_RATE_LIMITED);
            default -> throw new TrackingRouteException(ROUTE_UPSTREAM_ERROR);
        }

        var paths = root.path("paths");
        var path = paths.path(0);
        var points = path.path("points");
        var distance = path.path("distance");
        var duration = path.path("time");
        if (!path.path("points_encoded").isBoolean() || !path.path("points_encoded").booleanValue()
                || !points.isTextual() || !distance.isNumber() || !Double.isFinite(distance.doubleValue())
                || distance.doubleValue() <= 0 || !duration.isIntegralNumber() || !duration.canConvertToLong()
                || duration.longValue() <= 0) {
            throw new TrackingRouteException(ROUTE_UPSTREAM_ERROR);
        }

        return new RouteData(RoutePolyline.decode(points.textValue(), origin, destination), distance.doubleValue(),
                duration.longValue(), fix, Instant.now());
    }

    @PreDestroy
    void close() {
        if (http != null) {
            http.close(CloseMode.IMMEDIATE);
        }
    }

    record RouteData(OrderTrackingRouteResponse.Geometry geometry, double distanceMeters, long durationMs,
                     OrderTrackingRouteResponse.Origin origin, Instant generatedAt) {
    }

    private static final class BoundedBody extends AbstractBinAsyncEntityConsumer<byte[]> {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        @Override
        protected void streamStart(ContentType contentType) {
            // Provider errors can be plain text. Inspect HTTP status first.
        }

        @Override
        protected int capacityIncrement() {
            return MAX_RESPONSE_BYTES - buffer.size() + 1;
        }

        @Override
        protected void data(ByteBuffer source, boolean endOfStream) throws IOException {
            if (source.remaining() > MAX_RESPONSE_BYTES - buffer.size()) {
                throw new IOException("Route response exceeds size limit");
            }
            byte[] chunk = new byte[source.remaining()];
            source.get(chunk);
            buffer.writeBytes(chunk);
        }

        @Override
        protected byte[] generateContent() {
            return buffer.toByteArray();
        }

        @Override
        public void releaseResources() {
            buffer.reset();
        }
    }
}
