package com.example.demo.web;

import com.example.demo.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 全局模型属性。
 *
 * <p>注意：这里只做"新增"，不改动任何已有属性名，避免影响既有模板。</p>
 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final CurrentUserService currentUserService;

    public GlobalModelAdvice(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @ModelAttribute("currentUser")
    public Object currentUser() {
        return currentUserService.get();
    }

    @ModelAttribute("schoolName")
    public String schoolName() {
        return "广州华商职业学院";
    }

    /**
     * 当前页面应当高亮的侧栏导航项，由请求路径自动推断。
     *
     * <p>这样新增页面时不必再手工维护 active 传参，子页面也会自动继承父级高亮。
     * 片段 {@code sidebar(role, active)} 的签名保持不变，active 参数仍照常传入，
     * 仅作为本属性推断不到时的兜底。</p>
     */
    @ModelAttribute("navActive")
    public String navActive(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String path = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) {
            path = path.substring(ctx.length());
        }
        if (path.isEmpty()) {
            path = "/";
        }

        // ---- 教师 ----
        // 批改相关：/teacher/assignments/{id}/submissions 与 /teacher/submissions/{id}/answers
        if (path.startsWith("/teacher/assignments/") && path.contains("/submissions")) {
            return "review";
        }
        if (path.startsWith("/teacher/submissions/")) {
            return "review";
        }
        if (path.startsWith("/teacher/review")) {
            return "review";
        }
        // 其余教师页面都归属"我的课程"（含作业编辑器 / 作业详情）
        if (path.startsWith("/teacher/")) {
            return "courses";
        }

        // ---- 学生 ----
        if (path.startsWith("/student/grades")) {
            return "grades";
        }
        if (path.startsWith("/student/")) {
            return "courses";
        }

        // ---- 管理员 ----
        if (path.startsWith("/admin/students")) {
            return "students";
        }
        if (path.startsWith("/admin/teachers")) {
            return "teachers";
        }
        if (path.startsWith("/admin/accounts")) {
            return "accounts";
        }
        if (path.startsWith("/admin")) {
            return "dashboard";
        }

        // ---- 账号 ----
        if (path.startsWith("/profile")) {
            return "profile";
        }
        return "";
    }
}
