package com.example.taskmanager.controller;

import com.example.taskmanager.model.SubTask;
import com.example.taskmanager.model.Task;
import com.example.taskmanager.model.User;
import com.example.taskmanager.repository.TaskRepository;
import com.example.taskmanager.repository.UserRepository;
import com.example.taskmanager.service.AIService;
import com.example.taskmanager.service.EmailService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*")
public class TaskController {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final AIService aiService;
    private final EmailService emailService;
    private final SimpMessagingTemplate messagingTemplate;

    public TaskController(TaskRepository taskRepository, UserRepository userRepository, AIService aiService,
                          EmailService emailService, SimpMessagingTemplate messagingTemplate) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.aiService = aiService;
        this.emailService = emailService;
        this.messagingTemplate = messagingTemplate;
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

    @PostMapping(value = "/upload-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadSchedule(@RequestParam("file") MultipartFile file) {
        String username = getCurrentUsername();
        User currentUser = userRepository.findByUsername(username).orElse(null);
        if (!isAdmin(username, currentUser)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin login required to import schedules");
        }
        if (file.isEmpty() || file.getOriginalFilename() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an .xlsx or .csv file");
        }

        String filename = file.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".csv")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .xlsx and .csv files are supported");
        }

        try {
            if (filename.endsWith(".xlsx")) {
                return ResponseEntity.ok(importInstitutionWorkbook(file, username));
            }
        } catch (IOException | RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not parse the schedule file", exception);
        }

        List<String[]> rows;
        try {
            rows = readCsv(file);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not parse the schedule file", exception);
        }

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The schedule file has no data rows");
        }

        Map<String, Integer> columns = headerIndexes(rows.remove(0));
        List<String> required = List.of("task name", "target hours", "due date", "assigned student username/email");
        if (required.stream().anyMatch(column -> !columns.containsKey(column))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Required headers: Task Name, Description, Target Hours, Due Date, Assigned Student Username/Email");
        }

        int imported = 0;
        List<String> errors = new ArrayList<>();
        for (int rowNumber = 0; rowNumber < rows.size(); rowNumber++) {
            String[] row = rows.get(rowNumber);
            try {
                String assignedValue = requiredValue(row, columns, "assigned student username/email");
                User student = userRepository.findByUsername(assignedValue)
                        .or(() -> userRepository.findByEmail(assignedValue)).orElse(null);

                Task task = new Task();
                task.setTitle(requiredValue(row, columns, "task name"));
                task.setDescription(value(row, columns, "description"));
                task.setHours(Double.parseDouble(requiredValue(row, columns, "target hours")));
                task.setDueDate(parseDate(requiredValue(row, columns, "due date")));
                task.setAssignedTo(student == null ? assignedValue : student.getUsername());
                task.setUser(student);
                task.setCategory("Calendar");
                task.setPriority(Task.Priority.MEDIUM);
                task.setStatus("In Progress");
                task.setRole("admin");
                task.setCreatedBy(username);

                Task saved = taskRepository.save(task);
                messagingTemplate.convertAndSend("/topic/tasks", saved);
                messagingTemplate.convertAndSend("/topic/calendar-tasks", saved);
                imported++;
            } catch (RuntimeException exception) {
                errors.add("Row " + (rowNumber + 2) + ": " + exception.getMessage());
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("imported", imported);
        response.put("errors", errors);
        return ResponseEntity.ok(response);
    }

    private List<String[]> readCsv(MultipartFile file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().filter(line -> !line.isBlank()).map(this::parseCsvLine).toList();
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                quoted = !quoted;
            } else if (character == ',' && !quoted) {
                values.add(value.toString().trim());
                value.setLength(0);
            } else {
                value.append(character);
            }
        }
        values.add(value.toString().trim());
        return values.toArray(String[]::new);
    }

    private Map<String, Object> importInstitutionWorkbook(MultipartFile file, String username) throws IOException {
        int imported = 0;
        List<String> errors = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int rowIndex = 5; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                String dateValue = cellValue(row.getCell(2), formatter);
                if (dateValue.isBlank()) {
                    continue;
                }
                try {
                    Task task = new Task();
                    task.setDueDate(parseDate(dateValue));
                    task.setTitle(requiredCellValue(row.getCell(3), formatter, "title of the event"));
                    task.setCategory(cellValue(row.getCell(4), formatter));
                    task.setFacultyCoordinator(cellValue(row.getCell(5), formatter));
                    task.setDescription(task.getFacultyCoordinator().isBlank()
                            ? "Institution calendar event"
                            : "Faculty coordinator: " + task.getFacultyCoordinator());
                    task.setHours(0D);
                    task.setAssignedTo("ALL");
                    task.setPriority(Task.Priority.MEDIUM);
                    task.setStatus("In Progress");
                    task.setRole("admin");
                    task.setCreatedBy(username);

                    Task saved = taskRepository.save(task);
                    messagingTemplate.convertAndSend("/topic/tasks", saved);
                    messagingTemplate.convertAndSend("/topic/calendar-tasks", saved);
                    imported++;
                } catch (RuntimeException exception) {
                    errors.add("Row " + (rowIndex + 1) + ": " + exception.getMessage());
                }
            }
        }
        if (imported == 0 && errors.isEmpty()) {
            throw new IllegalArgumentException("The workbook has no dated events from row 6 onward");
        }
        Map<String, Object> response = new HashMap<>();
        response.put("imported", imported);
        response.put("errors", errors);
        return response;
    }

    private String cellValue(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        if (DateUtil.isCellDateFormatted(cell)) {
            Date date = cell.getDateCellValue();
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("d/M/yyyy"));
        }
        return formatter.formatCellValue(cell).trim();
    }

    private String requiredCellValue(Cell cell, DataFormatter formatter, String column) {
        String value = cellValue(cell, formatter);
        if (value.isBlank()) {
            throw new IllegalArgumentException(column + " is required");
        }
        return value;
    }

    private Map<String, Integer> headerIndexes(String[] headers) {
        Map<String, Integer> indexes = new HashMap<>();
        for (int index = 0; index < headers.length; index++) {
            indexes.put(headers[index].trim().toLowerCase(), index);
        }
        return indexes;
    }

    private String value(String[] row, Map<String, Integer> columns, String column) {
        Integer index = columns.get(column);
        return index == null || index >= row.length ? "" : row[index].trim();
    }

    private String requiredValue(String[] row, Map<String, Integer> columns, String column) {
        String value = value(row, columns, column);
        if (value.isBlank()) {
            throw new IllegalArgumentException(column + " is required");
        }
        return value;
    }

    private LocalDate parseDate(String value) {
        try {
            double excelSerial = Double.parseDouble(value);
            if (DateUtil.isValidExcelDate(excelSerial)) {
                Date date = DateUtil.getJavaDate(excelSerial);
                return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
        } catch (NumberFormatException ignored) {
        }
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/yyyy"), DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yyyy"))) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new IllegalArgumentException("due date must use yyyy-MM-dd or M/d/yyyy");
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
