package io.hashmatrix.examples.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.hashmatrix.starter.tenant.TenantContextFilter;
import io.hashmatrix.starter.web.GlobalExceptionHandler;
import io.hashmatrix.test.fixtures.MockTenants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 端到端验证公共 starter 在真实子仓上下文中装配并生效：
 * 租户头透传（starter-tenant）+ 统一返回/异常（starter-web）+ 复用 fixtures（starter-test）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SampleApplicationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext context;

    @Test
    void publicStartersAreWiredIntoTheApplication() {
        assertThat(context.getBeansOfType(TenantContextFilter.class)).isNotEmpty();
        assertThat(context.getBeansOfType(GlobalExceptionHandler.class)).isNotEmpty();
    }

    @Test
    void tenantHeaderIsPropagatedToHandler() throws Exception {
        mvc.perform(get("/hello").header("X-Tenant-Id", MockTenants.ACME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.tenant").value("acme"));
    }

    @Test
    void missingTenantHeaderFallsBackToAnonymous() throws Exception {
        mvc.perform(get("/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenant").value("anonymous"));
    }

    @Test
    void businessExceptionIsRenderedByGlobalHandler() throws Exception {
        mvc.perform(get("/boom").header("X-Tenant-Id", MockTenants.ACME))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEMO_CONFLICT"))
                .andExpect(jsonPath("$.message").value("demo business error"));
    }
}
