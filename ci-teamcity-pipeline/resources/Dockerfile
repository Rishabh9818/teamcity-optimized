# Use an official OpenJDK image as the base
FROM openjdk:11-jre-slim

# Set the working directory in the container
WORKDIR /app

# Copy the application (e.g., JAR file) into the container
COPY . /app

# Expose the port if needed (if your app runs on a specific port)
EXPOSE 8080

# Define the command to run the application (e.g., run a JAR file)
CMD ["java", "-jar", "your-app.jar"]
