FROM eclipse-temurin:21-jdk-alpine AS construcao
WORKDIR /codigo
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S contextpilot && adduser -S contextpilot -G contextpilot
WORKDIR /aplicacao
COPY --from=construcao /codigo/target/contextpilot-*.jar aplicacao.jar
USER contextpilot
EXPOSE 8080 9090
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Dfile.encoding=UTF-8", "-jar", "aplicacao.jar"]
