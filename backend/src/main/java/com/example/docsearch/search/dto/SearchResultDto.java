package com.example.docsearch.search.dto;

// 검색 결과 하나(=chunk 하나)를 표현하는 DTO.
// document_chunks(Spring AI가 관리하는 테이블)에서 나온 결과를,
// 우리 도메인 관점에서 읽기 좋은 형태로 변환해서 응답한다.
public record SearchResultDto(
        String title,       // metadata.title - documents 테이블 재조회 없이 바로 보여줄 수 있음
        String content,      // 실제로 검색된 chunk 텍스트 (이게 매칭된 이유를 사용자가 확인할 수 있게)
        Double score,         // cosine similarity 점수. 1에 가까울수록 유사, 0에 가까울수록 무관
        String category,      // metadata.category, nullable
        Integer chunkIndex,   // metadata.chunk_index - 원본 문서에서 몇 번째 조각인지
        Long documentId       // metadata.documentId - 필요 시 documents 테이블 역참조용
) {
}
