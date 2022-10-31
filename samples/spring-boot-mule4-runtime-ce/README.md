## Build Spring Boot Mule 4 Runtime CE

Check available **Mule Runtime CE BOM** versions at [MuleSoft's public maven repository](https://repository.mulesoft.org/nexus/content/repositories/releases/org/mule/distributions/mule-runtime-impl-bom/).

Or check available bom versions at your local maven repository directory (`.m2/repository/org/mule/distributions/mule-bom`)

1. Build, with Mule Runtime BOM version 4.4.0:

``` bash
mvn clean package -Dmule.bom.version=4.4.0
````

2. (OPTIONAL) Install docker image, with Mule Runtime BOM version 4.4.0:

``` bash
mvn clean install -Pdocker -Dmule.bom.version=4.4.0
```

3. Run sample Mule application:

``` bash
java -Dmule.base=./test/mule -Dmule.apps=file:../test-mule-app/target/test-mule-app-1.0.0-mule-application.jar -Dmule.cleanStartup=true -jar target/spring-boot-mule4-runtime-ce-4.4.0.jar
```
