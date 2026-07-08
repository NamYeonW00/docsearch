# Spring AI 기반 Vector Search 문서 검색 시스템 프로젝트

## 1. 프로젝트 개요

Spring AI와 Vector DB(PostgreSQL + pgvector)를 활용하여, 문서를 등록하고 자연어 질문으로 의미 기반(semantic) 검색을 수행하는 REST API 시스템. 검색 결과를 컨텍스트로 활용한 RAG 기반 답변 생성 기능을 포함한다. 백엔드 API와 별도로 React 기반 프론트엔드를 모노레포 구조로 함께 개발한다.

---

## 2. 기능 요구사항 및 설계

### 2.1 문서 등록

| 항목 | 내용 |
| --- | --- |
| 요구사항 | 문서를 Vector DB에 저장 |
| 설계 결정 | `documents`(원본/버전 이력, JPA 관리) + `document_chunks`(검색 대상, Spring AI 자동 관리) 테이블 이원화 |
| 근거 | Spring AI `PgVectorStore`는 컬럼 구조(`id`/`content`/`metadata`/`embedding`)가 고정이라 `document_id`, `chunk_index` 같은 커스텀 컬럼을 가질 수 없음. 버전이 바뀔 때마다 이전 chunk를 `vectorStore.delete(filterExpression)`으로 지우고 새로 `add()`하는 방식이라, 검색 대상 테이블은 항상 "현재 활성 버전"만 담게 되어 별도 이력 컬럼(`is_active` 등)이 불필요함 |

### 2.2 문서 검색

| 항목 | 내용 |
| --- | --- |
| 요구사항 | 자연어 질문 → 의미적으로 유사한 문서 반환 |
| 설계 결정 | pgvector cosine similarity 기반 검색, `VectorStore.similaritySearch()` 활용 |
| 정렬 기준 | cosine similarity score 내림차순 (Spring AI가 자동 처리) |

### 2.3 Top K 검색

| 항목 | 내용 |
| --- | --- |
| 요구사항 | 검색 개수 지정 가능 |
| 설계 결정 | `topK` 쿼리 파라미터, 기본값 5, 최대값 20 (`@Max(20)` Bean Validation으로 상한 강제) |
| 근거 | 무제한 허용 시 DB 부하 및 RAG 컨텍스트 낭비 발생 |

### 2.4 Metadata 저장 및 반환

| 항목 | 내용 |
| --- | --- |
| 요구사항 | 검색 결과에 metadata 포함 |
| 설계 결정 | `document_chunks.metadata`(Spring AI 관리, JSON)에 `documentId`, `chunkIndex`, `title`, `category` 등을 담아 저장 |
| 근거 | 실컬럼이 아니라 JSON이라 스키마 변경 없이 필드 확장 가능, category filtering(도전과제)에도 그대로 활용 |

### 2.5 AI 답변 생성 (RAG)

| 항목 | 내용 |
| --- | --- |
| 요구사항 | 검색된 문서를 컨텍스트로 LLM 답변 생성 |
| 설계 결정 | `SearchService`의 검색 로직을 `RagService`가 재사용 → top-K chunk를 프롬프트에 삽입 → `ChatClient` 호출 |
| LLM | GPT-4o-mini |

---

## 3. 핵심 설계 고민에 대한 결론

| 질문 | 결론 | 근거 |
| --- | --- | --- |
| 문서/chunk를 어떻게 저장할 것인가? | `documents`(JPA, 버전 이력) + `document_chunks`(Spring AI 자동 관리, 검색 전용) 분리 | Spring AI 고정 스키마 제약과 이력 관리 책임을 분리 |
| Embedding은 언제 생성하는가? | 문서 등록 시점(동기), `vectorStore.add()` 호출 시 Spring AI가 내부적으로 embedding 계산 | 구현 단순성과 즉시 검색 가능성 확보 |
| 검색 개수는 어떻게 설정하는가? | `topK` 파라미터, 기본 5 / 최대 20 | DB 부하 및 컨텍스트 낭비 방지 |
| 검색 결과 정렬 기준은? | cosine similarity 내림차순, threshold 미달도 포함 | 빈 배열보다 낮은 score로 "관련 문서 없음"을 명확히 전달 |
| 동일 문서 재등록 시 처리는? | **버전 관리(Versioning)** — 기존 활성 버전 비활성화(`documents.is_active=false`) + 이전 chunk를 `vectorStore.delete(filterExpression)`로 삭제 + 새 버전 등록 | 이력 추적이 목적. 재등록 시 항상 재chunking + 재embedding |
| 문서는 chunk로 나누는가? | 처음부터 chunk 단위로 저장 (짧은 문서도 chunk 1개로 통일) | 로직 분기 없이 일관된 등록 파이프라인 유지 |
| chunk 이력을 별도 테이블(JPA)로 관리할 것인가? | **아니오.** `document_chunks`는 Spring AI가 전담 관리 | 이력 관리는 `documents` 테이블이 이미 책임지고 있고, 별도 JPA 테이블을 두면 Spring AI가 관리하는 테이블과 정보가 두 곳에 나뉘어 동기화가 어긋날 위험만 커짐 |

---

## 4. 기술 스택

| 영역 | 선택 | 비고 |
| --- | --- | --- |
| Language | Java 25 (LTS) | Spring Boot 4.x가 first-class 지원 |
| Framework | **Spring Boot 4.x** (Spring Framework 7 기반) | |
| AI 연동 | Spring AI (`spring-ai-starter-model-openai`) | ChatClient, EmbeddingModel |
| Vector DB | PostgreSQL + pgvector (`spring-ai-starter-vector-store-pgvector`) | Docker로 로컬 구동 |
| Embedding 모델 | OpenAI `text-embedding-3-small` (1536차원) | |
| LLM | GPT-4o-mini | RAG 답변 생성용 |
| Chunking | Spring AI `TokenTextSplitter` | chunkSize/overlap 설정 |
| ORM | Spring Data JPA | `documents` 테이블만 관리 (`document_chunks`는 Spring AI가 자동 관리) |
| 보일러플레이트 제거 | Lombok | JPA 엔티티에는 `@Data` 대신 `@Getter/@Setter/@EqualsAndHashCode(of="id")` 선택적으로 사용 |
| 검증 | Bean Validation | topK 상한 등 |
| 빌드 | Gradle | |
| 인프라 | Docker Compose | postgres+pgvector 컨테이너 |
| 프론트엔드 | React + Vite | 모노레포 내 별도 폴더, dev 프록시로 CORS 우회 |

---

## 5. DB 스키마

### documents 테이블 — 원본 문서 단위, 버전 관리 (JPA 직접 관리)

```sql
CREATE TABLE documents (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,          -- 원본 전체 텍스트 (chunk 분할 전)
    version INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    category VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_documents_title_active ON documents(title, is_active);
```

### document_chunks 테이블 — 검색 대상, 벡터 저장 (Spring AI 자동 생성, DDL로 만들지 않음)

애플리케이션 최초 기동 시 `spring.ai.vectorstore.pgvector.initialize-schema=true` 설정으로 자동 생성됨. 컬럼 구조는 Spring AI가 고정:

```
id          uuid PRIMARY KEY
content     text
metadata    json     -- documentId, chunkIndex, title, category 등을 이 안에 저장
embedding   vector(1536)
```

인덱스는 HNSW(기본값)를 사용한다. IVFFlat과 달리 데이터가 적은 초기 상태에서도 인덱스 품질 저하가 없어 빈 테이블에서 바로 생성해도 무방하다.

---

## 6. API 설계

```
POST   /api/documents                          문서 등록 (title 기준 새 버전 생성 + chunk 분할 + embedding)
GET    /api/documents/{id}                     단건 조회 (특정 버전)
GET    /api/documents/title/{title}            현재 활성 버전 조회
GET    /api/documents/title/{title}/versions    버전 이력 목록 조회 (도전과제)
DELETE /api/documents/{id}                     비활성화 (soft delete, 도전과제)

GET    /api/search?query=&topK=&category=&threshold=   벡터 검색 (document_chunks 대상)
GET    /api/chat?query=                         RAG 기반 답변 생성
POST   /api/documents/upload                    PDF 업로드 (도전과제)
```

---

## 7. 프로젝트 구조 (모노레포)

백엔드와 프론트엔드를 한 저장소 안에 폴더로 분리한다. `docker-compose.yml`은 둘이 공유하는 인프라(DB)이므로 루트에 둔다.

```
docsearch/
├─ backend/                         Spring Boot 프로젝트 (포트 8080)
│  ├─ build.gradle
│  ├─ settings.gradle
│  └─ src/main/java/com/example/docsearch/
│     ├─ document/
│     │  ├─ Document.java              -- Entity (버전 이력 단위)
│     │  ├─ DocumentRepository.java
│     │  ├─ DocumentController.java
│     │  ├─ DocumentService.java       -- 버전 판단 + chunk 분할 위임 + vectorStore.add/delete 호출
│     │  └─ dto/
│     ├─ search/
│     │  ├─ SearchController.java
│     │  └─ SearchService.java         -- VectorStore.similaritySearch 래핑, threshold/category 필터
│     ├─ chat/
│     │  ├─ ChatController.java
│     │  └─ RagService.java            -- 검색결과를 context로 프롬프트 조립 (SearchService 재사용)
│     ├─ chunking/
│     │  ├─ DocumentChunker.java       -- TokenTextSplitter 래핑
│     │  └─ ChunkingProperties.java    -- chunkSize, chunkOverlap 설정값
│     └─ config/
│        └─ EmbeddingConfig.java
│
├─ frontend/                        React + Vite 프로젝트 (포트 5173)
│  ├─ package.json
│  ├─ vite.config.js                -- /api → localhost:8080 프록시 설정 (dev CORS 우회)
│  └─ src/
│
├─ docker/
│  └─ init-db/
│     ├─ 01-init-extension.sql
│     └─ 02-init-schema.sql         -- documents 테이블만 생성 (document_chunks는 앱이 생성)
├─ docker-compose.yml               postgres+pgvector (포트 5432)
└─ .env.example
```

---

## 8. 단계별 구현 로드맵

1. **인프라**: Docker Compose로 postgres+pgvector 컨테이너 세팅, `vector` extension 활성화, `documents` 테이블 DDL 적용 ✅
2. **프로젝트 생성**: Spring Initializr로 backend 생성(Lombok 포함), Vite로 frontend 스캐폴딩, 모노레포 구조로 배치 ✅
3. **엔티티/리포지토리**: `Document` 엔티티 + `DocumentRepository` (버전 조회) ✅
4. **등록 파이프라인**: title 기준 버전 판단 → 기존 버전 비활성화 + 이전 chunk 삭제 → 신규 버전 생성 → chunk 분할 → `vectorStore.add()` (요구사항 1)
5. **검색**: `similaritySearch()` 연동, topK 검증 (요구사항 2~3)
6. **Metadata + 정렬**: metadata 응답 포함, score 정렬 확인 (요구사항 4)
7. **RAG 채팅**: top-K chunk 컨텍스트 조립 + `ChatClient` 호출 (요구사항 5)
8. **프론트엔드 화면**: 문서 등록 폼, 검색 화면, 채팅 화면 구현
9. **도전과제**: 버전 이력 조회 → soft delete → threshold → category filtering → PDF 업로드 순 확장

---

## 9. 도전 과제 (Optional)

- [ ] 버전 이력 조회 API (`GET /documents/title/{title}/versions`)
- [ ] 문서 삭제 (soft delete)
- [ ] Metadata Filtering (category별 검색)
- [ ] Score Threshold 적용
- [ ] PDF 파일 업로드 후 자동 임베딩
- [x] Chunk 단위 분할 저장 (필수 파이프라인에 처음부터 포함)
