import { CheckCircleFilled, DownOutlined, LoadingOutlined, UpOutlined } from '@ant-design/icons';
import { PREFIX_CLS } from '../../common/constants';
import { SimilarQuestionType } from '../../common/type';
import { useEffect, useRef, useState } from 'react';
import { querySimilarQuestions } from '../../service';
import { useChatApiPrefix } from '../../runtime/chatRuntime';

type Props = {
  queryId?: number;
  similarQueries?: SimilarQuestionType[];
  defaultExpanded?: boolean;
  onSelectQuestion: (question: SimilarQuestionType) => void;
};

const RETRY_DELAY_MS = 500;
const MAX_EMPTY_RETRY_COUNT = 3;

const SimilarQuestions: React.FC<Props> = ({
  queryId,
  similarQueries,
  defaultExpanded,
  onSelectQuestion,
}) => {
  const apiPrefix = useChatApiPrefix();
  const [similarQuestions, setSimilarQuestions] = useState<SimilarQuestionType[]>(
    similarQueries || []
  );
  const [expanded, setExpanded] = useState(defaultExpanded ?? true);
  const [loading, setLoading] = useState(false);
  const retryTimerRef = useRef<number>();
  const retryCountRef = useRef(0);
  const requestVersionRef = useRef(0);

  const tipPrefixCls = `${PREFIX_CLS}-item`;
  const prefixCls = `${PREFIX_CLS}-similar-questions`;

  const clearRetryTimer = () => {
    if (retryTimerRef.current !== undefined) {
      window.clearTimeout(retryTimerRef.current);
      retryTimerRef.current = undefined;
    }
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
      const res = await querySimilarQuestions(queryId!, apiPrefix);
      if (requestVersion !== requestVersionRef.current) {
        return;
      }
      const nextSimilarQuestions = res.data?.similarQueries || [];
      setSimilarQuestions(nextSimilarQuestions);
      if (nextSimilarQuestions.length > 0 || !queryId || !expanded) {
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
      setSimilarQuestions([]);
      if (!queryId || !expanded) {
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
    setSimilarQuestions(similarQueries || []);
  }, [apiPrefix, queryId, similarQueries]);

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
    setExpanded(!expanded);
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
