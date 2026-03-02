import { Button, Drawer, message, Popconfirm, Select, Table } from 'antd';
import moment from 'moment';
import { useEffect, useMemo, useState } from 'react';
import {
  deleteChatQuery,
  getAgentList,
  getChatList,
  getChatQuery,
  pageQueryInfo,
} from './service';
import type { AgentItem, ChatItem, QueryResp } from './service';
import styles from './style.less';

const DEFAULT_PAGE_SIZE = 20;
const MAX_REPLY_LENGTH = 100;

const toTime = (value?: string) => (value ? new Date(value).getTime() : 0);

const getReplySummary = (record: QueryResp) => {
  const result: any = record?.queryResult || {};
  const response: any = result.response;
  const candidates = [
    result.textSummary,
    result.textResult,
    typeof response === 'string' ? response : response?.description,
    response?.name,
    response?.fallbackReason,
  ];
  const text = candidates.find((item) => typeof item === 'string' && item.trim());
  if (!text) {
    return '';
  }
  const value = text.trim();
  return value.length > MAX_REPLY_LENGTH ? `${value.slice(0, MAX_REPLY_LENGTH)}...` : value;
};

const MessageManage = () => {
  const [agents, setAgents] = useState<AgentItem[]>([]);
  const [agentId, setAgentId] = useState<number | undefined>(undefined);
  const [chatList, setChatList] = useState<ChatItem[]>([]);
  const [chatId, setChatId] = useState<number | undefined>(undefined);
  const [data, setData] = useState<QueryResp[]>([]);
  const [loading, setLoading] = useState(false);
  const [pageNum, setPageNum] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [total, setTotal] = useState(0);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [detailVisible, setDetailVisible] = useState(false);
  const [detail, setDetail] = useState<QueryResp | null>(null);

  const loadAgents = async () => {
    const res = await getAgentList();
    setAgents(res?.data || []);
  };

  const loadChats = async (nextAgentId?: number) => {
    const res = await getChatList(nextAgentId);
    const list = (res?.data || []).filter((item) => item.isDelete !== 1);
    setChatList(list);
    if (list.length > 0) {
      setChatId(list[0].chatId);
      return list[0].chatId;
    }
    setChatId(undefined);
    return undefined;
  };

  const loadMessages = async (nextChatId?: number, nextPageNum?: number, nextPageSize?: number) => {
    if (!nextChatId) {
      setData([]);
      setTotal(0);
      return;
    }
    setLoading(true);
    try {
      const res = await pageQueryInfo(
        nextChatId,
        nextPageNum || pageNum,
        nextPageSize || pageSize
      );
      const pageInfo = res?.data;
      const list = pageInfo?.list || [];
      const sortedList = [...list].sort((a, b) => toTime(b.createTime) - toTime(a.createTime));
      setData(sortedList);
      setTotal(pageInfo?.total || 0);
    } catch (err) {
      message.error('消息列表获取失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAgents();
  }, []);

  useEffect(() => {
    loadChats(agentId).then((firstChatId) => {
      setPageNum(1);
      setSelectedRowKeys([]);
      loadMessages(firstChatId, 1, pageSize);
    });
  }, [agentId]);

  useEffect(() => {
    loadMessages(chatId, pageNum, pageSize);
  }, [chatId, pageNum, pageSize]);

  const agentOptions = useMemo(
    () =>
      agents.map((agent) => ({
        label: agent.name || `Agent ${agent.id}`,
        value: agent.id,
      })),
    [agents]
  );

  const chatOptions = useMemo(
    () =>
      chatList.map((chat) => ({
        label: chat.chatName || `Chat ${chat.chatId}`,
        value: chat.chatId,
      })),
    [chatList]
  );

  const onDeleteSingle = async (record: QueryResp) => {
    if (!record?.questionId) {
      return;
    }
    await deleteChatQuery(record.questionId);
    message.success('已删除');
    setData((prev) => prev.filter((item) => item.questionId !== record.questionId));
    setSelectedRowKeys((prev) => prev.filter((key) => key !== record.questionId));
  };

  const onBatchDelete = async () => {
    const ids = selectedRowKeys as number[];
    if (!ids.length) {
      message.warning('请选择要删除的消息');
      return;
    }
    for (const id of ids) {
      await deleteChatQuery(id);
    }
    message.success('批量删除成功');
    setData((prev) => prev.filter((item) => !ids.includes(item.questionId || -1)));
    setSelectedRowKeys([]);
  };

  const openDetail = async (record: QueryResp) => {
    if (!record?.questionId) {
      return;
    }
    try {
      const res = await getChatQuery(record.questionId);
      setDetail(res?.data || record);
      setDetailVisible(true);
    } catch (err) {
      message.error('消息详情获取失败');
    }
  };

  const columns = [
    {
      title: '消息ID',
      dataIndex: 'questionId',
      key: 'questionId',
      width: 120,
    },
    {
      title: '消息内容',
      dataIndex: 'queryText',
      key: 'queryText',
      render: (_: string, record: QueryResp) => {
        const reply = getReplySummary(record);
        return (
          <div>
            <div>{record.queryText || '-'}</div>
            {reply ? (
              <div className={styles.replySummary}>
                <span className={styles.replyLabel}>回复:</span>
                {reply}
              </div>
            ) : null}
          </div>
        );
      },
    },
    {
      title: '会话ID',
      dataIndex: 'chatId',
      key: 'chatId',
      width: 120,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (value: string) => (value ? moment(value).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_: any, record: QueryResp) => (
        <>
          <a
            onClick={() => {
              openDetail(record);
            }}
          >
            查看
          </a>
          <span style={{ margin: '0 8px' }}>|</span>
          <Popconfirm
            title="确定删除这条消息吗？"
            onConfirm={() => {
              onDeleteSingle(record);
            }}
          >
            <a>删除</a>
          </Popconfirm>
        </>
      ),
    },
  ];

  return (
    <div className={styles.messageManage}>
      <div className={styles.filterSection}>
        <div className={styles.filterItem}>
          <div className={styles.filterLabel}>助理名称</div>
          <Select
            className={styles.filterControl}
            placeholder="请选择助理"
            options={agentOptions}
            value={agentId}
            allowClear
            showSearch
            optionFilterProp="label"
            onChange={(value) => setAgentId(value)}
          />
        </div>
        <div className={styles.filterItem}>
          <div className={styles.filterLabel}>会话</div>
          <Select
            className={styles.filterControl}
            placeholder="请选择会话"
            options={chatOptions}
            value={chatId}
            allowClear
            showSearch
            optionFilterProp="label"
            onChange={(value) => setChatId(value)}
          />
        </div>
      </div>
      <div className={styles.listSection}>
        <div className={styles.titleBar}>
          <div className={styles.title}>消息列表</div>
          <div className={styles.actions}>
            <Popconfirm title="确定删除选中的消息吗？" onConfirm={onBatchDelete}>
              <Button danger disabled={selectedRowKeys.length === 0}>
                批量删除
              </Button>
            </Popconfirm>
          </div>
        </div>
        <Table
          rowKey="questionId"
          columns={columns}
          dataSource={data}
          loading={loading}
          rowSelection={{
            selectedRowKeys,
            onChange: (keys) => setSelectedRowKeys(keys),
          }}
          pagination={{
            current: pageNum,
            pageSize,
            total,
            showSizeChanger: true,
            onChange: (page, size) => {
              setPageNum(page);
              setPageSize(size || DEFAULT_PAGE_SIZE);
            },
          }}
        />
      </div>
      <Drawer
        title="消息详情"
        width={720}
        open={detailVisible}
        onClose={() => {
          setDetailVisible(false);
          setDetail(null);
        }}
      >
        <div className={styles.detailRow}>
          <span className={styles.detailLabel}>消息ID:</span>
          <span>{detail?.questionId ?? '-'}</span>
        </div>
        <div className={styles.detailRow}>
          <span className={styles.detailLabel}>会话ID:</span>
          <span>{detail?.chatId ?? '-'}</span>
        </div>
        <div className={styles.detailRow}>
          <span className={styles.detailLabel}>创建时间:</span>
          <span>
            {detail?.createTime ? moment(detail?.createTime).format('YYYY-MM-DD HH:mm') : '-'}
          </span>
        </div>
        <div className={styles.detailRow}>
          <span className={styles.detailLabel}>问题:</span>
          <span>{detail?.queryText ?? '-'}</span>
        </div>
        <div className={styles.detailRow}>
          <span className={styles.detailLabel}>解析信息:</span>
          <pre className={styles.preBlock}>
            {JSON.stringify(detail?.parseInfos || [], null, 2)}
          </pre>
        </div>
        <div className={styles.detailRow}>
          <span className={styles.detailLabel}>查询结果:</span>
          <pre className={styles.preBlock}>
            {JSON.stringify(detail?.queryResult || {}, null, 2)}
          </pre>
        </div>
      </Drawer>
    </div>
  );
};

export default MessageManage;
