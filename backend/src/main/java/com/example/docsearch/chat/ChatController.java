package com.example.docsearch.chat;

import com.example.docsearch.chat.dto.ChatResponseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    // 채팅은 검색과 달리 사용자가 topK/category/threshold를 직접 고를 이유가 약해서
    // (그냥 자연어로 묻는 행위지, 조건을 좁혀 찾는 행위가 아님) API로 노출하지 않고 내부 기본값으로 고정.
    // RagService.answer()가 이 파라미터들을 받는 시그니처는 유지 - 필요해지면(테스트 등) 내부에서는 유연하게 쓸 수 있음
    private static final int DEFAULT_TOP_K = 5;

    private final RagService ragService;

    public ChatController(RagService ragService) {
        this.ragService = ragService;
    }

    // GET /api/chat?query=Spring AI가 무엇인가요?
    @GetMapping
    public ChatResponseDto chat(@RequestParam String query) {
        return ragService.answer(query, DEFAULT_TOP_K, null, null);
    }
}
