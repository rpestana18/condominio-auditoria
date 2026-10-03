// Endereços do sistema. Em produção mudam por variável de ambiente no build (VITE_*), sem mexer no código.
export const config = {
  keycloakUrl: import.meta.env.VITE_KEYCLOAK_URL ?? "http://localhost:8180",
  keycloakRealm: import.meta.env.VITE_KEYCLOAK_REALM ?? "condominio",
  keycloakCliente: import.meta.env.VITE_KEYCLOAK_CLIENTE ?? "frontend",
  api: "/api",
};
