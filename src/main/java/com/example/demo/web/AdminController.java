package com.example.demo.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.common.BusinessException;
import com.example.demo.entity.Course;
import com.example.demo.entity.SysUser;
import com.example.demo.mapper.ClassInfoMapper;
import com.example.demo.mapper.CourseMapper;
import com.example.demo.mapper.StudentMapper;
import com.example.demo.mapper.SysUserMapper;
import com.example.demo.mapper.TeacherMapper;
import com.example.demo.service.AdminService;
import com.example.demo.service.AuthService;
import com.example.demo.service.CourseService;
import com.example.demo.service.CurrentUserService;
import com.example.demo.service.StatService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class AdminController {

    private final AdminService adminService;
    private final AuthService authService;
    private final CourseService courseService;
    private final StatService statService;
    private final CurrentUserService currentUserService;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final SysUserMapper userMapper;
    private final ClassInfoMapper classMapper;
    private final CourseMapper courseMapper;

    public AdminController(AdminService adminService, AuthService authService, CourseService courseService,
                           StatService statService, CurrentUserService currentUserService,
                           StudentMapper studentMapper, TeacherMapper teacherMapper, SysUserMapper userMapper,
                           ClassInfoMapper classMapper, CourseMapper courseMapper) {
        this.adminService = adminService;
        this.authService = authService;
        this.courseService = courseService;
        this.statService = statService;
        this.currentUserService = currentUserService;
        this.studentMapper = studentMapper;
        this.teacherMapper = teacherMapper;
        this.userMapper = userMapper;
        this.classMapper = classMapper;
        this.courseMapper = courseMapper;
    }

    @GetMapping("/admin")
    public String dashboard(Model model) {
        model.addAttribute("pendingCourses", courseService.listPending());
        model.addAttribute("courses", courseService.listAll());
        model.addAttribute("stats", java.util.Map.of(
                "students", studentMapper.selectCount(null),
                "teachers", teacherMapper.selectCount(null),
                "accounts", userMapper.selectCount(null),
                "pending", courseService.listPending().size()));
        model.addAttribute("monthlyStats", statService.listStats());
        return "admin/index";
    }

    @PostMapping("/admin/courses/{id}/approve")
    public String approve(@PathVariable Long id, RedirectAttributes ra) {
        try {
            courseService.approve(id);
            ra.addFlashAttribute("message", "课程已批准");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/courses/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        try {
            courseService.reject(id, reason);
            ra.addFlashAttribute("message", "课程已驳回");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin";
    }

    @GetMapping("/admin/students")
    public String students(@RequestParam(required = false) Long classId,
                           @RequestParam(required = false) String keyword, Model model) {
        model.addAttribute("classList", courseService.classes());
        model.addAttribute("classMap", adminService.classMap());
        model.addAttribute("classId", classId);
        model.addAttribute("keyword", keyword);
        model.addAttribute("students", adminService.students(classId, keyword));
        return "admin/students";
    }

    @PostMapping("/admin/classes/{id}/update")
    public String updateClass(@PathVariable Long id, @RequestParam String name,
                              @RequestParam(required = false) String major, RedirectAttributes ra) {
        try {
            adminService.updateClass(id, name, major);
            ra.addFlashAttribute("message", "班级信息已更新");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/students";
    }

    @PostMapping("/admin/students")
    public String addStudent(@RequestParam String sno, @RequestParam(required = false) String name,
                             @RequestParam Long classId, RedirectAttributes ra) {
        try {
            adminService.addStudent(sno, name, classId);
            ra.addFlashAttribute("message", "学生已添加");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/students?classId=" + classId;
    }

    @PostMapping("/admin/students/{id}/update")
    public String updateStudent(@PathVariable Long id, @RequestParam String name,
                                @RequestParam(required = false) Long classId, RedirectAttributes ra) {
        try {
            adminService.updateStudent(id, name);
            ra.addFlashAttribute("message", "学生信息已更新");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return classId == null ? "redirect:/admin/students" : "redirect:/admin/students?classId=" + classId;
    }

    @PostMapping("/admin/students/{id}/delete")
    public String deleteStudent(@PathVariable Long id, @RequestParam(required = false) Long classId,
                                RedirectAttributes ra) {
        try {
            adminService.deleteStudent(id);
            ra.addFlashAttribute("message", "学生已删除");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return classId == null ? "redirect:/admin/students" : "redirect:/admin/students?classId=" + classId;
    }

    @PostMapping("/admin/students/import")
    public String importStudents(@RequestParam Long classId, @RequestParam String text, RedirectAttributes ra) {
        try {
            int count = adminService.importStudents(classId, text);
            ra.addFlashAttribute("message", "成功导入 " + count + " 名学生");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/students?classId=" + classId;
    }

    @GetMapping("/admin/teachers")
    public String teachers(@RequestParam(required = false) String keyword, Model model) {
        model.addAttribute("keyword", keyword);
        model.addAttribute("teachers", adminService.teachers(keyword));
        return "admin/teachers";
    }

    @PostMapping("/admin/teachers")
    public String addTeacher(@RequestParam String tno, @RequestParam String name, @RequestParam String subject,
                             RedirectAttributes ra) {
        try {
            adminService.addTeacher(tno, name, subject);
            ra.addFlashAttribute("message", "教师已添加");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/teachers";
    }

    @PostMapping("/admin/teachers/{id}/update")
    public String updateTeacher(@PathVariable Long id, @RequestParam String name, @RequestParam String subject,
                                RedirectAttributes ra) {
        try {
            adminService.updateTeacher(id, name, subject);
            ra.addFlashAttribute("message", "教师信息已更新");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/teachers";
    }

    @GetMapping("/admin/accounts")
    public String accounts(@RequestParam(required = false) String q, Model model) {
        String kw = (q == null || q.isBlank()) ? null : q.trim();
        List<SysUser> accounts = authService.accounts(kw);
        model.addAttribute("accounts", accounts);
        model.addAttribute("q", kw);
        model.addAttribute("totalCount", userMapper.selectCount(null));
        return "admin/accounts";
    }

    @PostMapping("/admin/accounts/{id}/delete")
    public String deleteAccount(@PathVariable Long id, RedirectAttributes ra) {
        try {
            authService.deleteAccount(id, currentUserService.get());
            ra.addFlashAttribute("message", "账号已删除");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/accounts";
    }

    @PostMapping("/admin/stats/run")
    public String runStats(RedirectAttributes ra) {
        int count = statService.generateLastMonth();
        ra.addFlashAttribute("message", "已生成 " + count + " 个班级的月度统计");
        return "redirect:/admin";
    }
}
