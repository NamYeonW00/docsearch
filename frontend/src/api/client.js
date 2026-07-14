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

    // JSON이면 message/error 필드만 추출, 아니면 raw text를 그대로 사용.
    // throw는 try 밖에서 한 번만 한다 - try 안에서 던지면 파싱 성공 시 던진 에러가
    // 바로 아래 catch에 다시 잡혀서 추출한 메시지가 버려지기 때문.
    let message = text || `요청 실패 (status ${response.status})`;
    try {
        const json = JSON.parse(text);
        message = json.message || json.error || text;
    } catch {
        // 순수 텍스트 응답이면 파싱 실패가 정상. text를 그대로 둔다.
    }

    throw new Error(message);
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

// POST /api/documents/upload - PDF 업로드 후 텍스트 추출 → chunk 분할 + 임베딩 자동 처리
// FormData로 보낼 때는 Content-Type 헤더를 직접 지정하지 않는다 - 브라우저가 multipart 경계(boundary)를
// 포함한 헤더를 자동으로 붙여주기 때문. 수동으로 지정하면 오히려 경계가 빠져서 파싱이 깨진다.
export async function uploadDocumentPdf({ file, title, category }) {
    const formData = new FormData();
    formData.append("file", file);
    // title/category는 선택값이라 값이 있을 때만 담는다 (빈 문자열은 백엔드에서 파일명/ null로 보정됨).
    if (title) formData.append("title", title);
    if (category) formData.append("category", category);

    const response = await fetch(`${BASE_URL}/documents/upload`, {
        method: "POST",
        body: formData,
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

// GET /api/documents?title= - 제목 부분 일치(대소문자 무시)로 활성 문서 목록 조회.
// 정확한 제목을 몰라도("Spring"만) 매칭되는 문서들을 목록으로 받는다.
export async function searchDocumentsByTitle(title) {
    const params = new URLSearchParams({ title });
    const response = await fetch(`${BASE_URL}/documents?${params.toString()}`);
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