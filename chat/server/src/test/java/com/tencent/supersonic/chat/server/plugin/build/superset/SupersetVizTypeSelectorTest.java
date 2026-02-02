package com.tencent.supersonic.chat.server.plugin.build.superset;

import com.tencent.supersonic.chat.api.pojo.response.QueryResult;
import com.tencent.supersonic.common.pojo.QueryColumn;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

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
}
