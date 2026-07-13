import { useState } from "react";
import { getActiveDocumentByTitle, getDocumentVersions } from "../api/client";
import DocumentDetailModal from "./DocumentDetailModal";

// createdAt(ISO 문자열)을 화면용으로 다듬는다. 값이 없으면 빈 문자열.
function formatDate(value) {
    if (!value) return "";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value; // 파싱 실패 시 원본 그대로 노출
    return date.toLocaleString();
}

// 문서 한 건(DocumentResponse)의 메타데이터를 카드로 렌더링.
// 백엔드 DocumentResponse에는 content가 없으므로 조회 화면은 메타데이터/버전 정보 중심이다.
function DocumentCard({ doc, highlight, onClick }) {
    return (
        <div
            className={`card ${onClick ? "card-clickable" : ""}`}
            onClick={onClick}
            style={highlight ? { background: "var(--color-accent-soft)", border: "none" } : undefined}
        >
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 8, flexWrap: "wrap" }}>
                <strong>{doc.title}</strong>
                <span className="badge mono">v{doc.version}</span>
                {doc.active
                    ? <span className="badge">활성</span>
                    : <span className="badge" style={{ background: "var(--color-border)", color: "var(--color-ink-soft)" }}>비활성</span>}
                {doc.category && <span className="badge">{doc.category}</span>}
            </div>
            <div style={{ display: "flex", gap: 16, flexWrap: "wrap", fontSize: 13, color: "var(--color-ink-soft)" }}>
                <span className="mono">id {doc.id}</span>
                {doc.createdAt && <span>{formatDate(doc.createdAt)}</span>}
            </div>
        </div>
    );
}

export default function DocumentBrowser() {
    const [title, setTitle] = useState("");
    const [loading, setLoading] = useState(false);
    const [active, setActive] = useState(null);   // 현재 활성 버전(DocumentResponse)
    const [versions, setVersions] = useState(null); // 버전 이력 전체(DocumentResponse[])
    const [error, setError] = useState(null);
    const [searched, setSearched] = useState(false); // 조회를 한 번이라도 시도했는지
    const [searchedTitle, setSearchedTitle] = useState(""); // 실제로 조회한 title (삭제 후 새로고침에 사용)

    // 팝업으로 단건 조회할 문서 id. null이면 팝업 닫힘.
    const [detailId, setDetailId] = useState(null);

    // 특정 title로 활성 버전 + 버전 이력을 다시 불러온다. 최초 조회와 삭제 후 새로고침이 공유한다.
    async function runQuery(t) {
        const trimmed = t.trim();
        if (!trimmed) return;

        setLoading(true);
        setError(null);
        setActive(null);
        setVersions(null);
        setSearched(true);
        setSearchedTitle(trimmed);

        try {
            // 활성 버전과 버전 이력은 서로 독립적인 조회라 병렬로 요청한다.
            // 활성 버전이 없을 수도 있으므로(전부 비활성) 활성 버전 조회 실패는 치명적 오류로 보지 않는다.
            const [activeResult, versionsResult] = await Promise.all([
                getActiveDocumentByTitle(trimmed).catch(() => null),
                getDocumentVersions(trimmed),
            ]);
            setActive(activeResult);
            setVersions(versionsResult);
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    }

    function handleSubmit(e) {
        e.preventDefault();
        runQuery(title);
    }

    return (
        <div>
            <div className="panel-header">
                <h1>문서 조회</h1>
                <p>제목으로 현재 활성 버전과 전체 버전 이력을 확인해요. 버전을 클릭하면 해당 버전을 단건 조회해요.</p>
            </div>

            <form className="card" onSubmit={handleSubmit} style={{ marginBottom: 20 }}>
                <div className="field" style={{ marginBottom: 0 }}>
                    <label htmlFor="doc-title">문서 제목</label>
                    <input
                        id="doc-title"
                        className="input"
                        type="text"
                        value={title}
                        onChange={(e) => setTitle(e.target.value)}
                        placeholder="예: Spring AI 소개"
                    />
                </div>

                <button className="btn btn-primary" type="submit" disabled={loading} style={{ marginTop: 16 }}>
                    {loading ? "조회 중..." : "조회"}
                </button>
            </form>

            {error && <div className="status-message status-error">조회 실패: {error}</div>}

            {/* 버전 이력은 있지만 활성 버전이 없는 경우(전부 비활성) 안내 */}
            {searched && !error && versions && versions.length > 0 && !active && (
                <div className="status-message" style={{ background: "var(--color-border)", color: "var(--color-ink-soft)" }}>
                    현재 활성 버전이 없어요. 모든 버전이 비활성 상태입니다.
                </div>
            )}

            {searched && !loading && !error && versions && versions.length === 0 && (
                <div className="empty-state">해당 제목으로 등록된 문서가 없어요.</div>
            )}

            {active && (
                <div style={{ marginBottom: 20 }}>
                    <div style={{ fontSize: 13, fontWeight: 600, color: "var(--color-ink-soft)", marginBottom: 8 }}>
                        현재 활성 버전
                    </div>
                    <DocumentCard doc={active} highlight onClick={() => setDetailId(active.id)} />
                </div>
            )}

            {versions && versions.length > 0 && (
                <div>
                    <div style={{ fontSize: 13, fontWeight: 600, color: "var(--color-ink-soft)", marginBottom: 8 }}>
                        버전 이력 ({versions.length})
                    </div>
                    <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                        {versions.map((doc) => (
                            <button
                                key={doc.id}
                                onClick={() => setDetailId(doc.id)}
                                className="card card-clickable"
                                style={{ textAlign: "left", width: "100%" }}
                            >
                                <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
                                    <span className="badge mono">v{doc.version}</span>
                                    {doc.active
                                        ? <span className="badge">활성</span>
                                        : <span className="badge" style={{ background: "var(--color-border)", color: "var(--color-ink-soft)" }}>비활성</span>}
                                    {doc.category && <span className="badge">{doc.category}</span>}
                                    <span className="mono" style={{ fontSize: 12, color: "var(--color-ink-soft)" }}>id {doc.id}</span>
                                    {doc.createdAt && (
                                        <span style={{ fontSize: 12, color: "var(--color-ink-soft)", marginLeft: "auto" }}>
                                            {formatDate(doc.createdAt)}
                                        </span>
                                    )}
                                </div>
                            </button>
                        ))}
                    </div>
                </div>
            )}

            {detailId !== null && (
                <DocumentDetailModal
                    documentId={detailId}
                    onClose={() => setDetailId(null)}
                    onDeleted={() => runQuery(searchedTitle)}
                />
            )}
        </div>
    );
}
