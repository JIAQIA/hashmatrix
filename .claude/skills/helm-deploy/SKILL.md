---
name: helm-deploy
description: 调试·部署·发布 hashmatrix Helm 制品（deploy/）。lint/template/kubeconform 渲染门 → 部署到 test/prod/信创(xc)（切 values）→ 渲染并调试单租户 per-tenant release → 发布（chart 打包 / 推 OCI registry）。支持 dry-run/diff/回滚；红线守卫 Secrets 走 ESO 不入库。当用户要求部署、渲染、diff、发布 Helm chart 或调试某租户开通时使用。
argument-hint: "[env=test|prod|xc] 或 tenant <id>  ·  --diff/--dry-run/--apply/--rollback/--publish"
---

# helm-deploy · Helm 调试/部署/发布工作流

把 `deploy/` 的 umbrella 基座与 per-tenant 开通单元从渲染调试一路带到部署与发布。**硬门控**：任何部署前必过①红线守卫②渲染门；**默认 dry-run**，`--apply` 才落地。

> 配套：`deploy/scripts/validate.sh`（渲染门）、`secrets-guard.sh`（红线）、`tenant-diff.sh`（单租户 diff）。两个 chart：`charts/platform`（共享基座）、`charts/tenant`（租户开通单元）。

## Step 1: 明确目标

- **基座**：`env ∈ {test, prod, xc}`（xc=信创）。values：`deploy/values/values.yaml` + `values-<env>.yaml`。
- **租户**：`tenant <id>`。values：`charts/tenant/values.yaml` + `deploy/values/values-tenant-<id>.yaml`（缺失则先按 `values-tenant-demo.yaml` 生成）。
- 确认目标集群上下文（`kubectl config current-context`）与 namespace；模糊处**先问再做**。

## Step 2: 红线守卫（硬门控）⭐

```bash
bash deploy/scripts/secrets-guard.sh
```

- **非零退出即停**：`deploy/` 下不得有明文 Secret 实体（`kind: Secret` + data/stringData，或可疑 `*secret*.yaml`）。
- Secrets 一律经 **External Secrets Operator** 注入；ESO 的 `ExternalSecret`/`SecretStore`（仅引用外部路径、无明文）可入库。

## Step 3: 渲染门（硬门控）⭐

```bash
bash deploy/scripts/validate.sh <env> <tenantId>      # 默认 test demo
```

依次：① `helm dependency build`（仅本地 file:// 子 chart，离线）② `helm lint` ③ `helm template`（基座 + tenant）④ `kubeconform`（CRD 以 `-ignore-missing-schemas` 放行）。**任一步失败即停**。

## Step 4: dry-run / diff

```bash
# 基座
helm diff upgrade --install platform deploy/charts/platform \
  -f deploy/values/values.yaml -f deploy/values/values-<env>.yaml --namespace <ns>
# 单租户（无插件/集群时自动回退 helm template 纯渲染）
bash deploy/scripts/tenant-diff.sh <id>
```

- 需 `helm-diff` 插件（`helm plugin install https://github.com/databus23/helm-diff`）。**先看 diff 再 apply**。

## Step 5: 部署（默认 dry-run，`--apply` 落地）

```bash
# 基座（长生命周期，可选 Argo 托管；此处为命令式备选）
helm upgrade --install platform deploy/charts/platform \
  -f deploy/values/values.yaml -f deploy/values/values-<env>.yaml \
  --namespace <ns> --create-namespace [--dry-run]
# 单租户开通（生产由 control-plane 经 Helm SDK 等价执行，不依赖 Git/Argo）
helm upgrade --install tenant-<id> deploy/charts/tenant \
  -f deploy/charts/tenant/values.yaml -f deploy/values/values-tenant-<id>.yaml \
  --namespace tenant-<id> --create-namespace [--dry-run]
```

- **回滚**：`helm rollback <release> [revision]`；查历史 `helm history <release>`。
- **xc（信创）**：仅切 `values-xc.yaml`（镜像/参数/资源），**不改应用逻辑**。

## Step 6: 发布（chart 打包 / 推 OCI）

```bash
helm package deploy/charts/platform deploy/charts/tenant -d dist/
helm push dist/platform-<ver>.tgz oci://<registry>/hashmatrix-charts   # 凭据经环境注入，绝不入库
```

- 版本：改 `Chart.yaml` `version`（语义化）。OCI registry 地址/凭据**经环境变量注入，不入库**（红线）。
- 内网/信创：镜像同步到内网 registry（与 libs-java 私服同思路）。

## Step 7: 收尾

- 汇总：目标（env/tenant）、渲染门结果、diff 摘要、是否 apply/回滚、（如适用）发布制品。
- 若源于 Issue，PR/说明可关联对应 Issue（如 `Closes #2`）。
- **不在汇总/提交信息中带客户/凭据/真实主机信息**（红线）。
