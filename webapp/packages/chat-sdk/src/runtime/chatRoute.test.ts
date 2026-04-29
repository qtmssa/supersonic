import { getChatRouteState, isChatRoutePath } from './chatRoute';

describe('chatRoute', () => {
  test('/chat keeps the app layout and uses desktop mode', () => {
    expect(getChatRouteState('/chat')).toEqual({
      kind: 'standard',
      isChatRoute: true,
      mobileMode: false,
      usesAppLayout: true,
    });
    expect(getChatRouteState('/webapp/chat')).toEqual({
      kind: 'standard',
      isChatRoute: true,
      mobileMode: false,
      usesAppLayout: true,
    });
  });

  test('/chat/mobile forces mobile mode without the app layout', () => {
    expect(getChatRouteState('/chat/mobile')).toEqual({
      kind: 'mobile',
      isChatRoute: true,
      mobileMode: true,
      usesAppLayout: false,
    });
    expect(getChatRouteState('/webapp/chat/mobile')).toEqual({
      kind: 'mobile',
      isChatRoute: true,
      mobileMode: true,
      usesAppLayout: false,
    });
  });

  test('/chat/external stays on the desktop layout and excludes adjacent routes', () => {
    expect(getChatRouteState('/chat/external')).toEqual({
      kind: 'external',
      isChatRoute: true,
      mobileMode: false,
      usesAppLayout: false,
    });
    expect(getChatRouteState('/webapp/chat/external')).toEqual({
      kind: 'external',
      isChatRoute: true,
      mobileMode: false,
      usesAppLayout: false,
    });
    expect(isChatRoutePath('/chatSetting')).toBe(false);
    expect(isChatRoutePath('/webapp/chatSetting')).toBe(false);
  });
});
