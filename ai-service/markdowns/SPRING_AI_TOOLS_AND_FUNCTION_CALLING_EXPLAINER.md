# 🚀 Beginner's Guide: Spring AI Tools & Function Calling

Welcome! If you are new to AI and building Spring Boot microservices, this document explains **what Spring AI Tools & Function Calling are**, **why we need them**, and **how they work under the hood** in our `ai-service`.

---

## 💡 The Core Problem: Why Standard AI Isn't Enough

Imagine asking a regular AI model like ChatGPT:
> *"How much JVM memory is my server currently using, and is `auth-service` running?"*

A standard AI model **cannot answer this question accurately** on its own because:
1. **AI models only generate text:** They only know what was in their training data.
2. **AI cannot see your live system:** The AI model doesn't have access to your local machine, running databases, or microservice ports.
3. **AI hallucinates when asked for live data:** Without live system access, an AI might make up fake numbers or guess.

---

## 🛠️ The Solution: Spring AI Tools & Function Calling

**Function Calling (also called AI Tools)** gives the AI model **"hands and eyes"** to execute code inside your application!

Instead of guessing, when a user asks about live data:
1. The AI model realizes: *"I need live system metrics to answer this question."*
2. The AI model sends a request back to your Java application: *"Please call the `getSystemMetrics()` method."*
3. Your Java method runs locally, fetches actual JVM memory and thread metrics, and returns the data back to the AI.
4. The AI formats that real data into a clean, friendly response for the user.

```text
 ┌──────────────┐                 ┌──────────────┐                 ┌──────────────────┐
 │ User Prompt  │                │ Spring AI    │                 │ Local Java Method│
 │ "Check JVM   │───────────────►│ ChatClient   │───────────────►│ @Tool            │
 │  Memory"     │                │ & LLM        │                 │ SystemMetricsTool│
 └──────────────┘                 └──────┬───────┘                 └────────┬─────────┘
        ▲                                │                                  │
        │                                │ Calls Java method dynamically    │
        │                                ◄──────────────────────────────────┘
        │ Returns final natural response │  Returns live JVM memory data
        └────────────────────────────────┘
```

---

## 🎯 Why Do We Need This in `ai-service`?

In our enterprise microservices platform (`spring_core_services`), adding AI Tools provides huge benefits:

| Use Case                    | Without AI Tools                                                              | With Spring AI Tools                                                                                          |
|:----------------------------|:------------------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------------------|
| **System Diagnostics**      | Admin must inspect server logs, JMX consoles, or run shell commands manually. | Ask AI: *"Check system health and memory usage"*, AI executes `@Tool getSystemMetrics()` and explains status. |
| **Microservice Monitoring** | Admin checks Eureka dashboard (`http://localhost:8761`) manually.             | Ask AI: *"Is `auth-service` UP?"*, AI executes `@Tool getMicroserviceHealth("auth-service")`.                 |
| **User Support & Lookups**  | Support agent opens database tools to run SQL queries for user info.          | Ask AI: *"Get details for user@example.com"*, AI executes `@Tool getUserDetailsByEmail("user@example.com")`.  |

---

## 🧩 What We Built in `ai-service`

We created two dedicated `@Tool` components in `ai-service`:

### 1. `SystemMetricsTool` ([SystemMetricsTool.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/ai-service/src/main/java/com/chauhan/aiservice/tools/SystemMetricsTool.java))
Annotated Java component providing live JVM metrics:
- `@Tool getSystemMetrics()`: Returns JVM heap usage, max heap, thread count, CPU cores, uptime, and OS name.
- `@Tool getMicroserviceHealth(serviceName)`: Checks if microservices like `eureka-server`, `auth-service`, `gateway-service`, or `notification-service` are `UP`.

```java
@Component
public class SystemMetricsTool {

    @Tool(name = "getSystemMetrics", description = "Get real-time JVM system metrics including heap memory usage, active thread count, CPU processors, and system uptime.")
    public SystemMetrics getSystemMetrics() {
        // Reads live JVM memory and thread management beans
    }
}
```

### 2. `UserServiceTool` ([UserServiceTool.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/ai-service/src/main/java/com/chauhan/aiservice/tools/UserServiceTool.java))
Annotated Java component for user statistics and lookups:
- `@Tool getUserDetailsByEmail(email)`: Looks up user profile details by email.
- `@Tool getSystemUserStatistics()`: Returns aggregate platform user statistics (total users, active users, admins).

---

## ⚙️ How Tools Are Bound to the AI Prompt

In [AiConfig.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/ai-service/src/main/java/com/chauhan/aiservice/config/AiConfig.java), we registered our tool beans with `ChatClient`:

```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder,
                            VectorStore vectorStore,
                            SystemMetricsTool systemMetricsTool,
                            UserServiceTool userServiceTool) {
    return builder
            .defaultSystem("You are an intelligent AI Assistant... You have access to system tools to query real-time system metrics and user service details when requested.")
            .defaultTools(systemMetricsTool, userServiceTool) // 👈 Binds tools to ChatClient
            .build();
}
```

---

## 📡 API Endpoint to Try It Out

We exposed an endpoint in [AiController.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/ai-service/src/main/java/com/chauhan/aiservice/controller/AiController.java):

* **Endpoint:** `POST /api/v1/ai/tools/execute`
* **Request Body:**
```json
{
  "prompt": "What is the current JVM memory usage and is auth-service online?"
}
```
* **What Happens:**
  1. `AiController` forwards prompt to `ChatClient`.
  2. LLM detects that it needs system metrics and service health.
  3. LLM triggers `getSystemMetrics()` and `getMicroserviceHealth("auth-service")` automatically.
  4. LLM returns a complete natural language response summarizing real system stats!

---

## 🎓 Key Takeaway for AI Beginners

- **LLMs are brilliant at language, but blind to real-time data.**
- **Function Calling (`@Tool`) bridges the gap:** It allows AI to act as a conversational interface over your existing Java code, databases, and microservices.
