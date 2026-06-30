FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S liferay && adduser -S liferay -G liferay

COPY --chown=liferay:liferay *.jar /opt/liferay/jar-runner.jar

ENV JDK_JAVA_OPTIONS="--add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.nio.channels=ALL-UNNAMED --add-opens=java.base/java.security=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED"

USER liferay

EXPOSE 58081

CMD ["java", "-jar", "/opt/liferay/jar-runner.jar"]