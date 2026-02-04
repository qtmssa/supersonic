package com.tencent.supersonic.chat.server.plugin.build.superset;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SupersetApiClientTest {

    @Test
    public void testExtractDashboards() {
        SupersetPluginConfig config = new SupersetPluginConfig();
        SupersetApiClient client = new SupersetApiClient(config);
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("id", 12L);
        dashboard.put("dashboard_title", "Sales Overview");
        result.add(dashboard);
        response.put("result", result);

        List<SupersetDashboardInfo> dashboards = client.extractDashboards(response);
        Assertions.assertEquals(1, dashboards.size());
        Assertions.assertEquals(12L, dashboards.get(0).getId());
        Assertions.assertEquals("Sales Overview", dashboards.get(0).getTitle());
    }

    @Test
    public void testBuildTagPayload() {
        SupersetPluginConfig config = new SupersetPluginConfig();
        SupersetApiClient client = new SupersetApiClient(config);
        List<String> tags = Arrays.asList("supersonic", "supersonic-single-chart");

        Map<String, Object> payload = client.buildTagPayload(tags);

        Assertions.assertTrue(payload.containsKey("properties"));
        Object properties = payload.get("properties");
        Assertions.assertTrue(properties instanceof Map);
        Map<?, ?> propertiesMap = (Map<?, ?>) properties;
        Assertions.assertEquals(tags, propertiesMap.get("tags"));
    }

    @Test
    public void testBuildGuestTokenPayloadIncludesUser() {
        SupersetPluginConfig config = new SupersetPluginConfig();
        SupersetApiClient client = new SupersetApiClient(config);
        Map<String, Object> resource = new HashMap<>();
        resource.put("type", "dashboard");
        resource.put("id", "uuid-123");

        Map<String, Object> payload = client.buildGuestTokenPayload(resource);

        Assertions.assertTrue(payload.containsKey("user"));
        Object user = payload.get("user");
        Assertions.assertTrue(user instanceof Map);
        Map<?, ?> userMap = (Map<?, ?>) user;
        Assertions.assertEquals("supersonic-guest", userMap.get("username"));
        Assertions.assertEquals("Supersonic", userMap.get("first_name"));
        Assertions.assertEquals("Guest", userMap.get("last_name"));
    }

    @Test
    public void testListDatabasesBuildsExpectedUrl() throws Exception {
        SupersetPluginConfig config = new SupersetPluginConfig();
        config.setBaseUrl("http://localhost:8088/");
        SupersetApiClient client = new SupersetApiClient(config);
        RecordingFactory factory = new RecordingFactory("{\"result\":[]}");
        replaceRestTemplate(client, new RestTemplate(factory));

        client.listDatabases("jwt-token", 1, 50);

        Assertions.assertEquals("http://localhost:8088/api/v1/database/?q=(page:1,page_size:50)",
                factory.getLastUri().toString());
        Assertions.assertEquals("Bearer jwt-token",
                factory.getLastRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    public void testListDatabasesAccessTokenRequired() {
        SupersetPluginConfig config = new SupersetPluginConfig();
        config.setBaseUrl("http://localhost:8088");
        SupersetApiClient client = new SupersetApiClient(config);

        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
                () -> client.listDatabases(" ", 0, 10));
        Assertions.assertTrue(ex.getMessage().contains("access token"));
    }

    private void replaceRestTemplate(SupersetApiClient client, RestTemplate restTemplate)
            throws Exception {
        Field field = SupersetApiClient.class.getDeclaredField("restTemplate");
        field.setAccessible(true);
        field.set(client, restTemplate);
    }

    private static class RecordingFactory implements ClientHttpRequestFactory {
        private final byte[] body;
        private URI lastUri;
        private HttpMethod lastMethod;
        private RecordingRequest lastRequest;

        RecordingFactory(String responseJson) {
            this.body = responseJson.getBytes();
        }

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
            this.lastUri = uri;
            this.lastMethod = httpMethod;
            this.lastRequest = new RecordingRequest(uri, httpMethod, body);
            return lastRequest;
        }

        URI getLastUri() {
            return lastUri;
        }

        HttpMethod getLastMethod() {
            return lastMethod;
        }

        RecordingRequest getLastRequest() {
            return lastRequest;
        }
    }

    private static class RecordingRequest implements ClientHttpRequest {
        private final URI uri;
        private final HttpMethod method;
        private final byte[] responseBody;
        private final HttpHeaders headers = new HttpHeaders();

        RecordingRequest(URI uri, HttpMethod method, byte[] responseBody) {
            this.uri = uri;
            this.method = method;
            this.responseBody = responseBody;
        }

        @Override
        public ClientHttpResponse execute() {
            return new ClientHttpResponse() {
                @Override
                public HttpStatus getStatusCode() {
                    return HttpStatus.OK;
                }

                @Override
                public int getRawStatusCode() {
                    return HttpStatus.OK.value();
                }

                @Override
                public String getStatusText() {
                    return HttpStatus.OK.getReasonPhrase();
                }

                @Override
                public void close() {}

                @Override
                public InputStream getBody() {
                    return new ByteArrayInputStream(responseBody);
                }

                @Override
                public HttpHeaders getHeaders() {
                    HttpHeaders responseHeaders = new HttpHeaders();
                    responseHeaders.setContentType(MediaType.APPLICATION_JSON);
                    return responseHeaders;
                }
            };
        }

        @Override
        public OutputStream getBody() throws IOException {
            return OutputStream.nullOutputStream();
        }

        @Override
        public HttpMethod getMethod() {
            return method;
        }

        @Override
        public URI getURI() {
            return uri;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }
}
