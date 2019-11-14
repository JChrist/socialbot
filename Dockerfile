FROM adoptopenjdk/openjdk11:alpine-jre
COPY ./target/socialbot.jar /socialbot.jar
CMD ["java", "-jar", "/socialbot.jar"]
