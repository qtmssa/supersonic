import assert = require('node:assert/strict');
import fs = require('node:fs');
import path = require('node:path');

const readSource = (...segments: string[]) =>
  fs.readFileSync(path.resolve(process.cwd(), ...segments), 'utf8');

const appSource = readSource('src/app.tsx');
assert.match(appSource, /getChatLayoutRuntime/);
assert.match(appSource, /const chatShellRuntime = getChatLayoutRuntime\(/);
assert.match(appSource, /chatShellRuntime\.contentStyle/);
assert.match(appSource, /chatShellRuntime\.childrenWrapperStyle/);
assert.match(appSource, /chatShellRuntime\.copilotProps/);

const chatPageSource = readSource('src/pages/ChatPage/index.tsx');
assert.match(chatPageSource, /getChatPageProps/);
assert.match(chatPageSource, /const chatPageProps = getChatPageProps\(/);
assert.match(chatPageSource, /<Chat \{\.\.\.chatPageProps\} \/>/);

console.log('chat-page-wiring-test: ok');
