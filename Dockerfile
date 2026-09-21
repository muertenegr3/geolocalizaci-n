# Multi-stage build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY --from=build /app/target/*.jar app.jar
ENV PORT=8082
EXPOSE 8082
HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl -f http://localhost:8082/api/geolocalizacion/health || exit 1
ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=$PORT"]
