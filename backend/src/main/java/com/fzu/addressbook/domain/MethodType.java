package com.fzu.addressbook.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 联系方式的类型。
 *
 * <p>与前端（frontend/js/api.js 的 TYPES）保持一致：
 * {@code phone / email / social / address / other}，
 * 数据库与 JSON 中用大写枚举名，解析时大小写与中英文都接受。
 * 每种类型在导出的 Excel 中固定占据一列。
 */
public enum MethodType {

    PHONE("电话"),
    EMAIL("邮箱"),
    SOCIAL("社交账号"),
    ADDRESS("地址"),
    OTHER("其他");

    private final String columnTitle;

    MethodType(String columnTitle) {
        this.columnTitle = columnTitle;
    }

    /** Excel 中该类型对应的列名，也是界面上展示的中文名。 */
    public String getColumnTitle() {
        return columnTitle;
    }

    /**
     * 宽松解析：接受枚举名、中文列名，以及前端/历史数据里常见的别名。
     */
    public static Optional<MethodType> fromInput(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.matches(key))
                .findFirst();
    }

    private boolean matches(String key) {
        return switch (this) {
            case PHONE -> "phone".equals(key) || "tel".equals(key) || "mobile".equals(key)
                    || "mobilephone".equals(key) || "telephone".equals(key)
                    || "电话".equals(key) || "手机".equals(key) || "手机号".equals(key) || "电话号码".equals(key);
            case EMAIL -> "email".equals(key) || "mail".equals(key) || "e-mail".equals(key)
                    || "邮箱".equals(key) || "电子邮箱".equals(key) || "邮件".equals(key);
            case SOCIAL -> "social".equals(key) || "socialmedia".equals(key) || "wechat".equals(key)
                    || "qq".equals(key) || "weibo".equals(key)
                    || "社交账号".equals(key) || "社交".equals(key) || "微信".equals(key) || "微博".equals(key);
            case ADDRESS -> "address".equals(key) || "addr".equals(key) || "location".equals(key)
                    || "地址".equals(key) || "住址".equals(key);
            case OTHER -> "other".equals(key) || "其他".equals(key) || "其它".equals(key);
        };
    }
}
