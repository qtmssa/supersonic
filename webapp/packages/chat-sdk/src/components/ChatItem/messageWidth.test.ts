import { MsgContentTypeEnum } from '../../common/constants';
import {
  mergeMessageWidthReport,
  resolveChartMessageWidthReport,
  resolveSupersetMessageWidth,
  resolveSupersetVizTypeWidth,
} from './messageWidth';

describe('messageWidth', () => {
  test('keeps the widest width within the same result scope', () => {
    const initial = { scopeKey: 'result:chart-1', preferredWidth: 560 };
    const merged = mergeMessageWidthReport(initial, {
      scopeKey: 'result:chart-1',
      preferredWidth: 420,
    });

    expect(merged).toEqual({ scopeKey: 'result:chart-1', preferredWidth: 560 });
  });

  test('clears the cached width when a new scope reports no preferred width', () => {
    const merged = mergeMessageWidthReport(
      { scopeKey: 'result:chart-1', preferredWidth: 860 },
      { scopeKey: 'result:text-2' }
    );

    expect(merged).toEqual({ scopeKey: 'result:text-2', preferredWidth: undefined });
  });

  test('uses the widest switchable content width for chart messages', () => {
    const report = resolveChartMessageWidthReport({
      scopeKey: 'result:metric-pie',
      type: MsgContentTypeEnum.METRIC_PIE,
      queryColumnsLength: 2,
      queryResultsLength: 10,
      metricFieldsLength: 1,
      includeBar: true,
      includeTable: true,
    });

    expect(report).toEqual({ scopeKey: 'result:metric-pie', preferredWidth: 676 });
  });

  test('resolves Superset width from actual renderable candidates only', () => {
    const width = resolveSupersetMessageWidth([
      { key: 'table', vizType: 'table' },
      { key: 'line', vizType: 'echarts_timeseries_line' },
    ]);

    expect(width).toBe(860);
  });

  test('falls back unknown Superset viz types to the generic chart width', () => {
    expect(resolveSupersetVizTypeWidth('custom_visualization_widget')).toBe(820);
  });
});
