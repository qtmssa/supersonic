import assert = require('node:assert/strict');
import {
  getChatRouteState as getSdkChatRouteState,
  normalizeChatPathname as normalizeSdkChatPathname,
} from '../../../../chat-sdk/src/runtime/chatRoute';
import {
  getChatRouteState as getPageChatRouteState,
  normalizeChatPathname as normalizePageChatPathname,
} from './routeState';

const pathnames = [
  '/chat',
  '/chat/',
  '/chat/mobile',
  '/chat/mobile/',
  '/chat/external',
  '/chat/external/',
  '/webapp/chat',
  '/webapp/chat/',
  '/webapp/chat/mobile',
  '/webapp/chat/mobile/',
  '/webapp/chat/external',
  '/webapp/chat/external/',
  '/dashboard',
  '/webapp/dashboard',
  '/webapp',
  '/',
];

for (const pathname of pathnames) {
  assert.equal(normalizePageChatPathname(pathname), normalizeSdkChatPathname(pathname));

  const pageRouteState = getPageChatRouteState(pathname);
  const sdkRouteState = getSdkChatRouteState(pathname);
  assert.deepEqual(pageRouteState, {
    isChatRoute: sdkRouteState.isChatRoute,
    kind: sdkRouteState.kind,
    mobileMode: sdkRouteState.mobileMode,
  });
}

console.log('chat-page-route-parity-test: ok');
