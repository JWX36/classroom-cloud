package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.demo.common.BusinessException;
import com.example.demo.entity.Course;
import com.example.demo.entity.Student;
import com.example.demo.entity.SysUser;
import com.example.demo.entity.Teacher;
import com.example.demo.form.PasswordForm;
import com.example.demo.form.ProfileForm;
import com.example.demo.form.RegisterForm;
import com.example.demo.mapper.CourseMapper;
import com.example.demo.mapper.StudentMapper;
import com.example.demo.mapper.SysUserMapper;
import com.example.demo.mapper.TeacherMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final CourseMapper courseMapper;
    private final PasswordEncoder passwordEncoder;
    private final FileService fileService;

    public AuthService(SysUserMapper userMapper, StudentMapper studentMapper, TeacherMapper teacherMapper,
                       CourseMapper courseMapper, PasswordEncoder passwordEncoder, FileService fileService) {
        this.userMapper = userMapper;
        this.studentMapper = studentMapper;
        this.teacherMapper = teacherMapper;
        this.courseMapper = courseMapper;
        this.passwordEncoder = passwordEncoder;
        this.fileService = fileService;
    }

    public record RosterInfo(String code, String name, String role) {
    }

    public RosterInfo findRoster(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) return null;
        String code = rawCode.trim();
        Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>().eq(Student::getSno, code));
        if (student != null) return new RosterInfo(student.getSno(), student.getName(), "STUDENT");
        Teacher teacher = teacherMapper.selectOne(new LambdaQueryWrapper<Teacher>().eq(Teacher::getTno, code));
        if (teacher != null) return new RosterInfo(teacher.getTno(), teacher.getName(), "TEACHER");
        return null;
    }

    public RosterInfo verifyForRegister(String code) {
        RosterInfo roster = findRoster(code);
        if (roster == null) {
            throw new BusinessException("学号/教师编号不存在，请核对后重新输入");
        }
        if (userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getCode, roster.code())) != null) {
            throw new BusinessException("该学号/教师编号已经注册，请直接登录");
        }
        return roster;
    }

    @Transactional
    public SysUser register(RegisterForm form) {
        RosterInfo roster = verifyForRegister(form.getCode());
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new BusinessException("两次输入的密码不一致");
        }
        SysUser user = new SysUser();
        user.setCode(roster.code());
        user.setName(roster.name());
        user.setRole(roster.role());
        user.setPhone(form.getPhone());
        user.setEmail(form.getEmail() == null || form.getEmail().isBlank() ? null : form.getEmail().trim());
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        if (form.getAvatar() != null && !form.getAvatar().isEmpty()) {
            user.setAvatar(fileService.storeAvatar(form.getAvatar()));
        }
        userMapper.insert(user);
        return user;
    }

    @Transactional
    public void updateProfile(SysUser user, ProfileForm form) {
        String newName = form.getName().trim();
        user.setName(newName);
        user.setPhone(form.getPhone());
        user.setEmail(form.getEmail() == null || form.getEmail().isBlank() ? null : form.getEmail().trim());
        if (form.getAvatar() != null && !form.getAvatar().isEmpty()) {
            String old = user.getAvatar();
            user.setAvatar(fileService.storeAvatar(form.getAvatar()));
            fileService.delete(old);
        }
        userMapper.updateById(user);

        if ("STUDENT".equals(user.getRole())) {
            Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                    .eq(Student::getSno, user.getCode()));
            if (student != null) {
                student.setName(newName);
                studentMapper.updateById(student);
            }
        } else if ("TEACHER".equals(user.getRole())) {
            Teacher teacher = teacherMapper.selectOne(new LambdaQueryWrapper<Teacher>()
                    .eq(Teacher::getTno, user.getCode()));
            if (teacher != null) {
                teacher.setName(newName);
                teacherMapper.updateById(teacher);
                courseMapper.update(null, new LambdaUpdateWrapper<Course>()
                        .eq(Course::getTeacherId, teacher.getId())
                        .set(Course::getTeacherName, newName));
            }
        }
    }

    /** 独立更换头像：裁剪后的图片直接上传生效，旧文件清理。 */
    @Transactional
    public void updateAvatar(SysUser user, MultipartFile avatar) {
        String path = fileService.storeAvatar(avatar);
        String old = user.getAvatar();
        user.setAvatar(path);
        userMapper.updateById(user);
        fileService.delete(old);
    }

    public void changePassword(SysUser user, PasswordForm form) {
        if (!passwordEncoder.matches(form.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码不正确");
        }
        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            throw new BusinessException("两次输入的新密码不一致");
        }
        user.setPassword(passwordEncoder.encode(form.getNewPassword()));
        userMapper.updateById(user);
    }

    public List<SysUser> accounts() {
        return accounts(null);
    }

    /** 账号列表；q 非空时按账号（code）或姓名（name）模糊匹配 */
    public List<SysUser> accounts(String q) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .orderByAsc(SysUser::getRole).orderByAsc(SysUser::getCode);
        if (q != null && !q.isBlank()) {
            String kw = q.trim();
            wrapper.and(w -> w.like(SysUser::getCode, kw).or().like(SysUser::getName, kw));
        }
        return userMapper.selectList(wrapper);
    }

    public void deleteAccount(Long id, SysUser admin) {
        if (id == null) throw new BusinessException("账号不存在");
        SysUser target = userMapper.selectById(id);
        if (target == null) throw new BusinessException("账号不存在");
        if (target.getId().equals(admin.getId())) throw new BusinessException("不能删除当前登录的管理员账号");
        if (target.getAvatar() != null) fileService.delete(target.getAvatar());
        userMapper.deleteById(id);
    }
}
