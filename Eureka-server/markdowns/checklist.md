# Eureka Server Development Checklist

This checklist tracks the setup, configuration, and verification tasks for the centralized Service Registry.

## Phase 1: Project Setup & Maven Configuration

- [x] **Reactor Integration:**
    - [x] Register the `Eureka-server` module in the root `pom.xml`.
- [x] **POM Refactoring:**
    - [x] Inherit from the `spring-core-services` parent POM.
    - [x] Remove redundant `groupId` and `version` fields.
    - [x] Remove duplicate properties (`java.version`, `spring-cloud.version`).
    - [x] Remove redundant `<dependencyManagement>` section.
- [x] **Dependencies:**
    - [x] Ensure `spring-cloud-starter-netflix-eureka-server` dependency is declared correctly.

## Phase 2: Server Coding & Properties Configuration

- [x] **Main Application class:**
    - [x] Create the runner class under `com.chauhan.eurekaserver.EurekaServerApplication`.
    - [x] Add `@EnableEurekaServer` annotation.
- [x] **Application Configuration:**
    - [x] Create `application.yml` inside the resource directory (`src/main/resources`).
    - [x] Set server port to `8761`.
    - [x] Set `register-with-eureka: false` to disable self-registration.
    - [x] Set `fetch-registry: false` to disable standalone registry downloads.

## Phase 3: Client Discovery Integration

- [x] **Auth-Service Connection:**
    - [x] Add Eureka Client dependency in `auth-service/pom.xml`.
    - [x] Enable client registry properties in `auth-service/src/main/resources/application-dev.yml`.
- [x] **Gateway-Service Connection:**
    - [x] Add Eureka Client dependency in `gateway-service/pom.xml`.
    - [x] Enable client properties inside the gateway's `application.yml`.

## Phase 4: Runtime Verification

- [ ] **Dashboard Check:**
    - [ ] Run `Eureka-server` and verify access to UI dashboard on `http://localhost:8761`.
- [ ] **Service Discovery Verification:**
    - [ ] Run `auth-service` and verify it registers with Eureka dashboard under the `AUTH-SERVICE` ID.
    - [ ] Run `gateway-service` and verify it registers and can route calls to `lb://auth-service/`.
