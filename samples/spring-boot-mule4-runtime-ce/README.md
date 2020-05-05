## Build Spring Boot Mule 4 Runtime CE

Build, with Mule Runtime BOM version 4.2.2-hf5:

``` bash
mvn clean package -Dmule.bom.version=4.2.2-hf5
````

Install docker image, with Mule Runtime BOM version 4.2.2-hf5:

``` bash
mvn clean install -Pdocker -Dmule.bom.version=4.2.2-hf5
```

Run sample Mule application:

``` bash
java -Dmule.base=./test/mule -Dmule.apps=file:../test-mule-app/target/test-mule-app-1.0.0-mule-application.jar -Dmule.cleanStartup=true -jar target/spring-boot-mule4-runtime-ce-4.2.2.jar
```
