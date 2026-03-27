#!/usr/bin/env bash

sbinDir=$(cd "$(dirname "$0")"; pwd)
source $sbinDir/supersonic-common.sh
source $sbinDir/supersonic-env.sh

function loadProjectEnv {
  local project_env="$projectDir/.env"
  if [ -f "$project_env" ]; then
    set -a
    source "$project_env"
    set +a
  fi
}

loadProjectEnv

command=$1
service=$2
profile=$3

if [ -z "$service"  ]; then
  service=${STANDALONE_SERVICE}
fi

if [ -z "$profile" ]; then
  profile=${S2_DB_TYPE}
fi

model_name=$service
cd $baseDir
server_port=${S2_SERVER_PORT:-9080}
instance_id=${S2_INSTANCE_ID:-}
resolved_runtime_dir=$baseDir
service_log_dir=
jvm_opts=

function setMainClass {
  if [ "$service" == $CHAT_SERVICE ]; then
    main_class="com.tencent.supersonic.ChatLauncher"
  elif [ "$service" == $HEADLESS_SERVICE ]; then
    main_class="com.tencent.supersonic.HeadlessLauncher"
  else
    main_class="com.tencent.supersonic.StandaloneLauncher"
  fi
}

function setAppName {
  if [ "$service" == $CHAT_SERVICE ]; then
    app_name=$CHAT_APP_NAME
  elif [ "$service" == $HEADLESS_SERVICE ]; then
    app_name=$HEADLESS_APP_NAME
  else
    app_name=$STANDALONE_APP_NAME
  fi
  if [ -n "$instance_id" ]; then
    app_name="${app_name}_${instance_id}"
  fi
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

function runJavaService {
  javaRunDir=$resolved_runtime_dir
  local_app_name=$1
  libDir=$baseDir/lib
  confDir=$baseDir/conf

  if ! ensureRuntimeLayout; then
    return 1
  fi

  javaRunDir=$resolved_runtime_dir
  libDir=$javaRunDir/lib
  confDir=$javaRunDir/conf

  CLASSPATH=""
  CLASSPATH=$CLASSPATH:$confDir
  if [ -f "$libDir/common-1.0.0-SNAPSHOT.jar" ]; then
    CLASSPATH=$CLASSPATH:$libDir/common-1.0.0-SNAPSHOT.jar
  fi

  for jarPath in $libDir/*.jar; do
   if [ "$jarPath" = "$libDir/common-1.0.0-SNAPSHOT.jar" ]; then
     continue
   fi
   CLASSPATH=$CLASSPATH:$jarPath
  done

  export CLASSPATH
  export LANG="zh_CN.UTF-8"

  cd $javaRunDir
  if [[ "$JAVA_HOME" == "" ]]; then
    JAVA_HOME=$(ls /usr/jdk64/jdk* -d 2>/dev/null | xargs | awk '{print "'$local_app_name'"}')
  fi
  export PATH=$JAVA_HOME/bin:$PATH
  jvm_opts=${S2_JAVA_OPTS:-"-Xms1024m -Xmx2048m -XX:+UseZGC -XX:+ZGenerational"}
  command="-Dfile.encoding=UTF-8 -Duser.language=Zh -Duser.region=CN -Duser.timezone=GMT+08
  -Dapp_name=${local_app_name} -Dserver.port=${server_port} ${jvm_opts} $main_class"

  service_log_dir=${S2_LOG_DIR:-$javaRunDir/logs/$local_app_name}
  mkdir -p "$service_log_dir"
  java -Dspring.profiles.active="$profile" $command >/dev/null 2>"$service_log_dir/error.log" &
  service_pid=$!
}

function start() {
  local_app_name=$1
  echo "Starting ${local_app_name} on port ${server_port}"
  pid=$(ps aux | grep ${local_app_name} | grep -v grep | awk '{print $2}')
  if [[ "$pid" == "" ]]; then
    if ! runJavaService ${local_app_name}; then
      echo "Start failed during runtime preparation."
      return 1
    fi
    pid=$service_pid
    for _ in 1 2 3 4 5; do
      if ! kill -0 "$pid" 2>/dev/null; then
        echo "Start failed, see ${service_log_dir}/error.log"
        return 1
      fi
      sleep 1
    done
  else
    echo "Process (PID = $pid) is running."
    return 1
  fi
  echo "Start success"
  echo "Target URL: http://localhost:${server_port}"
}

function stop() {
  echo "Stopping $1"
  pid=$(ps aux | grep $1 | grep -v grep | awk '{print $2}')
  if [[ "$pid" == "" ]]; then
    echo "Process $1 is not running!"
    return 1
  else
    kill -9 $pid
    echo "Process (PID = $pid) is killed!"
    return 0
  fi
  echo "Stop success"
}

setMainClass
setAppName
case "$command" in
  start)
    start ${app_name}
    ;;
  stop)
    stop $app_name
    ;;
  restart)
    stop ${app_name}
    start ${app_name}
    ;;
  *)
    echo "Use command {start|stop|restart} to run."
    exit 1
esac
