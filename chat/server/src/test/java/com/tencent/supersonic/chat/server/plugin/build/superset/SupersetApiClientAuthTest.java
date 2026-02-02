package com.tencent.supersonic.chat.server.plugin.build.superset;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

public class SupersetApiClientAuthTest {

    @Test
    public void testBuildHeadersWithoutAuth() {
        SupersetPluginConfig config = new SupersetPluginConfig();
        config.setAuthEnabled(false);
        config.setApiKey("token-123");
        SupersetApiClient client = new SupersetApiClient(config);

        HttpHeaders headers = client.buildHeaders();
        Assertions.assertFalse(headers.containsKey("Authorization"));
    }

    @Test
    public void testBuildHeadersWithAuth() {
        SupersetPluginConfig config = new SupersetPluginConfig();
        config.setAuthEnabled(true);
        config.setApiKey("token-123");
        SupersetApiClient client = new SupersetApiClient(config);

        HttpHeaders headers = client.buildHeaders();
        Assertions.assertEquals("Bearer token-123", headers.getFirst("Authorization"));
    }
}
