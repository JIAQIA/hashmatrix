# hashmatrix 主仓初始化 Spec

> 状态：**已确认**（经 `/interview` 逐项敲定，重点为多租户）。据此把多租户架构沉淀进 `docs/architecture/05-*` 并初始化主仓工程化。
> 红线：本文件公开开源，仅含通用技术方案与产品决策，**不含任何甲方信息**。

## 0. 主仓职责（super-project）

主仓 `hashmatrix` 不放业务源码，承担**横切治理**四件事：① 多租户**控制平面**；② 公共依赖治理（Java BOM/parent）；③ 部署运维治理（Helm）；④ 契约（`contracts/`）与研制文档（`docs/`）。

---

## 1. 双模产品形态（贯穿全局的前提）

平台是**双模交付**，"租户"语义随模式而变，但**隔离机制同一套**：

| 模式 | 运营方 | 品牌 | "租户"= | 隔离单元 |
|------|--------|------|---------|---------|
| **公网 SaaS** | 我们 | **我们公司统一品牌**（多租户共享界面风格） | 一个企业客户 | 企业 |
| **私有化部署** | 客户自有环境 | **客户品牌**（部署内各部门统一用该品牌） | 客户的部门 | 部门 |

**品牌是部署级（deployment-level），不是按租户运行期换肤** —— 与 webui spec 的「构建期默认（我们品牌）+ 运行期 `config.js` 覆盖（私有化换客户品牌）」**完全一致**。故身份/前端**不需要 per-tenant 动态换肤**，login 品牌部署级统一。

---

## 2. 多租户架构（重点，已定）

### 2.1 控制平面 vs 数据平面

- **控制平面**：跨租户单例，**新增 `services/control-plane`（Java）独立子系统**。负责租户注册、开通编排（provision）、生命周期、配额、租户目录。与 `platform-common`（运行期共享：调度/工作流/元数据）**职责分离**。
- **数据平面**：每租户一份隔离的服务实例 + 数据存储，由控制平面按需开通。

### 2.2 隔离维度定档（已选）

| 维度 | 定档 | 说明 |
|------|------|------|
| **供给模型** | **C 分层/Bridge** | 控制平面共享 + 数据平面按租户隔离；档位可调 |
| **身份** | **Keycloak Organizations 单 realm** | org=租户（公网=企业 / 私有化=部门）；JWT 带 tenant 声明；企业可按 org 联邦自有 AD；个别强隔离租户可升独立 realm（escape hatch） |
| **数据** | **schema/db-per-tenant** | 平台业务库 PG 按租户 schema/db；数据仓 Doris / 湖 Paimon 按租户 catalog/database |
| **计算** | **namespace-per-tenant** | 每租户独立 namespace + ResourceQuota/LimitRange + NetworkPolicy；监管/大客户可升 vCluster |
| **网络** | 命名空间 NetworkPolicy | Kyverno/Cilium 策略，命名空间隔离 |
| **规模** | 个位数~十几个政企大客户 | ≤15 量级；偏 siloed 桥接成本可控；架构预留向数十演进 |

### 2.3 控制平面如何落地开通（命令式，不依赖 Git）

> **控制平面是一等 K8s 编排者**：经 **Helm（CLI/SDK 包装）+ Kubernetes Java client** 直接渲染并应用 per-tenant release。**生产期租户开通不耦合 Git/Argo**；Argo CD 仅作平台自身基座的可选 GitOps，非租户 provision 必需。

```
租户注册(自助) → 控制平面校验 + 审批门控 → 通过后:
  ① Keycloak Admin API：建 Organization + 租户管理员（+可选联邦客户 AD）
  ② 控制平面经 Helm SDK + K8s client 编排 per-tenant release：
       · namespace + ResourceQuota/LimitRange + NetworkPolicy
       · schema/db(PG) + Doris/Paimon catalog/db
       · 拉起该租户服务实例
       · External Secrets Operator 注入租户 secrets
  ③ 回写租户目录(状态/配额/接入信息)
  ④ 租户管理员登录，纳管自有资源
```

### 2.4 租户上下文 + 配额

- **上下文透传**：Keycloak JWT 带 `tenant`(org) 声明 → 网关注入 `X-Tenant-*` 头 → 各 Java 服务由 `starter-tenant` 统一取用并下推到数据访问层（schema/catalog 路由 + 行级兜底过滤）。
- **配额**：K8s ResourceQuota + 业务配额（用户数/数据量/作业数）**硬限**；metering 计费**仅预留接口**（政企合同制，不按量计费）。

---

## 3. Java 依赖治理（BOM/parent · 回答"只拉子仓能否装依赖"）

`libs-java/`（主仓，非 submodule）维护 **parent POM + BOM + 公共 starter** 源码；CI 构建后**发布为带版本制品到 Maven 仓库**。子仓经 **Maven 坐标**引用（`<parent>` + `import` BOM），**不依赖 submodule 路径**——故工程师**只拉子仓也能 `mvn` 构建**，只要能访问该 Maven 仓库。

```
hashmatrix-platform-parent (pom)   # 统一 Java 版本/插件/编译/质量门/profile(开源·信创)
hashmatrix-bom            (pom)     # dependencyManagement：Spring Boot 家族 + 公共库版本钉死
hashmatrix-starter-*      (jar)     # 公共能力：日志/审计/鉴权/Web 基座 + starter-tenant(多租户上下文)
```

- **制品仓库 = GitHub Packages**（开发期便利、免自建）。
- ⚠️ **内网/信创交付 caveat**：内网无公网，须把 GitHub Packages 的制品**镜像同步到内网 Maven 私服（Nexus/Artifactory）**，子仓在内网指向私服。此镜像同步流程列入交付清单。
- 版本演进：主仓 CI 发布 BOM `x.y.z` → 子仓 pin，升级 = 改一行版本号。

---

## 4. Helm 部署治理

`deploy/` 维护 **umbrella chart + 子 chart + values 分层**，规范如下：

- **结构**：umbrella `Chart.yaml` 聚合各服务子 chart；有状态依赖（Kafka/Doris/Milvus/MinIO/PG/Redis/ES）**优先社区 Operator/chart 作为 Chart 依赖**并钉版本。
- **values 分层**：`values.yaml`(base) + `values-<env>.yaml`(prod/test/**信创**) + `values-tenant-<id>.yaml`(per-tenant) + secrets 外置。
- **多租户部署单元**：每租户一个 Helm release（参数化 namespace/资源/数据存储），**由控制平面经 Helm SDK 渲染应用**（见 2.3）。
- **Secrets**：**External Secrets Operator** 从外部 Vault/KMS 注入，**不入库**（红线）；`.gitignore` 挡 `*secret*.yaml`/`.env`。
- **GitOps**：Argo CD **可选**，仅管平台自身基座（gateway/data/observability 等长生命周期组件）；**租户 provision 不走 Argo**。
- **CI 门**：helm lint + template + kubeconform/kubeval（+ chart-testing 可选）。
- **信创双轨**：`values-信创.yaml` 切镜像/参数/资源，不改应用逻辑（呼应架构 04 §3）。

---

## 5. 主仓目录结构（在现有脚手架上充实）

```
hashmatrix/
├─ deploy/              # Helm umbrella + 子 chart + values(env/信创/tenant) + (可选)argocd 基座
│  ├─ charts/           #   各服务子 chart + 基础设施依赖声明
│  ├─ values/           #   values-{prod,test,信创}.yaml + tenant 模板
│  └─ argocd/           #   (可选) 平台基座 app-of-apps
├─ libs-java/           # platform-parent(pom) + bom(pom) + starter-*（发布 GitHub Packages）
├─ libs-ts/             # 公共前端组件库/SDK（webui 复用）
├─ contracts/           # ICD：OpenAPI/proto + 控制平面/租户开通 API 契约
├─ docs/                # 架构与研制文档（多租户落 architecture/05-*）
└─ services/            # 各 git submodule
   └─ control-plane/    # 【新增】租户注册/开通/生命周期/配额（Helm SDK + K8s client 直管）
```

---

## 6. 决策记录（关键取舍）

| # | 决策 | 选定 | 理由 |
|---|------|------|------|
| 形态 | 产品模式 | **双模**：公网 SaaS（我们品牌）+ 私有化（客户品牌，部门为子租户） | 品牌部署级，与 webui config.js 一致 |
| M1 | 规模 | 个位数~十几个政企大客户（≤15） | 偏 siloed 桥接成本可控，预留演进 |
| M2 | 供给模型 | **C 分层/Bridge** | 控制平面共享 + 数据平面按租户隔离 |
| M3 | 数据隔离 | **schema/db-per-tenant** | 隔离/成本平衡点 |
| M4 | 计算隔离 | **namespace-per-tenant** | 呼应"开通一套服务"，可升 vCluster |
| M5 | 身份 | **Keycloak Organizations 单 realm** | login 品牌部署级统一，realm-per-tenant 优势已消解 |
| M6 | 控制平面归属 | **新 `services/control-plane`** | 控制面/运行面职责分离 |
| M7 | 开通自动化 | **自助注册 → 审批 → 自动 provision** | 兼顾政企合同门槛与自动化 |
| M8 | 计量配额 | **仅配额 + 预留计量** | 合同制非按量 |
| 落地 | 开通执行 | **控制平面 Helm SDK + K8s client 命令式**（不依赖 Git/Argo） | 生产开通不耦合 Git |
| E1 | Java 制品仓 | **GitHub Packages**（内网需镜像到私服） | 开发期免自建；交付期镜像同步 |
| E3 | Secrets | **External Secrets Operator** | 不入库，多环境/租户友好 |

---

## 7. 落定后执行项

1. 沉淀 `docs/architecture/05-多租户与控制平面.md`（线框图 + Mermaid，与 01–04 同风格）：双模形态、控制/数据平面、隔离定档、开通时序、租户上下文。
2. 新建 `services/control-plane` 子模块（GitHub 建仓 + 加 submodule + CLAUDE.md + README + LICENSE）。
3. `libs-java/`：platform-parent + bom + starter-tenant 骨架；GitHub Packages 发布流程 + 内网镜像同步说明。
4. `deploy/`：umbrella chart 骨架 + values 分层 + per-tenant 模板 + ESO 接入；CI helm lint/template。
5. `contracts/`：控制平面 / 租户开通 API 契约（OpenAPI）。
6. 更新主仓 README/CLAUDE.md；提交推送；记忆落档。
7. （建议）按 webui 模式，为以上建 GitHub 初始化 Issue，派给工程师执行。
