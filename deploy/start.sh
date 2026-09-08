#!/usr/bin/env bash
# ============================================================
# YUNHE 启动脚本（服务器端）
# 启动应用服务 yunhe-app。若尚未部署先引导执行 install.sh。
# 幂等：已运行则提示；环境变量 .env 缺失则引导。
#
# 用法（root，或 sudo）：
#   ./start.sh
# ============================================================
set -euo pipefail

APP_USER="yunhe"
APP_DIR="/opt/${APP_USER}"
SERVICE="yunhe-app"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ---------- root 检查 ----------
if [ "$(id -u)" != "0" ]; then
  echo "✗ 请以 root 运行：sudo ./start.sh" >&2; exit 1
fi

# ---------- 是否已安装 ----------
if [ ! -f "/etc/systemd/system/${SERVICE}.service" ]; then
  echo "⚠ 尚未安装服务。请先在 deploy/ 目录执行:  sudo ./install.sh" >&2
  exit 1
fi
if [ ! -f "${APP_DIR}/.env" ]; then
  echo "⚠ 缺少 ${APP_DIR}/.env。请先运行:  sudo ./install.sh" >&2
  exit 1
fi

# ---------- 启动 ----------
echo "==> 启动 ${SERVICE} ..."
if systemctl is-active --quiet "${SERVICE}"; then
  echo "✓ 服务已在运行。如需重启:  sudo systemctl restart ${SERVICE}"
else
  systemctl start "${SERVICE}"
  echo "✓ 已发送启动指令。"
fi

# ---------- 等待并反馈 ----------
echo "==> 等待健康检查 (最多 60s)..."
for i in $(seq 1 60); do
  if systemctl is-active --quiet "${SERVICE}"; then
    # 通过日志确认 Spring 启动完成
    if journalctl -u "${SERVICE}" --no-pager -n 200 2>/dev/null | grep -qE "Started .*Application|Tomcat started on port"; then
      echo "✓ 应用启动完成！"
      break
    fi
  else
    echo "✗ 服务启动失败。查看日志:" >&2
    journalctl -u "${SERVICE}" -e --no-pager -n 60 >&2
    exit 1
  fi
  sleep 1
done

PORT="$(grep -E '^SERVER_PORT=' "${APP_DIR}/.env" 2>/dev/null | cut -d= -f2- || echo 8080)"
PORT="${PORT:-8080}"
echo ""
echo "============================================================"
echo " 应用:  http://<服务器IP>:${PORT}   (官网首页)"
echo " 后台:  http://<服务器IP>:${PORT}/login"
echo " 日志:  sudo journalctl -u ${SERVICE} -f"
echo " 停止:  ${SCRIPT_DIR}/stop.sh"
echo "============================================================"
