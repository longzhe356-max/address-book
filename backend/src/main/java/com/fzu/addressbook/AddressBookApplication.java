package com.fzu.addressbook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 通讯录地址簿后端入口。
 *
 * <p>作业对应的三项基础功能分别位于：
 * <ul>
 *   <li>收藏联系人 —— {@code ContactService#updateFavorite}</li>
 *   <li>多联系方式 —— {@code ContactMethod} 实体与 {@code ContactMethodService}</li>
 *   <li>Excel 导入导出 —— {@code ExcelService}</li>
 * </ul>
 */
@SpringBootApplication
public class AddressBookApplication {

    public static void main(String[] args) {
        SpringApplication.run(AddressBookApplication.class, args);
    }
}
