# contracts · 接口契约（ICD）

跨子系统的**接口契约单一事实源**。多服务架构下，契约是各子系统**独立迭代而不互相破坏**的边界——**先改契约、再改实现**。

> 规范（文档格式 / 版本兼容 / 评审门 / 契约测试）见 [`CONVENTIONS.md`](./CONVENTIONS.md)；
> 设计与决策见架构 [`06-契约治理`](../docs/architecture/06-契约治理.md)。

## 目录结构

| 目录 | 契约类型 | 格式 | 静态门 |
|--|--|--|--|
| [`openapi/`](./openapi) | REST 接口（南北向 + 东西向） | OpenAPI 3.1 | Spectral lint · oasdiff 破坏性 |
| [`asyncapi/`](./asyncapi) | 异步事件（Kafka 等） | AsyncAPI 2.6 + JSON Schema | Spectral lint · schema 向后兼容 |
| [`proto/`](./proto) | gRPC / 内部 RPC | protobuf | buf lint · buf breaking |
| [`icd/`](./icd) | 跨切面**线契约**（非单服务，如租户头/错误信封） | Markdown(+schema) | 评审 + 示例 |
| `templates/` | 各类契约模板（新建契约从此拷贝） | — | — |

## 契约索引（registry）

| 契约 | 类型 | producer | status |
|--|--|--|--|
| [`icd/tenant-context-headers`](./icd/tenant-context-headers-icd.md) | ICD | gateway | draft |
| [`icd/governance-metadata`](./icd/governance-metadata-icd.md) | ICD | governance | draft |
| [`icd/control-plane-provisioning`](./icd/control-plane-provisioning-icd.md) | ICD | control-plane | draft |
| [`openapi/control-plane-v1`](./openapi/control-plane-v1.yaml) | OpenAPI | control-plane | draft |
| [`openapi/governance-metadata-v1`](./openapi/governance-metadata-v1.yaml) | OpenAPI | governance | draft |
| [`openapi/privacy-orchestrator-v1`](./openapi/privacy-orchestrator-v1.yaml) | OpenAPI | privacy | draft |
| [`openapi/privacy-psi-v1`](./openapi/privacy-psi-v1.yaml) | OpenAPI | privacy | draft |
| [`asyncapi/governance-metadata`](./asyncapi/governance-metadata.yaml) | AsyncAPI | governance | draft |
| [`proto/privacy-psi`](./proto/privacy/v1/psi.proto) | proto | privacy | draft |

> 机器可读索引见 [`registry.yaml`](./registry.yaml)（producers/consumers 全量）；新增契约务必同步登记。

## 怎样新增 / 变更契约

1. 从 `templates/` 拷贝对应模板，填 front-matter（`id/owner/status/version/producers/consumers`）。
2. 放入对应类型目录；在上方 registry 登记。
3. 开 PR——CI 跑静态门（lint + 破坏性检测）；契约变更需 **CODEOWNERS 评审**。
4. 合并后，producer/consumer 各自仓 CI 据此验证实现（契约测试，见 CONVENTIONS §测试）。
5. **破坏性变更**：升 MAJOR + 弃用期双跑 + 通知全部 consumers（见 CONVENTIONS §版本兼容）。

## 子项目如何查阅契约

各子仓经 `CLAUDE.md`「契约」块感知 + 实时拉取本仓（不存副本）。标准块、各仓 producer/consumer 映射、传播方式见 [`integration.md`](./integration.md)（规范见 [`CONVENTIONS.md`](./CONVENTIONS.md) §7）。

## 🔴 红线

契约为公开内容：不得含甲方信息、真实主机/IP、凭据；示例数据一律脱敏（`acme` / `tenant-demo` / `example.com`）。
