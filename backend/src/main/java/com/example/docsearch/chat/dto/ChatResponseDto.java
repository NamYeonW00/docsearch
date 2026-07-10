package com.example.docsearch.chat.dto;

import com.example.docsearch.search.dto.SearchResultDto;

import java.util.List;

// GET /api/chat 응답 형태.
// answer: LLM이 생성한 최종 답변
// sources: 답변을 만들 때 컨텍스트로 사용한 chunk들 (SearchResultDto 재사용 -
//          검색 결과와 동일한 형태라 별도 DTO를 새로 만들 필요가 없음)
public record ChatResponseDto(
        String answer,
        List<SearchResultDto> sources
) {
}