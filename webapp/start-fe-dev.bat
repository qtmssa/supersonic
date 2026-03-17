@echo off

for /f "delims=" %%i in ('node -v') do set "node_version=%%i"
for /f "tokens=2 delims=v." %%i in ("%node_version%") do set "major_version=%%i"
if not "%major_version%"=="18" if not "%major_version%"=="20" (
  echo Current Node.js %node_version% is incompatible with the Supersonic frontend toolchain. Please use Node.js 18 or 20 LTS.
  exit /b 1
)
set "NODE_OPTIONS=--openssl-legacy-provider"
echo Using Node.js %node_version%. NODE_OPTIONS has been set to --openssl-legacy-provider.
where /q pnpm
if errorlevel 1 (
  echo pnpm is not installed. Installing...
  npm install -g pnpm
  if errorlevel 1 (
    echo Failed to install pnpm. Please check if npm is installed and the network connection is working.
    exit /b 1
  ) else (
    echo pnpm installed successfully.
  )
) else (
  echo pnpm is already installed.
)

rmdir /s /q ".\packages\supersonic-fe\src\.umi" 2>nul
rmdir /s /q ".\packages\supersonic-fe\src\.umi-production" 2>nul

cmd /c "pnpm i --config.confirmModulesPurge=false"
if errorlevel 1 exit /b 1
node .\scripts\ensure-prism-core.cjs
if errorlevel 1 exit /b 1

cd ./packages/chat-sdk
cmd /c "pnpm run build"
if errorlevel 1 exit /b 1

cd ../supersonic-fe
cmd /c "pnpm start"
