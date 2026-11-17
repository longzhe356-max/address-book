package com.fzu.addressbook.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 联系人。字段与前端 frontend/js/api.js 的模型对应：
 * {@code name / group / note / favorite / methods / updatedAt}。
 */
@Entity
@Table(name = "contact")
public class Contact {

    /** 前端未填分组时使用的默认值。 */
    public static final String DEFAULT_GROUP = "未分组";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** group 是 SQL 保留字，列名加前缀。 */
    @Column(name = "contact_group", nullable = false, length = 100)
    private String group = DEFAULT_GROUP;

    @Column(length = 1000)
    private String note;

    /** 是否收藏（1.1 收藏联系人）。 */
    @Column(nullable = false)
    private boolean favorite = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "contact", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ContactMethod> methods = new ArrayList<>();

    protected Contact() {
        // JPA
    }

    public Contact(String name) {
        this.name = name;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** 添加一条联系方式，同时维护双向关联。 */
    public void addMethod(ContactMethod method) {
        method.setContact(this);
        this.methods.add(method);
    }

    /** 清空全部联系方式，用于 PUT 全量更新。 */
    public void clearMethods() {
        this.methods.clear();
    }

    public Long getId() {
        return id;
    }

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

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<ContactMethod> getMethods() {
        return methods;
    }
}
