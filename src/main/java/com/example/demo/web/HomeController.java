package com.example.demo.web;

import com.example.demo.common.BusinessException;
import com.example.demo.common.Result;
import com.example.demo.entity.SysUser;
import com.example.demo.service.CurrentUserService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HomeController {

    private final CurrentUserService currentUserService;

    public HomeController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/")
    public String index() {
        SysUser user = currentUserService.get();
        if (user == null) return "redirect:/login";
        return switch (user.getRole()) {
            case "ADMIN" -> "redirect:/admin";
            case "TEACHER" -> "redirect:/teacher/courses";
            default -> "redirect:/student/courses";
        };
    }

    @GetMapping("/api/health")
    @ResponseBody
    public Result<String> health() {
        return Result.ok("ok");
    }
}
