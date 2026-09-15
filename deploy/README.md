# YUNHE 官网 / CRM — 单机部署（systemd，无 Docker）

> 架构：`Spring Boot 3.3.5 (Java 21) + MySQL 8.0`，应用由 **systemd** 守护裸跑 jar。
> 不用 Docker。一台阿里云 ECS。

---

## 一、为什么这么部署

- 本机无可用 Docker（虚拟化受限），且只有一台 ECS，Docker 的隔离/编排价值体现不出来。
- 因此：**MySQL 用本机系统服务，应用用 systemd 直接跑 jar**，最简、最少依赖。

---

## 二、本机构建部署包

一条 Maven 命令产出**最终部署压缩包**（含可执行 jar + 全部脚本/配置）：

```bash
cd C:/Users/Muyun/IdeaProjects/YUNHE
mvn clean package -Pdeploy -DskipTests
```

产物：`target/yunhe-website-0.0.1-SNAPSHOT-deploy.tar.gz`（约 63MB）

> 想打包时跳过测试用 `-DskipTests`；只想出普通 jar 用 `mvn package`（不带 `-Pdeploy`）。

---

## 三、服务器部署

### 0) 服务器一次性准备（首次，手动）

1. 装 JDK 21：`apt install -y openjdk-21-jre-headless`（Alibaba/CentOS: `yum install -y java-21-openjdk-headless`）
2. 装并启动 MySQL 8：`apt install -y mysql-server && systemctl enable --now mysql`
3. 建库与业务账号（在 MySQL root 下执行一次）。**认证插件用 MySQL 8 推荐的 `caching_sha2_password`**，切勿用 `mysql_native_password`——新版 MySQL(8.4+)默认已禁用该插件，登录会报 `ERROR 1524 (Plugin 'mysql_native_password' is not loaded)`：
   ```sql
   CREATE DATABASE IF NOT EXISTS yunhe
     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER IF NOT EXISTS 'yunhe_app'@'%'
     IDENTIFIED WITH caching_sha2_password BY '<强口令>';
   ALTER USER 'yunhe_app'@'%'
     IDENTIFIED WITH caching_sha2_password BY '<强口令>';
   GRANT ALL PRIVILEGES ON yunhe.* TO 'yunhe_app'@'%';
   FLUSH PRIVILEGES;
   ```

### 1) 上传并解压

把 `target/yunhe-website-0.0.1-SNAPSHOT-deploy.tar.gz` 拷到服务器任意目录（如 `/root/`）：

```bash
# 本机 scp 上传示例
scp target/yunhe-website-0.0.1-SNAPSHOT-deploy.tar.gz root@<ECS_IP>:/root/

# 服务器上解压（平铺得到 install.sh/start.sh/stop.sh/yunhe-app.service/.env.example/yunhe-website.jar）
cd /root
tar -xzf yunhe-website-0.0.1-SNAPSHOT-deploy.tar.gz
```

### 2) 部署（首次 / 升级都执行这个）

```bash
sudo bash ./install.sh
```

脚本会：检查 JDK/MySQL → 建 `yunhe` 系统用户与 `/opt/yunhe` → 拷 jar → 生成 `/opt/yunhe/.env` → 安装并启用 systemd 服务 `yunhe-app`。

> `.env` 口令改法：`vi /opt/yunhe/.env`，把 `DB_USERNAME`/`DB_PASSWORD` 改成第 0 步建的那个账号口令（默认 `yunhe_app`，口令须与建库一致）。

### 3) 启动

```bash
sudo ./start.sh        # 启动并等待健康
```

访问：`http://<ECS_IP>:8080`（官网首页）/ `http://<ECS_IP>:8080/login`（后台）

### 4) 停止

```bash
sudo ./stop.sh
```

---

## 四、首次建管理员账号

应用默认不自动播种 admin（prod `seed-default-admin=false`）。首次登录后台前建号：

1. 编辑 `/opt/yunhe/.env`，临时开启播种并设初始密码：
   ```
   SPRING_APP_SECURITY_SEED_DEFAULT_ADMIN=true
   SPRING_APP_SECURITY_ADMIN_INITIAL_PASSWORD=<临时强密码>
   ```
2. 重启：`sudo systemctl restart yunhe-app`
3. 看日志确认已建号：`sudo journalctl -u yunhe-app -e`（搜「已创建默认管理员账号：admin」）
4. 浏览器 `/login` 用 `admin` + 临时密码登录，**系统强制改密**。
5. 改密后把 `.env` 两行改回 false/空，再 `systemctl restart yunhe-app`。

---

## 五、升级 / 重发版本

```bash
# 本机
mvn clean package -Pdeploy -DskipTests
scp target/yunhe-website-0.0.1-SNAPSHOT-deploy.tar.gz root@<ECS_IP>:/root/
# 服务器
cd /root && tar -xzf yunhe-website-0.0.1-SNAPSHOT-deploy.tar.gz
sudo bash ./install.sh     # 覆盖 jar + 保留已有 /opt/yunhe/.env，不重复建
sudo ./start.sh            # 或 systemctl restart yunhe-app
```

---

## 六、运维

```bash
# 状态
systemctl status yunhe-app
# 实时日志
journalctl -u yunhe-app -f
# 末尾 100 行
journalctl -u yunhe-app -e -n 100
# 数据库备份
mysqldump -u yunhe_app -p yunhe > yunhe-$(date +%F).sql
```

数据在 MySQL 的 `yunhe` 库（本机服务，data 目录 `/var/lib/mysql`）。备份用 `mysqldump` 即可。

---

## 七、nginx 反向代理 + HTTPS（域名接入）

应用侧已开启 `server.forward-headers-strategy: framework`（application-prod.yml），
**nginx 必须传 `X-Forwarded-Proto`/`X-Forwarded-Host`**，否则语言拦截器的 302 会降级成 http（被 403，Googlebot 无法抓取）。

```nginx
# 80 端口：全部 301 收敛到 https://www（apex + www）
server {
    listen 80;
    server_name cnyunhe.ltd www.cnyunhe.ltd;
    return 301 https://www.cnyunhe.ltd$request_uri;
}

# 443：主站（www）
server {
    listen 443 ssl;
    server_name www.cnyunhe.ltd;
    # ssl_certificate ...;（证书需覆盖 apex + www 或单独签发）

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;   # 应用按此还原 https（必须）
        proxy_set_header X-Forwarded-Host  $host;
    }
}

# 443：apex → www
server {
    listen 443 ssl;
    server_name cnyunhe.ltd;
    return 301 https://www.cnyunhe.ltd$request_uri;
}
```

改完 `nginx -t && systemctl reload nginx`。验收：四个入口
`http(s)://cnyunhe.ltd`、`http(s)://www.cnyunhe.ltd` 全部 301 收敛到
`https://www.cnyunhe.ltd/?lang=en` 后 200。

---

## 八、常见问题

| 现象 | 处理 |
|---|---|
| `java: command not found` | 没装 JDK21，见「三/0」 |
| 启动即退出，日志 `Access denied` / `Unknown database` | `.env` 账号口令错，或库没建/没授权 → 检查第三步与 `/opt/yunhe/.env` |
| 空库报 `table not found` | `SPRING_JPA_HIBERNATE_DDL_AUTO` 需 `update`（.env 默认已是），首次 update 建表 |
| 中文乱码 | 建库已用 utf8mb4；若旧库需 `ALTER DATABASE yunhe CHARACTER SET utf8mb4` |
| 时区差 8 小时 | JDBC URL 已带 `serverTimezone=Asia/Shanghai`；确保 MySQL `default-time-zone=+08:00` |
| 脚本无执行权限 | 解压后若 `bash install.sh` 即可；install 会对同目录脚本自动补 chmod |
