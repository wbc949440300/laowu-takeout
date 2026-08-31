# 老吴外卖后端镜像：多阶段构建（Maven 编译 + JDK8 精简运行）
FROM maven:3.8-openjdk-8 AS build
WORKDIR /build
COPY pom.xml .
COPY sky-common/pom.xml sky-common/
COPY sky-pojo/pom.xml sky-pojo/
COPY sky-server/pom.xml sky-server/
# 先拉依赖（利用 Docker 层缓存）
RUN mvn dependency:go-offline -B
COPY sky-common sky-common
COPY sky-pojo sky-pojo
COPY sky-server sky-server
RUN mvn package -DskipTests -B

FROM openjdk:8-jre-slim
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /build/sky-server/target/sky-server-1.0-SNAPSHOT.jar app.jar
# 数据库/Redis 等敏感配置通过环境变量覆盖（Spring Boot 支持 SKY_DATASOURCE_PASSWORD 形式）
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
