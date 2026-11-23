# 课业云 · 班级学业管理与作业提交系统

广州华商职业学院 · 网络技术专业课程设计（选题四）
基于 Spring Boot 4.1.2 + Thymeleaf + MyBatis-Plus + MySQL 8 + Redis + Spring Security 的班级作业全流程系统。

## 一、已实现功能

### 基础级
- **学生/教师/管理员三角色**，登录后自动进入对应首页
- **注册**：输入学号或教师编号 → 校验管理员名册 → 设置头像/手机号/密码/邮箱
- **个人中心**：修改姓名、头像、手机号、邮箱、密码；支持「切换账号」
- **管理员后台**：
  - 4 个班级学生名册（每班 40~60 人，学号 `2024CCNN`，姓名取学号末两位）
  - 6 位教师名册（`HS010001` ~ `HS010006`）
  - 学生/教师名册增改、批量导入、班级信息维护
  - 账号管理（删除账号）
  - 课程审批
- **教师**：创建课程（课程名/教师姓名必填、`20XX年 上/下学期`、选择班级）→ 管理员审批
- **作业**：教师发布/编辑/删除（标题、要求、截止时间、附件）；支持添加单选、多选、判断题，学生在线选择；也可以只上传附件让学生下载完成
- **成绩**：教师逐人打分、填写评语、发布成绩，可查看学生作答详情与客观题得分；学生查看得分与评语
- **公告**：教师发布/置顶/删除班级公告，学生课程首页置顶展示
- **文件上传下载**：头像、作业附件、学生提交文件；下载带权限校验
- **个人菜单**：右上角头像点击进入个人中心，悬停显示「个人中心 / 切换账号」

### 进阶级
- **Spring Security 三角色权限隔离**（学生 `/student/**`、教师 `/teacher/**`、管理员 `/admin/**`）
- **Redis 缓存**：课程列表、公告列表、教师作业列表（`CACHE_MODE=redis`；开发环境默认本地缓存）
- **邮件提醒**：发布作业、成绩发布、截止前 1 天提醒（开发环境打印日志，生产配置 SMTP 后真实发送）
- **定时任务**：每天 02:00 结束过期课程、标记未提交学生、发送截止提醒；每月 1 日 03:00 生成班级提交率
- **工程化**：全局异常处理、Jakarta Validation 参数校验、统一 `Result` 返回、统一错误页

### 暂未实现（高级）
作业查重、RabbitMQ 异步、成绩分析/教学看板、Redis 并发优化等，按课程要求留到高级阶段。

## 二、演示账号

| 角色 | 账号 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `admin123` |
| 学生 | `20240101` | `123456` |
| 教师 | `HS010001` | `123456` |

其他学生/教师需在注册页使用名册中的学号/教师编号注册。
名册学号示例：`20240101`（2024 级 1 班 01 号），教师编号：`HS010001`。

## 三、开发环境运行（免安装 MySQL/Redis）

环境：JDK 21+（项目使用 `java.version=21`）。

1. IDEA 打开项目，等待 Maven 依赖导入完成。
   > 如果 Reload 时提示 `No implementation for ArtifactHandlerManager was bound`，在 `Settings → Build, Execution, Deployment → Build Tools → Maven` 中把 **Maven home path** 从 `Use Maven wrapper` 改为 **Bundled (Maven 3)**，再 Reload。
2. 运行 `com.example.demo.DemoApplication`。
3. 浏览器打开：<http://127.0.0.1:8081/login>

开发环境使用 **H2 文件数据库**（`./data/kys`），启动时自动建表并生成名册、教师、示例课程。
如需重新生成演示数据，停止应用后删除 `data` 目录再启动。
> 如果之前运行过旧版本，建议删除一次 `data` 目录，让新增的题目表重新建好；开发数据均为演示数据。

> 端口在 `application.yaml` 中默认为 8081；8081 空闲时可改为 8081。

## 四、生产环境（MySQL 8 + Redis + 邮件）

1. 建库并执行建表脚本：

```bash
mysql -uroot -p -e "CREATE DATABASE kys DEFAULT CHARSET utf8mb4;"
mysql -uroot -p kys < src/main/resources/db/schema.sql
```

2. 配置环境变量并以 `prod` 启动：

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_URL="jdbc:mysql://127.0.0.1:3306/kys?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false"
export DB_USERNAME=root
export DB_PASSWORD=你的密码
export REDIS_HOST=127.0.0.1
export REDIS_PORT=6379
export MAIL_HOST=smtp.qq.com
export MAIL_PORT=465
export MAIL_USERNAME=你的邮箱
export MAIL_PASSWORD=邮箱授权码
export UPLOAD_DIR=/opt/kys/uploads
export SERVER_PORT=8081
java -jar demo-0.0.1-SNAPSHOT.jar
```

生产环境的 `application-prod.yaml` 已配置 MySQL、Redis、邮件、日志与上传目录。
首次启动会自动生成管理员、教师名册、4 个班级学生名册和示例课程。

## 五、目录结构

```
src/main/java/com/example/demo
├── common/          统一返回、业务异常
├── config/          安全、MyBatis-Plus、Web、数据初始化
├── controller(web)  登录注册、学生、教师、管理员、文件、全局异常
├── entity/          班级、学生、教师、账号、课程、作业、提交、公告、月统计
├── form/            注册、课程、作业、评分、公告、资料、密码表单
├── mapper/          MyBatis-Plus BaseMapper
├── security/        UserDetailsService、登录成功跳转
└── service/         业务、缓存、邮件、文件、定时任务
```

## 六、阿里云部署提示

详细步骤见 [DEPLOY.md](DEPLOY.md)。

- 安全组放行 80/443（Nginx），8081 仅本机访问
- MySQL、Redis 只监听 127.0.0.1，不要暴露公网
- 上传目录、日志目录使用持久化磁盘并定期备份
- 修改默认管理员密码，配置真实 SMTP 与 HTTPS
