import { useLayoutEffect } from 'react';
import { useLocation } from '@umijs/max';
import { getToken } from '@/utils/utils';
import { Chat } from 'supersonic-chat-sdk';
import {
  applyChatPageLayoutClass,
  CHAT_PAGE_ROOT_CLASS_NAME,
} from './layout';
import { getChatPageProps } from './routeState';

const ChatPage = () => {
  const location = useLocation();
  const chatPageProps = getChatPageProps(location.pathname, location.search, getToken() || '');

  useLayoutEffect(() => {
    return applyChatPageLayoutClass(document);
  }, []);

  return (
    <div className={CHAT_PAGE_ROOT_CLASS_NAME}>
      <Chat {...chatPageProps} />
    </div>
  );
};

export default ChatPage;
