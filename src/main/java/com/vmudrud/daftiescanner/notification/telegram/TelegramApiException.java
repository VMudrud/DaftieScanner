package com.vmudrud.daftiescanner.notification.telegram;

public class TelegramApiException extends RuntimeException {

    private final int statusCode;

    public TelegramApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }

    public boolean isForbidden() {
        return statusCode == 403;
    }
}
