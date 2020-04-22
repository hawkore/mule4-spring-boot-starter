## Build Spring Boot Mule 4 Runtime EE

- You will need access to Mulesoft's private repository (https://repository.mulesoft.org/nexus/content/repositories/private/), 
otherwise, try to install AnypointStudio 7+, add Mule Runtime 4.2.2 EE to AnypointStudio (or another Mule enterprise version to match the starter version you use) and 
create from AnypointStudio a Mule Application with that Mule Runtime EE version. This should update your local maven repository with required Mule enterprise modules to build embedded Mule 4 Runtime EE as a Spring Boot application ;).

- To deploy and run applications on production, you will need a valid Mule EE license into the classpath of your Spring Boot Mule Runtime application. Follow the steps at [Install an Enterprise License](https://docs.mulesoft.com/mule-runtime/4.2/installing-an-enterprise-license) and 
[Install Enterprise License on Embedded Mule](https://docs.mulesoft.com/mule-runtime/4.2/installing-an-enterprise-license#install-enterprise-license-on-embedded-mule).

Build Spring Boot application:

``` bash
mvn clean package
```

Build Spring Boot application into a docker image:

``` bash
mvn clean package -Pdocker
```

Run sample Mule application in testing mode (`-Dmule.testingMode=true`):

_The testing mode flag, indicates that EE license validation is disabled. So you can freely deploy and run apps with EE features such as Dataweave without providing a license for testing purposes. If you would like to run your app in production mode you will need a valid Mule EE license._ 

``` bash
java -Dmule.base=./test/mule -Dmule.apps=file:../test-mule-app/target/test-mule-app-1.0.0-mule-application.jar -Dmule.cleanStartup=true -Dmule.testingMode=true -jar target/spring-boot-mule4-runtime-ee-4.2.2.jar
```
