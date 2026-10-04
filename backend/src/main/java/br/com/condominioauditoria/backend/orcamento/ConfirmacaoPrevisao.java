package br.com.condominioauditoria.backend.orcamento;

import static br.com.condominioauditoria.backend.orcamento.DinheiroBr.formatar;

import br.com.condominioauditoria.backend.arquivo.Arquivo;
import br.com.condominioauditoria.backend.arquivo.ArquivoRepository;
import br.com.condominioauditoria.backend.arquivo.Categoria;
import br.com.condominioauditoria.backend.auditoria.RegistroAchados;
import br.com.condominioauditoria.backend.auditoria.RegistroAchados.Evidencia;
import br.com.condominioauditoria.backend.auditoria.RegraTetoFundoReserva;
import br.com.condominioauditoria.backend.condominio.Condominio;
import br.com.condominioauditoria.backend.condominio.CondominioRepository;
import br.com.condominioauditoria.backend.contabil.Fundo;
import br.com.condominioauditoria.backend.contabil.FundoRepository;
import br.com.condominioauditoria.backend.orcamento.PedidoConfirmacao.CodigoEfetivo;
import br.com.condominioauditoria.backend.orcamento.PedidoConfirmacao.LigacaoFundo;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.PrevisaoDetalhe;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Confirmação da PO pelo Admin (RF-03.1.3; RF-03.1.2 e Q29; ADR 0004, Decisão 8). Recusa com todos os motivos de uma
 * vez. Os valores lidos nunca são editados: a confirmação só registra exercício, ata, códigos efetivos das linhas
 * repetidas, ligação dos fundos e, quando a PO foi lida com divergência de soma, a ciência com justificativa.
 */
@Service
public class ConfirmacaoPrevisao {

    private static final Logger log = LoggerFactory.getLogger(ConfirmacaoPrevisao.class);
    private static final Pattern CODIGO = Pattern.compile("^\\d+(\\.\\d+)+$");

    private final CondominioRepository condominios;
    private final PrevisaoOrcamentariaRepository previsoes;
    private final LinhaPoRepository linhas;
    private final PoFundoRepository poFundos;
    private final EventoPrevisaoRepository eventos;
    private final ArquivoRepository arquivos;
    private final FundoRepository fundos;
    private final ConsultaPrevisao consulta;
    private final ReservaDaPo reserva;
    private final RegistroAchados achados;

    ConfirmacaoPrevisao(CondominioRepository condominios, PrevisaoOrcamentariaRepository previsoes,
            LinhaPoRepository linhas, PoFundoRepository poFundos, EventoPrevisaoRepository eventos,
            ArquivoRepository arquivos, FundoRepository fundos, ConsultaPrevisao consulta, ReservaDaPo reserva,
            RegistroAchados achados) {
        this.condominios = condominios;
        this.previsoes = previsoes;
        this.linhas = linhas;
        this.poFundos = poFundos;
        this.eventos = eventos;
        this.arquivos = arquivos;
        this.fundos = fundos;
        this.consulta = consulta;
        this.reserva = reserva;
        this.achados = achados;
    }

    @Transactional
    public PrevisaoDetalhe confirmar(UUID condominioId, UUID poId, PedidoConfirmacao pedido, String usuario) {
        Condominio condominio = condominios.travar(condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        PrevisaoOrcamentaria po = previsoes.findByIdAndCondominioId(poId, condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO não encontrada"));
        if (po.getEstado().travada()) {
            throw new ConfirmacaoRecusadaException(HttpStatus.CONFLICT, List.of("A PO já foi confirmada."));
        }
        List<LinhaPo> lidas = linhas.findByPrevisaoIdOrderByOrdem(po.getId());
        EstruturaPo estrutura = EstruturaPo.de(lidas);
        var avaliacao = consulta.avaliar(po, estrutura);

        List<String> motivos = new ArrayList<>();
        YearMonth inicio = mes(pedido.exercicioInicio(), "início", motivos);
        YearMonth fim = mes(pedido.exercicioFim(), "fim", motivos);
        if (inicio != null && fim != null && fim.isBefore(inicio)) {
            motivos.add("O fim do exercício (" + fim + ") é anterior ao início (" + inicio + ").");
        }
        Arquivo ata = validarAta(condominioId, pedido, motivos);
        validarDivergencia(po, pedido, avaliacao, motivos);
        Map<UUID, String> novosCodigos = validarCodigos(lidas, pedido.codigosEfetivos(), motivos);
        Map<LinhaPo, Fundo> ligacoes = validarFundos(condominio, estrutura, pedido.fundos(), motivos);
        if (!motivos.isEmpty()) {
            throw new ConfirmacaoRecusadaException(HttpStatus.UNPROCESSABLE_CONTENT, motivos);
        }

        Instant agora = Instant.now();
        List<PrevisaoOrcamentaria> substituidas = sobrepostas(condominioId, po, inicio, fim, pedido.reaprovacao());
        int versao = substituidas.stream().map(PrevisaoOrcamentaria::getVersao).filter(Objects::nonNull)
                .max(Integer::compare).orElse(0) + 1;
        for (PrevisaoOrcamentaria anterior : substituidas) {
            anterior.substituirAPartirDe(inicio);
            previsoes.save(anterior);
            eventos.save(new EventoPrevisao(anterior, EventoPrevisao.SUBSTITUIDA, usuario, agora, null,
                    "Substituída a partir de " + inicio + " pela versão " + versao + " (PO " + po.getId() + ")."));
        }

        Map<UUID, LinhaPo> porId = lidas.stream().collect(Collectors.toMap(LinhaPo::getId, Function.identity()));
        List<String> trocas = new ArrayList<>();
        novosCodigos.forEach((id, codigo) -> {
            LinhaPo l = porId.get(id);
            trocas.add("%s (ordem %d, %s) → %s".formatted(l.getCodigoImpresso(), l.getOrdem(), l.getDescricao(), codigo));
            l.definirCodigoEfetivo(codigo);
        });
        linhas.saveAll(lidas);
        ligacoes.forEach((linha, fundo) -> poFundos.save(new PoFundo(po.getId(), linha.getId(), fundo.getId())));

        boolean ciente = po.getEstado() == EstadoPrevisao.LIDA_COM_DIVERGENCIA;
        String justificativa = ciente ? pedido.justificativa().trim() : null;
        po.confirmar(versao, inicio, fim, ata == null ? null : ata.getId(), pedido.semAta(), pedido.dataAprovacao(),
                ciente, justificativa, usuario, agora);
        previsoes.save(po);
        eventos.save(new EventoPrevisao(po, EventoPrevisao.CONFIRMADA, usuario, agora, justificativa,
                detalheDoEvento(po, ata, trocas, ligacoes, avaliacao, substituidas)));

        registrarAchadoDaReserva(po, estrutura);
        log.info("PO {} confirmada: condominio={} versao={} exercicio={} a {} por={} ciente={}", po.getId(),
                condominioId, versao, inicio, fim, usuario, ciente);
        return consulta.detalhe(po);
    }

    private static YearMonth mes(String texto, String qual, List<String> motivos) {
        if (texto == null || texto.isBlank()) {
            motivos.add("Informe o " + qual + " do exercício (AAAA-MM).");
            return null;
        }
        try {
            return YearMonth.parse(texto.trim());
        } catch (DateTimeParseException e) {
            motivos.add("O " + qual + " do exercício deve estar no formato AAAA-MM: " + texto);
            return null;
        }
    }

    private Arquivo validarAta(UUID condominioId, PedidoConfirmacao pedido, List<String> motivos) {
        if (pedido.semAta() && pedido.ataArquivoId() != null) {
            motivos.add("Informe a ata ou marque \"sem ata\", não os dois.");
            return null;
        }
        if (!pedido.semAta() && pedido.ataArquivoId() == null) {
            motivos.add("Informe a ata que aprovou a PO ou marque \"sem ata\".");
            return null;
        }
        if (pedido.semAta()) {
            return null;
        }
        Arquivo ata = arquivos.findByIdAndCondominioId(pedido.ataArquivoId(), condominioId).orElse(null);
        if (ata == null) {
            motivos.add("A ata informada não é um arquivo deste condomínio.");
        } else if (ata.getCategoria() != Categoria.ATA) {
            motivos.add("O arquivo \"" + ata.getNomeOriginal() + "\" não está na categoria \"" + Categoria.ATA.rotulo()
                    + "\".");
        }
        if (pedido.dataAprovacao() == null) {
            motivos.add("Informe a data da assembleia que aprovou a PO.");
        }
        return ata;
    }

    /** Q29: PO lida com divergência de soma só é confirmada "ciente da divergência", com justificativa. */
    private static void validarDivergencia(PrevisaoOrcamentaria po, PedidoConfirmacao pedido,
            AvaliacaoLeituraPo.Resultado avaliacao, List<String> motivos) {
        if (po.getEstado() != EstadoPrevisao.LIDA_COM_DIVERGENCIA) {
            return;
        }
        String somas = String.join("; ", avaliacao.divergencias());
        if (!pedido.cienteDivergencia()) {
            motivos.add("A PO foi lida com divergência (" + somas + "). Para confirmar, marque \"ciente da"
                    + " divergência\" e informe a justificativa; os cálculos usam a soma das linhas.");
        } else if (pedido.justificativa() == null || pedido.justificativa().isBlank()) {
            motivos.add("A confirmação ciente da divergência exige justificativa.");
        }
    }

    /**
     * Códigos efetivos: só para linhas cujo código impresso se repete, no mesmo grupo e com o mesmo número de níveis.
     * Depois de aplicados, nenhum código efetivo pode se repetir.
     */
    private static Map<UUID, String> validarCodigos(List<LinhaPo> lidas, List<CodigoEfetivo> pedidos,
            List<String> motivos) {
        Map<UUID, LinhaPo> porId = lidas.stream().collect(Collectors.toMap(LinhaPo::getId, Function.identity()));
        Map<String, Long> impressos = lidas.stream()
                .collect(Collectors.groupingBy(LinhaPo::getCodigoImpresso, Collectors.counting()));
        Map<UUID, String> novos = new LinkedHashMap<>();
        for (CodigoEfetivo c : pedidos) {
            LinhaPo l = c.linhaId() == null ? null : porId.get(c.linhaId());
            String codigo = c.codigo() == null ? "" : c.codigo().trim();
            if (l == null) {
                motivos.add("Código efetivo para uma linha que não é desta PO.");
            } else if (impressos.get(l.getCodigoImpresso()) < 2) {
                motivos.add("O código da linha " + l.getCodigoImpresso() + " não se repete e não pode ser trocado.");
            } else if (!CODIGO.matcher(codigo).matches() || !mesmoGrupo(l.getCodigoImpresso(), codigo)) {
                motivos.add("Código efetivo \"" + codigo + "\" inválido para a linha " + l.getCodigoImpresso()
                        + " (ordem " + l.getOrdem() + "): use um código do mesmo grupo, como "
                        + pai(l.getCodigoImpresso()) + ".N.");
            } else if (novos.put(l.getId(), codigo) != null) {
                motivos.add("Mais de um código efetivo para a linha " + l.getCodigoImpresso() + " (ordem "
                        + l.getOrdem() + ").");
            }
        }
        Map<String, List<LinhaPo>> efetivos = new LinkedHashMap<>();
        lidas.forEach(l -> efetivos.computeIfAbsent(novos.getOrDefault(l.getId(), l.getCodigoEfetivo()),
                k -> new ArrayList<>()).add(l));
        efetivos.forEach((codigo, mesmas) -> {
            if (mesmas.size() > 1) {
                motivos.add("Código repetido sem código efetivo distinto: " + codigo + " (ordens " + mesmas.stream()
                        .map(l -> String.valueOf(l.getOrdem())).collect(Collectors.joining(", "))
                        + "). Informe um código distinto para a linha repetida; código repetido não é divergência de"
                        + " soma.");
            }
        });
        return novos;
    }

    private static boolean mesmoGrupo(String impresso, String efetivo) {
        return pai(impresso).equals(pai(efetivo)) && impresso.split("\\.").length == efetivo.split("\\.").length;
    }

    private static String pai(String codigo) {
        int ponto = codigo.lastIndexOf('.');
        return ponto < 0 ? "" : codigo.substring(0, ponto);
    }

    /** Cada linha de fundo ligada a um fundo do fluxo, distinto e que não seja o fundo ordinário (Condomínio). */
    private Map<LinhaPo, Fundo> validarFundos(Condominio condominio, EstruturaPo estrutura, List<LigacaoFundo> pedidos,
            List<String> motivos) {
        List<LinhaPo> deFundo = estrutura.fundos().map(EstruturaPo.Grupo::linhas).orElse(List.of());
        Map<UUID, LinhaPo> porId = deFundo.stream().collect(Collectors.toMap(LinhaPo::getId, Function.identity()));
        Map<UUID, Fundo> doCondominio = fundos.findByCondominioId(condominio.getId()).stream()
                .collect(Collectors.toMap(Fundo::getId, Function.identity()));
        Map<LinhaPo, Fundo> ligacoes = new LinkedHashMap<>();
        Set<UUID> usados = new HashSet<>();
        for (LigacaoFundo f : pedidos) {
            LinhaPo linha = f.linhaId() == null ? null : porId.get(f.linhaId());
            Fundo fundo = f.fundoId() == null ? null : doCondominio.get(f.fundoId());
            if (linha == null) {
                motivos.add("Ligação de fundo para uma linha que não é linha de fundo desta PO.");
            } else if (fundo == null) {
                motivos.add("O fundo informado para a linha " + linha.getCodigoImpresso() + " não é deste condomínio.");
            } else if (fundo.getId().equals(condominio.getFundoOrdinarioId())) {
                motivos.add("O fundo \"" + fundo.getNome() + "\" é o fundo ordinário e não pode ser ligado à linha "
                        + linha.getCodigoImpresso() + ".");
            } else if (!usados.add(fundo.getId())) {
                motivos.add("O fundo \"" + fundo.getNome() + "\" foi ligado a mais de uma linha de fundo.");
            } else if (ligacoes.put(linha, fundo) != null) {
                motivos.add("Mais de um fundo para a linha " + linha.getCodigoImpresso() + ".");
            }
        }
        deFundo.stream().filter(l -> !ligacoes.containsKey(l) && pedidos.stream()
                .noneMatch(p -> l.getId().equals(p.linhaId())))
                .forEach(l -> motivos.add("Ligue a linha " + l.getCodigoImpresso() + " " + l.getDescricao()
                        + " a um fundo do fluxo."));
        return ligacoes;
    }

    /** Uma PO por mês: sobreposição só com reaprovação, que cobre até o fim da PO substituída. */
    private List<PrevisaoOrcamentaria> sobrepostas(UUID condominioId, PrevisaoOrcamentaria po, YearMonth inicio,
            YearMonth fim, boolean reaprovacao) {
        List<VigenciaPo> conflitos = previsoes.findByCondominioIdAndEstadoIn(condominioId,
                        EnumSet.of(EstadoPrevisao.CONFIRMADA, EstadoPrevisao.SUBSTITUIDA)).stream()
                .filter(p -> !p.getId().equals(po.getId()))
                .map(VigenciaPo::de).flatMap(java.util.Optional::stream)
                .filter(v -> v.sobrepoe(inicio, fim)).toList();
        if (conflitos.isEmpty()) {
            return List.of();
        }
        String lista = conflitos.stream().map(v -> "versão " + v.previsao().getVersao() + " (" + v.periodo() + ")")
                .collect(Collectors.joining(", "));
        if (!reaprovacao) {
            throw new ConfirmacaoRecusadaException(HttpStatus.CONFLICT, List.of("Já existe PO confirmada para "
                    + "meses deste exercício: " + lista + ". Só uma PO vale para cada mês; se esta é uma reaprovação,"
                    + " confirme como reaprovação."));
        }
        List<String> motivos = conflitos.stream().filter(v -> fim.isBefore(v.fim()))
                .map(v -> "A reaprovação precisa cobrir até " + v.fim() + ", fim da versão " + v.previsao().getVersao()
                        + " que ela substitui.")
                .toList();
        if (!motivos.isEmpty()) {
            throw new ConfirmacaoRecusadaException(HttpStatus.CONFLICT, motivos);
        }
        return conflitos.stream().map(VigenciaPo::previsao).toList();
    }

    private void registrarAchadoDaReserva(PrevisaoOrcamentaria po, EstruturaPo estrutura) {
        if (!(reserva.avaliar(po, estrutura) instanceof ReservaDaPo.Avaliada a) || !a.avaliacao().acimaDoTeto()) {
            return;
        }
        LinhaPo l = a.linha();
        String descricao = ("Fundo de reserva previsto na PO (linha %s): %s por mês, %s do previsto do mês (%s). "
                + "O teto da Conv. 20.1 é %s. Verificar a ata que aprovou a PO.").formatted(l.getCodigoEfetivo(),
                formatar(l.getOrcado()), a.avaliacao().percentualExibido(), formatar(po.getPrevistoMes()),
                a.avaliacao().tetoExibido());
        achados.registrar(po.getCondominioId(), RegraTetoFundoReserva.CODIGO, RegraTetoFundoReserva.VERSAO,
                RegraTetoFundoReserva.SEVERIDADE, po.getExercicioInicio(), alvo(po, l), descricao,
                List.of(new Evidencia(po.getArquivoId(), po.getSha256(), l.getPagina(),
                        "PO, linha %s %s (ordem %d): %s".formatted(l.getCodigoEfetivo(), l.getDescricao(),
                                l.getOrdem(), formatar(l.getOrcado())), l.getId())));
    }

    static String alvo(PrevisaoOrcamentaria po, LinhaPo linha) {
        return prefixoAlvo(po) + "linha:" + linha.getId();
    }

    static String prefixoAlvo(PrevisaoOrcamentaria po) {
        return "previsao:" + po.getId() + ":";
    }

    private static String detalheDoEvento(PrevisaoOrcamentaria po, Arquivo ata, List<String> trocas,
            Map<LinhaPo, Fundo> ligacoes, AvaliacaoLeituraPo.Resultado avaliacao,
            List<PrevisaoOrcamentaria> substituidas) {
        List<String> partes = new ArrayList<>();
        partes.add("Exercício " + po.getExercicioInicio() + " a " + po.getExercicioFim() + "; versão " + po.getVersao());
        partes.add(ata == null ? "Sem ata (pendência de implantação)"
                : "Ata: " + ata.getNomeOriginal() + " (" + ata.getSha256() + "), aprovada em " + po.getDataAprovacao());
        partes.add("Códigos efetivos: " + (trocas.isEmpty() ? "nenhuma troca" : String.join("; ", trocas)));
        partes.add("Fundos: " + (ligacoes.isEmpty() ? "nenhuma linha de fundo" : ligacoes.entrySet().stream()
                .map(e -> e.getKey().getCodigoImpresso() + " " + e.getKey().getDescricao() + " → " + e.getValue().getNome())
                .collect(Collectors.joining("; "))));
        if (!avaliacao.divergencias().isEmpty()) {
            partes.add("Conferências que falharam (confirmada ciente da divergência): "
                    + String.join("; ", avaliacao.divergencias()));
        }
        if (!avaliacao.arredondamentos().isEmpty()) {
            partes.add("Diferenças tratadas como arredondamento: " + String.join("; ", avaliacao.arredondamentos()));
        }
        if (!substituidas.isEmpty()) {
            Map<UUID, Integer> versoes = new HashMap<>();
            substituidas.forEach(s -> versoes.put(s.getId(), s.getVersao()));
            partes.add("Reaprovação: substitui a partir de " + po.getExercicioInicio() + " " + versoes.entrySet()
                    .stream().map(e -> "a versão " + e.getValue() + " (PO " + e.getKey() + ")")
                    .collect(Collectors.joining(", ")));
        }
        return String.join("\n", partes);
    }
}
