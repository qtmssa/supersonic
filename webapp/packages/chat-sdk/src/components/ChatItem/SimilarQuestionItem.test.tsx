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
    await userEvent.click(screen.getByText('up'));
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
});
