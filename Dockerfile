FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/my-rag-agent-1.0.0.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseG1GC", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]
