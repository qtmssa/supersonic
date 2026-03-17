const fs = require('fs');
const path = require('path');

const rootDir = path.resolve(__dirname, '..');
const pnpmDir = path.join(rootDir, 'node_modules', '.pnpm');
const shimContent = "module.exports = require('../prism');\n";

function ensureShim(prismDir) {
  const componentsDir = path.join(prismDir, 'components');
  const shimPath = path.join(componentsDir, 'prism-core.js');

  if (!fs.existsSync(componentsDir) || fs.existsSync(shimPath)) {
    return false;
  }

  fs.writeFileSync(shimPath, shimContent, 'utf8');
  return true;
}

let patched = 0;

if (fs.existsSync(pnpmDir)) {
  for (const entry of fs.readdirSync(pnpmDir, { withFileTypes: true })) {
    if (!entry.isDirectory() || !entry.name.startsWith('prismjs@')) {
      continue;
    }
    const prismDir = path.join(pnpmDir, entry.name, 'node_modules', 'prismjs');
    if (fs.existsSync(prismDir) && ensureShim(prismDir)) {
      patched += 1;
    }
  }
}

const hoistedPrismDir = path.join(rootDir, 'node_modules', 'prismjs');
if (fs.existsSync(hoistedPrismDir) && ensureShim(hoistedPrismDir)) {
  patched += 1;
}

if (patched > 0) {
  console.log(`Patched prism-core shim in ${patched} prismjs installation(s).`);
}
