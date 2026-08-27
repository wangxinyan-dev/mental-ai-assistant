# 第一阶段：构建
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
# 使用阿里云 Maven 镜像加速依赖下载
COPY settings.xml /root/.m2/settings.xml
RUN mvn clean package -DskipTests

# 第二阶段：运行（用 JRE 瘦身镜像）
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 创建上传目录
RUN mkdir -p /app/uploads

# 从构建阶段复制 JAR
COPY --from=builder /app/target/*.jar app.jar

# 暴露端口
EXPOSE 1236

# 通过环境变量注入敏感配置（不要在镜像里写死）
ENV SERVER_PORT=1236
ENV DB_USERNAME=root
ENV DB_PASSWORD=change-me-in-production
ENV JWT_SECRET=change-me-in-production
ENV AI_API_KEY=your-api-key
ENV SPRING_PROFILES_ACTIVE=prod

# 启动
ENTRYPOINT ["java", "-jar", "app.jar"]
