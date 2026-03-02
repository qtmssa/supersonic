package com.tencent.supersonic.chat.server.plugin.build.superset;

import com.tencent.supersonic.chat.api.pojo.response.QueryResult;
import com.tencent.supersonic.chat.server.agent.Agent;
import com.tencent.supersonic.common.pojo.ChatApp;
import com.tencent.supersonic.common.pojo.QueryColumn;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class SupersetVizTypeSelectorTest {

    @Test
    public void testSelectLineForDateAndMetric() {
        QueryResult result = new QueryResult();
        QueryColumn dateColumn = new QueryColumn();
        dateColumn.setShowType("DATE");
        dateColumn.setType("DATE");
        QueryColumn metricColumn = new QueryColumn();
        metricColumn.setShowType("NUMBER");
        metricColumn.setType("BIGINT");
        result.setQueryColumns(Arrays.asList(dateColumn, metricColumn));
        result.setQueryResults(Arrays.asList(new HashMap<>(), new HashMap<>()));
        SupersetPluginConfig config = new SupersetPluginConfig();
        config.setVizTypeLlmEnabled(false);
        Assertions.assertEquals("echarts_timeseries_line",
                SupersetVizTypeSelector.select(config, result, null));
    }

    @Test
    public void testSelectTableWhenEmpty() {
        QueryResult result = new QueryResult();
        result.setQueryColumns(Collections.emptyList());
        SupersetPluginConfig config = new SupersetPluginConfig();
        config.setVizTypeLlmEnabled(false);
        Assertions.assertEquals("table", SupersetVizTypeSelector.select(config, result, null));
    }

    @Test
    public void testSelectTableWhenNull() {
        SupersetPluginConfig config = new SupersetPluginConfig();
        config.setVizTypeLlmEnabled(false);
        Assertions.assertEquals("table", SupersetVizTypeSelector.select(config, null, null));
    }

    @Test
    public void testSelectPieForSmallCategory() {
        QueryResult result = new QueryResult();
        QueryColumn categoryColumn = new QueryColumn();
        categoryColumn.setShowType("CATEGORY");
        categoryColumn.setType("STRING");
        QueryColumn metricColumn = new QueryColumn();
        metricColumn.setShowType("NUMBER");
        metricColumn.setType("DOUBLE");
        result.setQueryColumns(Arrays.asList(categoryColumn, metricColumn));
        result.setQueryResults(Arrays.asList(new HashMap<>(), new HashMap<>(), new HashMap<>()));
        SupersetPluginConfig config = new SupersetPluginConfig();
        config.setVizTypeLlmEnabled(false);
        Assertions.assertEquals("pie", SupersetVizTypeSelector.select(config, result, null));
    }

    @Test
    public void testSelectLineWhenTimeGrainDetectedFromValues() {
        QueryResult result = new QueryResult();
        QueryColumn timeColumn = new QueryColumn();
        timeColumn.setName("day");
        timeColumn.setType("STRING");
        QueryColumn metricColumn = new QueryColumn();
        metricColumn.setName("count");
        metricColumn.setType("STRING");
        HashMap<String, Object> row1 = new HashMap<>();
        row1.put("day", "2024-01-01");
        row1.put("count", 12);
        HashMap<String, Object> row2 = new HashMap<>();
        row2.put("day", "2024-01-02");
        row2.put("count", 18);
        result.setQueryColumns(Arrays.asList(timeColumn, metricColumn));
        result.setQueryResults(Arrays.asList(row1, row2));
        SupersetPluginConfig config = new SupersetPluginConfig();
        config.setVizTypeLlmEnabled(false);
        Assertions.assertEquals("echarts_timeseries_line",
                SupersetVizTypeSelector.select(config, result, null));
    }

    @Test
    public void testAllowListOverridesRule() {
        QueryResult result = new QueryResult();
        QueryColumn categoryColumn = new QueryColumn();
        categoryColumn.setShowType("CATEGORY");
        categoryColumn.setType("STRING");
        QueryColumn metricColumn = new QueryColumn();
        metricColumn.setShowType("NUMBER");
        metricColumn.setType("DOUBLE");
        result.setQueryColumns(Arrays.asList(categoryColumn, metricColumn));
        result.setQueryResults(Arrays.asList(new HashMap<>(), new HashMap<>(), new HashMap<>()));
        SupersetPluginConfig config = new SupersetPluginConfig();
        config.setVizTypeLlmEnabled(false);
        config.setVizTypeAllowList(Collections.singletonList("table"));
        Assertions.assertEquals("table", SupersetVizTypeSelector.select(config, result, null));
    }

    @Test
    public void testResolveFromModelResponse() {
        SupersetVizTypeSelector.VizTypeItem pie = new SupersetVizTypeSelector.VizTypeItem();
        pie.setVizType("pie");
        pie.setName("Pie Chart");
        SupersetVizTypeSelector.VizTypeItem bar = new SupersetVizTypeSelector.VizTypeItem();
        bar.setVizType("bar");
        bar.setName("Bar Chart");
        String response =
                "{\"viz_type\":\"Pie Chart\",\"alternatives\":[\"bar\"],\"reason\":\"match\"}";
        String resolved =
                SupersetVizTypeSelector.resolveFromModelResponse(response, Arrays.asList(pie, bar));
        Assertions.assertEquals("pie", resolved);
    }

    @Test
    public void testResolveCandidatesFromModelResponse() {
        SupersetVizTypeSelector.VizTypeItem pie = new SupersetVizTypeSelector.VizTypeItem();
        pie.setVizType("pie");
        pie.setName("Pie Chart");
        SupersetVizTypeSelector.VizTypeItem bar = new SupersetVizTypeSelector.VizTypeItem();
        bar.setVizType("bar");
        bar.setName("Bar Chart");
        SupersetVizTypeSelector.VizTypeItem table = new SupersetVizTypeSelector.VizTypeItem();
        table.setVizType("table");
        table.setName("Table");
        String response =
                "{\"viz_type\":\"Pie Chart\",\"alternatives\":[\"Bar Chart\",\"Pie Chart\",\"table\"],\"reason\":\"match\"}";
        Assertions.assertEquals(Arrays.asList("pie", "bar", "table"), SupersetVizTypeSelector
                .resolveCandidatesFromModelResponse(response, Arrays.asList(pie, bar, table)));
    }

    @Test
    public void testResolveChatModelIdUsesConfig() {
        SupersetPluginConfig config = new SupersetPluginConfig();
        config.setVizTypeLlmChatModelId(12);
        Agent agent = new Agent();
        Map<String, ChatApp> chatApps = new HashMap<>();
        chatApps.put("S2SQL_PARSER", ChatApp.builder().chatModelId(23).enable(true).build());
        agent.setChatAppConfig(chatApps);
        Assertions.assertEquals(12, SupersetVizTypeSelector.resolveChatModelId(config, agent));
    }

    @Test
    public void testResolveChatModelIdFallsBackToAgent() {
        SupersetPluginConfig config = new SupersetPluginConfig();
        Agent agent = new Agent();
        Map<String, ChatApp> chatApps = new LinkedHashMap<>();
        chatApps.put("S2SQL_PARSER", ChatApp.builder().chatModelId(33).enable(true).build());
        agent.setChatAppConfig(chatApps);
        Assertions.assertEquals(33, SupersetVizTypeSelector.resolveChatModelId(config, agent));
    }

    @Test
    public void testSelectByLlmMissingModelIdThrows() {
        QueryResult result = new QueryResult();
        QueryColumn categoryColumn = new QueryColumn();
        categoryColumn.setShowType("CATEGORY");
        categoryColumn.setType("STRING");
        QueryColumn metricColumn = new QueryColumn();
        metricColumn.setShowType("NUMBER");
        metricColumn.setType("DOUBLE");
        result.setQueryColumns(Arrays.asList(categoryColumn, metricColumn));
        result.setQueryResults(Arrays.asList(new HashMap<>(), new HashMap<>()));
        SupersetPluginConfig config = new SupersetPluginConfig();
        config.setVizTypeLlmEnabled(true);
        Assertions.assertThrows(IllegalStateException.class,
                () -> SupersetVizTypeSelector.select(config, result, null, null));
    }
}
