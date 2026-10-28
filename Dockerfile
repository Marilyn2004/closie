FROM node:24-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml ./
RUN npm install --global pnpm@11.25.0 && pnpm install --frozen-lockfile
COPY frontend/index.html frontend/vite.config.js ./
COPY frontend/src ./src
COPY frontend/public ./public
RUN pnpm build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY pom.xml ./
RUN mvn --batch-mode --quiet dependency:go-offline
COPY src ./src
COPY --from=frontend /build/frontend/dist/ ./src/main/resources/static/
RUN mvn --batch-mode --quiet -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /build/target/fashion-search-engine-0.0.1-SNAPSHOT.jar /app/closie.jar
RUN groupadd --system closie && useradd --system --gid closie closie
USER closie
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:InitialRAMPercentage=20 -XX:+UseSerialGC -XX:ActiveProcessorCount=2"
ENV SPRING_PROFILES_ACTIVE=public-demo
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/closie.jar"]
