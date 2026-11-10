package com.orbit.research.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    private ResponseEntity<?> response(int status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message, "requestId", String.valueOf(request.getAttribute("requestId"))));
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> domain(ApiException error, HttpServletRequest request) { return response(error.status(), error.code(), error.getMessage(), request); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.ServletRequestBindingException.class, org.springframework.web.multipart.MultipartException.class})
    ResponseEntity<?> invalid(Exception error, HttpServletRequest request) { return response(400, "INVALID_REQUEST", "请求参数不完整或格式不正确。", request); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> size(Exception error, HttpServletRequest request) { return response(413, "FILE_TOO_LARGE", "每份 PDF 不超过 50 MB，每批最多 5 份。", request); }
    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class, org.springframework.web.servlet.NoHandlerFoundException.class})
    ResponseEntity<?> missing(Exception error, HttpServletRequest request) { return response(404, "NOT_FOUND", "接口不存在。", request); }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<?> method(Exception error, HttpServletRequest request) { return response(405, "METHOD_NOT_ALLOWED", "请求方法不正确。", request); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception error, HttpServletRequest request) {
        // Never log arbitrary upstream bodies, prompts, files or provider credentials.
        LoggerFactory.getLogger(ApiErrors.class).error("request={} errorType={}", request.getAttribute("requestId"), error.getClass().getSimpleName());
        return response(500, "INTERNAL_ERROR", "服务暂时无法完成请求，请保留请求编号后重试。", request);
    }
}
