package cn.slainc.web.exception;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.exception.SystemException;
import cn.slainc.core.exception.ThirdPartyException;
import cn.slainc.core.exception.ValidationException;
import cn.slainc.core.result.Result;
import cn.slainc.core.result.ResultCode;
import cn.slainc.web.config.WebExceptionProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    static class Form {
        @NotBlank(message = "不能为空")
        private String name;
    }

    private final WebExceptionProperties properties = new WebExceptionProperties();
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(properties);
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");

    @Test
    void businessExceptionMapsToHttpStatus() {
        ResponseEntity<Result<Void>> response = handler.handleAbstract(new BizException("下单失败"), request);
        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(ResultCode.BUSINESS_ERROR.code(), response.getBody().code());
        assertEquals("下单失败", response.getBody().message());
    }

    @Test
    void systemExceptionMapsTo500() {
        ResponseEntity<Result<Void>> response = handler.handleAbstract(new SystemException("数据库不可用"), request);
        assertEquals(500, response.getStatusCode().value());
        assertEquals(ResultCode.INTERNAL_SERVER_ERROR.code(), response.getBody().code());
    }

    @Test
    void thirdPartyExceptionKeeps502() {
        ThirdPartyException exception = new ThirdPartyException("支付网关超时").withService("payment");
        ResponseEntity<Result<Void>> response = handler.handleThirdParty((ThirdPartyException) exception, request);
        assertEquals(502, response.getStatusCode().value());
        assertEquals("支付网关超时", response.getBody().message());
    }

    @Test
    void validationExceptionJoinsViolations() {
        ValidationException exception = new ValidationException(ResultCode.BAD_REQUEST, "参数校验失败",
                List.of("name 不能为空", "age 必须大于 0"));
        ResponseEntity<Result<Void>> response = handler.handleValidation(exception, request);
        assertEquals(400, response.getStatusCode().value());
        assertEquals("name 不能为空；age 必须大于 0", response.getBody().message());
    }

    @Test
    void constraintViolationExceptionIsHandled() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<Form>> violations = validator.validate(new Form());
        ResponseEntity<Result<Void>> response =
                handler.handleConstraintViolation(new ConstraintViolationException(violations), request);
        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().message().contains("name"));
    }

    @Test
    void unexpectedExceptionHidesMessageWhenConfigured() {
        properties.setExposeMessage(false);
        ResponseEntity<Result<Void>> hidden = handler.handleUnexpected(new IllegalStateException("内部细节"), request);
        assertEquals(500, hidden.getStatusCode().value());
        assertEquals(properties.getDefaultMessage(), hidden.getBody().message());

        properties.setExposeMessage(true);
        ResponseEntity<Result<Void>> exposed = handler.handleUnexpected(new IllegalStateException("数据库连接失败"), request);
        assertEquals("数据库连接失败", exposed.getBody().message());
    }
}
