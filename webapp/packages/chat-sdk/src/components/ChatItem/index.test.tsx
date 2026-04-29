import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ChatItem from './index';

jest.mock('./ExecuteItem', () => (props: any) => (
  <div
    data-testid="execute-item"
    data-has-data={String(Boolean(props.data))}
    data-query-mode={props.data?.queryMode || ''}
    data-execute-tip={props.executeTip || ''}
  />
));
jest.mock('./ExpandParseTip', () => () => null);
jest.mock('./ParseTip', () => () => null);
jest.mock('./SqlItem', () => () => null);
jest.mock('./SimilarQuestionItem', () => (props: any) => (
  <button
    data-testid="similar-question-item"
    data-default-expanded={String(props.defaultExpanded)}
    data-similar-queries={(props.similarQueries || []).map((item: any) => item.queryText).join('|')}
    onClick={() => props.onSelectQuestion?.({ queryText: '按品牌看近30天销售额' })}
  />
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

const {
  chatExecute: mockChatExecute,
  chatParse: mockChatParse,
  getExecuteSummary: mockGetExecuteSummary,
} = jest.requireMock('../../service');

describe('ChatItem', () => {
  beforeEach(() => {
    mockChatExecute.mockReset();
    mockChatParse.mockReset();
    mockGetExecuteSummary.mockReset();
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
      const executeItem = screen.getByTestId('execute-item');
      expect(executeItem).toHaveAttribute('data-has-data', 'true');
      expect(executeItem).toHaveAttribute('data-query-mode', 'SUPERSET');
      expect(executeItem).toHaveAttribute('data-execute-tip', '');
    });
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

  test('clicking a similar question sends a new question in the same conversation', async () => {
    const onSendMsg = jest.fn();

    render(
      <ChatItem
        msg=""
        conversationId={20}
        onSendMsg={onSendMsg}
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
            similarQueries: [{ queryText: '按品牌看近30天销售额' }],
          } as any
        }
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('similar-question-item')).toBeInTheDocument();
    });

    await userEvent.click(screen.getByTestId('similar-question-item'));

    expect(onSendMsg).toHaveBeenCalledWith('按品牌看近30天销售额');
  });

  test('refreshes bootstrap similar queries with richer summary suggestions', async () => {
    jest.useFakeTimers();
    const onMsgDataLoaded = jest.fn();
    mockChatParse.mockResolvedValue({
      code: 200,
      data: {
        state: 'COMPLETED',
        selectedParses: [
          {
            id: 1,
            queryId: 66,
            queryMode: 'SQL',
            dimensionFilters: [],
            dateInfo: {},
          },
        ],
        candidateParses: [],
        queryId: 66,
        parseTimeCost: {},
      },
    });
    mockChatExecute.mockResolvedValue({
      code: 200,
      msg: 'success',
      data: {
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
        similarQueries: [{ queryText: '按品牌看近30天销售额', queryId: 2, parseId: 0 }],
      },
    });
    mockGetExecuteSummary.mockResolvedValue({
      data: {
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
        similarQueries: [
          { queryText: '按品牌看近30天销售额', queryId: 2, parseId: 0 },
          { queryText: '按渠道看近30天销售额', queryId: 3, parseId: 0 },
          { queryText: '按品类看近30天销售额', queryId: 4, parseId: 0 },
        ],
      },
    });

    render(
      <ChatItem
        msg="近30天销售额"
        conversationId={20}
        onMsgDataLoaded={onMsgDataLoaded}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('similar-question-item')).toHaveAttribute(
        'data-similar-queries',
        '按品牌看近30天销售额'
      );
    });

    await act(async () => {
      jest.advanceTimersByTime(500);
    });

    await waitFor(() => {
      expect(screen.getByTestId('similar-question-item')).toHaveAttribute(
        'data-similar-queries',
        '按品牌看近30天销售额|按渠道看近30天销售额|按品类看近30天销售额'
      );
    });

    expect(onMsgDataLoaded).toHaveBeenLastCalledWith(
      expect.objectContaining({
        similarQueries: [
          expect.objectContaining({ queryText: '按品牌看近30天销售额' }),
          expect.objectContaining({ queryText: '按渠道看近30天销售额' }),
          expect.objectContaining({ queryText: '按品类看近30天销售额' }),
        ],
      }),
      true,
      true
    );
  });

  test('keeps bootstrap similar queries when summary returns an empty list', async () => {
    jest.useFakeTimers();
    const onMsgDataLoaded = jest.fn();
    mockChatParse.mockResolvedValue({
      code: 200,
      data: {
        state: 'COMPLETED',
        selectedParses: [
          {
            id: 1,
            queryId: 66,
            queryMode: 'SQL',
            dimensionFilters: [],
            dateInfo: {},
          },
        ],
        candidateParses: [],
        queryId: 66,
        parseTimeCost: {},
      },
    });
    mockChatExecute.mockResolvedValue({
      code: 200,
      msg: 'success',
      data: {
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
        similarQueries: [{ queryText: '按品牌看近30天销售额', queryId: 2, parseId: 0 }],
      },
    });
    mockGetExecuteSummary.mockResolvedValue({
      data: {
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
      },
    });

    render(
      <ChatItem
        msg="近30天销售额"
        conversationId={20}
        onMsgDataLoaded={onMsgDataLoaded}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('similar-question-item')).toHaveAttribute(
        'data-similar-queries',
        '按品牌看近30天销售额'
      );
    });

    await act(async () => {
      jest.advanceTimersByTime(500);
    });

    await waitFor(() => {
      expect(screen.getByTestId('similar-question-item')).toHaveAttribute(
        'data-similar-queries',
        '按品牌看近30天销售额'
      );
    });

    expect(onMsgDataLoaded).toHaveBeenLastCalledWith(
      expect.objectContaining({
        similarQueries: [expect.objectContaining({ queryText: '按品牌看近30天销售额' })],
      }),
      true,
      true
    );
  });
});
