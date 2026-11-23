package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Assignment;
import com.example.demo.entity.Course;
import com.example.demo.entity.Student;
import com.example.demo.entity.Submission;
import com.example.demo.entity.SysUser;
import com.example.demo.mapper.AssignmentMapper;
import com.example.demo.mapper.CourseMapper;
import com.example.demo.mapper.StudentMapper;
import com.example.demo.mapper.SubmissionMapper;
import com.example.demo.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    private final CourseMapper courseMapper;
    private final AssignmentMapper assignmentMapper;
    private final StudentMapper studentMapper;
    private final SubmissionMapper submissionMapper;
    private final SysUserMapper userMapper;
    private final AssignmentService assignmentService;
    private final StatService statService;
    private final MailService mailService;

    public ScheduledTasks(CourseMapper courseMapper, AssignmentMapper assignmentMapper,
                          StudentMapper studentMapper, SubmissionMapper submissionMapper,
                          SysUserMapper userMapper, AssignmentService assignmentService,
                          StatService statService, MailService mailService) {
        this.courseMapper = courseMapper;
        this.assignmentMapper = assignmentMapper;
        this.studentMapper = studentMapper;
        this.submissionMapper = submissionMapper;
        this.userMapper = userMapper;
        this.assignmentService = assignmentService;
        this.statService = statService;
        this.mailService = mailService;
    }

    /** 每天凌晨 2 点：结束过期课程、标记未提交、发送截止前 1 天提醒。 */
    @Scheduled(cron = "0 0 2 * * *")
    public void dailyJob() {
        markEndedCourses();
        markMissingSubmissions();
        sendDeadlineReminders();
    }

    /** 每月 1 日凌晨 3 点：生成上月班级提交率。 */
    @Scheduled(cron = "0 0 3 1 * *")
    public void monthlyJob() {
        int count = statService.generateLastMonth();
        log.info("[定时任务] 已生成 {} 个班级的上月提交率统计", count);
    }

    public void markEndedCourses() {
        List<Course> approved = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .eq(Course::getStatus, "APPROVED"));
        for (Course course : approved) {
            if (course.isEnded()) {
                course.setStatus("ENDED");
                courseMapper.updateById(course);
            }
        }
    }

    public void markMissingSubmissions() {
        List<Assignment> expired = assignmentMapper.selectList(new LambdaQueryWrapper<Assignment>()
                .lt(Assignment::getDeadline, LocalDateTime.now())
                .eq(Assignment::getMissingMarked, false));
        for (Assignment assignment : expired) {
            int count = assignmentService.markMissing(assignment);
            if (count > 0) log.info("[定时任务] 作业 {} 标记 {} 名学生未提交", assignment.getId(), count);
        }
    }

    public void sendDeadlineReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<Assignment> soon = assignmentMapper.selectList(new LambdaQueryWrapper<Assignment>()
                .between(Assignment::getDeadline, now, now.plusDays(1)));
        for (Assignment assignment : soon) {
            Course course = courseMapper.selectById(assignment.getCourseId());
            if (course == null) continue;
            List<Student> students = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                    .eq(Student::getClassId, course.getClassId()));
            List<Submission> submissions = submissionMapper.selectList(new LambdaQueryWrapper<Submission>()
                    .eq(Submission::getAssignmentId, assignment.getId()));
            Map<Long, Submission> map = submissions.stream()
                    .collect(Collectors.toMap(Submission::getStudentId, s -> s));
            for (Student student : students) {
                if (map.containsKey(student.getId())) continue;
                SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getCode, student.getSno()));
                if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
                    mailService.send(user.getEmail(), "作业截止提醒：" + assignment.getTitle(),
                            "课程《" + course.getName() + "》的作业《" + assignment.getTitle()
                                    + "》将在 1 天内截止，请尽快提交。");
                }
            }
        }
    }
}
