package com.fzu.addressbook.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 联系人响应体。字段名与前端模型保持一致：
 * {@code id / name / group / note / favorite / methods / updatedAt}。
 */
public class ContactResponse {

    private Long id;
    private String name;
    private String group;
    private String note;
    private boolean favorite;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<MethodResponse> methods;

    public ContactResponse() {
    }

    public ContactResponse(Long id, String name, String group, String note, boolean favorite,
                           LocalDateTime createdAt, LocalDateTime updatedAt, List<MethodResponse> methods) {
        this.id = id;
        this.name = name;
        this.group = group;
        this.note = note;
        this.favorite = favorite;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.methods = methods;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getGroup() {
        return group;
    }

    public String getNote() {
        return note;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<MethodResponse> getMethods() {
        return methods;
    }
}
