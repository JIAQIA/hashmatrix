# hashmatrix · 数据中台（数据治理平台）

云原生数据中台主仓（super-project）。采用 **Git Submodule** 管理各分系统/服务，主仓负责**公共依赖**与**部署运维能力**封装。

- 技术栈：Java + TypeScript（隐私计算等子项目含 Python）
- 部署：Kubernetes + Helm（umbrella chart）
- 架构设计见 [`docs/architecture/`](./docs/architecture/README.md)

> 各子项目的**技术选型仍在逐个讨论中**，当前仅为初始脚手架。

## 仓库结构

```
hashmatrix/                     # 主仓：公共依赖 + 部署运维
├── deploy/                     # Helm umbrella chart + 各 env values
├── libs-java/                  # 公共 Java BOM/starter
├── libs-ts/                    # 公共 TS 组件库/SDK
├── contracts/                  # ICD：OpenAPI/protobuf 接口契约
├── docs/                       # 架构与研制文档（敏感标书材料不入库）
└── services/                   # ↓ 各为独立 git submodule
    ├── webui/                  → hashmatrix-webui            前端 · TS
    ├── gateway/                → hashmatrix-gateway          网关配置/插件
    ├── governance/             → hashmatrix-governance       数据治理分系统 · Java
    ├── security/               → hashmatrix-security         数据安全分系统 · Java
    ├── tools-bi/               → hashmatrix-tools-bi         数据工具(报表BI/可视编排)
    ├── privacy/                → hashmatrix-privacy          隐私计算 · Python+Java
    ├── data-foundation/        → hashmatrix-data-foundation  数据基础(采集/计算/湖仓)
    └── platform-common/        → hashmatrix-platform-common  平台公共(调度/认证/元数据)
```

## 克隆（含子模块）

```bash
git clone --recurse-submodules git@github.com:JIAQIA/hashmatrix.git
# 已克隆主仓后补拉子模块：
git submodule update --init --recursive
```

## 子模块协作约定

- 子模块按**分系统粗粒度**拆分，独立开发、独立发版。
- 主仓只记录各子模块的**提交指针（SHA）**；推进子模块版本时，在主仓提交更新后的指针。
- 接口契约统一放主仓 `contracts/`（ICD），各子模块据此对接。

## License

[Apache-2.0](./LICENSE)
