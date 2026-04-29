import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { message } from 'antd';
import SupersetChart from './index';

jest.mock('@superset-ui/embedded-sdk', () => ({
  embedDashboard: jest.fn(),
}));

jest.mock('../../../service', () => ({
  fetchSupersetGuestToken: jest.fn(),
  fetchSupersetManualDashboards: jest.fn(),
  createSupersetDashboard: jest.fn(),
  pushSupersetChartToDashboard: jest.fn(),
  queryData: jest.fn(),
}));

jest.spyOn(message, 'success').mockImplementation(() => undefined as any);
jest.spyOn(message, 'error').mockImplementation(() => undefined as any);

const buildChatContext = (overrides: any = {}) => ({
  id: 701,
  dateInfo: {
    dateMode: 'RECENT',
    unit: 7,
  },
  dimensions: [
    {
      id: 1,
      itemId: 1,
      model: 9,
      name: '日期',
      bizName: 'ds',
      showType: 'DATE',
      type: 'DATE',
      value: 'ds',
      status: 1,
    },
  ],
  metrics: [
    {
      id: 11,
      itemId: 11,
      model: 9,
      name: '访问次数',
      bizName: 'pv',
      type: 'NUMBER',
      value: 'pv',
      status: 1,
    },
  ],
  ...overrides,
});

const buildData = (response: any, overrides: any = {}) =>
  ({
    id: 801,
    queryId: 901,
    response,
    queryMode: 'SUPERSET',
    queryState: 'SUCCESS',
    queryColumns: [],
    queryResults: [],
    chatContext: buildChatContext(),
    recommendedDimensions: [],
    ...overrides,
  } as any);

const buildRecommendedDimension = (overrides: any = {}) => ({
  dataSetId: 501,
  dataSetName: '访问模型',
  model: 9,
  id: 2,
  name: '省份',
  bizName: 'province',
  type: 'DIMENSION',
  alias: ['地区'],
  useCnt: 8,
  order: 1,
  isTag: 0,
  description: '省份维度',
  extInfo: {
    DIMENSION_TYPE: 'NORMAL',
  },
  ...overrides,
});

const ensureEmbedDashboardMock = () => {
  const { embedDashboard } = require('@superset-ui/embedded-sdk');
  embedDashboard.mockReset();
  embedDashboard.mockResolvedValue({
    unmount: jest.fn(),
    setThemeMode: jest.fn(),
    setThemeConfig: jest.fn(),
  });
  return embedDashboard;
};

const ensureServiceMocks = () => {
  const {
    fetchSupersetGuestToken,
    fetchSupersetManualDashboards,
    createSupersetDashboard,
    pushSupersetChartToDashboard,
    queryData,
  } = require('../../../service');
  fetchSupersetGuestToken.mockReset();
  fetchSupersetManualDashboards.mockReset();
  createSupersetDashboard.mockReset();
  pushSupersetChartToDashboard.mockReset();
  queryData.mockReset();
  fetchSupersetGuestToken.mockResolvedValue({ data: { token: 'token-default' } });
  fetchSupersetManualDashboards.mockResolvedValue({
    code: 200,
    data: {
      pluginId: 1,
      supersetDomain: 'https://superset.example.com',
      dashboards: [
        {
          id: 9001,
          title: '经营分析总览',
          embeddedId: 'manual-dashboard-9001',
          supersetDomain: 'https://superset.example.com',
        },
      ],
    },
  });
  createSupersetDashboard.mockResolvedValue({
    code: 200,
    data: {
      id: 9101,
      title: '新建看板',
      embeddedId: 'manual-dashboard-9101',
      supersetDomain: 'https://superset.example.com',
    },
  });
  pushSupersetChartToDashboard.mockResolvedValue({ code: 200, data: true });
  queryData.mockResolvedValue({ code: 200, data: null });
};

describe('SupersetChart', () => {
  beforeEach(() => {
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.className = '';
    document.documentElement.style.cssText = '';
    document.body.className = '';
    document.body.style.cssText = '';
    ensureEmbedDashboardMock();
    ensureServiceMocks();
  });

  test('does not use embedded url fallback', async () => {
    const data = buildData({
      webPage: { url: 'https://superset.example.com/superset/embedded/uuid-123/', params: [] },
      pluginId: 1,
      chartUuid: 'uuid-123',
      guestToken: 'token-123',
    });
    const { embedDashboard } = require('@superset-ui/embedded-sdk');
    render(<SupersetChart id={1} data={data} />);
    await waitFor(() => {
      expect(screen.getByText('Superset 嵌入信息缺失，无法渲染看板。')).toBeTruthy();
    });
    expect(embedDashboard).not.toHaveBeenCalled();
  });

  test('uses embedded sdk when response provides embed info', async () => {
    const data = buildData({
      webPage: { url: '', params: [] },
      pluginId: 1,
      embeddedId: 'uuid-456',
      supersetDomain: 'https://superset.example.com',
    });
    const { embedDashboard } = require('@superset-ui/embedded-sdk');
    render(<SupersetChart id={1} data={data} />);
    await waitFor(() => {
      expect(embedDashboard).toHaveBeenCalled();
    });
    const args = embedDashboard.mock.calls[0][0];
    expect(args.id).toBe('uuid-456');
    expect(args.supersetDomain).toBe('https://superset.example.com');
    expect(args.iframeTitle).toBe('supersetIframe');
    expect(args.dashboardUiConfig.hideChartControls).toBe(false);
    await expect(args.fetchGuestToken()).resolves.toBe('token-default');
  });

  test('uses first candidate embed as default view and renders switcher', async () => {
    const { embedDashboard } = require('@superset-ui/embedded-sdk');
    embedDashboard.mockClear();
    const data = buildData({
      webPage: { url: '', params: [] },
      pluginId: 1,
      dashboardId: 88,
      dashboardTitle: '分析看板',
      embeddedId: 'embed-line',
      supersetDomain: 'https://superset.example.com',
      vizTypeCandidates: [
        {
          vizType: 'echarts_timeseries_line',
          vizName: 'Line Chart',
          embeddedId: 'embed-line',
          supersetDomain: 'https://superset.example.com',
          chartId: 11,
        },
        {
          vizType: 'echarts_timeseries_bar',
          vizName: 'Bar Chart',
          embeddedId: 'embed-bar',
          supersetDomain: 'https://superset.example.com',
          chartId: 22,
        },
        {
          vizType: 'table',
          vizName: 'Table',
          embeddedId: 'embed-table',
          supersetDomain: 'https://superset.example.com',
          chartId: 33,
        },
      ],
    });
    render(<SupersetChart id={1} data={data} />);
    await waitFor(() => {
      expect(embedDashboard).toHaveBeenCalled();
    });
    expect(embedDashboard).toHaveBeenCalledTimes(1);
    const args = embedDashboard.mock.calls[0][0];
    expect(args.id).toBe('embed-line');
    await expect(args.fetchGuestToken()).resolves.toBe('token-default');
    expect(screen.queryByText('分析看板')).toBeNull();
    expect(screen.getByText('折线图')).toBeTruthy();
    expect(screen.getByText('柱状图')).toBeTruthy();
    expect(screen.getByText('数据表')).toBeTruthy();
    expect(screen.queryByText('Line Chart')).toBeNull();
    expect(screen.getByRole('button', { name: '折线图' })).toBeTruthy();
    expect(screen.getByText('推送到看板')).toBeTruthy();
  });

  test('prefers final dashboard embed when candidates only describe child charts', async () => {
    const { embedDashboard } = require('@superset-ui/embedded-sdk');
    embedDashboard.mockClear();
    const data = buildData({
      webPage: { url: '', params: [] },
      pluginId: 1,
      dashboardId: 88,
      dashboardTitle: '访问趋势分析',
      embeddedId: 'final-dashboard-embed',
      supersetDomain: 'https://superset.example.com',
      vizTypeCandidates: [
        {
          vizType: 'echarts_timeseries_line',
          vizName: '趋势折线图',
          chartId: 11,
          chartUuid: 'chart-uuid-11',
        },
        {
          vizType: 'pie',
          vizName: '占比饼图',
          chartId: 22,
          chartUuid: 'chart-uuid-22',
        },
      ],
    });
    render(<SupersetChart id={1} data={data} />);
    await waitFor(() => {
      expect(embedDashboard).toHaveBeenCalled();
    });
    expect(embedDashboard).toHaveBeenCalledTimes(1);
    expect(embedDashboard.mock.calls[0][0].id).toBe('final-dashboard-embed');
    expect(screen.queryByText('访问趋势分析')).toBeNull();
    expect(screen.getByText('折线图')).toBeTruthy();
    expect(screen.getByText('饼图')).toBeTruthy();
    expect(screen.queryByText('推送到看板')).toBeNull();
  });

  test('switches embedded dashboard when user selects another viz type', async () => {
    const { embedDashboard } = require('@superset-ui/embedded-sdk');
    embedDashboard.mockClear();
    const data = buildData({
      webPage: { url: '', params: [] },
      pluginId: 1,
      dashboardId: 88,
      dashboardTitle: '访问趋势分析',
      embeddedId: 'embed-line',
      supersetDomain: 'https://superset.example.com',
      vizTypeCandidates: [
        {
          vizType: 'echarts_timeseries_line',
          vizName: 'Line Chart',
          embeddedId: 'embed-line',
          supersetDomain: 'https://superset.example.com',
          chartId: 11,
        },
        {
          vizType: 'echarts_timeseries_bar',
          vizName: 'Bar Chart',
          embeddedId: 'embed-bar',
          supersetDomain: 'https://superset.example.com',
          chartId: 22,
        },
        {
          vizType: 'table',
          vizName: 'Table',
          embeddedId: 'embed-table',
          supersetDomain: 'https://superset.example.com',
          chartId: 33,
        },
      ],
    });
    render(<SupersetChart id={1} data={data} />);
    await waitFor(() => {
      expect(embedDashboard).toHaveBeenCalledTimes(1);
    });
    fireEvent.click(screen.getByRole('button', { name: '柱状图' }));
    await waitFor(() => {
      expect(embedDashboard).toHaveBeenCalledTimes(2);
    });
    expect(embedDashboard.mock.calls[1][0].id).toBe('embed-bar');

    fireEvent.click(screen.getByRole('button', { name: '数据表' }));
    await waitFor(() => {
      expect(embedDashboard).toHaveBeenCalledTimes(3);
    });
    expect(embedDashboard.mock.calls[2][0].id).toBe('embed-table');
  });

  test('fetches guest token from response wrapper', async () => {
    const { embedDashboard } = require('@superset-ui/embedded-sdk');
    const { fetchSupersetGuestToken } = require('../../../service');
    fetchSupersetGuestToken.mockResolvedValue({ data: { token: 'token-789' } });
    const data = buildData({
      webPage: { url: '', params: [] },
      pluginId: 1,
      embeddedId: 'uuid-789',
      supersetDomain: 'https://superset.example.com',
    });
    render(<SupersetChart id={1} data={data} />);
    await waitFor(() => {
      expect(embedDashboard).toHaveBeenCalled();
    });
    const args = embedDashboard.mock.calls[0][0];
    await expect(args.fetchGuestToken()).resolves.toBe('token-789');
  });

  test('fetches guest token from direct token response', async () => {
    const { embedDashboard } = require('@superset-ui/embedded-sdk');
    const { fetchSupersetGuestToken } = require('../../../service');
    fetchSupersetGuestToken.mockResolvedValue({ token: 'token-555' });
    const data = buildData({
      webPage: { url: '', params: [] },
      pluginId: 1,
      embeddedId: 'uuid-555',
      supersetDomain: 'https://superset.example.com',
    });
    render(<SupersetChart id={1} data={data} />);
    await waitFor(() => {
      expect(embedDashboard).toHaveBeenCalled();
    });
    const args = embedDashboard.mock.calls[0][0];
    await expect(args.fetchGuestToken()).resolves.toBe('token-555');
  });

  test('always fetches guest token from api', async () => {
    const { embedDashboard } = require('@superset-ui/embedded-sdk');
    const { fetchSupersetGuestToken } = require('../../../service');
    fetchSupersetGuestToken.mockResolvedValue({ data: { token: 'token-new' } });
    const data = buildData({
      webPage: { url: '', params: [] },
      pluginId: 1,
      embeddedId: 'uuid-expired',
      supersetDomain: 'https://superset.example.com',
    });
    render(<SupersetChart id={1} data={data} />);
    await waitFor(() => {
      expect(embedDashboard).toHaveBeenCalled();
    });
    const args = embedDashboard.mock.calls[0][0];
    await expect(args.fetchGuestToken()).resolves.toBe('token-new');
  });

  test('syncs host theme mode and color tokens into embedded dashboard', async () => {
    const { embedDashboard } = require('@superset-ui/embedded-sdk');
    document.documentElement.style.setProperty('--tme-primary-color', '#1672fa');
    document.documentElement.style.setProperty('--component-background', '#ffffff');
    document.documentElement.style.setProperty('--body-background', '#f7fafa');
    document.documentElement.style.setProperty('--text-color', '#181a1a');
    document.documentElement.style.setProperty('--text-color-secondary', '#3d4242');
    document.documentElement.style.setProperty('--border-color-base', '#e1e6e6');

    const data = buildData({
      webPage: { url: '', params: [] },
      pluginId: 1,
      embeddedId: 'uuid-theme-light',
      supersetDomain: 'https://superset.example.com',
    });
    render(<SupersetChart id={1} data={data} />);
    await waitFor(() => {
      expect(embedDashboard).toHaveBeenCalled();
    });
    const instance = await embedDashboard.mock.results[0].value;
    await waitFor(() => {
      expect(instance.setThemeMode).toHaveBeenCalledWith('default');
    });
    expect(instance.setThemeConfig).toHaveBeenCalledWith({
      token: {
        colorPrimary: '#1672fa',
        colorBgBase: '#ffffff',
        colorBgLayout: '#f7fafa',
        colorBgContainer: '#ffffff',
        colorTextBase: '#181a1a',
        colorText: '#181a1a',
        colorTextSecondary: '#3d4242',
        colorBorder: '#e1e6e6',
      },
    });
  });

  test('re-syncs embedded dashboard theme when host theme changes', async () => {
    const { embedDashboard } = require('@superset-ui/embedded-sdk');
    document.documentElement.style.setProperty('--component-background', '#ffffff');
    document.documentElement.style.setProperty('--body-background', '#f7fafa');

    const data = buildData({
      webPage: { url: '', params: [] },
      pluginId: 1,
      embeddedId: 'uuid-theme-switch',
      supersetDomain: 'https://superset.example.com',
    });
    render(<SupersetChart id={1} data={data} />);
    await waitFor(() => {
      expect(embedDashboard).toHaveBeenCalled();
    });
    const instance = await embedDashboard.mock.results[0].value;
    await waitFor(() => {
      expect(instance.setThemeMode).toHaveBeenCalledWith('default');
    });

    document.documentElement.setAttribute('data-theme', 'dark');
    document.documentElement.style.setProperty('--component-background', '#101014');
    document.documentElement.style.setProperty('--body-background', '#0b0c0f');

    await waitFor(() => {
      expect(instance.setThemeMode).toHaveBeenLastCalledWith('dark');
    });
    expect(instance.setThemeConfig).toHaveBeenLastCalledWith(
      expect.objectContaining({
        token: expect.objectContaining({
          colorBgBase: '#101014',
          colorBgLayout: '#0b0c0f',
        }),
      })
    );
  });

  test('shows error when embed info missing', async () => {
    const data = buildData({
      webPage: { url: '', params: [] },
      guestToken: 'token-123',
    });
    render(<SupersetChart id={1} data={data} />);
    await waitFor(() => {
      expect(screen.getByText('Superset 嵌入信息缺失，无法渲染看板。')).toBeTruthy();
    });
  });

  test('does not fetch dashboard list or show push action for dashboard-first response', async () => {
    const data = buildData({
      webPage: { url: '', params: [] },
      pluginId: 1,
      dashboardId: 100,
      embeddedId: 'dashboard-embed-100',
      supersetDomain: 'https://superset.example.com',
    });
    render(<SupersetChart id={1} data={data} />);
    await waitFor(() => {
      expect(screen.queryByText('推送到看板')).toBeNull();
    });
  });

  test('pushes the currently selected chart into existing manual dashboard', async () => {
    const { embedDashboard } = require('@superset-ui/embedded-sdk');
    const { fetchSupersetManualDashboards, pushSupersetChartToDashboard } = require('../../../service');
    embedDashboard.mockClear();
    const data = buildData({
      webPage: { url: '', params: [] },
      pluginId: 1,
      dashboardId: 88,
      dashboardTitle: '访问趋势分析',
      embeddedId: 'embed-line',
      supersetDomain: 'https://superset.example.com',
      vizTypeCandidates: [
        {
          vizType: 'echarts_timeseries_line',
          vizName: 'Line Chart',
          embeddedId: 'embed-line',
          supersetDomain: 'https://superset.example.com',
          chartId: 11,
        },
        {
          vizType: 'echarts_timeseries_bar',
          vizName: 'Bar Chart',
          embeddedId: 'embed-bar',
          supersetDomain: 'https://superset.example.com',
          chartId: 22,
        },
      ],
    });
    render(<SupersetChart id={1} data={data} />);
    await waitFor(() => {
      expect(embedDashboard).toHaveBeenCalledTimes(1);
    });
    fireEvent.click(screen.getByRole('button', { name: '柱状图' }));
    await waitFor(() => {
      expect(embedDashboard).toHaveBeenCalledTimes(2);
    });
    fireEvent.click(screen.getByRole('button', { name: '推送到看板' }));
    await waitFor(() => {
      expect(fetchSupersetManualDashboards).toHaveBeenCalledWith(1);
    });
    await screen.findByText('经营分析总览');
    fireEvent.click(screen.getByRole('button', { name: '经营分析总览' }));
    fireEvent.click(screen.getByRole('button', { name: '推送到所选看板' }));
    await waitFor(() => {
      expect(pushSupersetChartToDashboard).toHaveBeenCalledWith({
        pluginId: 1,
        dashboardId: 9001,
        chartId: 22,
      });
    });
  });

  test('creates a new dashboard and pushes current chart when requested', async () => {
    const { embedDashboard } = require('@superset-ui/embedded-sdk');
    const { createSupersetDashboard, pushSupersetChartToDashboard } = require('../../../service');
    embedDashboard.mockClear();
    const data = buildData({
      webPage: { url: '', params: [] },
      pluginId: 1,
      dashboardId: 88,
      dashboardTitle: '访问趋势分析',
      embeddedId: 'embed-line',
      supersetDomain: 'https://superset.example.com',
      vizTypeCandidates: [
        {
          vizType: 'echarts_timeseries_line',
          vizName: 'Line Chart',
          embeddedId: 'embed-line',
          supersetDomain: 'https://superset.example.com',
          chartId: 11,
        },
      ],
    });
    render(<SupersetChart id={1} data={data} />);
    await waitFor(() => {
      expect(embedDashboard).toHaveBeenCalledTimes(1);
    });
    fireEvent.click(screen.getByRole('button', { name: '推送到看板' }));
    fireEvent.change(screen.getByPlaceholderText('输入新看板名称'), {
      target: { value: '我的趋势看板' },
    });
    fireEvent.click(screen.getByRole('button', { name: '新建并推送' }));
    await waitFor(() => {
      expect(createSupersetDashboard).toHaveBeenCalledWith({
        pluginId: 1,
        title: '我的趋势看板',
      });
    });
    await waitFor(() => {
      expect(pushSupersetChartToDashboard).toHaveBeenCalledWith({
        pluginId: 1,
        dashboardId: 9101,
        chartId: 11,
      });
    });
  });

  test('preserves full recommended dimension schema when drilling down and hides repeated dimensions', async () => {
    const { embedDashboard } = require('@superset-ui/embedded-sdk');
    const { queryData } = require('../../../service');
    embedDashboard.mockClear();
    const provinceDimension = buildRecommendedDimension();
    const cityDimension = buildRecommendedDimension({
      id: 3,
      name: '城市',
      bizName: 'city',
      alias: ['城市名称'],
      description: '城市维度',
    });
    queryData.mockResolvedValueOnce({
      code: 200,
      data: buildData(
        {
          webPage: { url: '', params: [] },
          pluginId: 1,
          embeddedId: 'embed-drilled',
          supersetDomain: 'https://superset.example.com',
        },
        {
          chatContext: buildChatContext({
            dimensions: [
              {
                id: 1,
                itemId: 1,
                model: 9,
                name: '日期',
                bizName: 'ds',
                showType: 'DATE',
                type: 'DATE',
                value: 'ds',
                status: 1,
              },
              {
                id: 2,
                itemId: 2,
                model: 9,
                name: '省份',
                bizName: 'province',
                showType: 'CATEGORY',
                type: 'STRING',
                value: 'province',
                status: 1,
              },
            ],
          }),
          recommendedDimensions: [
            provinceDimension,
            cityDimension,
          ],
        }
      ),
    });
    const data = buildData(
      {
        webPage: { url: '', params: [] },
        pluginId: 1,
        embeddedId: 'embed-root-line',
        supersetDomain: 'https://superset.example.com',
        vizType: 'echarts_timeseries_line',
        vizTypeCandidates: [
          {
            vizType: 'echarts_timeseries_line',
            vizName: 'Line Chart',
            embeddedId: 'embed-root-line',
            supersetDomain: 'https://superset.example.com',
            chartId: 21,
          },
          {
            vizType: 'echarts_timeseries_bar',
            vizName: 'Bar Chart',
            embeddedId: 'embed-root-bar',
            supersetDomain: 'https://superset.example.com',
            chartId: 22,
          },
        ],
      },
      {
        recommendedDimensions: [
          provinceDimension,
        ],
      }
    );
    render(<SupersetChart id={data.queryId} data={data} />);
    await waitFor(() => {
      expect(embedDashboard).toHaveBeenCalledTimes(1);
    });

    fireEvent.click(screen.getByRole('button', { name: /省\s*份/ }));

    await waitFor(() => {
      expect(queryData).toHaveBeenCalledWith(
        expect.objectContaining({
          queryId: 901,
          parseId: 701,
          dimensions: expect.arrayContaining([expect.objectContaining({ bizName: 'ds' })]),
        })
      );
    });
    const drilledDimensions = queryData.mock.calls[0][0].dimensions;
    expect(drilledDimensions[1]).toEqual(provinceDimension);
    expect(drilledDimensions[1]).not.toMatchObject({
      itemId: provinceDimension.id,
      status: 1,
      type: 'STRING',
      value: provinceDimension.bizName,
    });
    await waitFor(() => {
      expect(embedDashboard).toHaveBeenCalledTimes(2);
    });
    expect(embedDashboard.mock.calls[1][0].id).toBe('embed-drilled');
    expect(screen.queryByRole('button', { name: /省\s*份/ })).toBeNull();
    expect(screen.getByRole('button', { name: /城\s*市/ })).toBeTruthy();
  });

  test('keeps the selected viz type when drilling down and drilling up', async () => {
    const { embedDashboard } = require('@superset-ui/embedded-sdk');
    const { queryData } = require('../../../service');
    embedDashboard.mockClear();
    const provinceDimension = buildRecommendedDimension();
    queryData
      .mockResolvedValueOnce({
        code: 200,
        data: buildData(
          {
            webPage: { url: '', params: [] },
            pluginId: 1,
            embeddedId: 'embed-drilled-line',
            supersetDomain: 'https://superset.example.com',
            vizType: 'echarts_timeseries_line',
            vizTypeCandidates: [
              {
                vizType: 'echarts_timeseries_line',
                vizName: 'Line Chart',
                embeddedId: 'embed-drilled-line',
                supersetDomain: 'https://superset.example.com',
                chartId: 31,
              },
              {
                vizType: 'echarts_timeseries_bar',
                vizName: 'Bar Chart',
                embeddedId: 'embed-drilled-bar',
                supersetDomain: 'https://superset.example.com',
                chartId: 32,
              },
            ],
          },
          {
            chatContext: buildChatContext({
              dimensions: [
                {
                  id: 1,
                  itemId: 1,
                  model: 9,
                  name: '日期',
                  bizName: 'ds',
                  showType: 'DATE',
                  type: 'DATE',
                  value: 'ds',
                  status: 1,
                },
                {
                  id: 2,
                  itemId: 2,
                  model: 9,
                  name: '省份',
                  bizName: 'province',
                  showType: 'CATEGORY',
                  type: 'STRING',
                  value: 'province',
                  status: 1,
                },
              ],
            }),
            recommendedDimensions: [
              buildRecommendedDimension({
                id: 3,
                name: '城市',
                bizName: 'city',
              }),
            ],
          }
        ),
      })
      .mockResolvedValueOnce({
        code: 200,
        data: buildData(
          {
            webPage: { url: '', params: [] },
            pluginId: 1,
            embeddedId: 'embed-root-returned-line',
            supersetDomain: 'https://superset.example.com',
            vizType: 'echarts_timeseries_line',
            vizTypeCandidates: [
              {
                vizType: 'echarts_timeseries_line',
                vizName: 'Line Chart',
                embeddedId: 'embed-root-returned-line',
                supersetDomain: 'https://superset.example.com',
                chartId: 41,
              },
              {
                vizType: 'echarts_timeseries_bar',
                vizName: 'Bar Chart',
                embeddedId: 'embed-root-returned-bar',
                supersetDomain: 'https://superset.example.com',
                chartId: 42,
              },
            ],
          },
          {
            recommendedDimensions: [
              provinceDimension,
            ],
          }
        ),
      });
    const data = buildData(
      {
        webPage: { url: '', params: [] },
        pluginId: 1,
        embeddedId: 'embed-root-line',
        supersetDomain: 'https://superset.example.com',
        vizType: 'echarts_timeseries_line',
        vizTypeCandidates: [
          {
            vizType: 'echarts_timeseries_line',
            vizName: 'Line Chart',
            embeddedId: 'embed-root-line',
            supersetDomain: 'https://superset.example.com',
            chartId: 21,
          },
          {
            vizType: 'echarts_timeseries_bar',
            vizName: 'Bar Chart',
            embeddedId: 'embed-root-bar',
            supersetDomain: 'https://superset.example.com',
            chartId: 22,
          },
        ],
      },
      {
        recommendedDimensions: [
          provinceDimension,
        ],
      }
    );
    render(<SupersetChart id={data.queryId} data={data} />);
    await waitFor(() => {
      expect(embedDashboard.mock.calls.at(-1)?.[0].id).toBe('embed-root-line');
    });

    fireEvent.click(screen.getByRole('button', { name: '柱状图' }));
    await waitFor(() => {
      expect(embedDashboard.mock.calls.at(-1)?.[0].id).toBe('embed-root-bar');
    });

    fireEvent.click(screen.getByRole('button', { name: /省\s*份/ }));
    await waitFor(() => {
      expect(embedDashboard.mock.calls.at(-1)?.[0].id).toBe('embed-drilled-bar');
    });

    fireEvent.click(screen.getByRole('button', { name: /上\s*钻/ }));

    await waitFor(() => {
      expect(queryData).toHaveBeenLastCalledWith(
        expect.objectContaining({
          queryId: 901,
          parseId: 701,
          dimensions: [expect.objectContaining({ bizName: 'ds' })],
        })
      );
    });
    await waitFor(() => {
      expect(embedDashboard.mock.calls.at(-1)?.[0].id).toBe('embed-root-returned-bar');
    });
  });

  test('keeps the current embedded chart when drill response falls back without embed info', async () => {
    const { embedDashboard } = require('@superset-ui/embedded-sdk');
    const { queryData } = require('../../../service');
    embedDashboard.mockClear();
    queryData.mockResolvedValueOnce({
      code: 200,
      data: buildData(
        {
          webPage: { url: '', params: [] },
          pluginId: 1,
          fallback: true,
          fallbackReason: '当前组合暂不支持继续下钻',
        },
        {
          recommendedDimensions: [],
        }
      ),
    });
    const data = buildData(
      {
        webPage: { url: '', params: [] },
        pluginId: 1,
        embeddedId: 'embed-root',
        supersetDomain: 'https://superset.example.com',
      },
      {
        recommendedDimensions: [buildRecommendedDimension()],
      }
    );

    render(<SupersetChart id={data.queryId} data={data} />);
    await waitFor(() => {
      expect(embedDashboard).toHaveBeenCalledTimes(1);
    });

    fireEvent.click(screen.getByRole('button', { name: /省\s*份/ }));

    await waitFor(() => {
      expect(queryData).toHaveBeenCalledTimes(1);
    });
    expect(embedDashboard).toHaveBeenCalledTimes(1);
    expect(screen.queryByText('Superset 嵌入信息缺失，无法渲染看板。')).toBeNull();
    expect(screen.queryByText('当前钻取路径')).toBeNull();
  });

  test('does not reset drill state when host refreshes the same query context', async () => {
    const { embedDashboard } = require('@superset-ui/embedded-sdk');
    const { queryData } = require('../../../service');
    embedDashboard.mockClear();
    const provinceDimension = buildRecommendedDimension();
    queryData.mockResolvedValueOnce({
      code: 200,
      data: buildData(
        {
          webPage: { url: '', params: [] },
          pluginId: 1,
          embeddedId: 'embed-drilled',
          supersetDomain: 'https://superset.example.com',
        },
        {
          recommendedDimensions: [
            buildRecommendedDimension({
              id: 3,
              name: '城市',
              bizName: 'city',
            }),
          ],
        }
      ),
    });
    const data = buildData(
      {
        webPage: { url: '', params: [] },
        pluginId: 1,
        embeddedId: 'embed-root',
        supersetDomain: 'https://superset.example.com',
      },
      {
        textSummary: '第一次摘要',
        recommendedDimensions: [provinceDimension],
      }
    );

    const { rerender } = render(<SupersetChart id={data.queryId} data={data} />);
    await waitFor(() => {
      expect(embedDashboard).toHaveBeenCalledTimes(1);
    });

    fireEvent.click(screen.getByRole('button', { name: /省\s*份/ }));
    await waitFor(() => {
      expect(embedDashboard).toHaveBeenCalledTimes(2);
    });
    expect(screen.getByText('当前钻取路径')).toBeTruthy();

    rerender(
      <SupersetChart
        id={data.queryId}
        data={{
          ...data,
          textSummary: '刷新后的摘要',
        }}
      />
    );

    expect(embedDashboard).toHaveBeenCalledTimes(2);
    expect(screen.getByText('当前钻取路径')).toBeTruthy();
  });
});
