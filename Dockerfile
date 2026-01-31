FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y busybox && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 80
ENTRYPOINT ["java", "-jar", "app.jar"]
