# deploy · 部署运维

Helm 部署与运维能力封装（主仓职责）。

- `charts/` —— umbrella chart + 各子模块子 chart（待补）
- `envs/` —— 分环境 values：`values-prod.yaml` / `values-test.yaml` / `values-xc.yaml`（信创）
- 有状态依赖（Kafka/Doris/Milvus/MinIO/PG…）优先复用社区 Operator，主仓前期不自研 Operator。

> 当前为占位，部署拓扑见 [`../docs/architecture/04-工程与部署.md`](../docs/architecture/04-工程与部署.md)。
