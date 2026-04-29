package com.tencent.supersonic.chat.server.service.impl;

import com.alibaba.fastjson2.JSON;
import com.tencent.supersonic.chat.api.pojo.request.ChatExecuteReq;
import com.tencent.supersonic.chat.api.pojo.response.QueryResult;
import com.tencent.supersonic.chat.api.pojo.response.SimilarQueryRecallResp;
import com.tencent.supersonic.chat.server.persistence.dataobject.ChatQueryDO;
import com.tencent.supersonic.chat.server.persistence.repository.ChatQueryRepository;
import com.tencent.supersonic.chat.server.persistence.repository.ChatRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

class ChatManageServiceImplTest {

    @Test
    void saveQueryResultShouldSyncSimilarQueriesToLegacyField() {
        ChatManageServiceImpl service = new ChatManageServiceImpl();
        ChatQueryRepository chatQueryRepository = Mockito.mock(ChatQueryRepository.class);
        ChatRepository chatRepository = Mockito.mock(ChatRepository.class);
        ReflectionTestUtils.setField(service, "chatQueryRepository", chatQueryRepository);
        ReflectionTestUtils.setField(service, "chatRepository", chatRepository);

        ChatQueryDO chatQueryDO = new ChatQueryDO();
        chatQueryDO.setQuestionId(12L);
        Mockito.when(chatQueryRepository.getChatQueryDO(12L)).thenReturn(chatQueryDO);

        QueryResult queryResult = new QueryResult();
        queryResult.setSimilarQueries(List.of(
                SimilarQueryRecallResp.builder().queryText("按品牌看近30天销售额").build()));

        service.saveQueryResult(ChatExecuteReq.builder()
                .queryId(12L)
                .chatId(7)
                .queryText("近30天销售额怎么样")
                .build(), queryResult);

        ArgumentCaptor<ChatQueryDO> captor = ArgumentCaptor.forClass(ChatQueryDO.class);
        Mockito.verify(chatQueryRepository).updateChatQuery(captor.capture());

        ChatQueryDO saved = captor.getValue();
        QueryResult persistedQueryResult = JSON.parseObject(saved.getQueryResult(), QueryResult.class);
        List<SimilarQueryRecallResp> persistedLegacySimilarQueries =
                JSON.parseArray(saved.getSimilarQueries(), SimilarQueryRecallResp.class);

        Assertions.assertEquals("按品牌看近30天销售额",
                persistedQueryResult.getSimilarQueries().get(0).getQueryText());
        Assertions.assertEquals("按品牌看近30天销售额",
                persistedLegacySimilarQueries.get(0).getQueryText());
    }
}
