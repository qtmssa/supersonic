package com.tencent.supersonic.chat.server.processor.parse;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.tencent.supersonic.chat.api.pojo.response.QueryResp;
import com.tencent.supersonic.chat.api.pojo.response.SimilarQueryRecallResp;
import com.tencent.supersonic.chat.server.persistence.dataobject.ChatQueryDO;
import com.tencent.supersonic.chat.server.persistence.repository.ChatQueryRepository;
import com.tencent.supersonic.chat.server.pojo.ParseContext;
import com.tencent.supersonic.common.config.EmbeddingConfig;
import com.tencent.supersonic.common.pojo.Text2SQLExemplar;
import com.tencent.supersonic.common.service.ExemplarService;
import com.tencent.supersonic.common.util.ContextUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * MetricRecommendProcessor fills recommended query based on embedding similarity.
 **/
@Slf4j
public class QueryRecommendProcessor implements ParseResultProcessor {

    private static final int SIMILAR_QUERY_LIMIT = 5;

    private final SimilarQueryGenerator similarQueryGenerator = new SimilarQueryGenerator();

    @Override
    public boolean accept(ParseContext parseContext) {
        return true;
    }

    @Override
    public void process(ParseContext parseContext) {
        CompletableFuture.runAsync(() -> doProcess(parseContext));
    }

    @SneakyThrows
    private void doProcess(ParseContext parseContext) {
        Long queryId = parseContext.getResponse().getQueryId();
        try {
            List<Text2SQLExemplar> recalledExemplars = recallSimilarExemplars(
                    parseContext.getRequest().getQueryText(), parseContext.getAgent().getId());
            List<String> historyQueries = getHistoryQueries(parseContext, queryId);
            List<SimilarQueryRecallResp> solvedQueries =
                    similarQueryGenerator.generate(parseContext.getRequest().getQueryText(),
                            recalledExemplars, historyQueries, SIMILAR_QUERY_LIMIT);
            updateChatQuery(queryId, solvedQueries);
        } catch (Exception e) {
            log.warn("Failed to generate similar queries, queryId={}", queryId, e);
        }
    }

    public List<Text2SQLExemplar> recallSimilarExemplars(String queryText, Integer agentId) {
        ExemplarService exemplarService = ContextUtils.getBean(ExemplarService.class);
        EmbeddingConfig embeddingConfig = ContextUtils.getBean(EmbeddingConfig.class);
        String memoryCollectionName = embeddingConfig.getMemoryCollectionName(agentId);
        return exemplarService.recallExemplars(memoryCollectionName, queryText,
                SIMILAR_QUERY_LIMIT);
    }

    private List<String> getHistoryQueries(ParseContext parseContext, Long currentQueryId) {
        ChatQueryRepository chatQueryRepository = ContextUtils.getBean(ChatQueryRepository.class);
        Integer chatId = parseContext.getRequest().getChatId();
        if (chatId == null) {
            return List.of();
        }
        return chatQueryRepository.getChatQueries(chatId).stream().filter(Objects::nonNull)
                .filter(query -> !Objects.equals(query.getQuestionId(), currentQueryId))
                .map(QueryResp::getQueryText)
                .filter(org.apache.commons.lang3.StringUtils::isNotBlank)
                .collect(Collectors.toList());
    }

    private void updateChatQuery(Long queryId, List<SimilarQueryRecallResp> similarQueries) {
        ChatQueryRepository chatQueryRepository = ContextUtils.getBean(ChatQueryRepository.class);
        UpdateWrapper<ChatQueryDO> updateWrapper = new UpdateWrapper<>();
        updateWrapper.lambda().eq(ChatQueryDO::getQuestionId, queryId)
                .set(ChatQueryDO::getSimilarQueries, JSONObject.toJSONString(similarQueries));
        chatQueryRepository.updateChatQuery(new ChatQueryDO(), updateWrapper);
    }
}
