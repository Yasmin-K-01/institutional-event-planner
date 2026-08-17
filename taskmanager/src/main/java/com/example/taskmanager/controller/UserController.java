package com.example.taskmanager.controller;

import com.example.taskmanager.model.Task;
import com.example.taskmanager.model.User;
import com.example.taskmanager.repository.TaskRepository;
import com.example.taskmanager.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;

    public UserController(UserRepository userRepository, TaskRepository taskRepository) {
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getUsers() {
        List<Map<String, Object>> users = userRepository.findAll().stream()
                .filter(user -> user.getUsername() != null && !user.getUsername().toLowerCase().contains("admin"))
                .map(user -> {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("id", user.getId());
                    payload.put("username", user.getUsername());
                    payload.put("email", user.getEmail());
                    return payload;
                })
                .toList();

        return ResponseEntity.ok(users);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        String username = user.getUsername();

        List<Task> associatedTasks = taskRepository.findAll().stream()
                .filter(task -> isAssociatedWithUser(task, user, username))
                .toList();
        taskRepository.deleteAll(associatedTasks);
        userRepository.delete(user);

        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }

    private boolean isAssociatedWithUser(Task task, User user, String username) {
        if (task.getUser() != null && user.getId().equals(task.getUser().getId())) {
            return true;
        }
        if (username == null) {
            return false;
        }
        return username.equalsIgnoreCase(task.getCreatedBy())
                || username.equalsIgnoreCase(task.getAssignedTo());
    }
}
