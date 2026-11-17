package com.fzu.addressbook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 一条联系方式，例如「手机：13800000000」「邮箱：a@b.com」。
 *
 * <p>1.2 要求一个联系人可以关联多种联系方式，因此这里与 {@link Contact} 是 N:1 关系。
 */
@Entity
@Table(name = "contact_method")
public class ContactMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MethodType type;

    /** 可选的标签，例如「工作」「家庭」，用于区分同类型的多个号码。 */
    @Column(length = 50)
    private String label;

    /** 数据库列名用 method_value：value 是 H2/MySQL 的保留字。 */
    @Column(name = "method_value", nullable = false, length = 500)
    private String value;

    protected ContactMethod() {
        // JPA
    }

    public ContactMethod(MethodType type, String label, String value) {
        this.type = type;
        this.label = label;
        this.value = value;
    }

    public Long getId() {
        return id;
    }

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    public MethodType getType() {
        return type;
    }

    public void setType(MethodType type) {
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
