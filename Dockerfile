FROM eclipse-temurin:17-jdk

WORKDIR /app
COPY . .

RUN chmod +x ./gradlew && ./gradlew --no-daemon bootJar

EXPOSE 8091
CMD ["java", "-jar", "build/libs/notification-service-0.0.1-SNAPSHOT.jar"]
