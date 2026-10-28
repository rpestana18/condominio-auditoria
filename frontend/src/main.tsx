import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { ApiError } from "./api/client";
import { startLogin } from "./auth/keycloak";
import "./styles.css";

// A 4xx is an answer, not a failure: repeating it only repeats the error (and its log line, ADR 0008)
const retry = (failures: number, error: Error) => !(error instanceof ApiError && error.status < 500) && failures < 1;
const queryClient = new QueryClient({ defaultOptions: { queries: { retry, refetchOnWindowFocus: false } } });
const root = createRoot(document.getElementById("root")!);

// Nothing shows up before login: Keycloak redirects to its sign-in page and comes back with the token
startLogin()
  .then(() =>
    root.render(
      <StrictMode>
        <QueryClientProvider client={queryClient}>
          <App />
        </QueryClientProvider>
      </StrictMode>,
    ),
  )
  .catch(() => root.render(<p className="notice error">Não foi possível conectar ao serviço de login.</p>));
