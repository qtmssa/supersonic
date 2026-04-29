import { resolveChatMobileMode } from '../runtime/chatRuntime';

describe('chat mobile mode', () => {
  test('prefers explicit mobile mode over a desktop user agent', () => {
    expect(
      resolveChatMobileMode({
        explicitMobileMode: true,
        userAgent:
          'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/123.0.0.0 Safari/537.36',
      })
    ).toBe(true);
  });

  test('falls back to user-agent detection when no explicit mode is provided', () => {
    expect(
      resolveChatMobileMode({
        userAgent:
          'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/123.0.0.0 Safari/537.36',
      })
    ).toBe(false);
    expect(
      resolveChatMobileMode({
        userAgent:
          'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148',
      })
    ).toBe(true);
  });

  test('uses prefixed pathname routing before user-agent fallback', () => {
    expect(
      resolveChatMobileMode({
        pathname: '/webapp/chat/mobile',
        userAgent:
          'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/123.0.0.0 Safari/537.36',
      })
    ).toBe(true);
    expect(
      resolveChatMobileMode({
        pathname: '/webapp/chat',
        userAgent:
          'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148',
      })
    ).toBe(false);
  });
});
