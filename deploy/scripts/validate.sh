#!/usr/bin/env bash
# deploy/scripts/validate.sh —— Helm 渲染门（离线可跑）。
# 依次：① 本地依赖 build（仅 file:// 子 chart，不联网）② helm lint ③ helm template
#       ④ kubeconform 校验（CRD 以 -ignore-missing-schemas 放行）。
# 用法： bash deploy/scripts/validate.sh [env=test] [tenantId=demo]
# CI 与 helm-deploy SKILL 共用本脚本。
set -euo pipefail

ENV="${1:-test}"
TENANT="${2:-demo}"

# 定位 deploy/ 根（脚本在 deploy/scripts/ 下）。
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PLATFORM="$DEPLOY_DIR/charts/platform"
TENANT_CHART="$DEPLOY_DIR/charts/tenant"
VALUES="$DEPLOY_DIR/values"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

echo "==> [1/4] helm dependency build（本地 file:// 子 chart，离线）"
# 有 Chart.lock 走 build；lock 缺失/过期则回退 update 重新生成（均不拉远程有状态依赖）。
helm dependency build "$PLATFORM" || helm dependency update "$PLATFORM"

echo "==> [2/4] helm lint"
helm lint "$PLATFORM" -f "$VALUES/values.yaml" -f "$VALUES/values-${ENV}.yaml"
helm lint "$TENANT_CHART" -f "$TENANT_CHART/values.yaml" -f "$VALUES/values-tenant-${TENANT}.yaml"

echo "==> [3/4] helm template（platform base[${ENV}] + tenant-${TENANT}）"
helm template platform "$PLATFORM" \
  -f "$VALUES/values.yaml" -f "$VALUES/values-${ENV}.yaml" > "$OUT/platform.yaml"
helm template "tenant-$TENANT" "$TENANT_CHART" \
  -f "$TENANT_CHART/values.yaml" -f "$VALUES/values-tenant-${TENANT}.yaml" > "$OUT/tenant.yaml"
echo "    platform.yaml: $(grep -c '^kind:' "$OUT/platform.yaml" || true) 个清单 · tenant.yaml: $(grep -c '^kind:' "$OUT/tenant.yaml" || true) 个清单"

echo "==> [4/4] kubeconform"
if command -v kubeconform >/dev/null 2>&1; then
  # -ignore-missing-schemas：放行 ExternalSecret 等 CRD（无内置 schema）。
  kubeconform -strict -summary -ignore-missing-schemas "$OUT/platform.yaml" "$OUT/tenant.yaml"
else
  echo "    ⚠ 未找到 kubeconform，跳过（CI 会安装并强制执行）。"
  echo "      本地安装： go install github.com/yannh/kubeconform/cmd/kubeconform@latest"
fi

echo "==> 渲染门通过 ✅ (env=$ENV tenant=$TENANT)"
