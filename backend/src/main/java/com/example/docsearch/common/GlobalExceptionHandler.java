package com.example.docsearch.common;

import com.example.docsearch.chat.AiServiceException;
import com.example.docsearch.pdf.PdfProcessingException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 전역 예외 처리기. 이전에는 각 컨트롤러(@ExceptionHandler)에 흩어져 있던 예외 매핑을 한곳에 모았다.
 *
 * MaxUploadSizeExceededException은 컨트롤러 메서드 진입 전(멀티파트 파싱 시점)에 던져지기 때문에
 * 컨트롤러 로컬 @ExceptionHandler로는 (resolution 모드에 따라) 잡지 못할 수 있어 전역 처리가 필수다.
 * 나머지 예외들도 여러 컨트롤러가 생긴 지금 중복을 없애기 위해 함께 이관했다.
 *
 * 모든 핸들러는 예외 메시지를 그대로 body로 내려준다 - 프론트가 사용자에게 바로 노출할 수 있도록.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // DocumentService 등에서 "문서를 찾을 수 없습니다" 등으로 던지는 IllegalArgumentException을
    // 500이 아니라 404(Not Found)로 변환한다.
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(IllegalArgumentException e) {
        return e.getMessage();
    }

    // PDF 업로드 관련 사용자 입력 문제(비-PDF, 빈 파일, 텍스트 없음, 손상된 파일 등)는 400으로 응답한다.
    @ExceptionHandler(PdfProcessingException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handlePdfProcessing(PdfProcessingException e) {
        return e.getMessage();
    }

    // @Validated가 붙은 컨트롤러의 @RequestParam 검증(@NotBlank, @Min, @Max 등) 위반은
    // ConstraintViolationException으로 던져진다. 기본 처리 시 500으로 나가므로 명시적으로 400에 매핑한다.
    // 예: topK=21 요청 → @Max(20) 위반 → 400. (요구사항 명세: topK 20 초과 시 400)
    // 메시지엔 검증 애노테이션에 지정한 사용자 친화적 문구(message)만 뽑아 전달한다.
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleConstraintViolation(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        return detail.isBlank() ? "잘못된 요청입니다." : detail;
    }

    // 업로드 파일이 max-file-size(또는 max-request-size)를 초과하면 멀티파트 파싱 단계에서 이 예외가 던져진다.
    // "서버 오류"(500)가 아니라 "요청이 너무 큼"(413 Content Too Large)으로 명확히 응답한다.
    // (PAYLOAD_TOO_LARGE는 RFC 9110의 명칭 변경으로 deprecated → CONTENT_TOO_LARGE 사용)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
    public String handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return "업로드 가능한 파일 크기를 초과했습니다. (최대 20MB)";
    }

    // LLM 호출 실패/타임아웃은 "서버 버그"가 아니라 "외부 서비스 일시 장애"에 가까우므로
    // 500 대신 503(Service Unavailable)으로 의미를 명확히 구분해서 응답한다.
    @ExceptionHandler(AiServiceException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String handleAiServiceException(AiServiceException e) {
        return e.getMessage();
    }
}
