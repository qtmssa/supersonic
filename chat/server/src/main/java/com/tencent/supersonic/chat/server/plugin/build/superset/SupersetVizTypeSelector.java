package com.tencent.supersonic.chat.server.plugin.build.superset;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.tencent.supersonic.chat.api.pojo.response.QueryResult;
import com.tencent.supersonic.common.config.ChatModel;
import com.tencent.supersonic.common.pojo.ChatModelConfig;
import com.tencent.supersonic.common.pojo.QueryColumn;
import com.tencent.supersonic.common.service.ChatModelService;
import com.tencent.supersonic.common.util.ContextUtils;
import com.tencent.supersonic.common.util.JsonUtil;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.provider.ModelProvider;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class SupersetVizTypeSelector {

    private static final String DEFAULT_VIZ_TYPE = "table";
    private static final String VIZTYPE_RESOURCE = "viztype.json";
    private static final VizTypeCatalog VIZTYPE_CATALOG = VizTypeCatalog.load();
    private static final String LLM_PROMPT = "" + "#Role: You are a Superset chart type selector.\n"
            + "#Task: Choose the best Superset viz_type from the available list.\n" + "#Rules:\n"
            + "1. ONLY select from #AvailableVizTypes.\n"
            + "2. Consider both #UserInstruction and #DataProfile.\n"
            + "3. Return JSON only, no extra text.\n"
            + "4. Output fields: viz_type, alternatives (array), reason.\n"
            + "5. alternatives length should be {{top_n}} and ordered by preference.\n"
            + "#UserInstruction: {{instruction}}\n" + "#DataProfile: {{data_profile}}\n"
            + "#AvailableVizTypes: {{candidates}}\n" + "#Response:";
    private static final Set<String> NUMBER_TYPES =
            Stream.of("INT", "INTEGER", "BIGINT", "LONG", "FLOAT", "DOUBLE", "DECIMAL", "NUMBER")
                    .collect(Collectors.toSet());
    private static final Set<String> DATE_TYPES =
            Stream.of("DATE", "DATETIME", "TIMESTAMP").collect(Collectors.toSet());
    private static final int MAX_SAMPLE_ROWS = 200;
    private static final int MAX_SAMPLE_VALUES = 5;

    public static String select(QueryResult queryResult) {
        SupersetPluginConfig config = new SupersetPluginConfig();
        config.setVizTypeLlmEnabled(false);
        return select(config, queryResult, null);
    }

    public static String select(SupersetPluginConfig config, QueryResult queryResult,
            String queryText) {
        if (queryResult == null || queryResult.getQueryColumns() == null) {
            return DEFAULT_VIZ_TYPE;
        }
        List<QueryColumn> columns = queryResult.getQueryColumns();
        List<Map<String, Object>> results = queryResult.getQueryResults();
        if (columns.isEmpty()) {
            return DEFAULT_VIZ_TYPE;
        }
        SupersetPluginConfig safeConfig = config == null ? new SupersetPluginConfig() : config;
        List<VizTypeItem> candidates = filterCandidates(safeConfig, VIZTYPE_CATALOG.getItems());

        long metricCount = columns.stream().filter(SupersetVizTypeSelector::isNumber).count();
        long dateCount = columns.stream().filter(SupersetVizTypeSelector::isDate).count();
        long categoryCount = columns.stream().filter(SupersetVizTypeSelector::isCategory).count();

        Optional<String> ruleChoice = selectByRules(columns, results, metricCount, dateCount,
                categoryCount, candidates, safeConfig.isVizTypeLlmEnabled());
        if (ruleChoice.isPresent()) {
            return ruleChoice.get();
        }

        if (!safeConfig.isVizTypeLlmEnabled()) {
            return resolveFallback(candidates);
        }

        Optional<String> llmChoice = selectByLlm(safeConfig, queryResult, queryText, candidates);
        return llmChoice.orElseGet(() -> resolveFallback(candidates));
    }

    private static boolean isNumber(QueryColumn column) {
        if (column == null) {
            return false;
        }
        if ("NUMBER".equalsIgnoreCase(column.getShowType())) {
            return true;
        }
        String type = StringUtils.upperCase(column.getType());
        return StringUtils.isNotBlank(type) && NUMBER_TYPES.contains(type);
    }

    private static boolean isDate(QueryColumn column) {
        if (column == null) {
            return false;
        }
        if ("DATE".equalsIgnoreCase(column.getShowType())) {
            return true;
        }
        String type = StringUtils.upperCase(column.getType());
        return StringUtils.isNotBlank(type) && DATE_TYPES.contains(type);
    }

    private static boolean isCategory(QueryColumn column) {
        if (column == null) {
            return false;
        }
        return "CATEGORY".equalsIgnoreCase(column.getShowType());
    }

    private static Optional<String> selectByRules(List<QueryColumn> columns,
            List<Map<String, Object>> results, long metricCount, long dateCount, long categoryCount,
            List<VizTypeItem> candidates, boolean llmEnabled) {
        if (metricCount == 1 && columns.size() == 1 && results != null && results.size() == 1) {
            return resolveCandidateByName("Big Number", candidates)
                    .or(() -> resolveCandidateByName("Big Number with Trendline", candidates))
                    .or(() -> resolveCandidateByName("Big Number Total", candidates));
        }

        if (llmEnabled) {
            return Optional.empty();
        }

        if (dateCount >= 1 && metricCount >= 1 && categoryCount <= 1) {
            return resolveCandidateByName("Line Chart", candidates)
                    .or(() -> resolveCandidateByName("Generic Chart", candidates));
        }

        if (categoryCount >= 1 && metricCount == 1) {
            if (results != null && results.size() <= 10) {
                return resolveCandidateByName("Pie Chart", candidates)
                        .or(() -> resolveCandidateByName("Partition Chart", candidates));
            }
            return resolveCandidateByName("Bar Chart", candidates);
        }

        if (categoryCount >= 1 && metricCount > 1) {
            return resolveCandidateByName("Pivot Table", candidates);
        }

        return resolveCandidateByName("Table", candidates);
    }

    private static Optional<String> selectByLlm(SupersetPluginConfig config,
            QueryResult queryResult, String queryText, List<VizTypeItem> candidates) {
        if (config.getVizTypeLlmChatModelId() == null) {
            log.debug("superset viztype llm skipped: chat model id missing");
            return Optional.empty();
        }
        ChatModelConfig chatModelConfig = resolveChatModelConfig(config.getVizTypeLlmChatModelId());
        if (chatModelConfig == null) {
            log.debug("superset viztype llm skipped: chat model config missing");
            return Optional.empty();
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        int topN = config.getVizTypeLlmTopN() == null ? 3 : Math.max(1, config.getVizTypeLlmTopN());
        String promptText = StringUtils.defaultIfBlank(config.getVizTypeLlmPrompt(), LLM_PROMPT);
        String dataProfile = buildDataProfile(queryResult);
        String candidateSummary = buildCandidateSummary(candidates);
        Map<String, Object> variables = new HashMap<>();
        variables.put("instruction", StringUtils.defaultString(queryText));
        variables.put("data_profile", dataProfile);
        variables.put("candidates", candidateSummary);
        variables.put("top_n", topN);

        Prompt prompt = PromptTemplate.from(promptText).apply(variables);
        ChatLanguageModel chatLanguageModel = ModelProvider.getChatModel(chatModelConfig);
        Response<AiMessage> response = chatLanguageModel.generate(prompt.toUserMessage());
        String answer =
                response == null || response.content() == null ? null : response.content().text();
        log.info("superset viztype llm req:\n{} \nresp:\n{}", prompt.text(), answer);
        String resolved = resolveFromModelResponse(answer, candidates);
        return Optional.ofNullable(resolved);
    }

    private static ChatModelConfig resolveChatModelConfig(Integer chatModelId) {
        if (chatModelId == null) {
            return null;
        }
        ChatModelService chatModelService = ContextUtils.getBean(ChatModelService.class);
        if (chatModelService == null) {
            return null;
        }
        ChatModel chatModel = chatModelService.getChatModel(chatModelId);
        return chatModel == null ? null : chatModel.getConfig();
    }

    static String resolveFromModelResponse(String response, List<VizTypeItem> candidates) {
        if (StringUtils.isBlank(response) || candidates == null || candidates.isEmpty()) {
            return null;
        }
        String payload = extractJsonPayload(response);
        if (StringUtils.isBlank(payload)) {
            return null;
        }
        JSONObject json;
        try {
            json = JSONObject.parseObject(payload);
        } catch (Exception ex) {
            log.warn("superset viztype llm response parse failed", ex);
            return null;
        }
        List<String> ordered = new ArrayList<>();
        String primary =
                StringUtils.defaultIfBlank(json.getString("viz_type"), json.getString("vizType"));
        if (StringUtils.isNotBlank(primary)) {
            ordered.add(primary);
        }
        JSONArray alternatives = json.getJSONArray("alternatives");
        if (alternatives != null) {
            for (int i = 0; i < alternatives.size(); i++) {
                String value = alternatives.getString(i);
                if (StringUtils.isNotBlank(value)) {
                    ordered.add(value);
                }
            }
        }
        return resolveCandidate(ordered, candidates);
    }

    private static String resolveCandidate(List<String> ordered, List<VizTypeItem> candidates) {
        if (ordered == null || ordered.isEmpty()) {
            return null;
        }
        Map<String, String> lookup = buildLookup(candidates);
        for (String candidate : ordered) {
            String resolved = resolveCandidateValue(candidate, candidates, lookup);
            if (StringUtils.isNotBlank(resolved)) {
                return resolved;
            }
        }
        return null;
    }

    private static Map<String, String> buildLookup(List<VizTypeItem> candidates) {
        Map<String, String> lookup = new HashMap<>();
        for (VizTypeItem item : candidates) {
            if (StringUtils.isNotBlank(item.getVizType())) {
                lookup.put(normalize(item.getVizType()), item.getVizType());
            }
            if (StringUtils.isNotBlank(item.getName())) {
                lookup.put(normalize(item.getName()), item.getVizType());
            }
            if (StringUtils.isNotBlank(item.getVizKey())) {
                lookup.put(normalize(item.getVizKey()), item.getVizType());
            }
        }
        return lookup;
    }

    private static String resolveCandidateValue(String value, List<VizTypeItem> candidates,
            Map<String, String> lookup) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String normalized = normalize(value);
        if (lookup.containsKey(normalized)) {
            return lookup.get(normalized);
        }
        int leftParen = normalized.indexOf('(');
        int rightParen = normalized.indexOf(')');
        if (leftParen >= 0 && rightParen > leftParen) {
            String inner = normalized.substring(leftParen + 1, rightParen).trim();
            String direct = lookup.get(inner);
            if (StringUtils.isNotBlank(direct)) {
                return direct;
            }
        }
        for (VizTypeItem item : candidates) {
            if (item == null) {
                continue;
            }
            String vizType = item.getVizType();
            String name = item.getName();
            if (StringUtils.isNotBlank(vizType) && normalized.contains(normalize(vizType))) {
                return vizType;
            }
            if (StringUtils.isNotBlank(name) && normalized.contains(normalize(name))) {
                return item.getVizType();
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return StringUtils.lowerCase(StringUtils.trimToEmpty(value), Locale.ROOT);
    }

    private static String extractJsonPayload(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return response.substring(start, end + 1);
    }

    private static String buildDataProfile(QueryResult queryResult) {
        Map<String, Object> profile = new LinkedHashMap<>();
        List<QueryColumn> columns =
                queryResult == null ? Collections.emptyList() : queryResult.getQueryColumns();
        List<Map<String, Object>> rows =
                queryResult == null ? Collections.emptyList() : queryResult.getQueryResults();
        List<Map<String, Object>> sampleRows = sampleRows(rows);
        profile.put("rowCount", sampleRows.size());
        if (columns != null && !columns.isEmpty()) {
            List<Map<String, Object>> columnProfiles = new ArrayList<>();
            List<Map<String, Object>> normalizedRows = normalizeRows(sampleRows);
            for (QueryColumn column : columns) {
                columnProfiles.add(buildColumnProfile(column, normalizedRows));
            }
            profile.put("columns", columnProfiles);
        }
        if (queryResult != null && queryResult.getAggregateInfo() != null
                && queryResult.getAggregateInfo().getMetricInfos() != null
                && !queryResult.getAggregateInfo().getMetricInfos().isEmpty()) {
            profile.put("aggregateInfo", queryResult.getAggregateInfo().getMetricInfos());
        }
        return JsonUtil.toString(profile);
    }

    private static List<Map<String, Object>> sampleRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        if (rows.size() <= MAX_SAMPLE_ROWS) {
            return rows;
        }
        return rows.subList(0, MAX_SAMPLE_ROWS);
    }

    private static List<Map<String, Object>> normalizeRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> normalizedRow = new HashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey() != null) {
                    normalizedRow.put(normalize(entry.getKey()), entry.getValue());
                }
            }
            normalized.add(normalizedRow);
        }
        return normalized;
    }

    private static Map<String, Object> buildColumnProfile(QueryColumn column,
            List<Map<String, Object>> rows) {
        Map<String, Object> profile = new LinkedHashMap<>();
        if (column == null) {
            return profile;
        }
        profile.put("name", column.getName());
        profile.put("bizName", column.getBizName());
        profile.put("type", column.getType());
        profile.put("showType", column.getShowType());
        profile.put("role", resolveRole(column));
        String[] keys = resolveKeys(column);
        List<Object> values = new ArrayList<>();
        int nullCount = 0;
        Map<String, Long> frequency = new HashMap<>();
        DoubleSummaryStatistics numericStats = new DoubleSummaryStatistics();
        for (Map<String, Object> row : rows) {
            Object value = resolveValue(row, keys);
            if (value == null) {
                nullCount++;
                continue;
            }
            values.add(value);
            String stringValue = String.valueOf(value);
            frequency.put(stringValue, frequency.getOrDefault(stringValue, 0L) + 1L);
            Double numeric = tryParseNumber(value);
            if (numeric != null) {
                numericStats.accept(numeric);
            }
        }
        int sampleSize = rows == null ? 0 : rows.size();
        profile.put("nullRate", sampleSize == 0 ? 0 : (double) nullCount / sampleSize);
        profile.put("distinctCount", frequency.size());
        profile.put("sampleValues", values.stream().filter(Objects::nonNull)
                .limit(MAX_SAMPLE_VALUES).collect(Collectors.toList()));
        if (numericStats.getCount() > 0) {
            Map<String, Object> numericSummary = new LinkedHashMap<>();
            numericSummary.put("min", numericStats.getMin());
            numericSummary.put("max", numericStats.getMax());
            numericSummary.put("avg", numericStats.getAverage());
            profile.put("numericStats", numericSummary);
        }
        String grain = detectTimeGrain(values);
        if (StringUtils.isNotBlank(grain)) {
            profile.put("timeGrain", grain);
        }
        if (!frequency.isEmpty()) {
            List<Map<String, Object>> topValues = frequency.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(MAX_SAMPLE_VALUES).map(entry -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("value", entry.getKey());
                        item.put("count", entry.getValue());
                        return item;
                    }).collect(Collectors.toList());
            profile.put("topValues", topValues);
        }
        return profile;
    }

    private static String resolveRole(QueryColumn column) {
        if (column == null) {
            return "UNKNOWN";
        }
        if (isDate(column)) {
            return "TIME";
        }
        if (isNumber(column)) {
            return "METRIC";
        }
        return "DIMENSION";
    }

    private static String[] resolveKeys(QueryColumn column) {
        return new String[] {normalize(column.getName()), normalize(column.getBizName()),
                        normalize(column.getNameEn())};
    }

    private static Object resolveValue(Map<String, Object> row, String[] keys) {
        if (row == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (StringUtils.isBlank(key)) {
                continue;
            }
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private static Double tryParseNumber(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private static String detectTimeGrain(List<Object> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        TreeMap<Integer, String> grains = new TreeMap<>(Comparator.naturalOrder());
        for (Object value : values) {
            String grain = detectTimeGrain(value);
            if (StringUtils.isNotBlank(grain)) {
                grains.put(grainRank(grain), grain);
            }
        }
        if (grains.isEmpty()) {
            return null;
        }
        return grains.get(grains.lastKey());
    }

    private static String detectTimeGrain(Object value) {
        if (value == null) {
            return null;
        }
        String raw = String.valueOf(value).trim();
        if (raw.matches("^\\d{4}$")) {
            return "YEAR";
        }
        if (raw.matches("^\\d{4}[-/]\\d{1,2}$")) {
            return "MONTH";
        }
        if (raw.matches("^\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}$")) {
            return "DAY";
        }
        if (raw.matches("^\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}[ T]\\d{1,2}$")) {
            return "HOUR";
        }
        if (raw.matches("^\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}[ T]\\d{1,2}:\\d{1,2}$")) {
            return "MINUTE";
        }
        if (raw.matches("^\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}[ T]\\d{1,2}:\\d{1,2}:\\d{1,2}$")) {
            return "SECOND";
        }
        if (value instanceof Number) {
            String digits = String.valueOf(((Number) value).longValue());
            if (digits.length() == 10) {
                return "SECOND";
            }
            if (digits.length() == 13) {
                return "MILLISECOND";
            }
        }
        return null;
    }

    private static int grainRank(String grain) {
        if ("YEAR".equalsIgnoreCase(grain)) {
            return 1;
        }
        if ("MONTH".equalsIgnoreCase(grain)) {
            return 2;
        }
        if ("DAY".equalsIgnoreCase(grain)) {
            return 3;
        }
        if ("HOUR".equalsIgnoreCase(grain)) {
            return 4;
        }
        if ("MINUTE".equalsIgnoreCase(grain)) {
            return 5;
        }
        if ("SECOND".equalsIgnoreCase(grain)) {
            return 6;
        }
        if ("MILLISECOND".equalsIgnoreCase(grain)) {
            return 7;
        }
        return 0;
    }

    private static List<VizTypeItem> filterCandidates(SupersetPluginConfig config,
            List<VizTypeItem> items) {
        if (items == null) {
            return Collections.emptyList();
        }
        List<String> allowList = config.getVizTypeAllowList();
        List<String> denyList = config.getVizTypeDenyList();
        List<VizTypeItem> filtered = new ArrayList<>(items);
        if (allowList != null && !allowList.isEmpty()) {
            Set<String> allowNormalized = allowList.stream().filter(StringUtils::isNotBlank)
                    .map(SupersetVizTypeSelector::normalize).collect(Collectors.toSet());
            filtered = filtered.stream()
                    .filter(item -> allowNormalized.contains(normalize(item.getVizType()))
                            || allowNormalized.contains(normalize(item.getName()))
                            || allowNormalized.contains(normalize(item.getVizKey())))
                    .collect(Collectors.toList());
        }
        if (denyList != null && !denyList.isEmpty()) {
            Set<String> denyNormalized = denyList.stream().filter(StringUtils::isNotBlank)
                    .map(SupersetVizTypeSelector::normalize).collect(Collectors.toSet());
            filtered = filtered.stream()
                    .filter(item -> !denyNormalized.contains(normalize(item.getVizType()))
                            && !denyNormalized.contains(normalize(item.getName()))
                            && !denyNormalized.contains(normalize(item.getVizKey())))
                    .collect(Collectors.toList());
        }
        return filtered;
    }

    private static Optional<String> resolveCandidateByName(String name,
            List<VizTypeItem> candidates) {
        if (StringUtils.isBlank(name) || candidates == null) {
            return Optional.empty();
        }
        return candidates.stream().filter(item -> name.equalsIgnoreCase(item.getName()))
                .map(VizTypeItem::getVizType).filter(StringUtils::isNotBlank).findFirst();
    }

    private static String resolveFallback(List<VizTypeItem> candidates) {
        String candidate =
                resolveCandidate(Collections.singletonList(DEFAULT_VIZ_TYPE), candidates);
        if (StringUtils.isNotBlank(candidate)) {
            return candidate;
        }
        return candidates == null || candidates.isEmpty() ? DEFAULT_VIZ_TYPE
                : StringUtils.defaultIfBlank(candidates.get(0).getVizType(), DEFAULT_VIZ_TYPE);
    }

    private static String buildCandidateSummary(List<VizTypeItem> candidates) {
        List<Map<String, Object>> summary = new ArrayList<>();
        for (VizTypeItem item : candidates) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("vizType", item.getVizType());
            entry.put("name", item.getName());
            entry.put("category", item.getCategory());
            entry.put("description", item.getDescription());
            summary.add(entry);
        }
        return JsonUtil.toString(summary);
    }

    @Data
    public static class VizTypeItem {
        private String vizKey;
        private String vizType;
        private String name;
        private String category;
        private String description;
        private String sourcePath;
    }

    @Data
    public static class VizTypeCatalog {
        private String source;
        private String generatedAt;
        private List<VizTypeItem> items = Collections.emptyList();

        public Optional<String> resolveByName(String name) {
            if (StringUtils.isBlank(name) || items == null) {
                return Optional.empty();
            }
            return items.stream().filter(item -> name.equalsIgnoreCase(item.getName()))
                    .map(VizTypeItem::getVizType).filter(StringUtils::isNotBlank).findFirst();
        }

        public List<VizTypeItem> getItems() {
            return items == null ? Collections.emptyList() : items;
        }

        public static VizTypeCatalog load() {
            String payload = readPayload();
            if (StringUtils.isBlank(payload)) {
                return new VizTypeCatalog();
            }
            try {
                return JsonUtil.toObject(payload, VizTypeCatalog.class);
            } catch (Exception ex) {
                log.warn("superset viztype catalog load failed", ex);
                return new VizTypeCatalog();
            }
        }

        private static String readPayload() {
            try (InputStream stream = SupersetVizTypeSelector.class.getClassLoader()
                    .getResourceAsStream(VIZTYPE_RESOURCE)) {
                if (stream != null) {
                    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException ex) {
                log.warn("superset viztype resource read failed", ex);
            }
            Path fallback = Path.of(VIZTYPE_RESOURCE);
            if (!Files.exists(fallback)) {
                return null;
            }
            try {
                return Files.readString(fallback, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                log.warn("superset viztype file read failed: {}", fallback, ex);
                return null;
            }
        }
    }
}
