# 构建阶段
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /build

# 复制父项目 pom.xml
COPY pom.xml .
# 复制子模块 app 源码和 pom.xml
COPY app/pom.xml app/
COPY app/src app/src
# 复制 test 模块的 pom.xml（满足父模块声明）
COPY test/pom.xml test/

# 编译打包 (跳过测试，只构建 app 模块)
RUN mvn clean package -pl app -am -DskipTests

# 运行阶段
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# 设置默认时区环境变量
ENV TZ=Asia/Shanghai

# 从构建阶段复制 jar 包
COPY --from=build /build/app/target/app-1.0-SNAPSHOT-exec.jar app.jar

# 创建必要目录：data 用于 H2, logs 用于运行日志
RUN mkdir -p /app/data /app/logs

# 暴露端口：8080 (Web), 2525 (SMTP)
EXPOSE 8080 2525

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]
