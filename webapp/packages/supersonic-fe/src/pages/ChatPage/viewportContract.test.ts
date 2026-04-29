import assert = require('node:assert/strict');
import fs = require('node:fs');
import path = require('node:path');
import {
  CHAT_LAYOUT_HEADER_HEIGHT_VALUE,
  CHAT_PAGE_ROOT_CLASS_NAME,
  getChatChildrenWrapperStyle,
} from './layout';
import { getChatLayoutRuntime, getChatPageProps } from './routeState';

const chatRoutes = [
  { pathname: '/chat', mobileMode: false },
  { pathname: '/chat/external', mobileMode: false },
  { pathname: '/chat/mobile', mobileMode: true },
];

for (const { pathname, mobileMode } of chatRoutes) {
  const runtime = getChatLayoutRuntime(pathname, {}, false, 'layout-token');
  const pageProps = getChatPageProps(pathname, '', 'chat-token');
  const contentStyle = runtime.contentStyle as Record<string, unknown>;
  const childrenWrapperStyle = runtime.childrenWrapperStyle as Record<string, unknown>;

  assert.equal(runtime.isChatRoute, true);
  assert.equal(contentStyle.height, `calc(100dvh - ${CHAT_LAYOUT_HEADER_HEIGHT_VALUE})`);
  assert.equal(contentStyle.overflow, 'hidden');
  assert.equal(childrenWrapperStyle.height, '100%');
  assert.equal(childrenWrapperStyle.overflow, 'hidden');
  assert.equal(childrenWrapperStyle.flex, 1);
  assert.equal((getChatChildrenWrapperStyle() as Record<string, unknown>).minHeight, 0);
  assert.equal(pageProps.mobileMode, mobileMode);
}

const appSource = fs.readFileSync(path.resolve(process.cwd(), 'src/app.tsx'), 'utf8');
assert.match(appSource, /contentStyle:\s*chatShellRuntime\.contentStyle/);
assert.match(appSource, /style=\{chatShellRuntime\.childrenWrapperStyle\}/);

const chatPageSource = fs.readFileSync(
  path.resolve(process.cwd(), 'src/pages/ChatPage/index.tsx'),
  'utf8'
);
assert.equal(CHAT_PAGE_ROOT_CLASS_NAME, 'ss-chat-page-root');
assert.match(chatPageSource, /className=\{CHAT_PAGE_ROOT_CLASS_NAME\}/);
assert.match(chatPageSource, /applyChatPageLayoutClass\(document\)/);

const globalStyleSource = fs.readFileSync(path.resolve(process.cwd(), 'src/global.less'), 'utf8');
assert.match(globalStyleSource, /html\.ss-chat-page,\s*body\.ss-chat-page\s*\{[\s\S]*overflow:\s*hidden;/);
assert.match(globalStyleSource, /body\.ss-chat-page #root[\s\S]*overflow:\s*hidden;/);
assert.match(globalStyleSource, /body\.ss-chat-page \.ant-layout\s*\{[\s\S]*height:\s*100%;/);
assert.match(globalStyleSource, /\.ss-chat-page-root\s*\{[\s\S]*height:\s*100%;[\s\S]*overflow:\s*hidden;/);

console.log('chat-page-viewport-contract-test: ok');
