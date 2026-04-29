import assert = require('node:assert/strict');
import {
  getChatLayoutRuntime,
  getChatPageProps,
  getChatPageRuntime,
  getChatShellRuntime,
} from './routeState';

const chatShell = getChatShellRuntime('/chat', { background: 'red' }, false);
assert.equal(chatShell.isChatRoute, true);
assert.equal(chatShell.shouldRenderCopilot, false);
assert.equal((chatShell.contentStyle as Record<string, unknown>).background, 'red');

const prefixedChatShell = getChatShellRuntime('/webapp/chat', { background: 'blue' }, false);
assert.equal(prefixedChatShell.isChatRoute, true);
assert.equal(prefixedChatShell.shouldRenderCopilot, false);
assert.equal((prefixedChatShell.contentStyle as Record<string, unknown>).background, 'blue');

const externalShell = getChatShellRuntime('/chat/external', {}, false);
assert.equal(externalShell.isChatRoute, true);
assert.equal(externalShell.shouldRenderCopilot, false);

const prefixedExternalShell = getChatShellRuntime('/webapp/chat/external', {}, false);
assert.equal(prefixedExternalShell.isChatRoute, true);
assert.equal(prefixedExternalShell.shouldRenderCopilot, false);

const nonChatShell = getChatShellRuntime('/chatSetting/model/1', {}, false);
assert.equal(nonChatShell.isChatRoute, false);
assert.equal(nonChatShell.shouldRenderCopilot, true);

const desktopLayout = getChatLayoutRuntime('/dashboard', {}, false, 'layout-token');
assert.equal(desktopLayout.copilotProps?.token, 'layout-token');
assert.equal(desktopLayout.copilotProps?.isDeveloper, true);

const chatLayout = getChatLayoutRuntime('/chat/mobile', {}, false, 'layout-token');
assert.equal(chatLayout.copilotProps, undefined);

const prefixedChatLayout = getChatLayoutRuntime('/webapp/chat', {}, false, 'layout-token');
assert.equal(prefixedChatLayout.copilotProps, undefined);

const mobileRuntime = getChatPageRuntime('/chat/mobile', '?agentId=42');
assert.equal(mobileRuntime.mobileMode, true);
assert.equal(mobileRuntime.initialAgentId, 42);

const prefixedMobileRuntime = getChatPageRuntime('/webapp/chat/mobile', '?agentId=42');
assert.equal(prefixedMobileRuntime.mobileMode, true);
assert.equal(prefixedMobileRuntime.initialAgentId, 42);

const externalRuntime = getChatPageRuntime('/chat/external', '');
assert.equal(externalRuntime.mobileMode, false);
assert.equal(externalRuntime.initialAgentId, undefined);

const prefixedExternalRuntime = getChatPageRuntime('/webapp/chat/external', '');
assert.equal(prefixedExternalRuntime.mobileMode, false);
assert.equal(prefixedExternalRuntime.initialAgentId, undefined);

const chatPageProps = getChatPageProps('/chat/mobile', '?agentId=42', 'chat-token');
assert.equal(chatPageProps.mobileMode, true);
assert.equal(chatPageProps.initialAgentId, 42);
assert.equal(chatPageProps.token, 'chat-token');
assert.equal(chatPageProps.isDeveloper, true);

const prefixedChatPageProps = getChatPageProps('/webapp/chat/mobile', '?agentId=42', 'chat-token');
assert.equal(prefixedChatPageProps.mobileMode, true);
assert.equal(prefixedChatPageProps.initialAgentId, 42);
assert.equal(prefixedChatPageProps.token, 'chat-token');
assert.equal(prefixedChatPageProps.isDeveloper, true);

console.log('chat-page-route-state-test: ok');
