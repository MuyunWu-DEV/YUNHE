# ============================================================
# YUNHE Website — 应用镜像
# 构建方式：本地 mvn package 产出 jar → COPY 进 JRE 镜像（云端无需 JDK/Maven）
# 用法见 deploy/README-部署.md
#
# 若你的 ECS 能访问 Docker Hub：直接用下面的 eclipse-temurin。
# 若网络受限(国内无加速)，把基础镜像换成阿里云内网/加速源里的 JRE，例如：
#   FROM registry.cn-hangzhou.aliyuncs.com/xxx/temurin:21-jre
# ============================================================

# ---- 运行时基础镜像：Java 21 JRE（与 pom <java.version>21 一致）----
FROM eclipse-temurin:21-jre

# 中文字体渲染依赖的系统库（fontconfig + freetype）已被 temurin-jre 自带部分；
# 若后续 PDF/图文字体异常，再 uncomment 安装 fontconfig。TZ 必须设置避免时区偏差。
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8

# 应用工作目录
WORKDIR /app

# 应用自身非 root 运行更安全
RUN useradd --create-home --shell /bin/bash appuser \
    && mkdir -p /app && chown -R appuser:appuser /app
USER appuser

# 拷贝本机已构建好的可执行 jar（构建前先 mvn package；文件名可用 *.jar 通配）
# 注意：build context 必须是项目根（见 docker-compose.yml build.context），故此处相对 context 取 target/
COPY target/*.jar /app/yunhe-website.jar

# 健康检查：Spring Boot Actuator 未显式引入时用 TCP 探测 8080 兜底
EXPOSE 8080

# 生产用 prod profile；库连接经环境变量注入（见 docker-compose.yml）
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-Xms512m -Xmx1024m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/yunhe-website.jar"]
