import { useQueryClient } from "@tanstack/react-query";
import { createContext, useContext, useState, type ReactNode } from "react";
import { ApiError, postJson } from "../../api/client";
import type { DocumentFilters, QuestionRequest, AssistantAnswer, ConversationTurn } from "../../api/types";
import { useSession } from "../../context";
import { assistantErrorMessage } from "./errors";

/** How many previous turns go with each question (the backend also cuts at the last 6). */
const TURNS_IN_HISTORY = 6;

/** A question and what came back: the answer, an error, or nothing yet (waiting). */
export interface ChatTurn {
  id: number;
  question: string;
  answer?: AssistantAnswer;
  error?: string;
}

interface ConversationState {
  /** The conversation belongs to a single condominium (RF-04.11): switching it starts another one. */
  condominiumId: string;
  /** Changes on every "Nova conversa": answers that arrive from an old conversation are discarded. */
  conversationId: number;
  turns: ChatTurn[];
  filters: DocumentFilters;
}

interface Conversation {
  turns: ChatTurn[];
  pending: boolean;
  filters: DocumentFilters;
  changeFilters: (filters: DocumentFilters) => void;
  ask: (question: string) => void;
  newConversation: () => void;
}

let nextId = 1;
const newId = () => nextId++;

const empty = (condominiumId: string): ConversationState => ({ condominiumId, conversationId: newId(), turns: [], filters: {} });

/**
 * Text of an answer for the history: the "Nos documentos" paragraphs and the "Nos dados gravados" rows,
 * as they came from the API (without recalculating anything). It only helps the model understand "e no ano anterior?".
 */
function answerText(answer: AssistantAnswer): string {
  if (answer.status === "NOT_FOUND") {
    return ["Não encontrei nos documentos.", answer.suggestion].filter(Boolean).join(" ");
  }
  const documents = answer.fromDocuments.map((p) => p.text);
  const data = answer.fromStoredData.map(
    (d) => `${d.query}: ${d.rows.map((r) => `${r.label} ${r.value}`).join("; ")}`,
  );
  return [...documents, ...data].join("\n");
}

/** Filters without empty fields: the API reads a missing field as "all". */
export function filtersForApi(filters: DocumentFilters): DocumentFilters | undefined {
  const clean: DocumentFilters = {};
  if (filters.categories?.length) clean.categories = filters.categories;
  if (filters.fileIds?.length) clean.fileIds = filters.fileIds;
  if (filters.startDate) clean.startDate = filters.startDate;
  if (filters.endDate) clean.endDate = filters.endDate;
  return Object.keys(clean).length > 0 ? clean : undefined;
}

const ConversationContext = createContext<Conversation | null>(null);

/**
 * Keeps the Assistant conversation in memory only (never in localStorage/sessionStorage, RF-04.11).
 * It sits above the routes so the conversation does not vanish when visiting another screen; it goes away when switching
 * condominium, with "Nova conversa" and on logout (logging out reloads the page).
 */
export function ConversationProvider({ children }: { children: ReactNode }) {
  const { condominiumId } = useSession();
  const queryClient = useQueryClient();
  const [state, setState] = useState<ConversationState>(() => empty(condominiumId));

  // Condominium switched: start another conversation (state adjustment during render, a React pattern)
  let current = state;
  if (state.condominiumId !== condominiumId) {
    current = empty(condominiumId);
    setState(current);
  }

  const pending = current.turns.some((t) => !t.answer && !t.error);

  /** Updates a turn, but only if it is still the same conversation (otherwise the answer arrived late and is discarded). */
  function complete(conversationId: number, turnId: number, result: Pick<ChatTurn, "answer" | "error">) {
    setState((s) =>
      s.conversationId !== conversationId ? s : { ...s, turns: s.turns.map((t) => (t.id === turnId ? { ...t, ...result } : t)) },
    );
  }

  function ask(question: string) {
    if (pending) return;
    const history: ConversationTurn[] = current.turns
      .filter((t) => t.answer)
      .slice(-TURNS_IN_HISTORY)
      .map((t) => ({ question: t.question, answer: answerText(t.answer!) }));
    const request: QuestionRequest = { question, history, filters: filtersForApi(current.filters) };
    const { conversationId } = current;
    const turnId = newId();
    setState((s) => ({ ...s, turns: [...s.turns, { id: turnId, question }] }));

    postJson<AssistantAnswer>(`/condominiums/${condominiumId}/assistant/questions`, request)
      .then((answer) => complete(conversationId, turnId, { answer }))
      .catch((error: unknown) => {
        complete(conversationId, turnId, { error: assistantErrorMessage(error) });
        // 409: the mode changed since the screen opened; reload the context so the screen adjusts (RF-04.16)
        if (error instanceof ApiError && (error.status === 409 || error.featureNotEnabled)) {
          void queryClient.invalidateQueries({ queryKey: ["context", condominiumId] });
        }
      });
  }

  const conversation: Conversation = {
    turns: current.turns,
    pending,
    filters: current.filters,
    changeFilters: (filters) => setState((s) => ({ ...s, filters })),
    ask,
    // Keeps the filters: they are a choice of the screen, not of the conversation
    newConversation: () => setState((s) => ({ ...empty(s.condominiumId), filters: s.filters })),
  };
  return <ConversationContext.Provider value={conversation}>{children}</ConversationContext.Provider>;
}

export function useConversation(): Conversation {
  const conversation = useContext(ConversationContext);
  if (!conversation) throw new Error("useConversation outside ConversationProvider");
  return conversation;
}
