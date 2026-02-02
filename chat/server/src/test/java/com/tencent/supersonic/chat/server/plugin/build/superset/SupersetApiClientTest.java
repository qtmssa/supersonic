package com.tencent.supersonic.chat.server.plugin.build.superset;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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

}
