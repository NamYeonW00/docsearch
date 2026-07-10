package com.example.docsearch.chat;

import com.example.docsearch.chat.dto.ChatResponseDto;
import com.example.docsearch.search.SearchService;
import com.example.docsearch.search.dto.SearchResultDto;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagService {

    // LLM에게 "컨텍스트 밖 지식으로 답하지 말라"는 규칙을 강제하는 시스템 프롬프트.
    // {context} 자리에 검색된 chunk들의 본문을 채워 넣는다.
    private static final String SYSTEM_TEMPLATE = """
            당신은 사내 문서 검색 도우미입니다. 아래 컨텍스트에 있는 정보만 사용해서 답변하세요.
            컨텍스트에 질문과 관련된 내용이 없으면, 모른다고 솔직하게 답하고 추측하지 마세요.

            컨텍스트:
            %s
            """;

    private final ChatClient chatClient;
    private final SearchService searchService;

    // ChatClient.Builder는 spring-ai-starter-model-openai가 자동 등록해주는 Bean.
    // 생성자에서 build()까지 미리 해두고, 완성된 ChatClient를 필드로 재사용한다.
    public RagService(ChatClient.Builder chatClientBuilder, SearchService searchService) {
        this.chatClient = chatClientBuilder.build();
        this.searchService = searchService;
    }

    public ChatResponseDto answer(String query, int topK, String category, Double threshold) {
        List<SearchResultDto> sources  =searchService.search(query, topK, category, threshold);

        if (sources.isEmpty()) {
            // 컨텍스트가 없는 상태로 LLM을 호출하면 근거 없는 답변(hallucination) 위험이 크고
            // 비용만 발생하므로, 이 경우엔 LLM 호출 자체를 생략한다.
            return new ChatResponseDto("관련된 문서를 찾지 못했습니다. 질문을 다르게 표현해보시거나, 관련 문서를 먼저 등록해주세요.", List.of());
        }

        String context = buildContext(sources);
        String systemPrompt = SYSTEM_TEMPLATE.formatted(context);

        String answer = chatClient.prompt()
                .system(systemPrompt)
                .user(query)
                .call()
                .content();

        return new ChatResponseDto(answer, sources);
    }

    // 검색된 chunk들을 "[제목] 내용" 형태로 이어붙여 하나의 컨텍스트 텍스트로 만든다.
    // 제목을 같이 넣어주는 이유: chunk 본문만 나열하면 LLM이 "이 문장이 어느 문서 얘기인지" 구분하기 어려워짐
    private String buildContext(List<SearchResultDto> sources) {
        StringBuilder sb = new StringBuilder();
        for (SearchResultDto source : sources) {
            sb.append("[").append(source.title()).append("]\n")
                    .append(source.content()).append("\n\n");
        }
        return sb.toString();
    }
}