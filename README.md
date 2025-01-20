# Crane

Crane is a small Spring Boot project used for hosting authenticated R
Repositories. It is designed to be used in combination with RDepot. Because
Crane combines the hosting of directories with authentication and authorization
it can be used for many other use-cases, such as the hosting of documentation or
project assets.

Learn more at https://crane.rdepot.io

## Building from source

Clone this repository and run

```bash
mvn -U clean install
```

The build will result in a single `.jar` file: `target/crane-${CRANE_VERSION}-SNAPSHOT-exec.jar` where crane

## Running

Crane can either be run locally or using the [docker image](https://hub.docker.com/r/openanalytics/crane-snapshot).

To run crane locally the following command can be used:
```bash
java -jar target/crane-${CRANE_VERSION}-SNAPSHOT-exec.jar
```

## Java Version
This project requires JDK 17.

**(c) Copyright Open Analytics NV, 2021-2025 - Apache License 2.0**
