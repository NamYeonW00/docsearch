package com.example.docsearch.document;

import com.example.docsearch.document.dto.DocumentRequest;
import com.example.docsearch.document.dto.DocumentResponse;
import com.example.docsearch.pdf.PdfProcessingException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    // 임시 처리: 지금은 이 컨트롤러 안에서만 404를 처리.
    // DocumentService에서 던지는 IllegalArgumentException("문서를 찾을 수 없습니다" 등)을 여기서 잡아서
    // 500(서버 에러)이 아니라 404(Not Found)로 변환해 응답함.
    // search/chat 컨트롤러까지 생기면 @RestControllerAdvice로 전역 예외 처리로 옮길 것.
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(IllegalArgumentException e) {
        return e.getMessage();
    }

    // PDF 업로드 관련 문제(비-PDF, 빈 파일, 텍스트 없음, 손상된 파일 등)는 "잘못된 요청"이므로 400으로 응답한다.
    // 프론트가 이 메시지를 그대로 사용자에게 보여줄 수 있도록 예외 메시지를 body로 내려준다.
    @ExceptionHandler(PdfProcessingException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handlePdfProcessing(PdfProcessingException e) {
        return e.getMessage();
    }
}