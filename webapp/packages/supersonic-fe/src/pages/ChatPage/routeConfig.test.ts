import assert = require('node:assert/strict');
import routes from '../../../config/routes';

const getRoute = (pathname: string) => routes.find((route: any) => route.path === pathname);

const standardChatRoute = getRoute('/chat');
assert.ok(standardChatRoute);
assert.equal(standardChatRoute?.component, './ChatPage');
assert.equal(standardChatRoute?.layout, undefined);

const mobileChatRoute = getRoute('/chat/mobile');
assert.ok(mobileChatRoute);
assert.equal(mobileChatRoute?.component, './ChatPage');
assert.equal(mobileChatRoute?.layout, false);

const externalChatRoute = getRoute('/chat/external');
assert.ok(externalChatRoute);
assert.equal(externalChatRoute?.component, './ChatPage');
assert.equal(externalChatRoute?.layout, false);

console.log('chat-page-route-config-test: ok');
