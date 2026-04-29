import fs from 'node:fs';
import path from 'node:path';

describe('MessageContainer styles', () => {
  test('uses auto vertical overflow so the scrollbar only appears when content exceeds the viewport', () => {
    const styleFile = path.resolve(__dirname, 'style.module.less');
    const source = fs.readFileSync(styleFile, 'utf8');

    expect(source).toContain('overflow-y: auto;');
    expect(source).not.toContain('overflow-y: scroll;');
    expect(source).toContain('scrollbar-gutter: stable both-edges;');
  });
});
