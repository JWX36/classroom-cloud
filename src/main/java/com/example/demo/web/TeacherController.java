package com.example.demo.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.common.BusinessException;
import com.example.demo.entity.Assignment;
import com.example.demo.entity.AssignmentScoreConfig;
import com.example.demo.entity.ClassInfo;
import com.example.demo.entity.Course;
import com.example.demo.entity.Question;
import com.example.demo.entity.Submission;
import com.example.demo.entity.Teacher;
import com.example.demo.form.AnnouncementForm;
import com.example.demo.form.AssignmentForm;
import com.example.demo.form.CourseForm;
import com.example.demo.form.GradeForm;
import com.example.demo.mapper.ClassInfoMapper;
import com.example.demo.mapper.SubmissionMapper;
import com.example.demo.service.AnnouncementService;
import com.example.demo.service.AssignmentService;
import com.example.demo.service.CourseService;
import com.example.demo.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Controller
public class TeacherController {

    private final CurrentUserService currentUserService;
    private final CourseService courseService;
    private final AssignmentService assignmentService;
    private final AnnouncementService announcementService;
    private final ClassInfoMapper classMapper;
    private final SubmissionMapper submissionMapper;
    private final com.example.demo.mapper.StudentMapper studentMapper;

    public TeacherController(CurrentUserService currentUserService, CourseService courseService,
                             AssignmentService assignmentService, AnnouncementService announcementService,
                             ClassInfoMapper classMapper, SubmissionMapper submissionMapper,
                             com.example.demo.mapper.StudentMapper studentMapper) {
        this.currentUserService = currentUserService;
        this.courseService = courseService;
        this.assignmentService = assignmentService;
        this.announcementService = announcementService;
        this.classMapper = classMapper;
        this.submissionMapper = submissionMapper;
        this.studentMapper = studentMapper;
    }

    @GetMapping("/teacher/courses")
    public String courses(Model model) {
        Teacher teacher = currentUserService.getTeacher();
        if (teacher == null) return "redirect:/login";
        List<Course> courses = courseService.listForTeacher(teacher);
        long pending = courses.stream().filter(c -> "PENDING".equals(c.getStatus())).count();
        long active = courses.stream().filter(Course::isActive).count();
        long assignmentTotal = 0;
        for (Course c : courses) assignmentTotal += assignmentService.countByCourse(c.getId());
        model.addAttribute("teacher", teacher);
        model.addAttribute("courses", courses);
        model.addAttribute("pendingCount", pending);
        model.addAttribute("activeCount", active);
        model.addAttribute("assignmentTotal", assignmentTotal);
        return "teacher/courses";
    }

    @GetMapping("/teacher/courses/new")
    public String newCourse(Model model) {
        Teacher teacher = currentUserService.getTeacher();
        if (teacher == null) return "redirect:/login";
        CourseForm form = new CourseForm();
        form.setTeacherName(teacher.getName());
        form.setYear(LocalDateTime.now().getYear());
        form.setTerm("上学期");
        model.addAttribute("form", form);
        model.addAttribute("classes", courseService.classes());
        model.addAttribute("years", years());
        return "teacher/course-form";
    }

    @PostMapping("/teacher/courses")
    public String createCourse(@Valid @ModelAttribute("form") CourseForm form, BindingResult binding,
                               Model model, RedirectAttributes ra) {
        Teacher teacher = currentUserService.getTeacher();
        if (teacher == null) return "redirect:/login";
        if (binding.hasErrors()) {
            model.addAttribute("classes", courseService.classes());
            model.addAttribute("years", years());
            return "teacher/course-form";
        }
        try {
            courseService.create(teacher, form.getName(), form.getTeacherName(), form.getYear(), form.getTerm(), form.getClassId());
            ra.addFlashAttribute("message", "课程已提交，等待管理员审批");
            return "redirect:/teacher/courses";
        } catch (BusinessException e) {
            binding.reject("course", e.getMessage());
            model.addAttribute("classes", courseService.classes());
            model.addAttribute("years", years());
            return "teacher/course-form";
        }
    }

    @GetMapping("/teacher/courses/{id}")
    public String course(@PathVariable Long id, Model model) {
        Teacher teacher = currentUserService.getTeacher();
        if (teacher == null) return "redirect:/login";
        Course course = courseService.assertTeacher(id, teacher);
        ClassInfo clazz = classMapper.selectById(course.getClassId());
        model.addAttribute("course", course);
        model.addAttribute("clazz", clazz);
        model.addAttribute("announcements", announcementService.listForCourse(id));
        model.addAttribute("assignments", assignmentService.listForTeacherCourse(course));
        model.addAttribute("announcementForm", new AnnouncementForm());
        return "teacher/course";
    }

    @GetMapping("/teacher/courses/{id}/assignments/new")
    public String newAssignment(@PathVariable Long id, Model model) {
        Teacher teacher = currentUserService.getTeacher();
        if (teacher == null) return "redirect:/login";
        Course course = courseService.assertTeacher(id, teacher);
        if (!"APPROVED".equals(course.getStatus()) || course.isEnded()) {
            return "redirect:/teacher/courses/" + id;
        }
        AssignmentForm form = new AssignmentForm();
        form.setDeadline(LocalDateTime.now().plusDays(7));
        form.setScoreMode(AssignmentScoreConfig.EVEN);
        model.addAttribute("form", form);
        model.addAttribute("course", course);
        model.addAttribute("editing", false);
        model.addAttribute("questions", List.of());
        return "teacher/assignment-form";
    }

    /** 误用 GET 访问发布接口（例如刷新历史地址）时回到课程页，避免 500。 */
    @GetMapping("/teacher/courses/{id}/assignments")
    public String assignmentsFallback(@PathVariable Long id) {
        return "redirect:/teacher/courses/" + id;
    }

    @PostMapping("/teacher/courses/{id}/assignments")
    public String createAssignment(@PathVariable Long id, @Valid @ModelAttribute("form") AssignmentForm form,
                                   BindingResult binding, @RequestParam MultiValueMap<String, String> params,
                                   Model model, RedirectAttributes ra) {
        Teacher teacher = currentUserService.getTeacher();
        if (teacher == null) return "redirect:/login";
        Course course = courseService.assertTeacher(id, teacher);
        // 与页面入口保持一致：课程通过审批且未结束才能发布作业（防止绕过表单页直接 POST）
        if (!"APPROVED".equals(course.getStatus()) || course.isEnded()) {
            throw new BusinessException("课程尚未通过审批或已结束，无法发布作业");
        }
        List<String> rowErrors = new ArrayList<>();
        List<Question> questions = List.of();
        AssignmentScoreConfig scoreConfig = buildScoreConfig(form);
        try {
            questions = buildQuestions(params, rowErrors);
            assignmentService.validateScoreConfig(scoreConfig, questions);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
        }
        if (binding.hasErrors() || model.containsAttribute("error") || !rowErrors.isEmpty()) {
            if (!rowErrors.isEmpty() && !model.containsAttribute("error")) {
                model.addAttribute("error", rowErrors.size() == 1 ? rowErrors.get(0)
                        : rowErrors.get(0) + "（共 " + rowErrors.size() + " 处问题，请逐题检查）");
            }
            model.addAttribute("course", course);
            model.addAttribute("editing", false);
            model.addAttribute("questions", questions);
            return "teacher/assignment-form";
        }
        Assignment created = assignmentService.createWithQuestions(course, teacher, form.getTitle(), form.getRequirement(),
                form.getDeadline(), form.getAttachment(), scoreConfig, questions);
        ra.addFlashAttribute("message", questions.isEmpty() ? "作业已发布（附件/说明型）"
                : "作业已发布，共 " + questions.size() + " 道题（已按 单选→多选→判断 排序）");
        return "redirect:/teacher/courses/" + id;
    }

    @GetMapping("/teacher/assignments/{id}/edit")
    public String editAssignment(@PathVariable Long id, Model model) {
        Teacher teacher = currentUserService.getTeacher();
        if (teacher == null) return "redirect:/login";
        Assignment assignment = assignmentService.getForTeacher(id, teacher);
        Course course = courseService.get(assignment.getCourseId());
        AssignmentForm form = new AssignmentForm();
        form.setTitle(assignment.getTitle());
        form.setRequirement(assignment.getRequirement());
        form.setDeadline(assignment.getDeadline());
        AssignmentScoreConfig scoreConfig = assignmentService.scoreConfig(id);
        form.setScoreMode(scoreConfig.getMode());
        form.setSinglePercent(scoreConfig.getSinglePercent());
        form.setMultiplePercent(scoreConfig.getMultiplePercent());
        form.setJudgePercent(scoreConfig.getJudgePercent());
        form.setAttachmentPercent(scoreConfig.getAttachmentPercent());
        model.addAttribute("form", form);
        model.addAttribute("assignment", assignment);
        model.addAttribute("course", course);
        model.addAttribute("editing", true);
        model.addAttribute("questions", assignment.getQuestions());
        return "teacher/assignment-form";
    }

    @PostMapping("/teacher/assignments/{id}")
    public String updateAssignment(@PathVariable Long id, @Valid @ModelAttribute("form") AssignmentForm form,
                                   BindingResult binding, @RequestParam MultiValueMap<String, String> params,
                                   Model model, RedirectAttributes ra) {
        Teacher teacher = currentUserService.getTeacher();
        if (teacher == null) return "redirect:/login";
        Assignment assignment = assignmentService.getForTeacher(id, teacher);
        Course course = courseService.get(assignment.getCourseId());
        List<String> rowErrors = new ArrayList<>();
        List<Question> questions = List.of();
        AssignmentScoreConfig scoreConfig = buildScoreConfig(form);
        try {
            questions = buildQuestions(params, rowErrors);
            assignmentService.validateScoreConfig(scoreConfig, questions);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
        }
        if (binding.hasErrors() || model.containsAttribute("error") || !rowErrors.isEmpty()) {
            if (!rowErrors.isEmpty() && !model.containsAttribute("error")) {
                model.addAttribute("error", rowErrors.size() == 1 ? rowErrors.get(0)
                        : rowErrors.get(0) + "（共 " + rowErrors.size() + " 处问题，请逐题检查）");
            }
            model.addAttribute("assignment", assignment);
            model.addAttribute("course", course);
            model.addAttribute("editing", true);
            model.addAttribute("questions", questions);
            return "teacher/assignment-form";
        }
        boolean questionsChanged = assignmentService.updateWithQuestions(assignment, teacher, form.getTitle(),
                form.getRequirement(), form.getDeadline(), form.getAttachment(), scoreConfig, questions);
        ra.addFlashAttribute("message", questionsChanged
                ? "作业已更新。注意：题目内容有变化（已按 单选→多选→判断 排序），学生作答已清除，已批改的成绩需重新批改"
                : "作业已更新（题目未变，学生作答已保留）");
        return "redirect:/teacher/courses/" + course.getId();
    }

    @PostMapping("/teacher/assignments/{id}/delete")
    public String deleteAssignment(@PathVariable Long id) {
        Teacher teacher = currentUserService.getTeacher();
        if (teacher == null) return "redirect:/login";
        Assignment assignment = assignmentService.getForTeacher(id, teacher);
        Long courseId = assignment.getCourseId();
        assignmentService.delete(assignment);
        return "redirect:/teacher/courses/" + courseId;
    }

    @GetMapping("/teacher/assignments/{id}/submissions")
    public String submissions(@PathVariable Long id, Model model) {
        Teacher teacher = currentUserService.getTeacher();
        if (teacher == null) return "redirect:/login";
        Assignment assignment = assignmentService.getForTeacher(id, teacher);
        Course course = courseService.get(assignment.getCourseId());
        List<Submission> submissions = assignmentService.submissionsForTeacher(assignment, course);
        model.addAttribute("assignment", assignment);
        model.addAttribute("course", course);
        model.addAttribute("submissions", submissions);
        model.addAttribute("gradedCount", submissions.stream().filter(Submission::isGraded).count());
        model.addAttribute("submittedCount", submissions.stream().filter(s -> !s.isMissing()).count());
        return "teacher/submissions";
    }

    @GetMapping("/teacher/submissions/{id}/answers")
    public String submissionAnswers(@PathVariable Long id, Model model) {
        Teacher teacher = currentUserService.getTeacher();
        if (teacher == null) return "redirect:/login";
        Submission submission = submissionMapper.selectById(id);
        if (submission == null) return "redirect:/teacher/courses";
        Assignment assignment = assignmentService.getForTeacher(submission.getAssignmentId(), teacher);
        Course course = courseService.get(assignment.getCourseId());
        java.util.Map<Long, String> answers = assignmentService.answersForSubmission(id);
        assignmentService.decorateSubmission(submission, assignment, answers);
        model.addAttribute("submission", submission);
        model.addAttribute("assignment", assignment);
        model.addAttribute("course", course);
        model.addAttribute("questions", assignment.getQuestions());
        model.addAttribute("answers", answers);
        model.addAttribute("objectiveScore", submission.getObjectiveScore());
        model.addAttribute("subjectiveScore", submission.getSubjectiveScore());
        model.addAttribute("gradeHistory", assignmentService.gradeHistory(submission.getId()));
        model.addAttribute("student", studentMapper.selectById(submission.getStudentId()));
        return "teacher/answers";
    }

    @PostMapping("/teacher/submissions/{id}/grade")
    public String grade(@PathVariable Long id, @Valid @ModelAttribute GradeForm form, BindingResult binding,
                        @RequestParam Long assignmentId, RedirectAttributes ra) {
        Teacher teacher = currentUserService.getTeacher();
        if (teacher == null) return "redirect:/login";
        if (binding.hasErrors()) {
            ra.addFlashAttribute("error", binding.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/teacher/assignments/" + assignmentId + "/submissions";
        }
        try {
            assignmentService.grade(id, teacher, form.getScore(), form.getComment(), Boolean.TRUE.equals(form.getPublish()));
            ra.addFlashAttribute("message", form.isPublish() ? "成绩已保存并发布" : "成绩已保存");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/teacher/assignments/" + assignmentId + "/submissions";
    }

    @PostMapping("/teacher/assignments/{id}/publish-all")
    public String publishAll(@PathVariable Long id, RedirectAttributes ra) {
        Teacher teacher = currentUserService.getTeacher();
        if (teacher == null) return "redirect:/login";
        int count = assignmentService.publishAll(id, teacher);
        ra.addFlashAttribute("message", "已发布 " + count + " 份成绩");
        return "redirect:/teacher/assignments/" + id + "/submissions";
    }

    @PostMapping("/teacher/courses/{id}/announcements")
    public String createAnnouncement(@PathVariable Long id, @Valid @ModelAttribute AnnouncementForm form,
                                     BindingResult binding, RedirectAttributes ra) {
        Teacher teacher = currentUserService.getTeacher();
        if (teacher == null) return "redirect:/login";
        Course course = courseService.assertTeacher(id, teacher);
        if (binding.hasErrors()) {
            ra.addFlashAttribute("error", binding.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/teacher/courses/" + id;
        }
        announcementService.create(course, teacher, form.getTitle(), form.getContent(), form.isPinned());
        ra.addFlashAttribute("message", "公告已发布");
        return "redirect:/teacher/courses/" + id;
    }

    @PostMapping("/teacher/announcements/{id}/delete")
    public String deleteAnnouncement(@PathVariable Long id, @RequestParam Long courseId, RedirectAttributes ra) {
        Teacher teacher = currentUserService.getTeacher();
        if (teacher == null) return "redirect:/login";
        try {
            announcementService.delete(id, teacher);
            ra.addFlashAttribute("message", "公告已删除");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/teacher/courses/" + courseId;
    }

    @GetMapping("/teacher/review")
    public String review(Model model) {
        Teacher teacher = currentUserService.getTeacher();
        if (teacher == null) return "redirect:/login";
        List<Course> courses = courseService.listForTeacher(teacher);
        List<Submission> pending = new ArrayList<>();
        for (Course course : courses) {
            for (Assignment assignment : assignmentService.listForCourse(course)) {
                List<Submission> list = submissionMapper.selectList(new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getAssignmentId, assignment.getId())
                        .eq(Submission::getStatus, "SUBMITTED"));
                for (Submission submission : list) {
                    submission.setAssignmentTitle(assignment.getTitle());
                    submission.setCourseName(course.getName());
                    com.example.demo.entity.Student student = studentMapper.selectById(submission.getStudentId());
                    if (student != null) {
                        submission.setStudentSno(student.getSno());
                        submission.setStudentName(student.getName());
                    }
                    pending.add(submission);
                }
            }
        }
        model.addAttribute("teacher", teacher);
        model.addAttribute("pending", pending);
        return "teacher/review";
    }

    /** 解析题目行；校验问题不抛异常中断，而是逐条收集，保证失败时已填题目全部保留。 */
    private List<Question> buildQuestions(MultiValueMap<String, String> params, List<String> errors) {
        List<String> types = params.getOrDefault("qType", List.of());
        List<String> stems = params.getOrDefault("qStem", List.of());
        List<String> optionAs = params.getOrDefault("qOptionA", List.of());
        List<String> optionBs = params.getOrDefault("qOptionB", List.of());
        List<String> optionCs = params.getOrDefault("qOptionC", List.of());
        List<String> optionDs = params.getOrDefault("qOptionD", List.of());
        List<String> corrects = params.getOrDefault("qCorrect", List.of());
        int size = Math.max(stems.size(), types.size());
        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String stem = at(stems, i);
            if (stem == null || stem.isBlank()) continue;
            int no = questions.size() + 1;
            String type = at(types, i).isBlank() ? "SINGLE" : at(types, i).toUpperCase();
            Question question = new Question();
            question.setType(type);
            if (stem.trim().length() > 1000) {
                errors.add("第 " + no + " 题题干过长（最多 1000 字）");
            }
            question.setStem(stem.trim());
            question.setScore(java.math.BigDecimal.ZERO);
            if ("JUDGE".equals(type)) {
                question.setOptionA("对");
                question.setOptionB("错");
                String correct = at(corrects, i).trim().toUpperCase();
                if ("对".equals(correct) || correct.isEmpty()) correct = "A";
                if ("错".equals(correct)) correct = "B";
                if (!"A".equals(correct) && !"B".equals(correct)) {
                    errors.add("第 " + no + " 题判断题的正确答案只能填“对”或“错”");
                }
                question.setCorrectAnswer(correct);
            } else {
                String a = at(optionAs, i).trim();
                String b = at(optionBs, i).trim();
                String c = at(optionCs, i).trim();
                String d = at(optionDs, i).trim();
                if (a.isEmpty() || b.isEmpty()) {
                    errors.add("第 " + no + " 题至少需要 A、B 两个选项");
                }
                if (a.length() > 500 || b.length() > 500 || c.length() > 500 || d.length() > 500) {
                    errors.add("第 " + no + " 题选项内容过长（每个最多 500 字）");
                }
                question.setOptionA(a);
                question.setOptionB(b);
                question.setOptionC(c.isEmpty() ? null : c);
                question.setOptionD(d.isEmpty() ? null : d);
                String correct = at(corrects, i).replace(" ", "").toUpperCase();
                if (correct.isEmpty()) {
                    errors.add("第 " + no + " 题请填写正确答案");
                }
                StringBuilder valid = new StringBuilder("AB");
                if (!c.isEmpty()) valid.append('C');
                if (!d.isEmpty()) valid.append('D');
                for (String key : correct.split(",")) {
                    if (!key.isEmpty() && (key.length() != 1 || valid.indexOf(key) < 0)) {
                        errors.add("第 " + no + " 题正确答案只能是 " + valid + " 中的选项");
                        break;
                    }
                }
                if ("SINGLE".equals(type) && correct.contains(",")) {
                    errors.add("第 " + no + " 题是单选题，只能有一个正确答案");
                }
                question.setCorrectAnswer(correct);
            }
            questions.add(question);
        }
        return questions;
    }

    private AssignmentScoreConfig buildScoreConfig(AssignmentForm form) {
        AssignmentScoreConfig config = new AssignmentScoreConfig();
        boolean custom = AssignmentScoreConfig.CUSTOM.equalsIgnoreCase(form.getScoreMode());
        config.setMode(custom ? AssignmentScoreConfig.CUSTOM : AssignmentScoreConfig.EVEN);
        config.setSinglePercent(custom ? nz(form.getSinglePercent()) : java.math.BigDecimal.ZERO);
        config.setMultiplePercent(custom ? nz(form.getMultiplePercent()) : java.math.BigDecimal.ZERO);
        config.setJudgePercent(custom ? nz(form.getJudgePercent()) : java.math.BigDecimal.ZERO);
        config.setAttachmentPercent(custom ? nz(form.getAttachmentPercent()) : java.math.BigDecimal.ZERO);
        return config;
    }

    private java.math.BigDecimal nz(java.math.BigDecimal value) {
        return value == null ? java.math.BigDecimal.ZERO : value;
    }

    private String at(List<String> list, int index) {
        return index < list.size() && list.get(index) != null ? list.get(index) : "";
    }

    private List<Integer> years() {
        int year = LocalDateTime.now().getYear();
        return List.of(year - 1, year, year + 1);
    }
}
