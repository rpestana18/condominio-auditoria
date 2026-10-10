import type { ModeloIa, ProvedorIa } from "../../api/tipos";

/** "2.00" vira "2,00": só troca o separador do texto decimal exato que veio da API (sem conta). */
const preco = (texto: string) => texto.replace(".", ",");

function rotuloModelo(m: ModeloIa): string {
  const precos = `US$ ${preco(m.inputPricePerMillionUsd)} entrada / US$ ${preco(m.outputPricePerMillionUsd)} saída por milhão de tokens`;
  return `${m.name}${m.isDefault ? " (padrão)" : ""} · ${precos}`;
}

interface Props {
  id: string;
  provedores: ProvedorIa[];
  provedor: string;
  modelo: string;
  aoMudar: (provedor: string, modelo: string) => void;
  /** Esconde preços (embeddings locais custam zero). */
  semPrecos?: boolean;
}

/** Provedor e modelo do catálogo. Modelo vazio = o padrão do provedor (o backend escolhe). */
export function SeletorModelo({ id, provedores, provedor, modelo, aoMudar, semPrecos = false }: Props) {
  const escolhido = provedores.find((p) => p.code === provedor);
  return (
    <div className="filtros">
      <label className="campo">
        Provedor
        <select id={`${id}-provedor`} value={provedor} onChange={(e) => aoMudar(e.target.value, "")}>
          {!escolhido && <option value="">Escolha…</option>}
          {provedores.map((p) => (
            <option key={p.code} value={p.code}>
              {p.name}
            </option>
          ))}
        </select>
      </label>
      <label className="campo">
        Modelo
        <select id={`${id}-modelo`} value={modelo} onChange={(e) => aoMudar(provedor, e.target.value)} disabled={!escolhido}>
          <option value="">Padrão do provedor</option>
          {escolhido?.models.map((m) => (
            <option key={m.id} value={m.id}>
              {semPrecos ? `${m.name}${m.isDefault ? " (padrão)" : ""}` : rotuloModelo(m)}
            </option>
          ))}
        </select>
      </label>
    </div>
  );
}
