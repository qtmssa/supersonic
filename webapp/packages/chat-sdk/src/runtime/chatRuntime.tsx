import { createContext, useContext } from 'react';
import { getChatRouteState, type ChatRouteState } from './chatRoute';

const MOBILE_USER_AGENT_PATTERN = /(iPhone|iPod|Android|ios)/i;

const getDefaultUserAgent = () =>
  typeof window === 'undefined' ? '' : window.navigator.userAgent || '';

export type ChatRuntime = {
  apiPrefix: '/api' | '/openapi';
  mobileMode: boolean;
  routeState: ChatRouteState;
};

export type CreateChatRuntimeOptions = {
  explicitMobileMode?: boolean;
  pathname?: string;
  userAgent?: string;
};

export const resolveChatMobileMode = (options: CreateChatRuntimeOptions = {}) => {
  const { explicitMobileMode, pathname, userAgent } = options;
  if (typeof explicitMobileMode === 'boolean') {
    return explicitMobileMode;
  }
  if (pathname) {
    const routeState = getChatRouteState(pathname);
    if (routeState.isChatRoute) {
      return routeState.mobileMode;
    }
  }
  return MOBILE_USER_AGENT_PATTERN.test(userAgent || getDefaultUserAgent());
};

export const createChatRuntime = (options: CreateChatRuntimeOptions = {}): ChatRuntime => {
  const routeState = getChatRouteState(options.pathname || '');
  const mobileMode = resolveChatMobileMode(options);

  return {
    apiPrefix: mobileMode ? '/openapi' : '/api',
    mobileMode,
    routeState,
  };
};

const DEFAULT_CHAT_ROUTE_STATE = getChatRouteState('');

export const DEFAULT_CHAT_RUNTIME: ChatRuntime = {
  apiPrefix: '/api',
  mobileMode: false,
  routeState: DEFAULT_CHAT_ROUTE_STATE,
};

export const DEFAULT_CHAT_API_PREFIX = DEFAULT_CHAT_RUNTIME.apiPrefix;

const ChatRuntimeContext = createContext<ChatRuntime>(DEFAULT_CHAT_RUNTIME);

export const ChatRuntimeProvider = ChatRuntimeContext.Provider;

export const useChatRuntime = () => useContext(ChatRuntimeContext);

export const useChatMobileMode = () => useChatRuntime().mobileMode;

export const useChatApiPrefix = () => useChatRuntime().apiPrefix;
