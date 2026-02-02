import { render, waitFor } from '@testing-library/react';
import SupersetChart, { filterTemporaryDashboards } from './index';

jest.mock('@superset-ui/embedded-sdk', () => ({
  embedDashboard: jest.fn().mockResolvedValue({ unmount: jest.fn() }),
}));

const buildData = (response: any) =>
  ({
    response,
    queryMode: 'SUPERSET',
    queryState: 'SUCCESS',
    queryColumns: [],
    queryResults: [],
  } as any);

describe('SupersetChart', () => {
  test('does not use embedded url fallback', async () => {
    const data = buildData({
      webPage: { url: 'https://superset.example.com/superset/embedded/uuid-123/', params: [] },
      pluginId: 1,
      chartUuid: 'uuid-123',
      guestToken: 'token-123',
    });
    const { embedDashboard } = require('@superset-ui/embedded-sdk');
    const { getByText } = render(<SupersetChart id={1} data={data} />);
    await waitFor(() => {
      expect(getByText('Superset 嵌入信息缺失，无法渲染看板。')).toBeTruthy();
    });
    expect(embedDashboard).not.toHaveBeenCalled();
  });

  test('uses embedded sdk when response provides embed info', async () => {
    const data = buildData({
      webPage: { url: '', params: [] },
      pluginId: 1,
      embeddedId: 'uuid-456',
      supersetDomain: 'https://superset.example.com',
      guestToken: 'token-456',
    });
    const { embedDashboard } = require('@superset-ui/embedded-sdk');
    render(<SupersetChart id={1} data={data} />);
    await waitFor(() => {
      expect(embedDashboard).toHaveBeenCalled();
    });
    const args = embedDashboard.mock.calls[0][0];
    expect(args.id).toBe('uuid-456');
    expect(args.supersetDomain).toBe('https://superset.example.com');
    await expect(args.fetchGuestToken()).resolves.toBe('token-456');
  });

  test('shows error when embed info missing', async () => {
    const data = buildData({
      webPage: { url: '', params: [] },
      guestToken: 'token-123',
    });
    const { getByText } = render(<SupersetChart id={1} data={data} />);
    await waitFor(() => {
      expect(getByText('Superset 嵌入信息缺失，无法渲染看板。')).toBeTruthy();
    });
  });

  test('shows push button when dashboards exist', () => {
    const data = buildData({
      webPage: { url: 'https://superset.example.com/embed', params: [] },
      pluginId: 1,
      chartId: 2,
      dashboards: [{ id: 10, title: 'Sales' }],
    });
    const { getByText } = render(<SupersetChart id={1} data={data} />);
    expect(getByText('推送到看板')).toBeTruthy();
  });

  test('filters temporary dashboards by supersonic prefix', () => {
    const dashboards = [
      { id: 10, title: 'supersonic_Plugin_123' },
      { id: 11, title: 'Sales' },
    ];
    expect(filterTemporaryDashboards(dashboards, 'Plugin')).toEqual([{ id: 11, title: 'Sales' }]);
  });
});
