# Architectural Design & Implementation Plan: Multi-Module Maven POM Refactoring

This document outlines the refactoring strategy for standardizing Maven dependency management, plugin inheritance, and BOM imports across the root parent `pom.xml` and all submodules (`auth-service`, `gateway-service`, `notification-service`, `Eureka-server`).

---

## Section 0: Technical Rationale for Multi-Module POM Standardization

### 1. Centralized Dependency Management (`<dependencyManagement>`)
In enterprise Spring Cloud microservices, managing dependency versions independently across submodules causes **version drift**, **classloader conflicts**, and **transitive dependency bugs** (e.g. `auth-service` using JWT `0.13.0` while `gateway-service` resolves a different version).
* **Solution:** Declare all shared third-party dependencies (JWT, ModelMapper, User-Agent Parser, Twilio, Firebase Admin, Resilience4j) in the root parent `<dependencyManagement>`. Submodules declare dependencies without specifying `<version>` tags, ensuring 100% version alignment.

### 2. Spring Boot & Spring Cloud BOM Alignment
* **Spring Boot Starter Parent:** Provides central version management for all `org.springframework.boot` starters (AMQP, Mail, WebFlux, Data JPA, Redis, Security, Validation).
* **Spring Cloud BOM (`spring-cloud-dependencies`):** Provides central version management for all `org.springframework.cloud` starters (Eureka Server/Client, API Gateway, Resilience4j).
* **Submodule Cleanliness:** Submodules should never specify versions for Spring Boot or Spring Cloud starters.

### 3. Plugin Management (`<pluginManagement>`)
* Declare `spring-boot-maven-plugin` and `maven-compiler-plugin` settings in root parent `<build><pluginManagement>`.
* Submodules inherit plugin configurations cleanly without repeating plugin executions or configuration boilerplate.

---

## Refactoring Plan by Module

### 1. Root `pom.xml`
- Consolidate all dependency version properties under `<properties>`.
- Expand `<dependencyManagement>` to cover all shared libraries across microservices:
  - Spring Cloud Dependencies BOM (`2025.1.2`)
  - JJWT (`0.13.0`)
  - ModelMapper (`3.2.4`)
  - UAP-Java (`1.6.1`)
  - Twilio SDK (`10.6.0`)
  - Firebase Admin (`9.4.3`)
  - Lombok (`org.projectlombok:lombok`)
- Add `<build><pluginManagement>` to define `spring-boot-maven-plugin` and `maven-compiler-plugin` (with Lombok annotation processor configuration).

### 2. `Eureka-server/pom.xml`
- Clean parent declaration.
- Clean dependency declarations for `spring-cloud-starter-netflix-eureka-server` and `spring-boot-starter-test` without explicit version tags.

### 3. `gateway-service/pom.xml`
- Standardize dependencies (`spring-cloud-starter-gateway-server-webflux`, `spring-cloud-starter-netflix-eureka-client`, `spring-boot-starter-data-redis-reactive`, `jjwt-api`, `jjwt-impl`, `jjwt-jackson`, `spring-boot-starter-security`, `spring-cloud-starter-circuitbreaker-reactor-resilience4j`).
- Inherit versions from parent `<dependencyManagement>`.

### 4. `auth-service/pom.xml`
- Standardize dependencies and eliminate duplicate test dependency declarations.
- Inherit Lombok and compiler configurations from root `pluginManagement`.

### 5. `notification-service/pom.xml`
- Standardize dependencies (`spring-boot-starter-amqp`, `spring-boot-starter-mail`, `spring-boot-starter-webflux`, `spring-cloud-starter-netflix-eureka-client`, `twilio`, `firebase-admin`, `lombok`).
- Inherit versions from root parent `<dependencyManagement>`.

---

## Verification & Build Strategy

Run full project compilation from root directory with JDK 25 constraint:
```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 mvn clean compile -DskipTests
```
Ensure all 4 submodules (`Eureka-server`, `gateway-service`, `auth-service`, `notification-service`) compile cleanly with **BUILD SUCCESS**.
