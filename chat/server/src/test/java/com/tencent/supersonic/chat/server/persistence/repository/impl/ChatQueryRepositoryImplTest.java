package com.tencent.supersonic.chat.server.persistence.repository.impl;

import com.alibaba.fastjson2.JSON;
import com.tencent.supersonic.chat.api.pojo.response.QueryResp;
import com.tencent.supersonic.chat.api.pojo.response.QueryResult;
import com.tencent.supersonic.chat.api.pojo.response.SimilarQueryRecallResp;
import com.tencent.supersonic.chat.server.persistence.dataobject.ChatQueryDO;
import com.tencent.supersonic.chat.server.persistence.mapper.ChatQueryDOMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

class ChatQueryRepositoryImplTest {

    @Test
    void getChatQueryShouldTreatPersistedSimilarQueriesAsAuthoritativeSource() {
        ChatQueryRepositoryImpl repository = new ChatQueryRepositoryImpl();
        ChatQueryDOMapper chatQueryDOMapper = Mockito.mock(ChatQueryDOMapper.class);
        ReflectionTestUtils.setField(repository, "chatQueryDOMapper", chatQueryDOMapper);

        QueryResult queryResult = new QueryResult();
        queryResult.setSimilarQueries(List.of(
                SimilarQueryRecallResp.builder().queryText("旧的嵌套相似问题").build()));

        ChatQueryDO chatQueryDO = new ChatQueryDO();
        chatQueryDO.setQuestionId(12L);
        chatQueryDO.setQueryResult(JSON.toJSONString(queryResult));
        chatQueryDO.setSimilarQueries(JSON.toJSONString(List.of(
                SimilarQueryRecallResp.builder().queryText("新的权威相似问题").build())));
        Mockito.when(chatQueryDOMapper.selectById(12L)).thenReturn(chatQueryDO);

        QueryResp response = repository.getChatQuery(12L);

        Assertions.assertEquals("新的权威相似问题",
                response.getSimilarQueries().get(0).getQueryText());
        Assertions.assertEquals("新的权威相似问题",
                response.getQueryResult().getSimilarQueries().get(0).getQueryText());
    }
}
