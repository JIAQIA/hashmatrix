package io.hashmatrix.examples.sample;

import io.hashmatrix.starter.audit.AuditEvent;
import io.hashmatrix.starter.audit.AuditRecorder;
import io.hashmatrix.starter.tenant.TenantContextHolder;
import io.hashmatrix.starter.web.ApiResponse;
import io.hashmatrix.starter.web.BusinessException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演示公共能力复用：
 * <ul>
 *   <li>{@code GET /hello} —— starter-tenant 读当前租户、starter-web 统一返回、starter-audit 记审计（租户自动加盖）。</li>
 *   <li>{@code GET /boom} —— 抛业务异常，由 starter-web 的全局处理统一转 ApiResponse。</li>
 * </ul>
 * 可观测（starter-observability）以 actuator / 指标方式被动生效，无需控制器显式调用。
 */
@RestController
public class HelloController {

    private final AuditRecorder auditRecorder;

    public HelloController(AuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

    @GetMapping("/hello")
    public ApiResponse<Map<String, String>> hello() {
        String tenant = TenantContextHolder.getTenantId().orElse("anonymous");
        // 复用 starter-audit：事件自动加盖当前租户
        auditRecorder.record(
                AuditEvent.of("sample-user", "HELLO", "/hello", AuditEvent.Outcome.SUCCESS, null));
        return ApiResponse.ok(Map.of("tenant", tenant, "message", "hello"));
    }

    @GetMapping("/boom")
    public ApiResponse<Void> boom() {
        throw new BusinessException(HttpStatus.CONFLICT, "DEMO_CONFLICT", "demo business error");
    }
}
