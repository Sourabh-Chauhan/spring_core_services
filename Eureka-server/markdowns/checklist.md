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
- [ ] **Dependencies:**
    - [ ] Ensure `spring-cloud-starter-netflix-eureka-server` dependency is declared correctly.

## Phase 2: Server Coding & Properties Configuration

- [ ] **Main Application class:**
    - [ ] Create the runner class under `com.chauhan.eurekaserver.EurekaServerApplication`.
    - [ ] Add `@EnableEurekaServer` annotation.
- [ ] **Application Configuration:**
    - [ ] Create `application.yml` inside the resource directory (`src/main/resources`).
    - [ ] Set server port to `8761`.
    - [ ] Set `register-with-eureka: false` to disable self-registration.
    - [ ] Set `fetch-registry: false` to disable standalone registry downloads.

## Phase 3: Client Discovery Integration

- [ ] **Auth-Service Connection:**
    - [ ] Add Eureka Client dependency in `auth-service/pom.xml`.
    - [ ] Enable client registry properties in `auth-service/src/main/resources/application-dev.yml`.
- [ ] **Gateway-Service Connection:**
    - [ ] Add Eureka Client dependency in `gateway-service/pom.xml`.
    - [ ] Enable client properties inside the gateway's `application.yml`.

## Phase 4: Runtime Verification

- [ ] **Dashboard Check:**
    - [ ] Run `Eureka-server` and verify access to UI dashboard on `http://localhost:8761`.
- [ ] **Service Discovery Verification:**
    - [ ] Run `auth-service` and verify it registers with Eureka dashboard under the `AUTH-SERVICE` ID.
    - [ ] Run `gateway-service` and verify it registers and can route calls to `lb://auth-service/`.
