# hashmatrix · 数据中台（数据治理平台）

云原生数据中台主仓（super-project）。采用 **Git Submodule** 管理各分系统/服务，主仓负责**公共依赖**与**部署运维能力**封装。

- 技术栈：Java + TypeScript（隐私计算等子项目含 Python）
- 部署：Kubernetes + Helm（umbrella chart）
- 架构设计见 [`docs/architecture/`](./docs/architecture/README.md)

> 除 `webui` 选型已定稿外，后端各子项目的**详细技术选型仍在逐个讨论中**；下表为架构首选骨架。

## 产品形态与多租户（北极星）

**双模交付**：公网 SaaS（我们运营 · 统一**我们品牌** · 租户=企业）／私有化部署（客户环境 · **客户品牌**部署级 · 租户=客户部门）。品牌**部署级**、不按租户运行期换肤。多租户走 **C 分层桥接**：控制平面共享 + 数据平面按租户隔离（Keycloak Organizations 单 realm · schema/db-per-tenant · namespace-per-tenant），由 `control-plane` 编排开通。

**本仓视角**：主仓承载全局模型——控制平面治理 + 公共依赖（`starter-tenant` 租户上下文）+ 部署（per-tenant Helm release）。

> 详见 [`docs/00-主仓初始化-spec.md`](./docs/00-主仓初始化-spec.md)、[`docs/architecture/05-多租户与控制平面.md`](./docs/architecture/05-多租户与控制平面.md)。

## 子系统角色与骨架一览（一眼看懂）

| 子仓 | 层 / 位置 | 职责（一句话） | 骨架技术选型（首选） |
|--|--|--|--|
| [`webui`](./services/webui) | 接入层 · 唯一前端仓 | **同仓双 app**：console（使用平面：控制台/大屏/画布）+ admin（管理平面：运营控制台） | React 19 · Vite · AntD v6 · AntV（**已定稿**） |
| [`gateway`](./services/gateway) | 南北向网关 | 路由/限流/鉴权/审计 · 注入租户头 | APISIX · Keycloak OIDC |
| [`governance`](./services/governance) | 应用层 · 数据治理 | 元数据/元模型引擎/血缘/质量/数据标准 | Spring Boot · 自研元模型引擎(Atlas 蓝本) · PostgreSQL · ES |
| [`security`](./services/security) | 应用层 · 数据安全 | 分类分级/标签/审批/审计 | Spring Boot · Flowable · Prometheus |
| [`privacy`](./services/privacy) | 应用层 · 隐私计算 | MPC/PSI/匿踪/节点互联 | SecretFlow(Python) · Java 编排 |
| [`data-foundation`](./services/data-foundation) | 接入+计算+存储 · 数据底座 | 流批采集/湖仓/计算/Connector SPI | Flink · Kafka · SeaTunnel · Paimon · Doris · Milvus |
| [`platform-common`](./services/platform-common) | 横切 · 平台公共 | 调度/工作流/统一元数据 | Spring Boot · DolphinScheduler · Flowable |
| [`control-plane`](./services/control-plane) | 横切 · 控制平面 | 租户注册/开通/生命周期/配额 | Spring Boot · Helm SDK · K8s client · Keycloak Admin |

**主仓自身（super-project）**：`deploy/`（Helm umbrella + values 分层 env/信创/tenant + per-tenant release，Secrets 走 External Secrets Operator）· `libs-java/`（parent + BOM + `starter-*`，发布 GitHub Packages）· `contracts/`（OpenAPI/protobuf 接口契约）· `docs/`（架构与研制文档）。

> 后端各仓为**架构首选**，详细选型逐仓独立讨论细化；每个子仓 README 均含「角色与位置 / 职责与边界 / 骨架技术选型」。

## 仓库结构

```
hashmatrix/                     # 主仓：公共依赖 + 部署运维
├── deploy/                     # Helm umbrella chart + values(env/信创/tenant) + per-tenant release
├── libs-java/                  # 公共 Java parent + BOM + starter（含 starter-tenant）
├── libs-ts/                    # 公共 TS 组件库/SDK
├── contracts/                  # ICD：OpenAPI/protobuf 接口契约
├── docs/                       # 架构与研制文档（敏感材料不入库）
└── services/                   # ↓ 各为独立 git submodule
    ├── webui/                  → hashmatrix-webui            接入层 · 唯一前端仓(双app:console+admin) · TS
    ├── gateway/                → hashmatrix-gateway          南北向网关 · APISIX
    ├── governance/             → hashmatrix-governance       数据治理分系统 · Java
    ├── security/               → hashmatrix-security         数据安全分系统 · Java
    ├── privacy/                → hashmatrix-privacy          隐私计算 · Python+Java
    ├── data-foundation/        → hashmatrix-data-foundation  数据基础(采集/计算/湖仓)
    ├── platform-common/        → hashmatrix-platform-common  平台公共(调度/工作流/元数据)
    └── control-plane/          → hashmatrix-control-plane     多租户控制平面(开通/隔离/配额)
```

## 克隆（含子模块）

```bash
git clone --recurse-submodules git@github.com:HashMatrixData/hashmatrix.git
# 已克隆主仓后补拉子模块：
git submodule update --init --recursive
```

## 子模块协作约定

- 子模块按**分系统粗粒度**拆分，独立开发、独立发版。
- 主仓只记录各子模块的**提交指针（SHA）**；推进子模块版本时，在主仓提交更新后的指针。
- 接口契约统一放主仓 `contracts/`（ICD），各子模块据此对接。

## License

[Apache-2.0](./LICENSE)
