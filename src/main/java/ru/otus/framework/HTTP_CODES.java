package ru.otus.framework;

import lombok.Getter;

@Getter
public enum HTTP_CODES {
    OK(200, "OK"), BAD_REQUEST(400, "Bad Request");

    private final int code;
    private final String name;

    HTTP_CODES(int code, String name) {
        this.code = code;
        this.name = name;
    }
}