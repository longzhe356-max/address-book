package com.fzu.addressbook.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * 新增或整体更新联系人的请求体。
 *
 * <p>字段与前端 {@code API.toPayload()} 一致：
 * {@code name / group / note / favorite / methods[{type,value}]}。
 */
public class ContactRequest {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 100, message = "姓名长度不能超过 100")
    private String name;

    @Size(max = 100, message = "分组长度不能超过 100")
    private String group;

    @Size(max = 1000, message = "备注长度不能超过 1000")
    private String note;

    private Boolean favorite;

    @Valid
    private List<MethodRequest> methods = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Boolean getFavorite() {
        return favorite;
    }

    public void setFavorite(Boolean favorite) {
        this.favorite = favorite;
    }

    public List<MethodRequest> getMethods() {
        return methods;
    }

    public void setMethods(List<MethodRequest> methods) {
        this.methods = methods == null ? new ArrayList<>() : methods;
    }
}
