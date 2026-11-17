package com.fzu.addressbook.controller;

import com.fzu.addressbook.dto.ContactRequest;
import com.fzu.addressbook.dto.ContactResponse;
import com.fzu.addressbook.dto.FavoriteRequest;
import com.fzu.addressbook.dto.MethodRequest;
import com.fzu.addressbook.dto.MethodResponse;
import com.fzu.addressbook.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 联系人与联系方式接口。
 *
 * <p>接口文档见 {@code docs/api.md}。
 */
@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    /** 联系人列表，支持 {@code ?keyword=} 关键字搜索。 */
    @GetMapping
    public List<ContactResponse> list(@RequestParam(required = false) String keyword) {
        return contactService.list(keyword);
    }

    /** 1.1 收藏联系人列表。 */
    @GetMapping("/favorites")
    public List<ContactResponse> favorites() {
        return contactService.listFavorites();
    }

    @GetMapping("/{id}")
    public ContactResponse get(@PathVariable Long id) {
        return contactService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContactResponse create(@Valid @RequestBody ContactRequest request) {
        return contactService.create(request);
    }

    @PutMapping("/{id}")
    public ContactResponse update(@PathVariable Long id, @Valid @RequestBody ContactRequest request) {
        return contactService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        contactService.delete(id);
    }

    /** 1.1 收藏 / 取消收藏：{@code {"favorite": true}}。 */
    @PatchMapping("/{id}/favorite")
    public ContactResponse updateFavorite(@PathVariable Long id, @RequestBody FavoriteRequest request) {
        return contactService.updateFavorite(id, request.isFavorite());
    }

    /** 1.2 追加一条联系方式。 */
    @PostMapping("/{id}/methods")
    @ResponseStatus(HttpStatus.CREATED)
    public MethodResponse addMethod(@PathVariable Long id, @Valid @RequestBody MethodRequest request) {
        return contactService.addMethod(id, request);
    }

    /** 1.2 修改一条联系方式。 */
    @PutMapping("/{id}/methods/{methodId}")
    public MethodResponse updateMethod(@PathVariable Long id,
                                       @PathVariable Long methodId,
                                       @Valid @RequestBody MethodRequest request) {
        return contactService.updateMethod(id, methodId, request);
    }

    /** 1.2 删除一条联系方式。 */
    @DeleteMapping("/{id}/methods/{methodId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteMethod(@PathVariable Long id, @PathVariable Long methodId) {
        contactService.deleteMethod(id, methodId);
        return ResponseEntity.noContent().build();
    }
}
