package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.common.BusinessException;
import com.example.demo.entity.Assignment;
import com.example.demo.entity.AssignmentScoreConfig;
import com.example.demo.entity.Course;
import com.example.demo.entity.Question;
import com.example.demo.entity.Student;
import com.example.demo.entity.Submission;
import com.example.demo.entity.SubmissionAnswer;
import com.example.demo.entity.SubmissionGradeHistory;
import com.example.demo.entity.SubmissionSubjective;
import com.example.demo.entity.SysUser;
import com.example.demo.entity.Teacher;
import com.example.demo.mapper.AssignmentMapper;
import com.example.demo.mapper.AssignmentScoreConfigMapper;
import com.example.demo.mapper.QuestionMapper;
import com.example.demo.mapper.StudentMapper;
import com.example.demo.mapper.SubmissionAnswerMapper;
import com.example.demo.mapper.SubmissionMapper;
import com.example.demo.mapper.SubmissionGradeHistoryMapper;
import com.example.demo.mapper.SubmissionSubjectiveMapper;
import com.example.demo.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AssignmentService {

    private final AssignmentMapper assignmentMapper;
    private final SubmissionMapper submissionMapper;
    private final QuestionMapper questionMapper;
    private final SubmissionAnswerMapper answerMapper;
    private final AssignmentScoreConfigMapper scoreConfigMapper;
    private final SubmissionSubjectiveMapper subjectiveMapper;
    private final SubmissionGradeHistoryMapper gradeHistoryMapper;
    private final StudentMapper studentMapper;
    private final SysUserMapper userMapper;
    private final CourseService courseService;
    private final FileService fileService;
    private final MailService mailService;
    private final CacheService cacheService;

    public AssignmentService(AssignmentMapper assignmentMapper, SubmissionMapper submissionMapper,
                             QuestionMapper questionMapper, SubmissionAnswerMapper answerMapper,
                             AssignmentScoreConfigMapper scoreConfigMapper,
                             SubmissionSubjectiveMapper subjectiveMapper,
                             SubmissionGradeHistoryMapper gradeHistoryMapper,
                             StudentMapper studentMapper, SysUserMapper userMapper,
                             CourseService courseService, FileService fileService, MailService mailService,
                             CacheService cacheService) {
        this.assignmentMapper = assignmentMapper;
        this.submissionMapper = submissionMapper;
        this.questionMapper = questionMapper;
        this.answerMapper = answerMapper;
        this.scoreConfigMapper = scoreConfigMapper;
        this.subjectiveMapper = subjectiveMapper;
        this.gradeHistoryMapper = gradeHistoryMapper;
        this.studentMapper = studentMapper;
        this.userMapper = userMapper;
        this.courseService = courseService;
        this.fileService = fileService;
        this.mailService = mailService;
        this.cacheService = cacheService;
    }

    public List<Assignment> listForCourse(Course course) {
        List<Assignment> assignments = assignmentMapper.selectList(
                new LambdaQueryWrapper<Assignment>().eq(Assignment::getCourseId, course.getId())
                        .orderByDesc(Assignment::getDeadline));
        if (assignments.isEmpty()) return assignments;
        List<Long> ids = assignments.stream().map(Assignment::getId).toList();
        List<Submission> submissions = submissionMapper.selectList(
                new LambdaQueryWrapper<Submission>().in(Submission::getAssignmentId, ids));
        Map<Long, List<Submission>> byAssignment = submissions.stream()
                .collect(Collectors.groupingBy(Submission::getAssignmentId));
        int total = Math.toIntExact(studentMapper.selectCount(
                new LambdaQueryWrapper<Student>().eq(Student::getClassId, course.getClassId())));
        for (Assignment assignment : assignments) {
            List<Submission> list = byAssignment.getOrDefault(assignment.getId(), List.of());
            assignment.setTotalStudents(total);
            assignment.setSubmittedCount(list.stream().filter(s -> !"MISSING".equals(s.getStatus())).count());
            assignment.setGradedCount(list.stream().filter(s -> "GRADED".equals(s.getStatus())).count());
            assignment.setCourseName(course.getName());
        }
        return assignments;
    }

    @SuppressWarnings("unchecked")
    public List<Assignment> listForTeacherCourse(Course course) {
        String key = "assignments:teacher:" + course.getId();
        Object cached = cacheService.get(key);
        if (cached instanceof List<?> list) return (List<Assignment>) list;
        List<Assignment> assignments = listForCourse(course);
        cacheService.put(key, assignments, java.time.Duration.ofMinutes(2));
        return assignments;
    }

    private void evictAssignments(Long courseId) {
        cacheService.evict("assignments:teacher:" + courseId);
    }

    public List<Assignment> listForStudent(Course course, Student student) {
        List<Assignment> assignments = listForCourse(course);
        if (assignments.isEmpty()) return assignments;
        Map<Long, Submission> mine = submissionMapper.selectList(
                        new LambdaQueryWrapper<Submission>()
                                .eq(Submission::getStudentId, student.getId())
                                .in(Submission::getAssignmentId,
                                        assignments.stream().map(Assignment::getId).toList()))
                .stream().collect(Collectors.toMap(Submission::getAssignmentId, s -> s));
        for (Assignment assignment : assignments) {
            assignment.setMySubmission(mine.get(assignment.getId()));
        }
        return assignments;
    }

    public Assignment get(Long id) {
        Assignment assignment = assignmentMapper.selectById(id);
        if (assignment == null) throw new BusinessException("作业不存在");
        List<Question> questions = questions(id);
        assignment.setQuestions(questions);
        AssignmentScoreConfig config = scoreConfig(id);
        assignment.setScoreConfig(config);
        java.math.BigDecimal objectiveFull = ScoreCalculator.sum(questions);
        assignment.setObjectiveFull(objectiveFull);
        assignment.setSubjectiveFull(config.isCustom()
                ? config.subjectivePercent()
                : ScoreCalculator.FULL_SCORE.subtract(objectiveFull).max(java.math.BigDecimal.ZERO));
        return assignment;
    }

    public List<Question> questions(Long assignmentId) {
        List<Question> questions = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getAssignmentId, assignmentId).orderByAsc(Question::getSortNo));
        ScoreCalculator.apply(scoreConfig(assignmentId), questions);
        for (Question question : questions) {
            List<String> keys = new java.util.ArrayList<>();
            if (question.getCorrectAnswer() != null) {
                for (String key : question.getCorrectAnswer().split(",")) {
                    if (!key.isBlank()) keys.add(key.trim().toUpperCase());
                }
            }
            question.setCorrectKeys(keys);
        }
        return questions;
    }

    /** 读取作业评分方案，没有配置时按“平均分配”处理。 */
    public AssignmentScoreConfig scoreConfig(Long assignmentId) {
        AssignmentScoreConfig config = scoreConfigMapper.selectById(assignmentId);
        if (config == null) return AssignmentScoreConfig.even(assignmentId);
        if (config.getMode() == null || config.getMode().isBlank()) config.setMode(AssignmentScoreConfig.EVEN);
        if (config.getSinglePercent() == null) config.setSinglePercent(java.math.BigDecimal.ZERO);
        if (config.getMultiplePercent() == null) config.setMultiplePercent(java.math.BigDecimal.ZERO);
        if (config.getJudgePercent() == null) config.setJudgePercent(java.math.BigDecimal.ZERO);
        if (config.getAttachmentPercent() == null) config.setAttachmentPercent(java.math.BigDecimal.ZERO);
        return config;
    }

    public void saveScoreConfig(Long assignmentId, AssignmentScoreConfig config) {
        if (config == null) return;
        config.setAssignmentId(assignmentId);
        if (config.getMode() == null || config.getMode().isBlank()) config.setMode(AssignmentScoreConfig.EVEN);
        if (scoreConfigMapper.selectById(assignmentId) == null) {
            scoreConfigMapper.insert(config);
        } else {
            scoreConfigMapper.updateById(config);
        }
    }

    /**
     * 校验按题型自定义的占比：每项 0~100，合计不超过 100；
     * 设有占比的题型必须真的有题目，否则分数无处可分。
     */
    public void validateScoreConfig(AssignmentScoreConfig config, List<Question> questions) {
        // 注意：即使本次作业没有题目（纯附件型），占比合计也要校验，防止"多选5+判断10+附件100=115%"被放行
        if (config == null || !config.isCustom()) return;
        boolean hasQuestions = questions != null && !questions.isEmpty();
        java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
        for (String type : ScoreCalculator.TYPES) {
            java.math.BigDecimal percent = config.percentOf(type);
            if (percent.compareTo(java.math.BigDecimal.ZERO) < 0
                    || percent.compareTo(ScoreCalculator.FULL_SCORE) > 0) {
                throw new BusinessException("各题型占比必须在 0~100 之间");
            }
            if (!hasQuestions) {
                if (percent.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    throw new BusinessException("本次作业没有添加题目，请把" + typeText(type) + "占比调回 0，或先添加题目");
                }
                continue;
            }
            boolean hasType = questions.stream().anyMatch(q -> type.equals(q.getType()));
            if (percent.compareTo(java.math.BigDecimal.ZERO) > 0 && !hasType) {
                throw new BusinessException("设置了" + typeText(type) + "占比，但本次作业没有该类题目");
            }
            sum = sum.add(percent);
        }
        java.math.BigDecimal attach = AssignmentScoreConfig.nz(config.getAttachmentPercent());
        if (attach.compareTo(java.math.BigDecimal.ZERO) < 0 || attach.compareTo(ScoreCalculator.FULL_SCORE) > 0) {
            throw new BusinessException("附件占比必须在 0~100 之间");
        }
        sum = sum.add(attach);
        if (sum.compareTo(ScoreCalculator.FULL_SCORE) > 0) {
            throw new BusinessException("各题型与附件占比合计不能超过 100%（当前 " + sum.stripTrailingZeros().toPlainString() + "%）");
        }
    }

    private String typeText(String type) {
        if ("MULTIPLE".equals(type)) return "多选题";
        if ("JUDGE".equals(type)) return "判断题";
        return "单选题";
    }

    /** 题目内容是否与库中一致（忽略 id 与分值，分值由评分方案在读取时推导）。 */
    public boolean questionsUnchanged(Long assignmentId, List<Question> incoming) {
        List<Question> current = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getAssignmentId, assignmentId).orderByAsc(Question::getSortNo));
        if (current.size() != incoming.size()) return false;
        for (int i = 0; i < current.size(); i++) {
            Question a = current.get(i);
            Question b = incoming.get(i);
            if (!plainEquals(a.getType(), b.getType()) || !plainEquals(a.getStem(), b.getStem())
                    || !plainEquals(a.getOptionA(), b.getOptionA()) || !plainEquals(a.getOptionB(), b.getOptionB())
                    || !plainEquals(a.getOptionC(), b.getOptionC()) || !plainEquals(a.getOptionD(), b.getOptionD())
                    || !textEquals(normalizeKeys(a.getCorrectAnswer()), normalizeKeys(b.getCorrectAnswer()))) {
                return false;
            }
        }
        return true;
    }

    private String normalizeKeys(String value) {
        if (value == null) return "";
        String[] parts = value.replace(" ", "").toUpperCase().split(",");
        java.util.Arrays.sort(parts);
        return String.join(",", parts);
    }

    private boolean textEquals(String a, String b) {
        return normalizeKeys(a).equals(normalizeKeys(b));
    }

    private boolean plainEquals(String a, String b) {
        return (a == null ? "" : a.trim()).equals(b == null ? "" : b.trim());
    }

    /**
     * 保存题目。
     * @return true 表示题目内容有变化，已重建题目并清除学生作答；
     *         false 表示题目未变，不做任何改动（保留学生作答与已批改状态）。
     */
    /** 题型排序权重：单选题 → 多选题 → 判断题。 */
    private static int typeOrder(Question q) {
        String type = q.getType() == null ? "" : q.getType();
        if ("SINGLE".equals(type)) return 0;
        if ("MULTIPLE".equals(type)) return 1;
        if ("JUDGE".equals(type)) return 2;
        return 3;
    }

    @Transactional
    public boolean saveQuestions(Long assignmentId, List<Question> questions) {
        if (questionsUnchanged(assignmentId, questions)) {
            // 内容未变：若顺序不符合题型排序，仅调整 sortNo（不动题目内容与学生作答）
            List<Question> current = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                    .eq(Question::getAssignmentId, assignmentId).orderByAsc(Question::getSortNo));
            List<Question> desired = new ArrayList<>(current);
            desired.sort(java.util.Comparator.<Question>comparingInt(AssignmentService::typeOrder));
            boolean orderChanged = false;
            for (int i = 0; i < current.size(); i++) {
                if (!current.get(i).getId().equals(desired.get(i).getId())) { orderChanged = true; break; }
            }
            if (orderChanged) {
                for (int i = 0; i < desired.size(); i++) {
                    Question q = desired.get(i);
                    q.setSortNo(i + 1);
                    questionMapper.updateById(q);
                }
            }
            return false;
        }
        ScoreCalculator.apply(scoreConfig(assignmentId), questions);
        List<Submission> submissions = submissionMapper.selectList(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getAssignmentId, assignmentId));
        for (Submission submission : submissions) {
            answerMapper.delete(new LambdaQueryWrapper<SubmissionAnswer>()
                    .eq(SubmissionAnswer::getSubmissionId, submission.getId()));
            if ("GRADED".equals(submission.getStatus())) {
                archiveGrade(submission);
                // 题目变了，原客观分失真：退回待批改，教师需要重新批改（主观分保留，附件未变）
                submission.setStatus("SUBMITTED");
                submission.setScore(null);
                submission.setTeacherComment(null);
                submission.setGradedAt(null);
                submission.setGradedBy(null);
                submission.setPublished(false);
                submissionMapper.updateById(submission);
            }
        }
        questionMapper.delete(new LambdaQueryWrapper<Question>().eq(Question::getAssignmentId, assignmentId));
        // 按题型重排：单选题 → 多选题 → 判断题（同题型内保持教师添加顺序）
        List<Question> sorted = new ArrayList<>(questions);
        sorted.sort(java.util.Comparator.<Question>comparingInt(AssignmentService::typeOrder));
        int index = 1;
        for (Question question : sorted) {
            question.setId(null);
            question.setAssignmentId(assignmentId);
            question.setSortNo(index++);
            questionMapper.insert(question);
        }
        return true;
    }

    /** 把一份已批改的成绩归档进历史，再被覆盖之前调用。 */
    private void archiveGrade(Submission submission) {
        if (submission == null || submission.getId() == null) return;
        boolean graded = "GRADED".equals(submission.getStatus()) && submission.getScore() != null;
        if (!graded) return;
        SubmissionGradeHistory history = new SubmissionGradeHistory();
        history.setSubmissionId(submission.getId());
        history.setScore(submission.getScore());
        history.setTeacherComment(submission.getTeacherComment());
        history.setGradedAt(submission.getGradedAt());
        history.setGradedBy(submission.getGradedBy());
        history.setAttemptSubmittedAt(submission.getSubmittedAt());
        history.setCreatedAt(LocalDateTime.now());
        gradeHistoryMapper.insert(history);
    }

    /** 某份提交的历史成绩，按批阅时间倒序。 */
    public List<SubmissionGradeHistory> gradeHistory(Long submissionId) {
        return gradeHistoryMapper.selectList(new LambdaQueryWrapper<SubmissionGradeHistory>()
                .eq(SubmissionGradeHistory::getSubmissionId, submissionId)
                .orderByDesc(SubmissionGradeHistory::getGradedAt));
    }

    public java.math.BigDecimal subjectiveScore(Long submissionId) {
        SubmissionSubjective row = subjectiveMapper.selectById(submissionId);
        return row == null ? null : row.getScore();
    }

    public void saveSubjectiveScore(Long submissionId, java.math.BigDecimal score) {
        if (score == null) return;
        SubmissionSubjective row = subjectiveMapper.selectById(submissionId);
        boolean create = row == null;
        if (create) row = new SubmissionSubjective();
        row.setSubmissionId(submissionId);
        row.setScore(score);
        row.setUpdatedAt(LocalDateTime.now());
        if (create) subjectiveMapper.insert(row);
        else subjectiveMapper.updateById(row);
    }

    /** 给提交记录补上客观/主观分值，供页面展示。 */
    public void decorateSubmission(Submission submission, Assignment assignment, Map<Long, String> answers) {
        if (submission == null || assignment == null) return;
        java.math.BigDecimal objective = objectiveScore(assignment, answers == null ? Map.of() : answers);
        submission.setObjectiveScore(objective);
        submission.setObjectiveFull(assignment.getObjectiveFull());
        submission.setSubjectiveFull(assignment.getSubjectiveFull());
        java.math.BigDecimal subjective = subjectiveScore(submission.getId());
        if (subjective == null && submission.getScore() != null) {
            // 兼容旧数据：已发布成绩但没有拆分记录时，反推主观部分
            subjective = submission.getScore().subtract(objective);
            if (subjective.compareTo(java.math.BigDecimal.ZERO) < 0) subjective = java.math.BigDecimal.ZERO;
            if (assignment.getSubjectiveFull() != null
                    && subjective.compareTo(assignment.getSubjectiveFull()) > 0) {
                subjective = assignment.getSubjectiveFull();
            }
        }
        submission.setSubjectiveScore(subjective);
    }

    public Map<Long, String> answersForSubmission(Long submissionId) {
        Map<Long, String> map = new HashMap<>();
        for (SubmissionAnswer answer : answerMapper.selectList(new LambdaQueryWrapper<SubmissionAnswer>()
                .eq(SubmissionAnswer::getSubmissionId, submissionId))) {
            map.put(answer.getQuestionId(), answer.getAnswer());
        }
        return map;
    }

    public java.math.BigDecimal objectiveScore(Assignment assignment, Map<Long, String> answers) {
        List<Question> questions = assignment.getQuestions();
        if (questions == null) {
            questions = questions(assignment.getId());
            assignment.setQuestions(questions);
        }
        return objectiveScore(questions, answers);
    }

    public java.math.BigDecimal objectiveScore(List<Question> questions, Map<Long, String> answers) {
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        for (Question question : questions) {
            // 多选题：全对满分、部分正确（不含错误项）得一半分、含错误项 0 分
            total = total.add(question.scoreOf(answers.get(question.getId())));
        }
        return total;
    }

    public Assignment getForStudent(Long id, Student student) {
        Assignment assignment = get(id);
        courseService.assertStudent(assignment.getCourseId(), student);
        Submission submission = findSubmission(id, student.getId());
        assignment.setMySubmission(submission);
        Map<Long, String> answers = submission == null ? Map.of() : answersForSubmission(submission.getId());
        assignment.setAnswers(answers);
        if (submission != null) decorateSubmission(submission, assignment, answers);
        return assignment;
    }

    public Assignment getForTeacher(Long id, Teacher teacher) {
        Assignment assignment = get(id);
        courseService.assertTeacher(assignment.getCourseId(), teacher);
        return assignment;
    }

    public Assignment create(Course course, Teacher teacher, String title, String requirement,
                             LocalDateTime deadline, MultipartFile attachment) {
        if (title == null || title.isBlank()) throw new BusinessException("作业标题不能为空");
        if (deadline == null) throw new BusinessException("请选择截止时间");
        if (title.trim().length() > 200) throw new BusinessException("作业标题不能超过 200 个字符");
        Assignment assignment = new Assignment();
        assignment.setCourseId(course.getId());
        assignment.setTitle(title.trim());
        assignment.setRequirement(requirement);
        assignment.setDeadline(deadline);
        assignment.setMissingMarked(false);
        assignment.setCreatedBy(teacher.getId());
        assignment.setCreatedAt(LocalDateTime.now());
        if (attachment != null && !attachment.isEmpty()) {
            String path = fileService.storeDocument(attachment, "assignments");
            assignment.setAttachmentPath(path);
            assignment.setAttachmentName(attachment.getOriginalFilename());
        }
        assignmentMapper.insert(assignment);
        evictAssignments(course.getId());
        notifyAssignment(course, assignment);
        return assignment;
    }

    public void update(Assignment assignment, Teacher teacher, String title, String requirement,
                       LocalDateTime deadline, MultipartFile attachment) {
        if (title == null || title.isBlank()) throw new BusinessException("作业标题不能为空");
        if (deadline == null) throw new BusinessException("请选择截止时间");
        if (title.trim().length() > 200) throw new BusinessException("作业标题不能超过 200 个字符");
        if (attachment != null && !attachment.isEmpty()) {
            String old = assignment.getAttachmentPath();
            String path = fileService.storeDocument(attachment, "assignments");
            assignment.setAttachmentPath(path);
            assignment.setAttachmentName(attachment.getOriginalFilename());
            fileService.delete(old);
        }
        assignment.setTitle(title.trim());
        assignment.setRequirement(requirement);
        assignment.setDeadline(deadline);
        assignmentMapper.updateById(assignment);
        evictAssignments(assignment.getCourseId());
    }

    /** 发布作业：作业 + 评分方案 + 题目在同一事务内写入，避免中途失败留下半成品。 */
    @Transactional
    public Assignment createWithQuestions(Course course, Teacher teacher, String title, String requirement,
                                          LocalDateTime deadline, MultipartFile attachment,
                                          AssignmentScoreConfig scoreConfig, List<Question> questions) {
        Assignment created = create(course, teacher, title, requirement, deadline, attachment);
        saveScoreConfig(created.getId(), scoreConfig);
        saveQuestions(created.getId(), questions);
        return created;
    }

    /** 编辑作业：更新基本信息 + 评分方案 + 题目在同一事务内完成。 */
    @Transactional
    public boolean updateWithQuestions(Assignment assignment, Teacher teacher, String title, String requirement,
                                       LocalDateTime deadline, MultipartFile attachment,
                                       AssignmentScoreConfig scoreConfig, List<Question> questions) {
        update(assignment, teacher, title, requirement, deadline, attachment);
        saveScoreConfig(assignment.getId(), scoreConfig);
        return saveQuestions(assignment.getId(), questions);
    }

    @Transactional
    public void delete(Assignment assignment) {
        List<Submission> submissions = submissionMapper.selectList(
                new LambdaQueryWrapper<Submission>().eq(Submission::getAssignmentId, assignment.getId()));
        for (Submission submission : submissions) {
            fileService.delete(submission.getFilePath());
            answerMapper.delete(new LambdaQueryWrapper<SubmissionAnswer>()
                    .eq(SubmissionAnswer::getSubmissionId, submission.getId()));
            subjectiveMapper.deleteById(submission.getId());
            gradeHistoryMapper.delete(new LambdaQueryWrapper<SubmissionGradeHistory>()
                    .eq(SubmissionGradeHistory::getSubmissionId, submission.getId()));
            submissionMapper.deleteById(submission.getId());
        }
        questionMapper.delete(new LambdaQueryWrapper<Question>().eq(Question::getAssignmentId, assignment.getId()));
        scoreConfigMapper.deleteById(assignment.getId());
        fileService.delete(assignment.getAttachmentPath());
        assignmentMapper.deleteById(assignment.getId());
        evictAssignments(assignment.getCourseId());
    }

    public Submission findSubmission(Long assignmentId, Long studentId) {
        return submissionMapper.selectOne(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getAssignmentId, assignmentId)
                .eq(Submission::getStudentId, studentId));
    }

    public Submission submit(Assignment assignment, Student student, String content, MultipartFile file,
                             Map<Long, String> answers) {
        Course course = courseService.assertStudent(assignment.getCourseId(), student);
        if (course.isEnded()) throw new BusinessException("课程已结束，无法提交");
        if (assignment.isExpired()) throw new BusinessException("作业已截止，无法提交");
        boolean hasFile = file != null && !file.isEmpty();
        if ((content == null || content.isBlank()) && !hasFile && (answers == null || answers.isEmpty())) {
            throw new BusinessException("请完成题目、填写提交说明或上传附件");
        }
        Submission submission = findSubmission(assignment.getId(), student.getId());
        boolean create = submission == null;
        if (create) {
            submission = new Submission();
            submission.setAssignmentId(assignment.getId());
            submission.setStudentId(student.getId());
        } else {
            // 学生重新提交：把上一次已批改的成绩先归档，再重置为待批改
            archiveGrade(submission);
        }
        if (hasFile) {
            String old = submission.getFilePath();
            String path = fileService.storeDocument(file, "submissions");
            submission.setFilePath(path);
            submission.setFileName(file.getOriginalFilename());
            submission.setFileSize(file.getSize());
            fileService.delete(old);
        }
        submission.setContent(content);
        submission.setStatus("SUBMITTED");
        submission.setSubmittedAt(LocalDateTime.now());
        submission.setUpdatedAt(LocalDateTime.now());
        submission.setScore(null);
        submission.setTeacherComment(null);
        submission.setGradedAt(null);
        submission.setGradedBy(null);
        submission.setPublished(false);
        if (create) {
            try {
                submissionMapper.insert(submission);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 双击/并发提交撞唯一约束：转成可读提示，避免 500
                throw new BusinessException("提交冲突：作业已存在提交记录，请刷新页面后重试");
            }
        } else {
            submissionMapper.updateById(submission);
            subjectiveMapper.deleteById(submission.getId());
        }
        answerMapper.delete(new LambdaQueryWrapper<SubmissionAnswer>()
                .eq(SubmissionAnswer::getSubmissionId, submission.getId()));
        if (answers != null) {
            for (Question question : questions(assignment.getId())) {
                String value = answers.get(question.getId());
                if (value != null && !value.isBlank()) {
                    SubmissionAnswer answer = new SubmissionAnswer();
                    answer.setSubmissionId(submission.getId());
                    answer.setQuestionId(question.getId());
                    answer.setAnswer(value.trim());
                    answerMapper.insert(answer);
                }
            }
        }
        evictAssignments(assignment.getCourseId());
        return submission;
    }

    public List<Submission> submissionsForTeacher(Assignment assignment, Course course) {
        List<Student> students = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .eq(Student::getClassId, course.getClassId()).orderByAsc(Student::getSno));
        List<Submission> submissions = submissionMapper.selectList(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getAssignmentId, assignment.getId()));
        Map<Long, Submission> map = new HashMap<>();
        for (Submission submission : submissions) map.put(submission.getStudentId(), submission);
        Map<Long, Map<Long, String>> answersBySubmission = answersForAssignment(assignment.getId());
        List<Submission> result = new ArrayList<>();
        for (Student student : students) {
            Submission submission = map.get(student.getId());
            if (submission == null) {
                submission = new Submission();
                submission.setAssignmentId(assignment.getId());
                submission.setStudentId(student.getId());
                submission.setStatus("MISSING");
                submission.setPublished(false);
            }
            submission.setStudentName(student.getName());
            submission.setStudentSno(student.getSno());
            submission.setAssignmentTitle(assignment.getTitle());
            submission.setCourseName(course.getName());
            if (submission.getId() != null) {
                decorateSubmission(submission, assignment, answersBySubmission.get(submission.getId()));
            } else {
                submission.setObjectiveScore(java.math.BigDecimal.ZERO);
                submission.setObjectiveFull(assignment.getObjectiveFull());
                submission.setSubjectiveFull(assignment.getSubjectiveFull());
            }
            result.add(submission);
        }
        return result;
    }

    private Map<Long, Map<Long, String>> answersForAssignment(Long assignmentId) {
        Map<Long, Map<Long, String>> result = new HashMap<>();
        List<Submission> submissions = submissionMapper.selectList(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getAssignmentId, assignmentId));
        if (submissions.isEmpty()) return result;
        List<Long> ids = submissions.stream().map(Submission::getId).toList();
        for (SubmissionAnswer answer : answerMapper.selectList(new LambdaQueryWrapper<SubmissionAnswer>()
                .in(SubmissionAnswer::getSubmissionId, ids))) {
            result.computeIfAbsent(answer.getSubmissionId(), key -> new HashMap<>())
                    .put(answer.getQuestionId(), answer.getAnswer());
        }
        return result;
    }

    /**
     * 批改：客观题按答案自动算分，主观题（附件）由教师手动录入。
     * 存在主观题分值而教师没有录入时，视为批改未完成，不写入成绩。
     */
    public void grade(Long submissionId, Teacher teacher, BigDecimal subjective, String comment, boolean publish) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) throw new BusinessException("提交记录不存在");
        if ("MISSING".equals(submission.getStatus())) throw new BusinessException("学生尚未提交，无法打分");
        Assignment assignment = get(submission.getAssignmentId());
        Course course = courseService.assertTeacher(assignment.getCourseId(), teacher);
        Map<Long, String> answers = answersForSubmission(submissionId);
        BigDecimal objective = objectiveScore(assignment, answers);
        boolean hasQuestions = assignment.isHasQuestions();
        BigDecimal subjectiveFull = assignment.getSubjectiveFull();

        BigDecimal subjectiveScore;
        BigDecimal total;
        if (!hasQuestions) {
            if (subjective == null) throw new BusinessException("请填写分数");
            if (subjective.compareTo(BigDecimal.ZERO) < 0 || subjective.compareTo(ScoreCalculator.FULL_SCORE) > 0) {
                throw new BusinessException("分数必须在 0~100 之间");
            }
            subjectiveScore = subjective;
            total = subjective;
        } else if (subjectiveFull.compareTo(BigDecimal.ZERO) > 0) {
            if (subjective == null) {
                throw new BusinessException("请先录入主观题（附件）得分，再保存批改");
            }
            if (subjective.compareTo(BigDecimal.ZERO) < 0 || subjective.compareTo(subjectiveFull) > 0) {
                throw new BusinessException("主观题得分必须在 0~" + plain(subjectiveFull) + " 之间");
            }
            subjectiveScore = subjective;
            total = objective.add(subjective);
        } else {
            subjectiveScore = BigDecimal.ZERO;
            total = objective;
        }
        if (total.compareTo(ScoreCalculator.FULL_SCORE) > 0) total = ScoreCalculator.FULL_SCORE;

        archiveGrade(submission);
        saveSubjectiveScore(submissionId, subjectiveScore);
        submission.setObjectiveScore(objective);
        submission.setSubjectiveScore(subjectiveScore);
        submission.setObjectiveFull(assignment.getObjectiveFull());
        submission.setSubjectiveFull(subjectiveFull);
        submission.setScore(total.setScale(2, java.math.RoundingMode.HALF_UP));
        submission.setTeacherComment(comment);
        submission.setStatus("GRADED");
        submission.setGradedAt(LocalDateTime.now());
        submission.setGradedBy(teacher.getId());
        submission.setPublished(publish);
        submissionMapper.updateById(submission);
        evictAssignments(assignment.getCourseId());
        if (publish) notifyGrade(course, assignment, submission);
    }

    private String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    public int publishAll(Long assignmentId, Teacher teacher) {
        Assignment assignment = getForTeacher(assignmentId, teacher);
        Course course = courseService.get(assignment.getCourseId());
        List<Submission> submissions = submissionMapper.selectList(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getAssignmentId, assignmentId)
                .eq(Submission::getStatus, "GRADED")
                .eq(Submission::getPublished, false));
        for (Submission submission : submissions) {
            submission.setPublished(true);
            submissionMapper.updateById(submission);
            notifyGrade(course, assignment, submission);
        }
        evictAssignments(assignment.getCourseId());
        return submissions.size();
    }

    @Transactional
    public int markMissing(Assignment assignment) {
        if (Boolean.TRUE.equals(assignment.getMissingMarked())) return 0;
        Course course = courseService.get(assignment.getCourseId());
        List<Student> students = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .eq(Student::getClassId, course.getClassId()));
        List<Submission> submissions = submissionMapper.selectList(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getAssignmentId, assignment.getId()));
        Map<Long, Submission> existing = submissions.stream()
                .collect(Collectors.toMap(Submission::getStudentId, s -> s));
        int created = 0;
        for (Student student : students) {
            if (!existing.containsKey(student.getId())) {
                Submission submission = new Submission();
                submission.setAssignmentId(assignment.getId());
                submission.setStudentId(student.getId());
                submission.setStatus("MISSING");
                submission.setPublished(false);
                submissionMapper.insert(submission);
                created++;
            }
        }
        assignment.setMissingMarked(true);
        assignmentMapper.updateById(assignment);
        evictAssignments(assignment.getCourseId());
        return created;
    }

    public List<Submission> publishedForStudent(Student student) {
        List<Submission> submissions = submissionMapper.selectList(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getStudentId, student.getId())
                .eq(Submission::getPublished, true)
                .orderByDesc(Submission::getGradedAt));
        for (Submission submission : submissions) {
            Assignment assignment = assignmentMapper.selectById(submission.getAssignmentId());
            if (assignment != null) {
                submission.setAssignmentTitle(assignment.getTitle());
                Course course = courseService.get(assignment.getCourseId());
                submission.setCourseName(course.getName());
            }
        }
        return submissions;
    }

    public long countByCourse(Long courseId) {
        List<Assignment> list = assignmentMapper.selectList(new LambdaQueryWrapper<Assignment>()
                .eq(Assignment::getCourseId, courseId));
        return list.size();
    }

    private void notifyAssignment(Course course, Assignment assignment) {
        List<Student> students = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .eq(Student::getClassId, course.getClassId()));
        if (students.isEmpty()) return;
        List<String> codes = students.stream().map(Student::getSno).toList();
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getCode, codes).isNotNull(SysUser::getEmail));
        for (SysUser user : users) {
            mailService.send(user.getEmail(), "新作业发布：" + assignment.getTitle(),
                    "课程《" + course.getName() + "》发布了新作业《" + assignment.getTitle()
                            + "》，截止时间：" + assignment.getDeadlineText() + "，请及时完成提交。");
        }
    }

    private void notifyGrade(Course course, Assignment assignment, Submission submission) {
        Student student = studentMapper.selectById(submission.getStudentId());
        if (student == null) return;
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getCode, student.getSno()));
        if (user == null) return;
        mailService.send(user.getEmail(), "成绩发布：" + assignment.getTitle(),
                "你在课程《" + course.getName() + "》的作业《" + assignment.getTitle() + "》成绩已发布，得分："
                        + submission.getScoreText() + " 分。评语：" + (submission.getTeacherComment() == null ? "无" : submission.getTeacherComment()));
    }
}
