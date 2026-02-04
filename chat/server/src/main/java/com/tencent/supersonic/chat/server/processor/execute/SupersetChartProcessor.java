package com.tencent.supersonic.chat.server.processor.execute;

import com.alibaba.fastjson.JSONObject;
import com.tencent.supersonic.chat.api.pojo.response.QueryResult;
import com.tencent.supersonic.chat.server.agent.Agent;
import com.tencent.supersonic.chat.server.agent.AgentToolType;
import com.tencent.supersonic.chat.server.agent.PluginTool;
import com.tencent.supersonic.chat.server.plugin.ChatPlugin;
import com.tencent.supersonic.chat.server.plugin.build.ParamOption;
import com.tencent.supersonic.chat.server.plugin.build.WebBase;
import com.tencent.supersonic.chat.server.plugin.build.superset.SupersetApiClient;
import com.tencent.supersonic.chat.server.plugin.build.superset.SupersetChartCandidate;
import com.tencent.supersonic.chat.server.plugin.build.superset.SupersetChartInfo;
import com.tencent.supersonic.chat.server.plugin.build.superset.SupersetChartResp;
import com.tencent.supersonic.chat.server.plugin.build.superset.SupersetDashboardInfo;
import com.tencent.supersonic.chat.server.plugin.build.superset.SupersetPluginConfig;
import com.tencent.supersonic.chat.server.plugin.build.superset.SupersetVizTypeSelector;
import com.tencent.supersonic.chat.server.pojo.ExecuteContext;
import com.tencent.supersonic.chat.server.service.PluginService;
import com.tencent.supersonic.common.util.ContextUtils;
import com.tencent.supersonic.common.util.JsonUtil;
import com.tencent.supersonic.headless.api.pojo.SchemaElement;
import com.tencent.supersonic.headless.api.pojo.SemanticParseInfo;
import com.tencent.supersonic.headless.api.pojo.response.QueryState;
import com.tencent.supersonic.headless.server.sync.superset.SupersetDatasetColumn;
import com.tencent.supersonic.headless.server.sync.superset.SupersetDatasetInfo;
import com.tencent.supersonic.headless.server.sync.superset.SupersetDatasetMetric;
import com.tencent.supersonic.headless.server.sync.superset.SupersetSyncService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class SupersetChartProcessor implements ExecuteResultProcessor {

    public static final String QUERY_MODE = "SUPERSET";

    @Override
    public boolean accept(ExecuteContext executeContext) {
        QueryResult queryResult = executeContext.getResponse();
        if (queryResult == null || executeContext.getParseInfo() == null) {
            return false;
        }
        if (!QueryState.SUCCESS.equals(queryResult.getQueryState())) {
            return false;
        }
        String queryMode = queryResult.getQueryMode();
        return !StringUtils.equalsAnyIgnoreCase(queryMode, "WEB_PAGE", "WEB_SERVICE", "PLAIN_TEXT");
    }

    @Override
    public void process(ExecuteContext executeContext) {
        QueryResult queryResult = executeContext.getResponse();
        log.debug("superset process start, queryId={}, queryMode={}",
                executeContext.getRequest().getQueryId(),
                queryResult == null ? null : queryResult.getQueryMode());
        Optional<ChatPlugin> pluginOptional = resolveSupersetPlugin(executeContext);
        if (!pluginOptional.isPresent()) {
            log.debug("superset plugin not found for queryId={}",
                    executeContext.getRequest().getQueryId());
            return;
        }
        ChatPlugin plugin = pluginOptional.get();
        SupersetPluginConfig config =
                JsonUtil.toObject(plugin.getConfig(), SupersetPluginConfig.class);
        if (config == null || !config.isEnabled()) {
            log.debug("superset config disabled, pluginId={}, queryId={}", plugin.getId(),
                    executeContext.getRequest().getQueryId());
            return;
        }
        SupersetChartResp response = new SupersetChartResp();
        response.setName(plugin.getName());
        response.setPluginId(plugin.getId());
        response.setPluginType(plugin.getType());

        String sql = resolveSql(queryResult, executeContext.getParseInfo());
        List<SupersetVizTypeSelector.VizTypeItem> vizTypeCandidates =
                resolveVizTypeCandidates(config, queryResult, executeContext);
        String vizType = resolvePrimaryVizType(vizTypeCandidates);
        response.setVizType(vizType);
        if (StringUtils.isBlank(sql) || StringUtils.isBlank(config.getBaseUrl())
                || !config.hasValidAuthConfig()) {
            response.setFallback(true);
            response.setFallbackReason("superset config invalid");
            log.debug(
                    "superset fallback: invalid config, pluginId={}, hasSql={}, baseUrl={}, authOk={}",
                    plugin.getId(), StringUtils.isNotBlank(sql), config.getBaseUrl(),
                    config.hasValidAuthConfig());
            queryResult.setQueryMode(QUERY_MODE);
            queryResult.setResponse(response);
            return;
        }
        SupersetDatasetInfo datasetInfo = resolveSupersetDataset(executeContext);
        Long datasetId = datasetInfo == null ? null : datasetInfo.getId();
        Long databaseId = datasetInfo == null ? null : datasetInfo.getDatabaseId();
        String schema = datasetInfo == null ? null : datasetInfo.getSchema();
        if (datasetId == null && databaseId == null) {
            response.setFallback(true);
            response.setFallbackReason("superset dataset unresolved");
            log.debug("superset fallback: dataset unresolved, pluginId={}, dataSetId={}",
                    plugin.getId(), executeContext.getParseInfo() == null ? null
                            : executeContext.getParseInfo().getDataSetId());
            queryResult.setQueryMode(QUERY_MODE);
            queryResult.setResponse(response);
            return;
        }
        try {
            SupersetApiClient client = new SupersetApiClient(config);
            String chartName = buildChartName(executeContext, plugin);
            log.debug("superset build chart start, pluginId={}, vizType={}, chartName={}",
                    plugin.getId(), vizType, chartName);
            List<String> dashboardTags = buildDashboardTags(executeContext);
            List<SupersetChartCandidate> chartCandidates =
                    buildChartCandidates(client, vizTypeCandidates, sql, chartName, config,
                            queryResult, datasetInfo, datasetId, databaseId, schema, dashboardTags);
            if (!chartCandidates.isEmpty()) {
                SupersetChartCandidate primary = chartCandidates.get(0);
                response.setChartId(primary.getChartId());
                response.setChartUuid(primary.getChartUuid());
                response.setGuestToken(primary.getGuestToken());
                response.setEmbeddedId(primary.getEmbeddedId());
                response.setSupersetDomain(primary.getSupersetDomain());
                response.setVizType(primary.getVizType());
                response.setVizTypeCandidates(chartCandidates);
            } else {
                throw new IllegalStateException("superset chart build failed");
            }
            response.setWebPage(buildWebPage(config));
            log.debug(
                    "superset build chart success, pluginId={}, chartId={}, chartUuid={}, guestToken={}",
                    plugin.getId(), response.getChartId(), response.getChartUuid(),
                    StringUtils.isNotBlank(response.getGuestToken()));
            List<SupersetDashboardInfo> dashboards = Collections.emptyList();
            try {
                dashboards = client.listDashboards();
            } catch (Exception ex) {
                log.warn("superset dashboard list load failed", ex);
            }
            response.setDashboards(dashboards);
            response.setFallback(false);
        } catch (Exception ex) {
            log.warn("superset chart build failed", ex);
            response.setFallback(true);
            response.setFallbackReason(ex.getMessage());
            log.debug("superset fallback: {}", ex.getMessage());
        }
        queryResult.setQueryMode(QUERY_MODE);
        queryResult.setResponse(response);
        log.debug("superset process complete, queryId={}, fallback={}",
                executeContext.getRequest().getQueryId(), response.isFallback());
    }

    private Optional<ChatPlugin> resolveSupersetPlugin(ExecuteContext executeContext) {
        PluginService pluginService = ContextUtils.getBean(PluginService.class);
        List<ChatPlugin> plugins = pluginService.getPluginList();
        if (CollectionUtils.isEmpty(plugins)) {
            return Optional.empty();
        }
        Agent agent = executeContext.getAgent();
        List<Long> pluginIds = resolvePluginIds(agent);
        if (!CollectionUtils.isEmpty(pluginIds)) {
            plugins = plugins.stream().filter(plugin -> pluginIds.contains(plugin.getId()))
                    .collect(Collectors.toList());
        }
        Long dataSetId = executeContext.getParseInfo().getDataSetId();
        return plugins.stream().filter(plugin -> "SUPERSET".equalsIgnoreCase(plugin.getType()))
                .filter(plugin -> matchDataSet(plugin, dataSetId)).findFirst();
    }

    private List<Long> resolvePluginIds(Agent agent) {
        if (agent == null) {
            return Collections.emptyList();
        }
        List<String> tools = agent.getTools(AgentToolType.PLUGIN);
        if (CollectionUtils.isEmpty(tools)) {
            return Collections.emptyList();
        }
        List<Long> pluginIds = new ArrayList<>();
        for (String tool : tools) {
            PluginTool pluginTool = JSONObject.parseObject(tool, PluginTool.class);
            if (pluginTool != null && !CollectionUtils.isEmpty(pluginTool.getPlugins())) {
                pluginIds.addAll(pluginTool.getPlugins());
            }
        }
        return pluginIds;
    }

    private boolean matchDataSet(ChatPlugin plugin, Long dataSetId) {
        if (plugin.isContainsAllDataSet()) {
            return true;
        }
        if (dataSetId == null) {
            return true;
        }
        return plugin.getDataSetList() != null && plugin.getDataSetList().contains(dataSetId);
    }

    private String resolveSql(QueryResult queryResult, SemanticParseInfo parseInfo) {
        if (queryResult != null && StringUtils.isNotBlank(queryResult.getQuerySql())) {
            return queryResult.getQuerySql();
        }
        if (parseInfo != null && parseInfo.getSqlInfo() != null) {
            return parseInfo.getSqlInfo().getCorrectedS2SQL();
        }
        return null;
    }

    private SupersetDatasetInfo resolveSupersetDataset(ExecuteContext executeContext) {
        if (executeContext == null || executeContext.getParseInfo() == null) {
            return null;
        }
        Long dataSetId = executeContext.getParseInfo().getDataSetId();
        if (dataSetId == null) {
            return null;
        }
        SupersetSyncService syncService;
        try {
            syncService = ContextUtils.getBean(SupersetSyncService.class);
        } catch (Exception ex) {
            log.debug("superset sync service missing", ex);
            return null;
        }
        SupersetDatasetInfo datasetInfo = syncService.resolveDatasetByDataSetId(dataSetId);
        if (datasetInfo == null) {
            log.debug("superset dataset resolve failed, dataSetId={}", dataSetId);
            return null;
        }
        log.debug(
                "superset dataset resolved, dataSetId={}, supersetDatasetId={}, databaseId={}, schema={}",
                dataSetId, datasetInfo.getId(), datasetInfo.getDatabaseId(),
                datasetInfo.getSchema());
        return datasetInfo;
    }

    private List<SupersetVizTypeSelector.VizTypeItem> resolveVizTypeCandidates(
            SupersetPluginConfig config, QueryResult queryResult, ExecuteContext executeContext) {
        if (StringUtils.isNotBlank(config.getVizType())
                && !"AUTO".equalsIgnoreCase(config.getVizType())) {
            SupersetVizTypeSelector.VizTypeItem configured =
                    SupersetVizTypeSelector.resolveItemByVizType(config.getVizType(), null);
            return configured == null ? Collections.emptyList()
                    : Collections.singletonList(configured);
        }
        String queryText = executeContext == null || executeContext.getRequest() == null ? null
                : executeContext.getRequest().getQueryText();
        Agent agent = executeContext == null ? null : executeContext.getAgent();
        return SupersetVizTypeSelector.selectCandidates(config, queryResult, queryText, agent);
    }

    private String resolvePrimaryVizType(List<SupersetVizTypeSelector.VizTypeItem> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "table";
        }
        SupersetVizTypeSelector.VizTypeItem primary = candidates.get(0);
        return primary == null || StringUtils.isBlank(primary.getVizType()) ? "table"
                : primary.getVizType();
    }

    private String buildChartName(ExecuteContext executeContext, ChatPlugin plugin) {
        Long queryId = executeContext.getRequest().getQueryId();
        String suffix =
                queryId == null ? String.valueOf(System.currentTimeMillis()) : queryId.toString();
        return "supersonic_" + plugin.getName() + "_" + suffix;
    }

    private List<String> buildDashboardTags(ExecuteContext executeContext) {
        List<String> tags = new ArrayList<>();
        tags.add("supersonic");
        tags.add("supersonic-single-chart");
        SemanticParseInfo parseInfo = executeContext == null ? null : executeContext.getParseInfo();
        if (parseInfo != null && parseInfo.getDataSet() != null
                && parseInfo.getDataSet().getDataSetId() != null) {
            tags.add("supersonic-dataset-" + parseInfo.getDataSet().getDataSetId());
        }
        return tags;
    }

    private List<SupersetChartCandidate> buildChartCandidates(SupersetApiClient client,
            List<SupersetVizTypeSelector.VizTypeItem> candidates, String sql, String chartName,
            SupersetPluginConfig config, QueryResult queryResult, SupersetDatasetInfo datasetInfo,
            Long datasetId, Long databaseId, String schema, List<String> dashboardTags) {
        if (client == null || candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        List<SupersetChartCandidate> results = new ArrayList<>();
        Long resolvedDatasetId = datasetId;
        int limit = Math.min(3, candidates.size());
        for (int i = 0; i < limit; i++) {
            SupersetVizTypeSelector.VizTypeItem candidate = candidates.get(i);
            if (candidate == null || StringUtils.isBlank(candidate.getVizType())) {
                continue;
            }
            String vizType = candidate.getVizType();
            String candidateChartName = buildCandidateChartName(chartName, vizType, i);
            Map<String, Object> formData =
                    buildFormData(config, executeContext.getParseInfo(), datasetInfo, vizType);
            log.debug(
                    "superset chart formData prepared, vizType={}, keys={}, size={}, datasetColumns={}, datasetMetrics={}, parseMetrics={}, parseDimensions={}",
                    vizType, formData.keySet(), formData.size(),
                    datasetInfo == null || datasetInfo.getColumns() == null ? 0
                            : datasetInfo.getColumns().size(),
                    datasetInfo == null || datasetInfo.getMetrics() == null ? 0
                            : datasetInfo.getMetrics().size(),
                    executeContext.getParseInfo() == null
                            || executeContext.getParseInfo().getMetrics() == null ? 0
                                    : executeContext.getParseInfo().getMetrics().size(),
                    executeContext.getParseInfo() == null
                            || executeContext.getParseInfo().getDimensions() == null ? 0
                                    : executeContext.getParseInfo().getDimensions().size());
            SupersetChartInfo chartInfo = client.createEmbeddedChart(sql, candidateChartName,
                    vizType, formData, resolvedDatasetId, databaseId, schema, dashboardTags);
            if (resolvedDatasetId == null && chartInfo.getDatasetId() != null) {
                resolvedDatasetId = chartInfo.getDatasetId();
            }
            SupersetChartCandidate chartCandidate = new SupersetChartCandidate();
            chartCandidate.setVizType(vizType);
            chartCandidate.setVizName(candidate.getName());
            chartCandidate.setChartId(chartInfo.getChartId());
            chartCandidate.setChartUuid(chartInfo.getChartUuid());
            chartCandidate.setGuestToken(chartInfo.getGuestToken());
            chartCandidate.setEmbeddedId(chartInfo.getEmbeddedId());
            chartCandidate.setSupersetDomain(buildSupersetDomain(config));
            results.add(chartCandidate);
        }
        return results;
    }

    private String buildCandidateChartName(String baseName, String vizType, int index) {
        String safeVizType =
                StringUtils.defaultIfBlank(vizType, "candidate").replaceAll("[^a-zA-Z0-9_-]", "_");
        return baseName + "_" + safeVizType + "_" + (index + 1);
    }

    /**
     * 构建 Superset 图表的 formData，优先合并插件自定义配置。
     *
     * Args: config: Superset 插件配置。 parseInfo: 语义解析信息。 datasetInfo: Superset dataset 信息。 vizType:
     * 图表类型。
     *
     * Returns: 合并后的 formData。
     */
    Map<String, Object> buildFormData(SupersetPluginConfig config, SemanticParseInfo parseInfo,
            SupersetDatasetInfo datasetInfo, String vizType) {
        Map<String, Object> autoFormData = buildAutoFormData(parseInfo, datasetInfo, vizType);
        Map<String, Object> customFormData = config == null ? null : config.getFormData();
        if (customFormData == null || customFormData.isEmpty()) {
            return autoFormData;
        }
        if (autoFormData.isEmpty()) {
            return customFormData;
        }
        Map<String, Object> merged = new HashMap<>(autoFormData);
        merged.putAll(customFormData);
        return merged;
    }

    /**
     * 基于 dataset 与解析信息生成 Superset formData，确保由 Superset 计算结果。
     *
     * Args: parseInfo: 语义解析信息。 datasetInfo: Superset dataset 信息。 vizType: 图表类型。
     *
     * Returns: 自动生成的 formData。
     */
    private Map<String, Object> buildAutoFormData(SemanticParseInfo parseInfo,
            SupersetDatasetInfo datasetInfo, String vizType) {
        Map<String, Object> formData = new HashMap<>();
        List<SupersetDatasetColumn> datasetColumns =
                datasetInfo == null ? Collections.emptyList() : datasetInfo.getColumns();
        List<String> columnNames = resolveDatasetColumns(datasetColumns);
        Map<String, SupersetDatasetColumn> columnMap = toColumnMap(datasetColumns);
        List<String> dimensionColumns = resolveDimensionColumns(parseInfo, columnMap);
        List<Object> metrics = resolveMetrics(parseInfo, datasetInfo, columnMap);
        String timeColumn = resolveTimeColumn(parseInfo, datasetInfo, columnMap);
        if (isTableVizType(vizType)) {
            if (!metrics.isEmpty() || !dimensionColumns.isEmpty()) {
                formData.put("query_mode", "aggregate");
                if (!metrics.isEmpty()) {
                    formData.put("metrics", metrics);
                }
                if (!dimensionColumns.isEmpty()) {
                    formData.put("groupby", dimensionColumns);
                }
                if (StringUtils.isNotBlank(timeColumn)) {
                    formData.put("granularity_sqla", timeColumn);
                }
                return formData;
            }
            if (!columnNames.isEmpty()) {
                formData.put("query_mode", "raw");
                formData.put("all_columns", columnNames);
                formData.put("columns", columnNames);
            }
            return formData;
        }
        if (!metrics.isEmpty()) {
            formData.put("metrics", metrics);
        }
        if (!dimensionColumns.isEmpty()) {
            formData.put("groupby", dimensionColumns);
        }
        if (StringUtils.isNotBlank(timeColumn)) {
            formData.put("granularity_sqla", timeColumn);
        }
        if (!formData.isEmpty()) {
            formData.put("query_mode", "aggregate");
            return formData;
        }
        if (!columnNames.isEmpty()) {
            formData.put("query_mode", "raw");
            formData.put("all_columns", columnNames);
            formData.put("columns", columnNames);
        }
        return formData;
    }

    private List<String> resolveDatasetColumns(List<SupersetDatasetColumn> columns) {
        if (CollectionUtils.isEmpty(columns)) {
            return Collections.emptyList();
        }
        return columns.stream().map(SupersetDatasetColumn::getColumnName)
                .filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
    }

    private Map<String, SupersetDatasetColumn> toColumnMap(List<SupersetDatasetColumn> columns) {
        if (CollectionUtils.isEmpty(columns)) {
            return Collections.emptyMap();
        }
        Map<String, SupersetDatasetColumn> map = new HashMap<>();
        for (SupersetDatasetColumn column : columns) {
            if (column == null || StringUtils.isBlank(column.getColumnName())) {
                continue;
            }
            map.put(normalizeName(column.getColumnName()), column);
        }
        return map;
    }

    private Map<String, SupersetDatasetMetric> toMetricMap(List<SupersetDatasetMetric> metrics) {
        if (CollectionUtils.isEmpty(metrics)) {
            return Collections.emptyMap();
        }
        Map<String, SupersetDatasetMetric> map = new HashMap<>();
        for (SupersetDatasetMetric metric : metrics) {
            if (metric == null || StringUtils.isBlank(metric.getMetricName())) {
                continue;
            }
            map.put(normalizeName(metric.getMetricName()), metric);
        }
        return map;
    }

    private List<String> resolveDimensionColumns(SemanticParseInfo parseInfo,
            Map<String, SupersetDatasetColumn> columnMap) {
        if (parseInfo != null && !CollectionUtils.isEmpty(parseInfo.getDimensions())) {
            List<String> dimensions = new ArrayList<>();
            for (SchemaElement element : parseInfo.getDimensions()) {
                String name = resolveSchemaElementName(element);
                if (StringUtils.isBlank(name)) {
                    continue;
                }
                if (!columnMap.isEmpty() && !columnMap.containsKey(normalizeName(name))) {
                    continue;
                }
                dimensions.add(name);
            }
            if (!dimensions.isEmpty()) {
                return dimensions.stream().distinct().collect(Collectors.toList());
            }
        }
        if (columnMap.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> dimensions = new ArrayList<>();
        for (SupersetDatasetColumn column : columnMap.values()) {
            if (column != null && Boolean.TRUE.equals(column.getGroupby())
                    && StringUtils.isNotBlank(column.getColumnName())) {
                dimensions.add(column.getColumnName());
            }
        }
        return dimensions.stream().distinct().collect(Collectors.toList());
    }

    private List<Object> resolveMetrics(SemanticParseInfo parseInfo,
            SupersetDatasetInfo datasetInfo, Map<String, SupersetDatasetColumn> columnMap) {
        List<Object> metrics = new ArrayList<>();
        List<SupersetDatasetMetric> datasetMetrics =
                datasetInfo == null ? Collections.emptyList() : datasetInfo.getMetrics();
        Map<String, SupersetDatasetMetric> metricMap = toMetricMap(datasetMetrics);
        boolean hasDatasetMetrics = !metricMap.isEmpty();
        if (parseInfo != null && !CollectionUtils.isEmpty(parseInfo.getMetrics())) {
            for (SchemaElement element : parseInfo.getMetrics()) {
                String name = resolveSchemaElementName(element);
                if (StringUtils.isBlank(name)) {
                    continue;
                }
                if (hasDatasetMetrics) {
                    SupersetDatasetMetric metric = metricMap.get(normalizeName(name));
                    if (metric != null) {
                        metrics.add(metric.getMetricName());
                    }
                    continue;
                }
                SupersetDatasetColumn column = columnMap.get(normalizeName(name));
                if (column != null) {
                    metrics.add(buildAdhocMetric(column, resolveAggregate(element)));
                }
            }
        }
        if (!metrics.isEmpty()) {
            return metrics;
        }
        if (hasDatasetMetrics) {
            metrics.add(datasetMetrics.get(0).getMetricName());
            return metrics;
        }
        SupersetDatasetColumn numeric = resolveNumericColumn(columnMap);
        if (numeric != null) {
            metrics.add(buildAdhocMetric(numeric, "SUM"));
        }
        return metrics;
    }

    private SupersetDatasetColumn resolveNumericColumn(
            Map<String, SupersetDatasetColumn> columnMap) {
        if (columnMap.isEmpty()) {
            return null;
        }
        for (SupersetDatasetColumn column : columnMap.values()) {
            if (column == null || StringUtils.isBlank(column.getColumnName())) {
                continue;
            }
            if (isNumericType(column.getType())) {
                return column;
            }
        }
        return null;
    }

    private String resolveTimeColumn(SemanticParseInfo parseInfo, SupersetDatasetInfo datasetInfo,
            Map<String, SupersetDatasetColumn> columnMap) {
        if (datasetInfo != null && StringUtils.isNotBlank(datasetInfo.getMainDttmCol())) {
            return datasetInfo.getMainDttmCol();
        }
        if (!columnMap.isEmpty()) {
            for (SupersetDatasetColumn column : columnMap.values()) {
                if (column != null && Boolean.TRUE.equals(column.getIsDttm())
                        && StringUtils.isNotBlank(column.getColumnName())) {
                    return column.getColumnName();
                }
            }
        }
        if (parseInfo != null && !CollectionUtils.isEmpty(parseInfo.getDimensions())) {
            for (SchemaElement element : parseInfo.getDimensions()) {
                String name = resolveSchemaElementName(element);
                if (StringUtils.isBlank(name)) {
                    continue;
                }
                SupersetDatasetColumn column = columnMap.get(normalizeName(name));
                if (column != null) {
                    return column.getColumnName();
                }
            }
        }
        return null;
    }

    private Map<String, Object> buildAdhocMetric(SupersetDatasetColumn column, String aggregate) {
        Map<String, Object> metric = new HashMap<>();
        metric.put("expressionType", "SIMPLE");
        if (StringUtils.isNotBlank(aggregate)) {
            metric.put("aggregate", aggregate);
        }
        Map<String, Object> columnRef = new HashMap<>();
        columnRef.put("column_name", column.getColumnName());
        if (StringUtils.isNotBlank(column.getType())) {
            columnRef.put("type", column.getType());
        }
        metric.put("column", columnRef);
        metric.put("hasCustomLabel", true);
        String label =
                StringUtils.defaultIfBlank(aggregate, "SUM") + "(" + column.getColumnName() + ")";
        metric.put("label", label);
        metric.put("optionName", "metric_" + normalizeName(column.getColumnName()));
        return metric;
    }

    private String resolveSchemaElementName(SchemaElement element) {
        if (element == null) {
            return null;
        }
        if (StringUtils.isNotBlank(element.getBizName())) {
            return element.getBizName();
        }
        return element.getName();
    }

    private String resolveAggregate(SchemaElement element) {
        if (element == null || StringUtils.isBlank(element.getDefaultAgg())) {
            return "SUM";
        }
        return element.getDefaultAgg().toUpperCase();
    }

    private String normalizeName(String name) {
        return StringUtils.lowerCase(StringUtils.trimToEmpty(name));
    }

    /**
     * 判断列是否为数值类型。
     *
     * Args: column: 查询列信息。
     *
     * Returns: 是否为数值列。
     */
    private boolean isNumericType(String type) {
        String normalized = StringUtils.defaultString(type).toUpperCase();
        return normalized.contains("INT") || normalized.contains("LONG")
                || normalized.contains("DOUBLE") || normalized.contains("FLOAT")
                || normalized.contains("DECIMAL") || normalized.contains("NUMBER")
                || normalized.contains("NUMERIC") || normalized.contains("BIGINT")
                || normalized.contains("SHORT");
    }

    /**
     * 判断是否为表格类图表。
     *
     * Args: vizType: 图表类型。
     *
     * Returns: 是否为 table 类。
     */
    private boolean isTableVizType(String vizType) {
        return StringUtils.isNotBlank(vizType) && vizType.toLowerCase().contains("table");
    }

    private WebBase buildWebPage(SupersetPluginConfig config) {
        WebBase webBase = new WebBase();
        webBase.setUrl("");
        List<ParamOption> paramOptions = new ArrayList<>();
        if (config.getHeight() != null) {
            ParamOption heightOption = new ParamOption();
            heightOption.setParamType(ParamOption.ParamType.FORWARD);
            heightOption.setKey("height");
            heightOption.setValue(config.getHeight());
            paramOptions.add(heightOption);
        }
        webBase.setParamOptions(paramOptions);
        return webBase;
    }

    private String buildSupersetDomain(SupersetPluginConfig config) {
        String baseUrl = StringUtils.defaultString(config.getBaseUrl());
        if (StringUtils.isBlank(baseUrl)) {
            return null;
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
