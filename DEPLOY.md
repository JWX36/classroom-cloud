# 阿里云 ECS 部署说明

以 Ubuntu 22.04 / 24.04 + JDK 21 + MySQL 8 + Redis + Nginx 为例。

## 1. 安装运行环境

```bash
sudo apt update
sudo apt install -y openjdk-21-jre-headless mysql-server redis-server nginx
sudo systemctl enable --now mysql redis nginx
```

确认版本：

```bash
java -version
mysql --version
redis-server --version
```

## 2. 初始化数据库

```bash
sudo mysql -e "CREATE DATABASE kys DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;"
sudo mysql -e "CREATE USER 'kys'@'127.0.0.1' IDENTIFIED BY '强密码';"
sudo mysql -e "GRANT ALL PRIVILEGES ON kys.* TO 'kys'@'127.0.0.1'; FLUSH PRIVILEGES;"
mysql -ukys -p kys < db/schema.sql
```

Redis 仅本机访问，确认 `/etc/redis/redis.conf`：

```conf
bind 127.0.0.1
requirepass 你的Redis密码
```

## 3. 打包并上传

本地执行：

```bash
./mvnw clean package -DskipTests
```

上传 `target/demo-0.0.1-SNAPSHOT.jar` 到服务器：

```bash
sudo mkdir -p /opt/kys/uploads /opt/kys/logs
sudo useradd -r -s /bin/false kys || true
sudo chown -R kys:kys /opt/kys
```

## 4. 配置环境变量

新建 `/etc/kys.env`：

```bash
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8081
DB_URL=jdbc:mysql://127.0.0.1:3306/kys?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
DB_USERNAME=kys
DB_PASSWORD=你的数据库密码
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=你的Redis密码
MAIL_HOST=smtp.qq.com
MAIL_PORT=465
MAIL_USERNAME=你的邮箱
MAIL_PASSWORD=邮箱授权码
UPLOAD_DIR=/opt/kys/uploads
LOG_FILE=/opt/kys/logs/kys.log
```

```bash
sudo chmod 600 /etc/kys.env
```

## 5. systemd 服务

`/etc/systemd/system/kys.service`：

```ini
[Unit]
Description=KYS Classroom Cloud
After=network.target mysql.service redis-server.service

[Service]
Type=simple
User=kys
WorkingDirectory=/opt/kys
EnvironmentFile=/etc/kys.env
ExecStart=/usr/bin/java -Xms256m -Xmx512m -jar /opt/kys/demo-0.0.1-SNAPSHOT.jar
SuccessExitStatus=143
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now kys
sudo systemctl status kys
curl http://127.0.0.1:8081/api/health
```

## 6. Nginx 反向代理

`/etc/nginx/sites-available/kys`：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    client_max_body_size 60m;

    location / {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/kys /etc/nginx/sites-enabled/kys
sudo nginx -t
sudo systemctl reload nginx
```

HTTPS 可使用 `certbot --nginx` 申请免费证书。

## 7. 阿里云安全组

- 放行 `80`、`443`
- **不要**放行 `3306`、`6379`、`8081`
- 服务器只允许 SSH 管理

## 8. 首次登录与安全

- 访问 `http://服务器IP/`，管理员 `admin / admin123`
- 登录后立即在个人中心修改管理员密码
- 关闭演示账号或删除演示学生账号
- 数据库、上传目录、日志目录纳入定期备份
