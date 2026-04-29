package com.tencent.supersonic.chat.server.processor.parse;

import com.tencent.supersonic.chat.api.pojo.request.ChatParseReq;
import com.tencent.supersonic.chat.api.pojo.response.ChatParseResp;
import com.tencent.supersonic.chat.api.pojo.response.SimilarQueryRecallResp;
import com.tencent.supersonic.chat.server.agent.Agent;
import com.tencent.supersonic.chat.server.pojo.ParseContext;
import com.tencent.supersonic.common.pojo.Text2SQLExemplar;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class QueryRecommendProcessorTest {

    @Test
    void processShouldFallbackToGeneratedCandidatesWhenRecallFails() throws Exception {
        TestableQueryRecommendProcessor processor = new TestableQueryRecommendProcessor();
        ParseContext parseContext = buildParseContext("按品牌看近30天销售额", 7, 12L, 3);

        processor.process(parseContext);

        Assertions.assertTrue(processor.updated.await(1, TimeUnit.SECONDS));
        Assertions.assertNotNull(processor.updatedSimilarQueries);
        Assertions.assertTrue(processor.updatedSimilarQueries.size() >= 3
                && processor.updatedSimilarQueries.size() <= 5);
        Assertions.assertTrue(processor.updatedSimilarQueries.stream()
                .noneMatch(item -> "按品牌看近30天销售额".equals(item.getQueryText())));
        Assertions.assertTrue(processor.updatedSimilarQueries.stream().anyMatch(
                item -> item.getQueryText().contains("整体")
                        || item.getQueryText().contains("总体")
                        || item.getQueryText().contains("回到整体")));
    }

    @Test
    void processShouldReturnBeforeSlowRecallCompletes() throws Exception {
        BlockingQueryRecommendProcessor processor = new BlockingQueryRecommendProcessor();
        ParseContext parseContext = buildParseContext("近30天销售额怎么样", 7, 12L, 3);

        Assertions.assertTimeoutPreemptively(Duration.ofMillis(200),
                () -> processor.process(parseContext));
        Assertions.assertTrue(processor.recallStarted.await(1, TimeUnit.SECONDS));
        Assertions.assertNull(processor.updatedSimilarQueries);

        processor.allowRecallToFinish.countDown();

        Assertions.assertTrue(processor.updated.await(1, TimeUnit.SECONDS));
        Assertions.assertFalse(processor.updatedSimilarQueries.isEmpty());
    }

    private ParseContext buildParseContext(String queryText, int chatId, Long queryId, int agentId) {
        ChatParseReq request =
                ChatParseReq.builder().queryText(queryText).chatId(chatId).agentId(agentId).build();
        ChatParseResp response = new ChatParseResp(queryId);
        Agent agent = new Agent();
        agent.setId(agentId);

        ParseContext parseContext = new ParseContext(request, response);
        parseContext.setAgent(agent);
        return parseContext;
    }

    private static class TestableQueryRecommendProcessor extends QueryRecommendProcessor {
        protected List<SimilarQueryRecallResp> updatedSimilarQueries;
        protected final CountDownLatch updated = new CountDownLatch(1);

        @Override
        public List<Text2SQLExemplar> recallSimilarExemplars(String queryText, Integer agentId) {
            throw new IllegalStateException("embedding recall is unavailable");
        }

        @Override
        protected List<String> getHistoryQueries(ParseContext parseContext, Long currentQueryId) {
            return List.of("销售额趋势如何", "按渠道看销售额", "Top 10 客户销售额");
        }

        @Override
        protected void updateChatQuery(Long queryId,
                List<SimilarQueryRecallResp> similarQueries) {
            this.updatedSimilarQueries = similarQueries;
            updated.countDown();
        }
    }

    private static class BlockingQueryRecommendProcessor extends TestableQueryRecommendProcessor {
        private final CountDownLatch recallStarted = new CountDownLatch(1);
        private final CountDownLatch allowRecallToFinish = new CountDownLatch(1);

        @Override
        public List<Text2SQLExemplar> recallSimilarExemplars(String queryText, Integer agentId) {
            recallStarted.countDown();
            try {
                allowRecallToFinish.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return List.of();
        }
    }
}
