# deploy/charts/gateway —— 网关子 chart（占位）

> **状态：占位（placeholder）**。`version: 0.0.0-placeholder`，尚不可部署。
> 关联：HashMatrixData/hashmatrix#2（部署运维封装）。

## 定位

主仓 umbrella chart 下的网关子 chart，职责是把**南北向网关**部署到 K8s `ns: gateway`：

- 以 **APISIX 官方 chart** 为依赖部署 APISIX 数据面；
- 把 submodule `services/gateway` 的**声明式配置与自定义插件**注入 APISIX。

## 与 gateway 子模块的关系（配置即代码）

配置/插件的**事实源在 `services/gateway`**，本 chart 只负责「渲染 + 注入」：

| 来源（services/gateway） | 注入方式（本 chart，待落地） |
|--|--|
| `apisix/config.yaml`、`apisix/apisix.yaml` | 渲染为 ConfigMap，挂载到 APISIX（standalone / `config_provider: yaml`，**无 etcd**） |
| `plugins/*.lua`（tenant-context / audit-log） | initContainer 拷贝或镜像分层（方式待定） |
| Keycloak `discovery` / `clientId` | 部署级 values 注入（`oidc.*`） |

> 网关采用 **standalone 声明式**模式，故本 chart **不部署 etcd**（`apisix.etcd.enabled=false`）。

## 待落地清单（TODO）

- [ ] 在 `Chart.yaml` 启用并锁定 `dependencies: apisix` 版本
- [ ] `templates/` 渲染声明式 ConfigMap + 自定义插件注入
- [ ] `oidc.*` / `tenancy.*` values 接入 umbrella chart 的分环境 values（prod/test/信创）
- [ ] 与主仓 umbrella chart 聚合、补 `values-<env>.yaml`
