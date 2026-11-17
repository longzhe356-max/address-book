package com.fzu.addressbook.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Excel 导入结果。
 *
 * <p>字段名与前端 {@code API.importContacts()} 读取的字段一致：
 * {@code inserted / updated / failed / errors}。
 */
public class ImportResult {

    /** 文件中识别到的联系人行数（不含表头与空行）。 */
    private int totalRows;
    /** 新增的联系人数。 */
    private int inserted;
    /** 合并到已有联系人的数量（姓名相同且至少一条联系方式相同）。 */
    private int updated;
    /** 被跳过的行数。 */
    private int failed;
    /** 逐行错误说明，最多保留前 100 条。 */
    private List<String> errors = new ArrayList<>();

    public ImportResult() {
    }

    public ImportResult(int totalRows) {
        this.totalRows = totalRows;
    }

    public void recordInserted() {
        this.inserted++;
    }

    public void recordUpdated() {
        this.updated++;
    }

    public void recordFailure(String message) {
        this.failed++;
        if (this.errors.size() < 100) {
            this.errors.add(message);
        }
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getInserted() {
        return inserted;
    }

    public void setInserted(int inserted) {
        this.inserted = inserted;
    }

    public int getUpdated() {
        return updated;
    }

    public void setUpdated(int updated) {
        this.updated = updated;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}
