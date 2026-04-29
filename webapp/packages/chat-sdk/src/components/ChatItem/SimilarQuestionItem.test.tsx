import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SimilarQuestionItem from './SimilarQuestionItem';

const mockQuerySimilarQuestions = jest.fn();

jest.mock('../../service', () => ({
  querySimilarQuestions: (...args: any[]) => mockQuerySimilarQuestions(...args),
}));

jest.mock('@ant-design/icons', () => ({
  CheckCircleFilled: () => <span>check</span>,
  DownOutlined: () => <span>down</span>,
  LoadingOutlined: () => <span>loading</span>,
  UpOutlined: () => <span>up</span>,
}));

describe('SimilarQuestionItem', () => {
  beforeEach(() => {
    mockQuerySimilarQuestions.mockReset();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  test('expands by default and fetches similar questions when queryId exists', async () => {
    mockQuerySimilarQuestions.mockResolvedValue({
      data: {
        similarQueries: [{ queryText: '按品牌看近30天销售额', queryId: 2, parseId: 0 }],
      },
    });

    render(
      <SimilarQuestionItem
        queryId={1}
        similarQueries={[]}
        onSelectQuestion={jest.fn()}
      />
    );

    await waitFor(() => {
      expect(mockQuerySimilarQuestions).toHaveBeenCalledWith(1);
    });
    expect(await screen.findByText('1. 按品牌看近30天销售额')).toBeInTheDocument();
  });

  test('keeps collapse toggle available after loading', async () => {
    const onSelectQuestion = jest.fn();
    mockQuerySimilarQuestions.mockResolvedValue({
      data: {
        similarQueries: [{ queryText: '按品牌看近30天销售额', queryId: 2, parseId: 0 }],
      },
    });

    render(
      <SimilarQuestionItem
        queryId={1}
        similarQueries={[]}
        onSelectQuestion={onSelectQuestion}
      />
    );

    expect(await screen.findByText('1. 按品牌看近30天销售额')).toBeInTheDocument();
    await act(async () => {
      userEvent.click(screen.getByText('up'));
    });
    expect(screen.queryByText('1. 按品牌看近30天销售额')).not.toBeInTheDocument();
  });

  test('retries fetching similar questions when async generation finishes after first request', async () => {
    jest.useFakeTimers();
    mockQuerySimilarQuestions
      .mockResolvedValueOnce({
        data: {
          similarQueries: [],
        },
      })
      .mockResolvedValueOnce({
        data: {
          similarQueries: [{ queryText: '按品牌看近30天销售额', queryId: 2, parseId: 0 }],
        },
      });

    render(
      <SimilarQuestionItem
        queryId={1}
        similarQueries={[]}
        onSelectQuestion={jest.fn()}
      />
    );

    await waitFor(() => {
      expect(mockQuerySimilarQuestions).toHaveBeenCalledTimes(1);
    });
    expect(screen.getByText('暂无推荐')).toBeInTheDocument();

    await act(async () => {
      jest.advanceTimersByTime(1000);
    });

    await waitFor(() => {
      expect(mockQuerySimilarQuestions).toHaveBeenCalledTimes(2);
    });
    expect(await screen.findByText('1. 按品牌看近30天销售额')).toBeInTheDocument();
  });

  test('retries after a transient fetch failure and eventually shows suggestions', async () => {
    jest.useFakeTimers();
    mockQuerySimilarQuestions
      .mockRejectedValueOnce(new Error('temporary network error'))
      .mockResolvedValueOnce({
        data: {
          similarQueries: [{ queryText: '按品牌看近30天销售额', queryId: 2, parseId: 0 }],
        },
      });

    render(
      <SimilarQuestionItem
        queryId={1}
        similarQueries={[]}
        onSelectQuestion={jest.fn()}
      />
    );

    await waitFor(() => {
      expect(mockQuerySimilarQuestions).toHaveBeenCalledTimes(1);
    });

    await act(async () => {
      jest.advanceTimersByTime(1000);
    });

    await waitFor(() => {
      expect(mockQuerySimilarQuestions).toHaveBeenCalledTimes(2);
    });
    expect(await screen.findByText('1. 按品牌看近30天销售额')).toBeInTheDocument();
  });

  test('keeps bootstrap suggestions when a later empty prop update arrives', async () => {
    const bootstrapSuggestions = [{ queryText: '按品牌看近30天销售额', queryId: 2, parseId: 0 }];
    const { rerender } = render(
      <SimilarQuestionItem
        queryId={1}
        similarQueries={bootstrapSuggestions}
        onSelectQuestion={jest.fn()}
      />
    );

    expect(screen.getByText('1. 按品牌看近30天销售额')).toBeInTheDocument();

    rerender(
      <SimilarQuestionItem queryId={1} similarQueries={[]} onSelectQuestion={jest.fn()} />
    );

    expect(screen.getByText('1. 按品牌看近30天销售额')).toBeInTheDocument();
    expect(mockQuerySimilarQuestions).not.toHaveBeenCalled();
  });

  test('preserves collapse state and single-click behavior when richer suggestions replace bootstrap', async () => {
    const onSelectQuestion = jest.fn();
    const bootstrapSuggestions = [
      { queryText: '按品牌看近30天销售额', queryId: 2, parseId: 0 },
      { queryText: '按渠道看近30天销售额', queryId: 3, parseId: 0 },
    ];
    const richerSuggestions = [
      { queryText: '按品牌看近30天销售额', queryId: 2, parseId: 0 },
      { queryText: '按渠道看近30天销售额', queryId: 3, parseId: 0 },
      { queryText: '按品类看近30天销售额', queryId: 4, parseId: 0 },
      { queryText: '按品牌看近30天退货率', queryId: 5, parseId: 0 },
    ];
    const { rerender } = render(
      <SimilarQuestionItem
        queryId={1}
        similarQueries={bootstrapSuggestions}
        onSelectQuestion={onSelectQuestion}
      />
    );

    expect(screen.getByText('1. 按品牌看近30天销售额')).toBeInTheDocument();
    await act(async () => {
      userEvent.click(screen.getByText('up'));
    });
    expect(screen.queryByText('1. 按品牌看近30天销售额')).not.toBeInTheDocument();

    rerender(
      <SimilarQuestionItem
        queryId={1}
        similarQueries={richerSuggestions}
        onSelectQuestion={onSelectQuestion}
      />
    );

    expect(screen.queryByText('1. 按品牌看近30天销售额')).not.toBeInTheDocument();

    await act(async () => {
      userEvent.click(screen.getByText('down'));
    });
    expect(await screen.findByText('4. 按品牌看近30天退货率')).toBeInTheDocument();

    await act(async () => {
      userEvent.click(screen.getByText('1. 按品牌看近30天销售额'));
    });
    expect(onSelectQuestion).toHaveBeenCalledTimes(1);
    expect(onSelectQuestion).toHaveBeenCalledWith(richerSuggestions[0]);
  });
});
