package io.hashmatrix.examples.sample;

import static org.assertj.core.api.Assertions.assertThat;

import io.hashmatrix.starter.tenant.TenantContextFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Failsafe 集成测试（{@code *IT}）：跑在 {@code spring-boot:repackage} 之后（{@code integration-test} 阶段），
 * 验证「repackage 配 {@code classifier=exec} 后，failsafe 的 {@code @SpringBootTest} 仍能发现
 * {@code @SpringBootConfiguration} 并装配上下文」。
 *
 * <p>这是 surefire 的 {@link SampleApplicationTests}（跑在 repackage <b>之前</b>）覆盖不到的一面——
 * 见主仓 Discussion #21：服务若 repackage（fat-jar）而<b>不</b>配 classifier，主 jar 被 fat-jar 顶替
 * （类落 {@code BOOT-INF/classes}），failsafe 的 {@code @SpringBootTest} 即 bootstrap 失败。
 *
 * <p>故意用<b>裸</b> {@code @SpringBootTest}（不写 {@code classes=}）：正是要证明「自动发现
 * {@code @SpringBootConfiguration}」在 failsafe 阶段依然成立。
 */
@SpringBootTest
class SampleApplicationIT {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextBootstrapsUnderFailsafeAfterRepackage() {
        assertThat(context).isNotNull();
        // 公共 starter 在 repackage 后的 failsafe 上下文里同样装配（与 surefire 路径一致）。
        assertThat(context.getBeansOfType(TenantContextFilter.class)).isNotEmpty();
    }
}
