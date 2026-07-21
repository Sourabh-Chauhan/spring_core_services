package com.chauhan.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;

/**
 * Service for rendering externalized Thymeleaf HTML email templates with dynamic model variables.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateRenderingService {

    private final SpringTemplateEngine templateEngine;

    /**
     * Renders a Thymeleaf HTML template located under 'templates/' directory.
     *
     * @param templateName The path/name of the template relative to templates directory (e.g. "email/welcome-email").
     * @param variables    Map of key-value attributes to bind into the HTML template context.
     * @return Rendered HTML string.
     */
    public String render(String templateName, Map<String, Object> variables) {
        log.debug("Rendering Thymeleaf HTML template [{}] with variable keys [{}]", templateName, variables != null ? variables.keySet() : null);

        Context context = new Context();
        if (variables != null && !variables.isEmpty()) {
            context.setVariables(variables);
        }

        return templateEngine.process(templateName, context);
    }
}
