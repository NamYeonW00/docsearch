package com.example.docsearch.document.dto;

import jakarta.validation.constraints.NotBlank;

// POST /api/documents 요청 body를 받는 DTO.
// title/content는 필수(@NotBlank), category는 선택값이라 검증 어노테이션 없음
public record DocumentRequest(
        @NotBlank(message = "title은 필수입니다")
        String title,

        @NotBlank(message = "content는 필수입니다")
        String content,

        String category // nullable
) {
}