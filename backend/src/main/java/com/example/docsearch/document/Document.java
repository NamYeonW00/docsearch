package com.example.docsearch.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 문서의 버전 이력을 관리하는 엔티티.
 * 같은 title로 재등록되면 기존 활성 row는 active=false 처리되고,
 * 새로운 version의 row가 생성된다 (물리 삭제/덮어쓰기 없음).
 */
@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor    // JPA 스펙상 기본 생성자가 반드시 있어야 함 (Hibernate가 리플렉션으로 객체 생성)
@EqualsAndHashCode(of = "id") // JPA 엔티티는 id 기준으로만 동등성 비교
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB의 BIGSERIAL(auto-increment)에 위임
    private Long id;

    @Column(nullable = false)
    private String title; // 버전이 달라도 같은 문서군이면 title은 동일 (버전 조회 시 이 값으로 grouping)

    @Column(nullable = false, columnDefinition = "TEXT") // VARCHAR 길이 제한 없이 긴 원문 저장
    private String content;

    @Column(nullable = false)
    private Integer version; // 1부터 시작, 재등록마다 +1

    @Column(name = "is_active", nullable = false)
    private Boolean active; // true면 현재 검색/조회 대상, false면 과거 버전(이력 보존용)

    private String category; // nullable. 필수 아님 - 카테고리 필터링(도전과제)에 사용 예정

    @Column(name = "created_at", nullable = false, updatable = false) // 생성 후 변경 불가
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // INSERT 직전에 자동 호출됨 (JPA 콜백) - 생성/수정 시각을 코드에서 직접 안 채워도 되게 함
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // UPDATE 직전에 자동 호출됨 (예: deactivate() 호출 후 flush될 때)
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 새 버전을 생성하는 정적 팩토리 메서드.
     * new Document() + setter 여러 번 호출하는 대신, "새 버전 생성"이라는 의도가
     * 메서드 이름에 드러나도록 함 (생성자를 public으로 열어두지 않은 이유이기도 함).
     *
     * @param previousVersion 기존 활성 버전의 version 값. 최초 등록이면 null.
     */
    public static Document createVersion(String title, String content, String category, Integer previousVersion) {
        Document document = new Document();
        document.title = title;
        document.content = content;
        document.category = category;
        document.version = (previousVersion == null) ? 1 : previousVersion + 1; // null이면 1부터 시작
        document.active = true; // 새로 만드는 버전은 항상 활성 상태로 시작
        return document;
    }

    // 재등록 시 이전 버전을 비활성화할 때 호출. is_active=false로 바뀌고 @PreUpdate가 updated_at을 갱신함
    public void deactivate() {
        this.active = false;
    }
}
