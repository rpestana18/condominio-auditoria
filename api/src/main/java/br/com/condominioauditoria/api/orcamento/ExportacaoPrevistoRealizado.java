package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.condominio.Condominio;
import br.com.condominioauditoria.api.condominio.CondominioRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exportação do previsto × realizado em PDF ou Excel (RF-03.1.14), com os mesmos filtros da tela. Usa o mesmo
 * {@link CalculoPrevistoRealizado} da consulta: os números do arquivo são os do JSON, por construção.
 */
@Service
public class ExportacaoPrevistoRealizado {

    public enum Formato {
        PDF("application/pdf", "pdf"),
        XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

        final String tipo;
        final String extensao;

        Formato(String tipo, String extensao) {
            this.tipo = tipo;
            this.extensao = extensao;
        }

        static Formato de(String texto) {
            String t = texto == null ? "" : texto.trim().toUpperCase(Locale.ROOT);
            try {
                return valueOf(t);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato deve ser pdf ou xlsx: " + texto);
            }
        }
    }

    public record ArquivoExportado(String nome, String tipo, byte[] conteudo) {
    }

    private final CondominioRepository condominios;
    private final ConsultaPrevistoRealizado consulta;
    private final RelatorioPdf pdf;
    private final RelatorioExcel excel;

    ExportacaoPrevistoRealizado(CondominioRepository condominios, ConsultaPrevistoRealizado consulta, RelatorioPdf pdf,
            RelatorioExcel excel) {
        this.condominios = condominios;
        this.consulta = consulta;
        this.pdf = pdf;
        this.excel = excel;
    }

    @Transactional(readOnly = true)
    public ArquivoExportado exportar(UUID condominioId, String periodo, UUID poId, UUID fundoId, String formatoTexto,
            String geradoPor) {
        Formato formato = Formato.de(formatoTexto);
        Condominio condominio = condominios.findById(condominioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        var calculo = consulta.calcular(condominioId, periodo, poId, fundoId);
        String fundo = fundoId == null ? null : fundoId.equals(condominio.getFundoOrdinarioId())
                ? "Condomínio (fundo ordinário: " + consulta.fundoDoFiltro(condominioId, fundoId).getNome() + ")"
                : consulta.fundoDoFiltro(condominioId, fundoId).getNome();
        RelatorioPrevistoRealizado rel = RelatorioPrevistoRealizado.montar(condominio.getNome(), fundo, calculo,
                geradoPor, Instant.now());
        byte[] conteudo = formato == Formato.PDF ? pdf.gerar(rel) : excel.gerar(rel);
        String nome = "previsto-realizado-" + calculo.resultado().periodo() + "." + formato.extensao;
        return new ArquivoExportado(nome, formato.tipo, conteudo);
    }
}
