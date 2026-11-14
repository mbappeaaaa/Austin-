# 使用openjdk8的镜像（openjdk:8-jre 已被 Docker Hub 废弃，改用 eclipse-temurin:8-jre）
FROM eclipse-temurin:8-jre

ENV PARAMS="--spring.profiles.active=test"

# 设置工作目录
WORKDIR /build

# 将jar包复制到容器中
ADD ./austin-web/target/austin-web-0.0.1-SNAPSHOT.jar ./austin.jar

# 运行jar包
ENTRYPOINT ["sh","-c","java -jar $JAVA_OPTS austin.jar $PARAMS"]
