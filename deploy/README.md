# deploy · 部署运维

Helm 部署与运维能力封装（主仓职责）。关联 HashMatrixData/hashmatrix#2；设计见 `../docs/00-主仓初始化-spec.md` §4 与架构 `../docs/architecture/04-工程与部署.md` / `05-多租户与控制平面.md`。

## 结构

```
deploy/
├─ charts/
│  ├─ platform/          # umbrella：共享基座（聚合各分系统子 chart + 钉版本有状态依赖）
│  ├─ tenant/            # 租户开通单元（namespace/quota/netpol/ESO/schema·db/服务实例）
│  └─ <subsystem>/       # 各分系统子 chart（gateway 等 8 个，占位先行，由对应 submodule 充实）
├─ values/
│  ├─ values.yaml        # 共享 profile（跨环境通用）
│  ├─ values-{prod,test,xc}.yaml   # 环境覆盖（xc = 信创）
│  └─ values-tenant-<id>.yaml      # per-tenant 模板（demo 为示例）
├─ scripts/
│  ├─ validate.sh        # 渲染门：dep build + lint + template + kubeconform
│  ├─ secrets-guard.sh   # 红线守卫：无明文 Secret 入库
│  └─ tenant-diff.sh     # 单租户 release 渲染/diff
└─ argocd/               # （可选）平台基座 app-of-apps —— 仅管共享基座，租户 provision 不走 Argo
```

## 控制/数据平面分治（核心）

| | chart | 谁来应用 | 生命周期 |
|--|--|--|--|
| 共享基座 | `charts/platform` | 命令式 helm / 可选 Argo CD | 长生命周期 |
| 租户开通 | `charts/tenant` | `control-plane`（Helm SDK，**不依赖 Git/Argo**） | 按租户开通/回收 |

## values 分层

```
charts/<chart>/values.yaml   # BASE（安全默认：子 chart / 有状态依赖全关）
      ▼ 叠加
values/values.yaml           # 共享 profile
      ▼ 叠加
values/values-{prod,test,xc}.yaml   # 环境覆盖（xc 信创：切镜像/参数/资源，不改应用逻辑）
values/values-tenant-<id>.yaml      # per-tenant 实例
```

## 本地校验（离线）

```bash
bash deploy/scripts/validate.sh test demo   # 渲染门：基座(test) + tenant-demo
bash deploy/scripts/secrets-guard.sh        # 红线守卫
```

- 远程有状态依赖（Kafka/Doris/Milvus/MinIO/PG/Redis/ES）在 `charts/platform/Chart.yaml` 中**钉版本但注释**，保证 CI 离线；落地某环境时取消注释 + `helm dependency update` 并在 `values-<env>.yaml` 开启。
- 调试/部署/发布走 **`helm-deploy` SKILL**（`.claude/skills/helm-deploy`）。

## 红线

- **Secrets 绝不入库**：一律经 External Secrets Operator 注入；`.gitignore` 挡明文 `*secret*.yaml`（否定放行无明文的 ESO `ExternalSecret`/`SecretStore` CR）。
- 部署前 `secrets-guard.sh` 守卫；CI（`.github/workflows/deploy-ci.yml`）强制执行红线 + 渲染门。
