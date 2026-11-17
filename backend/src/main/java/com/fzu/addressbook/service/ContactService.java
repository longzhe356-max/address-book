package com.fzu.addressbook.service;

import com.fzu.addressbook.domain.Contact;
import com.fzu.addressbook.domain.ContactMethod;
import com.fzu.addressbook.domain.MethodType;
import com.fzu.addressbook.dto.ContactRequest;
import com.fzu.addressbook.dto.ContactResponse;
import com.fzu.addressbook.dto.MethodRequest;
import com.fzu.addressbook.dto.MethodResponse;
import com.fzu.addressbook.exception.BadRequestException;
import com.fzu.addressbook.exception.NotFoundException;
import com.fzu.addressbook.repository.ContactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 联系人业务逻辑：增删改查、收藏、多联系方式维护。
 */
@Service
@Transactional(readOnly = true)
public class ContactService {

    private final ContactRepository contactRepository;
    private final ContactMapper mapper;

    public ContactService(ContactRepository contactRepository, ContactMapper mapper) {
        this.contactRepository = contactRepository;
        this.mapper = mapper;
    }

    /** 列表：支持按关键字搜索（姓名、分组、备注、任意联系方式内容）。 */
    public List<ContactResponse> list(String keyword) {
        List<Contact> contacts;
        if (keyword == null || keyword.isBlank()) {
            contacts = contactRepository.findAllByOrderByFavoriteDescUpdatedAtDesc();
        } else {
            contacts = contactRepository.search(keyword.trim());
        }
        return mapper.toResponses(contacts);
    }

    /** 1.1 收藏联系人：仅返回已收藏的联系人，最近更新的排在前面。 */
    public List<ContactResponse> listFavorites() {
        return mapper.toResponses(contactRepository.findByFavoriteTrueOrderByUpdatedAtDesc());
    }

    public ContactResponse get(Long id) {
        return mapper.toResponse(requireContact(id));
    }

    @Transactional
    public ContactResponse create(ContactRequest request) {
        Contact contact = new Contact(request.getName().trim());
        contact.setGroup(normalizeGroup(request.getGroup()));
        contact.setNote(normalize(request.getNote()));
        contact.setFavorite(Boolean.TRUE.equals(request.getFavorite()));
        applyMethods(contact, request.getMethods());
        requireAtLeastOneMethod(contact);
        return mapper.toResponse(contactRepository.save(contact));
    }

    /** 整体更新：联系方式按提交内容全量替换（与前端 PUT 的语义一致）。 */
    @Transactional
    public ContactResponse update(Long id, ContactRequest request) {
        Contact contact = requireContact(id);
        contact.setName(request.getName().trim());
        contact.setGroup(normalizeGroup(request.getGroup()));
        contact.setNote(normalize(request.getNote()));
        if (request.getFavorite() != null) {
            contact.setFavorite(request.getFavorite());
        }
        contact.clearMethods();
        applyMethods(contact, request.getMethods());
        requireAtLeastOneMethod(contact);
        return mapper.toResponse(contactRepository.save(contact));
    }

    @Transactional
    public void delete(Long id) {
        Contact contact = requireContact(id);
        contactRepository.delete(contact);
    }

    /** 1.1 收藏/取消收藏。 */
    @Transactional
    public ContactResponse updateFavorite(Long id, boolean favorite) {
        Contact contact = requireContact(id);
        contact.setFavorite(favorite);
        return mapper.toResponse(contactRepository.save(contact));
    }

    /** 1.2 给已有联系人追加一条联系方式。 */
    @Transactional
    public MethodResponse addMethod(Long contactId, MethodRequest request) {
        Contact contact = requireContact(contactId);
        ContactMethod method = toMethod(request);

        boolean duplicated = contact.getMethods().stream()
                .anyMatch(existing -> existing.getType() == method.getType()
                        && existing.getValue().equalsIgnoreCase(method.getValue()));
        if (duplicated) {
            throw new BadRequestException("该联系方式已存在：" + method.getType().getColumnTitle() + " " + method.getValue());
        }

        contact.addMethod(method);
        Contact saved = contactRepository.save(contact);
        return mapper.toResponse(saved.getMethods().get(saved.getMethods().size() - 1));
    }

    /** 1.2 修改某条联系方式。 */
    @Transactional
    public MethodResponse updateMethod(Long contactId, Long methodId, MethodRequest request) {
        Contact contact = requireContact(contactId);
        ContactMethod target = contact.getMethods().stream()
                .filter(method -> method.getId().equals(methodId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("联系方式不存在：" + methodId));

        MethodType type = parseType(request.getType());
        boolean duplicated = contact.getMethods().stream()
                .anyMatch(method -> !method.getId().equals(methodId)
                        && method.getType() == type
                        && method.getValue().equalsIgnoreCase(request.getValue().trim()));
        if (duplicated) {
            throw new BadRequestException("该联系方式已存在：" + type.getColumnTitle() + " " + request.getValue());
        }

        target.setType(type);
        target.setLabel(normalize(request.getLabel()));
        target.setValue(request.getValue().trim());
        contactRepository.save(contact);
        return mapper.toResponse(target);
    }

    /** 1.2 删除某条联系方式。 */
    @Transactional
    public void deleteMethod(Long contactId, Long methodId) {
        Contact contact = requireContact(contactId);
        boolean removed = contact.getMethods().removeIf(method -> method.getId().equals(methodId));
        if (!removed) {
            throw new NotFoundException("联系方式不存在：" + methodId);
        }
        contactRepository.save(contact);
    }

    private Contact requireContact(Long id) {
        return contactRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("联系人不存在：" + id));
    }

    /**
     * 写入联系方式。同一类型下内容重复的条目会被静默忽略，保证提交/导入结果幂等。
     */
    private void applyMethods(Contact contact, List<MethodRequest> methods) {
        if (methods == null) {
            return;
        }
        Map<String, ContactMethod> unique = new LinkedHashMap<>();
        for (MethodRequest request : methods) {
            if (request == null || request.getValue() == null || request.getValue().isBlank()) {
                continue;
            }
            ContactMethod method = toMethod(request);
            String key = method.getType().name() + "|" + method.getValue().toLowerCase();
            unique.putIfAbsent(key, method);
        }
        unique.values().forEach(contact::addMethod);
    }

    private void requireAtLeastOneMethod(Contact contact) {
        if (contact.getMethods().isEmpty()) {
            throw new BadRequestException("至少需要一条联系方式");
        }
    }

    private ContactMethod toMethod(MethodRequest request) {
        MethodType type = parseType(request.getType());
        String value = request.getValue() == null ? "" : request.getValue().trim();
        if (value.isEmpty()) {
            throw new BadRequestException("联系方式内容不能为空");
        }
        if (value.length() > 500) {
            throw new BadRequestException("联系方式长度不能超过 500");
        }
        return new ContactMethod(type, normalize(request.getLabel()), value);
    }

    private MethodType parseType(String rawType) {
        return MethodType.fromInput(rawType)
                .orElseThrow(() -> new BadRequestException("不支持的联系方式类型：" + rawType));
    }

    private String normalizeGroup(String group) {
        String value = normalize(group);
        return value == null ? Contact.DEFAULT_GROUP : value;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
