# deploy/charts/governance ——  chart（占位）

> **状态：占位（placeholder）**。`version: 0.0.0-placeholder`，尚不可部署。
> 关联：HashMatrixData/hashmatrix#2（部署运维封装）。

## 定位

主仓 umbrella chart `platform` 下的 **governance** 子 chart。占位阶段仅声明聚合关系，
真实部署模板（Deployment/Service/HPA/Probe 等）由对应 submodule `services/governance` 贡献后充实。

- 事实源：`services/governance`（Java）。
- 聚合方式：umbrella `platform/Chart.yaml` 以 `file://../governance` 声明依赖，按 `governance.enabled` 条件渲染（默认关闭）。
- 分环境差异（prod/test/信创 xc）经 umbrella 的 `values-<env>.yaml` 注入，本子 chart 不内置环境耦合。

## 待落地清单（TODO）

- [ ] services/governance 贡献真实 `templates/`（无状态 Deployment + HPA + Probe）
- [ ] `image.repository/tag` 经部署级 values 注入（固定 tag，不用 latest）
- [ ] 接入 umbrella 分环境 values 与 per-tenant 渲染（数据平面 namespace-per-tenant）
