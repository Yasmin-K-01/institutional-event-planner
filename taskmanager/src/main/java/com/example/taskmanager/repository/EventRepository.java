package com.example.taskmanager.repository;

import com.example.taskmanager.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
}
