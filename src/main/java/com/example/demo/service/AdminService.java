package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.common.BusinessException;
import com.example.demo.entity.ClassInfo;
import com.example.demo.entity.Course;
import com.example.demo.entity.Student;
import com.example.demo.entity.Submission;
import com.example.demo.entity.SysUser;
import com.example.demo.entity.Teacher;
import com.example.demo.mapper.ClassInfoMapper;
import com.example.demo.mapper.CourseMapper;
import com.example.demo.mapper.StudentMapper;
import com.example.demo.mapper.SubmissionMapper;
import com.example.demo.mapper.SysUserMapper;
import com.example.demo.mapper.TeacherMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final ClassInfoMapper classMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final SysUserMapper userMapper;
    private final CourseMapper courseMapper;
    private final SubmissionMapper submissionMapper;

    public AdminService(ClassInfoMapper classMapper, StudentMapper studentMapper, TeacherMapper teacherMapper,
                        SysUserMapper userMapper, CourseMapper courseMapper, SubmissionMapper submissionMapper) {
        this.classMapper = classMapper;
        this.studentMapper = studentMapper;
        this.teacherMapper = teacherMapper;
        this.userMapper = userMapper;
        this.courseMapper = courseMapper;
        this.submissionMapper = submissionMapper;
    }

    public List<Student> students(Long classId, String keyword) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<Student>().orderByAsc(Student::getSno);
        if (classId != null) wrapper.eq(Student::getClassId, classId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Student::getSno, keyword).or().like(Student::getName, keyword));
        }
        return studentMapper.selectList(wrapper);
    }

    public List<Teacher> teachers(String keyword) {
        LambdaQueryWrapper<Teacher> wrapper = new LambdaQueryWrapper<Teacher>().orderByAsc(Teacher::getTno);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Teacher::getTno, keyword).or().like(Teacher::getName, keyword));
        }
        return teacherMapper.selectList(wrapper);
    }

    public Map<Long, ClassInfo> classMap() {
        return classMapper.selectList(null).stream().collect(Collectors.toMap(ClassInfo::getId, c -> c));
    }

    @Transactional
    public void addStudent(String sno, String name, Long classId) {
        if (sno == null || !sno.matches("^2024\\d{4}$")) {
            throw new BusinessException("学号格式应为 2024 + 班级号(2位) + 班内编号(2位)，例如 20240101");
        }
        if (classId == null || classMapper.selectById(classId) == null) throw new BusinessException("班级不存在");
        if (studentMapper.selectOne(new LambdaQueryWrapper<Student>().eq(Student::getSno, sno)) != null) {
            throw new BusinessException("学号已存在");
        }
        Student student = new Student();
        student.setSno(sno);
        student.setName(name == null || name.isBlank() ? sno.substring(6) : name.trim());
        student.setClassId(classId);
        studentMapper.insert(student);
        refreshClassCount(classId);
    }

    public void updateClass(Long id, String name, String major) {
        ClassInfo clazz = classMapper.selectById(id);
        if (clazz == null) throw new BusinessException("班级不存在");
        if (name == null || name.isBlank()) throw new BusinessException("班级名称不能为空");
        clazz.setName(name.trim());
        if (major != null && !major.isBlank()) clazz.setMajor(major.trim());
        classMapper.updateById(clazz);
    }

    public void updateStudent(Long id, String name) {        Student student = studentMapper.selectById(id);
        if (student == null) throw new BusinessException("学生不存在");
        if (name == null || name.isBlank()) throw new BusinessException("姓名不能为空");
        student.setName(name.trim());
        studentMapper.updateById(student);
    }

    @Transactional
    public void deleteStudent(Long id) {
        Student student = studentMapper.selectById(id);
        if (student == null) return;
        if (userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getCode, student.getSno())) != null) {
            throw new BusinessException("该学生已注册账号，请先在账号管理中删除账号");
        }
        long submissions = submissionMapper.selectCount(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getStudentId, id)
                .ne(Submission::getStatus, "MISSING"));
        if (submissions > 0) {
            throw new BusinessException("该学生名下有 " + submissions + " 条作业提交记录，删除会造成数据悬空，请先处理相关作业数据");
        }
        studentMapper.deleteById(id);
        refreshClassCount(student.getClassId());
    }

    @Transactional
    public int importStudents(Long classId, String text) {
        if (classId == null || classMapper.selectById(classId) == null) throw new BusinessException("班级不存在");
        if (text == null || text.isBlank()) throw new BusinessException("导入内容不能为空");
        int count = 0;
        for (String line : text.split("\\R")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("[,，\\s]+");
            String sno = parts[0].trim();
            if (!sno.matches("^2024\\d{4}$")) continue;
            if (studentMapper.selectOne(new LambdaQueryWrapper<Student>().eq(Student::getSno, sno)) != null) continue;
            Student student = new Student();
            student.setSno(sno);
            student.setName(parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : sno.substring(6));
            student.setClassId(classId);
            studentMapper.insert(student);
            count++;
        }
        refreshClassCount(classId);
        return count;
    }

    public void addTeacher(String tno, String name, String subject) {
        if (tno == null || !tno.matches("^HS\\d{6}$")) throw new BusinessException("教师编号格式应为 HS + 6 位数字");
        if (name == null || name.isBlank() || subject == null || subject.isBlank()) {
            throw new BusinessException("教师姓名和任教课程不能为空");
        }
        if (teacherMapper.selectOne(new LambdaQueryWrapper<Teacher>().eq(Teacher::getTno, tno)) != null) {
            throw new BusinessException("教师编号已存在");
        }
        Teacher teacher = new Teacher();
        teacher.setTno(tno);
        teacher.setName(name.trim());
        teacher.setSubject(subject.trim());
        teacherMapper.insert(teacher);
    }

    public void updateTeacher(Long id, String name, String subject) {
        Teacher teacher = teacherMapper.selectById(id);
        if (teacher == null) throw new BusinessException("教师不存在");
        if (name == null || name.isBlank() || subject == null || subject.isBlank()) {
            throw new BusinessException("教师姓名和任教课程不能为空");
        }
        teacher.setName(name.trim());
        teacher.setSubject(subject.trim());
        teacherMapper.updateById(teacher);
    }

    public long courseCount(Long teacherId) {
        return courseMapper.selectCount(new LambdaQueryWrapper<Course>().eq(Course::getTeacherId, teacherId));
    }

    private void refreshClassCount(Long classId) {
        ClassInfo clazz = classMapper.selectById(classId);
        if (clazz == null) return;
        clazz.setStudentCount(Math.toIntExact(studentMapper.selectCount(
                new LambdaQueryWrapper<Student>().eq(Student::getClassId, classId))));
        classMapper.updateById(clazz);
    }
}
