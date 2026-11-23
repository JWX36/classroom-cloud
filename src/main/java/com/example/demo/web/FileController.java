package com.example.demo.web;

import com.example.demo.common.BusinessException;
import com.example.demo.entity.Assignment;
import com.example.demo.entity.Course;
import com.example.demo.entity.Student;
import com.example.demo.entity.Submission;
import com.example.demo.entity.SysUser;
import com.example.demo.entity.Teacher;
import com.example.demo.mapper.AssignmentMapper;
import com.example.demo.mapper.CourseMapper;
import com.example.demo.mapper.SubmissionMapper;
import com.example.demo.service.CurrentUserService;
import com.example.demo.service.FileService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Controller
public class FileController {

    private final CurrentUserService currentUserService;
    private final FileService fileService;
    private final AssignmentMapper assignmentMapper;
    private final SubmissionMapper submissionMapper;
    private final CourseMapper courseMapper;

    public FileController(CurrentUserService currentUserService, FileService fileService,
                          AssignmentMapper assignmentMapper, SubmissionMapper submissionMapper,
                          CourseMapper courseMapper) {
        this.currentUserService = currentUserService;
        this.fileService = fileService;
        this.assignmentMapper = assignmentMapper;
        this.submissionMapper = submissionMapper;
        this.courseMapper = courseMapper;
    }

    @GetMapping("/files/assignment/{id}")
    public ResponseEntity<Resource> assignmentFile(@PathVariable Long id) {
        Assignment assignment = assignmentMapper.selectById(id);
        if (assignment == null || assignment.getAttachmentPath() == null) {
            throw new BusinessException("附件不存在");
        }
        Course course = courseMapper.selectById(assignment.getCourseId());
        checkCourseAccess(course);
        return download(assignment.getAttachmentPath(), assignment.getAttachmentName());
    }

    @GetMapping("/files/submission/{id}")
    public ResponseEntity<Resource> submissionFile(@PathVariable Long id) {
        Submission submission = submissionMapper.selectById(id);
        if (submission == null || submission.getFilePath() == null) {
            throw new BusinessException("附件不存在");
        }
        Assignment assignment = assignmentMapper.selectById(submission.getAssignmentId());
        Course course = assignment == null ? null : courseMapper.selectById(assignment.getCourseId());
        if (course == null) throw new BusinessException("课程不存在");
        SysUser user = currentUserService.get();
        if (user == null) throw new BusinessException("请先登录");
        if ("ADMIN".equals(user.getRole())) {
            return download(submission.getFilePath(), submission.getFileName());
        }
        if ("TEACHER".equals(user.getRole())) {
            Teacher teacher = currentUserService.getTeacher();
            if (teacher == null || !teacher.getId().equals(course.getTeacherId())) {
                throw new BusinessException("无权下载该附件");
            }
            return download(submission.getFilePath(), submission.getFileName());
        }
        Student student = currentUserService.getStudent();
        if (student == null || !student.getId().equals(submission.getStudentId())) {
            throw new BusinessException("无权下载该附件");
        }
        return download(submission.getFilePath(), submission.getFileName());
    }

    private void checkCourseAccess(Course course) {
        if (course == null) throw new BusinessException("课程不存在");
        SysUser user = currentUserService.get();
        if (user == null) throw new BusinessException("请先登录");
        if ("ADMIN".equals(user.getRole())) return;
        if ("TEACHER".equals(user.getRole())) {
            Teacher teacher = currentUserService.getTeacher();
            if (teacher == null || !teacher.getId().equals(course.getTeacherId())) {
                throw new BusinessException("无权下载该附件");
            }
            return;
        }
        Student student = currentUserService.getStudent();
        if (student == null || !student.getClassId().equals(course.getClassId())) {
            throw new BusinessException("无权下载该附件");
        }
        // 课程未审批/已驳回时不开放教师附件，避免学生提前拿到未审批课程的资料
        if (!"APPROVED".equals(course.getStatus())) {
            throw new BusinessException("课程尚未通过审批，附件暂不可下载");
        }
    }

    private ResponseEntity<Resource> download(String relative, String filename) {
        Path path = fileService.resolve(relative);
        if (path == null || !Files.exists(path)) throw new BusinessException("文件不存在");
        FileSystemResource resource = new FileSystemResource(path);
        String name = filename == null || filename.isBlank() ? path.getFileName().toString() : filename;
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(name, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }
}
