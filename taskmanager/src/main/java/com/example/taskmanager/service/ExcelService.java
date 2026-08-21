package com.example.taskmanager.service;

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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ExcelService {

    private static final int MAX_IMPORTED_TEXT_LENGTH = 2000;

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
                
                event.setTitle(limitImportedText(title));
                event.setCategory(limitImportedText(cellText(row.getCell(4), formatter)));
                event.setFacultyCoordinator(limitImportedText(cellText(row.getCell(5), formatter)));
                
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

        // Cell Type-a safe-a handle panni numeric/string crash-a fix panra logic
        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return new java.text.SimpleDateFormat("dd/MM/yyyy").format(cell.getDateCellValue());
            } else {
                return String.valueOf((long) cell.getNumericCellValue());
            }
        } else if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        } else if (cell.getCellType() == CellType.FORMULA) {
            try {
                return cell.getStringCellValue().trim();
            } catch (Exception e) {
                try {
                    return String.valueOf((long) cell.getNumericCellValue());
                } catch (Exception ex) {
                    return formatter.formatCellValue(cell).trim();
                }
            }
        }

        return formatter.formatCellValue(cell).trim();
    }

    private String limitImportedText(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_IMPORTED_TEXT_LENGTH ? text.substring(0, MAX_IMPORTED_TEXT_LENGTH) : text;
    }

    private LocalDate parseDate(String value) {
        LocalDate parsedDate = tryParseSingleDate(value);
        if (parsedDate != null) {
            return parsedDate;
        }

        String rangeStart = firstDateInRange(value);
        if (!rangeStart.equals(value.trim())) {
            parsedDate = tryParseSingleDate(rangeStart);
            if (parsedDate != null) {
                return parsedDate;
            }
        }

        throw new IllegalArgumentException("Invalid event date: " + value);
    }

    private LocalDate tryParseSingleDate(String value) {
        String dateValue = value == null ? "" : value.trim();
        if (dateValue.isBlank()) {
            return null;
        }
        try {
            double serial = Double.parseDouble(dateValue);
            if (DateUtil.isValidExcelDate(serial)) {
                return DateUtil.getJavaDate(serial).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
        } catch (NumberFormatException ignored) {
        }

        for (DateTimeFormatter formatter : List.of(
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
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                DateTimeFormatter.ISO_LOCAL_DATE,
                new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MMM-yyyy").toFormatter(Locale.ENGLISH),
                new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d-MMM-yyyy").toFormatter(Locale.ENGLISH),
                new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MMM-yy").toFormatter(Locale.ENGLISH),
                new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d-MMM-yy").toFormatter(Locale.ENGLISH),
                new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd MMM yyyy").toFormatter(Locale.ENGLISH),
                new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMM yyyy").toFormatter(Locale.ENGLISH))) {
            try {
                return LocalDate.parse(dateValue, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private String firstDateInRange(String value) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (normalized.toLowerCase(Locale.ENGLISH).contains(" to ")) {
            return normalized.split("(?i) to ")[0].trim();
        }
        return normalized;
    }
}
