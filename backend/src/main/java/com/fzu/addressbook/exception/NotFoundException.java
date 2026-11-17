package com.fzu.addressbook.exception;

/**
 * 资源不存在，统一映射为 HTTP 404。
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
