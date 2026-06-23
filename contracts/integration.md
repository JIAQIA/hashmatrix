# 子项目集成与契约查阅

让**每个子仓在 Claude Code / 工程师工作时都"意识到契约存在、可随时查阅"**的落地方式。单一事实源是主仓 `contracts/`；子仓不 vendoring 副本，而是**内联铁律 + 指针 + 实时拉取**。

## 1. 标准 CLAUDE.md「契约」块（每个子仓 CLAUDE.md 加入）

Claude Code 每次会话自动加载仓库根 `CLAUDE.md` → 天然"意识到存在"。把下面块（按 §2 填本仓 producer/consumer）加入各子仓 `CLAUDE.md`：

```markdown
## 🔗 契约（Contracts）—— 跨子系统集成

本项目经**契约**与其它子系统集成。契约的**单一事实源在主仓** `HashMatrixData/hashmatrix` 的 `contracts/`：
- 索引（机器可读）`contracts/registry.yaml` · 规范 `contracts/CONVENTIONS.md` · 设计 `docs/architecture/06-契约治理.md`
- 在线：https://github.com/HashMatrixData/hashmatrix/tree/main/contracts

**铁律**：先改契约、再改实现；加法兼容默认放行，破坏性走 MAJOR + 弃用期双跑 + 通知消费方；消费方一律 tolerant reader。

**本仓契约**：
- producer：<契约 id…，或「暂无」>
- consumer：<契约 id…，或「暂无」>

**如何查阅（随时拉最新，勿存本地副本）**：
- 在 superproject（`hashmatrix/services/<本仓>`）下：直接读 `../../contracts/`。
- 独立 clone：WebFetch `https://raw.githubusercontent.com/HashMatrixData/hashmatrix/main/contracts/registry.yaml`（公开仓免鉴权）→ 按 registry 取对应契约；或 `gh api repos/HashMatrixData/hashmatrix/contents/contracts/<path> -H "Accept: application/vnd.github.raw"`。
```

## 2. 各子仓 producer / consumer 映射（由 `registry.yaml` 反推）

| 子仓 | producer（须维护其契约） | consumer（须遵循其契约） |
|--|--|--|
| `gateway` | `icd/tenant-context-headers` | — |
| `governance` | `icd/governance-metadata`、`openapi/governance-metadata-v1`、`asyncapi/governance-metadata` | `icd/tenant-context-headers` |
| `security` | — | `icd/tenant-context-headers`、`icd/governance-metadata`、`openapi/governance-metadata-v1`、`asyncapi/governance-metadata` |
| `privacy` | `openapi/privacy-psi-v1`、`proto/privacy-psi`（内部线契约） | `icd/tenant-context-headers`（Java 编排侧） |
| `data-foundation` | — | `icd/tenant-context-headers`、`icd/governance-metadata`、`openapi/governance-metadata-v1`、`asyncapi/governance-metadata` |
| `platform-common` | — | `icd/tenant-context-headers`、`icd/governance-metadata`、`openapi/governance-metadata-v1`、`asyncapi/governance-metadata` |
| `control-plane` | `openapi/control-plane-v1`、`icd/control-plane-provisioning` | `icd/tenant-context-headers` |
| `webui` | — | `openapi/control-plane-v1`、`icd/control-plane-provisioning`、`openapi/governance-metadata-v1`、`icd/governance-metadata` |

> 注：webui 是浏览器 SPA，**不直接消费 Kafka**——`asyncapi/governance-metadata` 的消费方为后端子系统（见 registry）；webui 的实时需求经后端 BFF/推送桥接，另立契约，不直挂事件流。

> 映射随 `registry.yaml` 演进；新增契约后更新本表并同步各子仓块（见 §3）。

## 3. 传播与保持同步

- **传播**：把 §1 块（按 §2 填）加入各子仓 `CLAUDE.md`——经各仓 init Issue/PR 落地。
- **保持最新**：契约规格**实时从主仓拉取**，故子仓块本身只含"几乎不变的铁律 + 本仓清单"，无需频繁更新；仅当本仓 producer/consumer 关系变化时改块。
- **增强（规划）**：`hashmatrix-toolkit` 增 `contracts` skill，在任意仓按 registry 拉取最新契约；并可由 registry 自动生成/刷新各子仓块。
