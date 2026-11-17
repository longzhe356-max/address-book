package com.fzu.addressbook.service;

import com.fzu.addressbook.domain.Contact;
import com.fzu.addressbook.domain.ContactMethod;
import com.fzu.addressbook.dto.ContactResponse;
import com.fzu.addressbook.dto.MethodResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 实体到响应 DTO 的转换，集中维护前端依赖的字段名。
 */
@Component
public class ContactMapper {

    public ContactResponse toResponse(Contact contact) {
        List<MethodResponse> methods = contact.getMethods().stream()
                .map(this::toResponse)
                .toList();
        return new ContactResponse(
                contact.getId(),
                contact.getName(),
                contact.getGroup(),
                contact.getNote(),
                contact.isFavorite(),
                contact.getCreatedAt(),
                contact.getUpdatedAt(),
                methods);
    }

    public MethodResponse toResponse(ContactMethod method) {
        return new MethodResponse(
                method.getId(),
                method.getType().name(),
                method.getType().getColumnTitle(),
                method.getLabel(),
                method.getValue());
    }

    public List<ContactResponse> toResponses(List<Contact> contacts) {
        return contacts.stream().map(this::toResponse).toList();
    }
}
