package org.example.aispingboot.common;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import lombok.extern.slf4j.Slf4j;
import org.example.aispingboot.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobarExceptionHandler {
    // 处理参数校验异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handlerException(MethodArgumentNotValidException e) {
        // 处理异常数据的处理
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return Result.error(ResultCode.PARAM_ERROR.getCode(), ResultCode.PARAM_ERROR.getMsg(), message);
    }

    // 处理业务异常
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        // 如果异样携带额外的数据
        if (e.getData() != null) {
            return Result.error(e.getCode(), e.getMessage(), e.getData());
        }
        return Result.error(e.getCode(), e.getMessage(), null);
    }

    // 处理Token过期异常
    @ExceptionHandler(TokenExpiredException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<?> handleTokenExpiredException(TokenExpiredException e) {
        log.warn("Token已过期: {}", e.getMessage());
        return Result.error(ResultCode.TOKEN_EXPIRED.getCode(), ResultCode.TOKEN_EXPIRED.getMsg(), null);
    }

    // 处理Token无效异常
    @ExceptionHandler(JWTVerificationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<?> handleJWTVerificationException(JWTVerificationException e) {
        log.warn("Token验证失败: {}", e.getMessage());
        return Result.error(ResultCode.TOKEN_INVALID.getCode(), ResultCode.TOKEN_INVALID.getMsg(), null);
    }
}
