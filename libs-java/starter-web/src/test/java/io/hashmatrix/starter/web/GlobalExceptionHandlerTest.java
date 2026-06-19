package io.hashmatrix.starter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 直接对处理方法做单元测试（确定性、无 servlet 派发）。
 * 全局处理与 MVC 派发的端到端联动由 examples/sample-service 的 {@code /boom} 用例覆盖。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionMapsToItsStatusAndCode() {
        BusinessException ex = new BusinessException(HttpStatus.CONFLICT, "DUPLICATE", "duplicate tenant");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("DUPLICATE");
        assertThat(response.getBody().message()).isEqualTo("duplicate tenant");
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    void businessExceptionDefaultsToBadRequest() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusiness(new BusinessException("E001", "bad input"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unexpectedExceptionMapsTo500AndMasksDetail() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnexpected(new IllegalStateException("sensitive internal detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(GlobalExceptionHandler.INTERNAL_ERROR);
        assertThat(response.getBody().message())
                .isEqualTo("Internal server error")
                .doesNotContain("sensitive internal detail");
    }

    @Test
    void errorResponseExceptionKeepsItsStatusInsteadOf500() {
        // 实现 ErrorResponse 的框架异常（如 404 NoResourceFound / ResponseStatusException）→ 用其携带状态，
        // 不被兜底吞成 500。以 ResponseStatusException 作代表（与 NoResourceFoundException 同走 ErrorResponse 分支）。
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnexpected(
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "no such resource /secret-path"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("404");
        // 仅回状态短语，不泄露异常细节（路径等）。
        assertThat(response.getBody().message()).isEqualTo("Not Found").doesNotContain("/secret-path");
    }

    @Test
    void validationExceptionMapsTo400WithFieldDetail() throws Exception {
        Method method = Sample.class.getDeclaredMethod("create", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "payload");
        binding.addError(new FieldError("payload", "name", "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, binding);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(GlobalExceptionHandler.VALIDATION_ERROR);
        assertThat(response.getBody().message()).contains("name").contains("must not be blank");
    }

    /** 仅用于构造 MethodParameter 的占位方法。 */
    private static final class Sample {
        @SuppressWarnings("unused")
        void create(String name) {
        }
    }
}
