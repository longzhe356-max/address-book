package com.fzu.addressbook.service;

import com.fzu.addressbook.domain.Contact;
import com.fzu.addressbook.domain.ContactMethod;
import com.fzu.addressbook.domain.MethodType;
import com.fzu.addressbook.dto.ImportResult;
import com.fzu.addressbook.exception.BadRequestException;
import com.fzu.addressbook.repository.ContactRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 1.3 Excel 导入导出的行为测试。
 *
 * <p>重点验证：一人一行、一类一列；导入时按「同名 + 同一联系方式」合并。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:excel-service-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class ExcelServiceTest {

    /** 与前端 frontend/js/api.js 的 EXCEL_HEADERS 完全一致。 */
    private static final List<String> FRONTEND_HEADERS =
            List.of("姓名", "分组", "电话", "邮箱", "社交账号", "地址", "其他", "收藏", "备注");

    @Autowired
    private ExcelService excelService;

    @Autowired
    private ContactRepository contactRepository;

    @BeforeEach
    void cleanUp() {
        contactRepository.deleteAll();
    }

    @Test
    @DisplayName("导出的表头与前端约定完全一致")
    void exportHeaderMatchesFrontendContract() throws IOException {
        byte[] bytes = excelService.export(List.of());

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet(ExcelService.SHEET_NAME);
            Row header = sheet.getRow(0);

            assertThat((int) header.getLastCellNum()).isEqualTo(FRONTEND_HEADERS.size());
            for (int i = 0; i < FRONTEND_HEADERS.size(); i++) {
                assertThat(header.getCell(i).getStringCellValue()).isEqualTo(FRONTEND_HEADERS.get(i));
            }
        }
    }

    @Test
    @DisplayName("导出后重新导入，字段与联系方式保持一致")
    void exportThenImportRoundTrip() throws IOException {
        Contact contact = new Contact("张三");
        contact.setGroup("同事");
        contact.setNote("示例备注");
        contact.setFavorite(true);
        contact.addMethod(new ContactMethod(MethodType.PHONE, null, "13800000000"));
        contact.addMethod(new ContactMethod(MethodType.PHONE, null, "0591-22860000"));
        contact.addMethod(new ContactMethod(MethodType.EMAIL, null, "zhangsan@example.com"));
        contact.addMethod(new ContactMethod(MethodType.SOCIAL, null, "微信 zhangsan_wx"));
        contactRepository.save(contact);

        byte[] exported = excelService.export(contactRepository.findAllByOrderByFavoriteDescUpdatedAtDesc());

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(exported))) {
            Sheet sheet = workbook.getSheet(ExcelService.SHEET_NAME);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            Row row = sheet.getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("张三");
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("同事");
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("13800000000;0591-22860000");
            assertThat(row.getCell(3).getStringCellValue()).isEqualTo("zhangsan@example.com");
            assertThat(row.getCell(4).getStringCellValue()).isEqualTo("微信 zhangsan_wx");
            assertThat(row.getCell(5).getStringCellValue()).isEmpty();
            assertThat(row.getCell(6).getStringCellValue()).isEmpty();
            assertThat(row.getCell(7).getStringCellValue()).isEqualTo("是");
            assertThat(row.getCell(8).getStringCellValue()).isEqualTo("示例备注");
        }

        contactRepository.deleteAll();
        ImportResult result = excelService.importExcel(new ByteArrayInputStream(exported));

        assertThat(result.getTotalRows()).isEqualTo(1);
        assertThat(result.getInserted()).isEqualTo(1);
        assertThat(result.getUpdated()).isZero();
        assertThat(result.getFailed()).isZero();

        Contact restored = contactRepository.findAllByOrderByFavoriteDescUpdatedAtDesc().get(0);
        assertThat(restored.getName()).isEqualTo("张三");
        assertThat(restored.getGroup()).isEqualTo("同事");
        assertThat(restored.getNote()).isEqualTo("示例备注");
        assertThat(restored.isFavorite()).isTrue();
        assertThat(restored.getMethods())
                .extracting(m -> m.getType() + ":" + m.getValue())
                .containsExactlyInAnyOrder(
                        "PHONE:13800000000",
                        "PHONE:0591-22860000",
                        "EMAIL:zhangsan@example.com",
                        "SOCIAL:微信 zhangsan_wx");
    }

    @Test
    @DisplayName("导入时同名且联系方式重合的联系人会被合并，而不是新增")
    void importMergesExistingContact() throws IOException {
        Contact existing = new Contact("张三");
        existing.setGroup("同事");
        existing.addMethod(new ContactMethod(MethodType.PHONE, null, "13800000000"));
        contactRepository.save(existing);

        byte[] bytes = workbook(wb -> {
            Sheet sheet = wb.createSheet(ExcelService.SHEET_NAME);
            header(sheet);
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("张三");
            row.createCell(1).setCellValue("客户");
            row.createCell(2).setCellValue("13800000000");
            row.createCell(3).setCellValue("zhangsan@example.com");
        });

        ImportResult result = excelService.importExcel(new ByteArrayInputStream(bytes));

        assertThat(result.getInserted()).isZero();
        assertThat(result.getUpdated()).isEqualTo(1);

        Contact merged = contactRepository.findAllByOrderByFavoriteDescUpdatedAtDesc().get(0);
        assertThat(merged.getGroup()).isEqualTo("客户");
        assertThat(merged.getMethods())
                .extracting(m -> m.getType() + ":" + m.getValue())
                .containsExactlyInAnyOrder("PHONE:13800000000", "EMAIL:zhangsan@example.com");
    }

    @Test
    @DisplayName("数字单元格里的手机号不会变成科学计数法")
    void importNumericPhoneKeepsDigits() throws IOException {
        byte[] bytes = workbook(wb -> {
            Sheet sheet = wb.createSheet(ExcelService.SHEET_NAME);
            header(sheet);
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("李四");
            row.createCell(2).setCellValue(13800000000D);
        });

        ImportResult result = excelService.importExcel(new ByteArrayInputStream(bytes));

        assertThat(result.getInserted()).isEqualTo(1);
        Contact imported = contactRepository.findAllByOrderByFavoriteDescUpdatedAtDesc().get(0);
        assertThat(imported.getMethods()).singleElement()
                .satisfies(m -> assertThat(m.getValue()).isEqualTo("13800000000"));
    }

    @Test
    @DisplayName("缺少联系方式或姓名的行被跳过并记录原因")
    void importSkipsInvalidRows() throws IOException {
        byte[] bytes = workbook(wb -> {
            Sheet sheet = wb.createSheet(ExcelService.SHEET_NAME);
            header(sheet);

            Row valid = sheet.createRow(1);
            valid.createCell(0).setCellValue("王五");
            valid.createCell(1).setCellValue("客户");
            valid.createCell(2).setCellValue("13700000003");

            Row noMethods = sheet.createRow(2);
            noMethods.createCell(0).setCellValue("没有联系方式的人");

            sheet.createRow(3).createCell(0).setCellValue("");
        });

        ImportResult result = excelService.importExcel(new ByteArrayInputStream(bytes));

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getInserted()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getErrors()).singleElement()
                .satisfies(error -> assertThat(error).contains("没有联系方式"));
    }

    @Test
    @DisplayName("缺少姓名列时拒绝导入并提示参考模板")
    void importRejectsMissingNameColumn() throws IOException {
        byte[] bytes = workbook(wb -> {
            Sheet sheet = wb.createSheet(ExcelService.SHEET_NAME);
            sheet.createRow(0).createCell(0).setCellValue("电话");
            sheet.createRow(1).createCell(0).setCellValue("13800000000");
        });

        assertThatThrownBy(() -> excelService.importExcel(new ByteArrayInputStream(bytes)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("缺少「姓名」列");
    }

    @Test
    @DisplayName("模板包含表头与填写说明页")
    void templateContainsHeaderAndGuide() throws IOException {
        byte[] bytes = excelService.template();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheet(ExcelService.SHEET_NAME)).isNotNull();
            assertThat(workbook.getSheet("填写说明")).isNotNull();
            assertThat(workbook.getSheet(ExcelService.SHEET_NAME).getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("姓名");
        }
    }

    private void header(Sheet sheet) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < FRONTEND_HEADERS.size(); i++) {
            header.createCell(i).setCellValue(FRONTEND_HEADERS.get(i));
        }
    }

    private interface WorkbookBuilder {
        void build(XSSFWorkbook workbook) throws IOException;
    }

    private byte[] workbook(WorkbookBuilder builder) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            builder.build(workbook);
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
