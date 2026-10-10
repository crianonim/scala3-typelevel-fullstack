FROM azul/zulu-openjdk-alpine:17-jre-headless

WORKDIR /app

COPY app/dist ./app/dist
COPY server/target/scala-3.6.4/server-assembly-1.0.1.jar ./server-assembly-1.0.1.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "server-assembly-1.0.1.jar"]