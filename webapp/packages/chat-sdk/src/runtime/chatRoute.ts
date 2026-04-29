export const CHAT_STANDARD_ROUTE = '/chat';
export const CHAT_MOBILE_ROUTE = '/chat/mobile';
export const CHAT_EXTERNAL_ROUTE = '/chat/external';

export type ChatRouteKind = 'standard' | 'mobile' | 'external';

export type ChatRouteState = {
  kind: ChatRouteKind | null;
  isChatRoute: boolean;
  mobileMode: boolean;
  usesAppLayout: boolean;
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
      return {
        kind: 'standard',
        isChatRoute: true,
        mobileMode: false,
        usesAppLayout: true,
      };
    case CHAT_MOBILE_ROUTE:
      return {
        kind: 'mobile',
        isChatRoute: true,
        mobileMode: true,
        usesAppLayout: false,
      };
    case CHAT_EXTERNAL_ROUTE:
      return {
        kind: 'external',
        isChatRoute: true,
        mobileMode: false,
        usesAppLayout: false,
      };
    default:
      return {
        kind: null,
        isChatRoute: false,
        mobileMode: false,
        usesAppLayout: false,
      };
  }
};

export const isChatRoutePath = (pathname: string = '') => getChatRouteState(pathname).isChatRoute;
