FROM maven:3.9.11-ibm-semeru-25-noble as builder
ARG BUILD_DIR=/home/ubuntu
WORKDIR $BUILD_DIR

USER 1000
#ENV http_proxy=http://172.17.0.1:3128
#RUN cd /build/$APP && mvn dependency:go-offline -B

RUN mvn dependency:go-offline
COPY --chown=1000:0 . $BUILD_DIR

RUN mvn  clean package -DskipTests

FROM ibm-semeru-runtimes:open-25-jre-noble
ARG APP
ARG TLS=true
USER 0
#RUN dnf install -y procps-ng && dnf clean all
USER 1000

COPY --from=builder --chown=1000:0  $BUILD_DIR/target/*.*ar $BUILD_DIR
COPY --from=builder --chown=1001:0 $BUILD_DIR/target/lib/*.*ar $BUILD_DIR

