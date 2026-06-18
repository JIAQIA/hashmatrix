# 契约规范（CONVENTIONS）

本文件规定 `contracts/` 下所有契约的**文档格式、版本兼容策略、评审门与契约测试**。目标：**各子系统独立迭代、互不破坏**。设计背景见架构 [`06-契约治理`](../docs/architecture/06-契约治理.md)。

## 1. 文档格式

每份契约（含 ICD、OpenAPI、AsyncAPI、proto 的伴随说明）顶部带统一 **YAML front-matter**：

```yaml
---
id: <type>/<name>            # 如 openapi/governance-metadata、icd/tenant-context-headers
owner: <repo 或团队>          # 责任方（CODEOWNERS 对齐）
status: draft                # draft → review → stable → deprecated
version: 0.1.0               # 契约 semver（独立于实现/制品版本）
producers: [<repo>, ...]     # 谁实现/产生
consumers: [<repo>, ...]     # 谁依赖/消费
since: 2026-06-18            # 绝对日期
supersedes: <id@version>     # 可选：替代的旧契约
---
```

- 机器可读的 schema（OpenAPI/AsyncAPI/proto）以**自身文件**为准；front-matter 写在伴随 README 或文件内 `info`/注释。
- 命名：`kebab-case`；REST 一服务一文件 `openapi/<service>.yaml`；事件 `asyncapi/<domain>.yaml`；RPC `proto/<service>/v<major>/*.proto`。

## 2. 状态机

`draft`（征求意见，可大改）→ `review`（评审中，接口冻结待确认）→ `stable`（生产承诺，破坏须走弃用流程）→ `deprecated`（弃用期，给定移除窗口）。

## 3. 版本与兼容策略（独立迭代的核心）

契约走 **semver**，与实现/制品版本解耦：

| 变更 | 级别 | 示例 |
|--|--|--|
| 文档/澄清，无线上影响 | PATCH | 补注释、改措辞 |
| **加法兼容** | MINOR | 新增可选字段、新端点、新事件类型、放宽校验 |
| **破坏性** | MAJOR | 删/改字段、改类型、改语义、收紧必需性、改头名/路由 |

- **默认只允许加法兼容**；消费方一律 **tolerant reader**（忽略未知字段、不依赖顺序）。
- **破坏性变更**必须：① 升 MAJOR；② **弃用期双跑**（producer 同时支持新旧一个窗口）；③ 在 PR 与 registry 标注 `deprecated` + 移除日期；④ 通知全部 consumers（各 producer 仓 Issue/PR 关联）。
- REST 破坏走 URI 版本（`/v2`）或 media-type 版本；事件 envelope 带 `schemaVersion`；RPC 走 proto package 版本（`v1`→`v2`）。

## 4. 评审门（CODEOWNERS）

- `contracts/**` 变更需 **CODEOWNERS 评审**（在 `.github/CODEOWNERS` 把 `contracts/` 指给契约负责人/各 producer 仓负责人；本仓暂以 PR 评审落实，团队 handle 落定后补 CODEOWNERS）。
- 评审关注：兼容性级别是否正确标注、consumers 是否已知会、是否脱敏、是否登记 registry。

## 5. 契约测试（两阶段）

> 决策：**先 schema-first 立即落地，CDC 后续按需引入**（架构 06）。

### 阶段一 · schema-first（现行）
1. **静态门**（主仓 CI `contracts-ci`，对契约变更跑）：
   - OpenAPI/AsyncAPI → **Spectral** lint（`.spectral.yaml` 规则集）。
   - proto → **buf** `lint` + `breaking`（对基线分支检测破坏）。
   - OpenAPI → **oasdiff** breaking（对基线版本，破坏即 fail，除非 PR 显式 major bump）。
2. **生产者验证**（各 producer 仓 CI）：实现须符合自己的契约——REST 用 schemathesis / rest-assured + openapi-validator；事件用 JSON Schema 校验样例与序列化。
3. **消费者验证**（各 consumer 仓 CI）：从契约**生成客户端/ stub**（openapi-generator / asyncapi codegen / buf generate），编译期即锁定字段，契约破坏 → 消费方构建失败。

### 阶段二 · CDC（演进，高风险对优先）
- 对关键 producer↔consumer 对引入 **Pact**（polyglot：Java/TS/Python 均支持）：consumer 在自己仓声明期望 → 发布到 **Pact Broker** → producer CI `can-i-deploy` 验证。
- 触发条件：跨团队频繁协作、schema-first 漏过运行期期望、或破坏事故复盘要求。引入时把 broker 纳入 CI 基础设施。

### CI 三道门（汇总）
① 契约 lint + 破坏性检测（主仓）→ ② producer 实现符合契约（producer 仓）→ ③ consumer 据契约生成并编译/验证（consumer 仓）。任一红 → 阻塞。

## 6. 流程

提契约 PR → 静态门通过 + CODEOWNERS 评审 → 合并 → producer/consumer 各仓据契约跑测试 → 需要时发契约版本（tag / registry 更新）。**实现永远跟随已合并的契约，不反向**。

## 7. 子项目如何查阅契约（让各仓"意识到存在、随时可查"）

契约集中在主仓，但各子仓多为独立 clone——靠以下机制让子仓工作时**感知并随时查阅**契约（详见 [`integration.md`](./integration.md)）：

1. **每个子仓 `CLAUDE.md` 内置「契约」块**（[`integration.md`](./integration.md) §1 标准块）：Claude Code 每次会话自动加载 → 天然感知；块内含铁律 + 本仓 producer/consumer 清单 + 查阅入口。
2. **机器可读索引** [`registry.yaml`](./registry.yaml)：每契约的 `id/type/producers/consumers/status/path`，供工具/skill 反推"哪个仓看哪些契约"。
3. **实时拉取（不存本地副本）**：在 superproject 下读 `../../contracts/`；独立 clone 时 WebFetch `raw.githubusercontent.com/.../contracts/registry.yaml`（公开仓免鉴权）或 `gh api`。规格永远取最新，避免 vendoring 漂移。
4. **（规划）`hashmatrix-toolkit` 契约 skill**：任意仓按 registry 拉取最新契约并定位本仓相关项。
