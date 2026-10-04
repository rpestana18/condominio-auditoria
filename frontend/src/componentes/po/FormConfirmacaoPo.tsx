import { useState, type FormEvent } from "react";
import { ErroApi } from "../../api/cliente";
import { useArquivos } from "../../api/consultas";
import { useConfirmarPrevisao, useFundos } from "../../api/consultasOrcamento";
import type { PedidoConfirmacao, PrevisaoDetalhe } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { formatarData, formatarMoeda } from "../../formato";

/**
 * Confirmação da PO pelo Admin (RF-03.1.3): exercício, ata (ou "sem ata"), código distinto para as linhas
 * com código repetido e ligação de cada linha 1.9.x a um fundo do fluxo (RF-03.1.9). Os valores lidos não
 * são editados. Quem valida tudo é o backend; a tela só mostra os motivos de uma recusa.
 */
export function FormConfirmacaoPo({ detalhe }: { detalhe: PrevisaoDetalhe }) {
  const { condominioId } = useSessao();
  const confirmar = useConfirmarPrevisao(condominioId, detalhe.previsao.id);
  const { data: atas = [] } = useArquivos(condominioId, "ATA");
  const { data: fundosDoCondominio = [] } = useFundos(condominioId);

  const [inicio, setInicio] = useState(detalhe.previsao.exercicioInicio ?? "");
  const [fim, setFim] = useState(detalhe.previsao.exercicioFim ?? "");
  const [semAta, setSemAta] = useState(false);
  const [ataId, setAtaId] = useState("");
  const [dataAprovacao, setDataAprovacao] = useState("");
  const [codigos, setCodigos] = useState<Record<string, string>>(() =>
    Object.fromEntries(detalhe.codigosRepetidos.flatMap((r) => r.linhas.map((l) => [l.linhaId, l.codigoEfetivo]))),
  );
  const [fundos, setFundos] = useState<Record<string, string>>(() =>
    Object.fromEntries(detalhe.fundos.map((f) => [f.linhaId, f.fundoId])),
  );
  const [reaprovacao, setReaprovacao] = useState(false);
  const [ciente, setCiente] = useState(false);
  const [justificativa, setJustificativa] = useState("");

  const comDivergencia = detalhe.previsao.estado === "LIDA_COM_DIVERGENCIA";
  const linhasDeFundo = detalhe.linhas.filter((l) => l.linhaDeFundo && l.tipo === "LINHA");
  // Fundos do fluxo pelo nome impresso. O fundo ordinário (fundo Condomínio) não pode ser ligado a 1.9.x.
  const fundosDoFluxo = fundosDoCondominio.filter((f) => !f.ordinario);

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    const pedido: PedidoConfirmacao = {
      exercicioInicio: inicio,
      exercicioFim: fim,
      semAta,
      ataArquivoId: semAta ? null : ataId || null,
      dataAprovacao: semAta ? null : dataAprovacao || null,
      codigosEfetivos: Object.entries(codigos).map(([linhaId, codigo]) => ({ linhaId, codigo })),
      fundos: Object.entries(fundos)
        .filter(([, fundoId]) => fundoId)
        .map(([linhaId, fundoId]) => ({ linhaId, fundoId })),
      reaprovacao,
      cienteDivergencia: ciente,
      justificativa: ciente ? justificativa : null,
    };
    confirmar.mutate(pedido);
  }

  const motivos = confirmar.error instanceof ErroApi ? (confirmar.error.problema?.motivos ?? []) : [];

  return (
    <form className="bloco formulario" onSubmit={enviar}>
      <h2>Confirmar a PO</h2>

      <fieldset>
        <legend>Exercício</legend>
        <label className="campo">
          Mês inicial
          <input type="month" required value={inicio} onChange={(e) => setInicio(e.target.value)} />
        </label>
        <label className="campo">
          Mês final
          <input type="month" required value={fim} onChange={(e) => setFim(e.target.value)} />
        </label>
      </fieldset>

      <fieldset>
        <legend>Ata que aprovou a PO</legend>
        <label className="campo-linha">
          <input type="checkbox" checked={semAta} onChange={(e) => setSemAta(e.target.checked)} />
          Sem ata (fica como pendência de implantação)
        </label>
        {!semAta && (
          <>
            <label className="campo">
              Ata (arquivos da categoria Atas)
              <select required value={ataId} onChange={(e) => setAtaId(e.target.value)}>
                <option value="">Escolha a ata</option>
                {atas.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.nome} · enviada em {formatarData(a.enviadoEm)}
                  </option>
                ))}
              </select>
            </label>
            <label className="campo">
              Data da assembleia
              <input type="date" required value={dataAprovacao} onChange={(e) => setDataAprovacao(e.target.value)} />
            </label>
          </>
        )}
      </fieldset>

      {detalhe.codigosRepetidos.length > 0 && (
        <fieldset>
          <legend>Códigos repetidos na PO</legend>
          <p className="discreto">Dê um código distinto a uma das linhas. Nenhuma linha é descartada nem somada à outra.</p>
          {detalhe.codigosRepetidos.map((r) =>
            r.linhas.map((l) => (
              <label key={l.linhaId} className="campo">
                {r.codigoImpresso} · {l.descricao}
                <input
                  required
                  value={codigos[l.linhaId] ?? ""}
                  onChange={(e) => setCodigos((atual) => ({ ...atual, [l.linhaId]: e.target.value }))}
                />
              </label>
            )),
          )}
        </fieldset>
      )}

      {linhasDeFundo.length > 0 && (
        <fieldset>
          <legend>Fundos ligados às linhas de fundos</legend>
          <p className="discreto">
            Cada linha recebe no máximo um fundo, e cada fundo atende no máximo uma linha. Nomes como impressos no fluxo.
          </p>
          {linhasDeFundo.map((l) => (
            <label key={l.id} className="campo">
              {l.codigoEfetivo} {l.descricao} · {formatarMoeda(l.orcado)}/mês
              <select
                value={fundos[l.id] ?? ""}
                onChange={(e) => setFundos((atual) => ({ ...atual, [l.id]: e.target.value }))}
              >
                <option value="">Sem fundo ligado</option>
                {fundosDoFluxo.map((f) => (
                  <option key={f.id} value={f.id}>
                    {f.nome}
                  </option>
                ))}
              </select>
            </label>
          ))}
        </fieldset>
      )}

      <fieldset>
        <legend>Outras indicações</legend>
        <label className="campo-linha">
          <input type="checkbox" checked={reaprovacao} onChange={(e) => setReaprovacao(e.target.checked)} />
          É reaprovação (substitui a PO confirmada nos mesmos meses, a partir do início desta)
        </label>
        {comDivergencia && (
          <>
            <label className="campo-linha">
              <input type="checkbox" checked={ciente} onChange={(e) => setCiente(e.target.checked)} />
              Confirmar ciente da divergência de soma (o erro está no próprio documento)
            </label>
            {ciente && (
              <label className="campo">
                Justificativa (obrigatória, vai para a trilha)
                <textarea required rows={3} value={justificativa} onChange={(e) => setJustificativa(e.target.value)} />
              </label>
            )}
          </>
        )}
      </fieldset>

      {confirmar.isError && (
        <div className="aviso erro">
          {confirmar.error.message}
          {motivos.length > 0 && (
            <ul>
              {motivos.map((m) => (
                <li key={m}>{m}</li>
              ))}
            </ul>
          )}
        </div>
      )}
      {confirmar.isSuccess && <p className="aviso ok">PO confirmada.</p>}
      <div className="acoes">
        <button className="botao" type="submit" disabled={confirmar.isPending}>
          {confirmar.isPending ? "Confirmando…" : "Confirmar a PO"}
        </button>
      </div>
    </form>
  );
}
