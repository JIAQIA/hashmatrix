# hashmatrix-starter-test

统一测试栈：以 `<scope>test</scope>` 引入即获 **JUnit5 + AssertJ + Mockito + Spring Test + Testcontainers**
（`spring-boot-starter-test` + `testcontainers/junit-jupiter` + `postgresql`）+ 脱敏 Mock fixtures（`io.hashmatrix.test.fixtures.*`）。

```xml
<dependency>
  <groupId>io.hashmatrix</groupId>
  <artifactId>hashmatrix-starter-test</artifactId>
  <scope>test</scope>
</dependency>
```

## 🔴 用法红线：让 `@SpringBootTest` 在 **surefire 与 failsafe 两个阶段**都能装配

> 源：governance→主仓 Discussion #21（`InfraConnectivityIT` bootstrap 失败）。**是用法/构建配置问题、非平台代码 bug**，
> 但有**两个必要条件**——缺任一即 `@SpringBootTest` 引导失败。条件一对 surefire 与 failsafe 都需要；
> **条件二只对 failsafe `*IT` 额外需要**（这正是只跑 surefire 的样板长期没暴露、governance 第一个踩到的面）。

### 条件一 · 结构：`@SpringBootTest` 必须能发现一个 `@SpringBootConfiguration`

1. 子仓**必须有**一个 `@SpringBootApplication` 主类（如 `io.hashmatrix.<svc>.<Svc>Application`）；
2. 主类**必须位于测试所在包的祖先包**——Spring Boot 从测试类所在包**向上**搜 `@SpringBootConfiguration`，搜不到就失败。
   主类放根包 `io.hashmatrix.<svc>`、测试放其子包即可；
3. 结构对了就**用裸 `@SpringBootTest`**，别写 `@SpringBootTest(classes=...)` 去硬指（指错类反而触发
   `Failed to find merged annotation for @BootstrapWith`）。

> `examples/sample-service` 的 `SampleApplicationTests`（surefire `*Tests`、已验证绿）即条件一的最小样板：
> `SampleApplication`（根包，仅 `@SpringBootApplication`+`main`）+ 同包裸 `@SpringBootTest`。

### 条件二 · repackage 配 `classifier`：有 failsafe `@SpringBootTest *IT` 的服务必须 ⭐

> 只对 **failsafe 集成测试（`*IT`，跑在 `spring-boot:repackage` 之后）**生效；surefire `*Tests`（repackage 之前）碰不到。

服务若用 `spring-boot-maven-plugin` 打可执行 fat-jar，**必须**配 `<classifier>`——否则 repackage 用 fat-jar **顶替主 jar**
（类落 `BOOT-INF/classes`），failsafe 的 `@SpringBootTest` 扫不到 `@SpringBootConfiguration`（即便条件一已满足，仍报
`Unable to find a @SpringBootConfiguration`）：

```xml
<plugin>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-maven-plugin</artifactId>
  <version>${spring-boot.version}</version>   <!-- 对齐 hashmatrix-bom 的 spring-boot.version -->
  <configuration>
    <classifier>exec</classifier>             <!-- fat-jar 产为 *-exec.jar；瘦 jar 仍为可被 failsafe 扫描的主产物 -->
  </configuration>
  <executions>
    <execution><goals><goal>repackage</goal></goals></execution>
  </executions>
</plugin>
```

配套：**Dockerfile `COPY target/*-exec.jar`**（而非 `*.jar`——后者是瘦 jar、不可执行）；failsafe 若 parent 未绑定，子仓自绑
`integration-test`+`verify`。**实证**：governance commit `e96f4a0` 以此修复，`mvn verify` 全绿（19 单测 + 3 IT）。

**两个常见报错**：`Unable to find a @SpringBootConfiguration`（条件一缺主类/位置错，或条件二 failsafe 下 fat-jar 顶替主 jar）；
`Failed to find merged annotation for @BootstrapWith`（`classes=` 指向非配置类）。照上仍红？附最小复现（主类位置 + IT 包名 +
是否 repackage + 完整栈）上抛主仓 / libs-java。

## fixtures

`io.hashmatrix.test.fixtures` 提供脱敏 Mock 数据与租户占位（`MockData` / `MockTenants`，如 `MockTenants.ACME`）。
**红线**：测试数据一律虚构脱敏（`acme` / `tenant-demo` / `example.com`），禁用任何真实甲方数据。
