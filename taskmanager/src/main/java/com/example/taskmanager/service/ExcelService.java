package com.example.taskmanager.service;

import com.example.taskmanager.model.Event;
import com.example.taskmanager.repository.EventRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class ExcelService {

    private final EventRepository eventRepository;

    public ExcelService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public List<Event> importEvents(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("An .xlsx file is required");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".xlsx")) {
            throw new IllegalArgumentException("Only .xlsx files are supported");
        }

        List<Event> events = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("The workbook has no sheets");
            }
            Sheet sheet = workbook.getSheetAt(0);
            for (int rowIndex = 5; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                String dateText = cellText(row.getCell(2), formatter);
                if (dateText.isBlank()) {
                    continue;
                }
                String title = cellText(row.getCell(3), formatter);
                if (title.isBlank()) {
                    continue;
                }

                Event event = new Event();
                event.setEventDate(parseDate(dateText));
                event.setTitle(title);
                event.setCategory(cellText(row.getCell(4), formatter));
                event.setFacultyCoordinator(cellText(row.getCell(5), formatter));
                events.add(event);
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Could not read the Excel workbook", exception);
        }

        return eventRepository.saveAll(events);
    }

    private String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        if (DateUtil.isCellDateFormatted(cell)) {
            Date date = cell.getDateCellValue();
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                    .format(DateTimeFormatter.ofPattern("d/M/yyyy"));
        }
        return formatter.formatCellValue(cell).trim();
    }

    private LocalDate parseDate(String value) {
        try {
            double serial = Double.parseDouble(value);
            if (DateUtil.isValidExcelDate(serial)) {
                return DateUtil.getJavaDate(serial).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
        } catch (NumberFormatException ignored) {
        }

        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ISO_LOCAL_DATE)) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new IllegalArgumentException("Invalid event date: " + value);
    }
}
