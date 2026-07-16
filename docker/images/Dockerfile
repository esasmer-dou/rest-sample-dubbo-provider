# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM ibm-semeru-runtimes:open-21-jre

WORKDIR /app

COPY --from=build /workspace/target/rest-sample-dubbo-provider-*.jar /app/rest-sample-dubbo-provider.jar

ENV DUBBO_PROVIDER_APPLICATION_NAME=rest-sample-dubbo-provider \
    DUBBO_PROVIDER_HOST=rest-sample-dubbo-provider \
    DUBBO_PROVIDER_BIND_HOST=0.0.0.0 \
    DUBBO_PROVIDER_PORT=20880 \
    REACTOR_DUBBO_REGISTRY_ENABLED=false \
    SAMPLE_DB_JDBC_URL=jdbc:postgresql://postgres:5432/reactor_sample \
    SAMPLE_DB_USERNAME=reactor \
    SAMPLE_DB_DRIVER_CLASS_NAME=org.postgresql.Driver \
    SAMPLE_DB_POSTGRESQL_APPLICATION_NAME=rest-sample-dubbo-provider \
    SAMPLE_DB_MAXIMUM_POOL_SIZE=2 \
    SAMPLE_DB_MINIMUM_IDLE=0 \
    SAMPLE_DB_SCHEMA_INIT=true \
    SAMPLE_DB_WARMUP=true \
    IO_NETTY_ALLOCATOR_NUMDIRECTARENAS=1 \
    IO_NETTY_ALLOCATOR_NUMHEAPARENAS=1 \
    IO_NETTY_RECYCLER_MAXCAPACITYPERTHREAD=0 \
    IO_NETTY_NOPREFERDIRECT=true \
    DUBBO_APPLICATION_QOS_ENABLE=false \
    DUBBO_METRICS_ENABLE=false \
    DUBBO_TRACING_ENABLED=false \
    JAVA_TOOL_OPTIONS="-Xms32m -Xmx128m -Xss256k -XX:MaxRAMPercentage=60"

EXPOSE 20880

ENTRYPOINT ["java", "-jar", "/app/rest-sample-dubbo-provider.jar"]
