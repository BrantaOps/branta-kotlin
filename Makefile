.PHONY: setup build test clean

setup:
	gradle wrapper --gradle-version 8.10.2

build:
	./gradlew build

test:
	./gradlew test

clean:
	./gradlew clean
