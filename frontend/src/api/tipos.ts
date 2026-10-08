// Tipos gerados a partir de contracts/openapi.yaml (pnpm gerar-api). Não edite esquema.ts à mão.
import type { components } from "./esquema";

type Esquemas = components["schemas"];

export type Categoria = Esquemas["Categoria"];
export type StatusArquivo = Esquemas["StatusArquivo"];
export type CategoriaDto = Esquemas["CategoriaDto"];
export type NovaCategoria = Esquemas["NovaCategoria"];
export type UsuarioLogado = Esquemas["UsuarioLogado"];
export type ArquivoResumo = Esquemas["ArquivoResumo"];
export type ArquivoDetalhe = Esquemas["ArquivoDetalhe"];
export type Painel = Esquemas["Painel"];
export type Problema = Esquemas["Problema"];
export type Perfil = UsuarioLogado["perfis"][number];

// Previsto × realizado (ADR 0004)
export type EstadoPrevisao = Esquemas["EstadoPrevisao"];
export type PrevisaoResumo = Esquemas["PrevisaoResumo"];
export type PrevisaoDetalhe = Esquemas["PrevisaoDetalhe"];
export type LinhaPo = Esquemas["LinhaPo"];
export type ConferenciaPo = Esquemas["ConferenciaPo"];
export type AvisoPo = Esquemas["AvisoPo"];
export type PedidoConfirmacao = Esquemas["PedidoConfirmacao"];
export type MarcaLinha = NonNullable<LinhaPo["marca"]>;

// De-para
export type TipoDestino = Esquemas["TipoDestino"];
export type EstadoDepara = Esquemas["EstadoDepara"];
export type OrigemDepara = Esquemas["OrigemDepara"];
export type FiltroDepara = Esquemas["FiltroDepara"];
export type DestinoDepara = Esquemas["DestinoDepara"];
export type ContaDepara = Esquemas["ContaDepara"];
export type DeparaLista = Esquemas["DeparaLista"];
export type PedidoDestino = Esquemas["PedidoDestino"];
export type PedidoLote = Esquemas["PedidoLote"];
export type ResultadoLote = Esquemas["ResultadoLote"];
export type ContaIgnorada = Esquemas["ContaIgnorada"];
export type ResultadoSugestoes = Esquemas["ResultadoSugestoes"];
export type ResultadoPlanilha = Esquemas["ResultadoPlanilha"];
export type EventoDepara = Esquemas["EventoDepara"];

// Resultado do cálculo (vem pronto do backend; a tela só mostra)
export type PrevistoRealizado = Esquemas["PrevistoRealizado"];
export type MesPrevistoRealizado = PrevistoRealizado["meses"][number];
export type TotaisPrevistoRealizado = NonNullable<PrevistoRealizado["totais"]>;
export type Regra20 = NonNullable<PrevistoRealizado["regra20"]>;
export type ConferenciaFluxo = NonNullable<PrevistoRealizado["conferencia"]>;
export type FundoPrevistoRealizado = PrevistoRealizado["fundos"][number];
export type GrupoPrevistoRealizado = Esquemas["GrupoPrevistoRealizado"];
export type LinhaPrevistoRealizado = GrupoPrevistoRealizado["linhas"][number];
export type BlocoPrevistoRealizado = Esquemas["BlocoPrevistoRealizado"];
export type EvidenciaLancamento = Esquemas["EvidenciaLancamento"];
export type FluxoUsado = Esquemas["FluxoUsado"];

// Fundos, realocação, trilha da PO e achados (passos 8 e 9)
export type FundoFluxo = Esquemas["FundoFluxo"];
export type EventoPrevisao = Esquemas["EventoPrevisao"];
export type Realocacao = Esquemas["Realocacao"];
export type PedidoRealocacao = Esquemas["PedidoRealocacao"];
export type EstadoAchado = Esquemas["EstadoAchado"];
export type Achado = Esquemas["Achado"];
// Indexação para a busca (RF-04.7) e módulos contratáveis (RF-10)
export type IndexacaoArquivo = Esquemas["IndexacaoArquivo"];
export type SituacaoIndexacao = Esquemas["SituacaoIndexacao"];
export type ContextoCondominio = Esquemas["ContextoCondominio"];
export type ModuloDoCondominio = Esquemas["ModuloDoCondominio"];
export type AlteracaoModulo = Esquemas["AlteracaoModulo"];
export type EventoModulo = Esquemas["EventoModulo"];
export type PeriodoAtivo = Esquemas["PeriodoAtivo"];
export type FuncaoUso = Esquemas["FuncaoUso"];
export type TotalUso = Esquemas["TotalUso"];
export type UsoDoPeriodo = Esquemas["UsoDoPeriodo"];

// Assistente (RF-04.8 a 04.18) e configuração de IA do condomínio (RF-09.6)
export type ModoIa = Esquemas["ModoIa"];
export type ContextoAssistente = Esquemas["ContextoAssistente"];
export type ProvedorIa = Esquemas["ProvedorIa"];
export type ModeloIa = Esquemas["ModeloIa"];
export type ConfiguracaoIa = Esquemas["ConfiguracaoIa"];
export type PedidoConfiguracaoIa = Esquemas["PedidoConfiguracaoIa"];
export type FiltrosDocumentos = Esquemas["FiltrosDocumentos"];
export type PedidoPergunta = Esquemas["PedidoPergunta"];
export type TrocaHistorico = NonNullable<PedidoPergunta["historico"]>[number];
export type RespostaAssistente = Esquemas["RespostaAssistente"];
export type ParagrafoDocumentos = RespostaAssistente["nosDocumentos"][number];
export type DadoGravado = Esquemas["DadoGravado"];
export type TrechoDocumento = Esquemas["TrechoDocumento"];
export type CitacaoDocumento = Esquemas["CitacaoDocumento"];
export type PedidoBuscaDocumentos = Esquemas["PedidoBuscaDocumentos"];

// Análise da PO (ADR 0005): exercícios, comparação entre exercícios, coluna impressa e rubricas
export type ProrrogacaoPo = Esquemas["ProrrogacaoPo"];
export type ResumoDepara = Esquemas["ResumoDepara"];
export type Exercicio = Esquemas["Exercicio"];
export type MesExercicio = Exercicio["meses"][number];
export type ComparacaoExercicios = Esquemas["ComparacaoExercicios"];
export type ExercicioComparado = ComparacaoExercicios["exercicios"][number];
export type ResumoComparado = ComparacaoExercicios["resumo"][number];
export type GrupoComparado = ComparacaoExercicios["grupos"][number];
export type RubricaComparada = ComparacaoExercicios["linhas"][number];
export type LinhaSemCorrespondencia = ComparacaoExercicios["semCorrespondencia"][number];
export type ValorComparado = Esquemas["ValorComparado"];
export type VariacaoExercicio = Esquemas["VariacaoExercicio"];
export type ConferenciaColuna = Esquemas["ConferenciaColuna"];
export type GrupoConferenciaColuna = ConferenciaColuna["grupos"][number];
export type EstadoRubrica = Esquemas["EstadoRubrica"];
export type OrigemRubrica = Esquemas["OrigemRubrica"];
export type FiltroRubrica = Esquemas["FiltroRubrica"];
export type Rubrica = Esquemas["Rubrica"];
export type LinhaComRubrica = Esquemas["LinhaComRubrica"];
export type ResumoRubricas = Esquemas["ResumoRubricas"];
export type RubricasDaPo = Esquemas["RubricasDaPo"];
export type PedidoRubricaLinha = Esquemas["PedidoRubricaLinha"];
export type PedidoLoteRubrica = Esquemas["PedidoLoteRubrica"];
export type ResultadoLoteRubrica = Esquemas["ResultadoLoteRubrica"];
export type ResultadoSugestoesRubrica = Esquemas["ResultadoSugestoesRubrica"];
export type EventoRubrica = Esquemas["EventoRubrica"];
