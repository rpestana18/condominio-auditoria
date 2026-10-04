import { formatarMoeda } from "../../formato";

interface Props {
  valor: number;
  /** Abre os lançamentos que compõem o número (RF-03.1.12). */
  aoAbrir: () => void;
  className?: string;
}

/** Número em reais que leva aos lançamentos de origem. */
export function ValorComFonte({ valor, aoAbrir, className }: Props) {
  return (
    <button className={`valor-fonte ${className ?? ""}`} onClick={aoAbrir} title="Ver os lançamentos de origem">
      {formatarMoeda(valor)}
    </button>
  );
}
