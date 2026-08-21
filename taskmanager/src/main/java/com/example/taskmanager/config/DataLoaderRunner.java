package com.example.taskmanager.config;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;

@Component
public class DataLoaderRunner implements CommandLineRunner {

    @Override
    public void run(String... args) throws Exception {
        String filePath = "June 2026 to Dec 2026 (1).xlsx";
        File file = new File(filePath);

        if (!file.exists()) {
            System.err.println("❌ Excel file not found at project root: " + filePath);
            return;
        }

        System.out.println("🚀 Loading Excel file data into Portal...");

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            DataFormatter formatter = new DataFormatter();
            int totalCount = 0;

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName().trim();
                boolean isPlacement = sheetName.equalsIgnoreCase("Placement");

                int startRow = isPlacement ? 1 : 4;

                for (int r = startRow; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;

                    int titleCol = isPlacement ? 2 : 3;
                    String title = formatter.formatCellValue(row.getCell(titleCol)).trim();

                    if (title.isEmpty() || title.equalsIgnoreCase("Title of the event")) continue;

                    totalCount++;
                }
            }

            System.out.println("✅ SUCCESS: Processed " + totalCount + " events from Excel file!");
        } catch (Exception e) {
            System.err.println("❌ Error loading Excel file: " + e.getMessage());
        }
    }
}
