#!/usr/bin/env bash
# ============================================================
# YUNHE 部署脚本（服务器端执行）
#
# 用途：首次部署 / 升级重装 统一入口。
#   - 首次：建运行用户、检查 JDK/MySQL、拷 jar、生成 .env、装 systemd、拷 jar
#   - 升级：覆盖 jar、重启服务（其余幂等）
#   ※ 不含 MySQL 初始化——建库/建账号由你在服务器手动完成（脚本只检查服务在跑）。
#
# 用法（在服务器上解压后，进入解压目录以 root 执行）：
#   本机先构建部署包：  mvn clean package -Pdeploy -DskipTests
#   产物: target/yunhe-website-<版本>-deploy.tar.gz
#   拷到服务器解压：    tar -xzf yunhe-...-deploy.tar.gz   （得到 install.sh 等 + jar，平铺）
#   进入目录执行：      sudo bash ./install.sh
#
# 前置（首次需手动做一次）：
#   1) 服务器已装 JDK 21（java -version 验证）
#   2) 服务器已装并启动 MySQL 8，且已建好库与业务账号（见 install 末尾说明）
# ============================================================
set -euo pipefail

APP_NAME="yunhe"
APP_USER="yunhe"
APP_DIR="/opt/${APP_NAME}"
SERVICE="yunhe-app"                    # systemd 服务名（不带 .service）
SERVICE_FILE="${SERVICE}.service"     # unit 文件名
UNIT_FILE="/etc/systemd/system/${SERVICE_FILE}"
JAR_NAME="yunhe-website.jar"
PORT="${SERVER_PORT:-8080}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="${SCRIPT_DIR}"

# 与本地约定一致的默认 jar 查找位置（本 deploy/ 同目录 / 脚本上级 target/）
JAR_SRC=""
for cand in "${DEPLOY_DIR}/${JAR_NAME}" "${DEPLOY_DIR}/../target/${JAR_NAME}" "${DEPLOY_DIR}/../target/"*.jar; do
  if [ -f "${cand}" ]; then JAR_SRC="${cand}"; break; fi
done

echo "============================================================"
echo " YUNHE 部署 — 目标 ${APP_DIR}，服务 ${SERVICE}"
echo " 部署文件目录: ${DEPLOY_DIR}"
echo "============================================================"

# ---------- 0) root 检查 ----------
if [ "$(id -u)" != "0" ]; then
  echo "✗ 请以 root 运行：sudo ./install.sh" >&2; exit 1
fi

# 兜底：tar 解压可能丢失脚本可执行位，确保同目录脚本可执行
chmod +x "${DEPLOY_DIR}/"*.sh 2>/dev/null || true

# ---------- 1) JDK 检查 ----------
echo ""
echo "==> [1/5] 检查 JDK 21 ..."
if ! command -v java >/dev/null 2>&1; then
  echo "✗ 未找到 java。请先安装 JDK21：" >&2
  echo "    Ubuntu/Debian: sudo apt install -y openjdk-21-jre-headless" >&2
  echo "    或 Alibaba Cloud Linux/CentOS: sudo yum install -y java-21-openjdk-headless" >&2
  exit 1
fi
JAVA_VER="$(java -version 2>&1 | head -1 | sed 's/.*version "\([^"]*\)".*/\1/')"
echo "   java: ${JAVA_VER}"
case "${JAVA_VER}" in
  21*|22*|23*|24*) echo "   ✓ 版本满足 (>=21)" ;;
  *) echo "   ⚠ 当前 ${JAVA_VER}，建议 21+（低于 17 可能无法运行）" ;;
esac

# ---------- 2) MySQL 服务检查 ----------
echo ""
echo "==> [2/5] 检查本机 MySQL 服务 ..."
mysql_svc=""
for s in mysql mysqld mariadb; do
  if systemctl is-active --quiet "${s}" 2>/dev/null; then mysql_svc="${s}"; break; fi
done
if [ -z "${mysql_svc}" ]; then
  echo "✗ 未检测到运行中的 MySQL/MariaDB 服务。" >&2
  echo "   请先启动本机 MySQL 服务：" >&2
  echo "   Ubuntu: sudo systemctl enable --now mysql" >&2
  echo "   Alibaba: sudo systemctl enable --now mysqld" >&2
  echo "   （若尚未安装，请先安装 MySQL 8：apt install -y mysql-server 或 yum install -y mysql-server）" >&2
  exit 1
fi
if ! command -v mysql >/dev/null 2>&1; then
  echo "✗ 未找到 mysql 客户端命令。" >&2; exit 1
fi
echo "   ✓ MySQL 服务运行中 (${mysql_svc})"

# ---------- 3) jar 检查 / 定位 ----------
echo ""
echo "==> [3/5] 定位应用 jar ..."
if [ -z "${JAR_SRC}" ]; then
  echo "✗ 未找到 jar。请先在本机 mvn clean package，并把 jar 放到本 deploy/ 目录（命名 ${JAR_NAME}）" >&2
  echo "   或放入上级 target/ 目录。" >&2
  exit 1
fi
echo "   jar 源: ${JAR_SRC}"

# ---------- 4) 建用户 & 目录，拷 jar & 生成 .env ----------
echo ""
echo "==> [4/5] 准备运行环境（用户/目录/env）..."
if ! id "${APP_USER}" >/dev/null 2>&1; then
  useradd --system --home-dir "${APP_DIR}" --shell /usr/sbin/nologin "${APP_USER}"
  echo "   已创建系统用户: ${APP_USER}"
else
  echo "   用户已存在: ${APP_USER}"
fi
mkdir -p "${APP_DIR}"
# 拷 jar（保留校验与权限）
install -o "${APP_USER}" -g "${APP_USER}" -m 0644 "${JAR_SRC}" "${APP_DIR}/${JAR_NAME}"
echo "   ✓ jar → ${APP_DIR}/${JAR_NAME} ($(du -h "${APP_DIR}/${JAR_NAME}" | cut -f1))"

# .env：优先用已有（升级不覆盖），无则从 deploy/.env 或模板生成
ENV_FILE="${APP_DIR}/.env"
if [ -f "${ENV_FILE}" ]; then
  echo "   ✓ 复用已有 .env: ${ENV_FILE}"
  # 把本 deploy/.env（若有手动携带）合并不覆盖现有
  if [ -f "${DEPLOY_DIR}/.env" ]; then
    echo "   → 检测到 deploy/.env，将合并缺失键（已有值不被覆盖）"
    cp "${ENV_FILE}" "${ENV_FILE}.bak"
    # 只补不存在于目标文件的键
    while IFS='=' read -r k v; do
      [ -z "${k}" ] && continue
      case "${k}" in \#*) continue;; esac
      if ! grep -q "^${k}=" "${ENV_FILE}"; then
        printf '%s=%s\n' "${k}" "${v}" >> "${ENV_FILE}"
      fi
    done < "${DEPLOY_DIR}/.env"
  fi
else
  SRC_ENV=""
  for c in "${DEPLOY_DIR}/.env" "${DEPLOY_DIR}/.env.example"; do
    [ -f "${c}" ] && SRC_ENV="${c}" && break
  done
  if [ -z "${SRC_ENV}" ]; then
    echo "✗ 无 .env 模板（.env.example 缺失）" >&2; exit 1
  fi
  cp "${SRC_ENV}" "${ENV_FILE}"
  chown "${APP_USER}:${APP_USER}" "${ENV_FILE}"
  echo "   ✓ 已从 ${SRC_ENV##*/} 生成 ${ENV_FILE}（请检查 DB_PASSWORD 等字段！）"
fi
chmod 0600 "${ENV_FILE}"
chown "${APP_USER}:${APP_USER}" "${ENV_FILE}"

# ---------- 5) 安装 systemd 服务 ----------
echo ""
echo "==> [5/5] 安装 systemd 服务 ${SERVICE} ..."
# 占位符替换生成 unit
sed -e "s|__APP_DIR__|${APP_DIR}|g" \
    -e "s|__APP_USER__|${APP_USER}|g" \
    "${DEPLOY_DIR}/${SERVICE_FILE}" > "${UNIT_FILE}"
systemctl daemon-reload
systemctl enable "${SERVICE}" >/dev/null 2>&1 || true
echo "   ✓ 已安装并启用 ${SERVICE}（开机自启）"

echo ""
echo "============================================================"
echo " 部署文件已就绪。接下来："
echo " 1) 确认 MySQL 已建好库与账号，且 ${APP_DIR}/.env 中 DB_URL/DB_USERNAME/DB_PASSWORD 正确"
echo "    （建库建账号需你手动在 MySQL root 下执行，脚本不再代劳，见 README-部署.md）"
echo " 2) 启动:  ${DEPLOY_DIR}/start.sh"
echo " 3) 首次建管理员账号（若 SEED_ADMIN 未开）见 README-部署.md 播种说明"
echo "============================================================"
