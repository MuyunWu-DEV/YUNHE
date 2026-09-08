#!/usr/bin/env bash
# ============================================================
# YUNHE 停止脚本（服务器端）
# 停止应用服务 yunhe-app。
#
# 用法（root，或 sudo）：
#   ./stop.sh
#   ./stop.sh --disable     # 额外禁止开机自启（很少用）
# ============================================================
set -euo pipefail

SERVICE="yunhe-app"

if [ "$(id -u)" != "0" ]; then
  echo "✗ 请以 root 运行：sudo ./stop.sh" >&2; exit 1
fi

if [ ! -f "/etc/systemd/system/${SERVICE}.service" ]; then
  echo "⚠ 服务未安装，无需停止。" >&2
  exit 0
fi

echo "==> 停止 ${SERVICE} ..."
if systemctl is-active --quiet "${SERVICE}"; then
  systemctl stop "${SERVICE}"
  echo "✓ 服务已停止。"
else
  echo "✓ 服务本来就没在运行。"
fi

# 可选：禁止开机自启
if [ "${1:-}" = "--disable" ]; then
  systemctl disable "${SERVICE}" >/dev/null 2>&1 || true
  echo "✓ 已禁止开机自启（仅本次临时停止用 ./stop.sh 即可）。"
fi
