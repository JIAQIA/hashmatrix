package io.hashmatrix.starter.security;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 安全异常渲染（最高优先级）：把<b>方法级授权</b>在进入控制器后抛出的 Spring Security 异常按语义渲染为
 * 401/403，避免被应用的全局兜底异常处理吞成 500。
 *
 * <p><b>为什么需要它</b>：过滤链级的匿名拒绝由 {@code HttpStatusEntryPoint}（401）/ 默认
 * {@code AccessDeniedHandler}（403）处理；但 {@code @PreAuthorize} 等方法级授权是在 <b>DispatcherServlet
 * 进入控制器后</b>抛 {@link AccessDeniedException}（Spring Security 6 为 {@code AuthorizationDeniedException}），
 * 该异常在 MVC 内即被 {@code @ControllerAdvice} 处理，<b>到不了</b> {@code ExceptionTranslationFilter}。
 * 若应用装配了兜底 {@code @ExceptionHandler(Exception.class)}（如 starter-web {@code GlobalExceptionHandler}）→
 * 「已认证但缺角色」会被错误地渲染成 <b>500</b> 而非 403。
 *
 * <p><b>机制</b>：本 advice 以 {@link Ordered#HIGHEST_PRECEDENCE} 抢在兜底 advice（默认
 * {@code LOWEST_PRECEDENCE}）之前——DispatcherServlet 按 advice 顺序取<b>首个能处理该异常</b>的 advice，
 * 故本 advice 先命中并渲染 403/401（空体，与过滤链入口 {@code HttpStatusEntryPoint} 风格一致）；
 * 非安全异常本 advice 无匹配处理器，自然落到兜底 advice。
 *
 * <p>跨边界不变量由 {@code SecurityMatrixIntegrationTest}（control-plane）守护。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityErrorAdvice {

    /** 已认证但权限不足（方法级 {@code @PreAuthorize} 拒绝等）→ 403 Forbidden。 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Void> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    /** 认证缺失/失败若上抛到 MVC 层 → 401 Unauthorized（与过滤链入口一致）。 */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Void> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
