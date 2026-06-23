# deploy/charts/security —— 数据安全分系统子 chart（M1 真实部署）

> **状态：真实（M1）**。`version: 0.1.0`，由 umbrella chart `platform` 按 `security.enabled` 聚合。
> 关联：HashMatrixData/hashmatrix#38（I5 · security 真实部署）/ #15（贯通主线 I5/I2）。

## 定位

主仓 umbrella chart `platform` 下的 **security** 子 chart。**D5：子仓 `services/security` 交付 image，主仓 owns chart。**

- 事实源（镜像 + 应用契约）：`services/security`（Java / Spring Boot），镜像 `ghcr.io/hashmatrixdata/security`。
- 聚合方式：umbrella `platform/Chart.yaml` 以 `file://../security` 声明依赖，按 `security.enabled` 条件渲染（默认关，仅 `values-localdev` 开）。
- 分环境差异（prod/test/信创 xc）经 umbrella 的 `values-<env>.yaml` 注入，本子 chart 不内置环境耦合。

## M1 渲染内容

- **Deployment + Service `security`**（固定 Service 名，供 gateway upstream `security:8083` 解析）：应用 8083 / 管理 9083（actuator 独立端口）。
- **datasource → infra-dev in-cluster PG**：独立 db `security`（infra-dev initdb 幂等建）；Flowable 审批引擎复用本连接、启动期自建 `ACT_*` 表。
- **readiness/liveness 探针**指向管理端口 9083（readiness deps-optional：仅 `readinessState`，PG 缺失不拖垮探针）。
- 资源限额 + JVM 堆上限（`MaxRAMPercentage`，受容器 limit 约束，防多 JVM 叠加 OOM）。

## 经网关可达（I2 协同）

gateway chart 已加 `security-upstream`（`security:8083`）+ 受保护路由 `/api/security/*`（共享 `auth-tenant`：OIDC 校验 → 注入 `X-Tenant-*` → 审计）。业务 probe `/api/security/probe` 守 RBAC（缺 `security-viewer` → 403）+ 按 `X-Tenant-Id` 行级路由 `sec_<tenant>`。

> ⚠️ **当前可达性 = 403（链路打通 + RBAC 生效），非 200。** 网关 `auth-tenant` 链（`tenant-context.lua`）当前**仅注入 `X-Tenant-*`，未注入 `X-User`/`X-Roles`**；而 `starter-security` 据 `X-User`/`X-Roles` 还原认证主体与角色。故经网关命中 probe 当前必返 401/403——这恰好证明「经网关可达至应用 + 方法级 RBAC 真生效」。返回 **200 需 I2 在网关补「`X-Userinfo`(OIDC 验签产物) → `X-User`/`X-Roles` 注入」能力**（gateway 范畴，非本 chart）。本 issue 交付 chart + 路由 + 经网关可达（至 RBAC）；200 的端到端 demo 由该 I2 跟进项承接。

## 红线

仅 dev 占位凭据 / 脱敏 demo（`acme` / `tenant-demo` / `example.com`）；镜像 tag 固定（不用 `latest`）；生产经 ESO/secretRef 注入 `PG_PASSWORD`。
