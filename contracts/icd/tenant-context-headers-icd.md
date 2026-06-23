---
id: icd/tenant-context-headers
owner: hashmatrix-gateway
status: draft
version: 1.2.0
producers: [hashmatrix-gateway]
consumers: [hashmatrix-governance, hashmatrix-security, hashmatrix-privacy, hashmatrix-data-foundation, hashmatrix-platform-common, hashmatrix-control-plane]
since: 2026-06-18
---

# ICD · 租户上下文头（X-Tenant-*）

> 跨切面**线契约**：网关（边缘）与所有下游服务之间透传租户上下文的 HTTP 头约定。
> 非代码依赖——产生方（APISIX 插件 `tenant-context.lua`）与消费方（Java `starter-tenant`）**各自实现、共守此约**。
> 关联：架构 05《多租户与控制平面》§5；主仓 #1（starter-tenant）；gateway#1（X-Tenant 注入）。

## 1. 目的与范围

登录态经 Keycloak 颁发 JWT → 网关校验并解析 org/tenant 声明 → **注入 `X-Tenant-*` 头**下发上游 → 各服务据此做 schema/catalog 路由与行级兜底（数据/计算隔离）。本 ICD 固化这组头的**名称、语义、来源、信任模型与稳定性**，作为产生方与消费方的**单一事实源**。

> 本契约只约束**网关→上游**的可信头。客户端直送的同名头一律不可信（见 §4 信任模型）。

## 2. 头字段定义

| 头 | 语义 | 来源（Keycloak claim） | 必需 | 产生方 | 消费方 |
|--|--|--|--|--|--|
| `X-Tenant-Id` | **稳定租户标识**——数据/计算隔离的路由键（schema/catalog/namespace） | `active_organization` → 单一 `organization` → `tenant`（选择规则见 §3.4） | 是 | gateway 注入 | `starter-tenant` → `TenantContext.tenantId` |
| `X-Tenant-Org` | 活动 org 的原始标识/别名（信息性） | §3.4 选定活动 org 的原始标识（取自 `active_organization` / 单一 `organization`） | 否 | gateway 注入 | `starter-tenant` → `TenantContext.org`（可选） |
| `X-Tenant-Subject` | 终端用户主体（`sub`） | `sub` | 否 | gateway 注入 | **预留**（当前 `starter-tenant` 未消费） |

脱敏示例（请求到达上游时）：

```http
X-Tenant-Id: acme
X-Tenant-Org: acme
X-Tenant-Subject: 11111111-1111-4111-8111-111111111111
```

> 多租户模型：`org = 租户`（公网 SaaS=企业 / 私有化=部门），见架构 05 §1。占位一律脱敏（`acme` / `tenant-demo`）。
>
> **claim 结构基准**（以 Keycloak Organizations 映射为准）：`organization` 承载用户**全部 membership**（单项或多项）；`active_organization` 标识 org-scoped token **选定的活动 org**。`X-Tenant-Id` 取活动 org（按 §3.4 优先级），`X-Tenant-Org` 取同一活动 org 的原始标识/别名。
>
> **必需性的范围**：上表「必需=是」针对**租户隔离路由**（`require_tenant=true`）。**管理平面路由**（`require_tenant=false`，见 §3.6）有意不带租户上下文，`X-Tenant-*` 整组可缺失——消费方须容忍（§4.5）。

## 3. 产生方契约（gateway）

`tenant-context.lua` 必须：

1. **入口清洗**：先删除客户端可能携带的 `X-Tenant-*`，再写入网关可信值（防越权伪造）。
2. **消费验签产物**：仅从 `openid-connect` 验签后注入的 `X-Userinfo`（base64 JSON）解析，自身不验签；必须与 `openid-connect` 同路由且在其后执行（priority 2598 紧随 2599）。
3. **fail-closed**：`require_tenant=true`（默认）时——缺 `X-Userinfo`（路由漏配 openid-connect）→ `401`；userinfo 不可解析 → `403`；无租户声明 → `403`。
4. **单活动租户（解析规则）**：一个请求必须解析到**恰好一个活动租户**并注入唯一 `X-Tenant-Id`（D2：切换租户=重新换 token，`X-Tenant-Id` 始终唯一）。**结构上支持用户隶属多 org membership**，按以下**确定性优先级**选定活动租户：
   1. **活动 org 优先**：org-scoped token 携带 `active_organization`（用户经 Keycloak 选定/切换 org 后换得的令牌）→ 取其为活动租户；
   2. **单一 membership 回退**：无 `active_organization` 声明，但用户**恰好属单一 org** → 取该 org（无需选择，本就无歧义）；
   3. **不可判定即 fail-closed**：多 membership 且无 `active_organization`（既未选定、又非单一）→ 无法确定唯一活动租户 → `403`（`require_tenant=true` 时，与 §3.3 同档），**绝不静默错注**（不得靠 `pairs` 遍历顺序等不确定行为挑一个）。

   > 即：被拒绝的是「**无活动声明的多 membership**」这一**不可判定态**，而非「凡多 org 即拒绝」——已选定/切换 org 的多 membership 用户可正常解析到其活动租户。
5. 同时把租户暴露为 `$tenant_id` 变量（供 `limit-count` 等按租户限流）——属网关内部用法，**不在本头契约范围**。
6. **管理平面（`require_tenant=false`）**：管理/superadmin 平面路由可置 `require_tenant=false`。此时——`openid-connect` **仍验签**（无/坏 token 仍 `401`）、入口清洗（§3.1）**仍执行**；当**解析不到活动租户**（如 superadmin 不绑 org、token 无 `organization` 声明）时**放行且不注入任何 `X-Tenant-*`**（而非 §3.3 的 fail-closed）。语义为「**OIDC 已校验、但无租户上下文**」，专用于无租户的管理面；此模式下**绝不错注租户**。租户隔离资源仍只挂 `require_tenant=true` 路由。（实现见 gateway `apisix/apisix.yaml` admin 路由 + `plugins/tenant-context.lua`，gateway#6。）

## 4. 消费方契约（服务侧）

1. 路由键取 **`X-Tenant-Id`**；`X-Tenant-Org` 仅作信息展示，不得用于隔离路由。
2. `starter-tenant` 默认 `required=false`：**信任边缘已强制**（gateway `require_tenant=true`）。前提是**服务仅在网关之后可达**——见 §5。
3. `X-Tenant-Subject` 为**预留**头；消费方未用时必须**容忍其存在**（tolerant reader），不得因新增头报错。
4. 取不到租户上下文却访问租户隔离资源 → 编程/配置错误（`TenantContextHolder.require()` 抛错），不得静默放行。
5. **容忍管理平面无租户头**：`require_tenant=false` 路由（管理/superadmin 平面，§3.6）下 `X-Tenant-*` **整组可缺失**——这些路由无租户上下文，消费方**不得假设每请求必带 `X-Tenant-Id`**。反之，**租户隔离资源不得挂在此类路由后**（隔离资源只走 `require_tenant=true`）。当前管理面消费方主要为 control-plane。

## 5. 信任与安全模型

- **网关是租户头的信任根**。`X-Tenant-*` 仅在「经网关、且 `tenant-context` 在 `openid-connect` 之后」注入时可信。
- **服务不得直接对外暴露**：直连（绕过网关）的流量其 `X-Tenant-*` 不可信。私有化/信创部署亦须保证 Java 服务只在 `ns: gateway` 之后可达（NetworkPolicy）。
- 任何新增「读租户头」的消费方默认继承本信任假设；若某服务需独立校验 JWT，应显式声明并走 escape hatch，不在本 ICD 默认范围。

## 6. 语义稳定性（前向兼容关键）

- `X-Tenant-Id` 的**具体形态是部署级固定**（demo 下 `alias==id`；生产可映射 org UUID），但**在单次部署内稳定**。消费方只能依赖「它是稳定路由键」，不得假设其为别名或 UUID 的某一种。
- `X-Tenant-Org` 可能与 `X-Tenant-Id` 取值相同（当前实现）或不同（生产映射后）；消费方不得假设二者相等。
- **活动租户解析对消费方语义稳定**：无论用户属单一还是多 org membership，到达上游的 `X-Tenant-Id` 始终是**单一稳定的活动租户路由键**（来源与选择规则见 §3.4）。消费方**不得假设「持头用户必为单 org」**——多 membership 是结构常态，切换活动租户在边缘（重新换 token）完成，下游无感。

## 7. 版本与兼容策略

- 本 ICD 走 semver。**加法兼容**（新增可选头、放宽来源）允许在 MINOR；**改名 / 改语义 / 收紧必需性**为破坏性，需 MAJOR + 弃用期（产生方双跑新旧头一个窗口）+ 通知全部 consumers。
- 默认 consumer 为 **tolerant reader**：忽略未知头、不依赖头顺序。
- **v1.0.0 → v1.1.0（MINOR · 加法放宽，非破坏）**：§3.4 由「多 org 一律 `403`」放宽为「解析到单活动租户、结构预留多 membership」。对消费方契约（读取唯一 `X-Tenant-Id`）**无破坏**——仅放宽产生方的拒绝面，原被边缘拦截的多 membership 请求现可携唯一租户头到达上游；故**不设弃用期 / 双跑窗口**，仅作**信息性通知**全部 consumers（到量可能上升，行为无需改，但请各消费方自检是否残留「持头用户必为单 org」的隐含假设——§6 已禁止）：governance / security / privacy / data-foundation / platform-common / control-plane。
- **v1.1.0 → v1.2.0（MINOR · 加法，非破坏）**：补记**管理平面 `require_tenant=false`** 产生方模式（§3.6）及对应消费方容忍义务（§4.5）。纯加法——既有租户隔离路由（`require_tenant=true`）行为、与消费方读取唯一 `X-Tenant-Id` 的契约**均无变化**；仅显式化「管理面无租户上下文、`X-Tenant-*` 可缺失」这一既有实现（gateway#6），消除契约与实现的留痕缺口。不设弃用期；信息性通知**管理面消费方**（当前主要 control-plane）：admin 平面路由不带租户头，按 tolerant reader 处理。

## 8. 一致性校验要点（契约测试）

- **头名一致性**：gateway `tenant-context.lua` 的 `id_header`/`org_header`/`subject_header` 默认值，必须等于 `starter-tenant` `TenantProperties` 的 `header`/`orgHeader`（及未来 subject）默认值。当前均为 `X-Tenant-Id` / `X-Tenant-Org` ✅。
- **行为契约**：缺身份 → 边缘 401/403（非放行到上游）；客户端伪造头被清洗。建议产生方侧 e2e（APISIX 起栈 + 伪造头）+ 消费方侧 `TenantContextFilter` 单测共同覆盖。
- **管理平面（`require_tenant=false`，§3.6）**：无/坏 token 仍 `401`（openid-connect 不放松）；伪造 `X-Tenant-*` 仍被清洗；无租户即**放行且不注入** `X-Tenant-*`。产生方侧已覆盖（gateway `scripts/smoke_test.py` admin 用例 + `scripts/cluster_e2e.py` fail-closed 档）。
- **多 membership → 单活动租户**（对应 §3.4 三分支，产生方侧契约测试须覆盖）：(a) org-scoped token 带 `active_organization` 的**多 membership** 用户 → 注入对应的唯一 `X-Tenant-Id`；(b) **单一 membership** 无活动声明 → 注入该 org；(c) **多 membership 且无活动声明** → 边缘 `403`（不静默挑选、注入结果不依赖 claim 遍历顺序）。
- 纳入平台契约测试框架后，本 ICD 升 `stable`。
