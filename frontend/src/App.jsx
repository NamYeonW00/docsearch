import { useState } from "react";
import DocumentForm from "./components/DocumentForm";
import SearchPanel from "./components/SearchPanel";
import ChatPanel from "./components/ChatPanel";
import "./App.css";

// 탭 정의를 배열로 분리해둔 이유: 네비게이션 버튼 렌더링과 "현재 탭이 뭔지" 판단 로직이
// 이 배열 하나로 통일되어서, 탭을 추가/순서변경 할 때 여기 한 곳만 고치면 됨
const TABS = [
  { id: "register", label: "문서 등록", description: "새 문서를 벡터 저장소에 등록" },
  { id: "search", label: "검색", description: "의미 기반으로 문서 조각을 탐색" },
  { id: "chat", label: "질문하기", description: "검색 결과를 근거로 답변 생성" },
];

export default function App() {
  const [activeTab, setActiveTab] = useState("register");

  return (
      <div className="app">
        <nav className="rail">
          <div className="rail-brand">
            <span className="rail-brand-mark">docsearch</span>
            <span className="rail-brand-sub">vector search console</span>
          </div>

          <div className="rail-tabs">
            {TABS.map((tab) => (
                <button
                    key={tab.id}
                    className={`rail-tab ${activeTab === tab.id ? "rail-tab-active" : ""}`}
                    onClick={() => setActiveTab(tab.id)}
                >
                  <span className="rail-tab-label">{tab.label}</span>
                  <span className="rail-tab-desc">{tab.description}</span>
                </button>
            ))}
          </div>
        </nav>

        <main className="content">
          {activeTab === "register" && <DocumentForm />}
          {activeTab === "search" && <SearchPanel />}
          {activeTab === "chat" && <ChatPanel />}
        </main>
      </div>
  );
}