package com.fzu.addressbook.service;

import com.fzu.addressbook.domain.Contact;
import com.fzu.addressbook.dto.ContactRequest;
import com.fzu.addressbook.dto.ContactResponse;
import com.fzu.addressbook.dto.MethodRequest;
import com.fzu.addressbook.exception.BadRequestException;
import com.fzu.addressbook.exception.NotFoundException;
import com.fzu.addressbook.repository.ContactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 1.1 收藏联系人、1.2 多联系方式的业务逻辑测试。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:contact-service-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class ContactServiceTest {

    @Autowired
    private ContactService contactService;

    @Autowired
    private ContactRepository contactRepository;

    @BeforeEach
    void cleanUp() {
        contactRepository.deleteAll();
    }

    @Test
    @DisplayName("新增联系人时可以一次写入多种类型的联系方式")
    void createContactWithMultipleMethodTypes() {
        ContactRequest request = new ContactRequest();
        request.setName("张三");
        request.setGroup("同事");
        request.setNote("前端组");
        request.setMethods(List.of(
                method("phone", "13800000000"),
                method("tel", "0591-22860000"),
                method("email", "zhangsan@fzu.edu.cn"),
                method("social", "微信 zhangsan_wx"),
                method("地址", "福州市闽侯县")));

        ContactResponse created = contactService.create(request);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getGroup()).isEqualTo("同事");
        assertThat(created.getNote()).isEqualTo("前端组");
        assertThat(created.getMethods())
                .extracting(m -> m.getType() + ":" + m.getValue())
                .containsExactlyInAnyOrder(
                        "PHONE:13800000000",
                        "PHONE:0591-22860000",
                        "EMAIL:zhangsan@fzu.edu.cn",
                        "SOCIAL:微信 zhangsan_wx",
                        "ADDRESS:福州市闽侯县");
        assertThat(created.getMethods())
                .filteredOn(m -> "EMAIL".equals(m.getType()))
                .singleElement()
                .satisfies(m -> assertThat(m.getTypeTitle()).isEqualTo("邮箱"));
    }

    @Test
    @DisplayName("未填分组时使用默认分组「未分组」")
    void defaultGroupWhenAbsent() {
        ContactResponse created = contactService.create(requestOf("李四"));

        assertThat(created.getGroup()).isEqualTo(Contact.DEFAULT_GROUP);
    }

    @Test
    @DisplayName("没有联系方式时拒绝创建，返回明确提示")
    void rejectContactWithoutMethods() {
        ContactRequest request = new ContactRequest();
        request.setName("没有联系方式");

        assertThatThrownBy(() -> contactService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("至少需要一条联系方式");
    }

    @Test
    @DisplayName("收藏后出现在收藏列表，取消后从收藏列表移除")
    void toggleFavorite() {
        ContactResponse contact = contactService.create(requestOf("李四"));

        contactService.updateFavorite(contact.getId(), true);
        assertThat(contactService.listFavorites())
                .extracting(ContactResponse::getName)
                .containsExactly("李四");

        contactService.updateFavorite(contact.getId(), false);
        assertThat(contactService.listFavorites()).isEmpty();
    }

    @Test
    @DisplayName("相同类型且内容重复的联系方式会被拒绝")
    void rejectDuplicatedMethod() {
        ContactResponse contact = contactService.create(requestOf("王五"));
        contactService.addMethod(contact.getId(), method("phone", "13800000000"));

        assertThatThrownBy(() -> contactService.addMethod(contact.getId(), method("PHONE", "13800000000")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    @DisplayName("关键字可以命中分组与联系方式内容")
    void searchByGroupAndMethodValue() {
        ContactRequest request = requestOf("赵六");
        request.setGroup("客户");
        ContactResponse contact = contactService.create(request);
        contactService.addMethod(contact.getId(), method("qq", "10001"));

        assertThat(contactService.list("10001"))
                .extracting(ContactResponse::getName)
                .containsExactly("赵六");
        assertThat(contactService.list("客户"))
                .extracting(ContactResponse::getName)
                .containsExactly("赵六");
        assertThat(contactService.list("不存在的人")).isEmpty();
    }

    @Test
    @DisplayName("删除联系人会级联删除其联系方式")
    void deleteContactRemovesMethods() {
        ContactResponse contact = contactService.create(requestOf("钱七"));
        contactService.addMethod(contact.getId(), method("address", "福州市鼓楼区"));

        contactService.delete(contact.getId());

        assertThat(contactRepository.findById(contact.getId())).isEmpty();
        assertThatThrownBy(() -> contactService.get(contact.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("整体更新会用提交的联系方式替换旧数据")
    void updateReplacesMethods() {
        ContactResponse contact = contactService.create(requestOf("孙八"));
        contactService.addMethod(contact.getId(), method("phone", "13800000000"));

        ContactRequest update = requestOf("孙八");
        update.setGroup("家人");
        update.setFavorite(true);
        update.setMethods(List.of(method("email", "sunba@example.com")));

        ContactResponse updated = contactService.update(contact.getId(), update);

        assertThat(updated.isFavorite()).isTrue();
        assertThat(updated.getGroup()).isEqualTo("家人");
        assertThat(updated.getMethods()).singleElement()
                .satisfies(m -> {
                    assertThat(m.getType()).isEqualTo("EMAIL");
                    assertThat(m.getValue()).isEqualTo("sunba@example.com");
                });
    }

    @Test
    @DisplayName("不支持的联系方式类型给出明确错误")
    void rejectUnknownMethodType() {
        ContactResponse contact = contactService.create(requestOf("周九"));

        assertThatThrownBy(() -> contactService.addMethod(contact.getId(), method("fax", "123")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("不支持的联系方式类型");
    }

    private ContactRequest requestOf(String name) {
        ContactRequest request = new ContactRequest();
        request.setName(name);
        request.setMethods(List.of(method("phone", "13900000000")));
        return request;
    }

    private MethodRequest method(String type, String value) {
        MethodRequest request = new MethodRequest();
        request.setType(type);
        request.setValue(value);
        return request;
    }
}
