package com.example.demo.web;

import com.example.demo.common.BusinessException;
import com.example.demo.entity.SysUser;
import com.example.demo.form.PasswordForm;
import com.example.demo.form.ProfileForm;
import com.example.demo.form.RegisterForm;
import com.example.demo.service.AuthService;
import com.example.demo.service.CurrentUserService;
import jakarta.validation.Valid;
import com.example.demo.mapper.ClassInfoMapper;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;
    private final ClassInfoMapper classMapper;

    public AuthController(AuthService authService, CurrentUserService currentUserService,
                          ClassInfoMapper classMapper) {
        this.authService = authService;
        this.currentUserService = currentUserService;
        this.classMapper = classMapper;
    }

    @GetMapping("/login")
    public String login() {
        SysUser user = currentUserService.get();
        if (user != null) return homeOf(user);
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("step", 1);
        model.addAttribute("form", new RegisterForm());
        return "register";
    }

    @PostMapping("/register/check")
    public String checkCode(@RequestParam("code") String code, Model model, RedirectAttributes ra) {
        try {
            AuthService.RosterInfo roster = authService.verifyForRegister(code);
            model.addAttribute("step", 2);
            model.addAttribute("roster", Map.of("code", roster.code(), "name", roster.name(), "role", roster.role()));
            RegisterForm form = new RegisterForm();
            form.setCode(roster.code());
            model.addAttribute("form", form);
            return "register";
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/register";
        }
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult binding,
                           Model model, RedirectAttributes ra) {
        model.addAttribute("step", 2);
        AuthService.RosterInfo roster = authService.findRoster(form.getCode());
        if (roster != null) {
            model.addAttribute("roster", Map.of("code", roster.code(), "name", roster.name(), "role", roster.role()));
        }
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            binding.rejectValue("confirmPassword", "mismatch", "两次输入的密码不一致");
        }
        if (binding.hasErrors()) {
            return "register";
        }
        try {
            authService.register(form);
            ra.addFlashAttribute("message", "注册成功，请使用学号/教师编号登录");
            return "redirect:/login";
        } catch (BusinessException e) {
            binding.reject("register", e.getMessage());
            return "register";
        }
    }

    @GetMapping("/profile")
    public String profile(Model model) {
        SysUser user = currentUserService.get();
        if (user == null) return "redirect:/login";
        ProfileForm profile = new ProfileForm();
        profile.setName(user.getName());
        profile.setPhone(user.getPhone());
        profile.setEmail(user.getEmail());
        model.addAttribute("user", user);
        model.addAttribute("profileForm", profile);
        model.addAttribute("passwordForm", new PasswordForm());
        model.addAttribute("student", currentUserService.getStudent());
        model.addAttribute("teacher", currentUserService.getTeacher());
        if (currentUserService.getStudent() != null) {
            model.addAttribute("clazz", classMapper.selectById(currentUserService.getStudent().getClassId()));
        }
        return "profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@Valid @ModelAttribute("profileForm") ProfileForm form, BindingResult binding,
                                Model model, RedirectAttributes ra) {
        SysUser user = currentUserService.get();
        if (user == null) return "redirect:/login";
        if (binding.hasErrors()) {
            model.addAttribute("user", user);
            model.addAttribute("passwordForm", new PasswordForm());
            model.addAttribute("student", currentUserService.getStudent());
            model.addAttribute("teacher", currentUserService.getTeacher());
            return "profile";
        }
        try {
            authService.updateProfile(user, form);
            ra.addFlashAttribute("message", "资料已更新");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/profile";
    }

    /** 独立更换头像（配合个人中心裁剪弹窗）。 */
    @PostMapping("/profile/avatar")
    public String updateAvatar(@RequestParam("avatar") MultipartFile avatar, RedirectAttributes ra) {
        SysUser user = currentUserService.get();
        if (user == null) return "redirect:/login";
        try {
            authService.updateAvatar(user, avatar);
            ra.addFlashAttribute("message", "头像已更新");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/profile/password")
    public String changePassword(@Valid @ModelAttribute("passwordForm") PasswordForm form, BindingResult binding,
                                 RedirectAttributes ra) {
        SysUser user = currentUserService.get();
        if (user == null) return "redirect:/login";
        if (binding.hasErrors()) {
            ra.addFlashAttribute("error", binding.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/profile";
        }
        try {
            authService.changePassword(user, form);
            ra.addFlashAttribute("message", "密码已修改");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/profile";
    }

    private String homeOf(SysUser user) {
        return switch (user.getRole()) {
            case "ADMIN" -> "redirect:/admin";
            case "TEACHER" -> "redirect:/teacher/courses";
            default -> "redirect:/student/courses";
        };
    }
}
