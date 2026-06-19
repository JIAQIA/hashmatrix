#!/usr/bin/env bash
# deploy/scripts/sync-gateway-config.sh —— 把 gateway 配置/插件事实源同步进本 chart 的 vendored files/。
#
# 为什么需要：配置/插件的事实源(SoT)在 submodule services/gateway；但 Helm 打包/渲染只能读 chart 根内的文件
#   （.Files.Get 受限于 chart 目录），故 gateway chart 保留一份**同步副本**于 deploy/charts/gateway/files/。
#   env-specific 的 apisix.yaml（OIDC discovery / 上游）由 chart 模板按 values 渲染，**不在此同步**（保留集群版）。
#
# 用法：
#   bash deploy/scripts/sync-gateway-config.sh            # 同步 SoT -> chart files/（写入）
#   bash deploy/scripts/sync-gateway-config.sh --check    # 只校验副本与 SoT 是否一致（CI 用；漂移即非零退出，不写）
set -euo pipefail

MODE="${1:-sync}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SRC="$ROOT/services/gateway"
DST="$ROOT/deploy/charts/gateway/files"

if [ ! -f "$SRC/apisix/config.yaml" ]; then
  echo "❌ 找不到事实源 $SRC/apisix/config.yaml —— 是否已 git submodule update --init services/gateway？"
  exit 1
fi

# SoT -> 副本 路径对（apisix.yaml 走 chart 模板渲染，不在此列）。
PAIRS=(
  "$SRC/apisix/config.yaml:$DST/config.yaml"
  "$SRC/plugins/tenant-context.lua:$DST/plugins/tenant-context.lua"
  "$SRC/plugins/audit-log.lua:$DST/plugins/audit-log.lua"
)

if [ "$MODE" = "--check" ]; then
  fail=0
  for pair in "${PAIRS[@]}"; do
    src="${pair%%:*}"; dst="${pair##*:}"
    if ! diff -q "$src" "$dst" >/dev/null 2>&1; then
      echo "❌ 漂移：$dst 与 SoT $src 不一致"
      fail=1
    fi
  done
  if [ "$fail" -ne 0 ]; then
    echo "—— vendored 副本与 submodule SoT 漂移；请跑 bash deploy/scripts/sync-gateway-config.sh 重新同步并提交。"
    exit 1
  fi
  echo "✅ vendored 副本与 SoT 一致。"
  exit 0
fi

# 同步模式：写入副本。
mkdir -p "$DST/plugins"
for pair in "${PAIRS[@]}"; do
  cp "${pair%%:*}" "${pair##*:}"
done
echo "✅ synced gateway config/plugins SoT -> $DST"
git -C "$ROOT" diff --stat -- deploy/charts/gateway/files || true
