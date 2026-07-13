import { useState } from "react";
import { registerDocument } from "../api/client";

const initialForm = { title: "", content: "", category: "" };

export default function DocumentForm() {
    const [form, setForm] = useState(initialForm);
    const [loading, setLoading] = useState(false);
    const [result, setResult] = useState(null); // 성공 시 서버 응답(DocumentResponse) 저장
    const [error, setError] = useState(null);

    function updateField(key, value) {
        setForm((prev) => ({ ...prev, [key]: value }));
    }

    async function handleSubmit(e) {
        e.preventDefault(); // 폼 기본 동작(페이지 새로고침)을 막음 - SPA에서는 우리가 직접 fetch로 처리하니까
        setLoading(true);
        setError(null);
        setResult(null);

        try {
            const response = await registerDocument(form);
            setResult(response);
            // content/category는 남겨두고 title만 비워서, 같은 문서를 재등록하는 흐름을 편하게 해줌
            // (title이 그대로면 재등록 시 바로 버전이 올라가는 걸 눈으로 확인하기도 편함 - 근데 여기선 전체 초기화가 더 명확해서 전체 초기화로 감)
            setForm(initialForm);
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    }

    return (
        <div>
            <div className="panel-header">
                <h1>문서 등록</h1>
                <p>같은 제목으로 다시 등록하면 새 버전으로 저장되고, 이전 버전은 검색 대상에서 제외돼요.</p>
            </div>

            {result && (
                <div className="status-message status-success">
                    "{result.title}" 등록 완료 — version {result.version}
                    {result.version > 1 ? " (이전 버전은 비활성화되었습니다)" : ""}
                </div>
            )}
            {error && <div className="status-message status-error">등록 실패: {error}</div>}

            <form className="card" onSubmit={handleSubmit}>
                <div className="field">
                    <label htmlFor="title">제목</label>
                    <input
                        id="title"
                        className="input"
                        type="text"
                        value={form.title}
                        onChange={(e) => updateField("title", e.target.value)}
                        placeholder="예: Spring AI 소개"
                        required
                    />
                </div>

                <div className="field">
                    <label htmlFor="content">내용</label>
                    <textarea
                        id="content"
                        className="textarea"
                        value={form.content}
                        onChange={(e) => updateField("content", e.target.value)}
                        placeholder="문서 본문을 입력하세요. 길면 자동으로 여러 조각(chunk)으로 나뉘어 저장돼요."
                        required
                    />
                </div>

                <div className="field">
                    <label htmlFor="category">카테고리 (선택)</label>
                    <input
                        id="category"
                        className="input"
                        type="text"
                        value={form.category}
                        onChange={(e) => updateField("category", e.target.value)}
                        placeholder="예: framework"
                    />
                </div>

                <button className="btn btn-primary" type="submit" disabled={loading}>
                    {loading ? "등록 중..." : "문서 등록"}
                </button>
            </form>
        </div>
    );
}