package com.example.docsearch.document;

import com.example.docsearch.document.dto.DocumentRequest;
import com.example.docsearch.document.dto.DocumentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController // @Controller + @ResponseBody. 메서드 반환값이 View 이름이 아니라 JSON body로 바로 직렬화됨
@RequestMapping("/api/documents") // 이 컨트롤러의 모든 엔드포인트 앞에 공통으로 붙는 경로
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    // POST /api/documents  (body: {title, content, category})
    // @Valid가 DocumentRequest의 @NotBlank 검증을 트리거함. 검증 실패 시 400 Bad Request 자동 응답
    @PostMapping
    public DocumentResponse register(@RequestBody @Valid DocumentRequest request) {
        return documentService.register(request);
    }

    // GET /api/documents/{id}  - 특정 버전 하나를 id(PK)로 직접 조회
    @GetMapping("/{id}")
    public DocumentResponse getById(@PathVariable Long id) {
        return documentService.getById(id);
    }

    // GET /api/documents/title/{title}  - title로 "현재 활성" 버전 조회
    @GetMapping("/title/{title}")
    public DocumentResponse getActiveByTitle(@PathVariable String title) {
        return documentService.getActiveByTitle(title);
    }

    // GET /api/documents/title/{title}/versions  - 해당 title의 버전 이력 전체 (최신순)
    @GetMapping("/title/{title}/versions")
    public java.util.List<Document> getVersions(@PathVariable String title) {
        return documentService.getVersions(title);
    }

    // 임시 처리: 지금은 이 컨트롤러 안에서만 404를 처리.
    // DocumentService에서 던지는 IllegalArgumentException("문서를 찾을 수 없습니다" 등)을 여기서 잡아서
    // 500(서버 에러)이 아니라 404(Not Found)로 변환해 응답함.
    // search/chat 컨트롤러까지 생기면 @RestControllerAdvice로 전역 예외 처리로 옮길 것.
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(IllegalArgumentException e) {
        return e.getMessage();
    }
}
