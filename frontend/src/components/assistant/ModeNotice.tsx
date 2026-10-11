import { Link } from "react-router";
import type { AssistantContext } from "../../api/types";
import { config } from "../../config";
import { useSession } from "../../context";

/** EXTERNAL_MCP mode (RF-04.16): the chat is the user's own Claude, connected to the system's MCP. */
export function McpNotice() {
  const { condominiumName } = useSession();
  return (
    <section className="block mode-notice" aria-labelledby="mcp-title">
      <h2 id="mcp-title">O assistente deste condomínio é o seu Claude, conectado ao MCP.</h2>
      <p className="muted">O sistema não chama nenhum modelo de IA por aqui. Para conversar sobre os documentos:</p>
      <ol>
        <li>
          No Claude Desktop ou no Claude Code, adicione um servidor MCP com o endereço <code>{config.mcpUrl}</code>.
        </li>
        <li>Entre com o mesmo usuário que você usa neste sistema (o acesso aos condomínios é o mesmo).</li>
        <li>
          Pergunte normalmente e diga o condomínio: <strong>{condominiumName}</strong>. O Claude usa a busca nos documentos e
          as consultas aos dados gravados.
        </li>
      </ol>
      <p className="muted">Dúvidas na conexão? Fale com o administrador do sistema. Abaixo, a busca por palavra continua disponível.</p>
    </section>
  );
}

/** Why there is no chat (OFF, API_KEY without a key, or LOCAL still without a provider). For ADMIN, a link to the configuration. */
export function NoChatReason({ assistant }: { assistant: AssistantContext }) {
  const { can } = useSession();
  const reason =
    assistant.answersMode === "OFF"
      ? "A IA de respostas está desligada neste condomínio; a busca por palavra continua disponível."
      : assistant.answersMode === "API_KEY"
        ? "O chat usa a chave de IA do condomínio, que ainda não foi cadastrada. Enquanto isso, a busca por palavra está disponível."
        : "O chat não está disponível no modo de IA atual deste condomínio. A busca por palavra está disponível.";
  return (
    <p className="notice warning">
      {reason}{" "}
      {can("ADMIN") && (
        <Link to="/administracao/ia" className="link-button">
          Configurar a IA do condomínio
        </Link>
      )}
    </p>
  );
}
