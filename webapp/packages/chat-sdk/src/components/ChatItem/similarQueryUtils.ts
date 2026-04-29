import {
  MsgDataType,
  SimilarQuestionType,
  SimilarQueriesSourceType,
} from '../../common/type';

type SimilarQueriesCarrier = {
  similarQueries?: SimilarQuestionType[];
  similarQueriesSource?: SimilarQueriesSourceType;
};

const SOURCE_PRIORITY: Record<SimilarQueriesSourceType, number> = {
  bootstrap: 1,
  authoritative: 2,
};

const MIN_AUTHORITATIVE_QUERY_COUNT = 3;

const getNormalizedQueryText = (query?: SimilarQuestionType) => query?.queryText?.trim().toLowerCase() || '';

export const normalizeSimilarQueries = (
  similarQueries?: SimilarQuestionType[]
): SimilarQuestionType[] => {
  const seen = new Set<string>();
  return (similarQueries || []).reduce<SimilarQuestionType[]>((result, item) => {
    const queryText = item?.queryText?.trim();
    if (!queryText) {
      return result;
    }
    const queryKey = queryText.toLowerCase();
    if (seen.has(queryKey)) {
      return result;
    }
    seen.add(queryKey);
    result.push({
      ...item,
      queryText,
    });
    return result;
  }, []);
};

const isSameSimilarQueries = (left: SimilarQuestionType[], right: SimilarQuestionType[]) =>
  left.length === right.length &&
  left.every((item, index) => getNormalizedQueryText(item) === getNormalizedQueryText(right[index]));

const isSupersetSimilarQueries = (candidate: SimilarQuestionType[], base: SimilarQuestionType[]) => {
  if (candidate.length <= base.length) {
    return false;
  }
  const candidateSet = new Set(candidate.map(getNormalizedQueryText));
  return base.every(item => candidateSet.has(getNormalizedQueryText(item)));
};

const getRichnessScore = (similarQueries: SimilarQuestionType[]) =>
  similarQueries.length * 1000 +
  similarQueries.reduce((total, item) => total + (item.queryText?.length || 0), 0);

const getSourcePriority = (source?: SimilarQueriesSourceType) =>
  SOURCE_PRIORITY[source || 'bootstrap'];

export const mergeSimilarQueries = (
  current?: SimilarQueriesCarrier,
  next?: SimilarQueriesCarrier
): Required<SimilarQueriesCarrier> => {
  const currentSimilarQueries = normalizeSimilarQueries(current?.similarQueries);
  const nextSimilarQueries = normalizeSimilarQueries(next?.similarQueries);
  const currentSource = current?.similarQueriesSource || 'bootstrap';
  const nextSource = next?.similarQueriesSource || 'bootstrap';

  if (nextSimilarQueries.length === 0) {
    return {
      similarQueries: currentSimilarQueries,
      similarQueriesSource: currentSource,
    };
  }
  if (currentSimilarQueries.length === 0) {
    return {
      similarQueries: nextSimilarQueries,
      similarQueriesSource: nextSource,
    };
  }
  if (isSameSimilarQueries(currentSimilarQueries, nextSimilarQueries)) {
    return {
      similarQueries: currentSimilarQueries,
      similarQueriesSource:
        getSourcePriority(nextSource) >= getSourcePriority(currentSource) ? nextSource : currentSource,
    };
  }
  if (
    getSourcePriority(nextSource) > getSourcePriority(currentSource) &&
    (nextSimilarQueries.length >= MIN_AUTHORITATIVE_QUERY_COUNT ||
      nextSimilarQueries.length >= currentSimilarQueries.length)
  ) {
    return {
      similarQueries: nextSimilarQueries,
      similarQueriesSource: nextSource,
    };
  }
  if (
    isSupersetSimilarQueries(nextSimilarQueries, currentSimilarQueries) ||
    getRichnessScore(nextSimilarQueries) > getRichnessScore(currentSimilarQueries)
  ) {
    return {
      similarQueries: nextSimilarQueries,
      similarQueriesSource: nextSource,
    };
  }
  return {
    similarQueries: currentSimilarQueries,
    similarQueriesSource: currentSource,
  };
};

export const mergeMsgDataSimilarQueries = (
  currentData?: Partial<MsgDataType>,
  nextData?: Partial<MsgDataType>,
  nextSource: SimilarQueriesSourceType = 'bootstrap'
) => {
  if (!nextData && !currentData) {
    return undefined;
  }
  const mergedSimilarQueries = mergeSimilarQueries(
    {
      similarQueries: currentData?.similarQueries,
      similarQueriesSource: currentData?.similarQueriesSource,
    },
    {
      similarQueries: nextData?.similarQueries,
      similarQueriesSource: nextData?.similarQueriesSource || nextSource,
    }
  );
  return {
    ...(currentData || {}),
    ...(nextData || {}),
    similarQueries: mergedSimilarQueries.similarQueries,
    similarQueriesSource: mergedSimilarQueries.similarQueriesSource,
  } as MsgDataType;
};
