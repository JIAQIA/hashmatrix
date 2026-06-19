# deploy/charts/gateway —— 网关子 chart（APISIX standalone）

> **状态：可部署（M1）**。`version: 0.1.0`，把 APISIX standalone 声明式网关 + 自定义插件装进单 ns。
> 关联：#17（chart 落地）、hashmatrix-gateway#4（完成判据「chart 装进单 ns」）、决策 D5（主仓 owns charts）。

## 定位

主仓 umbrella chart `platform` 下的网关子 chart（按 `gateway.enabled` 聚合），把**南北向网关**部署到 K8s 单 ns：

- 部署 **APISIX**（`apache/apisix`，**standalone 声明式 / `config_provider: yaml`，无 etcd**）；
- 把 submodule `services/gateway` 的**声明式配置与自定义插件**注入 APISIX；
- env-specific 字段（OIDC discovery / 上游）由 **chart values → ConfigMap 渲染**注入。

## 关键决策（主仓拍板 · #17）

### D-A · APISIX 运行载体：自托管最小 Deployment（而非官方 chart 硬依赖）

主仓渲染门 `deploy/scripts/validate.sh` **离线**——`helm dependency build` 不拉外部 chart（见
`charts/platform/Chart.yaml` 对有状态依赖的「钉版本但注释」同款做法）。官方 APISIX chart 作**硬依赖**会破坏该离线门。
故 M1 以**自托管 Deployment** 运行 APISIX，1:1 镜像 `services/gateway/docker-compose.local.yml` 的 proven standalone 配置。
**官方 chart 依赖保留为升级路径**（`Chart.yaml` 注释 + `condition: apisix.enabled`）——骨架（ConfigMap 注入契约 /
端口 9080 / 插件挂载约定）与官方 chart 等价，后续可无损切换，不返工。

### D-B · 插件挂载：ConfigMap 卷挂载（subPath）（而非 initContainer / 镜像分层）

`tenant-context.lua` / `audit-log.lua` 经 ConfigMap 以 **subPath 单文件挂载**到
`/usr/local/apisix/apisix/plugins/`（不遮蔽内置插件目录），与 compose 的 bind-mount **机制一致**、无需 initContainer 或自定义镜像。

### D-C · Service 端口约定（集群侧，避免漂移）

集群内 Service→Pod 用**应用容器端口**：APISIX 代理 **9080**、上游 governance **8082**、Keycloak **8080**
（基线 `8180`/`9080→host` 仅宿主/dev 暴露，**勿写进集群侧 discovery/upstream**）。单 ns 内用裸 Service 名解析。

## 与 gateway 子模块的关系（配置即代码）

配置/插件**事实源(SoT)在 `services/gateway`**；Helm 打包只能读 chart 根内文件，故本 chart 保留 `files/` 同步副本：

| 来源（services/gateway） | 本 chart 处理 |
|--|--|
| `apisix/config.yaml`（静态，env-agnostic） | vendored `files/config.yaml` → ConfigMap（`.Files.Get`，**同步副本**） |
| `apisix/apisix.yaml`（声明式） | **不直接 copy**：chart 渲染**集群版**（`templates/configmap.yaml`，discovery/上游按 values 注入；SoT 的 `mock-upstream` 仅本地 compose 自洽） |
| `plugins/*.lua`（tenant-context / audit-log） | vendored `files/plugins/*.lua` → ConfigMap → subPath 挂载（**同步副本**） |
| Keycloak `discovery` / `clientId` / 上游 / 品牌 | 部署级 `values`（`oidc.*` / `upstreams.*` / `tenancy.brandingProfile`） |

> **同步 SoT → chart**：`bash deploy/scripts/sync-gateway-config.sh`（config.yaml + 两个 .lua；apisix.yaml 走模板不同步）。

## 渲染与校验

```bash
# 独立渲染本 chart
helm template demo deploy/charts/gateway

# 经 umbrella（test 环境已启用 gateway）走主仓渲染门（lint + template + kubeconform，离线）
bash deploy/scripts/validate.sh test demo
```

## 本地单 ns 部署（与 tools/local-infra 协同）

```bash
# 起本地 kind + 镜像缓存后（见 tools/local-infra/README.md）：
helm install platform deploy/charts/platform \
  -f deploy/values/values.yaml -f deploy/values/values-test.yaml -n hashmatrix --create-namespace
kubectl -n hashmatrix rollout status deploy/platform-gateway
kubectl -n hashmatrix port-forward svc/platform-gateway 9080:9080
curl -i http://127.0.0.1:9080/public/get      # 探活；/api/* 需 Keycloak OIDC token（无 token→401）
```
