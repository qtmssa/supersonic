const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const rootDir = path.resolve(__dirname, '..');
const chatSdkDir = path.join(rootDir, 'packages', 'chat-sdk');
const supersonicFeDir = path.join(rootDir, 'packages', 'supersonic-fe');
const outputDir = path.join(supersonicFeDir, 'supersonic-webapp');
const tarName = 'supersonic-webapp.tar.gz';
const tarPathInFe = path.join(supersonicFeDir, tarName);
const tarPathInRoot = path.join(rootDir, tarName);

function fail(message, code = 1) {
  console.error(message);
  process.exit(code);
}

function run(command, cwd) {
  const result = spawnSync(command, {
    cwd,
    env: process.env,
    shell: true,
    stdio: 'inherit',
  });

  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

const majorVersion = Number(process.versions.node.split('.')[0]);
if (![18, 20].includes(majorVersion)) {
  fail(
    `Current Node.js ${process.version} is incompatible with the Supersonic frontend toolchain. Please use Node.js 18 or 20 LTS.`,
  );
}

process.env.NODE_OPTIONS = '--openssl-legacy-provider';
console.log(`Using Node.js ${process.version}. NODE_OPTIONS has been set to --openssl-legacy-provider.`);

const pnpmCheck = spawnSync('pnpm --version', {
  cwd: rootDir,
  env: process.env,
  shell: true,
  stdio: 'ignore',
});

if (pnpmCheck.status !== 0) {
  console.log('pnpm is not installed. Installing...');
  run('npm install -g pnpm', rootDir);
  console.log('pnpm installed successfully.');
} else {
  console.log('pnpm is already installed.');
}

fs.rmSync(tarPathInRoot, { force: true });
fs.rmSync(path.join(supersonicFeDir, '.umi'), { recursive: true, force: true });
fs.rmSync(path.join(supersonicFeDir, '.umi-production'), { recursive: true, force: true });

run('pnpm i --config.confirmModulesPurge=false', rootDir);
require(path.join(__dirname, 'ensure-prism-core.cjs'));

run('pnpm run build', chatSdkDir);
run('pnpm run build:os-local', supersonicFeDir);

if (!fs.existsSync(outputDir)) {
  fail('Frontend build completed but did not produce the supersonic-webapp directory.');
}

run(`tar -zcvf ${tarName} supersonic-webapp`, supersonicFeDir);

fs.rmSync(tarPathInRoot, { force: true });
fs.renameSync(tarPathInFe, tarPathInRoot);
