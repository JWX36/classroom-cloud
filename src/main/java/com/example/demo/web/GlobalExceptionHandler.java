package com.example.demo.web;

import com.example.demo.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String business(BusinessException e, Model model, HttpServletRequest request) {
        log.warn("业务拒绝 {}：{}", request.getRequestURI(), e.getMessage());
        model.addAttribute("status", 400);
        model.addAttribute("message", e.getMessage());
        model.addAttribute("path", request.getRequestURI());
        return "error";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public String uploadTooLarge(Model model, HttpServletRequest request) {
        log.warn("上传超限 {}", request.getRequestURI());
        model.addAttribute("status", 413);
        model.addAttribute("message", "附件不能超过 50MB，请压缩或分卷后重试");
        model.addAttribute("path", request.getRequestURI());
        return "error";
    }

    /** 静态资源/路径不存在：保持 404 语义，交给 error 页展示，不要被兜底处理吞成 500。 */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(NoResourceFoundException e, Model model, HttpServletRequest request) {
        log.warn("页面不存在 {}：{}", request.getRequestURI(), e.getMessage());
        model.addAttribute("status", 404);
        model.addAttribute("message", "页面不存在或已被移除");
        model.addAttribute("path", request.getRequestURI());
        return "error";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String error(Exception e, Model model, HttpServletRequest request) {
        log.error("请求处理失败：{}", request.getRequestURI(), e);
        model.addAttribute("status", 500);
        model.addAttribute("message", "服务器开小差了，请稍后重试");
        model.addAttribute("path", request.getRequestURI());
        return "error";
    }
}
