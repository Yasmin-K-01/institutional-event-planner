package com.example.taskmanager.controller;

import com.example.taskmanager.model.SubTask;
import com.example.taskmanager.model.Task;
import com.example.taskmanager.model.User;
import com.example.taskmanager.repository.TaskRepository;
import com.example.taskmanager.repository.UserRepository;
import com.example.taskmanager.service.AIService;
import com.example.taskmanager.service.EmailService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*")
public class TaskController {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final AIService aiService;
    private final EmailService emailService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public TaskController(TaskRepository taskRepository, UserRepository userRepository, AIService aiService, EmailService emailService) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.aiService = aiService;
        this.emailService = emailService;
    }

    private String getCurrentUsername() {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            return "anonymousUser";
        }
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private boolean isAdmin(String username, User user) {
        // Check if username contains "admin" (case-insensitive)
        if ("admin".equalsIgnoreCase(username) || username.toLowerCase().contains("admin")) {
            return true;
        }
        
        // Check if user entity has ADMIN role (case-insensitive)
        if (user != null && user.getRole() != null) {
            String role = user.getRole().toUpperCase();
            return "ADMIN".equals(role) || "ROLE_ADMIN".equals(role);
        }
        
        return false;
    }

    @GetMapping
    public List<Task> getAllTasks() {
        String username = getCurrentUsername();
        
        // 1. Anonymous users should not see any data
        if ("anonymousUser".equalsIgnoreCase(username)) {
            return Collections.emptyList();
        }
        
        // 2. Fetch user from database
        User user = userRepository.findByUsername(username).orElse(null);
        
        // 3. Admin sees all tasks
        if (isAdmin(username, user)) {
            return taskRepository.findAll();
        }
        
        // 4. Regular user: Show tasks where they are the user, creator, or assigned recipient
        return taskRepository.findAll().stream()
                .filter(t -> {
                    // Check if user is the task owner (t.getUser())
                    if (t.getUser() != null && username.equalsIgnoreCase(t.getUser().getUsername())) {
                        return true;
                    }
                    // Check if user created the task
                    if (username.equalsIgnoreCase(t.getCreatedBy())) {
                        return true;
                    }
                    // Check if task is assigned to this user by username (MOST IMPORTANT FOR CROSS-DEVICE)
                    if (t.getAssignedTo() != null && username.equalsIgnoreCase(t.getAssignedTo())) {
                        return true;
                    }
                    // Check if task is assigned to ALL users
                    if ("ALL".equalsIgnoreCase(t.getAssignedTo())) {
                        return true;
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getTaskStats() {
        List<Task> tasks = getAllTasks();

        long totalTasks = tasks.size();
        long completedCount = tasks.stream().filter(Task::isCompleted).count();
        long pendingCount = totalTasks - completedCount;

        long highPriority = tasks.stream().filter(t -> t.getPriority() != null && "HIGH".equalsIgnoreCase(t.getPriority().name())).count();
        long mediumPriority = tasks.stream().filter(t -> t.getPriority() != null && "MEDIUM".equalsIgnoreCase(t.getPriority().name())).count();
        long lowPriority = tasks.stream().filter(t -> t.getPriority() != null && "LOW".equalsIgnoreCase(t.getPriority().name())).count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", totalTasks);
        stats.put("completed", completedCount);
        stats.put("pending", pendingCount);
        stats.put("highPriority", highPriority);
        stats.put("mediumPriority", mediumPriority);
        stats.put("lowPriority", lowPriority);

        return ResponseEntity.ok(stats);
    }

    @PostMapping
    public Task createTask(@RequestBody Task task) {
        String username = getCurrentUsername();
        if ("anonymousUser".equalsIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required to create a task");
        }

        User user = userRepository.findByUsername(username).orElse(null);
        task.setUser(user);
        task.setCreatedBy(username);
        task.setAssignedTo(username);
        task.setRole("student");
        if (task.getCategory() == null || task.getCategory().isEmpty()) {
            task.setCategory("General");
        }
        if (task.getStatus() == null || task.getStatus().isEmpty()) {
            task.setStatus("In Progress");
        }
        
        Task saved = taskRepository.save(task);

        messagingTemplate.convertAndSend("/topic/admin-tasks", saved);
        messagingTemplate.convertAndSend("/topic/tasks", saved);

        try {
            emailService.sendTaskNotification(username, "New Task Created: " + saved.getTitle(),
                    "Hello " + username + ",\nYour task '" + saved.getTitle() + "' was successfully created.");
        } catch (Exception ignored) {
        }

        return saved;
    }

    @PostMapping("/assign")
    public Task assignTask(@RequestBody Task task) {
        String username = getCurrentUsername();
        
        // Verify admin permission
        User currentUser = userRepository.findByUsername(username).orElse(null);
        if (!isAdmin(username, currentUser)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin login required to assign tasks");
        }
        
        // Assign task to the specified user
        if (task.getUser() != null && task.getUser().getId() != null) {
            User student = userRepository.findById(task.getUser().getId()).orElse(null);
            task.setUser(student);
            // CRITICAL: Set assignedTo to the username so task is visible across all devices
            if (student != null) {
                task.setAssignedTo(student.getUsername());
            }
        } else if (task.getUser() != null && task.getUser().getUsername() != null) {
            User student = userRepository.findByUsername(task.getUser().getUsername()).orElse(null);
            task.setUser(student);
            // CRITICAL: Set assignedTo to the username so task is visible across all devices
            if (student != null) {
                task.setAssignedTo(student.getUsername());
            }
        }

        if (task.getCategory() == null || task.getCategory().isEmpty()) {
            task.setCategory("General");
        }
        if (task.getStatus() == null || task.getStatus().isEmpty()) {
            task.setStatus("In Progress");
        }
        task.setRole("admin");
        task.setCreatedBy(username);

        Task savedTask = taskRepository.save(task);
        
        messagingTemplate.convertAndSend("/topic/admin-tasks", savedTask);
        messagingTemplate.convertAndSend("/topic/tasks", savedTask);
        
        return savedTask;
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Task> updateTaskStatus(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        String newStatus = payload.get("status");
        return taskRepository.findById(id).map(task -> {
            task.setStatus(newStatus);
            if ("COMPLETED".equalsIgnoreCase(newStatus)) {
                task.setCompleted(true);
            } else {
                task.setCompleted(false);
            }
            return ResponseEntity.ok(taskRepository.save(task));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public Task updateTask(@PathVariable Long id, @RequestBody Task updatedTask) {
        return taskRepository.findById(id).map(task -> {
            task.setTitle(updatedTask.getTitle());
            task.setDescription(updatedTask.getDescription());
            task.setPriority(updatedTask.getPriority());
            task.setDueDate(updatedTask.getDueDate());
            task.setCompleted(updatedTask.isCompleted());
            task.setCategory(updatedTask.getCategory());
            task.setStatus(updatedTask.getStatus());
            return taskRepository.save(task);
        }).orElseThrow(() -> new RuntimeException("Task not found with id " + id));
    }

    @DeleteMapping("/{id}")
    public String deleteTask(@PathVariable Long id) {
        taskRepository.deleteById(id);
        return "Task deleted successfully with id " + id;
    }

    @PostMapping("/{id}/generate-subtasks")
    public ResponseEntity<Task> generateSubTasks(@PathVariable Long id) {
        return taskRepository.findById(id).map(task -> {
            List<String> subtaskTitles = aiService.generateSubtasks(task.getTitle());
            for (String title : subtaskTitles) {
                SubTask subTask = new SubTask(title, task);
                task.getSubTasks().add(subTask);
            }
            Task saved = taskRepository.save(task);
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportTasksToPdf() {
        List<Task> tasks = getAllTasks();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(out);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        document.add(new Paragraph("Task Manager - Summary Report")
                .setFontSize(18)
                .setBold()
                .setMarginBottom(15));

        Table table = new Table(UnitValue.createPercentArray(new float[]{25, 15, 20, 20, 20}))
                .useAllAvailableWidth();

        table.addHeaderCell("Title");
        table.addHeaderCell("Category");
        table.addHeaderCell("Priority");
        table.addHeaderCell("Due Date");
        table.addHeaderCell("Status");

        for (Task task : tasks) {
            table.addCell(task.getTitle() != null ? task.getTitle() : "");
            table.addCell(task.getCategory() != null ? task.getCategory() : "General");
            table.addCell(task.getPriority() != null ? task.getPriority().toString() : "N/A");
            table.addCell(task.getDueDate() != null ? task.getDueDate().toString() : "N/A");
            table.addCell(task.getStatus() != null ? task.getStatus() : (task.isCompleted() ? "COMPLETED" : "TODO"));
        }

        document.add(table);
        document.close();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "task_report.pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(out.toByteArray());
    }
}
