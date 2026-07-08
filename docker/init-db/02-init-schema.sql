-- documents: 버전 이력 관리
CREATE TABLE IF NOT EXISTS documents (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    category VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_documents_title_active
    ON documents(title, is_active);

-- document_chunks 테이블은 두지 않는다.
-- 이유: 버전이 바뀔 때마다 vector_store에서 이전 버전 chunk를
-- 삭제(vectorStore.delete(filterExpression))하고 새 chunk를 add() 하는 방식이라,
-- vector_store에는 항상 "현재 활성 버전의 chunk"만 존재하게 된다.
-- 즉 documents(버전/이력) + vector_store(현재 검색 대상) 두 테이블만으로
-- 필요한 모든 기능(등록/검색/버전전환)을 커버할 수 있어 별도 이력 테이블이 불필요하다.

-- document_chunks 테이블(=Spring AI의 vector_store 역할)은 여기서 만들지 않는다.
-- spring.ai.vectorstore.pgvector.initialize-schema=true + table-name=document_chunks 설정으로
-- 애플리케이션 최초 기동 시 Spring AI가 자동으로 생성한다.
-- 컬럼은 id(uuid) / content(text) / metadata(json) / embedding(vector) 4개로 고정이며,
-- documentId·chunkIndex·category 등은 모두 metadata JSON 안에 저장된다 (실제 컬럼 아님).
