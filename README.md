# Spring AI 기반 Vector Search 문서 검색 시스템 프로젝트

## 1. 프로젝트 개요

Spring AI와 Vector DB(PostgreSQL + pgvector)를 활용하여, 문서를 등록하고 자연어 질문으로 의미 기반(semantic) 검색을 수행하는 REST API 시스템. 검색 결과를 컨텍스트로 활용한 RAG 기반 답변 생성 기능을 포함한다.

---

## 2. 기능 요구사항 및 설계

### 2.1 문서 등록

| 항목 | 내용 |
| --- | --- |
| 요구사항 | 문서를 Vector DB에 저장 |
| 설계 결정 | `documents`(원본/버전 이력) + `document_chunks`(검색 대상, embedding 보유) 테이블 이원화 |
| 근거 | Spring AI `VectorStore`는 chunk 단위 `Document` 객체로 동작. 원본과 검색 단위를 분리해야 버전 관리·이력 조회가 chunk 개수에 영향받지 않고 원자적으로 처리됨 |

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
| 설계 결정 | `document_chunks.metadata`를 JSONB로 저장 (`parent_doc_id`, `chunk_index`, `total_chunks`, `category` 등) |
| 근거 | 스키마 변경 없이 필드 확장 가능, category filtering(도전과제)에도 그대로 활용 |

### 2.5 AI 답변 생성 (RAG)

| 항목 | 내용 |
| --- | --- |
| 요구사항 | 검색된 문서를 컨텍스트로 LLM 답변 생성 |
| 설계 결정 | `SearchService`의 검색 로직을 `RagService`가 재사용 → top-K chunk를 프롬프트에 삽입 → `ChatClient` 호출 |
| LLM | GPT-4o-mini |

---

## 3. 기술 스택

| 영역 | 선택 | 비고 |
| --- | --- | --- |
| Language | Java 25 |  |
| Framework | Spring Boot 3.x |  |
| AI 연동 | Spring AI (`spring-ai-openai-spring-boot-starter`) | ChatClient, EmbeddingModel |
| Vector DB | PostgreSQL + pgvector (`spring-ai-pgvector-store-spring-boot-starter`) | Docker로 로컬 구동 |
| Embedding 모델 | OpenAI `text-embedding-3-small` (1536차원) |  |
| LLM | GPT-4o-mini | RAG 답변 생성용 |
| Chunking | Spring AI `TokenTextSplitter` | chunkSize/overlap 설정 |
| ORM | Spring Data JPA | 문서 메타 테이블 관리 |
| 검증 | Bean Validation | topK 상한 등 |
| 빌드 | Gradle |  |
| 인프라 | Docker Compose | postgres+pgvector 컨테이너 |

---

## 4. DB 스키마

**documents 테이블** — 원본 문서 단위, 버전 관리

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

**document_chunks 테이블** — 검색 대상, 벡터 저장

```sql
CREATE TABLE document_chunks (
id BIGSERIAL PRIMARY KEY,
document_id BIGINT NOT NULL REFERENCES documents(id),
chunk_index INT NOT NULL,
content TEXT NOT NULL,
embedding VECTOR(1536) NOT NULL,
metadata JSONB,                  -- category, chunk_index, total_chunks 등
is_active BOOLEAN NOT NULL DEFAULT TRUE,
created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_chunks_embedding ON document_chunks
USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX idx_chunks_document_id ON document_chunks(document_id);
```

---

## 4. API 설계

```
POST   /api/documents                      문서 등록 (title 기준 새 버전 생성 + chunk 분할 + embedding)
GET    /api/documents/{id}                 단건 조회 (특정 버전 하나)
GET    /api/documents/title/{title}                 문서의 현재 활성 버전 조회
GET    /api/documents/title/{title}/versions         버전 이력 목록 조회 (도전과제)
DELETE /api/documents/{id}                 삭제 (soft delete, is_active=false 처리, 도전과제)

GET    /api/search?query=&topK=&category=&threshold=  벡터 검색 (활성 버전 chunk 대상)
GET    /api/chat?query=                     RAG 기반 답변 생성
POST   /api/documents/upload                PDF 업로드 → 자동 chunk 분할 + embedding (도전과제)
```

---

## 5. 패키지 구조

```jsx
com.example.docsearch
├─ document/
│  ├─ Document.java              -- Entity (버전 이력 단위)
│  ├─ DocumentChunk.java         -- Entity (검색 대상, embedding 보유)
│  ├─ DocumentRepository.java
│  ├─ DocumentChunkRepository.java
│  ├─ DocumentController.java
│  ├─ DocumentService.java       -- 등록 시 버전 판단 + 이전 버전 비활성화 + chunk 분할 위임
│  └─ dto/
├─ search/
│  ├─ SearchController.java
│  └─ SearchService.java         -- VectorStore.similaritySearch 래핑, threshold/category 필터
├─ chat/
│  ├─ ChatController.java
│  └─ RagService.java            -- 검색결과를 context로 프롬프트 조립
├─ chunking/
│  ├─ DocumentChunker.java       -- TokenTextSplitter 래핑, chunk_index/total_chunks 부여
│  └─ ChunkingProperties.java    -- chunkSize, chunkOverlap 등 설정값
└─ config/
   ├─ VectorStoreConfig.java
   └─ EmbeddingConfig.java
```

---

## 6. 단계별 구현 로드맵

1. **인프라**: Docker Compose로 postgres+pgvector 컨테이너 세팅, `vector` extension 활성화, `documents`/`document_chunks` 테이블 DDL 적용
2. **등록 파이프라인 (버전 관리 + chunk 처음부터)**: `POST /documents` → title 기준 기존 활성 버전 조회 → 있으면 비활성화 → 새 `Document` row 생성 → `TokenTextSplitter`로 chunk 분할 → 각 chunk embedding 생성 → `document_chunks`에 저장. 짧은 문서도 chunk 1개로 통일 처리 (요구사항 1)
3. **검색**: `GET /search` → `VectorStore.similaritySearch()`로 활성 chunk 대상 검색, topK 파라미터 검증(기본5, 최대20) (요구사항 2~3)
4. **Metadata + 정렬**: chunk의 JSONB metadata(parent_doc_id, chunk_index, category) 응답 DTO에 포함, score 기준 정렬 확인 (요구사항 4)
5. **RAG 채팅**: 검색 결과 top-K chunk를 프롬프트 컨텍스트로 조립해 `ChatClient` 호출 (요구사항 5)
6. **도전과제**: 버전 이력 조회 API → soft delete → threshold 적용 → category filtering → PDF 업로드 순으로 확장
