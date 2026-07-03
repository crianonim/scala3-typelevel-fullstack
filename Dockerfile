FROM eclipse-temurin:17-jre

WORKDIR /app

COPY . .

ADD server/target/scala-3.6.4/server-assembly-1.0.1.jar .

ENTRYPOINT ["java", "-jar", "server-assembly-1.0.1.jar"]

EXPOSE 8080