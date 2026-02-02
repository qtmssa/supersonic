import { useEffect, useMemo, useRef, useState } from 'react';
import { Button, Dropdown, message } from 'antd';
import { embedDashboard } from '@superset-ui/embedded-sdk';
import {
  MsgDataType,
  SupersetChartResponseType,
  SupersetDashboardType,
} from '../../../common/type';
import {
  fetchSupersetDashboards,
  fetchSupersetGuestToken,
  pushSupersetChartToDashboard,
} from '../../../service';
import { isMobile } from '../../../utils/utils';

type Props = {
  id: string | number;
  data: MsgDataType;
};

const DEFAULT_HEIGHT = 800;
const SUPERSET_SINGLE_CHART_PREFIX = 'supersonic_';

export const filterTemporaryDashboards = (
  dashboardList: SupersetDashboardType[],
  pluginName?: string
) => {
  if (!Array.isArray(dashboardList) || dashboardList.length === 0) {
    return [];
  }
  if (!pluginName) {
    return dashboardList;
  }
  const prefix = `${SUPERSET_SINGLE_CHART_PREFIX}${pluginName}_`;
  return dashboardList.filter(dashboard => {
    const title = dashboard?.title || '';
    return !title.startsWith(prefix);
  });
};

const SupersetChart: React.FC<Props> = ({ id, data }) => {
  const [height, setHeight] = useState(DEFAULT_HEIGHT);
  const [dashboards, setDashboards] = useState<SupersetDashboardType[]>([]);
  const [dashboardsLoading, setDashboardsLoading] = useState(false);
  const [pushLoading, setPushLoading] = useState(false);
  const embedContainerRef = useRef<HTMLDivElement>(null);
  const embedInstanceRef = useRef<{ unmount: () => void } | null>(null);
  const initialGuestTokenRef = useRef<string | undefined>(undefined);
  const response = data.response as SupersetChartResponseType;
  const webPage = response?.webPage;
  const guestToken = response?.guestToken;

  const buildParamValue = (value: any) => {
    if (value === null || value === undefined) {
      return '';
    }
    if (typeof value === 'string') {
      return value;
    }
    return JSON.stringify(value);
  };

  const params = useMemo(() => {
    const rawParams = webPage?.params || webPage?.paramOptions || [];
    return Array.isArray(rawParams) ? rawParams : [];
  }, [webPage]);

  useEffect(() => {
    initialGuestTokenRef.current = guestToken;
  }, [guestToken]);

  const embedInfo = useMemo(() => {
    const embeddedId = response?.embeddedId;
    const supersetDomain = response?.supersetDomain;
    if (embeddedId && supersetDomain) {
      return { embedId: embeddedId, supersetDomain };
    }
    return null;
  }, [response?.embeddedId, response?.supersetDomain]);

  const valueParams = useMemo(() => {
    return (params || [])
      .filter((option: any) => option.paramType !== 'FORWARD')
      .reduce((result: any, item: any) => {
        if (item.key === 'guestToken') {
          return result;
        }
        result[item.key] = buildParamValue(item.value);
        return result;
      }, {});
  }, [params]);

  useEffect(() => {
    const heightValue =
      params?.find((option: any) => option.paramType === 'FORWARD' && option.key === 'height')
        ?.value || DEFAULT_HEIGHT;
    setHeight(heightValue);
  }, [params]);

  useEffect(() => {
    if (!embedInfo || !embedContainerRef.current) {
      return;
    }
    let cancelled = false;
    embedInstanceRef.current?.unmount();
    embedInstanceRef.current = null;
    embedContainerRef.current.replaceChildren();
    const fetchGuestToken = async () => {
      if (initialGuestTokenRef.current) {
        const token = initialGuestTokenRef.current;
        initialGuestTokenRef.current = undefined;
        return token;
      }
      const responseToken = await fetchSupersetGuestToken({
        pluginId: response?.pluginId,
        embeddedId: embedInfo.embedId,
      });
      return responseToken?.data?.token || '';
    };
    embedDashboard({
      id: embedInfo.embedId,
      supersetDomain: embedInfo.supersetDomain,
      mountPoint: embedContainerRef.current,
      fetchGuestToken,
      dashboardUiConfig: {
        hideTitle: true,
        hideTab: true,
        hideChartControls: true,
        filters: { visible: false, expanded: false },
        urlParams: Object.keys(valueParams || {}).length > 0 ? valueParams : undefined,
      },
    })
      .then(instance => {
        if (cancelled) {
          instance.unmount();
          return;
        }
        embedInstanceRef.current = instance;
        const iframe = embedContainerRef.current?.querySelector('iframe');
        if (iframe) {
          iframe.style.width = '100%';
          iframe.style.height = '100%';
          iframe.style.border = 'none';
        }
      })
      .catch(() => {
        message.error('Superset 嵌入失败');
      });
    return () => {
      cancelled = true;
      embedInstanceRef.current?.unmount();
      embedInstanceRef.current = null;
    };
  }, [embedInfo, response?.pluginId, valueParams]);

  useEffect(() => {
    if (Array.isArray(response?.dashboards)) {
      setDashboards(response?.dashboards || []);
    }
  }, [response?.dashboards]);

  useEffect(() => {
    const shouldFetch = response?.dashboards === undefined && response?.pluginId;
    if (!shouldFetch) {
      return;
    }
    setDashboardsLoading(true);
    fetchSupersetDashboards(response.pluginId)
      .then(res => {
        setDashboards(res?.data || []);
      })
      .catch(() => {
        message.error('获取 Dashboard 列表失败');
      })
      .finally(() => {
        setDashboardsLoading(false);
      });
  }, [response?.dashboards, response?.pluginId]);

  const handlePush = (dashboardId?: number) => {
    if (!dashboardId) {
      return;
    }
    if (!response?.chartId) {
      message.error('Chart 信息缺失');
      return;
    }
    setPushLoading(true);
    pushSupersetChartToDashboard({
      pluginId: response?.pluginId,
      dashboardId,
      chartId: response.chartId,
    })
      .then(() => {
        message.success('已推送到看板');
      })
      .catch(() => {
        message.error('推送失败');
      })
      .finally(() => {
        setPushLoading(false);
      });
  };

  const filteredDashboards = useMemo(
    () => filterTemporaryDashboards(dashboards, response?.name),
    [dashboards, response?.name]
  );

  const menuItems =
    filteredDashboards.length > 0
      ? filteredDashboards.map(item => ({
          key: String(item.id ?? item.title ?? 'unknown'),
          label: item.title || `Dashboard ${item.id}`,
        }))
      : [
          {
            key: 'empty',
            label: '暂无 Dashboard',
            disabled: true,
          },
        ];

  const showPushButton = !response?.fallback && response?.pluginId && response?.chartId;

  return (
    <>
      {showPushButton && (
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 8 }}>
          <Dropdown
            menu={{
              items: menuItems,
              onClick: info => {
                const dashboardId = Number(info.key);
                handlePush(Number.isNaN(dashboardId) ? undefined : dashboardId);
              },
            }}
            disabled={dashboardsLoading}
          >
            <Button size="small" loading={dashboardsLoading || pushLoading}>
              推送到看板
            </Button>
          </Dropdown>
        </div>
      )}
      {embedInfo ? (
        <div
          ref={embedContainerRef}
          style={{
            width: isMobile ? 'calc(100vw - 20px)' : 'calc(100vw - 410px)',
            height,
          }}
        />
      ) : (
        <div style={{ width: '100%', height }}>
          Superset 嵌入信息缺失，无法渲染看板。
        </div>
      )}
    </>
  );
};

export default SupersetChart;
