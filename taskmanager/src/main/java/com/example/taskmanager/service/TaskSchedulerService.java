package com.example.taskmanager.service;

import com.example.taskmanager.model.Task;
import com.example.taskmanager.repository.TaskRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class TaskSchedulerService {

    private final TaskRepository taskRepository;

    public TaskSchedulerService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    // Ovvoru naalum midnight 12:00-ku run aagum
    @Scheduled(cron = "0 0 0 * * ?")
    public void resetDailySchedule() {
        LocalDate today = LocalDate.now();

        // Database-la irukkura ellaa tasks-ahum eduthu check panrathu
        List<Task> tasks = taskRepository.findAll();

        for (Task task : tasks) {
            // Task date current date-ku munnadi irunthu, status completed illana "Overdue" nu maathum
            if (task.getDueDate() != null && task.getDueDate().isBefore(today) && !"COMPLETED".equalsIgnoreCase(task.getStatus())) {
                task.setStatus("OVERDUE");
                taskRepository.save(task);
            }
        }
        System.out.println("Daily schedule reset executed successfully for date: " + today);
    }
}
