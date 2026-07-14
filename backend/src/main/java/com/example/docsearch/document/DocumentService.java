package com.example.docsearch.document;

import com.example.docsearch.chunking.DocumentChunker;
import com.example.docsearch.document.dto.DocumentRequest;
import com.example.docsearch.document.dto.DocumentResponse;
import com.example.docsearch.pdf.PdfProcessingException;
import com.example.docsearch.pdf.PdfTextExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j // Lombok이 컴파일 시점에 `private static final Logger log = LoggerFactory.getLogger(DocumentService.class);` 를 자동 생성
@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentChunker documentChunker;
    private final VectorStore vectorStore;
    private final PdfTextExtractor pdfTextExtractor;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentChunker documentChunker,
                           VectorStore vectorStore,
                           PdfTextExtractor pdfTextExtractor) {
        this.documentRepository = documentRepository;
        this.documentChunker = documentChunker;
        this.vectorStore = vectorStore;
        this.pdfTextExtractor = pdfTextExtractor;
    }

    @Transactional
    public DocumentResponse register(DocumentRequest request) {
        // 1단계: 이 title로 이미 등록된 "활성" 버전이 있는지 조회
        Optional<Document> existingActive = documentRepository.findByTitleAndActiveTrue(request.title());
        Integer previousVersion = null;
        if (existingActive.isPresent()) {
            // 2단계 (재등록인 경우만): 기존 버전을 비활성화 + 그 버전의 벡터를 검색 대상에서 제거
            Document previous = existingActive.get();
            previousVersion = previous.getVersion();
            previous.deactivate();
            vectorStore.delete("documentId == " + previous.getId());
            log.info("문서 재등록 - title={}, 이전 버전(id={}, version={}) 비활성화 및 벡터 삭제",
                    request.title(), previous.getId(), previous.getVersion());
        }

        // 3단계: 새 버전 row 생성 및 저장
        Document newDocument = Document.createVersion(
                request.title(), request.content(), request.category(), previousVersion);
        documentRepository.save(newDocument);

        // 4단계: chunk 분할
        List<org.springframework.ai.document.Document> chunks = documentChunker.split(
                newDocument.getId(), newDocument.getTitle(), newDocument.getContent(), newDocument.getCategory());

        // 5단계: vector_store 저장
        vectorStore.add(chunks);

        // chunk 개수는 응답 대신 로그로만 남김
        log.info("문서 등록 완료 - id={}, title={}, version={}, chunkCount={}",
                newDocument.getId(), newDocument.getTitle(), newDocument.getVersion(), chunks.size());

        return DocumentResponse.of(newDocument);
    }

    /**
     * 업로드된 PDF에서 텍스트를 추출한 뒤, 기존 {@link #register(DocumentRequest)} 파이프라인
     * (버전 관리 → chunk 분할 → 임베딩 → vector_store 저장)에 그대로 흘려보낸다.
     * 즉 PDF는 "본문을 텍스트로 바꾼 일반 문서"로 취급되며, 등록 이후 처리는 텍스트 문서와 완전히 동일하다.
     *
     * @param file     업로드된 PDF (multipart)
     * @param title    문서 제목. 비어있으면 파일명(확장자 제외)을 제목으로 사용한다.
     * @param category nullable
     */
    @Transactional
    public DocumentResponse registerFromPdf(MultipartFile file, String title, String category) {
        if (file == null || file.isEmpty()) {
            throw new PdfProcessingException("업로드된 PDF 파일이 없습니다.");
        }
        if (!isPdf(file)) {
            throw new PdfProcessingException("PDF 파일만 업로드할 수 있습니다. (filename=" + file.getOriginalFilename() + ")");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new PdfProcessingException("업로드된 파일을 읽는 중 오류가 발생했습니다.", e);
        }

        String content = pdfTextExtractor.extractText(bytes, file.getOriginalFilename());
        String resolvedTitle = resolveTitle(title, file.getOriginalFilename());

        log.info("PDF 업로드 등록 시작 - filename={}, resolvedTitle={}", file.getOriginalFilename(), resolvedTitle);
        // 텍스트로 변환된 이후는 일반 등록과 동일하므로 기존 register()를 그대로 재사용한다 (재등록 시 새 버전 생성 포함).
        return register(new DocumentRequest(resolvedTitle, content, normalize(category)));
    }

    // content-type이 application/pdf 이거나 파일명이 .pdf로 끝나면 PDF로 간주한다.
    // 브라우저/OS에 따라 content-type이 application/octet-stream으로 오는 경우가 있어 확장자도 함께 본다.
    private boolean isPdf(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("pdf")) {
            return true;
        }
        String filename = file.getOriginalFilename();
        return filename != null && filename.toLowerCase().endsWith(".pdf");
    }

    // 사용자가 제목을 입력했으면 그 값을, 없으면 파일명에서 .pdf 확장자를 뗀 값을 제목으로 쓴다.
    private String resolveTitle(String title, String originalFilename) {
        if (title != null && !title.isBlank()) {
            return title.strip();
        }
        String filename = (originalFilename == null || originalFilename.isBlank()) ? "제목 없는 PDF" : originalFilename;
        // 경로 구분자가 섞여 들어오는 경우(브라우저별 차이)를 대비해 파일명만 남기고, 마지막 .pdf만 제거한다.
        filename = filename.replaceAll("^.*[/\\\\]", "");
        return filename.replaceAll("(?i)\\.pdf$", "").strip();
    }

    // 빈 문자열 카테고리는 null과 동일하게 취급 (metadata에 빈 category가 들어가지 않도록).
    private String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.strip();
    }

    public DocumentResponse getById(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다. id=" + id));
        return DocumentResponse.of(document);
    }

    /**
     * 특정 버전을 soft delete(비활성화)한다. 물리 삭제는 하지 않는다.
     * 활성 상태였다면 is_active=false로 바꾸고, 그 버전의 chunk를 vector_store에서도 제거해
     * 삭제한 문서가 검색/질문 결과에 더 이상 노출되지 않도록 한다 (재등록 로직과 동일한 정책).
     * 이미 비활성 상태면 아무 것도 하지 않는다(idempotent) - 이력 보존을 위해 재삭제하지 않음.
     * 활성 버전을 삭제해도 이전 버전을 자동 승격하지 않는다.
     */
    @Transactional
    public DocumentResponse deactivate(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다. id=" + id));
        if (document.getActive()) {
            document.deactivate();
            vectorStore.delete("documentId == " + id);
            log.info("문서 비활성화(soft delete) - id={}, title={}, version={}",
                    id, document.getTitle(), document.getVersion());
        }
        return DocumentResponse.of(document);
    }

    public DocumentResponse getActiveByTitle(String title) {
        Document document = documentRepository.findByTitleAndActiveTrue(title)
                .orElseThrow(() -> new IllegalArgumentException("활성 버전을 찾을 수 없습니다. title=" + title));
        return DocumentResponse.of(document);
    }

    // title로 등록된 버전이 하나도 없으면 빈 리스트를 반환한다 (404 아님).
    // getById/getActiveByTitle과 달리 이 API는 "목록 조회"이므로,
    // 결과 0건은 에러가 아니라 정상적인 응답으로 처리한다.
    public List<DocumentResponse> getVersions(String title) {
        return documentRepository.findAllByTitleOrderByVersionDesc(title).stream()
                .map(DocumentResponse::of) // 엔티티 리스트를 DTO 리스트로 변환 (엔티티를 API 밖으로 노출하지 않기 위함)
                .toList();
    }
}