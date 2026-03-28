package com.tencent.supersonic.chat.server.processor.parse;

import com.tencent.supersonic.chat.api.pojo.response.SimilarQueryRecallResp;
import com.tencent.supersonic.common.pojo.Text2SQLExemplar;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class SimilarQueryGenerator {

    private static final int MAX_SUGGESTION_SIZE = 5;

    private static final Pattern CHINESE_DIMENSION_PATTERN =
            Pattern.compile("按([\\p{IsHan}A-Za-z0-9_]{1,12}?)(?:看|维度|统计|分析|分布|排名|趋势|变化|对比|汇总)");

    private static final Pattern ENGLISH_DIMENSION_PATTERN = Pattern.compile(
            "\\bby\\s+([a-zA-Z][a-zA-Z\\s]{0,20}?)(?=\\?|$|,|\\.|\\s+(?:for|with|and)\\b)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CJK_PATTERN = Pattern.compile("[\\p{IsHan}]");

    public List<SimilarQueryRecallResp> generate(String currentQuery,
            List<Text2SQLExemplar> recalledExemplars, List<String> historyQueries, int limit) {
        if (StringUtils.isBlank(currentQuery) || limit <= 0) {
            return Collections.emptyList();
        }
        int finalLimit = Math.min(MAX_SUGGESTION_SIZE, limit);
        UserPatternProfile profile = UserPatternProfile.of(historyQueries, currentQuery);
        LinkedHashMap<String, Candidate> candidates = new LinkedHashMap<>();

        addRecallCandidates(candidates, currentQuery, recalledExemplars, profile);
        addGeneratedCandidates(candidates, currentQuery, profile);

        return candidates.values().stream()
                .sorted(Comparator.comparingInt(Candidate::getScore).reversed()
                        .thenComparing(Candidate::getText))
                .limit(finalLimit).map(candidate -> SimilarQueryRecallResp.builder()
                        .queryText(candidate.getText()).build())
                .collect(Collectors.toList());
    }

    private void addRecallCandidates(Map<String, Candidate> candidates, String currentQuery,
            List<Text2SQLExemplar> recalledExemplars, UserPatternProfile profile) {
        if (recalledExemplars == null) {
            return;
        }
        recalledExemplars.stream().filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(Text2SQLExemplar::getSimilarity).reversed())
                .forEach(exemplar -> addCandidate(candidates, currentQuery, exemplar.getQuestion(),
                        1000 + (int) Math.round(exemplar.getSimilarity() * 100)
                                + profile.getBonus(classify(exemplar.getQuestion()))));
    }

    private void addGeneratedCandidates(Map<String, Candidate> candidates, String currentQuery,
            UserPatternProfile profile) {
        boolean chinese = isChinese(currentQuery);
        String cleanedQuery = stripTrailingPunctuation(currentQuery);
        List<String> preferredDimensions =
                profile.getTopDimensions(extractDimensions(currentQuery));

        int dimensionPriority = 940;
        for (String dimension : preferredDimensions) {
            addCandidate(candidates, currentQuery,
                    buildBreakdownQuestion(cleanedQuery, dimension, chinese),
                    dimensionPriority + profile.getBonus(Category.BREAKDOWN));
            dimensionPriority -= 20;
        }

        if (preferredDimensions.isEmpty() && classify(currentQuery) != Category.BREAKDOWN) {
            addCandidate(candidates, currentQuery,
                    buildBreakdownQuestion(cleanedQuery, null, chinese),
                    900 + profile.getBonus(Category.BREAKDOWN));
        }
        if (classify(currentQuery) != Category.DETAIL) {
            addCandidate(candidates, currentQuery, buildDetailQuestion(cleanedQuery, chinese),
                    880 + profile.getBonus(Category.DETAIL));
        }
        if (classify(currentQuery) != Category.TREND) {
            addCandidate(candidates, currentQuery, buildTrendQuestion(cleanedQuery, chinese),
                    860 + profile.getBonus(Category.TREND));
        }
        if (classify(currentQuery) != Category.DRIVER) {
            addCandidate(candidates, currentQuery, buildDriverQuestion(cleanedQuery, chinese),
                    840 + profile.getBonus(Category.DRIVER));
        }
        if (classify(currentQuery) != Category.RANKING) {
            addCandidate(candidates, currentQuery, buildRankingQuestion(cleanedQuery, chinese),
                    820 + profile.getBonus(Category.RANKING));
        }
    }

    private void addCandidate(Map<String, Candidate> candidates, String currentQuery, String text,
            int score) {
        if (StringUtils.isBlank(text)) {
            return;
        }
        String normalizedCurrentQuery = normalize(currentQuery);
        String normalizedText = normalize(text);
        if (normalizedText.equals(normalizedCurrentQuery)) {
            return;
        }
        Candidate candidate = new Candidate(stripTrailingPunctuation(text), score);
        Candidate existing = candidates.get(normalizedText);
        if (existing == null || existing.getScore() < candidate.getScore()) {
            candidates.put(normalizedText, candidate);
        }
    }

    private String buildBreakdownQuestion(String query, String dimension, boolean chinese) {
        if (chinese) {
            if (StringUtils.isNotBlank(dimension)) {
                return String.format("按%s看，%s会有什么差异", dimension, query);
            }
            return String.format("按主要维度拆分，%s会有什么差异", query);
        }
        if (StringUtils.isNotBlank(dimension)) {
            return String.format("How does %s break down by %s", quote(query, false), dimension);
        }
        return String.format("How does %s break down across key dimensions", quote(query, false));
    }

    private String buildDetailQuestion(String query, boolean chinese) {
        if (chinese) {
            return String.format("围绕%s，能进一步下钻到明细吗", quote(query, true));
        }
        return String.format("Can we drill down into the details behind %s", quote(query, false));
    }

    private String buildTrendQuestion(String query, boolean chinese) {
        if (chinese) {
            return String.format("和上一个时间周期相比，%s有什么变化", quote(query, true));
        }
        return String.format("How has %s changed versus the previous period", quote(query, false));
    }

    private String buildDriverQuestion(String query, boolean chinese) {
        if (chinese) {
            return String.format("影响%s的主要因素有哪些", quote(query, true));
        }
        return String.format("What are the main drivers behind %s", quote(query, false));
    }

    private String buildRankingQuestion(String query, boolean chinese) {
        if (chinese) {
            return String.format("围绕%s，排名靠前或靠后的对象有哪些", quote(query, true));
        }
        return String.format("Which entities rank highest or lowest for %s", quote(query, false));
    }

    private String quote(String query, boolean chinese) {
        return chinese ? "“" + query + "”" : "\"" + query + "\"";
    }

    private static String normalize(String text) {
        return stripTrailingPunctuation(text).replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static String stripTrailingPunctuation(String text) {
        return StringUtils.trimToEmpty(text).replaceAll("[?？!！。,.，;；:：]+$", "");
    }

    private static boolean isChinese(String text) {
        return CJK_PATTERN.matcher(StringUtils.defaultString(text)).find();
    }

    private static Category classify(String text) {
        if (StringUtils.isBlank(text)) {
            return Category.OTHER;
        }
        String normalized = stripTrailingPunctuation(text).toLowerCase(Locale.ROOT);
        if (normalized.contains("明细") || normalized.contains("详情") || normalized.contains("下钻")
                || normalized.contains("drill") || normalized.contains("detail")) {
            return Category.DETAIL;
        }
        if (normalized.contains("原因") || normalized.contains("影响") || normalized.contains("驱动")
                || normalized.contains("why") || normalized.contains("driver")
                || normalized.contains("reason")) {
            return Category.DRIVER;
        }
        if (normalized.contains("趋势") || normalized.contains("变化") || normalized.contains("同比")
                || normalized.contains("环比") || normalized.contains("trend")
                || normalized.contains("change") || normalized.contains("previous period")) {
            return Category.TREND;
        }
        if (normalized.contains("排名") || normalized.contains("排行")
                || normalized.matches(".*top\\s*\\d+.*") || normalized.contains("highest")
                || normalized.contains("lowest") || normalized.contains("rank")) {
            return Category.RANKING;
        }
        if (normalized.contains("按") || normalized.contains("维度") || normalized.contains("拆分")
                || normalized.contains("break down") || normalized.contains("breakdown")
                || normalized.contains(" by ")) {
            return Category.BREAKDOWN;
        }
        return Category.OTHER;
    }

    private static List<String> extractDimensions(String text) {
        if (StringUtils.isBlank(text)) {
            return Collections.emptyList();
        }
        List<String> dimensions = new ArrayList<>();
        Matcher chineseMatcher = CHINESE_DIMENSION_PATTERN.matcher(text);
        while (chineseMatcher.find()) {
            dimensions.add(chineseMatcher.group(1));
        }
        Matcher englishMatcher = ENGLISH_DIMENSION_PATTERN.matcher(text);
        while (englishMatcher.find()) {
            dimensions.add(StringUtils.normalizeSpace(englishMatcher.group(1)));
        }
        return dimensions;
    }

    private enum Category {
        BREAKDOWN, DETAIL, TREND, DRIVER, RANKING, OTHER
    }

    private static class Candidate {
        private final String text;
        private final int score;

        private Candidate(String text, int score) {
            this.text = text;
            this.score = score;
        }

        public String getText() {
            return text;
        }

        public int getScore() {
            return score;
        }
    }

    private static class UserPatternProfile {
        private final Map<Category, Integer> categoryCounts = new EnumMap<>(Category.class);
        private final Map<String, Integer> dimensionCounts = new LinkedHashMap<>();

        private static UserPatternProfile of(List<String> historyQueries, String currentQuery) {
            UserPatternProfile profile = new UserPatternProfile();
            if (historyQueries == null) {
                return profile;
            }
            String normalizedCurrentQuery = normalize(currentQuery);
            historyQueries.stream().filter(StringUtils::isNotBlank)
                    .filter(query -> !normalize(query).equals(normalizedCurrentQuery))
                    .forEach(profile::record);
            return profile;
        }

        private void record(String query) {
            Category category = classify(query);
            categoryCounts.merge(category, 1, Integer::sum);
            extractDimensions(query)
                    .forEach(dimension -> dimensionCounts.merge(dimension, 1, Integer::sum));
        }

        private int getBonus(Category category) {
            return categoryCounts.getOrDefault(category, 0) * 25;
        }

        private List<String> getTopDimensions(Collection<String> excludedDimensions) {
            List<String> excluded = excludedDimensions == null ? Collections.emptyList()
                    : excludedDimensions.stream().map(SimilarQueryGenerator::normalize)
                            .collect(Collectors.toList());
            return dimensionCounts.entrySet().stream()
                    .filter(entry -> !excluded.contains(normalize(entry.getKey())))
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                            .thenComparing(Map.Entry::getKey))
                    .map(Map.Entry::getKey).limit(2).collect(Collectors.toList());
        }
    }
}
