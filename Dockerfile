FROM openjdk:17

WORKDIR /app

COPY . .

ADD server/target/scala-3.4.3/server-assembly-1.0.1.jar .

ENTRYPOINT ["java", "-jar", "server-assembly-1.0.1.jar"]

EXPOSE 4041