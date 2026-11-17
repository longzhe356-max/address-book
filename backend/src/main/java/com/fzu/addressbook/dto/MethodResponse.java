package com.fzu.addressbook.dto;

/**
 * 联系方式响应体。
 *
 * <p>{@code type} 为前端使用的枚举名（{@code PHONE / EMAIL / SOCIAL / ADDRESS / OTHER}），
 * {@code typeTitle} 为中文展示名（电话 / 邮箱 / 社交账号 / 地址 / 其他）。
 */
public class MethodResponse {

    private Long id;
    private String type;
    private String typeTitle;
    private String label;
    private String value;

    public MethodResponse() {
    }

    public MethodResponse(Long id, String type, String typeTitle, String label, String value) {
        this.id = id;
        this.type = type;
        this.typeTitle = typeTitle;
        this.label = label;
        this.value = value;
    }

    public Long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getTypeTitle() {
        return typeTitle;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }
}
