package com.tencent.supersonic.chat.server.service.impl;

import com.alibaba.fastjson2.JSON;
import com.tencent.supersonic.chat.api.pojo.request.ChatExecuteReq;
import com.tencent.supersonic.chat.api.pojo.response.QueryResult;
import com.tencent.supersonic.chat.api.pojo.response.SimilarQueryRecallResp;
import com.tencent.supersonic.chat.server.persistence.dataobject.ChatQueryDO;
import com.tencent.supersonic.chat.server.processor.execute.DataInterpretProcessor;
import com.tencent.supersonic.chat.server.service.ChatManageService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

class ChatQueryServiceImplTest {

    @Test
    void getTextSummaryShouldApplyPersistedSimilarQueriesAuthority() {
        ChatQueryServiceImpl service = new ChatQueryServiceImpl();
        ChatManageService chatManageService = Mockito.mock(ChatManageService.class);
        ReflectionTestUtils.setField(service, "chatManageService", chatManageService);

        QueryResult queryResult = new QueryResult();
        queryResult.setSimilarQueries(List.of(
                SimilarQueryRecallResp.builder().queryText("本地结果中的相似问题").build()));

        ChatQueryDO chatQueryDO = new ChatQueryDO();
        chatQueryDO.setQueryResult(JSON.toJSONString(queryResult));
        chatQueryDO.setSimilarQueries(JSON.toJSONString(List.of(
                SimilarQueryRecallResp.builder().queryText("持久化权威相似问题").build())));
        Mockito.when(chatManageService.getChatQueryDO(12L)).thenReturn(chatQueryDO);

        try (MockedStatic<DataInterpretProcessor> mockedStatic =
                Mockito.mockStatic(DataInterpretProcessor.class)) {
            mockedStatic.when(() -> DataInterpretProcessor.getTextSummary(12L)).thenReturn("");

            QueryResult result = service.getTextSummary(ChatExecuteReq.builder().queryId(12L).build());

            Assertions.assertEquals("持久化权威相似问题",
                    result.getSimilarQueries().get(0).getQueryText());
        }
    }
}
