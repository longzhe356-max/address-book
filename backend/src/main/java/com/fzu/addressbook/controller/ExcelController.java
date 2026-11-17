package com.fzu.addressbook.controller;

import com.fzu.addressbook.domain.Contact;
import com.fzu.addressbook.dto.ImportResult;
import com.fzu.addressbook.exception.BadRequestException;
import com.fzu.addressbook.repository.ContactRepository;
import com.fzu.addressbook.service.ExcelService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 1.3 Excel 导入导出接口。
 */
@RestController
@RequestMapping("/api/contacts")
public class ExcelController {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ExcelService excelService;
    private final ContactRepository contactRepository;

    public ExcelController(ExcelService excelService, ContactRepository contactRepository) {
        this.excelService = excelService;
        this.contactRepository = contactRepository;
    }

    /** 导出全部联系人为 .xlsx，一个联系人一行，每类信息一列。 */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        List<Contact> contacts = contactRepository.findAllByOrderByFavoriteDescUpdatedAtDesc();
        byte[] body = excelService.export(contacts);
        String today = LocalDate.now().format(FILE_DATE);
        String fileName = "通讯录_" + today + ".xlsx";
        String asciiFallback = "address-book-" + today + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(fileName, asciiFallback))
                .contentType(MediaType.parseMediaType(ExcelService.CONTENT_TYPE))
                .body(body);
    }

    /** 下载导入模板。 */
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] body = excelService.template();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment("通讯录导入模板.xlsx", "address-book-template.xlsx"))
                .contentType(MediaType.parseMediaType(ExcelService.CONTENT_TYPE))
                .body(body);
    }

    /** 上传 Excel 导入联系人。表单字段名固定为 {@code file}。 */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importContacts(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("请选择要导入的 Excel 文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new BadRequestException("仅支持 .xlsx 或 .xls 格式");
        }
        try (InputStream inputStream = file.getInputStream()) {
            return excelService.importExcel(inputStream);
        } catch (IOException ex) {
            throw new BadRequestException("读取上传文件失败");
        }
    }

    /**
     * 手写 Content-Disposition：{@code filename*} 在前给出 UTF-8 文件名，
     * 后面再给一个纯 ASCII 的 {@code filename} 作为兜底。
     *
     * <p>不用 {@code ContentDisposition.filename(name, UTF_8)}，因为它会把中文文件名编码成
     * {@code =?UTF-8?Q?...?=} 形式的 encoded-word，浏览器与前端都难以还原。
     */
    private String attachment(String utf8Name, String asciiName) {
        String encoded = URLEncoder.encode(utf8Name, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename*=UTF-8''" + encoded + "; filename=\"" + asciiName + "\"";
    }
}
