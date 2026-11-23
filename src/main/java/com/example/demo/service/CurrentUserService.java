package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Student;
import com.example.demo.entity.SysUser;
import com.example.demo.entity.Teacher;
import com.example.demo.mapper.StudentMapper;
import com.example.demo.mapper.SysUserMapper;
import com.example.demo.mapper.TeacherMapper;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final SysUserMapper userMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;

    public CurrentUserService(SysUserMapper userMapper, StudentMapper studentMapper, TeacherMapper teacherMapper) {
        this.userMapper = userMapper;
        this.studentMapper = studentMapper;
        this.teacherMapper = teacherMapper;
    }

    public SysUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getCode, auth.getName()));
    }

    public Student getStudent() {
        SysUser user = get();
        if (user == null || !"STUDENT".equals(user.getRole())) return null;
        return studentMapper.selectOne(new LambdaQueryWrapper<Student>().eq(Student::getSno, user.getCode()));
    }

    public Teacher getTeacher() {
        SysUser user = get();
        if (user == null || !"TEACHER".equals(user.getRole())) return null;
        return teacherMapper.selectOne(new LambdaQueryWrapper<Teacher>().eq(Teacher::getTno, user.getCode()));
    }
}
