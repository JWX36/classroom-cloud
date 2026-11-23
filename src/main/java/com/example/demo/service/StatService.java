package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Assignment;
import com.example.demo.entity.ClassInfo;
import com.example.demo.entity.Course;
import com.example.demo.entity.MonthlyStat;
import com.example.demo.entity.Student;
import com.example.demo.entity.Submission;
import com.example.demo.mapper.AssignmentMapper;
import com.example.demo.mapper.ClassInfoMapper;
import com.example.demo.mapper.CourseMapper;
import com.example.demo.mapper.MonthlyStatMapper;
import com.example.demo.mapper.StudentMapper;
import com.example.demo.mapper.SubmissionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatService {

    private final ClassInfoMapper classMapper;
    private final CourseMapper courseMapper;
    private final StudentMapper studentMapper;
    private final AssignmentMapper assignmentMapper;
    private final SubmissionMapper submissionMapper;
    private final MonthlyStatMapper statMapper;

    public StatService(ClassInfoMapper classMapper, CourseMapper courseMapper, StudentMapper studentMapper,
                       AssignmentMapper assignmentMapper, SubmissionMapper submissionMapper,
                       MonthlyStatMapper statMapper) {
        this.classMapper = classMapper;
        this.courseMapper = courseMapper;
        this.studentMapper = studentMapper;
        this.assignmentMapper = assignmentMapper;
        this.submissionMapper = submissionMapper;
        this.statMapper = statMapper;
    }

    @Transactional
    public int generateLastMonth() {
        YearMonth month = YearMonth.now().minusMonths(1);
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.atEndOfMonth().atTime(23, 59, 59);
        int count = 0;
        for (ClassInfo clazz : classMapper.selectList(null)) {
            List<Course> courses = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                    .eq(Course::getClassId, clazz.getId()));
            if (courses.isEmpty()) continue;
            List<Long> courseIds = courses.stream().map(Course::getId).toList();
            List<Assignment> assignments = assignmentMapper.selectList(new LambdaQueryWrapper<Assignment>()
                    .in(Assignment::getCourseId, courseIds)
                    .between(Assignment::getDeadline, start, end));
            if (assignments.isEmpty()) continue;
            List<Long> assignmentIds = assignments.stream().map(Assignment::getId).toList();
            long students = studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                    .eq(Student::getClassId, clazz.getId()));
            long submitted = submissionMapper.selectCount(new LambdaQueryWrapper<Submission>()
                    .in(Submission::getAssignmentId, assignmentIds)
                    .ne(Submission::getStatus, "MISSING"));
            long total = students * assignmentIds.size();
            BigDecimal rate = total == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(submitted * 100.0 / total).setScale(2, RoundingMode.HALF_UP);
            MonthlyStat existing = statMapper.selectOne(new LambdaQueryWrapper<MonthlyStat>()
                    .eq(MonthlyStat::getClassId, clazz.getId())
                    .eq(MonthlyStat::getStatMonth, month.toString()));
            if (existing == null) {
                MonthlyStat stat = new MonthlyStat();
                stat.setClassId(clazz.getId());
                stat.setStatMonth(month.toString());
                stat.setSubmissionRate(rate);
                stat.setCreatedAt(LocalDateTime.now());
                statMapper.insert(stat);
            } else {
                existing.setSubmissionRate(rate);
                statMapper.updateById(existing);
            }
            count++;
        }
        return count;
    }

    public List<MonthlyStat> listStats() {
        Map<Long, ClassInfo> classes = classMapper.selectList(null).stream()
                .collect(Collectors.toMap(ClassInfo::getId, c -> c));
        List<MonthlyStat> stats = statMapper.selectList(new LambdaQueryWrapper<MonthlyStat>()
                .orderByDesc(MonthlyStat::getStatMonth).orderByAsc(MonthlyStat::getClassId));
        for (MonthlyStat stat : stats) {
            ClassInfo clazz = classes.get(stat.getClassId());
            stat.setClassName(clazz == null ? "-" : clazz.getName());
        }
        return stats;
    }
}
