package com.example.docsearch.chunking;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TokenTextSplitter에 전달할 chunk 분할 설정값.
 *
 * 주의: overlap(청크 간 겹침) 설정은 여기 없음. Spring AI TokenTextSplitter가
 * 아직 overlap 기능을 지원하지 않기 때문 (관련 기능 요청이 spring-ai GitHub에 open 상태).
 * 필요해지면 직접 TextSplitter를 구현해야 함.
 */
@ConfigurationProperties(prefix = "app.chunking")
public record ChunkingProperties(
        int chunkSize,             // 목표 청크 크기 (토큰 개수 기준). 이 값을 넘으면 분할 시도
        int minChunkSizeChars,     // 청크의 최소 글자 수. 이보다 작은 조각은 앞/뒤 청크에 합쳐짐
        int minChunkLengthToEmbed, // 이보다 짧은 청크는 임베딩 대상에서 아예 제외 (의미 없는 파편 방지)
        int maxNumChunks           // 문서 하나당 생성 가능한 최대 청크 개수 (폭주 방지 안전장치)
) {
    // application.yml에 값이 없을 때를 대비한 기본값 (Spring AI TokenTextSplitter 기본값과 동일)
    public ChunkingProperties {
        if (chunkSize <= 0) {
            chunkSize = 800;
        }
        if (minChunkSizeChars <= 0) {
            minChunkSizeChars = 350;
        }
        if (minChunkLengthToEmbed <= 0) {
            minChunkLengthToEmbed = 5;
        }
        if (maxNumChunks <= 0) {
            maxNumChunks = 10000;
        }
    }
}