import { createContext, useContext, useState, type ReactNode } from "react";
import { useCondominiumContext } from "./api/queries";
import type { AssistantContext, CurrentUser, Role } from "./api/types";

interface Session {
  user: CurrentUser;
  condominiumId: string;
  condominiumName: string;
  switchCondominium: (id: string) => void;
  /** E.g. can("MANAGER", "ADMIN") to show the upload button. The backend is the one that really blocks. */
  can: (...roles: Role[]) => boolean;
  /** Codes of the features enabled in the condominium (GET /context). Empty while loading. */
  enabledFeatures: string[];
  /** E.g. isFeatureEnabled("ASSISTANT") to show the Assistant menu. The backend also blocks (403). */
  isFeatureEnabled: (code: string) => boolean;
  /** Effective AI mode of the Assistant (RF-04.16); null with the feature disabled or while loading. */
  assistant: AssistantContext | null;
  /** The context already arrived from the API (avoids showing "feature disabled" while loading). */
  contextLoaded: boolean;
}

const SessionContext = createContext<Session | null>(null);

export function SessionProvider({ user, children }: { user: CurrentUser; children: ReactNode }) {
  const [condominiumId, setCondominiumId] = useState(user.condominiums[0].id);
  const condominium = user.condominiums.find((c) => c.id === condominiumId)!;
  // Reloads when switching condominium (the query key includes the id) and after enabling/disabling a feature
  const { data: context } = useCondominiumContext(condominiumId);
  const enabledFeatures = context?.enabledFeatures ?? [];

  const session: Session = {
    user,
    condominiumId,
    condominiumName: condominium.name,
    switchCondominium: setCondominiumId,
    can: (...roles) => roles.some((r) => user.roles.includes(r)),
    enabledFeatures,
    isFeatureEnabled: (code) => enabledFeatures.includes(code),
    assistant: context?.assistant ?? null,
    contextLoaded: context !== undefined,
  };
  return <SessionContext.Provider value={session}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  const session = useContext(SessionContext);
  if (!session) throw new Error("useSession outside SessionProvider");
  return session;
}
