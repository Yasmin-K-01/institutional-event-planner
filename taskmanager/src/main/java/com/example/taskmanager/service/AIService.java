package com.example.taskmanager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AIService {

    private static final Logger logger = LoggerFactory.getLogger(AIService.class);

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${app.ai.enabled:true}")
    private boolean aiEnabled;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<String> generateSubtasks(String taskTitle) {
        List<String> subtasks = new ArrayList<>();

        // If AI service is disabled, return dev mock subtasks
        if (!aiEnabled) {
            logger.info("[OFFLINE MODE] Gemini API call skipped for task: {}", taskTitle);
            subtasks.add("Task Planning (Dev Mock)");
            subtasks.add("Execution (Dev Mock)");
            subtasks.add("Review (Dev Mock)");
            return subtasks;
        }

        if (apiKey == null || apiKey.isEmpty()) {
            subtasks.add("Break down requirement");
            subtasks.add("Implement solution");
            subtasks.add("Test and verify");
            return subtasks;
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;

        try {
            Map<String, Object> requestBody = new HashMap<>();
            
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", "Generate 3 short actionable subtasks for: " + taskTitle + ". Return only subtask titles line by line.");

            Map<String, Object> contentMap = new HashMap<>();
            contentMap.put("parts", List.of(textPart));

            requestBody.put("contents", List.of(contentMap));

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, requestBody, Map.class);

            if (response != null && response.containsKey("candidates")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");

                if (candidates != null && !candidates.isEmpty()) {
                    Map<String, Object> candidate = candidates.get(0);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> content = (Map<String, Object>) candidate.get("content");

                    if (content != null && content.containsKey("parts")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");

                        if (parts != null && !parts.isEmpty()) {
                            Map<String, Object> firstPart = parts.get(0);
                            String text = (String) firstPart.get("text");

                            if (text != null) {
                                String[] lines = text.split("\n");
                                for (String line : lines) {
                                    String cleaned = line.replaceAll("^[0-9\\-\\.\\*\\s]+", "").trim();
                                    if (!cleaned.isEmpty()) {
                                        subtasks.add(cleaned);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            subtasks.add("Define scope for " + taskTitle);
            subtasks.add("Execute primary tasks");
            subtasks.add("Final check and completion");
        }

        if (subtasks.isEmpty()) {
            subtasks.add("Plan execution steps");
            subtasks.add("Execute task");
            subtasks.add("Review result");
        }

        return subtasks;
    }
}