import { useState } from "react";
import { registerDocument, uploadDocumentPdf } from "../api/client";

const initialForm = { title: "", content: "", category: "" };

export default function DocumentForm() {
    const [mode, setMode] = useState("text"); // "text": 직접 입력, "pdf": PDF 업로드
    const [form, setForm] = useState(initialForm);
    const [file, setFile] = useState(null); // PDF 모드에서 선택된 파일
    // file input은 uncontrolled라 setFile(null)만으로는 화면의 파일명이 안 지워진다.
    // key를 바꿔 input을 remount시키면 표시된 파일명까지 깨끗하게 초기화된다.
    const [fileInputKey, setFileInputKey] = useState(0);
    const [loading, setLoading] = useState(false);
    const [result, setResult] = useState(null); // 성공 시 서버 응답(DocumentResponse) 저장
    const [error, setError] = useState(null);

    function updateField(key, value) {
        setForm((prev) => ({ ...prev, [key]: value }));
    }

    // 모드를 바꾸면 이전 모드에서 남은 입력/결과/에러를 초기화해 혼란을 막는다.
    function switchMode(next) {
        if (next === mode) return;
        setMode(next);
        setForm(initialForm);
        setFile(null);
        setFileInputKey((k) => k + 1);
        setResult(null);
        setError(null);
    }

    async function handleSubmit(e) {
        e.preventDefault(); // 폼 기본 동작(페이지 새로고침)을 막음 - SPA에서는 우리가 직접 fetch로 처리하니까
        setLoading(true);
        setError(null);
        setResult(null);

        try {
            let response;
            if (mode === "pdf") {
                // PDF 모드: 제목은 선택값(비우면 백엔드가 파일명을 제목으로 사용). content는 서버가 추출.
                response = await uploadDocumentPdf({ file, title: form.title, category: form.category });
            } else {
                response = await registerDocument(form);
            }
            setResult(response);
            // content/category는 남겨두고 title만 비워서, 같은 문서를 재등록하는 흐름을 편하게 해줌
            // (title이 그대로면 재등록 시 바로 버전이 올라가는 걸 눈으로 확인하기도 편함 - 근데 여기선 전체 초기화가 더 명확해서 전체 초기화로 감)
            setForm(initialForm);
            setFile(null);
            setFileInputKey((k) => k + 1); // 업로드 성공 후 file input에 남은 파일명까지 초기화
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    }

    // 등록 버튼 활성 조건: 텍스트 모드는 title/content 필수(required가 막아줌), PDF 모드는 파일 선택 필수
    const submitDisabled = loading || (mode === "pdf" && !file);

    return (
        <div>
            <div className="panel-header">
                <h1>문서 등록</h1>
                <p>같은 제목으로 다시 등록하면 새 버전으로 저장되고, 이전 버전은 검색 대상에서 제외돼요.</p>
            </div>

            {/* 입력 방식 선택: 직접 입력 vs PDF 업로드. 어느 쪽이든 등록 후 처리(chunk 분할·임베딩)는 동일하다. */}
            <div className="field-row" style={{ marginBottom: "1rem" }}>
                <button
                    type="button"
                    className={`btn ${mode === "text" ? "btn-primary" : "btn-secondary"}`}
                    onClick={() => switchMode("text")}
                >
                    직접 입력
                </button>
                <button
                    type="button"
                    className={`btn ${mode === "pdf" ? "btn-primary" : "btn-secondary"}`}
                    onClick={() => switchMode("pdf")}
                >
                    PDF 업로드
                </button>
            </div>

            {result && (
                <div className="status-message status-success">
                    "{result.title}" 등록 완료 — version {result.version}
                    {result.version > 1 ? " (이전 버전은 비활성화되었습니다)" : ""}
                </div>
            )}
            {error && <div className="status-message status-error">등록 실패: {error}</div>}

            <form className="card" onSubmit={handleSubmit}>
                {mode === "pdf" ? (
                    <>
                        <div className="field">
                            <label htmlFor="pdf-file">PDF 파일</label>
                            <input
                                key={fileInputKey}
                                id="pdf-file"
                                className="input"
                                type="file"
                                accept="application/pdf,.pdf"
                                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                                required
                            />
                            <span className="field-hint">
                                업로드하면 텍스트를 추출해 자동으로 여러 조각(chunk)으로 나눠 임베딩까지 처리해요.
                                스캔 이미지 형태의 PDF는 지원하지 않아요.
                            </span>
                        </div>

                        <div className="field">
                            <label htmlFor="title">제목 (선택)</label>
                            <input
                                id="title"
                                className="input"
                                type="text"
                                value={form.title}
                                onChange={(e) => updateField("title", e.target.value)}
                                placeholder="비워두면 파일명이 제목으로 사용돼요"
                            />
                        </div>
                    </>
                ) : (
                    <>
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
                    </>
                )}

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

                <button className="btn btn-primary" type="submit" disabled={submitDisabled}>
                    {loading ? "등록 중..." : mode === "pdf" ? "PDF 업로드" : "문서 등록"}
                </button>
            </form>
        </div>
    );
}
