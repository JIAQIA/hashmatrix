---
id: icd/data-foundation-datasources
owner: hashmatrix-data-foundation
status: draft
version: 0.1.0
producers: [hashmatrix-data-foundation]
consumers: [hashmatrix-gateway, hashmatrix-webui]
since: 2026-06-24
---

# ICD · 数据源接入（Data Foundation Datasources）

> 一句话：本契约约束 **webui / gateway** 与 **data-foundation** 之间的「数据源登记 / 连接测试 / 库表 / 预览」接口语义与红线（M2 锚点链①）。机器可读 REST 形状以 [`openapi/data-foundation-datasources-v1.yaml`](../openapi/data-foundation-datasources-v1.yaml) 为准；本 ICD 补充信封、租户、凭据红线与跨切面语义。

## 1. 目的与范围

让 console「数据集成」页能：新建数据源 → **真实测试连接** → 加密存 PG → **按租户**列表 → 列库表 → **预览前 N 行**。

- **在范围**：5 个端点的请求/响应语义、统一信封、租户隔离、凭据红线、防注入、错误码。
- **不在范围**：增量/实时同步、调度、连接池治理、非 MySQL 方言的真实实现、血缘/写回、ESO/Vault 真集成（仅预留 `secret_ref` 间接层）。

## 2. 端点与信封

| 端点 | 语义 | 成功 `data` | 关键错误 |
|--|--|--|--|
| `POST /api/datasources/test` | 真连测试 | `{ok, message}` | 400 |
| `POST /api/datasources` | 登记（口令加密落库） | `DatasourceView`（无口令） | 400 / 409 重名 |
| `GET /api/datasources` | 列本租户 | `DatasourceView[]` | 400 |
| `GET /api/datasources/{id}/tables` | 列库表 | `TableView[]` | 400 / 404 |
| `POST /api/datasources/{id}/preview` | 预览前 N 行 | `PreviewResult` | 400 / 404 |

**统一信封**：所有响应经平台 `ApiResponse { code, message, data, success }`（`code="0"` 成功）。OpenAPI 各 `200`
描述 **`data` 负载**；错误以非 `"0"` 的 `code` + `message` 表达、`data=null`（schema `Error`）。消费方按信封解包后取 `data`。

**探测语义**：`POST /test` 的「连不上」**不是** HTTP 错误——探测调用本身 `200`，连通与否在 `data.ok`，失败时 `data.message`
给脱敏真实错误。便于前端「测试连接」按钮直接渲染成功/失败。

## 3. 产生方契约（data-foundation）

- **D9 租户隔离**：`POST`/`GET`/`{id}/tables`/`{id}/preview` 一律按网关注入的 `X-Tenant-Id` 强制隔离——写落 `tenant_id`、
  读仅取本租户；跨租户访问数据源 id 视同**不存在**（`404 DATASOURCE_NOT_FOUND`，不泄露存在性）。缺租户上下文 → `400 TENANT_REQUIRED`。
  （`POST /test` 为无状态探测，不依赖租户，但其路由仍 `require_tenant=true`。）
- **D7 凭据红线**：口令仅 `writeOnly` 提交 → AES-GCM 加密存 `secret_cipher`；**任何响应/列表/预览/错误/日志均不回显口令明文**。
  `DatasourceView` 不含任何口令字段。预留 `secret_ref` 间接层语义：后续切 ESO/Vault 时改填引用、密文转空，**不改对外 schema**。
- **D8 连接器中立**：`type` 为方言类型键（`mysql` 为首个实现）；URL 形状由方言拥有，端点/模型不写死单一数据库；
  不支持的 `type` → `400 UNSUPPORTED_DATASOURCE_TYPE`。
- **防注入（预览）**：`table` 先按真实元数据**白名单**校验，命中后以规范化引用构造查询，绝不拼客户端串；
  `limit` 钳制 `[1,1000]`（默认 100）并在驱动侧封顶（方言中立，非 `LIMIT` 拼接）。表不在白名单 → `404 TABLE_NOT_FOUND`。

## 4. 消费方契约（webui / gateway）

- **tolerant reader**：忽略未知字段、不依赖字段顺序；按信封 `code=="0"` 判成功，否则读 `code`/`message`。
- **gateway**：对 `/api/datasources/*` 配 `require_tenant=true`，校验 JWT 后注入 `X-Tenant-Id`（见 [`icd/tenant-context-headers`](./tenant-context-headers-icd.md)）；
  调用方**不**自带租户头。SDK 由 OpenAPI 生成。
- **webui**：口令仅在新建/编辑表单单向提交，列表/详情不展示口令；「测试连接」读 `data.ok`/`data.message`；
  预览按 `columns`/`rows` 渲染表格。

## 5. 信任与安全模型

- 鉴权：网关校验 Keycloak JWT（`bearerAuth`）；data-foundation 信任网关注入的 `X-Tenant-Id`，不二次校验 token。
- 主密钥（AES-GCM）部署期经 K8s Secret 注入（`DS_SECRET_KEY`）、**绝不入库**（见主仓 deploy / D7）；明文口令绝不落库/日志/响应/异常栈。

## 6. 语义稳定性 / 前向兼容

- `type` 取值随方言插件增长（加法）；新增方言不改对外 schema。
- `TableView.catalog`/`schema` 可能为 `null`（数据源无对应层级）；消费方须容忍。
- `PreviewResult.rows` 为原生类型的自由对象（`additionalProperties`）；消费方按列名取值，不假设类型集合封闭。

## 7. 版本与兼容

semver，加法兼容 MINOR / 破坏走 MAJOR + 双跑弃用（CONVENTIONS §3）。当前 `0.1.0` draft——形状以
data-foundation 已实现的 5 端点（PR #14–#17）为生产者参照回填，评审冻结后转 `review`。

## 8. 一致性校验要点（契约测试）

- 静态门：Spectral lint + oasdiff 加法兼容（主仓 `contracts-ci`）。
- 生产者（data-foundation）：实现符合本契约（已有集成测试覆盖 5 端点 + 隔离 + 防注入 + 红线）。
- 消费者（webui）：据 OpenAPI 生成 SDK，编译期锁字段；gateway 据路径配 `require_tenant=true` 路由。
- **红线校验**：契约示例与响应 schema **无口令明文字段**；`DatasourceView` 不含 password；预览/错误不回显口令。

> 占位一律脱敏（`acme` / `tenant-demo` / `example.com` / `orders_demo`），不含甲方信息。
