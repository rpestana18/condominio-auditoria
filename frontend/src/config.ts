// System addresses. In production they change through build-time environment variables (VITE_*), without touching the code.
export const config = {
  keycloakUrl: import.meta.env.VITE_KEYCLOAK_URL ?? "http://localhost:8180",
  keycloakRealm: import.meta.env.VITE_KEYCLOAK_REALM ?? "condominio",
  keycloakClient: import.meta.env.VITE_KEYCLOAK_CLIENTE ?? "frontend",
  api: "/api",
  /** MCP server address, shown in the Claude connection instructions (EXTERNAL_MCP mode). */
  mcpUrl: import.meta.env.VITE_MCP_URL ?? "http://localhost:8083/mcp",
};
