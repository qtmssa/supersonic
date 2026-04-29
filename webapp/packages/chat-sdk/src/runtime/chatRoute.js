"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.isChatRoutePath = exports.getChatRouteState = exports.normalizeChatPathname = exports.CHAT_EXTERNAL_ROUTE = exports.CHAT_MOBILE_ROUTE = exports.CHAT_STANDARD_ROUTE = void 0;
exports.CHAT_STANDARD_ROUTE = '/chat';
exports.CHAT_MOBILE_ROUTE = '/chat/mobile';
exports.CHAT_EXTERNAL_ROUTE = '/chat/external';
const normalizeChatPathname = (pathname = '') => {
    const normalizedPathname = pathname.replace(/\/+$/, '');
    const runtimePathname = normalizedPathname === '/webapp'
        ? '/'
        : normalizedPathname.startsWith('/webapp/')
            ? normalizedPathname.slice('/webapp'.length)
            : normalizedPathname;
    return runtimePathname || '/';
};
exports.normalizeChatPathname = normalizeChatPathname;
const getChatRouteState = (pathname = '') => {
    const normalizedPathname = (0, exports.normalizeChatPathname)(pathname);
    switch (normalizedPathname) {
        case exports.CHAT_STANDARD_ROUTE:
            return {
                kind: 'standard',
                isChatRoute: true,
                mobileMode: false,
                usesAppLayout: true,
            };
        case exports.CHAT_MOBILE_ROUTE:
            return {
                kind: 'mobile',
                isChatRoute: true,
                mobileMode: true,
                usesAppLayout: false,
            };
        case exports.CHAT_EXTERNAL_ROUTE:
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
exports.getChatRouteState = getChatRouteState;
const isChatRoutePath = (pathname = '') => (0, exports.getChatRouteState)(pathname).isChatRoute;
exports.isChatRoutePath = isChatRoutePath;
