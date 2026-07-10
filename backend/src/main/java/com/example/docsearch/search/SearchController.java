package com.example.docsearch.search;

import com.example.docsearch.search.dto.SearchResultDto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@Validated // 클래스 레벨에 붙여야 @RequestParam에 붙인 @NotBlank/@Max/@Min 같은 검증 어노테이션이 실제로 동작함
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    // GET /api/search?query=Spring+AI&topK=5&category=framework&threshold=0.5
    @GetMapping
    public List<SearchResultDto> search(
            // 빈 문자열/공백만 있는 질의는 embedding 호출까지 가기 전에 400으로 즉시 차단
            @RequestParam @NotBlank(message = "query는 비어있을 수 없습니다") String query,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int topK,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double threshold
    ) {
        return searchService.search(query, topK, category, threshold);
    }
}