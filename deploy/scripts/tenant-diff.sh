#!/usr/bin/env bash
# deploy/scripts/tenant-diff.sh —— 渲染/对集群 diff 单租户 release（控制平面开通前的调试入口）。
# 用法： bash deploy/scripts/tenant-diff.sh <tenantId> [namespace]
#   · 有 helm-diff 插件且能连集群 → 输出 upgrade --install 的 diff（dry-run，不落地）
#   · 否则回退为 helm template（纯渲染）
set -euo pipefail

TENANT="${1:?用法： tenant-diff.sh <tenantId> [namespace]}"
NS="${2:-tenant-$TENANT}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TENANT_CHART="$DEPLOY_DIR/charts/tenant"
VALUES_FILE="$DEPLOY_DIR/values/values-tenant-${TENANT}.yaml"

if [ ! -f "$VALUES_FILE" ]; then
  echo "❌ 缺少租户模板： ${VALUES_FILE}（先按 values-tenant-demo.yaml 生成）"; exit 1
fi

# --namespace 设 helm 目标 ns；同时 --set tenant.namespace 让渲染出的资源落点与之一致
# （否则资源 namespace 由 chart 内 tenant.namespace 决定，与传入 [namespace] 不符）。
ARGS=( "tenant-$TENANT" "$TENANT_CHART"
       -f "$TENANT_CHART/values.yaml" -f "$VALUES_FILE"
       --namespace "$NS" --set "tenant.namespace=$NS" )

if helm plugin list 2>/dev/null | grep -qi '^diff' && kubectl cluster-info >/dev/null 2>&1; then
  echo "==> helm diff upgrade（dry-run，不落地）： tenant=$TENANT ns=$NS"
  helm diff upgrade --install --allow-unreleased "${ARGS[@]}"
else
  echo "==> 无 helm-diff 插件或集群不可达，回退 helm template： tenant=$TENANT ns=$NS"
  helm template "${ARGS[@]}"
fi
