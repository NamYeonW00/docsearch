package com.example.docsearch.search;

import com.example.docsearch.search.dto.SearchResultDto;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SearchService {

    private final VectorStore vectorStore;

    public SearchService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * @param query     검색 질의문 (필수)
     * @param topK      반환할 최대 결과 개수
     * @param category  nullable. 지정하면 해당 category의 chunk만 검색 대상으로 필터링
     * @param threshold nullable. 지정하면 그 값 미만인 결과는 제외 (null이면 SearchRequest 기본값 0.0 = 전체 허용)
     */
    public List<SearchResultDto> search(String query, int topK, String category, Double threshold) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topK);

        if (threshold != null) {
            builder.similarityThreshold(threshold);
        }
        if (category != null && !category.isBlank()) {
            // filterExpression은 SQL과 비슷한 문법의 문자열. 문자열 값은 작은따옴표로 감싸야 함
            builder.filterExpression("category == '" + category + "'");
        }

        List<Document> results = vectorStore.similaritySearch(builder.build());

        return results.stream()
                .map(this::toSearchResultDto)
                .toList();
    }

    private SearchResultDto toSearchResultDto(Document document) {
        Map<String, Object> metadata = document.getMetadata();

        return new SearchResultDto(
                (String) metadata.get("title"),
                document.getText(),
                document.getScore(),
                (String) metadata.get("category"), // 없으면 null 그대로 반환됨 (put 자체를 안 했으므로)
                toInteger(metadata.get("chunkIndex")),
                toLong(metadata.get("documentId"))
        );
    }

    // JSON 역직렬화 과정에서 숫자 타입이 Integer/Long 중 무엇으로 오는지 보장이 없어서,
    // Number로 받아 안전하게 변환한다 (직접 (Long) 캐스팅 시 ClassCastException 위험).
    private Long toLong(Object value) {
        return (value instanceof Number number) ? number.longValue() : null;
    }

    private Integer toInteger(Object value) {
        return (value instanceof Number number) ? number.intValue() : null;
    }
}