#!/usr/bin/env bash
# deploy/scripts/secrets-guard.sh —— 红线守卫：deploy/ 下不得出现含明文的 Secret 清单。
# 规则：① 文件名形如 *secret*.yaml 但**不是** ESO 的 ExternalSecret/SecretStore（后者无明文，可入库）；
#       ② 任意 yaml 含 `kind: Secret` 且带 `data:`/`stringData:`（明文/base64 实体）。
# 非零退出即阻断（CI 与发布前共用）。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
fail=0

# ① 可疑文件名（排除 ESO CR：externalsecret/secretstore/clustersecretstore）。
while IFS= read -r f; do
  base="$(basename "$f")"
  case "$base" in
    externalsecret*.yaml|externalsecret*.yml|secretstore*.yaml|secretstore*.yml|clustersecretstore*.yaml|clustersecretstore*.yml)
      ;;  # ESO CR：无明文，放行
    *)
      echo "❌ 可疑 secret 文件名（疑似明文实体，红线不入库）： $f"
      fail=1
      ;;
  esac
done < <(find "$DEPLOY_DIR" -type f \( -iname '*secret*.yaml' -o -iname '*secret*.yml' \) 2>/dev/null || true)

# ② 内容含 kind: Secret + data/stringData。
#    正则允许 kind 前缩进（多文档片段），并要求 Secret 后为行尾/注释——
#    故 `kind: Secret` 命中，而 `kind: SecretStore` / `kind: ExternalSecret`（"Secret" 非行尾）不误报。
while IFS= read -r f; do
  if grep -Eq '^[[:space:]]*kind:[[:space:]]*Secret[[:space:]]*(#.*)?$' "$f" 2>/dev/null \
     && grep -Eq '^[[:space:]]*(data|stringData):' "$f" 2>/dev/null; then
    echo "❌ 检出明文 Secret 实体（kind: Secret + data/stringData）： $f"
    fail=1
  fi
done < <(find "$DEPLOY_DIR" -type f \( -iname '*.yaml' -o -iname '*.yml' \) 2>/dev/null || true)

# ③ ESO 白名单文件（ExternalSecret/SecretStore）二次核：不得出现内联明文（stringData / data[].value）。
#    ESO 正道是 remoteRef 引用外部 store；内联 value 即明文，红线拦截。
while IFS= read -r f; do
  if grep -Eq '^[[:space:]]*(stringData:|value:|valueMap:)' "$f" 2>/dev/null; then
    echo "❌ ESO 清单含内联明文（stringData/value），应改用 remoteRef 引用外部 store： $f"
    fail=1
  fi
done < <(find "$DEPLOY_DIR" -type f \( -iname 'externalsecret*.y*ml' -o -iname '*secretstore*.y*ml' \) 2>/dev/null || true)

if [ "$fail" -ne 0 ]; then
  echo "—— 红线：Secret 一律经 External Secrets Operator 注入，禁止明文入库（见 CLAUDE.md / 架构 05）。"
  exit 1
fi
echo "✅ secrets-guard 通过：deploy/ 下无明文 Secret 实体。"
