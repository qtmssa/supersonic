package com.tencent.supersonic.chat.server.processor.parse;

import com.tencent.supersonic.chat.api.pojo.response.SimilarQueryRecallResp;
import com.tencent.supersonic.common.pojo.Text2SQLExemplar;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class SimilarQueryGeneratorTest {

    private final SimilarQueryGenerator generator = new SimilarQueryGenerator();

    @Test
    void generateShouldFallbackToSessionHabitsWhenMemoryRecallIsEmpty() {
        List<SimilarQueryRecallResp> suggestions = generator.generate("近30天销售额怎么样", List.of(),
                List.of("按品牌看销售额", "销售额趋势如何", "Top 10 客户销售额"), 5);

        Assertions.assertTrue(suggestions.size() >= 3 && suggestions.size() <= 5);
        Assertions.assertTrue(
                suggestions.stream().noneMatch(item -> "近30天销售额怎么样".equals(item.getQueryText())));
        Assertions.assertTrue(
                suggestions.stream().anyMatch(item -> item.getQueryText().contains("按品牌")));
        Assertions.assertTrue(suggestions.stream().anyMatch(
                item -> item.getQueryText().contains("变化") || item.getQueryText().contains("趋势")));
    }

    @Test
    void generateShouldKeepUsefulRecallResultsAndDeduplicateCurrentQuery() {
        List<Text2SQLExemplar> exemplars = List.of(
                Text2SQLExemplar.builder().question("近30天销售额怎么样").build(),
                Text2SQLExemplar.builder().question("近30天销售额按品牌怎么看").similarity(0.95).build(),
                Text2SQLExemplar.builder().question("影响近30天销售额的主要因素有哪些").similarity(0.82).build());

        List<SimilarQueryRecallResp> suggestions =
                generator.generate("近30天销售额怎么样", exemplars, List.of("按品牌看销售额"), 5);

        Assertions.assertFalse(suggestions.isEmpty());
        Assertions.assertEquals("近30天销售额按品牌怎么看", suggestions.get(0).getQueryText());
        Assertions.assertTrue(
                suggestions.stream().noneMatch(item -> "近30天销售额怎么样".equals(item.getQueryText())));
    }

    @Test
    void generateShouldOfferRollUpQuestionForBreakdownQueries() {
        List<SimilarQueryRecallResp> suggestions =
                generator.generate("按品牌看近30天销售额", List.of(), List.of("按渠道看销售额"), 5);

        Assertions.assertTrue(suggestions.stream()
                .anyMatch(item -> item.getQueryText().contains("整体")
                        || item.getQueryText().contains("总体")
                        || item.getQueryText().contains("回到整体")));
    }

    @Test
    void generateShouldKeepDiverseAnglesWhenRecallResultsAreRich() {
        List<Text2SQLExemplar> exemplars = List.of(
                Text2SQLExemplar.builder().question("近30天销售额按品牌怎么看").similarity(0.99).build(),
                Text2SQLExemplar.builder().question("近30天销售额按渠道怎么看").similarity(0.98).build(),
                Text2SQLExemplar.builder().question("近30天销售额按区域怎么看").similarity(0.97).build(),
                Text2SQLExemplar.builder().question("近30天销售额按客户等级怎么看").similarity(0.96).build(),
                Text2SQLExemplar.builder().question("近30天销售额按产品类型怎么看").similarity(0.95).build());

        List<SimilarQueryRecallResp> suggestions =
                generator.generate("近30天销售额怎么样", exemplars, List.of("按品牌看销售额"), 5);

        Assertions.assertEquals(5, suggestions.size());
        Assertions.assertTrue(suggestions.stream()
                .anyMatch(item -> item.getQueryText().contains("按")));
        Assertions.assertTrue(suggestions.stream().anyMatch(
                item -> item.getQueryText().contains("下钻")
                        || item.getQueryText().contains("明细")));
        Assertions.assertTrue(suggestions.stream().anyMatch(
                item -> item.getQueryText().contains("变化")
                        || item.getQueryText().contains("趋势")
                        || item.getQueryText().contains("原因")
                        || item.getQueryText().contains("影响")));
    }
}
