# deploy/argocd —— 平台基座 app-of-apps（占位 · 可选）

> **状态：占位（placeholder）**。关联 HashMatrixData/hashmatrix#2。

## 定位与边界（重要）

Argo CD 在本平台**仅作可选 GitOps**，且**只纳管共享基座**（`charts/platform`：gateway / data / observability 等长生命周期组件）。

🔴 **租户 provision 不走 Argo**：租户开通（`charts/tenant`）由 `services/control-plane` 经 **Helm SDK + K8s client** 命令式执行，生产期**不依赖 Git/Argo**（见架构 `05-多租户与控制平面.md` §2）。

```
Argo CD（可选）   ── app-of-apps ──▶  共享基座 charts/platform（长生命周期）
control-plane     ── Helm SDK ─────▶  租户单元 charts/tenant（命令式，按需开通，不经 Argo）
```

## 待落地清单（TODO）

- [ ] `app-of-apps.yaml`：根 Application 指向本目录子 app（仅 platform 基座）
- [ ] 各基座组件 `Application`（source=本仓 `deploy/charts/platform`，destination=目标集群/ns）
- [ ] sync policy / project / RBAC（与命令式租户开通**严格分离**，避免 Argo 误纳管 tenant-* namespace）

> 占位阶段不提供可应用的 Argo 清单；启用前需先确认基座/租户的纳管边界。
