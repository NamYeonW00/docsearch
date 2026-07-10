package com.example.docsearch.chat;

// LLM 호출이 실패(타임아웃 포함)했을 때 던지는 예외.
// RuntimeException을 상속해서 checked exception 강제 처리 부담 없이 사용.
public class AiServiceException extends RuntimeException {

    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}