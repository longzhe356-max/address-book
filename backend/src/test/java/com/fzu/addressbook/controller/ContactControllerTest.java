package com.fzu.addressbook.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fzu.addressbook.dto.ContactRequest;
import com.fzu.addressbook.dto.FavoriteRequest;
import com.fzu.addressbook.dto.MethodRequest;
import com.fzu.addressbook.repository.ContactRepository;
import com.fzu.addressbook.service.ExcelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST 接口的端到端测试（HTTP 层），请求体按前端 {@code API.toPayload()} 的真实结构构造。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:contact-controller-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class ContactControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private ExcelService excelService;

    @BeforeEach
    void cleanUp() {
        contactRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/contacts 接受前端 payload（小写 type）并返回 201")
    void createContactWithFrontendPayload() throws Exception {
        String payload = """
                {
                  "name": "张三",
                  "group": "同事",
                  "note": "前端组组长",
                  "favorite": true,
                  "methods": [
                    {"type": "phone", "value": "13800000000"},
                    {"type": "email", "value": "zhangsan@example.com"},
                    {"type": "social", "value": "微信 zhangwei_wx"}
                  ]
                }
                """;

        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("张三"))
                .andExpect(jsonPath("$.group").value("同事"))
                .andExpect(jsonPath("$.note").value("前端组组长"))
                .andExpect(jsonPath("$.favorite").value(true))
                .andExpect(jsonPath("$.methods", hasSize(3)))
                .andExpect(jsonPath("$.methods[0].type").value("PHONE"))
                .andExpect(jsonPath("$.methods[0].typeTitle").value("电话"))
                .andExpect(jsonPath("$.methods[2].type").value("SOCIAL"))
                .andExpect(jsonPath("$.methods[2].typeTitle").value("社交账号"))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @DisplayName("GET /api/contacts 返回 JSON 数组，前端 pickArray 可直接使用")
    void listReturnsArray() throws Exception {
        createContact("李四");

        mockMvc.perform(get("/api/contacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("李四"))
                .andExpect(jsonPath("$[0].group").value("未分组"));
    }

    @Test
    @DisplayName("姓名为空时返回 400 与 message，前端可直接 toast")
    void rejectBlankName() throws Exception {
        ContactRequest request = new ContactRequest();
        request.setName("   ");

        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("没有联系方式时返回 400，提示至少一条")
    void rejectContactWithoutMethods() throws Exception {
        ContactRequest request = new ContactRequest();
        request.setName("没有联系方式");

        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("至少需要一条联系方式"));
    }

    @Test
    @DisplayName("GET /api/health 返回 status=ok，前端据此进入后端模式")
    void healthReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    @DisplayName("PATCH /api/contacts/{id}/favorite 切换收藏并出现在收藏列表")
    void toggleFavoriteThroughApi() throws Exception {
        Long id = createContact("李四");

        mockMvc.perform(patch("/api/contacts/{id}/favorite", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FavoriteRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorite").value(true));

        mockMvc.perform(get("/api/contacts/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("李四"));
    }

    @Test
    @DisplayName("PUT 全量更新会用新提交的联系方式替换旧数据")
    void updateThroughApi() throws Exception {
        Long id = createContact("王五");

        ContactRequest update = new ContactRequest();
        update.setName("王五");
        update.setGroup("客户");
        update.setMethods(List.of(method("email", "wangwu@example.com")));

        mockMvc.perform(put("/api/contacts/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group").value("客户"))
                .andExpect(jsonPath("$.methods", hasSize(1)))
                .andExpect(jsonPath("$.methods[0].type").value("EMAIL"));
    }

    @Test
    @DisplayName("联系方式的增删接口")
    void manageMethodsThroughApi() throws Exception {
        Long id = createContact("王五");

        String response = mockMvc.perform(post("/api/contacts/{id}/methods", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(method("social", "QQ 10001"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.value").value("QQ 10001"))
                .andExpect(jsonPath("$.typeTitle").value("社交账号"))
                .andReturn().getResponse().getContentAsString();

        Long methodId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(delete("/api/contacts/{id}/methods/{methodId}", id, methodId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/contacts/{id}", id))
                .andExpect(jsonPath("$.methods", hasSize(1)))
                .andExpect(jsonPath("$.methods[0].type").value("PHONE"));
    }

    @Test
    @DisplayName("访问不存在的联系人返回 404 与 message")
    void notFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/contacts/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("GET /api/contacts/export 返回 xlsx 附件")
    void exportReturnsXlsxAttachment() throws Exception {
        createContact("赵六");

        mockMvc.perform(get("/api/contacts/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(ExcelService.CONTENT_TYPE))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));
    }

    @Test
    @DisplayName("POST /api/contacts/import 返回前端需要的 inserted/updated/failed")
    void importThroughApi() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "contacts.xlsx", ExcelService.CONTENT_TYPE, excelService.export(List.of()));

        mockMvc.perform(multipart("/api/contacts/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(0))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.failed").value(0));
    }

    @Test
    @DisplayName("非 Excel 文件被拒绝")
    void rejectNonExcelUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "contacts.txt", MediaType.TEXT_PLAIN_VALUE, "hello".getBytes());

        mockMvc.perform(multipart("/api/contacts/import").file(file))
                .andExpect(status().isBadRequest());
    }

    private Long createContact(String name) throws Exception {
        ContactRequest request = new ContactRequest();
        request.setName(name);
        request.setMethods(List.of(method("phone", "13900000000")));
        String body = mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private MethodRequest method(String type, String value) {
        MethodRequest request = new MethodRequest();
        request.setType(type);
        request.setValue(value);
        return request;
    }
}
