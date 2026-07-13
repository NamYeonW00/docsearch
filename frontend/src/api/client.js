// 백엔드 API 호출을 한 곳에 모아둔 파일.
// 경로가 절대 URL(http://localhost:8080/...)이 아니라 "/api/..."인 이유:
// vite.config.js의 proxy 설정이 /api로 시작하는 요청을 자동으로 백엔드(8080)로 전달해준다.
// 그래서 컴포넌트 쪽 코드는 백엔드 포트 번호를 몰라도 되고, 배포 환경이 바뀌어도 이 파일만 안 건드려도 된다.

const BASE_URL = "/api";

// 응답이 실패(4xx/5xx)면 서버가 내려준 에러 메시지를 최대한 추출해서 던진다.
// 우리 백엔드는 실패 시 JSON({"error": "..."}) 아니면 순수 텍스트를 내려주므로 둘 다 시도해본다.
async function handleResponse(response) {
    if (response.ok) {
        return response.json();
    }
    const text = await response.text();
    try {
        const json = JSON.parse(text);
        throw new Error(json.message || json.error || text);
    } catch {
        throw new Error(text || `요청 실패 (status ${response.status})`);
    }
}

// POST /api/documents - 문서 등록(재등록 시 새 버전 생성)
export async function registerDocument({ title, content, category }) {
    const response = await fetch(`${BASE_URL}/documents`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ title, content, category: category || null }),
    });
    return handleResponse(response);
}

// GET /api/search?query=&topK=&category=&threshold=
export async function searchDocuments({ query, topK, category, threshold }) {
    const params = new URLSearchParams({ query });
    if (topK) params.set("topK", topK);
    if (category) params.set("category", category);
    if (threshold !== undefined && threshold !== null && threshold !== "") {
        params.set("threshold", threshold);
    }
    const response = await fetch(`${BASE_URL}/search?${params.toString()}`);
    return handleResponse(response);
}

// GET /api/documents/{id} - 특정 버전 하나를 id(PK)로 직접 조회
export async function getDocumentById(id) {
    const response = await fetch(`${BASE_URL}/documents/${encodeURIComponent(id)}`);
    return handleResponse(response);
}

// GET /api/documents/title/{title} - title로 현재 활성 버전 조회
export async function getActiveDocumentByTitle(title) {
    const response = await fetch(`${BASE_URL}/documents/title/${encodeURIComponent(title)}`);
    return handleResponse(response);
}

// GET /api/documents/title/{title}/versions - 해당 title의 버전 이력 전체(최신순)
export async function getDocumentVersions(title) {
    const response = await fetch(`${BASE_URL}/documents/title/${encodeURIComponent(title)}/versions`);
    return handleResponse(response);
}

// DELETE /api/documents/{id} - 특정 버전 soft delete(비활성화). 비활성화된 문서를 반환.
export async function deactivateDocument(id) {
    const response = await fetch(`${BASE_URL}/documents/${encodeURIComponent(id)}`, {
        method: "DELETE",
    });
    return handleResponse(response);
}

// GET /api/chat?query=
export async function askChat(query) {
    const params = new URLSearchParams({ query });
    const response = await fetch(`${BASE_URL}/chat?${params.toString()}`);
    return handleResponse(response);
}