ARG BASE_IMAGE='eclipse-temurin:11-jre'
FROM $BASE_IMAGE
ARG DIST_JAR_NAME
ARG JAVA_OPTS
ARG BASE_PATH=/NT
ARG WORK_HOME=$BASE_PATH/config

WORKDIR $BASE_PATH
ENV JAVA_OPTS $JAVA_OPTS
ENV BASE_PATH $BASE_PATH
RUN sh -c 'mkdir -p $WORK_HOME' \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    echo "Asia/Shanghai" > /etc/timezone \
    dpkg-reconfigure -f noninteractive tzdata
COPY $DIST_JAR_NAME app.jar
VOLUME $WORK_HOME
EXPOSE 9070
ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Dspring.config.location=/NT/config/ -jar $BASE_PATH/app.jar" ]