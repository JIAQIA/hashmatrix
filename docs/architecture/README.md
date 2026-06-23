# 系统架构设计（hashmatrix 数据中台）

> 本目录是本数据中台**系统架构设计报告**的雏形（通用技术方案，面向公开）。
>
> - 定位：云原生数据中台增强建设（通用技术方案；甲方标识与原始需求材料等敏感信息仅本地留存、不入公开仓）
> - 范围：全平台 · 五大分系统（数据基础 / 数据治理 / 数据工具(隐私计算) / 数据安全 / 环境升级）
> - 版本：v0.1（草案）　日期：2026-06-17　状态：讨论基线

## 文档导航

| 文档 | 内容 |
|---|---|
| [01-架构总览](./01-架构总览.md) | 分层架构图、组件清单、端到端数据流图 |
| [02-平台治理与网关](./02-平台治理与网关.md) | 服务治理责任归属、请求路径、认证流、Mesh 演进路径 |
| [03-技术选型](./03-技术选型.md) | 标准开源选型菜单、与硬指标对标 |
| [04-工程与部署](./04-工程与部署.md) | Git Submodule 仓库结构、Helm 部署拓扑、信创双轨 |
| [05-多租户与控制平面](./05-多租户与控制平面.md) | 双模产品形态、控制/数据平面、隔离定档、租户开通时序、租户上下文 |
| [06-契约治理](./06-契约治理.md) | 契约类型/生命周期、版本兼容策略、两阶段契约测试(schema-first→CDC)、CI 静态门 |

> 主要结构图均提供**双形态**：`线框图`（纯文本，终端/代码评审/Word/PDF 均可读）+ `Mermaid`（支持渲染的环境显示精细版）。

## 核心设计原则

1. **平台治理为主，应用保持瘦** —— 服务发现/扩缩/自愈/部署交给 K8s，治理能力下沉到平台，不在应用 SDK 里堆服务治理。
2. **少组件，能力下沉** —— 组件越少，故障模式越少，越容易达成 99.99%，也越方便甲方接手源码自维护。
3. **信创双轨** —— 平台自身用标准开源快速迭代；信创刚需收敛到「数据源 Connector 插件」与「Helm values 切换」，支持并行开发。
4. **留好演进口子** —— 现在不付复杂度的钱，将来（东西向治理成刚需时）平滑引入 Service Mesh，应用几乎不动。

## 架构决策记录（ADR 摘要）

| 编号 | 决策点 | 选择 | 主要理由 | 状态 |
|---|---|---|---|---|
| AD-01 | 建设范围 | 全平台 · 五大分系统 | 按整体建设、不可分包 | 已定 |
| AD-02 | 部署形态 | K8s + Helm(umbrella chart)，前期不自研 Operator | 有状态依赖多已有成熟 Operator；Helm 足够 | 已定 |
| AD-03 | 服务治理 | 平台治理为主；**去 Nacos 注册**，服务发现走 K8s Service/DNS | 避免双注册中心反模式；甲方自维护要简单 | 已定 |
| AD-04 | 南北向网关 | **APISIX**（首选），SCG 备选 | 配置驱动+热加载、与发版解耦、高性能扛 1万 QPS | 已定 |
| AD-05 | 统一认证 | **Keycloak (OIDC/OAuth2)**，网关侧校验 | 应用无感；CNCF；JVM 可跑信创 | 已定 |
| AD-06 | 配置管理 | ConfigMap/Secret + Reloader；动态业务规则才上 Nacos(**仅 Config**) | 少组件；Nacos 只担一职不与 K8s 冲突 | 已定 |
| AD-07 | 熔断限流 | **Resilience4j**（应用内） | Hystrix 官方继任者，活跃维护 | 已定 |
| AD-08 | 可观测 | OpenTelemetry + Prometheus/Grafana/Loki + SkyWalking | 标准化、不绑框架 | 已定 |
| AD-09 | 信创策略 | 双轨：开源开发 + Connector SPI 数据源插件 + Helm values 切换 | 平台库用开源；信创点可并行开发 | 已定 |
| AD-10 | 仓库管理 | Git Submodule，按分系统粗粒度（约 8 仓） | 仓库少、契合团队按分系统分工 | 已定 |
| AD-11 | 东西向流量治理 | 后置 Service Mesh（Istio Ambient / Higress） | 真成刚需再上，不提前付复杂度 | 规划 |
| AD-12 | 多租户模型 | C 分层 + Keycloak Organizations 单 realm + schema/db-per-tenant + namespace-per-tenant | 政企 ≤15，控制平面共享、数据平面按租户隔离；品牌部署级 | 已定 |
| AD-13 | 控制平面 | 新增 `control-plane` 子系统，经 Helm SDK + K8s client 命令式开通（不依赖 Git/Argo） | 生产期租户开通不耦合 Git；Argo 仅管共享基座 | 已定 |
| AD-14 | 公共依赖治理 | `libs-java` 出 parent+BOM+starter，发 GitHub Packages，子仓走 Maven 坐标引用 | 只拉子仓也能构建；内网交付镜像到私服 | 已定 |
| AD-15 | 采集边界 | **元数据采集（governance，取结构·旁路）≠ 数据采集（data-foundation，搬数据·主链路）**，共用 Connector SPI + 数据源统一管理 | 连接器只实现一次、数据源引用态共享，产出/SLA/归属不同 | 已定 |
| AD-16 | 元模型引擎 | governance **自研元模型引擎**（Atlas TypeDef 蓝本，Spring Boot+PG+ES）；OM/Atlas/DataHub 仅作采集来源 | 产品要运行期 UI 自定义元模型(继承/基数/作用域)，OM/DataHub 类型代码态不契合；自研利于多租户作用域/信创/甲方自维护 | 已定 |
| AD-17 | 契约治理 | 契约集中主仓 `contracts/`（OpenAPI/AsyncAPI/proto/ICD）；**加法兼容默认+破坏走双跑弃用+自动破坏检测**；契约测试**两阶段 schema-first→CDC(Pact)** | 多服务多语言独立迭代不连坐；先低成本静态门落地，高风险对再上 CDC | 已定 |
| AD-18 | 计算调度后端 | **可插拔双模**：默认 **On K8s**（`namespace-per-tenant`），存量 Hadoop 私有化可部署期切 **On YARN**（`queue-per-tenant`）；抽象 `JobSubmitter` 提交层 + `computeBackend: k8s\|yarn` Helm 切换；**引擎自带、YARN 仅借作资源管理器、存储统一对象存储**；提交/编排复用 DolphinScheduler(批)/StreamPark·Kyuubi 等开源，不手搓 YARN RPC | 复用存量算力降落地成本；仅 Spark/Flink 临时计算落 YARN、其余栈不动；queue 级隔离弱于 namespace 须明示不等价；对甲方收敛为受支持矩阵（Hadoop 3.x/专用 queue/单服务主体 keytab/对象存储可达） | 规划（M1 预留抽象 · 后置实装） |
| AD-19 | BI/数据可视化范围 | **移出建设范围**：报表/自助分析/数据大屏（对标 DataArts Insight）不由本系统承载，归**上层独立产品**（DataEase 类）经数据服务对接；删除 `tools-bi` 子系统/仓 | BI 是治理**之上**的数据消费层、与治理非同层；私有化客户多自带 BI、平台只供数；保持治理平台边界纯粹 | 已定 |
