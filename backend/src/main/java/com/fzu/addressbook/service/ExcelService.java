package com.fzu.addressbook.service;

import com.fzu.addressbook.domain.Contact;
import com.fzu.addressbook.domain.ContactMethod;
import com.fzu.addressbook.domain.MethodType;
import com.fzu.addressbook.dto.ImportResult;
import com.fzu.addressbook.exception.BadRequestException;
import com.fzu.addressbook.repository.ContactRepository;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 1.3 Excel 导入 / 导出。
 *
 * <p>列结构与前端（frontend/js/api.js 的 {@code EXCEL_HEADERS}）完全一致：
 * 姓名、分组、电话、邮箱、社交账号、地址、其他、收藏、备注。
 * <b>一个联系人一行，每类信息一列</b>；同一列内多条同类型联系方式用半角分号 {@code ;} 分隔。
 */
@Service
public class ExcelService {

    public static final String SHEET_NAME = "通讯录";
    public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String EXPORT_FILE_NAME = "address-book.xlsx";
    public static final String TEMPLATE_FILE_NAME = "address-book-template.xlsx";

    private static final String COL_NAME = "姓名";
    private static final String COL_GROUP = "分组";
    private static final String COL_FAVORITE = "收藏";
    private static final String COL_NOTE = "备注";
    private static final String MULTI_VALUE_SEPARATOR = ";";
    private static final String MULTI_VALUE_SPLIT_REGEX = "[;；\\n\\r]+";

    private static final String FIELD_NAME = "name";
    private static final String FIELD_GROUP = "group";
    private static final String FIELD_FAVORITE = "favorite";
    private static final String FIELD_NOTE = "note";

    /** 表头别名：与前端 rowsToContacts() 的识别规则保持一致。 */
    private static final Map<String, List<String>> HEADER_ALIASES = new LinkedHashMap<>();

    static {
        HEADER_ALIASES.put(FIELD_NAME, List.of("姓名", "名字", "联系人", "name", "fullname"));
        HEADER_ALIASES.put(FIELD_GROUP, List.of("分组", "分类", "组别", "group", "category"));
        HEADER_ALIASES.put("phone", List.of("电话", "手机号", "手机", "电话号码", "phone", "mobile", "tel"));
        HEADER_ALIASES.put("email", List.of("邮箱", "电子邮箱", "邮件", "email", "mail"));
        HEADER_ALIASES.put("social", List.of("社交账号", "社交", "微信号", "微信", "qq", "social", "wechat"));
        HEADER_ALIASES.put("address", List.of("地址", "住址", "address", "addr"));
        HEADER_ALIASES.put("other", List.of("其他", "其它", "other"));
        HEADER_ALIASES.put(FIELD_FAVORITE, List.of("收藏", "书签", "favorite", "bookmark", "star"));
        HEADER_ALIASES.put(FIELD_NOTE, List.of("备注", "说明", "note", "remark", "comment"));
    }

    private final List<String> headers;
    private final ContactRepository contactRepository;
    private final int maxRows;
    private final int maxMethodsPerType;
    private final DataFormatter formatter = new DataFormatter(Locale.CHINA);

    public ExcelService(ContactRepository contactRepository,
                        @Value("${app.excel.max-rows:5000}") int maxRows,
                        @Value("${app.excel.max-methods-per-type:20}") int maxMethodsPerType) {
        this.contactRepository = contactRepository;
        this.maxRows = maxRows;
        this.maxMethodsPerType = maxMethodsPerType;
        List<String> list = new ArrayList<>(List.of(COL_NAME, COL_GROUP));
        Arrays.stream(MethodType.values()).map(MethodType::getColumnTitle).forEach(list::add);
        list.add(COL_FAVORITE);
        list.add(COL_NOTE);
        this.headers = List.copyOf(list);
    }

    public List<String> getHeaders() {
        return headers;
    }

    /** 导出全部联系人。 */
    public byte[] export(List<Contact> contacts) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle bodyStyle = bodyStyle(workbook);

            writeHeader(sheet, headerStyle);
            int rowIndex = 1;
            for (Contact contact : contacts) {
                Row row = sheet.createRow(rowIndex++);
                Map<MethodType, List<ContactMethod>> grouped = groupByType(contact);

                int col = 0;
                setCell(row, col++, contact.getName(), bodyStyle);
                setCell(row, col++, contact.getGroup(), bodyStyle);
                for (MethodType type : MethodType.values()) {
                    setCell(row, col++, joinMethods(grouped.get(type)), bodyStyle);
                }
                setCell(row, col++, contact.isFavorite() ? "是" : "否", bodyStyle);
                setCell(row, col, contact.getNote(), bodyStyle);
            }

            applyColumnWidths(sheet);
            sheet.createFreezePane(0, 1);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("导出 Excel 失败", ex);
        }
    }

    /** 导出空白模板（含填写说明页）。 */
    public byte[] template() {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            writeHeader(sheet, headerStyle(workbook));
            sheet.createFreezePane(0, 1);
            for (int i = 0; i < headers.size(); i++) {
                sheet.setColumnWidth(i, (i <= 1 ? 18 : 24) * 256);
            }

            Sheet help = workbook.createSheet("填写说明");
            String[][] lines = {
                    {"列名", "说明"},
                    {COL_NAME, "必填。联系人姓名。"},
                    {COL_GROUP, "可选。填写分组，如：同事 / 家人 / 客户；留空视为「未分组」。"},
                    {MethodType.PHONE.getColumnTitle(), "可选。多个号码用分号「;」分隔，如：13800000000;0591-22860000。"},
                    {MethodType.EMAIL.getColumnTitle(), "可选。多个邮箱用「;」分隔。"},
                    {MethodType.SOCIAL.getColumnTitle(), "可选。微信号、QQ 号、微博等社交账号，多个用「;」分隔。"},
                    {MethodType.ADDRESS.getColumnTitle(), "可选。多个地址用「;」分隔。"},
                    {MethodType.OTHER.getColumnTitle(), "可选。其他联系方式。"},
                    {COL_FAVORITE, "可选。填「是」或「否」，留空视为「否」。"},
                    {COL_NOTE, "可选。备注信息。"},
                    {"注意事项", "第一行表头请勿删除；一个联系人占一行；空行会被忽略；至少需要一条联系方式，否则该行会被跳过。"}
            };
            CellStyle guideHeader = headerStyle(workbook);
            for (int r = 0; r < lines.length; r++) {
                Row row = help.createRow(r);
                row.createCell(0).setCellValue(lines[r][0]);
                row.createCell(1).setCellValue(lines[r][1]);
                if (r == 0) {
                    row.getCell(0).setCellStyle(guideHeader);
                    row.getCell(1).setCellStyle(guideHeader);
                }
            }
            help.setColumnWidth(0, 18 * 256);
            help.setColumnWidth(1, 80 * 256);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("生成 Excel 模板失败", ex);
        }
    }

    /**
     * 从 Excel 导入联系人。
     *
     * <p>合并规则与前端演示模式一致：姓名相同且至少有一条联系方式相同，视为同一个人，
     * 合并联系方式并更新分组/备注，计入 {@code updated}；否则新增，计入 {@code inserted}。
     * 非法行跳过并记录原因，计入 {@code failed}。
     */
    @Transactional
    public ImportResult importExcel(InputStream inputStream) {
        if (inputStream == null) {
            throw new BadRequestException("请选择要导入的 Excel 文件");
        }
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BadRequestException("Excel 文件为空，至少需要一个工作表");
            }
            Sheet sheet = pickDataSheet(workbook);
            Row headerRow = findHeaderRow(sheet);
            if (headerRow == null) {
                throw new BadRequestException("未能识别表头，请使用模板文件（第一行需包含「姓名」列）");
            }
            Map<Integer, String> columns = readHeader(headerRow);
            if (!columns.containsValue(FIELD_NAME)) {
                throw new BadRequestException("缺少「姓名」列，请参考模板");
            }

            Map<String, List<Contact>> existingByName = indexExisting();
            ImportResult result = new ImportResult();

            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                result.setTotalRows(result.getTotalRows() + 1);
                if (result.getTotalRows() > maxRows) {
                    throw new BadRequestException("单次最多导入 " + maxRows + " 行，请拆分文件后重试");
                }
                try {
                    Contact parsed = parseRow(row, columns);
                    mergeOrInsert(parsed, existingByName, result);
                } catch (BadRequestException ex) {
                    result.recordFailure("第 " + (rowIndex + 1) + " 行：" + ex.getMessage());
                }
            }
            return result;
        } catch (IOException ex) {
            throw new BadRequestException("无法解析该文件，请确认是有效的 .xlsx / .xls 文件");
        }
    }

    private Map<String, List<Contact>> indexExisting() {
        Map<String, List<Contact>> index = new HashMap<>();
        for (Contact contact : contactRepository.findAllByOrderByFavoriteDescUpdatedAtDesc()) {
            index.computeIfAbsent(contact.getName(), key -> new ArrayList<>()).add(contact);
        }
        return index;
    }

    private void mergeOrInsert(Contact parsed, Map<String, List<Contact>> existingByName, ImportResult result) {
        List<Contact> candidates = existingByName.getOrDefault(parsed.getName(), List.of());
        Contact matched = candidates.stream()
                .filter(contact -> sharesMethod(contact, parsed))
                .findFirst()
                .orElse(null);

        if (matched == null) {
            Contact saved = contactRepository.save(parsed);
            existingByName.computeIfAbsent(saved.getName(), key -> new ArrayList<>()).add(saved);
            result.recordInserted();
            return;
        }

        for (ContactMethod method : parsed.getMethods()) {
            boolean duplicated = matched.getMethods().stream()
                    .anyMatch(existing -> existing.getType() == method.getType()
                            && existing.getValue().equalsIgnoreCase(method.getValue()));
            if (!duplicated) {
                matched.addMethod(new ContactMethod(method.getType(), method.getLabel(), method.getValue()));
            }
        }
        if (parsed.getGroup() != null && !parsed.getGroup().isBlank()) {
            matched.setGroup(parsed.getGroup());
        }
        if (parsed.getNote() != null && !parsed.getNote().isBlank()) {
            matched.setNote(parsed.getNote());
        }
        contactRepository.save(matched);
        result.recordUpdated();
    }

    private boolean sharesMethod(Contact left, Contact right) {
        return left.getMethods().stream().anyMatch(a -> right.getMethods().stream()
                .anyMatch(b -> a.getValue().equalsIgnoreCase(b.getValue())));
    }

    private Contact parseRow(Row row, Map<Integer, String> columns) {
        String name = null;
        String group = null;
        String note = null;
        boolean favorite = false;
        List<ContactMethod> methods = new ArrayList<>();
        Map<MethodType, Integer> counters = new EnumMap<>(MethodType.class);

        for (Map.Entry<Integer, String> entry : columns.entrySet()) {
            String text = readText(row.getCell(entry.getKey()));
            if (text == null || text.isBlank()) {
                continue;
            }
            String field = entry.getValue();
            switch (field) {
                case FIELD_NAME -> name = text.trim();
                case FIELD_GROUP -> group = text.trim();
                case FIELD_NOTE -> {
                    if (text.length() > 1000) {
                        throw new BadRequestException("备注长度不能超过 1000");
                    }
                    note = text.trim();
                }
                case FIELD_FAVORITE -> favorite = parseBoolean(text);
                default -> {
                    Optional<MethodType> type = MethodType.fromInput(field);
                    if (type.isPresent()) {
                        collectMethods(methods, counters, type.get(), text);
                    }
                }
            }
        }

        if (name == null || name.isBlank()) {
            throw new BadRequestException("缺少姓名，已跳过");
        }
        if (name.length() > 100) {
            throw new BadRequestException("姓名长度不能超过 100");
        }
        if (methods.isEmpty()) {
            throw new BadRequestException("没有联系方式，已跳过");
        }

        Contact contact = new Contact(name);
        contact.setGroup(group == null || group.isBlank() ? Contact.DEFAULT_GROUP : group);
        contact.setNote(note);
        contact.setFavorite(favorite);
        methods.forEach(contact::addMethod);
        return contact;
    }

    private void collectMethods(List<ContactMethod> methods, Map<MethodType, Integer> counters,
                                MethodType type, String rawText) {
        List<String> tokens = Arrays.stream(rawText.split(MULTI_VALUE_SPLIT_REGEX))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .toList();
        for (String value : tokens) {
            int count = counters.getOrDefault(type, 0);
            if (count >= maxMethodsPerType) {
                break;
            }
            boolean duplicated = methods.stream()
                    .anyMatch(method -> method.getType() == type && method.getValue().equalsIgnoreCase(value));
            if (!duplicated) {
                methods.add(new ContactMethod(type, null, value));
                counters.put(type, count + 1);
            }
        }
    }

    private boolean parseBoolean(String text) {
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "是", "true", "1", "y", "yes", "√" -> true;
            default -> false;
        };
    }

    private Sheet pickDataSheet(Workbook workbook) {
        Sheet byName = workbook.getSheet(SHEET_NAME);
        return byName != null ? byName : workbook.getSheetAt(0);
    }

    /** 表头可能不在第 1 行（用户上方插入了标题），向下最多找 5 行。 */
    private Row findHeaderRow(Sheet sheet) {
        int first = sheet.getFirstRowNum();
        int last = Math.min(sheet.getLastRowNum(), first + 5);
        Row firstHeaderLike = null;
        for (int i = first; i <= last; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            boolean containsName = false;
            boolean containsAnyKnown = false;
            for (Cell cell : row) {
                String title = readText(cell);
                if (title == null || title.isBlank()) {
                    continue;
                }
                String field = resolveField(title);
                if (field == null) {
                    continue;
                }
                if (FIELD_NAME.equals(field)) {
                    containsName = true;
                    break;
                }
                containsAnyKnown = true;
            }
            if (containsName) {
                return row;
            }
            if (containsAnyKnown && firstHeaderLike == null) {
                firstHeaderLike = row;
            }
        }
        return firstHeaderLike;
    }

    private Map<Integer, String> readHeader(Row headerRow) {
        Map<Integer, String> columns = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String title = readText(cell);
            if (title == null || title.isBlank()) {
                continue;
            }
            String field = resolveField(title);
            if (field != null && !columns.containsValue(field)) {
                columns.put(cell.getColumnIndex(), field);
            }
        }
        return columns;
    }

    /** 把表头单元格映射成内部字段名，识别不出返回 null。 */
    private String resolveField(String rawTitle) {
        String key = rawTitle.trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replaceAll("[（(].*?[)）]", "");
        if (key.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : HEADER_ALIASES.entrySet()) {
            for (String alias : entry.getValue()) {
                if (key.equals(alias) || key.contains(alias)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private boolean isBlankRow(Row row) {
        for (Cell cell : row) {
            String text = readText(cell);
            if (text != null && !text.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 读取单元格文本。数字型单元格（例如直接输入的手机号）转成不带科学计数法的普通数字串。
     */
    private String readText(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return formatter.formatCellValue(cell);
            }
            double value = cell.getNumericCellValue();
            if (value == Math.rint(value) && Math.abs(value) < 1e15) {
                return BigDecimal.valueOf((long) value).toPlainString();
            }
            return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        }
        if (type == CellType.BOOLEAN) {
            return cell.getBooleanCellValue() ? "是" : "否";
        }
        String text = formatter.formatCellValue(cell);
        return text == null ? null : text.trim();
    }

    private String joinMethods(List<ContactMethod> methods) {
        if (methods == null || methods.isEmpty()) {
            return "";
        }
        return String.join(MULTI_VALUE_SEPARATOR, methods.stream()
                .map(ContactMethod::getValue)
                .toList());
    }

    private Map<MethodType, List<ContactMethod>> groupByType(Contact contact) {
        Map<MethodType, List<ContactMethod>> grouped = new EnumMap<>(MethodType.class);
        for (MethodType type : MethodType.values()) {
            grouped.put(type, new ArrayList<>());
        }
        contact.getMethods().forEach(method -> grouped.get(method.getType()).add(method));
        return grouped;
    }

    private void writeHeader(Sheet sheet, CellStyle headerStyle) {
        Row header = sheet.createRow(0);
        header.setHeightInPoints(22);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
        }
    }

    private void setCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private void applyColumnWidths(Sheet sheet) {
        int[] minimums = {16, 14, 24, 24, 22, 32, 20, 8, 32};
        for (int i = 0; i < headers.size(); i++) {
            int width = minimums[Math.min(i, minimums.length - 1)];
            int longest = longestTextLength(sheet, i);
            sheet.setColumnWidth(i, Math.min(60, Math.max(width, longest + 4)) * 256);
        }
    }

    private int longestTextLength(Sheet sheet, int column) {
        int longest = 0;
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            Cell cell = row.getCell(column);
            String value = cell == null ? null : readText(cell);
            if (value != null) {
                longest = Math.max(longest, displayWidth(value));
            }
        }
        return longest;
    }

    /** 粗略估算显示宽度：中文按 2 个字符宽度计算。 */
    private int displayWidth(String value) {
        int width = 0;
        for (int i = 0; i < value.length(); i++) {
            width += value.charAt(i) > 0x2E80 ? 2 : 1;
        }
        return width;
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle bodyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.HAIR);
        style.setBorderTop(BorderStyle.HAIR);
        style.setBorderLeft(BorderStyle.HAIR);
        style.setBorderRight(BorderStyle.HAIR);
        return style;
    }
}
