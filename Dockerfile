FROM maven:3.9.1-amazoncorretto-17 AS build

ENV MAVEN_HOME /usr/share/maven
ENV MAVEN_CONFIG "$USER_HOME_DIR/.m2"

COPY ./pom.xml /usr/app/

RUN mvn -f /usr/app/pom.xml verify

COPY ./src /usr/app/src/

RUN mvn -f /usr/app/pom.xml -DskipTests clean package && mv /usr/app/target/searchadapter-*.jar /docker-image.jar

FROM amazoncorretto:17.0.7-alpine3.17

COPY --from=build /docker-image.jar /docker-image.jar
RUN sh -c 'touch /docker-image.jar' && apk update && apk add tzdata

ENV JAVA_OPTS="-Xmx4g"

ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -jar /docker-image.jar" ]