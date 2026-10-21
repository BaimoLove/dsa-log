# 运行镜像：只 COPY jar，不编译。
# 纪律：服务器上不跑 mvn —— 2G 机器编译 Spring Boot 极易 OOM；构建只在本机做。
FROM eclipse-temurin:17-jre-alpine

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_TOOL_OPTIONS=""

WORKDIR /app
COPY app.jar /app/app.jar

# 交给 docker 的 json-file 收集，容器内不再写文件日志
EXPOSE 8080

# -Xmx448m 与 compose 的 mem_limit: 900m 是双重保险：
# 堆先 OOM（有 HeapDump）比容器被内核直接 kill 更好查
ENTRYPOINT ["java", \
  "-Xms448m", "-Xmx448m", "-Xss512k", \
  "-XX:+UseSerialGC", "-XX:MaxMetaspaceSize=192m", "-XX:MaxDirectMemorySize=64m", \
  "-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=/app/dump", \
  "-Duser.timezone=Asia/Shanghai", "-Dfile.encoding=UTF-8", \
  "-jar", "/app/app.jar", \
  "--server.address=127.0.0.1", "--spring.profiles.active=prod"]
