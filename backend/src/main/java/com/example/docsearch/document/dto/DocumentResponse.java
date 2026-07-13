package com.example.docsearch.document.dto;

import com.example.docsearch.document.Document;

import java.time.LocalDateTime;

// API 응답으로 나가는 DTO. JPA 엔티티(Document)를 그대로 반환하지 않는 이유:
// 엔티티 내부 구조가 API 스펙에 그대로 노출되는 걸 막기 위해 (컬럼 추가/변경이 API 스펙에 직결되면 위험)
public record DocumentResponse(
        Long id,
        String title,
        String content,
        Integer version,
        Boolean active,
        String category,
        LocalDateTime createdAt
) {
    // Document 엔티티 -> DocumentResponse DTO 변환을 한 곳에 모아두는 정적 팩토리 메서드.
    public static DocumentResponse of(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getContent(),
                document.getVersion(),
                document.getActive(),
                document.getCategory(),
                document.getCreatedAt()
        );
    }
}