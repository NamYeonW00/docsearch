import { useState } from "react";
import { searchDocuments } from "../api/client";

export default function SearchPanel() {
    const [query, setQuery] = useState("");
    const [topK, setTopK] = useState(5);
    const [category, setCategory] = useState("");
    const [loading, setLoading] = useState(false);
    const [results, setResults] = useState(null); // null = 아직 검색 안 함, [] = 검색했는데 결과 없음
    const [error, setError] = useState(null);

    async function handleSubmit(e) {
        e.preventDefault();
        if (!query.trim()) return; // 빈 질의로는 검색 자체를 시도하지 않음

        setLoading(true);
        setError(null);

        try {
            const response = await searchDocuments({ query, topK, category });
            setResults(response);
        } catch (err) {
            setError(err.message);
            setResults(null);
        } finally {
            setLoading(false);
        }
    }

    return (
        <div>
            <div className="panel-header">
                <h1>검색</h1>
                <p>자연어로 질문하면 의미가 가장 비슷한 문서 조각을 찾아드려요.</p>
            </div>

            <form className="card" onSubmit={handleSubmit} style={{ marginBottom: 20 }}>
                <div className="field">
                    <label htmlFor="query">질문</label>
                    <input
                        id="query"
                        className="input"
                        type="text"
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                        placeholder="예: Spring AI가 뭐야?"
                    />
                </div>

                <div className="field-row">
                    <div className="field">
                        <label htmlFor="topK">결과 개수 (topK)</label>
                        <input
                            id="topK"
                            className="input"
                            type="number"
                            min={1}
                            max={20}
                            value={topK}
                            onChange={(e) => setTopK(Number(e.target.value))}
                        />
                    </div>
                    <div className="field">
                        <label htmlFor="category">카테고리 필터 (선택)</label>
                        <input
                            id="category"
                            className="input"
                            type="text"
                            value={category}
                            onChange={(e) => setCategory(e.target.value)}
                            placeholder="예: framework"
                        />
                    </div>
                </div>

                <button className="btn btn-primary" type="submit" disabled={loading}>
                    {loading ? "검색 중..." : "검색"}
                </button>
            </form>

            {error && <div className="status-message status-error">검색 실패: {error}</div>}

            {results && results.length === 0 && (
                <div className="empty-state">관련된 문서를 찾지 못했어요. 다른 표현으로 다시 검색해보세요.</div>
            )}

            {results && results.length > 0 && (
                <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                    {results.map((result, index) => (
                        <div className="card" key={index}>
                            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 12 }}>
                                <div style={{ flex: 1 }}>
                                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6 }}>
                                        <strong>{result.title}</strong>
                                        {result.category && <span className="badge">{result.category}</span>}
                                        {result.chunkIndex !== null && (
                                            <span className="badge mono">chunk #{result.chunkIndex}</span>
                                        )}
                                    </div>
                                    <p style={{ margin: 0, color: "var(--color-ink-soft)", fontSize: 14 }}>{result.content}</p>
                                </div>
                            </div>

                            {/* 시그니처 요소: score를 숫자 + 가로 게이지 바로 함께 표시 */}
                            <div className="similarity" style={{ marginTop: 12 }}>
                                <div className="similarity-track">
                                    <div
                                        className="similarity-fill"
                                        style={{ width: `${Math.min(Math.max(result.score, 0), 1) * 100}%` }}
                                    />
                                </div>
                                <span className="similarity-value">{result.score.toFixed(2)}</span>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}