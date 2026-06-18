# deploy/charts/tenant —— 租户开通单元 chart

> 关联：HashMatrixData/hashmatrix#2。设计见架构 `docs/architecture/05-多租户与控制平面.md`（开通时序 / 隔离定档）。

## 定位

**一个 Helm release = 一个租户**。控制平面（`services/control-plane`）经 **Helm SDK + K8s client** 命令式渲染应用本 chart 完成开通，
**生产期不依赖 Git/Argo**。本 chart 落「C 分层/Bridge」的数据平面隔离：

| 维度 | 本 chart 渲染 |
|--|--|
| 计算 | `namespace-per-tenant`（`tenant-<id>`）+ ResourceQuota + LimitRange |
| 网络 | NetworkPolicy：默认拒绝 + 放行 DNS / 同租户 / 共享基座（gateway·data·observability） |
| 数据 | schema/db-per-tenant 上下文 ConfigMap（PG schema/db · Doris catalog · Paimon db）——建库由控制平面 DB job 执行，本 chart 声明期望 |
| Secrets | ESO `ExternalSecret`（从外部 Vault/KMS 注入；**无明文入库**，红线） |
| 服务实例 | `services[]` 通用 Deployment+Service 占位（落地改渲对应分系统子 chart） |

## 渲染 / dry-run（DoD：tenant-demo）

```bash
# 渲染 tenant-demo（base values + 该租户模板）
helm template tenant-demo deploy/charts/tenant \
  -f deploy/charts/tenant/values.yaml \
  -f deploy/values/values-tenant-demo.yaml

# 对集群 diff（需 helm-diff 插件；控制平面亦走同一渲染）
bash deploy/scripts/tenant-diff.sh demo
```

`tenant.id` 必填，缺失时模板 `fail` 报错。命名空间默认 `tenant-<id>`，schema 默认 `tenant_<id>`，Doris catalog 默认 `catalog_<id>`。

## 红线

- `ExternalSecret` 仅引用外部 store 路径，**不含明文**——故 `externalsecret.yaml` 可安全入库。
- 任何真实凭据/明文 Secret **不入库**；`deploy/scripts/secrets-guard.sh` 做提交前守卫。
