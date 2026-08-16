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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping
    public List<Task> getAllTasks() {
        String username = getCurrentUsername();
        
        // 1. லாகின் செய்யாத பயனர் (anonymousUser) என்றால் டேட்டா எதுவும் அனுப்பக்கூடாது
        if ("anonymousUser".equalsIgnoreCase(username)) {
            return Collections.emptyList();
        }
        
        // 2. Admin லாகின் செய்திருந்தால் மட்டுமே அனைத்து டாஸ்க்குகளையும் அனுப்ப வேண்டும்
        if ("admin".equalsIgnoreCase(username) || username.toLowerCase().contains("admin")) {
            return taskRepository.findAll();
        }
        
        // 3. Student லாகின் செய்திருந்தால், அந்த மாணவருக்குரிய டாஸ்க்குகளை மட்டுமே அனுப்ப வேண்டும்
        return taskRepository.findAll().stream()
                .filter(t -> t.getUser() != null && username.equalsIgnoreCase(t.getUser().getUsername()))
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
        if (!"anonymousUser".equalsIgnoreCase(username)) {
            User user = userRepository.findByUsername(username).orElse(null);
            task.setUser(user);
        }
        if (task.getCategory() == null || task.getCategory().isEmpty()) {
            task.setCategory("General");
        }
        if (task.getStatus() == null || task.getStatus().isEmpty()) {
            task.setStatus("TODO");
        }
        
        Task saved = taskRepository.save(task);

        emailService.sendTaskNotification(username, "New Task Created: " + saved.getTitle(), 
                "Hello " + username + ",\nYour task '" + saved.getTitle() + "' was successfully created.");

        return saved;
    }

    @PostMapping("/assign")
    public Task assignTask(@RequestBody Task task) {
        // மாணவருக்கு டாஸ்க் அசைன் செய்யும்போது Admin ID மேல்எழுதப்படாமல் (Overwrite) இருக்க
        if (task.getUser() != null && task.getUser().getId() != null) {
            User student = userRepository.findById(task.getUser().getId()).orElse(null);
            task.setUser(student);
        } else if (task.getUser() != null && task.getUser().getUsername() != null) {
            User student = userRepository.findByUsername(task.getUser().getUsername()).orElse(null);
            task.setUser(student);
        }

        if (task.getCategory() == null || task.getCategory().isEmpty()) {
            task.setCategory("General");
        }
        if (task.getStatus() == null || task.getStatus().isEmpty()) {
            task.setStatus("TODO");
        }

        Task savedTask = taskRepository.save(task);
        
        messagingTemplate.convertAndSend("/topic/admin-tasks", savedTask);
        
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
