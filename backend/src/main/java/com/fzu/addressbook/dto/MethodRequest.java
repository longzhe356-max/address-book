package com.fzu.addressbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增/修改联系人时提交的一条联系方式。
 */
public class MethodRequest {

    /** 类型：{@code phone / email / social / address / other}，也接受中文名。 */
    @NotBlank(message = "联系方式类型不能为空")
    private String type;

    /** 可选标签，例如「工作」；前端当前不发送该字段。 */
    @Size(max = 50, message = "标签长度不能超过 50")
    private String label;

    @NotBlank(message = "联系方式内容不能为空")
    @Size(max = 500, message = "联系方式长度不能超过 500")
    private String value;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
