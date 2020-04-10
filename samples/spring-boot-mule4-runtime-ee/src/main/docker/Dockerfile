FROM openjdk:8u212-jdk-slim-stretch

MAINTAINER hawkore

# curl for health check
RUN apt-get update && apt-get install -y curl && apt-get clean

ENV ARTIFACT=@project.artifactId@-@project.version@.jar

ENV USER_JVM_OPTS=""

ENV DEFAULT_JVM_OPTS="-Dmule.base=/opt/mule -XX:+AlwaysPreTouch -XX:+UseG1GC -XX:+ScavengeBeforeFullGC -Dfile.encoding=UTF-8 -Djava.net.preferIPv4Stack=true"

COPY maven/$ARTIFACT /opt/mule/
COPY maven/mule/ /opt/mule/

# Define working directory.
WORKDIR /opt/mule

VOLUME ["/opt/mule/logs","/opt/mule/apps","/opt/mule/domains","/opt/mule/conf"]

# Startup command
CMD java $DEFAULT_JVM_OPTS $USER_JVM_OPTS -jar $ARTIFACT

# Default spring boot port (Mule deployment services)
EXPOSE 8880
