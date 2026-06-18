---
id: icd/governance-metadata
owner: hashmatrix-governance
status: draft
version: 0.1.0
producers: [hashmatrix-governance]
consumers: [hashmatrix-tools-bi, hashmatrix-security, hashmatrix-webui, hashmatrix-data-foundation, hashmatrix-platform-common]
since: 2026-06-18
---

# ICD · governance 元数据供数与变更事件

> 状态：**草案（待评审）**。本文登记 governance 对外的跨子系统接口轮廓与边界；字段级 schema 已细化落地：
> - 同步供数 REST → [`openapi/governance-metadata-v1.yaml`](../openapi/governance-metadata-v1.yaml)（本文 §2）
> - 异步变更事件 → [`asyncapi/governance-metadata.yaml`](../asyncapi/governance-metadata.yaml)（本文 §3）
>
> 本 ICD 保留接口**轮廓 + 边界（DG3/DG4）**作为设计事实源；端点/字段/事件 payload 以上述 schema 为权威。
> 来源：评审数据治理产品原型时，原型已列出对外供数 API 与变更事件（见原型 `openApis` / `webhooks`）。

## 1. 背景

`governance`（数据治理）不是孤立 UI，而是平台的**元数据供数中枢**：对外提供**同步供数 API**，并对外发**异步元数据变更事件**，被资产门户 / BI(`tools-bi`) / 数据质量 / 集成调度 / 数据服务等多个子系统消费。这些是跨子系统契约，须在本 ICD 统一登记、先改契约再改实现。

## 2. 同步供数 API（REST · OpenAPI 3.1）

> 字段级 schema 见 [`openapi/governance-metadata-v1.yaml`](../openapi/governance-metadata-v1.yaml)；下表为接口轮廓与消费方映射。
> 边界（DG3）：**元数据只出索引 + API**，门户/BI 作为独立消费方调用，不直连 governance 内部库。
> 鉴权：服务账号 + 凭证（经网关 APISIX 校验），调用方携带租户上下文（`X-Tenant-*`），返回结果按租户隔离。

| 接口 | 方法 | 用途 | 主要消费方 |
|--|--|--|--|
| `/api/meta/search` | GET | 元数据检索 / 分面 | 资产门户、数据服务 |
| `/api/meta/detail/{id}` | GET | 资产详情聚合（含字段、归属、标签） | 资产门户 |
| `/api/meta/lineage/{id}` | GET | 血缘 / 影响分析（表级 / 字段级） | 资产门户、BI、质量 |
| `/api/perm/elements/{id}` | GET | 权限要素（用于安全中心判定，**透传不裁决**，DG4） | security / 资产门户 |
| `/api/perm/status` | GET | 权限状态查询 | 资产门户 |
| `/api/open/query` | POST | 开放查询（统一供数） | 集成 / 质量 / BI / 数据服务 |

> 注：`/api/perm/*` 仅做**权限要素与状态透传**；分类分级策略与审批归 `security`（DG4/DG6）。

## 3. 异步元数据变更事件（事件驱动）

> 事件 payload schema 见 [`asyncapi/governance-metadata.yaml`](../asyncapi/governance-metadata.yaml)；下表为事件轮廓与订阅方映射。

| 事件 | 触发 | 主要订阅方 | 用途 |
|--|--|--|--|
| `metadata.model.changed` | 元模型结构变更（发布新版本 / 改属性·关系） | 集成调度、BI | 下游同步模型结构 |
| `metadata.collect.anomaly` | 采集异动（批量删表 / 结构突变 / 字段类型冲突） | BI、质量、告警 | 异动感知与整改 |
| `metadata.instance.changed` | 实例入库 / 认领 / 安全标签回写 | 质量、数据服务 | 资产状态联动 |

**传输方式（建议）**：以 **Kafka topic 为主干**（`hashmatrix.governance.metadata.*`，已在 data-foundation 提供 Kafka）；对需要 HTTP 推送的外部/第三方消费方，由网关或一个轻量 **webhook 桥接**从 topic 投递（幂等 + 重试）。**决策待评审确认**：纯 Kafka / Kafka + webhook 桥接。

## 4. 契约约定

- 事件 payload 与 API schema 统一在本目录维护（`openapi/` REST、`proto/` 或 Avro/JSON-Schema 事件）。
- 字段级敏感信息按 `security` 的分类分级标签裁剪后再出 API / 事件。
- 所有接口与事件均**租户感知**：携带 `tenant` 维度，跨租户默认不可见。

## 5. 待办

- [x] 细化各 API 的请求/响应 schema → [`openapi/governance-metadata-v1.yaml`](../openapi/governance-metadata-v1.yaml)
- [x] 定事件 payload schema 与 topic 命名规范 → [`asyncapi/governance-metadata.yaml`](../asyncapi/governance-metadata.yaml)（topic = `hashmatrix.governance.metadata.*`）
- [ ] 确认事件传输方式（纯 Kafka vs Kafka+webhook 桥接）
- [ ] 与 `security`（权限要素/标签）、`tools-bi`（取数/订阅）、数据服务 对齐消费契约
