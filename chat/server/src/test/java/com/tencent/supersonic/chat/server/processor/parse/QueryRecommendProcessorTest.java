package com.tencent.supersonic.chat.server.processor.parse;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.tencent.supersonic.chat.api.pojo.request.ChatParseReq;
import com.tencent.supersonic.chat.api.pojo.response.ChatParseResp;
import com.tencent.supersonic.chat.api.pojo.response.QueryResp;
import com.tencent.supersonic.chat.server.persistence.dataobject.ChatQueryDO;
import com.tencent.supersonic.chat.server.persistence.repository.ChatQueryRepository;
import com.tencent.supersonic.chat.server.pojo.ParseContext;
import com.tencent.supersonic.common.pojo.Text2SQLExemplar;
import com.tencent.supersonic.common.util.ContextUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class QueryRecommendProcessorTest {

    @Test
    void processShouldStillPersistGeneratedSuggestionsWhenRecallFails() throws Exception {
        ChatQueryRepository chatQueryRepository = Mockito.mock(ChatQueryRepository.class);
        Mockito.when(chatQueryRepository.getChatQueries(7)).thenReturn(List.of(
                buildQueryResp(1L, "按品牌看销售额"),
                buildQueryResp(2L, "销售额趋势如何"),
                buildQueryResp(99L, "近30天销售额怎么样")));

        CountDownLatch persistedLatch = new CountDownLatch(1);
        String[] persistedSimilarQueries = new String[1];
        Mockito.doAnswer(invocation -> {
            UpdateWrapper<ChatQueryDO> updateWrapper = invocation.getArgument(1);
            persistedSimilarQueries[0] = updateWrapper.getParamNameValuePairs().values().stream()
                    .filter(String.class::isInstance).map(String.class::cast)
                    .filter(value -> value.startsWith("["))
                    .findFirst().orElse(null);
            persistedLatch.countDown();
            return null;
        }).when(chatQueryRepository).updateChatQuery(Mockito.any(ChatQueryDO.class),
                Mockito.any(UpdateWrapper.class));

        QueryRecommendProcessor processor = new QueryRecommendProcessor() {
            @Override
            public List<Text2SQLExemplar> recallSimilarExemplars(String queryText, Integer agentId) {
                throw new IllegalStateException("embedding unavailable");
            }
        };
        ParseContext parseContext = new ParseContext(ChatParseReq.builder()
                .queryText("近30天销售额怎么样")
                .chatId(7)
                .build(), new ChatParseResp(99L));

        try (MockedStatic<ContextUtils> mockedContext = Mockito.mockStatic(ContextUtils.class)) {
            mockedContext.when(() -> ContextUtils.getBean(ChatQueryRepository.class))
                    .thenReturn(chatQueryRepository);

            processor.process(parseContext);

            Assertions.assertTrue(persistedLatch.await(2, TimeUnit.SECONDS));
        }

        Assertions.assertNotNull(persistedSimilarQueries[0]);
        Assertions.assertTrue(persistedSimilarQueries[0].contains("按品牌"));
    }

    private QueryResp buildQueryResp(Long queryId, String queryText) {
        QueryResp queryResp = new QueryResp();
        queryResp.setQuestionId(queryId);
        queryResp.setQueryText(queryText);
        return queryResp;
    }
}
