package br.com.condominioauditoria.api.report;

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
 * PDF of budget vs. actual (RF-03.1.14; ADR 0004, Decision 6): Thymeleaf template in {@code relatorios/} and
 * OpenHTMLtoPDF, with the DejaVu Sans font embedded (accents). No chart. The PDF loads nothing from outside: no link,
 * image or external stylesheet is resolved.
 */
@Component
public class BudgetVsActualPdfReport {

    static final String TEMPLATE = "previsto-realizado";
    private static final String FONT = "DejaVu Sans";

    private final ITemplateEngine engine;

    public BudgetVsActualPdfReport() {
        this.engine = engine();
    }

    /** The reports' own engine (it does not use the screen templates folder, which the api does not have). */
    public static ITemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("relatorios/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    /** Intermediate HTML (the same that becomes the PDF); also used in tests. */
    public String html(BudgetVsActualReport report) {
        Context ctx = new Context(Locale.forLanguageTag("pt-BR"));
        ctx.setVariable("rel", report);
        ctx.setVariable("r", report.result());
        ctx.setVariable("f", new ReportFormats());
        return engine.process(TEMPLATE, ctx);
    }

    public byte[] generate(BudgetVsActualReport report) {
        String html = html(report);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.useFont(() -> font("DejaVuSans.ttf"), FONT, 400, FontStyle.NORMAL, true);
            builder.useFont(() -> font("DejaVuSans-Bold.ttf"), FONT, 700, FontStyle.NORMAL, true);
            // Nothing external: any URI (image, style link) stays unresolved
            builder.useUriResolver((base, uri) -> null);
            builder.toStream(output);
            builder.run();
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gerar o PDF do previsto × realizado", e);
        }
    }

    private static InputStream font(String name) {
        InputStream in = BudgetVsActualPdfReport.class.getResourceAsStream("/relatorios/fontes/" + name);
        if (in == null) {
            throw new IllegalStateException("Fonte ausente nos recursos: " + name);
        }
        return in;
    }
}
