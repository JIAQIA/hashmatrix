# libs-java Changelog

## libs-java-v0.1.0 — 2026-06-18

首个公共依赖基线（Java 17 · Spring Boot 3.3.5），对应 GitHub Issue #1。

- `hashmatrix-platform-parent`：统一 Java 版本 / 插件管理 / enforcer 质量门 / profile（oss·信创·release）/ GitHub Packages 发布配置。
- `hashmatrix-bom`：钉死 Spring Boot 家族 + Testcontainers + `starter-*` 版本——开发框架版本唯一来源。
- `hashmatrix-starter-tenant`：多租户上下文 `TenantContext`（X-Tenant-* 头 → ThreadLocal，呼应架构 05 §5）。
- `hashmatrix-starter-web`：统一返回 `ApiResponse` + 全局异常处理 `GlobalExceptionHandler`。
- `hashmatrix-starter-test`：JUnit5 + AssertJ + Mockito + Testcontainers 统一测试栈 + 脱敏 fixtures（`MockTenants`/`MockData`）。
- 子仓经 Maven 坐标（`<parent>` + import BOM）引用，验证「只 clone 子仓可构建」；CI 发布到 GitHub Packages，附内网私服镜像同步流程。
