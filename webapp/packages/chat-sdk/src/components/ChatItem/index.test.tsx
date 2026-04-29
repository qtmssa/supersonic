import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import ChatItem from './index';

jest.mock('./ExecuteItem', () => {
  const React = require('react');
  return (props: any) => {
    const { ChartItemContext } = require('./index');
    const { reportMessageWidth } = React.useContext(ChartItemContext);

    React.useEffect(() => {
      props.data?.__testInitialWidthReports?.forEach((report: any) => {
        reportMessageWidth(report);
      });
    }, [props.data, reportMessageWidth]);

    return (
      <div
        data-testid="execute-item"
        data-has-data={String(Boolean(props.data))}
        data-query-mode={props.data?.queryMode || ''}
        data-execute-tip={props.executeTip || ''}
      >
        <button
          type="button"
          data-testid="execute-item-next-width-report"
          onClick={() => {
            props.data?.__testNextWidthReports?.forEach((report: any) => {
              reportMessageWidth(report);
            });
          }}
        />
      </div>
    );
  };
});
jest.mock('./ExpandParseTip', () => () => null);
jest.mock('./ParseTip', () => () => null);
jest.mock('./SqlItem', () => () => null);
jest.mock('./SimilarQuestionItem', () => (props: any) => (
  <div data-testid="similar-question-item" data-default-expanded={String(props.defaultExpanded)} />
));
jest.mock('../Tools', () => () => null);
jest.mock('../IconFont', () => () => null);
jest.mock('../../service', () => ({
  chatExecute: jest.fn(),
  chatParse: jest.fn(),
  queryData: jest.fn(),
  deleteQuery: jest.fn(),
  switchEntity: jest.fn(),
  getExecuteSummary: jest.fn(),
}));
jest.mock('../../utils/utils', () => ({
  isMobile: false,
  exportCsvFile: jest.fn(),
}));
jest.mock('../../hooks', () => ({
  useMethodRegister: () => ({
    register: jest.fn(),
    call: jest.fn(),
  }),
}));
jest.mock('antd', () => {
  const actual = jest.requireActual('antd');
  return {
    ...actual,
    Spin: ({ children }: any) => <>{children}</>,
    message: {
      success: jest.fn(),
      error: jest.fn(),
    },
  };
});

describe('ChatItem', () => {
  const originalResizeObserver = global.ResizeObserver;
  const originalGetBoundingClientRect = Element.prototype.getBoundingClientRect;

  beforeEach(() => {
    global.ResizeObserver = class {
      observe() {}
      disconnect() {}
    } as any;
    Element.prototype.getBoundingClientRect = jest.fn(function (this: Element) {
      const element = this as HTMLElement;
      const width =
        element.classList.contains('ss-chat-item')
          ? 1000
          : element.classList.contains('ss-chat-item-message-card')
            ? 320
            : 0;

      return {
        width,
        height: 0,
        top: 0,
        left: 0,
        bottom: 0,
        right: width,
        x: 0,
        y: 0,
        toJSON: () => ({}),
      };
    });
  });

  afterAll(() => {
    global.ResizeObserver = originalResizeObserver;
    Element.prototype.getBoundingClientRect = originalGetBoundingClientRect;
  });

  test('accepts successful superset results even when queryColumns are empty', async () => {
    render(
      <ChatItem
        msg=""
        conversationId={20}
        msgData={
          {
            queryId: 66,
            queryMode: 'SUPERSET',
            queryState: 'SUCCESS',
            queryColumns: [],
            queryResults: [],
            response: {
              name: 'Superset',
              pluginId: 2,
              pluginType: 'SUPERSET',
              fallback: false,
              vizType: 'table',
              webPage: {
                url: '',
                params: [],
                paramOptions: [],
                valueParams: [],
              },
            },
            chatContext: {
              id: 1,
              dimensionFilters: [],
              dateInfo: {},
            },
            similarQueries: [],
          } as any
        }
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('execute-item')).toHaveAttribute('data-has-data', 'true');
    });
    expect(screen.getByTestId('execute-item')).toHaveAttribute('data-query-mode', 'SUPERSET');
    expect(screen.getByTestId('execute-item')).toHaveAttribute('data-execute-tip', '');
  });

  test('renders similar questions expanded by default after execution', async () => {
    render(
      <ChatItem
        msg=""
        conversationId={20}
        msgData={
          {
            queryId: 66,
            queryMode: 'SQL',
            queryState: 'SUCCESS',
            queryColumns: [{ nameEn: 'sales', name: '销售额' }],
            queryResults: [{ sales: 10 }],
            chatContext: {
              id: 1,
              dimensionFilters: [],
              dateInfo: {},
            },
            similarQueries: [],
          } as any
        }
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('similar-question-item')).toHaveAttribute(
        'data-default-expanded',
        'true'
      );
    });
  });

  test('clamps the final bubble width to 90% of the available message row width', async () => {
    render(
      <ChatItem
        msg=""
        conversationId={20}
        msgData={
          {
            queryId: 77,
            queryMode: 'SQL',
            queryState: 'SUCCESS',
            queryColumns: [{ nameEn: 'sales', name: '销售额', showType: 'NUMBER' }],
            queryResults: [{ sales: 10 }],
            chatContext: {
              id: 1,
              dimensionFilters: [],
              dateInfo: {},
            },
            __testInitialWidthReports: [{ scopeKey: 'result:chart-1', preferredWidth: 1200 }],
            similarQueries: [],
          } as any
        }
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('chat-item-content')).toHaveStyle({ width: '900px' });
    });
    expect(screen.getByTestId('chat-item-content')).toHaveStyle({ maxWidth: '900px' });
  });

  test('resets cached chart width when a new result scope reports no preferred width', async () => {
    render(
      <ChatItem
        msg=""
        conversationId={20}
        msgData={
          {
            queryId: 88,
            queryMode: 'SQL',
            queryState: 'SUCCESS',
            queryColumns: [{ nameEn: 'sales', name: '销售额', showType: 'NUMBER' }],
            queryResults: [{ sales: 10 }],
            chatContext: {
              id: 1,
              dimensionFilters: [],
              dateInfo: {},
            },
            __testInitialWidthReports: [{ scopeKey: 'result:chart-1', preferredWidth: 720 }],
            __testNextWidthReports: [{ scopeKey: 'result:text-2' }],
            similarQueries: [],
          } as any
        }
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('chat-item-content')).toHaveStyle({ width: '720px' });
    });
    expect(screen.getByTestId('chat-item-content')).toHaveStyle({ maxWidth: '900px' });

    fireEvent.click(screen.getByTestId('execute-item-next-width-report'));

    await waitFor(() => {
      expect(screen.getByTestId('chat-item-content')).not.toHaveStyle({ width: '720px' });
    });
    expect((screen.getByTestId('chat-item-content') as HTMLDivElement).style.width).toBe('');
    expect(screen.getByTestId('chat-item-content')).toHaveStyle({ maxWidth: '900px' });
  });
});
