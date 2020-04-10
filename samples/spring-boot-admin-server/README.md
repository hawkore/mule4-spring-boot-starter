## Build Spring Boot Admin server

Build spring boot application:

``` bash
mvn clean package
```

Build spring boot application into a docker image:

``` bash
mvn clean package -Pdocker
```

Run `spring-boot-admin-server`:

``` bash
java -jar target/spring-boot-admin-server-1.0.0.jar
```
