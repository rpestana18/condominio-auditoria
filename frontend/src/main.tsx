import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { iniciarLogin } from "./autenticacao/keycloak";
import "./estilos.css";

const consultas = new QueryClient({ defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } } });
const raiz = createRoot(document.getElementById("raiz")!);

// Nada aparece antes do login: o Keycloak redireciona para a tela de entrada e volta com o token
iniciarLogin()
  .then(() =>
    raiz.render(
      <StrictMode>
        <QueryClientProvider client={consultas}>
          <App />
        </QueryClientProvider>
      </StrictMode>,
    ),
  )
  .catch(() => raiz.render(<p className="aviso erro">Não foi possível conectar ao serviço de login.</p>));
