package com.example.docsearch.search;

import com.example.docsearch.search.dto.SearchResultDto;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SearchService {

    private final VectorStore vectorStore;
    private final FilterExpressionBuilder filterExpressionBuilder = new FilterExpressionBuilder();

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
            // 문자열을 직접 이어붙이지 않고 DSL 빌더로 구성.
            // category 값에 작은따옴표 등 특수문자가 섞여도 표현식 문법이 깨지거나
            // 의도치 않게 조건이 바뀌는(필터 인젝션) 위험이 없음 - 값은 항상 데이터로만 취급됨
            builder.filterExpression(filterExpressionBuilder.eq("category", category).build());
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
                toInteger(metadata.get("chunk_index")), // TextSplitter가 chunk 분할 시 직접 채워주는 키
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