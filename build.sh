#!/bin/bash

docker run --rm -it -v "$PWD":/app -v "$HOME/.m2":/root/.m2 -w /app maven:3-jdk-11-slim mvn clean package