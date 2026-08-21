package com.example.taskmanager.controller;

import com.example.taskmanager.repository.EventRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventControllerTests {

    @Test
    void uploadEventsAcceptsStringDateCellsInXlsxWorkbooks() throws Exception {
        EventRepository eventRepository = mock(EventRepository.class);
        when(eventRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        EventController controller = new EventController(eventRepository, mock(SimpMessagingTemplate.class));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "calendar.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                workbookWithStringDate());

        ResponseEntity<Map<String, Object>> response = controller.uploadEvents(file, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("imported", 1);
        verify(eventRepository).deleteAll();
    }

    @Test
    void uploadEventsSkipsRowsWithMissingTitleOrDate() throws Exception {
        EventRepository eventRepository = mock(EventRepository.class);
        when(eventRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        EventController controller = new EventController(eventRepository, mock(SimpMessagingTemplate.class));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "calendar.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                workbookWithMissingRequiredValues());

        ResponseEntity<Map<String, Object>> response = controller.uploadEvents(file, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("imported", 0);
        verify(eventRepository).deleteAll();
    }

    @Test
    void uploadEventsAcceptsInstitutionWorkbook() throws Exception {
        EventRepository eventRepository = mock(EventRepository.class);
        when(eventRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        Path workbookPath = Path.of("June 2026 to Dec 2026 (1).xlsx");
        EventController controller = new EventController(eventRepository, mock(SimpMessagingTemplate.class));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                workbookPath.getFileName().toString(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Files.readAllBytes(workbookPath));

        ResponseEntity<Map<String, Object>> response = controller.uploadEvents(file, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("imported", 699);
        verify(eventRepository).deleteAll();
    }

    private byte[] workbookWithStringDate() throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("CSE");
            Row row = sheet.createRow(5);
            row.createCell(2).setCellValue("21/08/2026");
            row.createCell(3).setCellValue("Student Innovation Day");
            row.createCell(4).setCellValue("Workshop");
            row.createCell(5).setCellValue("Dr. Coordinator");

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] workbookWithMissingRequiredValues() throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("CSE");
            Row missingTitle = sheet.createRow(5);
            missingTitle.createCell(2).setCellValue("21/08/2026");
            Row missingDate = sheet.createRow(6);
            missingDate.createCell(3).setCellValue("Student Innovation Day");

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
