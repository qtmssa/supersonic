#!/usr/bin/env bash

sbinDir=$(cd "$(dirname "$0")"; pwd)
source "$sbinDir/supersonic-common.sh"
source "$sbinDir/supersonic-env.sh"

function loadProjectEnv {
  local project_env="$projectDir/.env"
  if [ -f "$project_env" ]; then
    set -a
    source "$project_env"
    set +a
  fi
}

function requireSystemdUser {
  if ! command -v systemd-run >/dev/null 2>&1; then
    echo "systemd-run is not available on this host."
    exit 1
  fi
  if ! systemctl --user show-environment >/dev/null 2>&1; then
    echo "systemd --user is not available in the current session."
    exit 1
  fi
}

loadProjectEnv

command=$1
service=$2
profile=$3

if [ -z "$command" ]; then
  echo "Use command {start|stop|restart|status|logs} to run."
  exit 1
fi

if [ -z "$service"  ]; then
  service=${STANDALONE_SERVICE}
fi

if [ -z "$profile" ]; then
  profile=${S2_DB_TYPE}
fi

model_name=$service
server_port=${S2_SERVER_PORT:-9080}
instance_id=${S2_INSTANCE_ID:-}
resolved_runtime_dir=$baseDir
main_class=
app_name=
unit_suffix=
unit_name=

function setMainClass {
  if [ "$service" == "$CHAT_SERVICE" ]; then
    main_class="com.tencent.supersonic.ChatLauncher"
  elif [ "$service" == "$HEADLESS_SERVICE" ]; then
    main_class="com.tencent.supersonic.HeadlessLauncher"
  else
    main_class="com.tencent.supersonic.StandaloneLauncher"
  fi
}

function setAppName {
  if [ "$service" == "$CHAT_SERVICE" ]; then
    app_name=$CHAT_APP_NAME
  elif [ "$service" == "$HEADLESS_SERVICE" ]; then
    app_name=$HEADLESS_APP_NAME
  else
    app_name=$STANDALONE_APP_NAME
  fi
  if [ -n "$instance_id" ]; then
    app_name="${app_name}_${instance_id}"
  fi
}

function setUnitName {
  unit_suffix=$service
  if [ -n "$instance_id" ]; then
    unit_suffix="${unit_suffix}-${instance_id}"
  fi
  unit_suffix=$(printf '%s' "$unit_suffix" | tr -c 'A-Za-z0-9_.@-' '-')
  unit_name="supersonic-${unit_suffix}.service"
}

function findServiceArchive {
  local archive
  archive=$(find "$projectDir/launchers/${model_name}/target" -maxdepth 1 -name "launchers-${model_name}-*-bin.tar.gz" | sort | tail -n 1)
  if [ -z "$archive" ]; then
    echo "Failed to find launcher archive for ${model_name} under $projectDir/launchers/${model_name}/target." >&2
    return 1
  fi
  echo "$archive"
}

function ensureRuntimeLayout {
  if [ -d "$baseDir/conf" ] && compgen -G "$baseDir/lib/*.jar" >/dev/null; then
    resolved_runtime_dir=$baseDir
    return 0
  fi

  local archive_path
  archive_path=$(findServiceArchive) || return 1

  resolved_runtime_dir="$runtimeDir/$app_name"
  mkdir -p "$resolved_runtime_dir"

  if [ -f "$resolved_runtime_dir/conf/application.yaml" ] && compgen -G "$resolved_runtime_dir/lib/*.jar" >/dev/null; then
    return 0
  fi

  echo "Preparing runtime layout in $resolved_runtime_dir from $(basename "$archive_path")"
  tar -xzf "$archive_path" --strip-components=1 -C "$resolved_runtime_dir"
}

function buildClasspath {
  local lib_dir="$1"
  local runtime_root
  runtime_root=$(dirname "$lib_dir")
  local classpath="$runtime_root/conf"

  if [ -f "$lib_dir/common-1.0.0-SNAPSHOT.jar" ]; then
    classpath="$classpath:$lib_dir/common-1.0.0-SNAPSHOT.jar"
  fi

  for jarPath in "$lib_dir"/*.jar; do
    if [ "$jarPath" = "$lib_dir/common-1.0.0-SNAPSHOT.jar" ]; then
      continue
    fi
    classpath="$classpath:$jarPath"
  done

  printf '%s\n' "$classpath"
}

function start() {
  requireSystemdUser
  setMainClass
  setAppName
  setUnitName

  if systemctl --user is-active --quiet "$unit_name"; then
    echo "Unit $unit_name is already running."
    return 1
  fi

  if ! ensureRuntimeLayout; then
    echo "Failed to prepare runtime layout."
    return 1
  fi

  local lib_dir="$resolved_runtime_dir/lib"
  local classpath
  classpath=$(buildClasspath "$lib_dir")
  local jvm_opts="${S2_JAVA_OPTS:-"-Xms1024m -Xmx2048m -XX:+UseZGC -XX:+ZGenerational"}"
  local -a jvm_opts_array=()
  local -a env_args=()
  local var_name
  read -r -a jvm_opts_array <<< "$jvm_opts"

  while IFS= read -r var_name; do
    env_args+=(--setenv="${var_name}=${!var_name}")
  done < <(compgen -A variable S2_)

  systemctl --user reset-failed "$unit_name" >/dev/null 2>&1 || true
  echo "Starting $unit_name on port $server_port with systemd --user"
  systemd-run \
    --user \
    --unit="$unit_name" \
    --description="Supersonic ${service} service" \
    --working-directory="$resolved_runtime_dir" \
    --setenv=CLASSPATH="$classpath" \
    --setenv=LANG=zh_CN.UTF-8 \
    "${env_args[@]}" \
    java \
    -Dspring.profiles.active="$profile" \
    -Dfile.encoding=UTF-8 \
    -Duser.language=Zh \
    -Duser.region=CN \
    -Duser.timezone=GMT+08 \
    -Dapp_name="$app_name" \
    -Dserver.port="$server_port" \
    "${jvm_opts_array[@]}" \
    "$main_class"
}

function stop() {
  requireSystemdUser
  setUnitName
  systemctl --user stop "$unit_name"
}

function status() {
  requireSystemdUser
  setUnitName
  systemctl --user status "$unit_name" --no-pager
}

function logs() {
  requireSystemdUser
  setUnitName
  journalctl --user -u "$unit_name" -n "${S2_JOURNAL_LINES:-100}" --no-pager
}

function restart() {
  stop >/dev/null 2>&1 || true
  start
}

case "$command" in
  start)
    start
    ;;
  stop)
    stop
    ;;
  restart)
    restart
    ;;
  status)
    status
    ;;
  logs)
    logs
    ;;
  *)
    echo "Use command {start|stop|restart|status|logs} to run."
    exit 1
esac
