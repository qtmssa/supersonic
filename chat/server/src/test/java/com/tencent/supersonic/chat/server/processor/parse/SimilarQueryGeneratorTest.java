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
}
