import { CheckCircleFilled, DownOutlined, LoadingOutlined, UpOutlined } from '@ant-design/icons';
import { PREFIX_CLS } from '../../common/constants';
import { SimilarQueriesSourceType, SimilarQuestionType } from '../../common/type';
import { useEffect, useRef, useState } from 'react';
import { querySimilarQuestions } from '../../service';
import { mergeSimilarQueries, normalizeSimilarQueries } from './similarQueryUtils';

type Props = {
  queryId?: number;
  similarQueries?: SimilarQuestionType[];
  similarQueriesSource?: SimilarQueriesSourceType;
  defaultExpanded?: boolean;
  onSelectQuestion: (question: SimilarQuestionType) => void;
};

const RETRY_DELAY_MS = 500;
const MAX_EMPTY_RETRY_COUNT = 3;

const SimilarQuestions: React.FC<Props> = ({
  queryId,
  similarQueries,
  similarQueriesSource,
  defaultExpanded,
  onSelectQuestion,
}) => {
  const [similarQuestions, setSimilarQuestions] = useState<SimilarQuestionType[]>(
    normalizeSimilarQueries(similarQueries)
  );
  const [expanded, setExpanded] = useState(defaultExpanded ?? true);
  const [loading, setLoading] = useState(false);
  const retryTimerRef = useRef<number>();
  const retryCountRef = useRef(0);
  const requestVersionRef = useRef(0);
  const previousQueryIdRef = useRef<number>();
  const similarQuestionsRef = useRef(
    mergeSimilarQueries(undefined, {
      similarQueries,
      similarQueriesSource: similarQueriesSource || 'bootstrap',
    })
  );

  const tipPrefixCls = `${PREFIX_CLS}-item`;
  const prefixCls = `${PREFIX_CLS}-similar-questions`;

  const clearRetryTimer = () => {
    if (retryTimerRef.current !== undefined) {
      window.clearTimeout(retryTimerRef.current);
      retryTimerRef.current = undefined;
    }
  };

  const updateSimilarQuestions = (
    nextSimilarQueries?: SimilarQuestionType[],
    nextSource: SimilarQueriesSourceType = 'bootstrap'
  ) => {
    const nextSimilarQuestionsState = mergeSimilarQueries(similarQuestionsRef.current, {
      similarQueries: nextSimilarQueries,
      similarQueriesSource: nextSource,
    });
    similarQuestionsRef.current = nextSimilarQuestionsState;
    setSimilarQuestions(nextSimilarQuestionsState.similarQueries);
    return nextSimilarQuestionsState.similarQueries;
  };

  const scheduleRetry = (requestVersion: number) => {
    if (retryCountRef.current < MAX_EMPTY_RETRY_COUNT) {
      retryCountRef.current += 1;
      clearRetryTimer();
      retryTimerRef.current = window.setTimeout(() => {
        initData(requestVersion);
      }, RETRY_DELAY_MS);
    }
  };

  const initData = async (requestVersion = requestVersionRef.current) => {
    setLoading(true);
    try {
      const res = await querySimilarQuestions(queryId!);
      if (requestVersion !== requestVersionRef.current) {
        return;
      }
      const nextSimilarQuestions = res.data?.similarQueries || [];
      const mergedSimilarQuestions = updateSimilarQuestions(nextSimilarQuestions, 'authoritative');
      if (mergedSimilarQuestions.length > 0 || !queryId || !expanded) {
        retryCountRef.current = 0;
        clearRetryTimer();
        return;
      }
      // Similar questions are written asynchronously on the backend, so retry briefly
      // before showing an empty state permanently.
      scheduleRetry(requestVersion);
    } catch {
      if (requestVersion !== requestVersionRef.current) {
        return;
      }
      if (similarQuestionsRef.current.similarQueries.length > 0 || !queryId || !expanded) {
        retryCountRef.current = 0;
        clearRetryTimer();
        return;
      }
      scheduleRetry(requestVersion);
    } finally {
      if (requestVersion === requestVersionRef.current) {
        setLoading(false);
      }
    }
  };

  useEffect(() => {
    requestVersionRef.current += 1;
    retryCountRef.current = 0;
    clearRetryTimer();
    if (previousQueryIdRef.current !== queryId) {
      similarQuestionsRef.current = {
        similarQueries: normalizeSimilarQueries(similarQueries),
        similarQueriesSource: similarQueriesSource || 'bootstrap',
      };
      previousQueryIdRef.current = queryId;
    } else {
      similarQuestionsRef.current = mergeSimilarQueries(similarQuestionsRef.current, {
        similarQueries,
        similarQueriesSource: similarQueriesSource || 'bootstrap',
      });
    }
    setSimilarQuestions(similarQuestionsRef.current.similarQueries);
  }, [queryId, similarQueries, similarQueriesSource]);

  useEffect(() => {
    if (expanded && similarQuestions?.length === 0 && queryId) {
      initData(requestVersionRef.current);
    }
    if (!expanded) {
      clearRetryTimer();
    }
    return () => {
      clearRetryTimer();
    };
  }, [expanded, queryId, similarQuestions?.length]);

  const onToggleExpanded = () => {
    setExpanded(currentExpanded => !currentExpanded);
  };

  return (
    <div className={`${tipPrefixCls}-parse-tip`}>
      <div className={`${tipPrefixCls}-title-bar`}>
        <CheckCircleFilled className={`${tipPrefixCls}-step-icon`} />
        <div className={`${tipPrefixCls}-step-title`}>
          推荐相似问题
          <span className={`${prefixCls}-toggle-expand-btn`} onClick={onToggleExpanded}>
            {loading ? <LoadingOutlined /> : expanded ? <UpOutlined /> : <DownOutlined />}
          </span>
        </div>
      </div>
      <div className={prefixCls}>
        {expanded && (
          <div className={`${prefixCls}-content`}>
            {Array.isArray(similarQuestions) && similarQuestions.length > 0 ? (
              similarQuestions?.slice(0, 5).map((question, index) => {
                return (
                  <div
                    className={`${prefixCls}-question`}
                    key={question.queryText}
                    onClick={() => {
                      onSelectQuestion(question);
                    }}
                  >
                    {index + 1}. {question.queryText}
                  </div>
                );
              })
            ) : (
              <>暂无推荐</>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default SimilarQuestions;
