import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { startLogin } from "./auth/keycloak";
import "./styles.css";

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } } });
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
