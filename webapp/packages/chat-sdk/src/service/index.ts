import axios from './axiosInstance';
import {
  ChatContextType,
  HistoryMsgItemType,
  HistoryType,
  MsgDataType,
  ParseDataType,
  SearchRecommendItem,
  SupersetDashboardItem,
  SupersetDashboardManageResp,
  SupersetGuestTokenResp,
} from '../common/type';
import { DEFAULT_CHAT_API_PREFIX } from '../runtime/chatRuntime';

const DEFAULT_CHAT_ID = 0;

export function searchRecommend(
  queryText: string,
  chatId?: number,
  modelId?: number,
  agentId?: number,
  apiPrefix: string = DEFAULT_CHAT_API_PREFIX
) {
  return axios.post<SearchRecommendItem[]>(`${apiPrefix}/chat/query/search`, {
    queryText,
    chatId: chatId || DEFAULT_CHAT_ID,
    modelId,
    agentId,
  });
}

export function chatQuery(
  queryText: string,
  chatId?: number,
  modelId?: number,
  filters?: any[],
  apiPrefix: string = DEFAULT_CHAT_API_PREFIX
) {
  return axios.post<MsgDataType>(`${apiPrefix}/chat/query/query`, {
    queryText,
    chatId: chatId || DEFAULT_CHAT_ID,
    modelId,
    queryFilters: filters
      ? {
          filters,
        }
      : undefined,
  });
}

export function chatParse({
  queryText,
  chatId,
  modelId,
  agentId,
  parseId,
  queryId,
  filters,
  parseInfo,
}: {
  queryText: string;
  chatId?: number;
  modelId?: number;
  agentId?: number;
  queryId?: number;
  parseId?: number;
  filters?: any[];
  parseInfo?: ChatContextType;
},
apiPrefix: string = DEFAULT_CHAT_API_PREFIX) {
  return axios.post<ParseDataType>(`${apiPrefix}/chat/query/parse`, {
    queryText,
    chatId: chatId || DEFAULT_CHAT_ID,
    dataSetId: modelId,
    agentId,
    parseId,
    queryId,
    selectedParse: parseInfo,
    queryFilters: filters
      ? {
          filters,
        }
      : undefined,
  });
}

export function chatExecute(
  queryText: string,
  chatId: number,
  parseInfo: ChatContextType,
  agentId?: number,
  streamingResult?: boolean,
  apiPrefix: string = DEFAULT_CHAT_API_PREFIX
) {
  // AgentService executes external agent calls that may take a long time.
  // Override axiosInstance default timeout (120s) to avoid frontend aborting the request.
  const requestConfig =
    parseInfo?.queryMode === 'AGENT_SERVICE' ? { timeout: 0 } : undefined;
  return axios.post<MsgDataType>(
      `${apiPrefix}/chat/query/execute`,
    {
      queryText,
      agentId,
      chatId: chatId || DEFAULT_CHAT_ID,
      queryId: parseInfo.queryId,
      parseId: parseInfo.id,
      streamingResult,
    },
    requestConfig as any
  );
}

export function getExecuteSummary(
  queryId: number,
  apiPrefix: string = DEFAULT_CHAT_API_PREFIX
) {
  return axios.post<MsgDataType>(`${apiPrefix}/chat/query/getExecuteSummary`, {
    queryId: queryId,
  });
}

export function fetchSupersetGuestToken(
  params: { pluginId?: number; embeddedId: string },
  apiPrefix: string = DEFAULT_CHAT_API_PREFIX
): Promise<SupersetGuestTokenResp> {
  return axios.post(`${apiPrefix}/chat/superset/guest-token`, params) as unknown as Promise<SupersetGuestTokenResp>;
}

export function fetchSupersetManualDashboards(
  pluginId?: number,
  apiPrefix: string = DEFAULT_CHAT_API_PREFIX
): Promise<SupersetDashboardManageResp> {
  return axios.post(`${apiPrefix}/chat/superset/dashboards/manage`, {
    pluginId,
  }) as unknown as Promise<SupersetDashboardManageResp>;
}

export function createSupersetDashboard(params: {
  pluginId?: number;
  title: string;
}, apiPrefix: string = DEFAULT_CHAT_API_PREFIX): Promise<SupersetDashboardItem> {
  return axios.post(`${apiPrefix}/chat/superset/dashboard/create`, params) as unknown as Promise<SupersetDashboardItem>;
}

export function pushSupersetChartToDashboard(params: {
  pluginId?: number;
  dashboardId: number;
  chartId: number;
}, apiPrefix: string = DEFAULT_CHAT_API_PREFIX): Promise<boolean> {
  return axios.post(`${apiPrefix}/chat/superset/dashboard/push`, params) as unknown as Promise<boolean>;
}

export function switchEntity(
  entityId: string,
  modelId?: number,
  chatId?: number,
  apiPrefix: string = DEFAULT_CHAT_API_PREFIX
) {
  return axios.post<any>(`${apiPrefix}/chat/query/switchQuery`, {
    queryText: entityId,
    modelId,
    chatId: chatId || DEFAULT_CHAT_ID,
  });
}

export function queryData(
  chatContext: Partial<ChatContextType>,
  apiPrefix: string = DEFAULT_CHAT_API_PREFIX
) {
  return axios.post<MsgDataType>(`${apiPrefix}/chat/query/queryData`, chatContext);
}

export function getHistoryMsg(
  current: number,
  chatId: number = DEFAULT_CHAT_ID,
  pageSize: number = 10,
  apiPrefix: string = DEFAULT_CHAT_API_PREFIX
) {
  return axios.post<HistoryType>(`${apiPrefix}/chat/manage/pageQueryInfo?chatId=${chatId}`, {
    current,
    pageSize,
  });
}

export function querySimilarQuestions(
  queryId: number,
  apiPrefix: string = DEFAULT_CHAT_API_PREFIX
) {
  return axios.get<HistoryMsgItemType>(`${apiPrefix}/chat/manage/getChatQuery/${queryId}`);
}

export function deleteQuery(queryId: number, apiPrefix: string = DEFAULT_CHAT_API_PREFIX) {
  return axios.delete<any>(`${apiPrefix}/chat/manage/${queryId}`);
}

export function queryEntities(
  entityId: string | number,
  modelId: number,
  apiPrefix: string = DEFAULT_CHAT_API_PREFIX
) {
  return axios.post<any>(`${apiPrefix}/chat/query/choice`, {
    entityId,
    modelId,
  });
}

export function updateQAFeedback(
  questionId: number,
  score: number,
  apiPrefix: string = DEFAULT_CHAT_API_PREFIX
) {
  return axios.post<any>(`${apiPrefix}/chat/manage/updateQAFeedback?id=${questionId}&score=${score}&feedback=`);
}

export function queryDimensionValues(
  modelId: number,
  bizName: string,
  agentId: number,
  elementID: number,
  value: string,
  apiPrefix: string = DEFAULT_CHAT_API_PREFIX
) {
  return axios.post<any>(`${apiPrefix}/chat/query/queryDimensionValue`, {
    modelId,
    bizName,
    agentId,
    elementID,
    value,
  });
}
