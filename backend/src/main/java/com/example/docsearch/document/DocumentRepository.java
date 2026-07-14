package com.example.docsearch.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    /**
     * title 기준 현재 활성 버전 조회.
     * title에 대해 active=true인 row는 항상 최대 1개만 존재해야 한다
     * (등록 파이프라인에서 새 버전 생성 전 기존 활성 버전을 비활성화하기 때문).
     */
    Optional<Document> findByTitleAndActiveTrue(String title);

    /**
     * title 기준 버전 이력 전체 조회 (최신 버전이 먼저 오도록 정렬).
     */
    List<Document> findAllByTitleOrderByVersionDesc(String title);

    /**
     * 제목 부분 일치(대소문자 무시) 활성 버전 조회.
     * "Spring"만 입력해도 "Spring AI 소개"가 걸리도록, 조회 화면의 제목 검색에 사용한다.
     * 활성 버전은 title당 최대 1개이므로 결과는 title별로 최대 1건씩(= 제목 목록)이 된다.
     */
    List<Document> findByActiveTrueAndTitleContainingIgnoreCaseOrderByTitleAsc(String title);
}