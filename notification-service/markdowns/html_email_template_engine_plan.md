# Implementation Plan: Externalized HTML Email Templates & Thymeleaf Template Engine

## Section 0: Architectural Rationale & Technical Context

### 1. Architectural Overview
Hardcoding HTML strings inside Java classes (such as text blocks in `NotificationEventListener.java`) leads to maintainability issues, tight coupling between business logic and presentation layer, and lack of visual tooling support for HTML/CSS designers.

To resolve this and introduce enterprise-grade email templating:
1. **Thymeleaf Template Engine Integration**: We add `spring-boot-starter-thymeleaf` to `notification-service/pom.xml`. Spring Boot automatically provisions a `SpringTemplateEngine` bean capable of rendering HTML templates with model attributes (`org.thymeleaf.context.Context`).
2. **Externalized HTML Templates**: We create dedicated, standalone HTML template files under `src/main/resources/templates/email/`:
   - `welcome-email.html`: Registration welcome & verification token email template.
   - `password-reset-email.html`: Password reset link / token email template.
3. **Template Rendering Service (`TemplateRenderingService`)**: We create a specialized Spring service component that encapsulates `SpringTemplateEngine`. It accepts a template name and a map of variables, returning the rendered HTML string ready for `MimeMessageHelper`.
4. **Listener Refactoring**: We update `NotificationEventListener` to use `TemplateRenderingService` instead of hardcoded inline Java text blocks.

---

## 2. Component Design & Changes

### A. Dependencies (`notification-service/pom.xml`)
- Add `spring-boot-starter-thymeleaf` dependency.

### B. HTML Template Files
- **`src/main/resources/templates/email/welcome-email.html`**:
  - Uses Thymeleaf attributes: `th:text="${name}"` and `th:text="${verificationToken}"`.
  - Professional responsive HTML email layout with CSS styling.
- **`src/main/resources/templates/email/password-reset-email.html`**:
  - Uses Thymeleaf attributes: `th:text="${name}"` and `th:text="${resetToken}"`.

### C. Service Implementation (`TemplateRenderingService.java`)
- Inject `SpringTemplateEngine`.
- Provide `render(String templatePath, Map<String, Object> variables)` method.

### D. Refactored Event Listener (`NotificationEventListener.java`)
- Inject `TemplateRenderingService`.
- Call `templateRenderingService.render("email/welcome-email", variables)` to dynamically generate email HTML.

---

## 3. Verification & Testing Plan
1. **Compilation Check**: Run `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 mvn clean compile`.
2. **Unit Tests**: Create `TemplateRenderingServiceTest` to verify HTML rendering with Thymeleaf variable substitution.
3. **Run All Tests**: Execute `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 mvn clean test`.
4. **Checklist Update**: Update `notification-service/markdowns/checklist.md` marking the HTML email template engine tasks as completed.
