import { useState, type FormEvent } from "react";
import { ErroApi } from "../../api/cliente";
import { useGravarConfiguracaoIa } from "../../api/consultas";
import type { ConfiguracaoIa, ModoIa, PedidoConfiguracaoIa, ProvedorIa } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { formatarDataHora } from "../../formato";
import { CampoChave } from "./CampoChave";
import { EscolhaModo, descricoesModo, opcoesModo } from "./EscolhaModo";
import { SeletorModelo } from "./SeletorModelo";

/** Respostas do Assistente: herdar o modo geral ou um modo próprio. */
type ModoRespostas = "HERDAR" | ModoIa;
type ModoEmbeddings = "LOCAL" | "DESLIGADO";

interface Props {
  configuracao: ConfiguracaoIa;
  provedores: ProvedorIa[];
  /** O catálogo não carregou (rag fora do ar): a tela avisa, mas deixa trocar os modos. */
  erroCatalogo: string | null;
}

/** Provedor inicial: o gravado; senão, o primeiro do catálogo para aquele uso. */
const inicial = (gravado: string | null | undefined, lista: ProvedorIa[]) => gravado ?? lista[0]?.codigo ?? "";

/**
 * Formulário da configuração de IA (RF-09.1, RF-09.2, RF-09.6). Só monta o pedido: quem valida e recusa
 * (422, com os motivos) é o backend.
 */
export function FormularioIa({ configuracao, provedores, erroCatalogo }: Props) {
  const { condominioId } = useSessao();
  const gravar = useGravarConfiguracaoIa(condominioId);
  const { respostas, embeddings } = configuracao.assistente;
  const provedoresRespostas = provedores.filter((p) => p.uso === "RESPOSTAS");
  // Embeddings só com provedor local nesta fase (Q12)
  const provedoresEmbeddings = provedores.filter((p) => p.uso === "EMBEDDINGS" && p.local);

  const [modoGeral, setModoGeral] = useState<ModoIa>(configuracao.modoGeral);
  const [modoRespostas, setModoRespostas] = useState<ModoRespostas>(respostas.modo ?? "HERDAR");
  const [provedorRespostas, setProvedorRespostas] = useState(inicial(respostas.provedor, provedoresRespostas));
  const [modeloRespostas, setModeloRespostas] = useState(respostas.modelo ?? "");
  const [chave, setChave] = useState("");
  const [removerChave, setRemoverChave] = useState(false);
  const [modoEmbeddings, setModoEmbeddings] = useState<ModoEmbeddings>(embeddings.modo === "DESLIGADO" ? "DESLIGADO" : "LOCAL");
  const [provedorEmbeddings, setProvedorEmbeddings] = useState(inicial(embeddings.provedor, provedoresEmbeddings));
  const [modeloEmbeddings, setModeloEmbeddings] = useState(embeddings.modelo ?? "");
  const [gravado, setGravado] = useState(false);

  // Só para mostrar ou esconder os campos de provedor e chave; o modo efetivo de verdade quem calcula é o backend
  const modoRespostasNaTela = modoRespostas === "HERDAR" ? modoGeral : modoRespostas;
  const usaChave = modoRespostasNaTela === "API_KEY";

  function salvar(evento: FormEvent) {
    evento.preventDefault();
    setGravado(false);
    const pedido: PedidoConfiguracaoIa = {
      modoGeral,
      assistente: {
        respostas: {
          modo: modoRespostas === "HERDAR" ? null : modoRespostas,
          provedor: provedorRespostas || null,
          modelo: modeloRespostas || null,
          // Em branco = mantém a chave guardada (a chave nunca volta do servidor)
          chave: removerChave ? null : chave.trim() || null,
          removerChave,
        },
        embeddings: {
          modo: modoEmbeddings,
          provedor: modoEmbeddings === "LOCAL" ? provedorEmbeddings || null : null,
          modelo: modoEmbeddings === "LOCAL" ? modeloEmbeddings || null : null,
        },
      },
    };
    gravar.mutate(pedido, {
      onSuccess: () => {
        setChave("");
        setRemoverChave(false);
        setGravado(true);
      },
    });
  }

  const motivos = gravar.error instanceof ErroApi ? (gravar.error.problema?.motivos ?? []) : [];

  return (
    <form onSubmit={salvar} onChange={() => setGravado(false)}>
      <section className="bloco">
        <h2>Modo geral do condomínio</h2>
        <EscolhaModo nome="modo-geral" legenda="Quem executa a IA" opcoes={opcoesModo} valor={modoGeral} aoMudar={setModoGeral} />
      </section>

      <section className="bloco">
        <h2>Assistente: respostas (chat)</h2>
        <EscolhaModo<ModoRespostas>
          nome="modo-respostas"
          legenda="Modo das respostas"
          opcoes={[
            {
              valor: "HERDAR",
              rotulo: "Herdar do geral",
              descricao: `Segue o modo geral (hoje: ${descricoesModo[modoGeral as keyof typeof descricoesModo]?.rotulo ?? modoGeral}).`,
            },
            ...opcoesModo,
          ]}
          valor={modoRespostas}
          aoMudar={setModoRespostas}
        />
        <p className="discreto">
          Modo efetivo gravado agora: <strong>{descricoesModo[respostas.modoEfetivo as keyof typeof descricoesModo]?.rotulo ?? respostas.modoEfetivo}</strong>
        </p>

        {usaChave && (
          <>
            <h3>Provedor e modelo</h3>
            {provedoresRespostas.length === 0 ? (
              <p className="aviso alerta">{erroCatalogo ?? "O catálogo não tem provedor de respostas."}</p>
            ) : (
              <SeletorModelo
                id="respostas"
                provedores={provedoresRespostas}
                provedor={provedorRespostas}
                modelo={modeloRespostas}
                aoMudar={(p, m) => {
                  setProvedorRespostas(p);
                  setModeloRespostas(m);
                }}
              />
            )}
            <h3>Chave de API do condomínio</h3>
            <CampoChave
              chaveCadastrada={respostas.chaveCadastrada}
              chaveFinal={respostas.chaveFinal}
              chave={chave}
              aoMudarChave={setChave}
              remover={removerChave}
              aoMudarRemover={(remover) => {
                setRemoverChave(remover);
                setChave("");
              }}
            />
          </>
        )}
        {!usaChave && respostas.chaveCadastrada && (
          <p className="discreto">
            Chave cadastrada terminando em ••••{respostas.chaveFinal}; fica guardada, mas não é usada neste modo.{" "}
            <button type="button" className="botao-link" onClick={() => setRemoverChave(!removerChave)}>
              {removerChave ? "Desfazer remoção" : "Remover chave"}
            </button>
            {removerChave && " (será removida ao salvar)"}
          </p>
        )}
      </section>

      <section className="bloco">
        <h2>Assistente: embeddings (busca por significado)</h2>
        <EscolhaModo<ModoEmbeddings>
          nome="modo-embeddings"
          legenda="Modo dos embeddings"
          opcoes={[
            { valor: "LOCAL", rotulo: "Local", descricao: "Modelo na infraestrutura do sistema; nenhum texto vai para fora." },
            { valor: "DESLIGADO", rotulo: "Desligado", descricao: "Sem busca por significado; a busca por palavra continua." },
          ]}
          valor={modoEmbeddings}
          aoMudar={setModoEmbeddings}
        />
        {modoEmbeddings === "LOCAL" &&
          (provedoresEmbeddings.length === 0 ? (
            <p className="aviso alerta">{erroCatalogo ?? "O catálogo não tem provedor local de embeddings."}</p>
          ) : (
            <SeletorModelo
              id="embeddings"
              provedores={provedoresEmbeddings}
              provedor={provedorEmbeddings}
              modelo={modeloEmbeddings}
              semPrecos
              aoMudar={(p, m) => {
                setProvedorEmbeddings(p);
                setModeloEmbeddings(m);
              }}
            />
          ))}
      </section>

      <div aria-live="polite">
        {gravar.isError && (
          <div className="aviso erro" role="alert">
            <p>{gravar.error.message}</p>
            {motivos.length > 0 && (
              <ul>
                {motivos.map((m) => (
                  <li key={m}>{m}</li>
                ))}
              </ul>
            )}
          </div>
        )}
        {gravado && <p className="aviso ok">Configuração gravada. O menu e a tela do Assistente já seguem o novo modo.</p>}
      </div>

      <div className="acoes">
        <button type="submit" className="botao" disabled={gravar.isPending}>
          {gravar.isPending ? "Gravando…" : "Salvar"}
        </button>
        {configuracao.atualizadoPor && configuracao.atualizadoEm ? (
          <span className="discreto">
            Última alteração por {configuracao.atualizadoPor} em {formatarDataHora(configuracao.atualizadoEm)}
          </span>
        ) : (
          <span className="discreto">Nunca gravada: valem os padrões.</span>
        )}
      </div>
    </form>
  );
}
