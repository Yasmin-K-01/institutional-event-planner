package com.example.taskmanager.controller;

import com.example.taskmanager.model.Event;
import com.example.taskmanager.repository.EventRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
@CrossOrigin(origins = "*")
public class EventController {

    private static final int MAX_IMPORTED_TEXT_LENGTH = 2000;

    private final EventRepository eventRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public EventController(EventRepository eventRepository, SimpMessagingTemplate messagingTemplate) {
        this.eventRepository = eventRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping
    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadEvents(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "excel", required = false) MultipartFile excel) {
            
        MultipartFile uploadFile = (file != null) ? file : excel;

        if (uploadFile == null || uploadFile.isEmpty() || uploadFile.getOriginalFilename() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid .xlsx or .csv file");
        }

        String filename = uploadFile.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".csv")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .xlsx and .csv files are supported");
        }

        try {
            Map<String, Object> result = filename.endsWith(".xlsx") ? importWorkbook(uploadFile) : importCsv(uploadFile);
            notifyCalendarRefresh();
            return ResponseEntity.ok(result);
        } catch (Exception exception) {
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("error", "File parsing failed: " + exception.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMap);
        }
    }

    @PostMapping("/live-sync")
    public ResponseEntity<String> receiveLiveSync(@RequestBody List<LiveSyncEventRequest> sheetEvents) {
        if (sheetEvents == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body must be a JSON array");
        }

        try {
            List<Event> newEvents = new ArrayList<>();
            for (int index = 0; index < sheetEvents.size(); index++) {
                LiveSyncEventRequest row = sheetEvents.get(index);
                if (row == null || row.isBlank()) {
                    continue;
                }
                newEvents.add(row.toEvent(index + 1));
            }

            eventRepository.deleteAll();
            eventRepository.saveAll(newEvents);
            notifyCalendarRefresh();

            return ResponseEntity.ok("Live calendar updated successfully. Total events: " + newEvents.size());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error syncing live events: " + exception.getMessage());
        }
    }

    private Map<String, Object> importWorkbook(MultipartFile file) throws IOException {
        int imported = 0;
        List<String> errors = new ArrayList<>();
        List<Event> eventsToSave = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                boolean placementSheet = "Placement".equalsIgnoreCase(sheet.getSheetName().trim());
                int startRow = placementSheet ? 1 : 5;
                int dateColumn = placementSheet ? 1 : 2;
                int titleColumn = placementSheet ? 2 : 3;
                int categoryColumn = placementSheet ? -1 : 4;
                int coordinatorColumn = placementSheet ? 3 : 5;

                for (int rowIndex = startRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null) {
                        continue;
                    }

                    String dateValue = cellValue(row.getCell(dateColumn), formatter);
                    String title = cellValue(row.getCell(titleColumn), formatter);
                    
                    if (dateValue.isBlank() || title.isBlank()) {
                        continue;
                    }

                    try {
                        Event event = new Event();
                        event.setEventDate(parseDate(dateValue));
                        event.setTitle(limitImportedText(title.trim()));
                        event.setDepartment(limitImportedText(sheet.getSheetName()));
                        event.setCategory(limitImportedText(placementSheet ? "Placement" : cellValue(row.getCell(categoryColumn), formatter)));
                        event.setFacultyCoordinator(limitImportedText(cellValue(row.getCell(coordinatorColumn), formatter)));
                        eventsToSave.add(event);
                        imported++;
                    } catch (Exception exception) {
                        errors.add("Sheet '" + sheet.getSheetName() + "' Row " + (rowIndex + 1) + ": " + exception.getMessage());
                    }
                }
            }
        }

        if (!eventsToSave.isEmpty()) {
            eventRepository.deleteAll();
            eventRepository.saveAll(eventsToSave);
        }

        return importResult(imported, errors);
    }

    private Map<String, Object> importCsv(MultipartFile file) throws IOException {
        List<String[]> rows;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            rows = reader.lines().filter(line -> !line.isBlank()).map(this::parseCsvLine).toList();
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("The CSV file has no rows");
        }

        Map<String, Integer> columns = headerIndexes(rows.remove(0));
        int imported = 0;
        List<String> errors = new ArrayList<>();
        List<Event> eventsToSave = new ArrayList<>();

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            String[] row = rows.get(rowIndex);
            String title = value(row, columns, "title");
            String date = value(row, columns, "date");

            if (title.isBlank() || date.isBlank()) {
                continue;
            }

            try {
                Event event = new Event();
                event.setTitle(limitImportedText(title.trim()));
                event.setEventDate(parseDate(date));
                event.setDepartment(limitImportedText(firstPresent(row, columns, "department", "dept")));
                event.setCategory(limitImportedText(firstPresent(row, columns, "category", "type", "department")));
                event.setFacultyCoordinator(limitImportedText(firstPresent(row, columns, "faculty coordinator", "coordinator")));
                eventsToSave.add(event);
                imported++;
            } catch (Exception exception) {
                errors.add("Row " + (rowIndex + 2) + ": " + exception.getMessage());
            }
        }

        if (!eventsToSave.isEmpty()) {
            eventRepository.deleteAll();
            eventRepository.saveAll(eventsToSave);
        }

        return importResult(imported, errors);
    }

    private void notifyCalendarRefresh() {
        if (messagingTemplate != null) {
            messagingTemplate.convertAndSend("/topic/calendar-events", "REFRESH");
        }
    }

    private Map<String, Object> importResult(int imported, List<String> errors) {
        Map<String, Object> response = new HashMap<>();
        response.put("imported", imported);
        response.put("errors", errors);
        return response;
    }

    private String cellValue(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            Date date = cell.getDateCellValue();
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        return formatter.formatCellValue(cell).trim();
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

    private String firstPresent(String[] row, Map<String, Integer> columns, String... columnNames) {
        for (String columnName : columnNames) {
            String value = value(row, columns, columnName);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String limitImportedText(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > MAX_IMPORTED_TEXT_LENGTH ? value.substring(0, MAX_IMPORTED_TEXT_LENGTH) : value;
    }

    private String requiredValue(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }

    private LocalDate parseDate(String value) {
        String date = firstDateInRange(requiredValue(value, "date"));
        try {
            double excelSerial = Double.parseDouble(date);
            if (DateUtil.isValidExcelDate(excelSerial)) {
                return DateUtil.getJavaDate(excelSerial).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
        } catch (NumberFormatException ignored) {
        }

        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("M/d/yyyy"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("d/M/yy"),
                DateTimeFormatter.ofPattern("dd/MM/yy"),
                DateTimeFormatter.ofPattern("M/d/yy"),
                DateTimeFormatter.ofPattern("MM/dd/yy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("d-M-yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yy"),
                DateTimeFormatter.ofPattern("d-M-yy"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                DateTimeFormatter.ofPattern("dd.MM.yy"),
                new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MMM-yyyy").toFormatter(Locale.ENGLISH),
                new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d-MMM-yyyy").toFormatter(Locale.ENGLISH),
                new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MMM-yy").toFormatter(Locale.ENGLISH),
                new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d-MMM-yy").toFormatter(Locale.ENGLISH)
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(date, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        throw new IllegalArgumentException("Invalid date format '" + date + "'");
    }

    private String firstDateInRange(String value) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        for (String delimiter : List.of(" to ", " - ", " TO ", " - ")) {
            int delimiterIndex = normalized.indexOf(delimiter);
            if (delimiterIndex > 0) {
                return normalized.substring(0, delimiterIndex).trim();
            }
        }
        return normalized;
    }

    public record LiveSyncEventRequest(
            String department,
            String date,
            String title,
            String type,
            String coordinator) {

        private boolean isBlank() {
            return isBlank(department) && isBlank(date) && isBlank(title) && isBlank(type) && isBlank(coordinator);
        }

        private Event toEvent(int rowNumber) {
            Event event = new Event();
            event.setDepartment(limitImportedText(trimToEmpty(department)));
            event.setEventDate(parseLiveSyncDate(date, rowNumber));
            event.setTitle(limitImportedText(requiredLiveSyncValue(title, "title", rowNumber)));
            event.setCategory(limitImportedText(trimToEmpty(type)));
            event.setFacultyCoordinator(limitImportedText(trimToEmpty(coordinator)));
            return event;
        }

        private static LocalDate parseLiveSyncDate(String value, int rowNumber) {
            String dateValue = firstLiveSyncDateInRange(requiredLiveSyncValue(value, "date", rowNumber));
            
            List<DateTimeFormatter> formatters = List.of(
                    DateTimeFormatter.ISO_LOCAL_DATE,
                    DateTimeFormatter.ofPattern("d/M/yyyy"),
                    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                    DateTimeFormatter.ofPattern("M/d/yyyy"),
                    DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                    DateTimeFormatter.ofPattern("d/M/yy"),
                    DateTimeFormatter.ofPattern("M/d/yy"),
                    DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                    DateTimeFormatter.ofPattern("d-M-yyyy"),
                    DateTimeFormatter.ofPattern("d-M-yy"),
                    DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                    DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                    new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MMM-yyyy").toFormatter(Locale.ENGLISH)
            );

            for (DateTimeFormatter formatter : formatters) {
                try {
                    return LocalDate.parse(dateValue, formatter);
                } catch (DateTimeParseException ignored) {
                }
            }
            throw new IllegalArgumentException("row " + rowNumber + ": date must use yyyy-MM-dd, dd/MM/yyyy, or dd-MM-yyyy");
        }

        private static String firstLiveSyncDateInRange(String value) {
            String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
            for (String delimiter : List.of(" to ", " - ", " TO ")) {
                int delimiterIndex = normalized.indexOf(delimiter);
                if (delimiterIndex > 0) {
                    return normalized.substring(0, delimiterIndex).trim();
                }
            }
            return normalized;
        }

        private static String requiredLiveSyncValue(String value, String label, int rowNumber) {
            String trimmed = trimToEmpty(value);
            if (trimmed.isBlank()) {
                throw new IllegalArgumentException("row " + rowNumber + ": " + label + " is required");
            }
            return trimmed;
        }

        private static String trimToEmpty(String value) {
            return value == null ? "" : value.trim();
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }
}