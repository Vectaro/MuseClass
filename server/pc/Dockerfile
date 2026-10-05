# Збірка всередині Docker: на машині не потрібні ні JDK, ні Maven.
# Копія під робочий ПК (x86-64, Docker Desktop на WSL 2).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
# Окремий шар із залежностями: при зміні коду вони не качаються заново
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --no-create-home museclass
WORKDIR /app
COPY --from=build /src/target/museclass-server-*.jar app.jar
USER museclass
EXPOSE 8080
# Запасний варіант для запуску без compose; compose задає явний -Xmx (API_HEAP)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --retries=4 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health HTTP/1.0\r\n\r\n" >&3 && grep -q UP <&3'
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
