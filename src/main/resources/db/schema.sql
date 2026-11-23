-- 班级学业管理与作业提交系统 建表脚本（MySQL 8 / H2 MySQL 模式通用）
CREATE TABLE IF NOT EXISTS class_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    grade_year INT NOT NULL,
    class_no INT NOT NULL,
    major VARCHAR(64) NOT NULL,
    student_count INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS student (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sno VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    class_id BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS teacher (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tno VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    subject VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    name VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(128),
    avatar VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS course (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    teacher_id BIGINT NOT NULL,
    teacher_name VARCHAR(64) NOT NULL,
    class_id BIGINT NOT NULL,
    course_year INT NOT NULL,
    term_name VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reject_reason VARCHAR(255),
    created_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS assignment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    requirement TEXT,
    deadline DATETIME NOT NULL,
    attachment_name VARCHAR(255),
    attachment_path VARCHAR(500),
    missing_marked BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS submission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    assignment_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    content TEXT,
    file_name VARCHAR(255),
    file_path VARCHAR(500),
    file_size BIGINT,
    status VARCHAR(20) NOT NULL,
    score DECIMAL(5,2),
    teacher_comment VARCHAR(1000),
    submitted_at DATETIME,
    updated_at DATETIME,
    graded_at DATETIME,
    graded_by BIGINT,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_submission UNIQUE (assignment_id, student_id)
);

CREATE TABLE IF NOT EXISTS announcement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS monthly_stat (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    class_id BIGINT NOT NULL,
    stat_month VARCHAR(7) NOT NULL,
    submission_rate DECIMAL(5,2) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_monthly_stat UNIQUE (class_id, stat_month)
);

CREATE TABLE IF NOT EXISTS question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    assignment_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    stem VARCHAR(1000) NOT NULL,
    score DECIMAL(5,2) NOT NULL,
    option_a VARCHAR(500),
    option_b VARCHAR(500),
    option_c VARCHAR(500),
    option_d VARCHAR(500),
    correct_answer VARCHAR(50) NOT NULL,
    sort_no INT NOT NULL
);

CREATE TABLE IF NOT EXISTS submission_answer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    answer VARCHAR(100),
    CONSTRAINT uk_submission_answer UNIQUE (submission_id, question_id)
);

-- 作业评分方案：EVEN=客观题平均分配 100 分；CUSTOM=按题型自定义占比（剩余为主观题/附件分）
CREATE TABLE IF NOT EXISTS assignment_score_config (
    assignment_id BIGINT PRIMARY KEY,
    mode VARCHAR(20) NOT NULL,
    single_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    multiple_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    judge_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    attachment_percent DECIMAL(5,2) NOT NULL DEFAULT 0
);


-- 主观题（附件）得分，由教师批改时手动录入；客观题得分由答案自动计算
CREATE TABLE IF NOT EXISTS submission_subjective (
    submission_id BIGINT PRIMARY KEY,
    score DECIMAL(5,2) NOT NULL,
    updated_at DATETIME
);

-- 成绩历史：学生重新提交、教师改分或题目变更导致成绩变动时，归档上一次已批改的成绩
CREATE TABLE IF NOT EXISTS submission_grade_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    score DECIMAL(5,2),
    teacher_comment VARCHAR(1000),
    graded_at DATETIME,
    graded_by BIGINT,
    attempt_submitted_at DATETIME,
    created_at DATETIME NOT NULL
);
