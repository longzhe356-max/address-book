package com.fzu.addressbook.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查，用于云服务器部署后确认服务存活。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        // 接口约定要求返回 status: "ok"；前端只判断 2xx，多出的字段不影响
        body.put("status", "ok");
        body.put("service", "address-book");
        body.put("time", LocalDateTime.now());
        return body;
    }
}
