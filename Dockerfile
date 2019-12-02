FROM maven:3-jdk-11-slim as builder
COPY . /app
RUN cd /app && mvn clean package

FROM oracle/graalvm-ce:19.3.0-java11 as native
COPY --from=builder /app/target/socialbot.jar /socialbot.jar
RUN gu install native-image
RUN native-image -jar /socialbot.jar --no-fallback --allow-incomplete-classpath --enable-https --static

FROM ubuntu:18.04
COPY --from=native /socialbot /socialbot
RUN chmod +x /socialbot
CMD ["/socialbot"]
