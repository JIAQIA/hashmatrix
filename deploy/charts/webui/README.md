# deploy/charts/webui —— 前端 Web UI 子 chart（M1 真实部署）

> **状态：真实（M1）**。`version: 0.1.0`，由 umbrella chart `platform` 按 `webui.enabled` 聚合。
> 关联：M1 贯通主线（I3 · webui nginx chart / I5 经网关可达）。

## 定位

主仓 umbrella chart `platform` 下的 **webui** 子 chart。**D5：子仓 `services/webui` 交付 image，主仓 owns chart。**

- 事实源（镜像 + 前端构建）：`services/webui`（pnpm monorepo，React + Vite），两套独立镜像：
  - `ghcr.io/hashmatrixdata/webui-console`（`apps/console`，租户控制台·使用面）
  - `ghcr.io/hashmatrixdata/webui-admin`（`apps/admin`，平台管理台·运营面）
- **console 与 admin 互不可达**（独立镜像/部署/Service/域名，安全爆炸半径隔离）——本 chart 渲染两套 Deployment + Service。
- 聚合方式：umbrella `platform/Chart.yaml` 以 `file://../webui` 声明依赖，按 `webui.enabled` 条件渲染（默认关，仅 `values-localdev` 开）。

## M1 渲染内容

- **webui-console / webui-admin**：各一个 Deployment + 固定名 Service（`webui-console:80` / `webui-admin:80`），nginx 托管静态 SPA。
- **运行期 config.js 注入（D3 部署级白标）**：容器 entrypoint `30-render-config.sh` 用 env `envsubst` 渲染 `/usr/share/nginx/html/config.js`——`branding.*`（默认 emerald `#059669`，D4）、`config.apiBaseUrl`（默认 `/api`，同源经网关访问后端）、`config.oidc*`。改 env 重启即换肤/换接入参数，免重建镜像；不按租户运行期换肤。
- **探针**：静态 SPA 无 actuator → readiness 探 `/config.js`（entrypoint 渲染后恒在）、liveness 探 `/`。

## 经网关可达（按 Host 分流）

gateway chart 已加 `webui-console-upstream` / `webui-admin-upstream` + 两条**公共**路由（无鉴权，`priority:0`）：按 Host 头区分 `webui.hosts.console` → console、`webui.hosts.admin` → admin，`uri: /*` 承接 SPA shell/assets/config.js。`/api/*` 受保护路由 `priority` 更高 → 两面的后端调用各走对应后端路由（console→各业务，admin→control-plane `/api/v1/*`），同时保留 console/admin 同源隔离。本地经 `/etc/hosts` 或 `curl -H "Host: ..."` 访问。

## 本地浏览器登录（localdev · kind）

两个要点：① **安全上下文**——OIDC PKCE(S256) 用 `crypto.subtle`，仅安全上下文可用；故 console 用 **`*.localhost`**（Chrome 视其为安全上下文，即使 HTTP），而非 `*.localdev`（非安全上下文 → 点登录 PKCE 静默失败不跳转）。② **issuer 一致**——token 的 `iss` 必须与网关 `oidc.discovery` 同主机（均 `keycloak:8080`），故浏览器也用 `keycloak:8080` 取 token（hosts 映射 + port-forward）。

```bash
# 1) hosts（一次性，需 sudo）：console.localhost（安全上下文）+ keycloak 解析到本机
echo "127.0.0.1 keycloak console.localhost" | sudo tee -a /etc/hosts

# 2) 浏览器侧代理绕行：把 console.localhost、keycloak 加入系统/代理 bypass（否则经代理 503）

# 3) port-forward（前台各开一个，或加 & 后台）
kubectl port-forward -n demo svc/keycloak 8080:8080
kubectl port-forward -n demo svc/platform-gateway 9080:9080

# 4) 浏览器打开（demo 用户 alice / Passw0rd!，租户 acme）
open http://console.localhost:9080/
```

- 登录重定向至 `http://keycloak:8080/realms/hashmatrix/...`（= `values-localdev` 的 `console.config.oidcAuthority`），token `iss=http://keycloak:8080` → 网关验签通过 → `/api/meta/search` 返回真实目录。
- 客户端为 realm 公共客户端 `hashmatrix-webui`（授权码流 + PKCE，redirectUri 含 `http://console.localhost:9080/*`，见 `services/gateway/keycloak/realm-export.json`）。
- 不设 hosts 时 console 页面仍可加载（`curl -H "Host: console.localhost" http://127.0.0.1:9080/`），但浏览器交互式登录依赖上述 hosts + 安全上下文。

## ⚠️ 生产硬化 follow-up（post-M1）

官方 `nginx:1.27-alpine` master 以 root 起、绑特权口 80，故本 chart **未设 `runAsNonRoot: true`**（localdev PSA=baseline 可跑；`drop ALL` 后加回 `NET_BIND_SERVICE` 以绑 80）。生产/restricted PSA 应切 **nginx-unprivileged**（监听 8080）+ `runAsNonRoot: true`——属子仓镜像 + 本 chart 协同的 post-M1 硬化项。

## 红线

仅 dev 占位 / 脱敏 demo（`acme` / `tenant-demo` / `example.com` / `Acme Demo Tech`）；镜像 tag 固定（不用 `latest`）；品牌/OIDC/API 参数部署级注入，不含真实甲方参数。
