# Build stage
FROM maven:3.9.6-eclipse-temurin-21-jammy AS build
WORKDIR /app

# Copy pom and source
COPY pom.xml .
COPY src ./src
# Build the fat jar (using the shade plugin configured in pom.xml)
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy the fat jar from the build stage
COPY --from=build /app/target/poc-app-1.0-SNAPSHOT.jar app.jar

# Create required directories for the app
RUN mkdir -p output uploads samples

# Provide the real stamp image
COPY samples/stamp-image.png samples/stamp-image.png
# Expose the Javalin port
EXPOSE 8274

# Run the app
CMD ["java", "-jar", "app.jar"]
