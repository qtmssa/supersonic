import fs from 'node:fs';
import path from 'node:path';
import {
  CHAT_LAYOUT_HEADER_HEIGHT_FALLBACK_PX,
  CHAT_LAYOUT_HEADER_HEIGHT_VALUE,
  CHAT_LAYOUT_HEADER_HEIGHT_VAR,
  getChatChildrenWrapperStyle,
  getChatContentStyle,
} from '../../../supersonic-fe/src/pages/ChatPage/layout';

describe('chat page layout contracts', () => {
  test('uses the shared header height constant to clamp the chat viewport', () => {
    expect(CHAT_LAYOUT_HEADER_HEIGHT_VAR).toBe('--ss-app-header-height');
    expect(CHAT_LAYOUT_HEADER_HEIGHT_FALLBACK_PX).toBe(56);
    expect(getChatContentStyle().height).toBe(`calc(100dvh - ${CHAT_LAYOUT_HEADER_HEIGHT_VALUE})`);
    expect(getChatContentStyle().overflow).toBe('hidden');
    expect(getChatChildrenWrapperStyle().minWidth).toBe(0);
  });

  test('removes the global root min-width and horizontal scrolling on chat pages', () => {
    const source = fs.readFileSync(
      path.resolve(__dirname, '../../../supersonic-fe/src/global.less'),
      'utf8'
    );

    expect(source).toContain('html.ss-chat-page,');
    expect(source).toContain('body.ss-chat-page #root');
    expect(source).toMatch(/body\.ss-chat-page \.ant-layout\s*\{[^}]*height:\s*100%;/);
    expect(source).toContain('min-width: 0;');
    expect(source).toContain('overflow-x: hidden;');
  });
});
