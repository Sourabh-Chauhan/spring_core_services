package com.chauhan.batchservice;

import com.chauhan.batchservice.service.JobExecutionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class JobLauncherControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobExecutionService jobExecutionService;

    @Test
    @DisplayName("GET /api/jobs should return all registered jobs")
    void testListJobs() throws Exception {
        mockMvc.perform(get("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalJobs", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.availableJobs", hasItems("helloWorldJob", "chunkBasicsJob", "csvToDatabaseJob")));
    }

    @Test
    @DisplayName("POST /api/jobs/helloWorldJob should trigger and complete execution")
    void testLaunchJobSuccess() throws Exception {
        mockMvc.perform(post("/api/jobs/helloWorldJob")
                        .param("source", "integrationTest")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobName", is("helloWorldJob")))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.exitCode", is("COMPLETED")))
                .andExpect(jsonPath("$.jobExecutionId", notNullValue()))
                .andExpect(jsonPath("$.jobInstanceId", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/jobs/{invalid} should return 404 Not Found")
    void testLaunchJobNotFound() throws Exception {
        mockMvc.perform(post("/api/jobs/nonExistentJob")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.errorMessage", containsString("not found")));
    }
}
