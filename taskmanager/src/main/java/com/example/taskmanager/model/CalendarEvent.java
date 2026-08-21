package com.example.taskmanager.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "calendar_excel_events")
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String sourceSheet;

    @Column(length = 100)
    private String department;

    @Column(length = 50)
    private String serialNumber;

    @Column(name = "event_month", length = 50)
    private String month;

    private LocalDate eventDate;

    @Column(length = 100)
    private String eventDateText;

    @Column(columnDefinition = "TEXT")
    private String titleOfEvent;

    @Column(length = 500)
    private String typeOfActivity;

    @Column(columnDefinition = "TEXT")
    private String facultyCoordinator;

    @Column(length = 500)
    private String fipUploadCoordinator;

    @Column(length = 500)
    private String finalStatus;

    @Column(length = 500)
    private String unplannedStatus;

    @Column(length = 500)
    private String companyName;

    @Column(length = 500)
    private String coordinator;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSourceSheet() {
        return sourceSheet;
    }

    public void setSourceSheet(String sourceSheet) {
        this.sourceSheet = sourceSheet;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public String getEventDateText() {
        return eventDateText;
    }

    public void setEventDateText(String eventDateText) {
        this.eventDateText = eventDateText;
    }

    public String getTitleOfEvent() {
        return titleOfEvent;
    }

    public void setTitleOfEvent(String titleOfEvent) {
        this.titleOfEvent = titleOfEvent;
    }

    public String getTypeOfActivity() {
        return typeOfActivity;
    }

    public void setTypeOfActivity(String typeOfActivity) {
        this.typeOfActivity = typeOfActivity;
    }

    public String getFacultyCoordinator() {
        return facultyCoordinator;
    }

    public void setFacultyCoordinator(String facultyCoordinator) {
        this.facultyCoordinator = facultyCoordinator;
    }

    public String getFipUploadCoordinator() {
        return fipUploadCoordinator;
    }

    public void setFipUploadCoordinator(String fipUploadCoordinator) {
        this.fipUploadCoordinator = fipUploadCoordinator;
    }

    public String getFinalStatus() {
        return finalStatus;
    }

    public void setFinalStatus(String finalStatus) {
        this.finalStatus = finalStatus;
    }

    public String getUnplannedStatus() {
        return unplannedStatus;
    }

    public void setUnplannedStatus(String unplannedStatus) {
        this.unplannedStatus = unplannedStatus;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(String coordinator) {
        this.coordinator = coordinator;
    }
}
