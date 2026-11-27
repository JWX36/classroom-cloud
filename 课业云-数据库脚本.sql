-- =====================================================================
-- 课业云 · 班级学业管理与作业提交系统  数据库脚本（MySQL 8.0）
-- 广州华商职业学院 · 网络技术专业 课程设计（选题四）
-- ---------------------------------------------------------------------
-- 使用方法：
--   1) 宝塔 phpMyAdmin / MySQL 命令行 / Navicat 直接导入本文件即可
--   2) 脚本包含：13 张建表语句 + 全量初始化测试数据 + 管理员测试账号
--   3) 可重复执行：先 DROP 再 CREATE，导入前会清空同名表
--   4) 应用启动后若检测到已有数据则不会重复播种，二者兼容
-- ---------------------------------------------------------------------
-- 测试账号（密码为 BCrypt 加密存储）：
--   管理员：admin    / admin123
--   学  生：20240101 / 123456   （2024级网络技术1班 01号）
--   教  师：HS010001 / 123456   （詹昌明 · 网络系统集成与运维）
-- =====================================================================

CREATE DATABASE IF NOT EXISTS kys DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE kys;

-- ---------------------------------------------------------------------
-- 一、清空旧表（可重复导入）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS submission_grade_history;
DROP TABLE IF EXISTS submission_subjective;
DROP TABLE IF EXISTS assignment_score_config;
DROP TABLE IF EXISTS submission_answer;
DROP TABLE IF EXISTS question;
DROP TABLE IF EXISTS submission;
DROP TABLE IF EXISTS assignment;
DROP TABLE IF EXISTS announcement;
DROP TABLE IF EXISTS monthly_stat;
DROP TABLE IF EXISTS course;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS student;
DROP TABLE IF EXISTS teacher;
DROP TABLE IF EXISTS class_info;

-- ---------------------------------------------------------------------
-- 二、建表语句（13 张表）
-- ---------------------------------------------------------------------
CREATE TABLE class_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    grade_year INT NOT NULL,
    class_no INT NOT NULL,
    major VARCHAR(64) NOT NULL,
    student_count INT NOT NULL DEFAULT 0
);

CREATE TABLE student (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sno VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    class_id BIGINT NOT NULL
);

CREATE TABLE teacher (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tno VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    subject VARCHAR(128) NOT NULL
);

CREATE TABLE sys_user (
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

CREATE TABLE course (
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

CREATE TABLE assignment (
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

CREATE TABLE submission (
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

CREATE TABLE announcement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL
);

CREATE TABLE monthly_stat (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    class_id BIGINT NOT NULL,
    stat_month VARCHAR(7) NOT NULL,
    submission_rate DECIMAL(5,2) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_monthly_stat UNIQUE (class_id, stat_month)
);

CREATE TABLE question (
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

CREATE TABLE submission_answer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    answer VARCHAR(100),
    CONSTRAINT uk_submission_answer UNIQUE (submission_id, question_id)
);

CREATE TABLE assignment_score_config (
    assignment_id BIGINT PRIMARY KEY,
    mode VARCHAR(20) NOT NULL,
    single_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    multiple_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    judge_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    attachment_percent DECIMAL(5,2) NOT NULL DEFAULT 0
);

CREATE TABLE submission_subjective (
    submission_id BIGINT PRIMARY KEY,
    score DECIMAL(5,2) NOT NULL,
    updated_at DATETIME
);

CREATE TABLE submission_grade_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    score DECIMAL(5,2),
    teacher_comment VARCHAR(1000),
    graded_at DATETIME,
    graded_by BIGINT,
    attempt_submitted_at DATETIME,
    created_at DATETIME NOT NULL
);

-- ---------------------------------------------------------------------
-- 三、初始化测试数据
-- ---------------------------------------------------------------------

-- 3.1 班级（4 个班，每班 40 人）
INSERT INTO class_info (id, name, grade_year, class_no, major, student_count) VALUES
(1, '2024级网络技术1班', 2024, 1, '网络技术', 40),
(2, '2024级网络技术2班', 2024, 2, '网络技术', 40),
(3, '2024级网络技术3班', 2024, 3, '网络技术', 40),
(4, '2024级网络技术4班', 2024, 4, '网络技术', 40);

-- 3.2 学生名册（160 人，学号规则：2024+班号两位+序号两位）
INSERT INTO student (sno, name, class_id) VALUES
('20240101', '01', 1),
('20240102', '02', 1),
('20240103', '03', 1),
('20240104', '04', 1),
('20240105', '05', 1),
('20240106', '06', 1),
('20240107', '07', 1),
('20240108', '08', 1),
('20240109', '09', 1),
('20240110', '10', 1),
('20240111', '11', 1),
('20240112', '12', 1),
('20240113', '13', 1),
('20240114', '14', 1),
('20240115', '15', 1),
('20240116', '16', 1),
('20240117', '17', 1),
('20240118', '18', 1),
('20240119', '19', 1),
('20240120', '20', 1),
('20240121', '21', 1),
('20240122', '22', 1),
('20240123', '23', 1),
('20240124', '24', 1),
('20240125', '25', 1),
('20240126', '26', 1),
('20240127', '27', 1),
('20240128', '28', 1),
('20240129', '29', 1),
('20240130', '30', 1),
('20240131', '31', 1),
('20240132', '32', 1),
('20240133', '33', 1),
('20240134', '34', 1),
('20240135', '35', 1),
('20240136', '36', 1),
('20240137', '37', 1),
('20240138', '38', 1),
('20240139', '39', 1),
('20240140', '40', 1),
('20240201', '01', 2),
('20240202', '02', 2),
('20240203', '03', 2),
('20240204', '04', 2),
('20240205', '05', 2),
('20240206', '06', 2),
('20240207', '07', 2),
('20240208', '08', 2),
('20240209', '09', 2),
('20240210', '10', 2),
('20240211', '11', 2),
('20240212', '12', 2),
('20240213', '13', 2),
('20240214', '14', 2),
('20240215', '15', 2),
('20240216', '16', 2),
('20240217', '17', 2),
('20240218', '18', 2),
('20240219', '19', 2),
('20240220', '20', 2),
('20240221', '21', 2),
('20240222', '22', 2),
('20240223', '23', 2),
('20240224', '24', 2),
('20240225', '25', 2),
('20240226', '26', 2),
('20240227', '27', 2),
('20240228', '28', 2),
('20240229', '29', 2),
('20240230', '30', 2),
('20240231', '31', 2),
('20240232', '32', 2),
('20240233', '33', 2),
('20240234', '34', 2),
('20240235', '35', 2),
('20240236', '36', 2),
('20240237', '37', 2),
('20240238', '38', 2),
('20240239', '39', 2),
('20240240', '40', 2),
('20240301', '01', 3),
('20240302', '02', 3),
('20240303', '03', 3),
('20240304', '04', 3),
('20240305', '05', 3),
('20240306', '06', 3),
('20240307', '07', 3),
('20240308', '08', 3),
('20240309', '09', 3),
('20240310', '10', 3),
('20240311', '11', 3),
('20240312', '12', 3),
('20240313', '13', 3),
('20240314', '14', 3),
('20240315', '15', 3),
('20240316', '16', 3),
('20240317', '17', 3),
('20240318', '18', 3),
('20240319', '19', 3),
('20240320', '20', 3),
('20240321', '21', 3),
('20240322', '22', 3),
('20240323', '23', 3),
('20240324', '24', 3),
('20240325', '25', 3),
('20240326', '26', 3),
('20240327', '27', 3),
('20240328', '28', 3),
('20240329', '29', 3),
('20240330', '30', 3),
('20240331', '31', 3),
('20240332', '32', 3),
('20240333', '33', 3),
('20240334', '34', 3),
('20240335', '35', 3),
('20240336', '36', 3),
('20240337', '37', 3),
('20240338', '38', 3),
('20240339', '39', 3),
('20240340', '40', 3),
('20240401', '01', 4),
('20240402', '02', 4),
('20240403', '03', 4),
('20240404', '04', 4),
('20240405', '05', 4),
('20240406', '06', 4),
('20240407', '07', 4),
('20240408', '08', 4),
('20240409', '09', 4),
('20240410', '10', 4),
('20240411', '11', 4),
('20240412', '12', 4),
('20240413', '13', 4),
('20240414', '14', 4),
('20240415', '15', 4),
('20240416', '16', 4),
('20240417', '17', 4),
('20240418', '18', 4),
('20240419', '19', 4),
('20240420', '20', 4),
('20240421', '21', 4),
('20240422', '22', 4),
('20240423', '23', 4),
('20240424', '24', 4),
('20240425', '25', 4),
('20240426', '26', 4),
('20240427', '27', 4),
('20240428', '28', 4),
('20240429', '29', 4),
('20240430', '30', 4),
('20240431', '31', 4),
('20240432', '32', 4),
('20240433', '33', 4),
('20240434', '34', 4),
('20240435', '35', 4),
('20240436', '36', 4),
('20240437', '37', 4),
('20240438', '38', 4),
('20240439', '39', 4),
('20240440', '40', 4);

-- 3.3 教师名册（6 人）
INSERT INTO teacher (id, tno, name, subject) VALUES
(1, 'HS010001', '詹昌明', '网络系统集成与运维'),
(2, 'HS010002', '潘洁仪', '电子商务数据分析与应用'),
(3, 'HS010003', '陈富荣', '数据结构'),
(4, 'HS010004', '胡志浩', '网络安全技术'),
(5, 'HS010005', '朱俊鑫', '移动应用开发'),
(6, 'HS010006', '张卫', '企业级项目开发');

-- 3.4 系统账号（密码 BCrypt 加密；admin123 / 123456）
INSERT INTO sys_user (id, code, password, name, role, phone, email, enabled, created_at) VALUES
(1, 'admin', '$2a$10$bTK7PK9CU8j/AjOSCWYhFeFanF.YQALyV65RGne09yxJ89KGuwfq6', '系统管理员', 'ADMIN', '13800000000', 'admin@example.com', TRUE, NOW()),
(2, '20240101', '$2a$10$MwR2MEnsp2G4cSAfWHH/LOdUEXybBDMIoJE81i9VBa2ltPmOLrM/u', '01', 'STUDENT', '13800000001', '20240101@example.com', TRUE, NOW()),
(3, 'HS010001', '$2a$10$MwR2MEnsp2G4cSAfWHH/LOdUEXybBDMIoJE81i9VBa2ltPmOLrM/u', '詹昌明', 'TEACHER', '13800000002', 'teacher@example.com', TRUE, NOW());

-- 3.5 课程（4 门：2 门已批准、1 门待审批、1 门往学期）
INSERT INTO course (id, name, teacher_id, teacher_name, class_id, course_year, term_name, status, reject_reason, created_at) VALUES
(1, 'Web前端框架技术', 1, '詹昌明', 1, 2026, '上学期', 'APPROVED', NULL, NOW()),
(2, '电子商务数据分析与应用', 2, '潘洁仪', 1, 2026, '下学期', 'APPROVED', NULL, NOW()),
(3, '数据结构', 3, '陈富荣', 1, 2026, '上学期', 'PENDING', NULL, NOW()),
(4, '网络安全技术', 4, '胡志浩', 2, 2025, '下学期', 'APPROVED', NULL, NOW());

-- 3.6 作业（4 份：截止时间相对导入时间自动计算，保证演示永远"未过期"）
INSERT INTO assignment (id, course_id, title, requirement, deadline, missing_marked, created_by, created_at) VALUES
(1, 1, '实验一：Vue 环境搭建', '完成 Vue 3 开发环境搭建，提交实验报告与运行截图。', DATE_ADD(NOW(), INTERVAL 10 DAY), FALSE, 1, NOW()),
(2, 1, '实验二：响应式数据绑定', '使用 Vue 3 完成一个响应式数据绑定的小案例，提交源代码压缩包与实验报告。', DATE_ADD(NOW(), INTERVAL 20 DAY), FALSE, 1, NOW()),
(3, 1, '课堂练习：指令综合应用', '完成 v-if、v-for、v-model 的综合练习。', DATE_SUB(NOW(), INTERVAL 3 DAY), FALSE, 1, NOW()),
(4, 2, '期末大作业：数据分析报告', '提交一份电子商务数据分析报告。', DATE_SUB(NOW(), INTERVAL 90 DAY), FALSE, 2, NOW());

-- 3.7 客观题（实验一：单选 + 多选 + 判断；判断题选项固定为"对/错"）
INSERT INTO question (id, assignment_id, type, stem, score, option_a, option_b, option_c, option_d, correct_answer, sort_no) VALUES
(1, 1, 'SINGLE', 'Vue 3 通过 CDN 引入的全局构建版本是（ ）。', 10, 'vue.global.js', 'vue.runtime.js', 'vue.esm-browser.js', 'vue.cjs.js', 'A', 1),
(2, 1, 'MULTIPLE', '以下哪些属于 Vue 的常用指令？（多选）', 15, 'v-if', 'v-for', 'v-model', '@click', 'A,B,C', 2),
(3, 1, 'JUDGE', 'v-model 可以实现表单元素与数据的双向绑定。（ ）', 10, '对', '错', NULL, NULL, 'A', 3);

-- 3.8 作业评分方案（实验一：平均分配模式）
INSERT INTO assignment_score_config (assignment_id, mode, single_percent, multiple_percent, judge_percent, attachment_percent) VALUES
(1, 'EVEN', 0, 0, 0, 0);

-- 3.9 课程公告（置顶 + 普通）
INSERT INTO announcement (id, course_id, title, content, pinned, created_by, created_at) VALUES
(1, 1, '课程通知：本周实验安排', '本周五 19:00 进行实验二讲解，请提前完成实验一提交。', TRUE, 1, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(2, 1, '作业提交提醒', '实验一截止时间为 10 天后，请合理安排时间。', FALSE, 1, DATE_SUB(NOW(), INTERVAL 2 DAY));

-- 3.10 学生提交（20240101：实验一已提交待批改，实验二已批改发布）
INSERT INTO submission (id, assignment_id, student_id, content, status, score, teacher_comment, submitted_at, updated_at, graded_at, graded_by, published) VALUES
(1, 1, 1, '已完成环境搭建，运行截图见附件。', 'SUBMITTED', NULL, NULL, DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 3 HOUR), NULL, NULL, FALSE),
(2, 2, 1, '完成响应式数据绑定案例，代码与报告已提交。', 'GRADED', 92.00, '完成度高，代码规范，报告结构完整。', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 1, TRUE);

-- 3.11 客观题作答（实验一：全对）
INSERT INTO submission_answer (submission_id, question_id, answer) VALUES
(1, 1, 'A'),
(1, 2, 'A,B,C'),
(1, 3, 'A');

-- 3.12 主观题（附件）得分（实验二无客观题，92 分为教师手动录入的附件分）
INSERT INTO submission_subjective (submission_id, score, updated_at) VALUES
(2, 92.00, DATE_SUB(NOW(), INTERVAL 1 DAY));

-- 3.13 月度统计（1 班上月提交率）
INSERT INTO monthly_stat (id, class_id, stat_month, submission_rate, created_at) VALUES
(1, 1, DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 1 MONTH), '%Y-%m'), 78.50, NOW());

-- 成绩历史表（submission_grade_history）在学生重新提交、教师改分时由系统自动归档，初始数据为空

-- ---------------------------------------------------------------------
-- 四、导入验证
--   SELECT COUNT(*) FROM student;            -- 应为 160
--   SELECT COUNT(*) FROM sys_user;           -- 应为 3
--   SELECT code, name, role FROM sys_user;   -- 三行测试账号
--   用 admin / admin123 登录管理员后台即验证通过
-- ---------------------------------------------------------------------
