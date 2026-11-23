package com.example.demo.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Assignment;
import com.example.demo.entity.Announcement;
import com.example.demo.entity.ClassInfo;
import com.example.demo.entity.Course;
import com.example.demo.entity.MonthlyStat;
import com.example.demo.entity.Question;
import com.example.demo.entity.Student;
import com.example.demo.entity.Submission;
import com.example.demo.entity.SubmissionAnswer;
import com.example.demo.entity.SysUser;
import com.example.demo.entity.Teacher;
import com.example.demo.mapper.AssignmentMapper;
import com.example.demo.mapper.AnnouncementMapper;
import com.example.demo.mapper.ClassInfoMapper;
import com.example.demo.mapper.CourseMapper;
import com.example.demo.mapper.MonthlyStatMapper;
import com.example.demo.mapper.QuestionMapper;
import com.example.demo.mapper.StudentMapper;
import com.example.demo.mapper.SubmissionAnswerMapper;
import com.example.demo.mapper.SubmissionMapper;
import com.example.demo.mapper.SysUserMapper;
import com.example.demo.mapper.TeacherMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

/**
 * 初始化管理员名册、演示账号和示例课程。
 * 开发环境每次启动都会检查，已存在数据时不会重复插入。
 */
@Component
@Order(1)
public class DataInitializer implements CommandLineRunner {

    private final ClassInfoMapper classMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final SysUserMapper userMapper;
    private final CourseMapper courseMapper;
    private final AssignmentMapper assignmentMapper;
    private final SubmissionMapper submissionMapper;
    private final QuestionMapper questionMapper;
    private final SubmissionAnswerMapper answerMapper;
    private final AnnouncementMapper announcementMapper;
    private final MonthlyStatMapper statMapper;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(ClassInfoMapper classMapper, StudentMapper studentMapper, TeacherMapper teacherMapper,
                           SysUserMapper userMapper, CourseMapper courseMapper, AssignmentMapper assignmentMapper,
                           SubmissionMapper submissionMapper, QuestionMapper questionMapper,
                           SubmissionAnswerMapper answerMapper, AnnouncementMapper announcementMapper,
                           MonthlyStatMapper statMapper, PasswordEncoder passwordEncoder) {
        this.classMapper = classMapper;
        this.studentMapper = studentMapper;
        this.teacherMapper = teacherMapper;
        this.userMapper = userMapper;
        this.courseMapper = courseMapper;
        this.assignmentMapper = assignmentMapper;
        this.submissionMapper = submissionMapper;
        this.questionMapper = questionMapper;
        this.answerMapper = answerMapper;
        this.announcementMapper = announcementMapper;
        this.statMapper = statMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedClassesAndStudents();
        seedTeachers();
        seedAdmin();
        seedDemoAccounts();
        seedDemoCourses();
    }

    private void seedClassesAndStudents() {
        if (classMapper.selectCount(null) > 0) return;
        Random random = new Random(20240101L);
        for (int classNo = 1; classNo <= 4; classNo++) {
            ClassInfo clazz = new ClassInfo();
            clazz.setName("2024级网络技术" + classNo + "班");
            clazz.setGradeYear(2024);
            clazz.setClassNo(classNo);
            clazz.setMajor("网络技术");
            int count = 40 + random.nextInt(21);
            clazz.setStudentCount(count);
            classMapper.insert(clazz);

            for (int i = 1; i <= count; i++) {
                Student student = new Student();
                student.setSno(String.format("2024%02d%02d", classNo, i));
                student.setName(String.format("%02d", i));
                student.setClassId(clazz.getId());
                studentMapper.insert(student);
            }
        }
    }

    private void seedTeachers() {
        if (teacherMapper.selectCount(null) > 0) return;
        String[][] data = {
                {"网络系统集成与运维", "詹昌明"},
                {"电子商务数据分析与应用", "潘洁仪"},
                {"数据结构", "陈富荣"},
                {"网络安全技术", "胡志浩"},
                {"移动应用开发", "朱俊鑫"},
                {"企业级项目开发", "张卫"}
        };
        for (int i = 0; i < data.length; i++) {
            Teacher teacher = new Teacher();
            teacher.setTno(String.format("HS0100%02d", i + 1));
            teacher.setSubject(data[i][0]);
            teacher.setName(data[i][1]);
            teacherMapper.insert(teacher);
        }
    }

    private void seedAdmin() {
        if (userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getCode, "admin")) != null) return;
        SysUser admin = new SysUser();
        admin.setCode("admin");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setName("系统管理员");
        admin.setRole("ADMIN");
        admin.setPhone("13800000000");
        admin.setEmail("admin@example.com");
        admin.setEnabled(true);
        admin.setCreatedAt(LocalDateTime.now());
        userMapper.insert(admin);
    }

    private void seedDemoAccounts() {
        if (userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getCode, "20240101")) == null) {
            Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>().eq(Student::getSno, "20240101"));
            if (student != null) {
                SysUser user = new SysUser();
                user.setCode(student.getSno());
                user.setPassword(passwordEncoder.encode("123456"));
                user.setName(student.getName());
                user.setRole("STUDENT");
                user.setPhone("13800000001");
                user.setEmail("20240101@example.com");
                user.setEnabled(true);
                user.setCreatedAt(LocalDateTime.now());
                userMapper.insert(user);
            }
        }
        if (userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getCode, "HS010001")) == null) {
            Teacher teacher = teacherMapper.selectOne(new LambdaQueryWrapper<Teacher>().eq(Teacher::getTno, "HS010001"));
            if (teacher != null) {
                SysUser user = new SysUser();
                user.setCode(teacher.getTno());
                user.setPassword(passwordEncoder.encode("123456"));
                user.setName(teacher.getName());
                user.setRole("TEACHER");
                user.setPhone("13800000002");
                user.setEmail("teacher@example.com");
                user.setEnabled(true);
                user.setCreatedAt(LocalDateTime.now());
                userMapper.insert(user);
            }
        }
    }

    private void seedDemoCourses() {
        if (courseMapper.selectCount(null) > 0) return;
        List<ClassInfo> classes = classMapper.selectList(new LambdaQueryWrapper<ClassInfo>().orderByAsc(ClassInfo::getClassNo));
        List<Teacher> teachers = teacherMapper.selectList(new LambdaQueryWrapper<Teacher>().orderByAsc(Teacher::getTno));
        if (classes.size() < 4 || teachers.size() < 6) return;

        Course c1 = createCourse("Web前端框架技术", teachers.get(0), classes.get(0), 2026, "上学期", "APPROVED", null);
        Course c2 = createCourse("电子商务数据分析与应用", teachers.get(1), classes.get(0), 2026, "下学期", "APPROVED", null);
        createCourse("数据结构", teachers.get(2), classes.get(0), 2026, "上学期", "PENDING", null);
        createCourse("网络安全技术", teachers.get(3), classes.get(1), 2025, "下学期", "APPROVED", null);

        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        Assignment a1 = createAssignment(c1, "实验一：Vue 环境搭建",
                "完成 Vue 3 开发环境搭建，提交实验报告与运行截图。", now.plusDays(10));
        Assignment a2 = createAssignment(c1, "实验二：响应式数据绑定",
                "使用 Vue 3 完成一个响应式数据绑定的小案例，提交源代码压缩包与实验报告。", now.plusDays(20));
        Assignment a3 = createAssignment(c1, "课堂练习：指令综合应用",
                "完成 v-if、v-for、v-model 的综合练习。", now.minusDays(3));
        createAssignment(c2, "期末大作业：数据分析报告",
                "提交一份电子商务数据分析报告。", now.minusMonths(3));

        Question q1 = createQuestion(a1, "SINGLE", "Vue 3 通过 CDN 引入的全局构建版本是（ ）。", 10,
                "vue.global.js", "vue.runtime.js", "vue.esm-browser.js", "vue.cjs.js", "A");
        Question q2 = createQuestion(a1, "MULTIPLE", "以下哪些属于 Vue 的常用指令？（多选）", 15,
                "v-if", "v-for", "v-model", "@click", "A,B,C");
        Question q3 = createQuestion(a1, "JUDGE", "v-model 可以实现表单元素与数据的双向绑定。（ ）", 10,
                null, null, null, null, "A");

        Announcement pinned = new Announcement();
        pinned.setCourseId(c1.getId());
        pinned.setTitle("课程通知：本周实验安排");
        pinned.setContent("本周五 19:00 进行实验二讲解，请提前完成实验一提交。");
        pinned.setPinned(true);
        pinned.setCreatedBy(teachers.get(0).getId());
        pinned.setCreatedAt(now.minusDays(1));
        announcementMapper.insert(pinned);

        Announcement normal = new Announcement();
        normal.setCourseId(c1.getId());
        normal.setTitle("作业提交提醒");
        normal.setContent("实验一截止时间为 10 天后，请合理安排时间。");
        normal.setPinned(false);
        normal.setCreatedBy(teachers.get(0).getId());
        normal.setCreatedAt(now.minusDays(2));
        announcementMapper.insert(normal);

        Student demo = studentMapper.selectOne(new LambdaQueryWrapper<Student>().eq(Student::getSno, "20240101"));
        if (demo != null) {
            Submission s1 = new Submission();
            s1.setAssignmentId(a1.getId());
            s1.setStudentId(demo.getId());
            s1.setContent("已完成环境搭建，运行截图见附件。");
            s1.setStatus("SUBMITTED");
            s1.setSubmittedAt(now.minusHours(3));
            s1.setUpdatedAt(now.minusHours(3));
            s1.setPublished(false);
            submissionMapper.insert(s1);

            answerMapper.insert(createAnswer(s1.getId(), q1.getId(), "A"));
            answerMapper.insert(createAnswer(s1.getId(), q2.getId(), "A,B,C"));
            answerMapper.insert(createAnswer(s1.getId(), q3.getId(), "A"));

            Submission s2 = new Submission();
            s2.setAssignmentId(a2.getId());
            s2.setStudentId(demo.getId());
            s2.setContent("完成响应式数据绑定案例，代码与报告已提交。");
            s2.setStatus("GRADED");
            s2.setScore(new BigDecimal("92"));
            s2.setTeacherComment("完成度高，代码规范，报告结构完整。");
            s2.setSubmittedAt(now.minusDays(2));
            s2.setUpdatedAt(now.minusDays(2));
            s2.setGradedAt(now.minusDays(1));
            s2.setGradedBy(teachers.get(0).getId());
            s2.setPublished(true);
            submissionMapper.insert(s2);
        }

        MonthlyStat stat = new MonthlyStat();
        stat.setClassId(classes.get(0).getId());
        stat.setStatMonth(now.minusMonths(1).toString().substring(0, 7));
        stat.setSubmissionRate(new BigDecimal("78.50"));
        stat.setCreatedAt(now);
        statMapper.insert(stat);
    }

    private Course createCourse(String name, Teacher teacher, ClassInfo clazz, int year, String term,
                                String status, String rejectReason) {
        Course course = new Course();
        course.setName(name);
        course.setTeacherId(teacher.getId());
        course.setTeacherName(teacher.getName());
        course.setClassId(clazz.getId());
        course.setCourseYear(year);
        course.setTermName(term);
        course.setStatus(status);
        course.setRejectReason(rejectReason);
        course.setCreatedAt(LocalDateTime.now());
        courseMapper.insert(course);
        return course;
    }

    private Question createQuestion(Assignment assignment, String type, String stem, int score,
                                    String a, String b, String c, String d, String correct) {
        Question question = new Question();
        question.setAssignmentId(assignment.getId());
        question.setType(type);
        question.setStem(stem);
        question.setScore(new BigDecimal(score));
        if ("JUDGE".equals(type)) {
            question.setOptionA("对");
            question.setOptionB("错");
        } else {
            question.setOptionA(a);
            question.setOptionB(b);
            question.setOptionC(c);
            question.setOptionD(d);
        }
        question.setCorrectAnswer(correct);
        question.setSortNo(Math.toIntExact(questionMapper.selectCount(new LambdaQueryWrapper<Question>()
                .eq(Question::getAssignmentId, assignment.getId()))) + 1);
        questionMapper.insert(question);
        return question;
    }

    private SubmissionAnswer createAnswer(Long submissionId, Long questionId, String answer) {
        SubmissionAnswer result = new SubmissionAnswer();
        result.setSubmissionId(submissionId);
        result.setQuestionId(questionId);
        result.setAnswer(answer);
        return result;
    }

    private Assignment createAssignment(Course course, String title, String requirement, LocalDateTime deadline) {        Assignment assignment = new Assignment();
        assignment.setCourseId(course.getId());
        assignment.setTitle(title);
        assignment.setRequirement(requirement);
        assignment.setDeadline(deadline);
        assignment.setMissingMarked(false);
        assignment.setCreatedBy(course.getTeacherId());
        assignment.setCreatedAt(LocalDateTime.now());
        assignmentMapper.insert(assignment);
        return assignment;
    }
}
