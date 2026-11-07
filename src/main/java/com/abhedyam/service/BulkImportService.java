package com.abhedyam.service;

import com.abhedyam.constants.ErrorCodes;
import com.abhedyam.dto.BulkImportResult;
import com.abhedyam.dto.CustomerCreateRequest;
import com.abhedyam.dto.PaymentCreateRequest;
import com.abhedyam.dto.SaleCreateRequest;
import com.abhedyam.dto.SaleDetailResponse;
import com.abhedyam.dto.SaleItemRequest;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.model.Customer;
import com.abhedyam.model.enums.PaymentMedium;
import com.abhedyam.model.enums.PaymentStatus;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.service.interfaces.IBulkImportService;
import com.abhedyam.util.PhoneUtil;
import com.abhedyam.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkImportService implements IBulkImportService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String SHEET_NAME = "Import Data";
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d/M/uuuu");

    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final SaleService saleService;
    private final PaymentService paymentService;

    @Override
    public BulkImportResult importData(UUID ownerId, MultipartFile file) {
        return importData(ownerId, file, false);
    }

    @Override
    public BulkImportResult importData(UUID ownerId, MultipartFile file, boolean dryRun) {
        validateOwnerAccess(ownerId);
        validateFile(file);

        int customersCreated = 0;
        int salesImported = 0;
        int paymentsRecorded = 0;
        int rowsSkipped = 0;
        List<String> errors = new ArrayList<>();
        Set<String> simulatedCreatedKeys = new HashSet<>();

        try (InputStream inputStream = file.getInputStream(); XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null && workbook.getNumberOfSheets() > 0) {
                sheet = workbook.getSheetAt(0);
            }

            if (sheet == null) {
                throw new BusinessException(ErrorCodes.VALIDATION_ERROR, "The uploaded file does not contain any sheets");
            }

            DataFormatter formatter = new DataFormatter();

            for (int rowIndex = 2; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isRowEmpty(row, formatter)) {
                    continue;
                }

                int displayRow = rowIndex + 1;
                try {
                    ImportRow importRow = parseRow(row, formatter);
                    CustomerResolution customerResolution = resolveCustomer(ownerId, importRow, dryRun, simulatedCreatedKeys);

                    if (customerResolution.created()) {
                        customersCreated++;
                    }

                    if (dryRun) {
                        salesImported++;
                        if (importRow.amountPaid().compareTo(BigDecimal.ZERO) > 0) {
                            paymentsRecorded++;
                        }
                    } else {
                        SaleCreateRequest saleRequest = new SaleCreateRequest();
                        saleRequest.setCustomerId(customerResolution.customer().getId());
                        saleRequest.setDueDate(importRow.paymentDeadline());

                        SaleItemRequest saleItemRequest = new SaleItemRequest();
                        saleItemRequest.setProductName(importRow.productName());
                        saleItemRequest.setPrice(importRow.productPrice());
                        saleRequest.setItems(List.of(saleItemRequest));

                        SaleDetailResponse sale = saleService.createSale(saleRequest);
                        if (sale.getItems() == null || sale.getItems().isEmpty()) {
                            throw new BusinessException(ErrorCodes.INVALID,
                                    "Sale was created without any sale items");
                        }
                        salesImported++;

                        if (importRow.amountPaid().compareTo(BigDecimal.ZERO) > 0) {
                            PaymentCreateRequest paymentRequest = new PaymentCreateRequest();
                            paymentRequest.setSaleItemId(sale.getItems().get(0).getId());
                            paymentRequest.setAmount(importRow.amountPaid());
                            paymentRequest.setMedium(PaymentMedium.CASH);
                            paymentRequest.setStatus(PaymentStatus.SUCCESS);

                            paymentService.createImportedPayment(ownerId, paymentRequest, importRow.amountPaidDate());
                            paymentsRecorded++;
                        }
                    }
                } catch (Exception e) {
                    rowsSkipped++;
                    String message = extractMessage(e);
                    errors.add("Row " + displayRow + ": " + message);
                    log.warn("Bulk import skipped row {} for owner {}: {}", displayRow, ownerId, message);
                }
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCodes.VALIDATION_ERROR, "Unable to read the uploaded Excel file");
        }

        return BulkImportResult.builder()
                .customersCreated(customersCreated)
                .salesImported(salesImported)
                .paymentsRecorded(paymentsRecorded)
                .rowsSkipped(rowsSkipped)
                .errors(errors)
                .build();
    }

    private void validateOwnerAccess(UUID ownerId) {
        UUID currentUserId = SecurityUtil.getCurrentUserId();
        if (ownerId == null || !ownerId.equals(currentUserId)) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You can only import data for your own account");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCodes.VALIDATION_ERROR, "Please upload a non-empty .xlsx file");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(ErrorCodes.VALIDATION_ERROR, "File size must be 10MB or less");
        }

        String originalFilename = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase();
        String contentType = Optional.ofNullable(file.getContentType()).orElse("");
        if (!originalFilename.endsWith(".xlsx") && !XLSX_CONTENT_TYPE.equalsIgnoreCase(contentType)) {
            throw new BusinessException(ErrorCodes.VALIDATION_ERROR, "Only .xlsx files are supported");
        }
    }

    private boolean isRowEmpty(Row row, DataFormatter formatter) {
        if (row == null) {
            return true;
        }

        for (int cellIndex = 0; cellIndex < 8; cellIndex++) {
            Cell cell = row.getCell(cellIndex);
            if (cell != null && !formatter.formatCellValue(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private ImportRow parseRow(Row row, DataFormatter formatter) {
        String customerName = getRequiredString(row, 0, "Customer Name", formatter);
        String phone = cleanPhone(row.getCell(1), formatter);
        String location = getOptionalString(row, 2, formatter);
        String productName = getRequiredString(row, 3, "Product Name", formatter);
        BigDecimal productPrice = getRequiredAmount(row, 4, "Product Price", formatter);
        BigDecimal amountPaid = getRequiredAmount(row, 5, "Amount Paid", formatter);
        Instant amountPaidDate = parseOptionalDate(row.getCell(6), "Amount Paid Date", formatter);
        Instant paymentDeadline = parseOptionalDate(row.getCell(7), "Payment Deadline", formatter);

        if (amountPaid.compareTo(productPrice) > 0) {
            throw new BusinessException(ErrorCodes.VALIDATION_ERROR, "Amount Paid cannot exceed Product Price");
        }

        return new ImportRow(customerName, phone, location, productName, productPrice, amountPaid, amountPaidDate,
                paymentDeadline);
    }

    private CustomerResolution resolveCustomer(UUID ownerId, ImportRow row, boolean dryRun, Set<String> simulatedCreatedKeys) {
        if (row.phone() != null && !row.phone().isBlank()) {
            String normalizedPhone = PhoneUtil.normalizePhone(row.phone());
            Optional<Customer> existingCustomer = customerRepository.findByPhoneNormalized(normalizedPhone);
            if (existingCustomer.isPresent()) {
                Customer customer = existingCustomer.get();
                if (!ownerId.equals(customer.getOwnerId())) {
                    throw new BusinessException(ErrorCodes.CUSTOMER_EXISTS,
                            "Phone number already belongs to another owner");
                }
                return new CustomerResolution(customer, false);
            }
            if (dryRun && simulatedCreatedKeys.contains("phone:" + normalizedPhone)) {
                Customer placeholder = new Customer();
                placeholder.setId(UUID.randomUUID());
                return new CustomerResolution(placeholder, false);
            }
        } else {
            Optional<Customer> existingCustomer = customerRepository.findFirstByOwnerIdAndNameIgnoreCase(ownerId,
                    row.customerName());
            if (existingCustomer.isPresent()) {
                return new CustomerResolution(existingCustomer.get(), false);
            }
            if (dryRun && simulatedCreatedKeys.contains("name:" + row.customerName().trim().toLowerCase())) {
                Customer placeholder = new Customer();
                placeholder.setId(UUID.randomUUID());
                return new CustomerResolution(placeholder, false);
            }
        }

        if (dryRun) {
            if (row.phone() != null && !row.phone().isBlank()) {
                simulatedCreatedKeys.add("phone:" + PhoneUtil.normalizePhone(row.phone()));
            } else {
                simulatedCreatedKeys.add("name:" + row.customerName().trim().toLowerCase());
            }
            Customer placeholder = new Customer();
            placeholder.setId(UUID.randomUUID());
            return new CustomerResolution(placeholder, true);
        }

        CustomerCreateRequest request = new CustomerCreateRequest();
        request.setName(row.customerName());
        request.setPhone(row.phone());
        request.setVillage(row.location());
        return new CustomerResolution(customerService.create(request), true);
    }

    private String getRequiredString(Row row, int cellIndex, String fieldName, DataFormatter formatter) {
        String value = getOptionalString(row, cellIndex, formatter);
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCodes.VALIDATION_ERROR, fieldName + " is required");
        }
        return value;
    }

    private String getOptionalString(Row row, int cellIndex, DataFormatter formatter) {
        Cell cell = row.getCell(cellIndex);
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell).trim();
        return value.isEmpty() ? null : value;
    }

    private BigDecimal getRequiredAmount(Row row, int cellIndex, String fieldName, DataFormatter formatter) {
        Cell cell = row.getCell(cellIndex);
        if (cell == null) {
            throw new BusinessException(ErrorCodes.VALIDATION_ERROR, fieldName + " is required");
        }

        BigDecimal amount = parseAmount(cell, formatter);
        if (amount == null) {
            throw new BusinessException(ErrorCodes.VALIDATION_ERROR, fieldName + " is required or invalid");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCodes.VALIDATION_ERROR, fieldName + " cannot be negative");
        }
        return amount;
    }

    private Instant parseOptionalDate(Cell cell, String fieldName, DataFormatter formatter) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime dateTime = cell.getLocalDateTimeCellValue();
            return dateTime.toLocalDate().atStartOfDay(IST_ZONE).toInstant();
        }

        String raw = formatter.formatCellValue(cell).trim();
        if (raw.isEmpty()) {
            return null;
        }

        try {
            LocalDate localDate = LocalDate.parse(raw, DATE_FORMATTER);
            return localDate.atStartOfDay(IST_ZONE).toInstant();
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCodes.VALIDATION_ERROR, fieldName + " must be in DD/MM/YYYY format");
        }
    }

    private BigDecimal parseAmount(Cell cell, DataFormatter formatter) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }

        if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
        }

        String raw = formatter.formatCellValue(cell).trim();
        if (raw.isEmpty()) {
            return null;
        }

        String sanitized = raw
                .replace(",", "")
                .replaceAll("[Rr][Ss]\\.?\\s*", "")
                .replace("\u20B9", "")
                .replaceAll("\\s+", "");

        sanitized = sanitized.replaceAll("[^0-9.\\-]", "");
        if (sanitized.isEmpty() || ".".equals(sanitized) || "-".equals(sanitized)) {
            return null;
        }

        try {
            return new BigDecimal(sanitized).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCodes.VALIDATION_ERROR, "Amount must be a valid number");
        }
    }

    private String cleanPhone(Cell cell, DataFormatter formatter) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }

        String raw;
        if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            raw = BigDecimal.valueOf(cell.getNumericCellValue())
                    .setScale(0, RoundingMode.DOWN)
                    .toPlainString();
        } else {
            raw = formatter.formatCellValue(cell).trim();
        }

        if (raw == null || raw.isBlank()) {
            return null;
        }

        String sanitized = raw.replaceAll("[^0-9+]", "");
        if (sanitized.isEmpty()) {
            return null;
        }

        if (sanitized.startsWith("0091") && sanitized.length() > 4) {
            sanitized = "+" + sanitized.substring(2);
        }

        if (sanitized.startsWith("91") && sanitized.length() == 12) {
            sanitized = "+" + sanitized;
        }

        if (!sanitized.startsWith("+")) {
            sanitized = sanitized.replaceFirst("^0+(?!$)", "");
        }

        if (!PhoneUtil.isValidPhone(sanitized)) {
            String digitsOnly = sanitized.replaceAll("[^0-9]", "");
            if (digitsOnly.length() == 10) {
                sanitized = "+91" + digitsOnly;
            } else if (digitsOnly.length() == 12 && digitsOnly.startsWith("91")) {
                sanitized = "+" + digitsOnly;
            } else {
                return null;
            }
        }

        return PhoneUtil.normalizePhone(sanitized);
    }

    private String extractMessage(Exception exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getMessage();
        }
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            return exception.getMessage();
        }
        return "Unable to import this row";
    }

    private record ImportRow(
            String customerName,
            String phone,
            String location,
            String productName,
            BigDecimal productPrice,
            BigDecimal amountPaid,
            Instant amountPaidDate,
            Instant paymentDeadline) {
    }

    private record CustomerResolution(Customer customer, boolean created) {
    }
}
