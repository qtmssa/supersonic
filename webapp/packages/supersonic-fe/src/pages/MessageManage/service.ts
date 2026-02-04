import { request } from 'umi';

export type AgentItem = {
  id?: number;
  name?: string;
};

export type ChatItem = {
  chatId: number;
  agentId?: number;
  chatName?: string;
  createTime?: string;
  lastTime?: string;
  creator?: string;
  lastQuestion?: string;
  isDelete?: number;
  isTop?: number;
};

export type QueryResult = {
  queryMode?: string;
  queryState?: string;
  querySql?: string;
  queryColumns?: any[];
  queryResults?: any[];
  textResult?: string;
  textSummary?: string;
  response?: any;
};

export type QueryResp = {
  questionId?: number;
  createTime?: string;
  chatId?: number;
  score?: number;
  feedback?: string;
  queryText?: string;
  queryResult?: QueryResult;
  parseInfos?: any[];
  similarQueries?: any[];
  parseTimeCost?: any;
};

export type PageInfo<T> = {
  list: T[];
  total: number;
  pageNum: number;
  pageSize: number;
};

export function getAgentList() {
  return request<Result<AgentItem[]>>('/api/chat/agent/getAgentList');
}

export function getChatList(agentId?: number) {
  return request<Result<ChatItem[]>>('/api/chat/manage/getAll', {
    params: agentId ? { agentId } : undefined,
    method: 'GET',
  });
}

export function pageQueryInfo(chatId: number, current: number, pageSize: number) {
  return request<Result<PageInfo<QueryResp>>>(`/api/chat/manage/pageQueryInfo?chatId=${chatId}`, {
    method: 'POST',
    data: {
      current,
      pageSize,
    },
  });
}

export function getChatQuery(queryId: number) {
  return request<Result<QueryResp>>(`/api/chat/manage/getChatQuery/${queryId}`, {
    method: 'GET',
  });
}

export function deleteChatQuery(queryId: number) {
  return request<Result<boolean>>(`/api/chat/manage/${queryId}`, {
    method: 'DELETE',
  });
}
