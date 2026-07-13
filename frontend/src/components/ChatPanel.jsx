import { useState } from "react";
import { askChat } from "../api/client";
import DocumentDetailModal from "./DocumentDetailModal";

export default function ChatPanel() {
    const [query, setQuery] = useState("");
    const [loading, setLoading] = useState(false);
    const [response, setResponse] = useState(null); // { answer, sources }
    const [error, setError] = useState(null);
    const [detailId, setDetailId] = useState(null); // 팝업으로 단건 조회할 문서 id

    async function handleSubmit(e) {
        e.preventDefault();
        if (!query.trim()) return;

        setLoading(true);
        setError(null);

        try {
            const result = await askChat(query);
            setResponse(result);
        } catch (err) {
            setError(err.message);
            setResponse(null);
        } finally {
            setLoading(false);
        }
    }

    return (
        <div>
            <div className="panel-header">
                <h1>질문하기</h1>
                <p>등록된 문서를 근거로 답변을 생성해요. 관련 문서가 없으면 모른다고 답해요.</p>
            </div>

            <form className="card" onSubmit={handleSubmit} style={{ marginBottom: 20 }}>
                <div className="field">
                    <label htmlFor="chat-query">질문</label>
                    <input
                        id="chat-query"
                        className="input"
                        type="text"
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                        placeholder="예: Spring AI가 무엇인가요?"
                    />
                </div>

                <button className="btn btn-primary" type="submit" disabled={loading}>
                    {loading ? "답변 생성 중..." : "질문하기"}
                </button>
            </form>

            {error && <div className="status-message status-error">답변 생성 실패: {error}</div>}

            {response && (
                <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
                    {/* 답변 카드 - 근거 카드들과 시각적으로 구분되도록 accent 배경을 씀 */}
                    <div className="card" style={{ background: "var(--color-accent-soft)", border: "none" }}>
                        <div style={{ fontSize: 12, fontWeight: 600, color: "var(--color-accent)", marginBottom: 8 }}>
                            답변
                        </div>
                        <p style={{ margin: 0, whiteSpace: "pre-wrap", lineHeight: 1.6 }}>{response.answer}</p>
                    </div>

                    {response.sources.length > 0 && (
                        <div>
                            <div style={{ fontSize: 13, fontWeight: 600, color: "var(--color-ink-soft)", marginBottom: 8 }}>
                                근거로 사용된 문서 ({response.sources.length})
                            </div>
                            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                                {response.sources.map((source, index) => (
                                    <div
                                        className={`card ${source.documentId != null ? "card-clickable" : ""}`}
                                        key={index}
                                        onClick={source.documentId != null ? () => setDetailId(source.documentId) : undefined}
                                        title={source.documentId != null ? "클릭하면 원문 전체를 봅니다" : undefined}
                                    >
                                        <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6 }}>
                                            <strong style={{ fontSize: 14 }}>{source.title}</strong>
                                            {source.category && <span className="badge">{source.category}</span>}
                                        </div>
                                        <p style={{ margin: 0, color: "var(--color-ink-soft)", fontSize: 13 }}>{source.content}</p>

                                        <div className="similarity" style={{ marginTop: 10 }}>
                                            <div className="similarity-track">
                                                <div
                                                    className="similarity-fill"
                                                    style={{ width: `${Math.min(Math.max(source.score, 0), 1) * 100}%` }}
                                                />
                                            </div>
                                            <span className="similarity-value">{source.score.toFixed(2)}</span>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}
                </div>
            )}

            {detailId !== null && (
                <DocumentDetailModal documentId={detailId} onClose={() => setDetailId(null)} />
            )}
        </div>
    );
}