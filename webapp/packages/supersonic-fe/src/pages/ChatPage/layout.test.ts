import assert = require('node:assert/strict');
import {
  applyChatPageLayoutClass,
  CHAT_PAGE_CLASS_NAME,
  CHAT_LAYOUT_HEADER_HEIGHT_FALLBACK_PX,
  CHAT_LAYOUT_HEADER_SELECTOR,
  CHAT_LAYOUT_HEADER_HEIGHT_VALUE,
  CHAT_LAYOUT_HEADER_HEIGHT_VAR,
  CHAT_PAGE_ROOT_CLASS_NAME,
  getChatChildrenWrapperStyle,
  getChatContentStyle,
  syncChatPageHeaderHeight,
} from './layout';

const createClassList = () => {
  const tokens = new Set<string>();

  return {
    add: (...items: string[]) => items.forEach((item) => tokens.add(item)),
    remove: (...items: string[]) => items.forEach((item) => tokens.delete(item)),
    has: (item: string) => tokens.has(item),
  };
};

const styleState = new Map<string, string>();
const style = {
  setProperty: (name: string, value: string) => {
    styleState.set(name, value);
  },
  removeProperty: (name: string) => {
    styleState.delete(name);
  },
};

const headerElement = {
  getBoundingClientRect: () =>
    ({
      x: 0,
      y: 0,
      width: 0,
      height: 72,
      top: 0,
      right: 0,
      bottom: 72,
      left: 0,
      toJSON: () => null,
    }) as DOMRect,
};

let resizeObserverTarget: unknown;
let resizeObserverDisconnected = false;

class ResizeObserverMock {
  constructor(private readonly callback: ResizeObserverCallback) {}

  observe(target: unknown) {
    resizeObserverTarget = target;
  }

  disconnect() {
    resizeObserverDisconnected = true;
  }

  trigger() {
    this.callback([], this as unknown as ResizeObserver);
  }
}

const documentElementClassList = createClassList();
const bodyClassList = createClassList();

const cleanup = applyChatPageLayoutClass({
  documentElement: { classList: documentElementClassList, style },
  body: { classList: bodyClassList },
  querySelector: (selector) => {
    assert.equal(selector, CHAT_LAYOUT_HEADER_SELECTOR);
    return headerElement;
  },
  defaultView: {
    ResizeObserver: ResizeObserverMock,
  },
});

assert.equal(documentElementClassList.has(CHAT_PAGE_CLASS_NAME), true);
assert.equal(bodyClassList.has(CHAT_PAGE_CLASS_NAME), true);
assert.equal(styleState.get(CHAT_LAYOUT_HEADER_HEIGHT_VAR), '72px');
assert.equal(resizeObserverTarget, headerElement);

cleanup();

assert.equal(documentElementClassList.has(CHAT_PAGE_CLASS_NAME), false);
assert.equal(bodyClassList.has(CHAT_PAGE_CLASS_NAME), false);
assert.equal(styleState.has(CHAT_LAYOUT_HEADER_HEIGHT_VAR), false);
assert.equal(resizeObserverDisconnected, true);

assert.equal(
  syncChatPageHeaderHeight({
    documentElement: { classList: documentElementClassList, style },
    querySelector: () => null,
  }),
  CHAT_LAYOUT_HEADER_HEIGHT_FALLBACK_PX
);
assert.equal(styleState.get(CHAT_LAYOUT_HEADER_HEIGHT_VAR), '56px');

const contentStyle = getChatContentStyle({ background: 'red' }) as Record<string, unknown>;
assert.equal(contentStyle.background, 'red');
assert.equal(contentStyle.minHeight, 0);
assert.equal(CHAT_LAYOUT_HEADER_HEIGHT_VAR, '--ss-app-header-height');
assert.equal(CHAT_LAYOUT_HEADER_HEIGHT_FALLBACK_PX, 56);
assert.equal(contentStyle.height, `calc(100dvh - ${CHAT_LAYOUT_HEADER_HEIGHT_VALUE})`);
assert.equal(contentStyle.overflow, 'hidden');

const wrapperStyle = getChatChildrenWrapperStyle() as Record<string, unknown>;
assert.equal(wrapperStyle.display, 'flex');
assert.equal(wrapperStyle.flexDirection, 'column');
assert.equal(wrapperStyle.flex, 1);
assert.equal(wrapperStyle.minHeight, 0);
assert.equal(wrapperStyle.height, '100%');
assert.equal(wrapperStyle.overflow, 'hidden');

assert.equal(CHAT_PAGE_ROOT_CLASS_NAME, 'ss-chat-page-root');

const globalStyleSource = require('node:fs').readFileSync(
  require('node:path').resolve(process.cwd(), 'src/global.less'),
  'utf8'
);
assert.match(globalStyleSource, /body\.ss-chat-page \.ant-layout\s*\{[^}]*height:\s*100%;/);

console.log('chat-page-layout-test: ok');
