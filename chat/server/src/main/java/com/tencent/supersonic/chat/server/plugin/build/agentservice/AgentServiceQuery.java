package com.tencent.supersonic.chat.server.plugin.build.agentservice;

import com.alibaba.fastjson.JSON;
import com.tencent.supersonic.chat.api.pojo.response.QueryResult;
import com.tencent.supersonic.chat.server.plugin.ChatPlugin;
import com.tencent.supersonic.chat.server.plugin.PluginParseConfig;
import com.tencent.supersonic.chat.server.plugin.PluginParseResult;
import com.tencent.supersonic.chat.server.plugin.PluginQueryManager;
import com.tencent.supersonic.chat.server.plugin.build.PluginSemanticQuery;
import com.tencent.supersonic.chat.server.plugin.build.WebBase;
import com.tencent.supersonic.common.pojo.Constants;
import com.tencent.supersonic.common.util.ContextUtils;
import com.tencent.supersonic.common.util.JsonUtil;
import com.tencent.supersonic.headless.api.pojo.response.QueryState;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class AgentServiceQuery extends PluginSemanticQuery {

    public static final String QUERY_MODE = "AGENT_SERVICE";
    private static final String PROP_AGENT_ID = "agentId";
    private static final String PROP_CHAT_ID = "chatId";
    private static final String RESPONSE_FIELD = "data";
    private static final String STATUS_FIELD = "status";
    private static final String ERROR_FIELD = "error";

    private RestTemplate restTemplate;

    public AgentServiceQuery() {
        PluginQueryManager.register(QUERY_MODE, this);
    }

    @Override
    public QueryResult build() {
        QueryResult queryResult = new QueryResult();
        queryResult.setQueryMode(QUERY_MODE);
        Map<String, Object> properties = parseInfo.getProperties();
        if (properties == null) {
            queryResult.setQueryState(QueryState.INVALID);
            queryResult.setErrorMsg("agent service properties missing");
            return queryResult;
        }
        PluginParseResult pluginParseResult = JsonUtil.toObject(
                JsonUtil.toString(properties.get(Constants.CONTEXT)), PluginParseResult.class);
        if (pluginParseResult == null || pluginParseResult.getPlugin() == null) {
            queryResult.setQueryState(QueryState.INVALID);
            queryResult.setErrorMsg("agent service plugin config missing");
            return queryResult;
        }
        ChatPlugin plugin = pluginParseResult.getPlugin();
        WebBase webBase = JsonUtil.toObject(plugin.getConfig(), WebBase.class);
        String url = webBase != null ? webBase.getUrl() : null;
        String agentServiceId = buildAgentServiceId(plugin);
        String sessionId = buildSessionId(properties);
        String message = pluginParseResult.getQueryText();
        if (StringUtils.isBlank(agentServiceId)) {
            queryResult.setQueryState(QueryState.INVALID);
            queryResult.setErrorMsg("agent service agent_id is blank");
            return queryResult;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("agent_id", agentServiceId);
        payload.put("conversation_id", sessionId);
        payload.put("message", message);

        try {
            Object responseBody = callAgentService(url, payload);
            Map<String, Object> responseMap =
                    JsonUtil.toMap(JsonUtil.toString(responseBody), String.class, Object.class);
            queryResult.setResponse(responseMap);
            if (responseMap != null && responseMap.get(STATUS_FIELD) != null
                    && !"ok".equalsIgnoreCase(String.valueOf(responseMap.get(STATUS_FIELD)))) {
                queryResult.setQueryState(QueryState.INVALID);
                String errorMsg = responseMap.get(ERROR_FIELD) != null
                        ? String.valueOf(responseMap.get(ERROR_FIELD))
                        : "agent service response status is not ok";
                queryResult.setErrorMsg(errorMsg);
            } else if (responseMap != null && responseMap.get(RESPONSE_FIELD) != null) {
                queryResult.setTextResult(String.valueOf(responseMap.get(RESPONSE_FIELD)));
                queryResult.setQueryState(QueryState.SUCCESS);
            } else {
                queryResult.setQueryState(QueryState.INVALID);
                String errorMsg = responseMap != null && responseMap.get(ERROR_FIELD) != null
                        ? String.valueOf(responseMap.get(ERROR_FIELD))
                        : "agent service response missing 'data'";
                queryResult.setErrorMsg(errorMsg);
            }
        } catch (Exception e) {
            log.info("agent service request error:{}", e.getMessage());
            queryResult.setQueryState(QueryState.SEARCH_EXCEPTION);
            queryResult.setErrorMsg(e.getMessage());
        }
        return queryResult;
    }

    protected Object callAgentService(String url, Map<String, Object> payload) {
        if (StringUtils.isBlank(url)) {
            throw new IllegalArgumentException("agent service url is blank");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(payload), headers);
        URI requestUrl = UriComponentsBuilder.fromHttpUrl(url).build().encode().toUri();
        restTemplate = ContextUtils.getBean(RestTemplate.class);
        ResponseEntity<String> responseEntity =
                restTemplate.exchange(requestUrl, HttpMethod.POST, entity, String.class);
        return responseEntity != null ? responseEntity.getBody() : null;
    }

    private String buildSessionId(Map<String, Object> properties) {
        Object agentId = properties.get(PROP_AGENT_ID);
        Object chatId = properties.get(PROP_CHAT_ID);
        return String.valueOf(agentId) + String.valueOf(chatId);
    }

    private String buildAgentServiceId(ChatPlugin plugin) {
        if (plugin == null || StringUtils.isBlank(plugin.getParseModeConfig())) {
            return null;
        }
        PluginParseConfig parseConfig =
                JsonUtil.toObject(plugin.getParseModeConfig(), PluginParseConfig.class);
        return parseConfig != null ? parseConfig.getName() : null;
    }
}
