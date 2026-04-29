import { MsgContentTypeEnum } from '../../common/constants';

export const HISTORY_MESSAGE_WIDTH_RATIO = 0.9;

const DESKTOP_AVATAR_OFFSET = 56;
const MIN_MESSAGE_WIDTH_LIMIT = 320;
const MAX_RECOMMENDED_MESSAGE_WIDTH = 1080;
const DEFAULT_TABLE_MIN_WIDTH = 420;
const DEFAULT_TABLE_COLUMN_WIDTH = 150;
const DEFAULT_BAR_MIN_WIDTH = 520;
const DEFAULT_PIE_WIDTH = 560;
const DEFAULT_TREND_MIN_WIDTH = 720;
const DEFAULT_METRIC_CARD_WIDTH = 360;
const DEFAULT_SUPERSET_GENERIC_WIDTH = 820;

export type MessageWidthReport = {
  scopeKey: string;
  preferredWidth?: number;
};

export type SupersetWidthCandidate = {
  key: string;
  vizType?: string;
};

type ResolveChartMessageWidthReportParams = {
  scopeKey: string;
  type?: MsgContentTypeEnum;
  queryColumnsLength?: number;
  queryResultsLength?: number;
  metricFieldsLength?: number;
  includeBar?: boolean;
  includeTable?: boolean;
};

type ResolveMessageWidthScopeKeyParams = {
  queryId?: string | number;
  queryMode?: string;
  queryColumns?: Array<{
    bizName?: string;
    nameEn?: string;
    showType?: string;
    type?: string;
  }>;
  queryResultsLength?: number;
  metricFieldsLength?: number;
  textResult?: string;
  textSummary?: string;
  responseSignature?: string;
};

function isFinitePositive(value?: number): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value > 0;
}

function clampRecommendedWidth(value: number) {
  return Math.min(Math.round(value), MAX_RECOMMENDED_MESSAGE_WIDTH);
}

function normalizeVizType(value?: string) {
  return value?.trim().toLowerCase() || '';
}

function normalizePreferredWidth(value?: number) {
  return isFinitePositive(value) ? Math.round(value as number) : undefined;
}

function uniqueContentTypes(types: Array<MsgContentTypeEnum | undefined>) {
  return Array.from(new Set(types.filter(Boolean))) as MsgContentTypeEnum[];
}

export function resolveMessageWidthLimit(
  containerWidth?: number,
  avatarOffset = DESKTOP_AVATAR_OFFSET
) {
  if (!isFinitePositive(containerWidth)) {
    return undefined;
  }
  const availableWidth = Math.max(
    Math.round((containerWidth as number) - avatarOffset),
    MIN_MESSAGE_WIDTH_LIMIT
  );
  return Math.round(availableWidth * HISTORY_MESSAGE_WIDTH_RATIO);
}

function isScrollableOverflow(value?: string) {
  return Boolean(value && /(auto|scroll)/.test(value));
}

export function resolveMessageWidthHostElement(element?: HTMLElement | null) {
  if (typeof window === 'undefined') {
    return undefined;
  }
  let current = element?.parentElement;
  while (current) {
    const style = window.getComputedStyle(current);
    if (
      isScrollableOverflow(style.overflowY) ||
      isScrollableOverflow(style.overflowX) ||
      isScrollableOverflow(style.overflow)
    ) {
      return current;
    }
    current = current.parentElement;
  }
  return undefined;
}

export function clampMessageBubbleWidth(preferredWidth?: number, widthLimit?: number) {
  if (!isFinitePositive(preferredWidth)) {
    return undefined;
  }
  if (!isFinitePositive(widthLimit)) {
    return Math.round(preferredWidth as number);
  }
  return Math.min(Math.round(preferredWidth as number), Math.round(widthLimit as number));
}

export function mergeMessageWidthReport(
  currentReport?: MessageWidthReport,
  nextReport?: MessageWidthReport
) {
  if (!nextReport?.scopeKey) {
    return currentReport;
  }

  const normalizedNextWidth = normalizePreferredWidth(nextReport.preferredWidth);
  if (currentReport?.scopeKey !== nextReport.scopeKey) {
    return {
      scopeKey: nextReport.scopeKey,
      preferredWidth: normalizedNextWidth,
    };
  }

  if (!isFinitePositive(normalizedNextWidth)) {
    return currentReport;
  }

  const currentWidth = normalizePreferredWidth(currentReport?.preferredWidth);
  return {
    scopeKey: nextReport.scopeKey,
    preferredWidth:
      currentWidth === undefined ? normalizedNextWidth : Math.max(currentWidth, normalizedNextWidth),
  };
}

export function resolveMessageWidthScopeKey({
  queryId,
  queryMode,
  queryColumns = [],
  queryResultsLength = 0,
  metricFieldsLength = 0,
  textResult,
  textSummary,
  responseSignature,
}: ResolveMessageWidthScopeKeyParams) {
  const columnSignature = queryColumns
    .map(column => {
      return [
        column.bizName || column.nameEn || '',
        column.showType || column.type || '',
      ].join(':');
    })
    .join(',');

  return [
    `query:${queryId ?? ''}`,
    `mode:${queryMode ?? ''}`,
    `cols:${queryColumns.length}`,
    `colsig:${columnSignature}`,
    `rows:${queryResultsLength}`,
    `metrics:${metricFieldsLength}`,
    `text:${textResult?.length || 0}`,
    `summary:${textSummary?.length || 0}`,
    responseSignature ? `response:${responseSignature}` : '',
  ]
    .filter(Boolean)
    .join('|');
}

export function resolveChartMessageWidth({
  type,
  queryColumnsLength = 0,
  queryResultsLength = 0,
  metricFieldsLength = 0,
}: {
  type?: MsgContentTypeEnum;
  queryColumnsLength?: number;
  queryResultsLength?: number;
  metricFieldsLength?: number;
}) {
  switch (type) {
    case MsgContentTypeEnum.METRIC_CARD:
      return DEFAULT_METRIC_CARD_WIDTH;
    case MsgContentTypeEnum.TABLE:
      return clampRecommendedWidth(
        Math.max(DEFAULT_TABLE_MIN_WIDTH, queryColumnsLength * DEFAULT_TABLE_COLUMN_WIDTH)
      );
    case MsgContentTypeEnum.METRIC_BAR:
      return clampRecommendedWidth(
        Math.max(
          DEFAULT_BAR_MIN_WIDTH,
          DEFAULT_BAR_MIN_WIDTH + Math.max(queryResultsLength - 4, 0) * 26
        )
      );
    case MsgContentTypeEnum.METRIC_PIE:
      return DEFAULT_PIE_WIDTH;
    case MsgContentTypeEnum.METRIC_TREND:
      return clampRecommendedWidth(
        Math.max(
          DEFAULT_TREND_MIN_WIDTH,
          DEFAULT_TREND_MIN_WIDTH +
            Math.max(queryResultsLength - 4, 0) * 24 +
            Math.max(metricFieldsLength - 1, 0) * 80
        )
      );
    default:
      return undefined;
  }
}

export function resolveChartMessageWidthReport({
  scopeKey,
  type,
  queryColumnsLength = 0,
  queryResultsLength = 0,
  metricFieldsLength = 0,
  includeBar = false,
  includeTable = false,
}: ResolveChartMessageWidthReportParams): MessageWidthReport {
  const candidateTypes = uniqueContentTypes([
    type,
    includeBar ? MsgContentTypeEnum.METRIC_BAR : undefined,
    includeTable ? MsgContentTypeEnum.TABLE : undefined,
  ]);

  const widths = candidateTypes
    .map(candidateType =>
      resolveChartMessageWidth({
        type: candidateType,
        queryColumnsLength,
        queryResultsLength,
        metricFieldsLength,
      })
    )
    .filter(isFinitePositive) as number[];

  return {
    scopeKey,
    preferredWidth: widths.length > 0 ? Math.max(...widths) : undefined,
  };
}

export function resolveSupersetVizTypeWidth(vizType?: string) {
  const normalized = normalizeVizType(vizType);
  if (!normalized) {
    return DEFAULT_SUPERSET_GENERIC_WIDTH;
  }
  if (normalized.includes('table')) {
    return 760;
  }
  if (
    normalized.includes('line') ||
    normalized.includes('timeseries') ||
    normalized.includes('mixed') ||
    normalized.includes('area')
  ) {
    return 860;
  }
  if (
    normalized.includes('pie') ||
    normalized.includes('radar') ||
    normalized.includes('gauge') ||
    normalized.includes('funnel') ||
    normalized.includes('rose') ||
    normalized.includes('big_number')
  ) {
    return DEFAULT_PIE_WIDTH;
  }
  if (
    normalized.includes('bar') ||
    normalized.includes('histogram') ||
    normalized.includes('scatter') ||
    normalized.includes('map') ||
    normalized.includes('heatmap') ||
    normalized.includes('graph') ||
    normalized.includes('tree') ||
    normalized.includes('treemap') ||
    normalized.includes('sankey') ||
    normalized.includes('sunburst') ||
    normalized.includes('gantt')
  ) {
    return DEFAULT_SUPERSET_GENERIC_WIDTH;
  }
  return DEFAULT_SUPERSET_GENERIC_WIDTH;
}

export function resolveSupersetMessageWidth(candidates: SupersetWidthCandidate[] = []) {
  if (candidates.length === 0) {
    return DEFAULT_SUPERSET_GENERIC_WIDTH;
  }

  return candidates.reduce((maxWidth, candidate) => {
    return Math.max(maxWidth, resolveSupersetVizTypeWidth(candidate.vizType));
  }, DEFAULT_SUPERSET_GENERIC_WIDTH);
}
