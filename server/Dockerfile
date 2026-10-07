# Збірка всередині Docker: на машині не потрібні ні JDK, ні Maven.
# Обидва образи multi-arch (amd64 і arm64), тож те саме збирається на x86 мініПК
# і на малині з 64-бітною ОС.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# Стеля для Maven, щоб збірка на малині з 2 ГБ не душила працюючу базу
ENV MAVEN_OPTS="-Xmx512m"
COPY pom.xml .
# Окремий шар із залежностями: при зміні коду вони не качаються заново
RUN mvn -q -B dependency:go-offline
# Довідник інструментів живе в корені репо (shared/), поза контекстом server/.
# Приходить окремим build-контекстом: compose — additional_contexts.shared,
# вручну — docker build --build-context shared=../shared . (з server/).
# Maven бере його з ../shared, тобто /shared.
COPY --from=shared instruments.json /shared/instruments.json
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
# start-period з запасом: Spring на Pi 4 з SD-карти стартує довше, ніж на ПК
HEALTHCHECK --interval=15s --timeout=3s --start-period=180s --retries=4 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health HTTP/1.0\r\n\r\n" >&3 && grep -q UP <&3'
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
