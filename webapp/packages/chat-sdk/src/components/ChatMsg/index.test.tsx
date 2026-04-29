import { render, screen } from '@testing-library/react';
import ChatMsg from './index';
import { ChartItemContext } from '../ChatItem';

jest.mock('../ChatItem', () => {
  const React = require('react');
  return {
    ChartItemContext: React.createContext({
      register: jest.fn(),
      call: jest.fn(),
      reportMessageWidth: jest.fn(),
    }),
  };
});

jest.mock('./Table', () => () => <div title="table-view" />);
jest.mock('./Pie', () => () => <div title="pie-view" />);
jest.mock('./Bar', () => () => <div title="bar-view" />);
jest.mock('./MetricCard', () => () => <div title="metric-card-view" />);
jest.mock('./MetricTrend', () => () => <div title="trend-view" />);
jest.mock('./MarkDown', () => () => <div title="markdown-view" />);
jest.mock('./Text', () => () => <div title="text-view" />);
jest.mock('../MetricOptions', () => () => null);
jest.mock('../DrillDownDimensions', () => () => null);
jest.mock('../../service', () => ({
  queryData: jest.fn(),
}));
jest.mock('../../utils/utils', () => ({
  isMobile: false,
}));

describe('ChatMsg', () => {
  test('does not force detail queries with chartable results into table mode', () => {
    const data = {
      queryMode: 'SUPERSET',
      queryColumns: [
        {
          name: '品牌名称',
          nameEn: 'brand_name',
          bizName: 'brand_name',
          showType: 'CATEGORY',
          type: 'STRING',
        },
        {
          name: '收入',
          nameEn: 'income',
          bizName: 'income',
          showType: 'NUMBER',
          type: 'NUMBER',
        },
      ],
      queryResults: [
        { brand_name: 'Office', income: 30 },
        { brand_name: 'Windows', income: 70 },
      ],
      chatContext: {
        queryType: 'DETAIL',
        queryMode: 'DETAIL_DIMENSION',
        metrics: [{ bizName: 'income', name: '收入' }],
        dimensions: [{ bizName: 'brand_name', name: '品牌名称' }],
      },
    } as any;

    render(
        <ChatMsg
          queryId={1}
          question="各品牌收入比例"
          data={data}
          chartIndex={0}
          widthScopeKey="result:pie-1"
          onMsgContentTypeChange={() => {}}
        />
    );

    expect(screen.getByTitle('pie-view')).toBeInTheDocument();
    expect(screen.queryByTitle('table-view')).toBeNull();
  });

  test('reports preferred width for trend chart messages', async () => {
    const reportMessageWidth = jest.fn();
    const data = {
      queryMode: 'METRIC',
      queryColumns: [
        {
          name: '日期',
          nameEn: 'ds',
          bizName: 'ds',
          showType: 'DATE',
          type: 'DATE',
        },
        {
          name: '收入',
          nameEn: 'income',
          bizName: 'income',
          showType: 'NUMBER',
          type: 'NUMBER',
        },
      ],
      queryResults: [
        { ds: '2024-01-01', income: 30 },
        { ds: '2024-01-02', income: 70 },
        { ds: '2024-01-03', income: 55 },
      ],
      chatContext: {
        queryMode: 'METRIC',
        metrics: [{ bizName: 'income', name: '收入' }],
        dimensions: [],
      },
    } as any;

    render(
      <ChartItemContext.Provider
        value={{ register: jest.fn(), call: jest.fn(), reportMessageWidth }}
      >
        <ChatMsg
          queryId={1}
          question="近三日收入趋势"
          data={data}
          chartIndex={0}
          widthScopeKey="result:trend-1"
          onMsgContentTypeChange={() => {}}
        />
      </ChartItemContext.Provider>
    );

    expect(reportMessageWidth).toHaveBeenCalledWith({
      scopeKey: 'result:trend-1',
      preferredWidth: 720,
    });
  });
});
