# deploy/charts/platform —— 共享基座 umbrella chart

> 关联：HashMatrixData/hashmatrix#2（Helm 部署治理）。设计见主仓 `docs/00-主仓初始化-spec.md` §4 与架构 `docs/architecture/05-多租户与控制平面.md`。

## 定位（控制/数据平面分治）

本 chart 只管 **长生命周期共享基座**（`gateway` / `app` / `data` / `observability`）——可由 Argo CD 可选 GitOps。
**租户数据平面**（namespace-per-tenant / quota / netpol / ESO / 服务实例）**不在此**，见 [`../tenant`](../tenant)，
由 `control-plane` 经 Helm SDK 按租户渲染应用（**provision 不走 Argo**）。

```
共享基座（本 chart, 长生命周期）        租户数据平面（charts/tenant, 控制平面按需开通）
  ns: gateway/app/data/observability      ns: tenant-<id> + quota/netpol/ESO/服务实例
```

## 聚合关系

| 类别 | 来源 | 装配方式 |
|--|--|--|
| 分系统子 chart（8 个） | `deploy/charts/<subsystem>`（本地） | `Chart.yaml` 以 `file://../<name>` 声明依赖，按 `<name>.enabled` 条件渲染。占位阶段默认全关。 |
| 有状态依赖（PG/Kafka/Doris/Milvus/MinIO/Redis/ES） | 社区 Operator/chart（远程） | `Chart.yaml` 中**钉版本但注释**（保持 CI 离线）；落地时取消注释 + `helm dependency update`，并在 `values-<env>.yaml` 置 `<name>.enabled=true`。 |

> 远程有状态依赖为何注释而非启用：避免 CI `helm dependency build` 拉多 GB 外部 chart（沿用 `charts/gateway` 对 `apisix` 的同款做法），保证渲染门离线可跑。版本号为占位，启用前核对社区稳定版。

## values 分层

```
charts/platform/values.yaml          # BASE：安全默认（全部 enabled=false）
        ▼ 叠加
deploy/values/values.yaml            # 共享 profile（跨环境通用）
        ▼ 叠加
deploy/values/values-{prod,test,xc}.yaml   # 环境覆盖（xc = 信创：切镜像/参数/资源，不改应用逻辑）
```

## 本地校验（离线）

```bash
helm dependency build deploy/charts/platform          # 仅本地 file:// 依赖，离线可跑
helm lint deploy/charts/platform -f deploy/values/values.yaml -f deploy/values/values-test.yaml
helm template platform deploy/charts/platform -f deploy/values/values.yaml -f deploy/values/values-test.yaml
# 或一键： bash deploy/scripts/validate.sh
```

> **Chart.lock 维护**：`Chart.lock` 入库、vendored `charts/*.tgz` 不入库（`.gitignore` 挡）。
> 改动任一子 chart 的 `version`（如占位 `0.0.0-placeholder` → 真实版本）后，须 `helm dependency update deploy/charts/platform`
> 重生成 `Chart.lock` 并提交，否则 `helm dependency build` 会因 digest 不匹配而回退 `update`（`validate.sh` 已兜底，但 lock 会静默漂移）。

## 待落地清单（TODO）

- [ ] 各 submodule 贡献真实子 chart 模板后，在对应 `values-<env>.yaml` 置 `<name>.enabled=true`
- [ ] 按环境取消 `Chart.yaml` 中有状态依赖注释并 `helm dependency update` 钉版本
- [ ] （可选）`deploy/argocd/` app-of-apps 仅纳管本基座（租户 provision 不纳入）
