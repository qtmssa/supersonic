import { render, screen } from '@testing-library/react';
import {
  ChatRuntimeProvider,
  createChatRuntime,
  useChatApiPrefix,
  useChatMobileMode,
} from './chatRuntime';

const RuntimeProbe = ({ label }: { label: string }) => {
  const mobileMode = useChatMobileMode();
  const apiPrefix = useChatApiPrefix();

  return (
    <div data-testid={label} data-mobile-mode={String(mobileMode)} data-api-prefix={apiPrefix} />
  );
};

describe('chatRuntime', () => {
  test('keeps mobile mode isolated per chat instance', () => {
    render(
      <>
        <ChatRuntimeProvider
          value={createChatRuntime({
            explicitMobileMode: true,
            userAgent:
              'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/123.0.0.0 Safari/537.36',
          })}
        >
          <RuntimeProbe label="mobile-chat" />
        </ChatRuntimeProvider>
        <ChatRuntimeProvider
          value={createChatRuntime({
            explicitMobileMode: false,
            userAgent:
              'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/123.0.0.0 Safari/537.36',
          })}
        >
          <RuntimeProbe label="desktop-chat" />
        </ChatRuntimeProvider>
      </>
    );

    expect(screen.getByTestId('mobile-chat')).toHaveAttribute('data-mobile-mode', 'true');
    expect(screen.getByTestId('mobile-chat')).toHaveAttribute('data-api-prefix', '/openapi');
    expect(screen.getByTestId('desktop-chat')).toHaveAttribute('data-mobile-mode', 'false');
    expect(screen.getByTestId('desktop-chat')).toHaveAttribute('data-api-prefix', '/api');
  });

  test('keeps the default runtime stable even under a mobile user agent', () => {
    const originalDescriptor = Object.getOwnPropertyDescriptor(window.navigator, 'userAgent');

    Object.defineProperty(window.navigator, 'userAgent', {
      configurable: true,
      value:
        'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148',
    });

    try {
      render(<RuntimeProbe label="default-runtime" />);

      expect(screen.getByTestId('default-runtime')).toHaveAttribute('data-mobile-mode', 'false');
      expect(screen.getByTestId('default-runtime')).toHaveAttribute('data-api-prefix', '/api');
    } finally {
      if (originalDescriptor) {
        Object.defineProperty(window.navigator, 'userAgent', originalDescriptor);
      }
    }
  });

  test('derives api prefix and mobile mode from prefixed chat pathnames', () => {
    const standardRuntime = createChatRuntime({ pathname: '/webapp/chat' });
    const mobileRuntime = createChatRuntime({ pathname: '/webapp/chat/mobile' });

    expect(standardRuntime.apiPrefix).toBe('/api');
    expect(standardRuntime.mobileMode).toBe(false);
    expect(standardRuntime.routeState.kind).toBe('standard');
    expect(mobileRuntime.apiPrefix).toBe('/openapi');
    expect(mobileRuntime.mobileMode).toBe(true);
    expect(mobileRuntime.routeState.kind).toBe('mobile');
  });
});
