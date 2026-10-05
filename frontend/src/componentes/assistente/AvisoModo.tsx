import { Link } from "react-router";
import type { ContextoAssistente } from "../../api/tipos";
import { config } from "../../config";
import { useSessao } from "../../contexto";

/** Modo MCP_EXTERNO (RF-04.16): o chat é o Claude do usuário, conectado ao MCP do sistema. */
export function AvisoMcp() {
  const { condominioNome } = useSessao();
  return (
    <section className="bloco aviso-modo" aria-labelledby="titulo-mcp">
      <h2 id="titulo-mcp">O assistente deste condomínio é o seu Claude, conectado ao MCP.</h2>
      <p className="discreto">O sistema não chama nenhum modelo de IA por aqui. Para conversar sobre os documentos:</p>
      <ol>
        <li>
          No Claude Desktop ou no Claude Code, adicione um servidor MCP com o endereço <code>{config.mcpUrl}</code>.
        </li>
        <li>Entre com o mesmo usuário que você usa neste sistema (o acesso aos condomínios é o mesmo).</li>
        <li>
          Pergunte normalmente e diga o condomínio: <strong>{condominioNome}</strong>. O Claude usa a busca nos documentos e
          as consultas aos dados gravados.
        </li>
      </ol>
      <p className="discreto">Dúvidas na conexão? Fale com o administrador do sistema. Abaixo, a busca por palavra continua disponível.</p>
    </section>
  );
}

/** Por que não há chat (DESLIGADO, API_KEY sem chave ou LOCAL ainda sem provedor). Para ADMIN, link para a configuração. */
export function MotivoSemChat({ assistente }: { assistente: ContextoAssistente }) {
  const { pode } = useSessao();
  const motivo =
    assistente.modoRespostas === "DESLIGADO"
      ? "A IA de respostas está desligada neste condomínio; a busca por palavra continua disponível."
      : assistente.modoRespostas === "API_KEY"
        ? "O chat usa a chave de IA do condomínio, que ainda não foi cadastrada. Enquanto isso, a busca por palavra está disponível."
        : "O chat não está disponível no modo de IA atual deste condomínio. A busca por palavra está disponível.";
  return (
    <p className="aviso alerta">
      {motivo}{" "}
      {pode("ADMIN") && (
        <Link to="/administracao/ia" className="botao-link">
          Configurar a IA do condomínio
        </Link>
      )}
    </p>
  );
}
