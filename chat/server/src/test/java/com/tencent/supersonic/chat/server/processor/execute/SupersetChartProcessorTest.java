package com.tencent.supersonic.chat.server.processor.execute;

import com.tencent.supersonic.chat.api.pojo.response.QueryResult;
import com.tencent.supersonic.chat.server.plugin.build.superset.SupersetPluginConfig;
import com.tencent.supersonic.common.pojo.QueryColumn;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SupersetChartProcessorTest {

    @Test
    public void testBuildFormDataTableDefaults() {
        SupersetChartProcessor processor = new SupersetChartProcessor();
        SupersetPluginConfig config = new SupersetPluginConfig();
        QueryResult queryResult = new QueryResult();
        queryResult.setQueryColumns(Arrays.asList(new QueryColumn("id", "INT", "id"),
                new QueryColumn("name", "STRING", "name")));

        Map<String, Object> formData = processor.buildFormData(config, queryResult, "table");

        Assertions.assertEquals("raw", formData.get("query_mode"));
        Assertions.assertEquals(Arrays.asList("id", "name"), formData.get("all_columns"));
        Assertions.assertEquals(Arrays.asList("id", "name"), formData.get("columns"));
    }

    @Test
    public void testBuildFormDataNonTableUsesMetricAndGroupby() {
        SupersetChartProcessor processor = new SupersetChartProcessor();
        SupersetPluginConfig config = new SupersetPluginConfig();
        QueryResult queryResult = new QueryResult();
        queryResult.setQueryColumns(Arrays.asList(new QueryColumn("category", "STRING", "category"),
                new QueryColumn("amount", "DECIMAL", "amount")));

        Map<String, Object> formData = processor.buildFormData(config, queryResult, "bar");

        Assertions.assertEquals(Collections.singletonList("amount"), formData.get("metrics"));
        Assertions.assertEquals(Collections.singletonList("category"), formData.get("groupby"));
    }

    @Test
    public void testBuildFormDataFromResultsWhenColumnsMissing() {
        SupersetChartProcessor processor = new SupersetChartProcessor();
        SupersetPluginConfig config = new SupersetPluginConfig();
        QueryResult queryResult = new QueryResult();
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("category", "A");
        row.put("amount", 12);
        queryResult.setQueryResults(Collections.singletonList(row));

        Map<String, Object> formData = processor.buildFormData(config, queryResult, "bar");

        Assertions.assertEquals(Collections.singletonList("amount"), formData.get("metrics"));
        Assertions.assertEquals(Collections.singletonList("category"), formData.get("groupby"));
    }

    @Test
    public void testBuildFormDataTableFromResultsWhenColumnsMissing() {
        SupersetChartProcessor processor = new SupersetChartProcessor();
        SupersetPluginConfig config = new SupersetPluginConfig();
        QueryResult queryResult = new QueryResult();
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("id", 1);
        row.put("name", "alice");
        queryResult.setQueryResults(Collections.singletonList(row));

        Map<String, Object> formData = processor.buildFormData(config, queryResult, "table");

        Assertions.assertEquals("raw", formData.get("query_mode"));
        Assertions.assertEquals(Arrays.asList("id", "name"), formData.get("all_columns"));
        Assertions.assertEquals(Arrays.asList("id", "name"), formData.get("columns"));
    }

    @Test
    public void testBuildFormDataEmptyColumnsReturnsEmpty() {
        SupersetChartProcessor processor = new SupersetChartProcessor();
        SupersetPluginConfig config = new SupersetPluginConfig();
        QueryResult queryResult = new QueryResult();
        queryResult.setQueryColumns(Collections.emptyList());

        Map<String, Object> formData = processor.buildFormData(config, queryResult, "table");

        Assertions.assertTrue(formData.isEmpty());
    }
}
