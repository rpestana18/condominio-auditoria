import { useState } from "react";
import { McpNotice, NoChatReason } from "../components/assistant/ModeNotice";
import { DocumentSearch } from "../components/assistant/DocumentSearch";
import { Chat } from "../components/assistant/Chat";
import { DocumentFiltersPanel } from "../components/assistant/DocumentFiltersPanel";
import { useSession } from "../context";

type Tab = "CONVERSATION" | "SEARCH";

/**
 * "Assistente" screen (RF-04.8 to 04.18). What shows up depends on the effective AI mode that comes from the context (RF-04.16):
 * - chat available (API_KEY with a key): conversation + document search;
 * - EXTERNAL_MCP: notice that the assistant is the user's own Claude, connection instructions + search;
 * - OFF or API_KEY without a key: only the search, with the reason.
 * Hiding is not enough: the backend refuses the chat (409) outside the right mode and everything (403) without the feature.
 */
export function Assistant() {
  const { condominiumId, condominiumName, isFeatureEnabled, assistant, contextLoaded } = useSession();
  const [tab, setTab] = useState<Tab>("CONVERSATION");

  if (!contextLoaded) return <p className="notice">Carregando…</p>;
  if (!isFeatureEnabled("ASSISTANT") || !assistant) {
    return <p className="notice">O módulo Assistente não está ligado neste condomínio.</p>;
  }

  const chat = assistant.chatAvailable;
  return (
    // key: switching condominium clears the search result (the conversation is already reset in ConversationProvider)
    <div key={condominiumId}>
      <header className="page-title">
        <h1>Assistente</h1>
        <span className="muted">{condominiumName}</span>
      </header>

      {!chat && (assistant.answersMode === "EXTERNAL_MCP" ? <McpNotice /> : <NoChatReason assistant={assistant} />)}

      <DocumentFiltersPanel />

      {chat ? (
        <>
          <div className="tabs" role="tablist" aria-label="Modo da tela">
            <button role="tab" id="conversation-tab" aria-selected={tab === "CONVERSATION"} aria-controls="assistant-panel" onClick={() => setTab("CONVERSATION")}>
              Conversa
            </button>
            <button role="tab" id="search-tab" aria-selected={tab === "SEARCH"} aria-controls="assistant-panel" onClick={() => setTab("SEARCH")}>
              Busca nos documentos
            </button>
          </div>
          <div id="assistant-panel" role="tabpanel" aria-labelledby={tab === "CONVERSATION" ? "conversation-tab" : "search-tab"}>
            {/* The conversation lives in ConversationProvider: switching tabs deletes nothing */}
            {tab === "CONVERSATION" ? <Chat /> : <DocumentSearch />}
          </div>
        </>
      ) : (
        <>
          <h2>Busca nos documentos</h2>
          <DocumentSearch />
        </>
      )}
    </div>
  );
}
