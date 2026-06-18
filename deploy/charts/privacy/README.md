# charts/privacy · 隐私计算子 chart（占位）

双工作负载骨架：

- `engine-py` —— Python 计算引擎（SecretFlow / PSI），`/healthz` 探针
- `orchestrator-java` —— Java 编排层，`/actuator/health/readiness` 探针，经 service 名访问引擎

> 占位 chart（`version: 0.0.0-placeholder`）：镜像仓库/标签随 privacy 子仓 CI 发布填充。
> 多租户按 **namespace-per-tenant** 由 `control-plane` 经 Helm SDK 渲染 per-tenant release
> （见架构 05 / spec §2）。实现源在 submodule `services/privacy`。

## 聚合方式

umbrella chart `platform` 以 `file://../privacy` 声明依赖，按 `privacy.enabled` 条件渲染（默认关闭）；
分环境差异（prod/test/信创 xc）经 umbrella 的 `values-<env>.yaml` 注入，本子 chart 不内置环境耦合。

```bash
helm template demo deploy/charts/privacy        # 本地渲染校验
```
