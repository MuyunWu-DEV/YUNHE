#!/usr/bin/env bash
# ============================================================
# YUNHE 一键部署到阿里云 ECS (Docker)
#
# 流程：本地 mvn package → scp jar + deploy/ 到 ECS → 远端 docker compose up
#
# 前置：
#   1) 本机可连 ECS（ssh/scp 免密或输口令）
#   2) ECS 已装 Docker + docker compose 插件（详见 README-部署.md）
#   3) 首次需先在 ECS 上 cp deploy/.env.example .env 并填好口令
#
# 用法：
#   ./deploy.sh                  # 使用下方默认 ECS_HOST/路径
#   ECS_HOST=root@1.2.3.4 ./deploy.sh
# ============================================================
set -euo pipefail

# ---------- 可配置项（可被环境变量覆盖） ----------
ECS_HOST="${ECS_HOST:-root@YOUR_ECS_IP}"     # 例: root@47.98.xx.xx
REMOTE_DIR="${REMOTE_DIR:-/opt/yunhe}"       # ECS 上项目部署根目录
REMOTE_DEPLOY_DIR="${REMOTE_DIR}/deploy"     # compose/配置文件所在子目录
SKIP_BUILD="${SKIP_BUILD:-0}"                # =1 跳过本地 mvn package（例如已在 IDEA 里打包）
PROFILE_OPT="${PROFILE_OPT:--DskipTests}"    # mvn 参数，默认跳过测试
# Windows 本机常见 Maven/JDK（改成本机的即可）。有 JAVA_HOME 时脚本优先用系统 mvn。
MVN_CMD="${MVN_CMD:-}"                        # 显式指定 mvn/mvn.cmd 绝对路径时用（如 C:/Muyun/Java/apache-maven-3.9.12/bin/mvn.cmd）

# ---------- 定位脚本目录 & 项目根 ----------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "==> 项目根: ${PROJECT_ROOT}"
echo "==> 目标 ECS: ${ECS_HOST}:${REMOTE_DIR}"

# ---------- 0) 前置检查 ----------
if ! command -v ssh >/dev/null 2>&1 || ! command -v scp >/dev/null 2>&1; then
  echo "✗ 未找到 ssh/scp，请确认 Git Bash / OpenSSH 可用" >&2; exit 1
fi

# 解析出可用的 Maven 命令
resolve_mvn() {
  if [ -n "${MVN_CMD}" ] && [ -x "${MVN_CMD}" ]; then echo "${MVN_CMD}"; return; fi
  if [ "${SKIP_BUILD}" = "1" ]; then echo ""; return; fi
  # 1) 系统 mvn / mvn.cmd
  if command -v mvn.cmd >/dev/null 2>&1; then echo "$(command -v mvn.cmd)"; return; fi
  if command -v mvn >/dev/null 2>&1; then echo "$(command -v mvn)"; return; fi
  # 2) maven-wrapper（项目自带，会按 .mvn/wrapper 下载）
  if [ -x "${PROJECT_ROOT}/mvnw.cmd" ]; then echo "${PROJECT_ROOT}/mvnw.cmd"; return; fi
  if [ -x "${PROJECT_ROOT}/mvnw" ]; then echo "${PROJECT_ROOT}/mvnw"; return; fi
  echo ""
}
MVN="$(resolve_mvn)"
if [ "${SKIP_BUILD}" != "1" ] && [ -z "${MVN}" ]; then
  echo "✗ 找不到 Maven。请设置 MVN_CMD=绝对路径，或在 IDEA 里 package 后用 SKIP_BUILD=1" >&2; exit 1
fi

# ---------- 1) 本地打包 ----------
cd "${PROJECT_ROOT}"
if [ "${SKIP_BUILD}" != "1" ]; then
  echo "==> [1/4] 本地 Maven 打包: ${MVN} clean package ${PROFILE_OPT}"
  # Windows 下 mvn.cmd 需要 JAVA_HOME 指向完整 JDK（仅含 javapath launcher 会失败）
  if [ -z "${JAVA_HOME:-}" ] && [ -d "C:/Program Files/Java/jdk-21" ]; then
    export JAVA_HOME="C:\\Program Files\\Java\\jdk-21"
    echo "     已自动设置 JAVA_HOME=${JAVA_HOME}"
  fi
  "${MVN}" clean package ${PROFILE_OPT}
else
  echo "==> [1/4] 跳过本地构建 (SKIP_BUILD=1，使用已有 target jar)"
fi

JAR="$(ls -t target/*.jar 2>/dev/null | head -1 || true)"
if [ -z "${JAR}" ]; then
  echo "✗ 未在 target/ 找到 jar，请确认打包成功" >&2; exit 1
fi
echo "     jar: ${JAR}"

# ---------- 2) 创建远端目录并同步文件 ----------
echo "==> [2/4] 同步部署文件到 ECS ..."
ssh "${ECS_HOST}" "mkdir -p '${REMOTE_DIR}' '${REMOTE_DEPLOY_DIR}'"
# 同步 deploy/ 下除 .env 之外的文件（.env 只在服务器手填，防本机覆盖服务器口令）
scp -r "${SCRIPT_DIR}/Dockerfile" \
       "${SCRIPT_DIR}/docker-compose.yml" \
       "${SCRIPT_DIR}/.env.example" \
       "${ECS_HOST}:${REMOTE_DEPLOY_DIR}/"
# jar 放远端 context 根（之后会复制进其 target/）
scp "${JAR}" "${ECS_HOST}:${REMOTE_DIR}/yunhe-website.jar"

# ---------- 3) 远端检查 .env ----------
echo "==> [3/4] 检查远端 .env ..."
ENV_EXISTS=$(ssh "${ECS_HOST}" "test -f '${REMOTE_DEPLOY_DIR}/.env' && echo yes || echo no")
if [ "${ENV_EXISTS}" != "yes" ]; then
  echo "⚠ 远端尚无 ${REMOTE_DEPLOY_DIR}/.env，已从模板生成（口令为占位，务必 ssh 上去改！）"
  ssh "${ECS_HOST}" "cp '${REMOTE_DEPLOY_DIR}/.env.example' '${REMOTE_DEPLOY_DIR}/.env'"
  echo "    请在 ECS 执行:  vi ${REMOTE_DEPLOY_DIR}/.env  然后填入真实口令，再重跑本脚本"
  exit 1
fi

# ---------- 4) 远端 Docker Compose 构建并启动 ----------
echo "==> [4/4] 远端 docker compose up -d --build ..."
ssh "${ECS_HOST}" bash -s <<EOF
set -e
# jar 就位到 compose build.context(=REMOTE_DIR) 下的 target/，供 Dockerfile COPY
mkdir -p "${REMOTE_DIR}/target"
cp -f "${REMOTE_DIR}/yunhe-website.jar" "${REMOTE_DIR}/target/yunhe-website.jar"
cd "${REMOTE_DEPLOY_DIR}"
docker compose up -d --build
echo "✔ compose 已启动。"
docker compose ps
EOF

echo ""
echo "============================================================"
echo " 部署完成！访问:  http://$(echo ${ECS_HOST} | cut -d@ -f2):8080"
echo " 日志:  docker compose -f ${REMOTE_DEPLOY_DIR}/docker-compose.yml logs -f app"
echo " 首次若登录后台无 admin，见 README-部署.md「初始化账号」"
echo "============================================================"
