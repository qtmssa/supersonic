import type { CSSProperties } from 'react';
import { getChatChildrenWrapperStyle, getChatContentStyle } from './layout';

export const CHAT_STANDARD_ROUTE = '/chat';
export const CHAT_MOBILE_ROUTE = '/chat/mobile';
export const CHAT_EXTERNAL_ROUTE = '/chat/external';

type ChatRouteKind = 'standard' | 'mobile' | 'external' | null;

type ChatRouteState = {
  isChatRoute: boolean;
  kind: ChatRouteKind;
  mobileMode: boolean;
};

type ChatCopilotProps = {
  token: string;
  isDeveloper: true;
};

type ChatPageProps = {
  initialAgentId?: number;
  mobileMode: boolean;
  token: string;
  isDeveloper: true;
};

export const normalizeChatPathname = (pathname: string = '') => {
  const normalizedPathname = pathname.replace(/\/+$/, '');
  const runtimePathname =
    normalizedPathname === '/webapp'
      ? '/'
      : normalizedPathname.startsWith('/webapp/')
        ? normalizedPathname.slice('/webapp'.length)
        : normalizedPathname;

  return runtimePathname || '/';
};

export const getChatRouteState = (pathname: string = ''): ChatRouteState => {
  const normalizedPathname = normalizeChatPathname(pathname);

  switch (normalizedPathname) {
    case CHAT_STANDARD_ROUTE:
      return { isChatRoute: true, kind: 'standard', mobileMode: false };
    case CHAT_MOBILE_ROUTE:
      return { isChatRoute: true, kind: 'mobile', mobileMode: true };
    case CHAT_EXTERNAL_ROUTE:
      return { isChatRoute: true, kind: 'external', mobileMode: false };
    default:
      return { isChatRoute: false, kind: null, mobileMode: false };
  }
};

export const getChatShellRuntime = (
  pathname: string,
  baseStyle: CSSProperties = {},
  isMobileDevice: boolean
) => {
  const routeState = getChatRouteState(pathname);

  return {
    ...routeState,
    contentStyle: routeState.isChatRoute
      ? getChatContentStyle(baseStyle)
      : { ...baseStyle },
    childrenWrapperStyle: routeState.isChatRoute ? getChatChildrenWrapperStyle() : undefined,
    shouldRenderCopilot: !routeState.isChatRoute && !isMobileDevice,
  };
};

export const getChatLayoutRuntime = (
  pathname: string,
  baseStyle: CSSProperties = {},
  isMobileDevice: boolean,
  token: string
) => {
  const runtime = getChatShellRuntime(pathname, baseStyle, isMobileDevice);

  return {
    ...runtime,
    copilotProps: runtime.shouldRenderCopilot
      ? ({ token, isDeveloper: true } satisfies ChatCopilotProps)
      : undefined,
  };
};

export const getChatPageRuntime = (pathname: string, search: string = '') => {
  const routeState = getChatRouteState(pathname);
  const query = new URLSearchParams(search.startsWith('?') ? search.slice(1) : search);
  const agentId = query.get('agentId');
  const initialAgentId = agentId && /^\d+$/.test(agentId) ? Number(agentId) : undefined;

  return {
    ...routeState,
    initialAgentId,
  };
};

export const getChatPageProps = (
  pathname: string,
  search: string = '',
  token: string
): ChatPageProps => {
  const runtime = getChatPageRuntime(pathname, search);

  return {
    initialAgentId: runtime.initialAgentId,
    mobileMode: runtime.mobileMode,
    token,
    isDeveloper: true,
  };
};
