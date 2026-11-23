package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.common.BusinessException;
import com.example.demo.entity.Announcement;
import com.example.demo.entity.Course;
import com.example.demo.entity.Student;
import com.example.demo.entity.Teacher;
import com.example.demo.mapper.AnnouncementMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AnnouncementService {

    private final AnnouncementMapper announcementMapper;
    private final CourseService courseService;
    private final CacheService cacheService;

    public AnnouncementService(AnnouncementMapper announcementMapper,
                               CourseService courseService, CacheService cacheService) {
        this.announcementMapper = announcementMapper;
        this.courseService = courseService;
        this.cacheService = cacheService;
    }

    @SuppressWarnings("unchecked")
    public List<Announcement> listForCourse(Long courseId) {
        String key = "announcements:course:" + courseId;
        Object cached = cacheService.get(key);
        if (cached instanceof List<?> list) return (List<Announcement>) list;
        List<Announcement> list = new ArrayList<>(announcementMapper.selectList(
                new LambdaQueryWrapper<Announcement>().eq(Announcement::getCourseId, courseId)));
        list.sort(Comparator.comparing((Announcement a) -> Boolean.TRUE.equals(a.getPinned())).reversed()
                .thenComparing(Announcement::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        cacheService.put(key, list, Duration.ofMinutes(5));
        return list;
    }

    @SuppressWarnings("unchecked")
    public List<Announcement> pinnedForStudent(Student student) {
        String key = "pinned:" + student.getClassId();
        Object cached = cacheService.get(key);
        if (cached instanceof List<?> list) return (List<Announcement>) list;
        List<Announcement> result = new ArrayList<>();
        for (Course course : courseService.listForStudent(student)) {
            for (Announcement announcement : listForCourse(course.getId())) {
                if (Boolean.TRUE.equals(announcement.getPinned())) {
                    announcement.setCourseName(course.getName());
                    result.add(announcement);
                }
            }
        }
        result.sort(Comparator.comparing(Announcement::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        if (result.size() > 3) result = new ArrayList<>(result.subList(0, 3));
        cacheService.put(key, result, Duration.ofMinutes(5));
        return result;
    }

    public void create(Course course, Teacher teacher, String title, String content, boolean pinned) {
        if (title == null || title.isBlank()) throw new BusinessException("公告标题不能为空");
        if (content == null || content.isBlank()) throw new BusinessException("公告内容不能为空");
        if (title.trim().length() > 200) throw new BusinessException("公告标题不能超过 200 个字符");
        Announcement announcement = new Announcement();
        announcement.setCourseId(course.getId());
        announcement.setTitle(title.trim());
        announcement.setContent(content.trim());
        announcement.setPinned(pinned);
        announcement.setCreatedBy(teacher.getId());
        announcement.setCreatedAt(LocalDateTime.now());
        announcementMapper.insert(announcement);
        evict(course);
    }

    public void delete(Long id, Teacher teacher) {
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) return;
        Course course = courseService.assertTeacher(announcement.getCourseId(), teacher);
        announcementMapper.deleteById(id);
        evict(course);
    }

    private void evict(Course course) {
        cacheService.evict("announcements:course:" + course.getId());
        cacheService.evict("pinned:" + course.getClassId());
    }
}
