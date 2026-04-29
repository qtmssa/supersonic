import axios from '../service/axiosInstance';
import { DEFAULT_CHAT_API_PREFIX } from '../runtime/chatRuntime';
import { AgentType, ModelType } from './type';

export function saveConversation(
  chatName: string,
  agentId: number,
  apiPrefix: string = DEFAULT_CHAT_API_PREFIX
) {
  return axios.post<any>(`${apiPrefix}/chat/manage/save?chatName=${chatName}&agentId=${agentId}`);
}

export function updateConversationName(
  chatName: string,
  chatId: number = 0,
  apiPrefix: string = DEFAULT_CHAT_API_PREFIX
) {
  return axios.post<any>(
    `${apiPrefix}/chat/manage/updateChatName?chatName=${chatName}&chatId=${chatId}`
  );
}

export function deleteConversation(chatId: number, apiPrefix: string = DEFAULT_CHAT_API_PREFIX) {
  return axios.post<any>(`${apiPrefix}/chat/manage/delete?chatId=${chatId}`);
}

export function getAllConversations(
  agentId?: number,
  apiPrefix: string = DEFAULT_CHAT_API_PREFIX
) {
  return axios.get<any>(`${apiPrefix}/chat/manage/getAll`, { params: { agentId } });
}

export function getModelList(apiPrefix: string = DEFAULT_CHAT_API_PREFIX) {
  return axios.get<ModelType[]>(`${apiPrefix}/chat/conf/modelList/dataSet`);
}

export function updateQAFeedback(
  questionId: number,
  score: number,
  apiPrefix: string = DEFAULT_CHAT_API_PREFIX
) {
  return axios.post<any>(
    `${apiPrefix}/chat/manage/updateQAFeedback?id=${questionId}&score=${score}&feedback=`
  );
}

export function queryMetricSuggestion(
  modelId: number,
  apiPrefix: string = DEFAULT_CHAT_API_PREFIX
) {
  return axios.get<any>(`${apiPrefix}/chat/recommend/metric/${modelId}`);
}

export function querySuggestion(modelId: number, apiPrefix: string = DEFAULT_CHAT_API_PREFIX) {
  return axios.get<any>(`${apiPrefix}/chat/recommend/${modelId}`);
}

export function queryRecommendQuestions(apiPrefix: string = DEFAULT_CHAT_API_PREFIX) {
  return axios.get<any>(`${apiPrefix}/chat/recommend/question`);
}

export function queryAgentList(apiPrefix: string = DEFAULT_CHAT_API_PREFIX) {
  return axios.get<AgentType[]>(`${apiPrefix}/chat/agent/getAgentList`);
}
