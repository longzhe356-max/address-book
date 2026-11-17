package com.fzu.addressbook.exception;

/**
 * 请求参数/文件内容不合法，统一映射为 HTTP 400。
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
