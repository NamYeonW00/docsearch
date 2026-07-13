package com.example.docsearch.pdf;

/**
 * PDF 업로드 처리 중 발생하는 "사용자 입력 문제"를 나타내는 예외.
 * 예: 파일이 비어있음, PDF가 아님, 손상된 PDF, 텍스트를 추출할 수 없는 스캔 이미지 PDF 등.
 *
 * 서버 내부 오류(500)가 아니라 잘못된 요청(400)으로 응답하기 위해 별도 타입으로 분리했다.
 * (DocumentController에서 이 예외를 400 Bad Request로 매핑한다.)
 */
public class PdfProcessingException extends RuntimeException {

    public PdfProcessingException(String message) {
        super(message);
    }

    public PdfProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
