package com.example.docsearch.pdf;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 업로드된 PDF 바이트에서 순수 텍스트를 추출한다.
 *
 * Spring AI의 {@link PagePdfDocumentReader}(내부적으로 Apache PDFBox 사용)로 페이지별
 * {@link Document}를 읽어와 본문만 이어붙여 하나의 문자열로 반환한다. 이렇게 얻은 텍스트는
 * 기존 텍스트 문서와 완전히 동일하게 DocumentChunker → VectorStore 파이프라인을 탄다.
 *
 * chunk 분할은 여기서 하지 않는다 (DocumentChunker의 책임). 이 컴포넌트는 "PDF → 텍스트"
 * 한 단계만 담당해 책임을 분리한다.
 */
@Slf4j
@Component
public class PdfTextExtractor {

    /**
     * @param pdfBytes     업로드된 PDF 원본 바이트
     * @param filenameHint 로그 표시용 파일명 (추출 로직에는 영향 없음, null 허용)
     * @return 페이지 본문을 줄바꿈으로 이어붙인 전체 텍스트
     * @throws PdfProcessingException PDF 파싱 실패 또는 추출 가능한 텍스트가 전혀 없는 경우
     */
    public String extractText(byte[] pdfBytes, String filenameHint) {
        // PagePdfDocumentReader는 Resource를 여러 번 읽을 수 있으므로, 스트림이 아닌
        // 메모리에 올린 ByteArrayResource를 넘긴다 (MultipartFile 스트림 재사용 문제 회피).
        ByteArrayResource resource = new ByteArrayResource(pdfBytes);

        List<Document> pages;
        try {
            PagePdfDocumentReader reader = new PagePdfDocumentReader(resource, PdfDocumentReaderConfig.defaultConfig());
            pages = reader.get();
        } catch (Exception e) {
            // 손상된 파일, 암호화된 PDF, PDF가 아닌 바이트 등은 모두 "잘못된 입력"으로 취급한다.
            log.warn("PDF 텍스트 추출 실패 - filename={}", filenameHint, e);
            throw new PdfProcessingException("PDF를 읽을 수 없습니다. 파일이 손상되었거나 올바른 PDF가 아닐 수 있습니다.", e);
        }

        String rawText = pages.stream()
                .map(Document::getText)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining("\n"));

        String text = normalizeWhitespace(rawText);

        if (text.isBlank()) {
            // 텍스트 레이어가 없는 스캔(이미지) PDF 등. 임베딩할 내용이 없으므로 명확히 안내한다.
            throw new PdfProcessingException("PDF에서 추출할 텍스트가 없습니다. 스캔 이미지 형태의 PDF는 지원하지 않습니다.");
        }

        log.info("PDF 텍스트 추출 완료 - filename={}, pageCount={}, textLength={}",
                filenameHint, pages.size(), text.length());
        return text;
    }

    /**
     * PDF 텍스트 추출 결과의 과도한 공백을 정리한다.
     *
     * PDFBox는 글자의 화면 좌표를 기준으로 텍스트를 뽑기 때문에, 원본 문서의 정렬/들여쓰기가
     * "단어 사이의 여러 개의 공백"이나 "줄 앞의 긴 들여쓰기"로 나타난다(특히 한글 문서에서 두드러짐).
     * 이 공백들은 의미가 없을 뿐 아니라 chunk 분할·임베딩 품질과 검색 결과 가독성을 떨어뜨리므로 제거한다.
     *
     * 단어(예: "프로젝트") 자체는 쪼개지지 않고 단어 사이 간격만 벌어지는 형태라, 연속 공백을 1칸으로
     * 줄이면 원래의 단어 경계가 그대로 보존된다.
     */
    private String normalizeWhitespace(String text) {
        return text
                .replace("\r\n", "\n").replace("\r", "\n")   // 윈도우/맥 개행을 \n으로 통일
                // 가로 공백(스페이스·탭·전각공백 U+3000·NBSP 등 \p{Zs}) 연속 → 스페이스 1개
                .replaceAll("[\\t\\x0B\\f\\p{Zs}]+", " ")
                .replaceAll("(?m)^ +", "")   // 각 줄 앞의 들여쓰기 제거
                .replaceAll("(?m) +$", "")   // 각 줄 뒤의 공백 제거
                .replaceAll("\\n{3,}", "\n\n") // 빈 줄 3개 이상 연속 → 2개로 축소
                .strip();
    }
}
