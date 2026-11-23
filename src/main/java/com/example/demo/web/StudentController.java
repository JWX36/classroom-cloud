package com.example.demo.web;

import com.example.demo.common.BusinessException;
import com.example.demo.entity.Assignment;
import com.example.demo.entity.ClassInfo;
import com.example.demo.entity.Course;
import com.example.demo.entity.Student;
import com.example.demo.entity.Submission;
import com.example.demo.mapper.ClassInfoMapper;
import com.example.demo.service.AnnouncementService;
import com.example.demo.service.AssignmentService;
import com.example.demo.service.CourseService;
import com.example.demo.service.CurrentUserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Controller
public class StudentController {

    private final CurrentUserService currentUserService;
    private final CourseService courseService;
    private final AssignmentService assignmentService;
    private final AnnouncementService announcementService;
    private final ClassInfoMapper classMapper;

    public StudentController(CurrentUserService currentUserService, CourseService courseService,
                             AssignmentService assignmentService, AnnouncementService announcementService,
                             ClassInfoMapper classMapper) {
        this.currentUserService = currentUserService;
        this.courseService = courseService;
        this.assignmentService = assignmentService;
        this.announcementService = announcementService;
        this.classMapper = classMapper;
    }

    @GetMapping("/student/courses")
    public String courses(Model model) {
        Student student = currentUserService.getStudent();
        if (student == null) return "redirect:/login";
        List<Course> courses = courseService.listForStudent(student);
        long active = courses.stream().filter(Course::isActive).count();
        ClassInfo clazz = classMapper.selectById(student.getClassId());
        model.addAttribute("student", student);
        model.addAttribute("clazz", clazz);
        model.addAttribute("courses", courses);
        model.addAttribute("pinned", announcementService.pinnedForStudent(student));
        model.addAttribute("activeCount", active);
        model.addAttribute("endedCount", courses.size() - active);
        return "student/courses";
    }

    @GetMapping("/student/courses/{id}")
    public String course(@PathVariable Long id, Model model) {
        Student student = currentUserService.getStudent();
        if (student == null) return "redirect:/login";
        Course course = courseService.assertStudent(id, student);
        model.addAttribute("course", course);
        model.addAttribute("announcements", announcementService.listForCourse(id));
        model.addAttribute("assignments", assignmentService.listForStudent(course, student));
        return "student/course";
    }

    @GetMapping("/student/assignments/{id}")
    public String assignment(@PathVariable Long id, Model model) {
        Student student = currentUserService.getStudent();
        if (student == null) return "redirect:/login";
        Assignment assignment = assignmentService.getForStudent(id, student);
        Course course = courseService.get(assignment.getCourseId());
        boolean canSubmit = !assignment.isExpired() && !course.isEnded();
        model.addAttribute("assignment", assignment);
        model.addAttribute("course", course);
        model.addAttribute("submission", assignment.getMySubmission());
        if (assignment.getMySubmission() != null) {
            model.addAttribute("gradeHistory",
                    assignmentService.gradeHistory(assignment.getMySubmission().getId()));
        }
        model.addAttribute("canSubmit", canSubmit);
        return "student/assignment";
    }

    @PostMapping("/student/assignments/{id}/submit")
    public String submit(@PathVariable Long id, @RequestParam(required = false) String content,
                         @RequestParam(required = false) MultipartFile file,
                         @RequestParam org.springframework.util.MultiValueMap<String, String> params,
                         RedirectAttributes ra) {
        Student student = currentUserService.getStudent();
        if (student == null) return "redirect:/login";
        try {
            Assignment assignment = assignmentService.getForStudent(id, student);
            java.util.Map<Long, String> answers = new java.util.HashMap<>();
            for (String key : params.keySet()) {
                if (!key.startsWith("answer_")) continue;
                try {
                    Long questionId = Long.valueOf(key.substring("answer_".length()));
                    String value = params.get(key).stream()
                            .filter(v -> v != null && !v.isBlank())
                            .distinct().sorted()
                            .collect(java.util.stream.Collectors.joining(","));
                    if (!value.isBlank()) answers.put(questionId, value);
                } catch (NumberFormatException ignored) {
                }
            }
            assignmentService.submit(assignment, student, content, file, answers);
            ra.addFlashAttribute("message", "作业提交成功");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student/assignments/" + id;
    }

    @GetMapping("/student/grades")
    public String grades(Model model) {
        Student student = currentUserService.getStudent();
        if (student == null) return "redirect:/login";
        List<Submission> submissions = assignmentService.publishedForStudent(student);
        model.addAttribute("submissions", submissions);
        model.addAttribute("student", student);
        // 成绩页顶部的裸数字统计
        BigDecimal max = BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        int scored = 0;
        for (Submission s : submissions) {
            if (s.getScore() != null) {
                max = max.max(s.getScore());
                sum = sum.add(s.getScore());
                scored++;
            }
        }
        model.addAttribute("scoreMax", max);
        model.addAttribute("scoreAvg", scored == 0 ? BigDecimal.ZERO
                : sum.divide(BigDecimal.valueOf(scored), 1, RoundingMode.HALF_UP));
        model.addAttribute("scoreCount", scored);
        model.addAttribute("scoreTotal", submissions.size());
        return "student/grades";
    }
}
