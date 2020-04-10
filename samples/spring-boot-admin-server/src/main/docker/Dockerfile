FROM openjdk:8u212-jdk-slim-stretch

MAINTAINER hawkore

# curl for health check
RUN apt-get update && apt-get install -y curl && apt-get clean

ENV ARTIFACT=@project.artifactId@-@project.version@.jar

ENV USER_JVM_OPTS=""

ENV DEFAULT_JVM_OPTS="-XX:+AlwaysPreTouch -XX:+UseG1GC -XX:+ScavengeBeforeFullGC -Dfile.encoding=UTF-8 -Djava.net.preferIPv4Stack=true"

COPY maven/$ARTIFACT /opt/sba/

# Define working directory.
WORKDIR /opt/sba

# Startup command
CMD java $DEFAULT_JVM_OPTS $USER_JVM_OPTS -jar $ARTIFACT

# Default spring boot admin port
EXPOSE 8882
