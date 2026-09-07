# YUNHE 官网 / CRM — 阿里云 ECS Docker 部署指南

> 目标架构：`Spring Boot 3.3.5 (Java 21) + MySQL 8.0`，用 Docker Compose 在**一台阿里云 ECS** 上运行。
> 访问：现阶段 `http://<ECS公网IP>:8080`；域名 `cnyunhe.ltd` 备案通过后再叠加 Nginx + HTTPS（见文末）。

---

## 0. 交付物清单

| 文件 | 作用 |
|---|---|
| `deploy/Dockerfile` | 由本机构建的 jar 生成应用镜像（Java 21 JRE） |
| `deploy/docker-compose.yml` | 编排 `db`(MySQL8) + `app`，含健康检查、数据卷、端口映射 |
| `deploy/.env.example` | 数据库口令模板（复制为 `.env` 填写） |
| `deploy/deploy.sh` | 一键脚本：本机打包 → 上传 ECS → 远端 `docker compose up` |
| 根目录 `.dockerignore` | 控制 build context 大小 |

---

## 1. 前提：阿里云 ECS 准备

1. **购买 ECS**：建议 2 vCPU / 4G 起步（MySQL + JVM 至少 ~1.5G 空闲）。系统镜像选 **Ubuntu 22.04 / 24.04** 或 **Alibaba Cloud Linux 3**。
2. **安全组放行端口**（阿里云控制台 → ECS → 安全组 → 配置规则 → 入方向）：
   - `8080`：应用访问（现阶段唯一必开）
   - `22`：SSH 管理
   - （备案后若要直连 80/443 再开；现阶段 80/443 未备案会拦截）
3. **绑定公网 IP**：记下 `ECS公网IP`。
4. **登录方式**：推荐密钥对登录（控制台生成），或 root 口令。
   > ⚠️ 国内 ECS 用 80/443 + 域名对外**必须 ICP 备案**。未备案时用 `IP:8080` 不受影响。

---

## 2. 在 ECS 安装 Docker

SSH 登录 ECS 后执行（Ubuntu/Debian 为例；CentOS/Alibaba Cloud Linux 见文末附注）：

```bash
# 安装依赖
sudo apt-get update
sudo apt-get install -y ca-certificates curl

# 阿里云 Docker 源安装 docker-ce
curl -fsSL https://mirrors.aliyun.com/docker-ce/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://mirrors.aliyun.com/docker-ce/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" | sudo tee /etc/aliyun-docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# 启动并设开机自启
sudo systemctl enable --now docker

# 验证
docker --version
docker compose version
```

> 提示：ECS 拉 Docker Hub 镜像慢时，可配加速器（阿里云容器镜像服务 → 镜像加速器）或在 `/etc/docker/daemon.json` 配 `registry-mirrors` 后重启 docker。

---

## 3. 首次部署（两方式选一）

### 方式 A：一键脚本（推荐）

在**你的电脑（Windows Git Bash / macOS / Linux）**上执行：

```bash
# 1) 进入项目根，确认可 ssh/scp 到 ECS
cd C:/Users/Muyun/IdeaProjects/YUNHE
ssh root@<ECS公网IP>          # 确认能登录

# 2) 运行一键部署（首次会把部署文件 + 构建好的 jar 传上去并拉起）
cd deploy
ECS_HOST=root@<ECS公网IP> ./deploy.sh
```

脚本会依次：
1. 本机 `mvn clean package -DskipTests`（需本机有 Maven + JDK21；脚本会自动探测 `mvn.cmd`/wrapper）
2. 把 `deploy/` 配置 + `target/*.jar` scp 到 ECS `/opt/yunhe`
3. 检查远端 `.env`（没有则从模板生成并**停下让你先填口令**）
4. 远端 `docker compose up -d --build`

> 若你的本机 Maven 不在 PATH：`MVN_CMD="C:/路径/mvn.cmd" ./deploy.sh`
> 若已在 IDEA 里打包好：`SKIP_BUILD=1 ./deploy.sh`
> 改脚本顶部 `ECS_HOST`/`REMOTE_DIR` 可长期固定目标。

### 方式 B：手动

```bash
# 本机：打 jar
cd C:/Users/Muyun/IdeaProjects/YUNHE
mvn clean package -DskipTests

# 本机：把整个项目传上 ECS（或用 git clone）
scp -r deploy target root@<IP>:/opt/yunhe/   # 实际建议 git 方式

# ECS 上：
cd /opt/yunhe/deploy
cp .env.example .env
vi .env                                  # 填写强口令
cd /opt/yunhe                            # 需要 target/ 在 context 根
docker compose -f deploy/docker-compose.yml up -d --build
```

---

## 4. 配置 .env（必改）

`deploy/.env.example` → 复制为 `.env`（`.env` 不进 git、只在 ECS 上保留）：

```ini
MYSQL_ROOT_PASSWORD=强随机根口令
MYSQL_USER=yunhe
MYSQL_PASSWORD=强随机业务口令
DDL_AUTO=update        # 首次 update 建表；稳定后改 validate（见下）
```

**首次运行后**：数据库与表会自动创建——MySQL 容器首次启动用 `MYSQL_DATABASE/MYSQL_USER` 建 `yunhe` 库并授权；应用侧 `SPRING_JPA_HIBERNATE_DDL_AUTO=update` 由 Hibernate 建全部表。

> ⚠️ 生产 profile 默认 `ddl-auto: validate`（只校验不改表），但**空库 validate 起不来**，所以 compose 里用环境变量覆盖为 `update` 完成首次建表。**表结构稳定后**，把 `.env` 的 `DDL_AUTO` 改为 `validate` 再重启，可防止误改线上表结构。

---

## 5. 验证 & 常用运维

```bash
# 看两个容器状态（应为 Up + healthy）
docker compose -f /opt/yunhe/deploy/docker-compose.yml ps

# 看应用实时日志
docker compose -f /opt/yunhe/deploy/docker-compose.yml logs -f app

# 数据库日志
docker compose -f /opt/yunhe/deploy/docker-compose.yml logs db

# 应用健康：容器内看端口
curl -s http://localhost:8080 -o /dev/null -w "%{http_code}\n"   # 期望 200/302
```

浏览器访问 `http://<ECS公网IP>:8080` 应看到官网首页。
前台公开路径：`/`、`/site/**`、`/products/**`、`/about/**`、`/references`。
后台/CRM 等其它路径需登录（登录页 `/login`，见下节建 admin 后即可进入管理后台）。

---

## 6. 初始化管理员账号

生产 profile 设 `app.security.seed-default-admin: false`（**不自动播种**，权限/角色/条款库等仍会幂等初始化）。首次登录后台前需建 `admin` 管理员，推荐用代码内置的播种器（比手写 SQL 安全，且自动带「首次登录强制改密」）：

1. 在 ECS 编辑 `deploy/.env`，临时开启播种并设初始密码：
   ```ini
   SEED_ADMIN=true
   ADMIN_INITIAL_PASSWORD=临时强密码_仅用一次
   ```
2. 重启应用让它播种（此时会自动建权限、SUPER_ADMIN 角色 + `admin` 账号，表在首次启动时也已由 `update` 建好）：
   ```bash
   cd /opt/yunhe/deploy && docker compose up -d
   docker compose logs -f app        # 看到「已创建默认管理员账号：admin」即成功
   ```
3. 浏览器访问 `http://<ECS公网IP>:8080/login`（后台/CRM 登录页），用 `admin` / 临时密码登录，**系统会强制要求改密**。
4. 改密后立刻回 `deploy/.env`，把两行改回：
   ```ini
   SEED_ADMIN=false
   ADMIN_INITIAL_PASSWORD=
   ```
   再 `docker compose up -d` 重启一次，杜绝默认播种凭据驻留。

> 管理员入口/表：`SysUser` → 表 `sys_user`；若你要完全手动 INSERT，密码须为 BCrypt 编码，并手动关联 `sys_user_role` 到 SUPER_ADMIN 角色（不推荐）。

---

## 7. 升级 / 重新部署

代码改动后重新发布：

```bash
cd C:/Users/Muyun/IdeaProjects/YUNHE
ECS_HOST=root@<IP> ./deploy/deploy.sh     # 会重新 mvn package 并 compose up --build
```

只更新 jar 不重传配置：`SKIP_BUILD=1 ECS_HOST=root@<IP> ./deploy/deploy.sh`。

---

## 8. 数据库备份 / 恢复

```bash
# 备份（可放 crontab 定时）
docker exec yunhe-db sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" yunhe' > yunhe-$(date +%F).sql

# 恢复
cat yunhe-2026-09-08.sql | docker exec -i yunhe-db sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" yunhe'
```

数据持久化在 volume `mysql_data`，`docker compose down` 不会删库（`down -v` 才删，慎用）。

---

## 9. 后续：域名 cnyunhe.ltd + HTTPS（备案通过后）

未备案阶段国内 80/443 无法对外；备案通过后按此扩展：

1. ECS 安全组放行 `80`、`443`。
2. 域名 cnyunhe.ltd 解析 A 记录 → ECS 公网 IP。
3. 在 `docker-compose.yml` 增加 `nginx` 服务：`443/80` 反代到 `app:8080`，挂阿里云免费 SSL 证书（`server.crt`/`server.key`）。
4. 访问改为 `https://cnyunhe.ltd`；`app` 的 `8080` 端口可改为仅内网映射或经 Nginx 转发。

> 届时把 `ports: "8080:8080"` 收敛为只让 Nginx 暴露即可，避免应用直连 8080 暴露外网。

---

## 10. 常见坑

| 现象 | 原因 / 处理 |
|---|---|
| `app` 一直 restart，日志 `Unknown database` 或连不上 db | db 未 ready：看 `depends_on: service_healthy`；首次建库需 MySQL 初始化完成（等 30-60s） |
| `ddl-auto validate` 报错 `table not found` | 空库 validate 起不来 → 用 `.env` `DDL_AUTO=update` 起一次建表 |
| 中文乱码 | compose 已强制 `utf8mb4`；若旧库需 `ALTER DATABASE yunhe CHARACTER SET utf8mb4` |
| 时区差 8 小时 | 已设 `TZ=Asia/Shanghai` + MySQL `default-time-zone=+08:00` |
| 大 jar(70MB) scp 慢 | 首次正常；之后可只传 jar（`SKIP_BUILD=1` 复用远端配置） |
| 找不到 Maven | 设 `MVN_CMD`；或 IDEA 打包后 `SKIP_BUILD=1` |
