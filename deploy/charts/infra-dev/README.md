# deploy/charts/infra-dev —— 本地/dev 基础设施子 chart

> **状态：dev-only（M1）**。`version: 0.2.0`。把 M1 端到端验证所需的 **in-cluster 依赖**装进单 ns：
> in-cluster **Keycloak**（OIDC 身份源）+ **go-httpbin 回显 upstream**（临时替尚未就绪的 governance）
> + in-cluster **PostgreSQL**（dev 级，单实例 + 各服务独立 db）。
> 关联：#15（M1 贯通主线 · I1 in-cluster 身份/数据底座）、gateway `scripts/cluster_e2e.py`（集群末端 e2e）。

## 定位

主仓 umbrella `platform` 下的 **dev-only 子 chart**（按 `infra-dev.enabled` 聚合，**默认仅 `values-localdev` 启用**）。
它**不是生产组件**——只为「在 governance 等真实后端镜像就绪前，先把 gateway 的 E2E 在单 ns 跑通」提供底座：

- **Keycloak**（`quay.io/keycloak/keycloak:26.0`，`start-dev --import-realm`）——网关 `oidc.discovery`
  指向的**真实身份源**，预置 realm `hashmatrix`（公开客户端 `apisix` + 脱敏 demo 用户/orgs）。
- **upstream-echo**（`mccutchen/go-httpbin`）——受保护路由的**临时回显上游**，`/headers` 回显其收到的请求头，
  使「网关注入 `X-Tenant-*` → 上游收到」闭环可被断言。
- **PostgreSQL**（`postgres:16.4`，固定 Service `postgres:5432`）——真实后端（governance/control-plane…）的
  dev 级关系库，单实例 + 各服务独立 db（`initdb` 幂等创建）；**无持久卷**（emptyDir，Pod 重建即重跑 initdb）。

## 关键决策（主仓拍板 · #15）

### D-E · dev 基础设施载体：自托管最小 manifests（而非社区 chart 硬依赖）

主仓渲染门 `deploy/scripts/validate.sh` **离线**——`helm dependency build` 不拉外部 chart（见
`charts/platform/Chart.yaml` 对有状态依赖、`charts/gateway` 对 APISIX 官方 chart 的同款「钉版本但注释」做法）。
把 Keycloak/PG 社区 chart 作**硬依赖**会破坏该离线门。故 dev 底座以**自托管最小 Deployment/Service/ConfigMap**
实现，**1:1 镜像 `services/gateway/docker-compose.local.yml` 的 proven 配置**（Keycloak 26.0 / go-httpbin v2.15.0）。
后续若需更完整的有状态依赖（PG/Operator），仍走 platform `Chart.yaml` 注释的「升级路径」，不返工。

### D-F · governance 未就绪期：受保护上游用 dev 回显桩（go-httpbin）

governance 镜像（I5 · 子仓 owned）尚未就绪，但 gateway 的 E2E **不应被 governance 阻塞**。故本 chart 提供
`upstream-echo`（go-httpbin），并由 `values-localdev` 把 `gateway.upstreams.governance` **临时重指向**它。
这让 gateway 的安全不变量族（fail-closed / 注入正确性 / 防伪 / ICD §8 头名一致性）在集群路径**真跑通**。

> **governance(I5) 就绪后的收口动作**（明确标注，避免桩长期残留冒充「已接真后端」）：
> ① `infra-dev.echoStub.enabled=false`；② `values-localdev` 的 `gateway.upstreams.governance` 改回 `governance:8082`。
> **收口后验证**：`kubectl -n hashmatrix get deploy upstream-echo` 应为 `NotFound`、且网关上游解析到真实 `governance`——
> 否则即「桩仍在、绿灯却接桩」。echoStub readiness 用 `/status/200` 恒绿，无法自暴露残留，故此校验须人工/巡检兜住。

## issuer 一致性（最易踩坑，务必先读）

OIDC 验签要求 **token 的 `iss` == 网关 `discovery` 解析出的 issuer**。本 chart 把 Keycloak 暴露为固定 Service
名 **`keycloak`**、并设 `KC_HOSTNAME_STRICT=false`（issuer 跟随访问主机）。于是：

- ✅ **集群内**（如 cluster_e2e 跑在 ns 内 Pod / Job）经 `http://keycloak:8080` 取 token → `iss=http://keycloak:8080/realms/hashmatrix`
  == 网关 `oidc.discovery` 默认值 → 验签通过。
- ⚠️ **宿主 port-forward**（`127.0.0.1:8080` 取 token、`127.0.0.1:9080` 打网关）→ token `iss` 为 `127.0.0.1`，
  与网关 discovery 的 `keycloak:8080` **不一致 → B 档全 401**。**故验收脚本应在集群内运行**（见下）。

## 预置 realm（脱敏 demo · 事实源在 gateway）

realm `hashmatrix` 的事实源(SoT)在 **`services/gateway/keycloak/realm-export.json`**（gateway 既是 OIDC 依赖方、
又定义了 demo 用户/orgs/协议映射器，emit `organization`/`active_organization` 声明供 `tenant-context` 解析）。
本 chart 保留 `files/realm-export.json` **同步副本**（Helm 打包只能读 chart 根内文件）：

| demo 用户 | organization | active_organization | 用途 |
|--|--|--|--|
| `alice` | `acme` | — | 单租户放行 → `X-Tenant-Id=acme` |
| `bob` | `tenant-demo` | — | 第二租户隔离 |
| `carol` | `acme, tenant-demo` | `acme` | 多 membership + 活动 org 优先（§3.4）→ `acme` |
| `dave` | `acme, tenant-demo` | — | 多 membership 无活动声明 → fail-closed 403 |
| `superadmin` | —（无 org） | — | admin 平面放行 / 租户路由 fail-closed 403 |

> 口令均为本地 dev 占位（`Passw0rd!`），**严禁用于任何真实环境**。
> **同步 SoT → chart**：`bash deploy/scripts/sync-gateway-config.sh`（含 realm；`--check` 为 CI 漂移守卫）。

## in-cluster PostgreSQL（#15 · I1 · dev 级）

真实后端（governance/control-plane…）落地需关系库，故本 chart 增量补入 **dev 级 PostgreSQL**（决策 D-E
「升级路径」的兑现，仍是自托管最小 manifest、不引社区 chart、守离线渲染门）：

- **固定 Service `postgres:5432`**（非 release 前缀）——各服务 datasource 以 `postgres:5432/<db>` 稳定引用。
- **单实例 + 各服务独立 db**（呼应端口基线「5432 单实例+独立 db」）：库名由 `values.postgres.databases` 驱动，
  `initdb` 阶段**幂等创建**（`governance`/`controlplane`/`security`/`datafoundation`/`privacy`）。新增后端在 values 追加即可。
- **dev 占位凭据**：单超级用户 `hashmatrix/hashmatrix` 拥有全部 db（`values.postgres.auth`）——**严禁用于任何真实环境（红线）**。
- **无持久卷**（emptyDir）：Pod 重建即重跑 initdb 重建各 db；dev e2e 足够，需持久化走下文 Operator 升级路径。
- **可移植硬化**：非 root（uid/gid 999）+ fsGroup + seccomp，restricted PSA 的 ns 也可跑。

> **接线给后端（I5/A2）**：各子 chart 的 datasource 指向 `postgres:5432/<db>`、用户 `hashmatrix`、库名同上；
> Keycloak 仍用 dev 内嵌 H2（`start-dev`），**不接此 PG**。

## 渲染与本地起栈

```bash
# 经 umbrella 渲染门（localdev 环境已启用 infra-dev + gateway；离线 lint/template/kubeconform）
bash deploy/scripts/validate.sh localdev demo

# 本地单 ns 起栈（先 tools/local-infra `make up`）
helm upgrade --install platform deploy/charts/platform \
  -f deploy/values/values.yaml -f deploy/values/values-localdev.yaml -n hashmatrix --create-namespace
kubectl -n hashmatrix rollout status deploy/keycloak
kubectl -n hashmatrix rollout status deploy/postgres
kubectl -n hashmatrix rollout status deploy/upstream-echo
kubectl -n hashmatrix rollout status deploy/platform-gateway

# 验收：在 ns 内跑 gateway 的 cluster_e2e.py（issuer 一致），UPSTREAM_ECHO_PATH=/api/headers 打开 B 档
# （脚本与运行细节见 services/gateway/scripts/README.md「集群末端 e2e」）。
```

> ⚠️ **镜像预载（go-httpbin / Keycloak）**：DaoCloud 免费镜像站对 `mccutchen/go-httpbin` 的 blob 偶发
> 返回 `text/html`（→ kubelet `unexpected media type text/html ... ImagePullBackOff`，**非 chart 缺陷**）。
> 规避：起栈前 `cd tools/local-infra && make warm`（已含三镜像）；个别仍失败用逃生口
> `make fetch IMG="docker.io/mccutchen/go-httpbin:v2.15.0"`。若节点 containerd 已缓存损坏 blob 致再载仍
> 报 `invalid character '<'`，需先清该镜像的 content/lease 再重载（见 `tools/local-infra/README.md` 附录 B）。

## 不在本 chart 范围（明确边界）

- **生产级有状态库 / Operator / 持久卷 / HA**：本 chart 的 PG 是 dev 单实例 + emptyDir（**无持久化**）；
  生产/HA 仍走 platform `Chart.yaml` 注释的 Operator 升级路径（CloudNativePG / bitnami 等），不返工。
- **真实 governance/control-plane**：子仓 owned（I5）；本 chart 仅提供其就绪前的 dev 替身。
- **生产身份**：本 Keycloak 为 dev 单副本、明文 dev 口令、无持久卷——**严禁用于任何非本地环境**。
