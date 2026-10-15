FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle gradle.properties ./
COPY buildSrc buildSrc
COPY modality-library modality-library
COPY modality-modules modality-modules
COPY modality-services modality-services
COPY modality-boot modality-boot

RUN chmod +x gradlew \
    && ./gradlew --no-daemon --no-build-cache :modality-boot:bootJar -x test

FROM eclipse-temurin:21-jre

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -Duser.timezone=Asia/Shanghai"

RUN groupadd --system --gid 10001 modality \
    && useradd --system --uid 10001 --gid modality --home-dir /app --shell /usr/sbin/nologin modality

WORKDIR /app

COPY --from=builder /workspace/build/modality-boot/libs/modality-boot-[0-9]*.jar /app/app.jar

RUN chown -R modality:modality /app

USER modality

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
