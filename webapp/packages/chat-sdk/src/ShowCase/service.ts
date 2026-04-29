import axios from '../service/axiosInstance';
import { DEFAULT_CHAT_API_PREFIX } from '../runtime/chatRuntime';
import { ShowCaseType } from './type';

export function queryShowCase(
  agentId: number,
  current: number,
  pageSize: number,
  apiPrefix: string = DEFAULT_CHAT_API_PREFIX
) {
  return axios.post<ShowCaseType>(`${apiPrefix}/chat/manage/queryShowCase?agentId=${agentId}`, {
    current,
    pageSize,
  });
}
