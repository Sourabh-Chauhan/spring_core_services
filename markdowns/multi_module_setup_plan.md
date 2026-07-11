
This plan outlines the steps to refactor the standalone microservices (`auth-service` and `gateway-service`) into a single, unified **multi-module Maven project** managed by a root parent `pom.xml`.

---

## 1. Architectural Strategy

We will establish a hierarchical Maven structure where:
1. The **Root Parent POM** inherits from `spring-boot-starter-parent` and centralizes dependency management, shared properties (such as Java version), and build configurations.
2. The **Child Modules** (`auth-service` and `gateway-service`) inherit from the root parent POM, removing redundant configuration and dependency version definitions.

### Before
```
spring_core_services/
  ├── auth-service/
  │    └── pom.xml (inherits from spring-boot-starter-parent)
  └── gateway-service/
       └── pom.xml (inherits from spring-boot-starter-parent)
```

### After
```
spring_core_services/
  ├── pom.xml (Root Parent POM - inherits from spring-boot-starter-parent)
  ├── auth-service/
  │    └── pom.xml (inherits from Root Parent POM)
  └── gateway-service/
       └── pom.xml (inherits from Root Parent POM)
```

---

## 2. Step-by-Step Execution Plan

### Step 1: Create the Root Parent `pom.xml`
Create a new `pom.xml` file at the root `/run/media/sourabh/WorkSpace/Java/Spring boot/MicroServices/spring_core_services/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" 
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>

    <groupId>com.chauhan</groupId>
    <artifactId>spring-core-services</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>spring-core-services</name>
    <description>Root parent POM for Spring Core Microservices</description>

    <modules>
        <module>auth-service</module>
        <module>gateway-service</module>
    </modules>

    <properties>
        <java.version>21</java.version>
        <spring-cloud.version>2023.0.3</spring-cloud.version>
        <jjwt.version>0.13.0</jjwt.version>
        <modelmapper.version>3.2.4</modelmapper.version>
        <uap-java.version>1.6.1</uap-java.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Spring Cloud Dependencies -->
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- JWT API -->
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-api</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-impl</artifactId>
                <version>${jjwt.version}</version>
                <scope>runtime</scope>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-jackson</artifactId>
                <version>${jjwt.version}</version>
                <scope>runtime</scope>
            </dependency>

            <!-- ModelMapper -->
            <dependency>
                <groupId>org.modelmapper</groupId>
                <artifactId>modelmapper</artifactId>
                <version>${modelmapper.version}</version>
            </dependency>

            <!-- User Agent Parser -->
            <dependency>
                <groupId>com.github.ua-parser</groupId>
                <artifactId>uap-java</artifactId>
                <version>${uap-java.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

### Step 2: Refactor `auth-service/pom.xml`
Modify [auth-service/pom.xml](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/pom.xml):
- Update the `<parent>` tag to point to the root parent POM.
- Remove `<groupId>` and `<version>` since they are inherited.
- Remove `<properties>` containing java version.
- Remove `<version>` tags from dependencies managed by the parent (`jjwt-*`, `modelmapper`, `uap-java`).

### Step 3: Refactor `gateway-service/pom.xml`
Modify [gateway-service/pom.xml](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/gateway-service/pom.xml):
- Update the `<parent>` tag to point to the root parent POM.
- Remove `<groupId>` and `<version>`.
- Remove `<properties>` and the entire `<dependencyManagement>` section since Spring Cloud versions are managed in the parent.
- Remove `<version>` tags from JWT dependencies.

---

## 3. Validation & Build Verification

To verify that the project structure is valid, we will execute:
1. **Root Build Compilation**:
   ```bash
   mvn clean compile
   ```
   *Expected outcome:* Maven builds both modules sequentially and reports a success matrix.
2. **Test Compilation Check**:
   ```bash
   mvn test-compile
   ```
   *Expected outcome:* Successfully compiles all integration and unit tests for both modules.
