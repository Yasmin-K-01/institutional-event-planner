package com.example.taskmanager.service;

import com.example.taskmanager.model.CalendarEvent;
import com.example.taskmanager.repository.CalendarEventRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ExcelImporterService {

    private static final Logger log = LoggerFactory.getLogger(ExcelImporterService.class);

    private static final String PLACEMENT_SHEET_NAME = "Placement";
    private static final int DEPARTMENT_DATA_START_ROW = 4;
    private static final int PLACEMENT_DATA_START_ROW = 1;

    private final CalendarEventRepository calendarEventRepository;

    public ExcelImporterService(CalendarEventRepository calendarEventRepository) {
        this.calendarEventRepository = calendarEventRepository;
    }

    @Transactional
    public List<CalendarEvent> importFromLocalFile(Path workbookPath) throws IOException {
        if (workbookPath == null) {
            throw new IllegalArgumentException("Workbook path must not be null");
        }
        if (!Files.exists(workbookPath)) {
            throw new IOException("Excel workbook not found: " + workbookPath.toAbsolutePath());
        }
        if (!Files.isRegularFile(workbookPath)) {
            throw new IOException("Excel workbook path is not a file: " + workbookPath.toAbsolutePath());
        }

        List<CalendarEvent> events = readWorkbook(workbookPath);
        if (events.isEmpty()) {
            log.warn("No calendar events were found in {}", workbookPath.toAbsolutePath());
            return events;
        }

        List<CalendarEvent> savedEvents = calendarEventRepository.saveAll(events);
        log.info("Imported {} calendar events from {}", savedEvents.size(), workbookPath.toAbsolutePath());
        return savedEvents;
    }

    public List<CalendarEvent> readWorkbook(Path workbookPath) throws IOException {
        DataFormatter formatter = new DataFormatter(Locale.ENGLISH);
        List<CalendarEvent> events = new ArrayList<>();

        try (InputStream inputStream = Files.newInputStream(workbookPath);
                Workbook workbook = WorkbookFactory.create(inputStream)) {
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                if (sheet == null) {
                    continue;
                }

                boolean placementSheet = isPlacementSheet(sheet);
                int dataStartRow = placementSheet ? PLACEMENT_DATA_START_ROW : DEPARTMENT_DATA_START_ROW;

                for (int rowIndex = dataStartRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (isEmptyRow(row, formatter) || isDuplicateHeaderRow(row, formatter, placementSheet)) {
                        continue;
                    }

                    Optional<CalendarEvent> event = placementSheet
                            ? mapPlacementRow(sheet.getSheetName(), row, formatter)
                            : mapDepartmentRow(sheet.getSheetName(), row, formatter);
                    event.ifPresent(events::add);
                }
            }
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Unable to read Excel workbook: " + workbookPath.toAbsolutePath(), exception);
        }

        return events;
    }

    private Optional<CalendarEvent> mapDepartmentRow(String sheetName, Row row, DataFormatter formatter) {
        String serialNumber = cellText(row.getCell(0), formatter);
        String month = cellText(row.getCell(1), formatter);
        String dateText = cellText(row.getCell(2), formatter);
        String title = cellText(row.getCell(3), formatter);
        String typeOfActivity = cellText(row.getCell(4), formatter);
        String facultyCoordinator = cellText(row.getCell(5), formatter);
        String fipUploadCoordinator = cellText(row.getCell(6), formatter);
        String finalStatus = cellText(row.getCell(7), formatter);
        String unplannedStatus = cellText(row.getCell(8), formatter);

        if (allBlank(serialNumber, month, dateText, title, typeOfActivity, facultyCoordinator,
                fipUploadCoordinator, finalStatus, unplannedStatus)) {
            return Optional.empty();
        }

        CalendarEvent event = new CalendarEvent();
        event.setSourceSheet(sheetName);
        event.setDepartment(sheetName);
        event.setSerialNumber(serialNumber);
        event.setMonth(month);
        event.setEventDateText(dateText);
        event.setEventDate(parseDate(row.getCell(2), dateText));
        event.setTitleOfEvent(title);
        event.setTypeOfActivity(typeOfActivity);
        event.setFacultyCoordinator(facultyCoordinator);
        event.setFipUploadCoordinator(fipUploadCoordinator);
        event.setFinalStatus(finalStatus);
        event.setUnplannedStatus(unplannedStatus);
        return Optional.of(event);
    }

    private Optional<CalendarEvent> mapPlacementRow(String sheetName, Row row, DataFormatter formatter) {
        String serialNumber = cellText(row.getCell(0), formatter);
        String dateText = cellText(row.getCell(1), formatter);
        String companyName = cellText(row.getCell(2), formatter);
        String coordinator = cellText(row.getCell(3), formatter);
        String finalStatus = cellText(row.getCell(4), formatter);
        String unplannedStatus = cellText(row.getCell(5), formatter);

        if (allBlank(serialNumber, dateText, companyName, coordinator, finalStatus, unplannedStatus)) {
            return Optional.empty();
        }

        CalendarEvent event = new CalendarEvent();
        event.setSourceSheet(sheetName);
        event.setDepartment(sheetName);
        event.setSerialNumber(serialNumber);
        event.setEventDateText(dateText);
        event.setEventDate(parseDate(row.getCell(1), dateText));
        event.setCompanyName(companyName);
        event.setCoordinator(coordinator);
        event.setTitleOfEvent(companyName);
        event.setFacultyCoordinator(coordinator);
        event.setFinalStatus(finalStatus);
        event.setUnplannedStatus(unplannedStatus);
        return Optional.of(event);
    }

    private boolean isPlacementSheet(Sheet sheet) {
        return PLACEMENT_SHEET_NAME.equalsIgnoreCase(sheet.getSheetName().trim());
    }

    private boolean isDuplicateHeaderRow(Row row, DataFormatter formatter, boolean placementSheet) {
        String firstCell = normalizeHeader(cellText(row.getCell(0), formatter));
        if (placementSheet) {
            String secondCell = normalizeHeader(cellText(row.getCell(1), formatter));
            return "sno".equals(firstCell) && "date".equals(secondCell);
        }

        String fourthCell = normalizeHeader(cellText(row.getCell(3), formatter));
        return ("slno".equals(firstCell) || "sno".equals(firstCell)) && fourthCell.contains("title");
    }

    private boolean isEmptyRow(Row row, DataFormatter formatter) {
        if (row == null) {
            return true;
        }
        for (int cellIndex = 0; cellIndex < Math.max(row.getLastCellNum(), 0); cellIndex++) {
            if (!cellText(row.getCell(cellIndex), formatter).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).trim();
    }

    private LocalDate parseDate(Cell cell, String formattedText) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return DateUtil.getJavaDate(cell.getNumericCellValue())
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
        }

        String value = formattedText == null ? "" : formattedText.trim();
        if (value.isBlank()) {
            return null;
        }

        try {
            double serialDate = Double.parseDouble(value);
            if (DateUtil.isValidExcelDate(serialDate)) {
                return DateUtil.getJavaDate(serialDate)
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
            }
        } catch (NumberFormatException ignored) {
            // Not an Excel serial date; try human-readable date formats below.
        }

        String rangeStart = firstDateInRange(value);
        for (String candidate : List.of(value, rangeStart)) {
            for (DateTimeFormatter formatter : supportedDateFormatters()) {
                try {
                    return LocalDate.parse(candidate, formatter);
                } catch (DateTimeParseException ignored) {
                    // Keep trying supported formats.
                }
            }
        }

        log.warn("Could not parse Excel date value '{}'; storing the original text only.", value);
        return null;
    }

    private String firstDateInRange(String value) {
        String normalized = value.trim().replaceAll("\\s+", " ");
        for (String delimiter : List.of(" to ", " - ")) {
            int delimiterIndex = normalized.toLowerCase(Locale.ENGLISH).indexOf(delimiter);
            if (delimiterIndex > 0) {
                return normalized.substring(0, delimiterIndex).trim();
            }
        }
        return normalized;
    }

    private List<DateTimeFormatter> supportedDateFormatters() {
        return List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("d/M/uuuu"),
                DateTimeFormatter.ofPattern("M/d/uuuu"),
                DateTimeFormatter.ofPattern("d/M/uu"),
                DateTimeFormatter.ofPattern("M/d/uu"),
                DateTimeFormatter.ofPattern("d-M-uuuu"),
                DateTimeFormatter.ofPattern("d.M.uuuu"),
                new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern("d MMM uuuu")
                        .toFormatter(Locale.ENGLISH),
                new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern("d MMMM uuuu")
                        .toFormatter(Locale.ENGLISH),
                new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern("d/M/")
                        .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
                        .toFormatter(Locale.ENGLISH));
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]", "");
    }

    private boolean allBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
