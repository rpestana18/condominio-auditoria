package br.com.condominioauditoria.backend.orcamento;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * PDF do previsto × realizado (RF-03.1.14; ADR 0004, Decisão 6): template Thymeleaf em {@code relatorios/} e
 * OpenHTMLtoPDF, com a fonte DejaVu Sans embutida (acentos). Sem gráfico. O PDF não carrega nada de fora: nenhum
 * link, imagem ou folha de estilo externa é resolvido.
 */
@Component
public class RelatorioPdf {

    static final String TEMPLATE = "previsto-realizado";
    private static final String FONTE = "DejaVu Sans";

    private final ITemplateEngine motor;

    public RelatorioPdf() {
        this.motor = motor();
    }

    /** Motor próprio dos relatórios (não usa a pasta de templates de telas, que o backend não tem). */
    static ITemplateEngine motor() {
        ClassLoaderTemplateResolver resolvedor = new ClassLoaderTemplateResolver();
        resolvedor.setPrefix("relatorios/");
        resolvedor.setSuffix(".html");
        resolvedor.setTemplateMode(TemplateMode.HTML);
        resolvedor.setCharacterEncoding("UTF-8");
        resolvedor.setCacheable(true);
        SpringTemplateEngine motor = new SpringTemplateEngine();
        motor.setTemplateResolver(resolvedor);
        return motor;
    }

    /** HTML intermediário (o mesmo que vira PDF); usado também nos testes. */
    String html(RelatorioPrevistoRealizado rel) {
        Context ctx = new Context(Locale.forLanguageTag("pt-BR"));
        ctx.setVariable("rel", rel);
        ctx.setVariable("r", rel.resultado());
        ctx.setVariable("f", new FormatosRelatorio());
        return motor.process(TEMPLATE, ctx);
    }

    public byte[] gerar(RelatorioPrevistoRealizado rel) {
        String html = html(rel);
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.useFont(() -> fonte("DejaVuSans.ttf"), FONTE, 400, FontStyle.NORMAL, true);
            builder.useFont(() -> fonte("DejaVuSans-Bold.ttf"), FONTE, 700, FontStyle.NORMAL, true);
            // Nada externo: qualquer URI (imagem, link de estilo) fica sem resolver
            builder.useUriResolver((base, uri) -> null);
            builder.toStream(saida);
            builder.run();
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gerar o PDF do previsto × realizado", e);
        }
    }

    private static InputStream fonte(String nome) {
        InputStream in = RelatorioPdf.class.getResourceAsStream("/relatorios/fontes/" + nome);
        if (in == null) {
            throw new IllegalStateException("Fonte ausente nos recursos: " + nome);
        }
        return in;
    }
}
