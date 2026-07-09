package com.example.docsearch.chunking;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 원본 문서 텍스트를 chunk 단위로 분할하고, 검색/필터링에 필요한 metadata를 채운
 * Spring AI {@link Document} 리스트로 변환한다.
 *
 * 짧은 문서(토큰 수가 chunkSize 이하)도 예외 처리 없이 그대로 통과시키면
 * TokenTextSplitter가 알아서 chunk 1개로 반환한다 (요구사항: 로직 분기 없이 일관된 파이프라인).
 */
@Component
@EnableConfigurationProperties(ChunkingProperties.class) // ChunkingProperties를 Bean으로 등록 + 생성자에 주입 가능하게 함
public class DocumentChunker {

    private final TokenTextSplitter splitter;

    public DocumentChunker(ChunkingProperties properties) {
        // TokenTextSplitter는 불변 객체라 요청마다 새로 만들지 않고, 생성자에서 한 번만 빌드해서 재사용
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(properties.chunkSize())
                .withMinChunkSizeChars(properties.minChunkSizeChars())
                .withMinChunkLengthToEmbed(properties.minChunkLengthToEmbed())
                .withMaxNumChunks(properties.maxNumChunks())
                .withKeepSeparator(true) // 줄바꿈 등 구분자를 청크 안에 남겨서 가독성/문맥 유지
                .build();
    }

    /**
     * @param documentId documents 테이블의 PK (버전 단위 문서 id)
     * @param title      검색 결과에서 바로 보여줄 수 있도록 metadata에 함께 저장 (documents 테이블 재조회 방지)
     * @param content    원본 전체 텍스트
     * @param category   nullable
     * @return vectorStore.add()에 바로 넘길 수 있는 chunk 목록
     */
    public List<Document> split(Long documentId, String title, String content, String category) {
        // chunk마다 공통으로 들어갈 metadata. TokenTextSplitter.apply()는 원본 Document의
        // metadata를 각 chunk에 그대로 복사해주기 때문에, 여기서 한 번만 만들어두면 됨.
        Map<String, Object> baseMetadata = new HashMap<>();
        baseMetadata.put("documentId", documentId); // 나중에 버전 전환 시 vectorStore.delete("documentId == N")로 찾기 위함
        baseMetadata.put("title", title);           // 검색 결과 표시용 (documents 테이블 재조회 없이 바로 응답 가능)
        if (category != null) {
            baseMetadata.put("category", category); // null이면 아예 안 넣음 (metadata에 null 값 넣는 것보다 키 자체를 생략하는 게 안전)
        }

        // Spring AI Document(원본 전체 텍스트 1개짜리)를 만들고, splitter에 리스트로 넘김
        // splitter.apply()는 List<Document> -> List<Document>라 단일 문서도 리스트로 감싸야 함
        Document source = new Document(content, baseMetadata);
        List<Document> chunks = splitter.apply(List.of(source));
        // 짧은 문서면 chunks.size() == 1로 나옴 (별도 분기 없이 동일 로직으로 처리됨)

        List<Document> result = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            // chunk.getMetadata()는 splitter가 원본에서 복사해준 것 (documentId, title, category 포함).
            // 여기에 chunkIndex/totalChunks를 "추가"해야 하는데, 반환된 Map이 불변(immutable)일 수 있어서
            // 방어적으로 새 HashMap에 복사한 뒤 put() 하는 것 (원본 Map을 직접 수정하면 예외가 날 수 있음)
            Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
            metadata.put("chunkIndex", i);              // 0부터 시작하는 chunk 순번
            metadata.put("totalChunks", chunks.size());  // 이 문서가 총 몇 개 chunk로 쪼개졌는지
            result.add(new Document(chunk.getText(), metadata));
        }
        return result;
    }
}