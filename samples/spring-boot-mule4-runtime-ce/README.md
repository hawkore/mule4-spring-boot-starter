## Build Spring Boot Mule 4 Runtime CE

Build spring boot application:

``` bash
mvn clean package
```

Build spring boot application into a docker image:

``` bash
mvn clean package -Pdocker
```

Run sample Mule application:

``` bash
java -Dmule.base=./test/mule -Dmule.apps=file:../test-mule-app/target/test-mule-app-1.0.0-mule-application.jar -Dmule.cleanStartup=true -jar target/spring-boot-mule4-runtime-ce-4.2.2.jar
```
