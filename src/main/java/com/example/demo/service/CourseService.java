package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.common.BusinessException;
import com.example.demo.entity.ClassInfo;
import com.example.demo.entity.Course;
import com.example.demo.entity.Student;
import com.example.demo.entity.Teacher;
import com.example.demo.mapper.ClassInfoMapper;
import com.example.demo.mapper.CourseMapper;
import com.example.demo.mapper.TeacherMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CourseService {

    private final CourseMapper courseMapper;
    private final ClassInfoMapper classMapper;
    private final TeacherMapper teacherMapper;
    private final CacheService cacheService;

    public CourseService(CourseMapper courseMapper, ClassInfoMapper classMapper,
                         TeacherMapper teacherMapper, CacheService cacheService) {
        this.courseMapper = courseMapper;
        this.classMapper = classMapper;
        this.teacherMapper = teacherMapper;
        this.cacheService = cacheService;
    }

    /** 列表分组：进行中 → 待审批 → 已驳回 → 已结束；组内按创建时间倒序。 */
    private static final java.util.Comparator<Course> COURSE_ORDER =
            java.util.Comparator.comparingInt(CourseService::groupRank)
                    .thenComparing(Course::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()));

    private static int groupRank(Course c) {
        if (c.isActive()) return 0;                              // 进行中
        if ("PENDING".equals(c.getStatus())) return 1;           // 待审批
        if ("REJECTED".equals(c.getStatus())) return 2;          // 已驳回
        return 3;                                                // 已结束
    }

    public List<ClassInfo> classes() {
        return classMapper.selectList(new LambdaQueryWrapper<ClassInfo>().orderByAsc(ClassInfo::getClassNo));
    }

    public List<Teacher> teachers() {
        return teacherMapper.selectList(new LambdaQueryWrapper<Teacher>().orderByAsc(Teacher::getTno));
    }

    public Map<Long, ClassInfo> classMap() {
        return classes().stream().collect(Collectors.toMap(ClassInfo::getId, Function.identity()));
    }

    @SuppressWarnings("unchecked")
    public List<Course> listForStudent(Student student) {
        String key = "student:courses:" + student.getClassId();
        Object cached = cacheService.get(key);
        if (cached instanceof List<?> list) {
            return (List<Course>) list;
        }
        List<Course> courses = new ArrayList<>(courseMapper.selectList(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getClassId, student.getClassId())
                        .eq(Course::getStatus, "APPROVED")));
        fillClassNames(courses);
        courses.sort(COURSE_ORDER);
        cacheService.put(key, courses, Duration.ofMinutes(5));
        return courses;
    }

    @SuppressWarnings("unchecked")
    public List<Course> listForTeacher(Teacher teacher) {
        String key = "teacher:courses:" + teacher.getId();
        Object cached = cacheService.get(key);
        if (cached instanceof List<?> list) {
            return (List<Course>) list;
        }
        List<Course> courses = new ArrayList<>(courseMapper.selectList(
                new LambdaQueryWrapper<Course>().eq(Course::getTeacherId, teacher.getId())));
        fillClassNames(courses);
        courses.sort(COURSE_ORDER);
        cacheService.put(key, courses, Duration.ofMinutes(5));
        return courses;
    }

    public List<Course> listAll() {
        List<Course> courses = new ArrayList<>(courseMapper.selectList(
                new LambdaQueryWrapper<Course>().orderByDesc(Course::getCreatedAt)));
        fillClassNames(courses);
        courses.sort(COURSE_ORDER);
        return courses;
    }

    public List<Course> listPending() {
        List<Course> courses = new ArrayList<>(courseMapper.selectList(
                new LambdaQueryWrapper<Course>().eq(Course::getStatus, "PENDING").orderByDesc(Course::getCreatedAt)));
        fillClassNames(courses);
        return courses;
    }

    public Course get(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) throw new BusinessException("课程不存在");
        ClassInfo clazz = classMapper.selectById(course.getClassId());
        course.setClassName(clazz == null ? "" : clazz.getName());
        return course;
    }

    public Course create(Teacher teacher, String name, String teacherName, int year, String term, Long classId) {
        if (name == null || name.isBlank()) throw new BusinessException("课程名称不能为空");
        if (teacherName == null || teacherName.isBlank()) throw new BusinessException("教师姓名不能为空");
        if (name.trim().length() > 128) throw new BusinessException("课程名称不能超过 128 个字符");
        if (teacherName.trim().length() > 64) throw new BusinessException("教师姓名不能超过 64 个字符");
        if (classId == null || classMapper.selectById(classId) == null) throw new BusinessException("请选择班级");
        Course course = new Course();
        course.setName(name.trim());
        course.setTeacherId(teacher.getId());
        course.setTeacherName(teacherName.trim());
        course.setClassId(classId);
        course.setCourseYear(year);
        course.setTermName(term);
        course.setStatus("PENDING");
        course.setCreatedAt(LocalDateTime.now());
        courseMapper.insert(course);
        evictTeacher(teacher.getId(), classId);
        return course;
    }

    public void approve(Long id) {
        Course course = get(id);
        if (!"PENDING".equals(course.getStatus())) throw new BusinessException("该课程不是待审批状态");
        course.setStatus("APPROVED");
        course.setRejectReason(null);
        courseMapper.updateById(course);
        evictTeacher(course.getTeacherId(), course.getClassId());
    }

    public void reject(Long id, String reason) {
        Course course = get(id);
        course.setStatus("REJECTED");
        String finalReason = reason == null || reason.isBlank() ? "不符合开课要求" : reason.trim();
        // 数据库列宽 255，超长直接截断避免 500
        if (finalReason.length() > 255) finalReason = finalReason.substring(0, 255);
        course.setRejectReason(finalReason);
        courseMapper.updateById(course);
        evictTeacher(course.getTeacherId(), course.getClassId());
    }

    public Course assertTeacher(Long courseId, Teacher teacher) {
        Course course = get(courseId);
        if (!course.getTeacherId().equals(teacher.getId())) {
            throw new BusinessException("无权操作该课程");
        }
        return course;
    }

    public Course assertStudent(Long courseId, Student student) {
        Course course = get(courseId);
        if (!course.getClassId().equals(student.getClassId())) {
            throw new BusinessException("无权查看该课程");
        }
        if (!"APPROVED".equals(course.getStatus())) {
            throw new BusinessException("课程尚未通过审批");
        }
        return course;
    }

    public void evictTeacher(Long teacherId, Long classId) {
        cacheService.evict("teacher:courses:" + teacherId);
        cacheService.evict("student:courses:" + classId);
        cacheService.evict("pinned:" + classId);
        cacheService.evict("admin:courses");
    }

    private void fillClassNames(List<Course> courses) {
        Map<Long, ClassInfo> map = classMap();
        for (Course course : courses) {
            ClassInfo clazz = map.get(course.getClassId());
            course.setClassName(clazz == null ? "" : clazz.getName());
        }
    }
}
