package io.hashmatrix.examples.sample;

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
 *   <li>{@code GET /hello} —— 经 starter-tenant 读取当前租户，经 starter-web 统一返回结构。</li>
 *   <li>{@code GET /boom} —— 抛业务异常，由 starter-web 的全局处理统一转 ApiResponse。</li>
 * </ul>
 */
@RestController
public class HelloController {

    @GetMapping("/hello")
    public ApiResponse<Map<String, String>> hello() {
        String tenant = TenantContextHolder.getTenantId().orElse("anonymous");
        return ApiResponse.ok(Map.of("tenant", tenant, "message", "hello"));
    }

    @GetMapping("/boom")
    public ApiResponse<Void> boom() {
        throw new BusinessException(HttpStatus.CONFLICT, "DEMO_CONFLICT", "demo business error");
    }
}
