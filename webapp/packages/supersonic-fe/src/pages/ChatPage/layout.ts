import type { CSSProperties } from 'react';

export const CHAT_PAGE_CLASS_NAME = 'ss-chat-page';
export const CHAT_PAGE_ROOT_CLASS_NAME = 'ss-chat-page-root';
export const CHAT_LAYOUT_HEADER_HEIGHT_VAR = '--ss-app-header-height';
export const CHAT_LAYOUT_HEADER_HEIGHT_FALLBACK_PX = 56;
export const CHAT_LAYOUT_HEADER_HEIGHT_VALUE = `var(${CHAT_LAYOUT_HEADER_HEIGHT_VAR}, ${CHAT_LAYOUT_HEADER_HEIGHT_FALLBACK_PX}px)`;
export const CHAT_LAYOUT_HEADER_SELECTOR =
  '.ant-pro-global-header, .ant-layout-header, .ant-page-header';

type ClassListTarget = {
  classList: {
    add: (...tokens: string[]) => void;
    remove: (...tokens: string[]) => void;
  };
};

type StyleTarget = {
  setProperty: (property: string, value: string) => void;
  removeProperty: (property: string) => void;
};

type HeaderElementTarget = Partial<Element> & {
  offsetHeight?: number;
  getBoundingClientRect?: () => { height: number };
};

type ResizeObserverLike = {
  observe: (target: Element | HeaderElementTarget) => void;
  disconnect: () => void;
};

type ResizeObserverConstructorLike = new (
  callback: ResizeObserverCallback
) => ResizeObserverLike;

type ChatPageDocumentLike = {
  documentElement?: (ClassListTarget & { style?: StyleTarget | null }) | null;
  body?: ClassListTarget | null;
  querySelector?: (selector: string) => HeaderElementTarget | null;
  defaultView?: {
    ResizeObserver?: ResizeObserverConstructorLike;
  } | null;
};

export const getChatContentStyle = (
  baseStyle: CSSProperties = {},
): CSSProperties => ({
  ...baseStyle,
  minWidth: 0,
  minHeight: 0,
  height: `calc(100dvh - ${CHAT_LAYOUT_HEADER_HEIGHT_VALUE})`,
  overflow: 'hidden',
});

export const getChatChildrenWrapperStyle = (): CSSProperties => ({
  display: 'flex',
  flexDirection: 'column',
  flex: 1,
  minWidth: 0,
  minHeight: 0,
  height: '100%',
  overflow: 'hidden',
});

const getChatHeaderHeight = (header: HeaderElementTarget | null | undefined) => {
  const measuredHeight = header?.getBoundingClientRect?.().height;
  if (typeof measuredHeight === 'number' && measuredHeight > 0) {
    return Math.round(measuredHeight);
  }
  if (typeof header?.offsetHeight === 'number' && header.offsetHeight > 0) {
    return header.offsetHeight;
  }
  return CHAT_LAYOUT_HEADER_HEIGHT_FALLBACK_PX;
};

export const syncChatPageHeaderHeight = (
  doc: ChatPageDocumentLike,
  header = doc.querySelector?.(CHAT_LAYOUT_HEADER_SELECTOR)
) => {
  const height = getChatHeaderHeight(header);
  doc.documentElement?.style?.setProperty(CHAT_LAYOUT_HEADER_HEIGHT_VAR, `${height}px`);
  return height;
};

export const applyChatPageLayoutClass = (doc: ChatPageDocumentLike) => {
  doc.documentElement?.classList.add(CHAT_PAGE_CLASS_NAME);
  doc.body?.classList.add(CHAT_PAGE_CLASS_NAME);
  const header = doc.querySelector?.(CHAT_LAYOUT_HEADER_SELECTOR);
  syncChatPageHeaderHeight(doc, header);

  const ResizeObserverImpl = doc.defaultView?.ResizeObserver;
  const observer =
    header && ResizeObserverImpl
      ? new ResizeObserverImpl(() => {
          syncChatPageHeaderHeight(doc, header);
        })
      : null;

  if (observer && header) {
    observer.observe(header);
  }

  return () => {
    observer?.disconnect();
    doc.documentElement?.style?.removeProperty(CHAT_LAYOUT_HEADER_HEIGHT_VAR);
    doc.documentElement?.classList.remove(CHAT_PAGE_CLASS_NAME);
    doc.body?.classList.remove(CHAT_PAGE_CLASS_NAME);
  };
};
