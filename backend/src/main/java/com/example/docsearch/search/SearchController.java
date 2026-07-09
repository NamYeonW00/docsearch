package com.example.docsearch.search;

import com.example.docsearch.search.dto.SearchResultDto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@Validated // 클래스 레벨에 붙여야 @RequestParam에 붙인 @Max/@Min 같은 검증 어노테이션이 실제로 동작함
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    // GET /api/search?query=Spring+AI&topK=5&category=framework&threshold=0.5
    @GetMapping
    public List<SearchResultDto> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int topK, // 파라미터 없으면 5, 20 초과 요청 시 400 에러
            @RequestParam(required = false) String category,   // 없으면 null -> SearchService에서 필터 미적용
            @RequestParam(required = false) Double threshold    // 없으면 null -> SearchService에서 기본값(0.0) 유지
    ) {
        return searchService.search(query, topK, category, threshold);
    }
}