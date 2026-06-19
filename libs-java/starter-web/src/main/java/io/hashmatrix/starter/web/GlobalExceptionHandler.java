package io.hashmatrix.starter.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：把异常统一转为 {@link ApiResponse} 出参，避免各服务各写一套错误结构。
 *
 * <ul>
 *   <li>{@link BusinessException} → 其携带的 HTTP 状态 + 业务码。</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 + {@code VALIDATION_ERROR}。</li>
 *   <li>框架自带状态的异常（实现 {@link ErrorResponse}，如 {@code NoResourceFoundException} 404、
 *       {@code ResponseStatusException}）→ 其携带状态，<b>不一律 500</b>。</li>
 *   <li>其它未捕获异常 → 500 + {@code INTERNAL_ERROR}，并记录日志；不向客户端泄露内部细节。</li>
 * </ul>
 *
 * <p>注：本兜底处理<b>不</b>触及 Spring Security 异常——方法级授权拒绝由 starter-security 的更高优先级
 * advice（{@code SecurityErrorAdvice}）抢先渲染为 403/401，不会落到这里的 {@code Exception} 兜底。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 参数校验失败业务码。 */
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";

    /** 兜底内部错误业务码。 */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        log.debug("Business exception [{}]: {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null
                ? fieldError.getField() + ": " + fieldError.getDefaultMessage()
                : "Validation failed";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(VALIDATION_ERROR, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        // 框架自带 HTTP 状态的异常（实现 ErrorResponse：404 NoResourceFound、ResponseStatusException 等）→
        // 用其携带状态返回，勿一律吞成 500（否则资源不存在等会错误地报内部错误）。
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            // 4xx（资源不存在等）属预期、不刷错误日志；但携带 5xx 的框架异常仍需落日志，避免可观测性回退。
            if (status.is5xxServerError()) {
                log.error("Framework ErrorResponse with server-error status", ex);
            }
            return ResponseEntity.status(status)
                    .body(ApiResponse.fail(String.valueOf(status.value()), reasonOf(status)));
        }
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(INTERNAL_ERROR, "Internal server error"));
    }

    /** 状态短语（不回显异常细节，避免泄露内部路径/实现）。 */
    private static String reasonOf(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : "Error";
    }
}
