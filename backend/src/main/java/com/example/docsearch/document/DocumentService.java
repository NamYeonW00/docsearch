package com.example.docsearch.document;

import com.example.docsearch.chunking.DocumentChunker;
import com.example.docsearch.document.dto.DocumentRequest;
import com.example.docsearch.document.dto.DocumentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j // Lombok이 컴파일 시점에 `private static final Logger log = LoggerFactory.getLogger(DocumentService.class);` 를 자동 생성
@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentChunker documentChunker;
    private final VectorStore vectorStore;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentChunker documentChunker,
                           VectorStore vectorStore) {
        this.documentRepository = documentRepository;
        this.documentChunker = documentChunker;
        this.vectorStore = vectorStore;
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