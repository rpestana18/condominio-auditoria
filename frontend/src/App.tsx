import { BrowserRouter, Route, Routes } from "react-router";
import { useCurrentUser } from "./api/queries";
import { Layout } from "./components/Layout";
import { SessionProvider } from "./context";
import { logout } from "./auth/keycloak";
import { ConversationProvider } from "./components/assistant/conversation";
import { AiAdministration } from "./pages/AiAdministration";
import { FeatureAdministration } from "./pages/FeatureAdministration";
import { Files } from "./pages/Files";
import { CompareFiscalYears } from "./pages/CompareFiscalYears";
import { Assistant } from "./pages/Assistant";
import { AccountMapping } from "./pages/AccountMapping";
import { Indicators } from "./pages/Indicators";
import { Home } from "./pages/Home";
import { Budget } from "./pages/Budget";
import { Budgets } from "./pages/Budgets";
import { BudgetVsActual } from "./pages/BudgetVsActual";
import { UtilityAccounts } from "./pages/UtilityAccounts";
import { BudgetTracking } from "./pages/BudgetTracking";
import { BalanceOrigin } from "./pages/BalanceOrigin";
import { FundMovementsPage } from "./pages/FundMovementsPage";
import { SpendingProfilesPage } from "./pages/SpendingProfilesPage";
import { CashFlowPage } from "./pages/CashFlowPage";

export function App() {
  const { data: user, error } = useCurrentUser();
  if (error) return <p className="notice error">Não foi possível falar com o servidor: {error.message}</p>;
  if (!user) return <p className="notice">Carregando…</p>;
  if (user.condominiums.length === 0) {
    return (
      <p className="notice">
        Seu usuário ainda não está vinculado a nenhum condomínio. Peça ao administrador.{" "}
        <button className="link-button" onClick={logout}>
          Sair
        </button>
      </p>
    );
  }
  return (
    <SessionProvider user={user}>
      {/* The Assistant conversation lives here, above the routes, in memory only (RF-04.11) */}
      <ConversationProvider>
        <BrowserRouter>
          <Routes>
            <Route element={<Layout />}>
              <Route index element={<Home />} />
              <Route path="arquivos" element={<Files />} />
              <Route path="concessionarias" element={<UtilityAccounts />} />
              <Route path="entradas-e-saidas" element={<CashFlowPage />} />
              <Route path="acompanhamento-po" element={<BudgetTracking />} />
              <Route path="origem-do-saldo" element={<BalanceOrigin />} />
              <Route path="fundos" element={<FundMovementsPage />} />
              {/* Out of the menu (Q53): opened from the month header of the tracking grid; old addresses keep working */}
              <Route path="previsto-realizado" element={<BudgetVsActual />} />
              <Route path="comparar-exercicios" element={<CompareFiscalYears />} />
              <Route path="indicadores" element={<Indicators />} />
              <Route path="depara" element={<AccountMapping />} />
              <Route path="previsoes" element={<Budgets />} />
              <Route path="previsoes/:budgetId" element={<Budget />} />
              <Route path="previsoes/:budgetId/tipos" element={<SpendingProfilesPage />} />
              <Route path="assistente" element={<Assistant />} />
              <Route path="administracao/modulos" element={<FeatureAdministration />} />
              <Route path="administracao/ia" element={<AiAdministration />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </ConversationProvider>
    </SessionProvider>
  );
}
