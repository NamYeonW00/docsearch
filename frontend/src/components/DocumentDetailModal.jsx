import { useEffect, useState } from "react";
import { getDocumentById, deactivateDocument } from "../api/client";

function formatDate(value) {
    if (!value) return "";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return date.toLocaleString();
}

// 문서 단건(특정 버전)을 팝업(모달)으로 보여준다.
// 조회/검색/질문하기 화면 어디서든 documentId만 넘기면 이 컴포넌트가 직접 GET /documents/{id}로
// 본문 전체를 포함한 상세를 불러와 표시한다.
export default function DocumentDetailModal({ documentId, onClose, onDeleted }) {
    const [doc, setDoc] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [deleting, setDeleting] = useState(false);

    // documentId가 바뀔 때마다 해당 문서를 다시 불러온다.
    useEffect(() => {
        let cancelled = false; // 언마운트/재요청 시 늦게 도착한 응답이 상태를 덮어쓰지 않도록 가드
        setLoading(true);
        setError(null);
        setDoc(null);

        getDocumentById(documentId)
            .then((result) => {
                if (!cancelled) setDoc(result);
            })
            .catch((err) => {
                if (!cancelled) setError(err.message);
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });

        return () => {
            cancelled = true;
        };
    }, [documentId]);

    // Esc 키로 닫기
    useEffect(() => {
        function onKeyDown(e) {
            if (e.key === "Escape") onClose();
        }
        window.addEventListener("keydown", onKeyDown);
        return () => window.removeEventListener("keydown", onKeyDown);
    }, [onClose]);

    // soft delete(비활성화). 확인 후 DELETE 호출, 성공하면 모달 내용을 비활성 상태로 갱신하고
    // 부모에게 알려(onDeleted) 목록 등을 새로고침할 수 있게 한다.
    async function handleDelete() {
        if (!doc) return;
        if (!window.confirm(`"${doc.title}" v${doc.version} 버전을 삭제(비활성화)할까요?\n검색과 질문 결과에서 제외됩니다.`)) {
            return;
        }
        setDeleting(true);
        setError(null);
        try {
            const updated = await deactivateDocument(doc.id);
            setDoc(updated);
            if (onDeleted) onDeleted(doc.id);
        } catch (err) {
            setError(err.message);
        } finally {
            setDeleting(false);
        }
    }

    return (
        // 배경(backdrop) 클릭 시 닫힘. 모달 본문 클릭은 stopPropagation으로 전파 차단.
        <div className="modal-backdrop" onClick={onClose}>
            <div className="modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
                <div className="modal-header">
                    <span style={{ fontSize: 13, fontWeight: 600, color: "var(--color-ink-soft)" }}>
                        문서 단건 조회
                    </span>
                    <button className="modal-close" onClick={onClose} aria-label="닫기">×</button>
                </div>

                <div className="modal-body">
                    {loading && <div className="empty-state" style={{ border: "none" }}>불러오는 중...</div>}
                    {error && <div className="status-message status-error">조회 실패: {error}</div>}

                    {doc && (
                        <div>
                            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 12, flexWrap: "wrap" }}>
                                <strong style={{ fontSize: 16 }}>{doc.title}</strong>
                                <span className="badge mono">v{doc.version}</span>
                                {doc.active
                                    ? <span className="badge">활성</span>
                                    : <span className="badge" style={{ background: "var(--color-border)", color: "var(--color-ink-soft)" }}>비활성</span>}
                                {doc.category && <span className="badge">{doc.category}</span>}
                            </div>

                            <div style={{ display: "flex", gap: 16, flexWrap: "wrap", fontSize: 13, color: "var(--color-ink-soft)", marginBottom: 16 }}>
                                <span className="mono">id {doc.id}</span>
                                {doc.createdAt && <span>{formatDate(doc.createdAt)}</span>}
                            </div>

                            <div style={{ fontSize: 12, fontWeight: 600, color: "var(--color-ink-soft)", marginBottom: 6 }}>
                                본문
                            </div>
                            <p style={{ margin: 0, whiteSpace: "pre-wrap", lineHeight: 1.6, fontSize: 14 }}>
                                {doc.content}
                            </p>
                        </div>
                    )}
                </div>

                {doc && (
                    <div className="modal-footer">
                        {doc.active ? (
                            <button className="btn btn-danger" onClick={handleDelete} disabled={deleting}>
                                {deleting ? "삭제 중..." : "삭제 (비활성화)"}
                            </button>
                        ) : (
                            <span style={{ fontSize: 13, color: "var(--color-ink-soft)" }}>
                                이미 비활성화된 버전입니다.
                            </span>
                        )}
                        <button className="btn btn-secondary" onClick={onClose}>닫기</button>
                    </div>
                )}
            </div>
        </div>
    );
}
