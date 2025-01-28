# Crane

Crane is a webserver with advanced Authentication and Authorization (using
OpenID Connect) for hosting data science artifacts, documentation and much more!

Learn more at <https://craneserver.net/>

## Building from source

Clone this repository and run

```bash
mvn -U clean install
```

The build will result in a single `.jar` file: `target/crane-${CRANE_VERSION}-exec.jar` where crane

## Running

Crane can either be run locally or using the [docker image](https://hub.docker.com/r/openanalytics/crane).

To run crane locally the following command can be used:

```bash
java -jar target/crane-${CRANE_VERSION}-exec.jar
```

## Java Version

This project requires JDK 17.

**(c) Copyright Open Analytics NV, 2021-2025 - Apache License 2.0**
