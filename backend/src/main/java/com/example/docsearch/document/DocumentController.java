package com.example.docsearch.document;

import com.example.docsearch.document.dto.DocumentRequest;
import com.example.docsearch.document.dto.DocumentResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

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

    // POST /api/documents/upload  (multipart/form-data: file=<PDF>, title?, category?)
    // PDF에서 텍스트를 추출해 자동으로 chunk 분할 + 임베딩까지 수행한다 (내부적으로 일반 등록 파이프라인 재사용).
    // title을 비워 보내면 파일명(확장자 제외)이 제목으로 사용된다.
    @PostMapping("/upload")
    public DocumentResponse upload(@RequestParam("file") MultipartFile file,
                                   @RequestParam(value = "title", required = false) String title,
                                   @RequestParam(value = "category", required = false) String category) {
        return documentService.registerFromPdf(file, title, category);
    }

    // GET /api/documents?title=spring  - 제목 부분 일치(대소문자 무시)로 활성 문서 목록 조회.
    // 정확한 제목을 몰라도("Spring"만 입력) 매칭되는 문서들을 목록으로 찾을 수 있게 한다.
    // 목록에서 문서를 고르면 프론트가 기존 title/{title}·versions 엔드포인트로 상세를 조회한다.
    @GetMapping
    public List<DocumentResponse> searchByTitle(@RequestParam(value = "title", required = false) String title) {
        return documentService.searchActiveByTitle(title);
    }

    // GET /api/documents/{id}  - 특정 버전 하나를 id(PK)로 직접 조회
    @GetMapping("/{id}")
    public DocumentResponse getById(@PathVariable Long id) {
        return documentService.getById(id);
    }

    // DELETE /api/documents/{id}  - 특정 버전 soft delete(비활성화). 물리 삭제 아님.
    // 비활성화된 최신 상태(DocumentResponse)를 반환해 프론트가 바로 UI를 갱신할 수 있게 한다.
    @DeleteMapping("/{id}")
    public DocumentResponse deactivate(@PathVariable Long id) {
        return documentService.deactivate(id);
    }

    // GET /api/documents/title/{title}  - title로 "현재 활성" 버전 조회
    @GetMapping("/title/{title}")
    public DocumentResponse getActiveByTitle(@PathVariable String title) {
        return documentService.getActiveByTitle(title);
    }

    // GET /api/documents/title/{title}/versions  - 해당 title의 버전 이력 전체 (최신순)
    @GetMapping("/title/{title}/versions")
    public List<DocumentResponse> getVersions(@PathVariable String title) {
        return documentService.getVersions(title);
    }

    // 예외 매핑(IllegalArgumentException 404, PdfProcessingException 400 등)은
    // com.example.docsearch.common.GlobalExceptionHandler로 이관했다.
}