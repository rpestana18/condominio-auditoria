package br.com.condominioauditoria.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * ADR 0008, local text format: the MDC (requestId, user, condominiumId, fileId...) goes between brackets on each line,
 * and a line outside a request has no empty brackets. The same pattern is in the application.yml of api, rag and mcp.
 */
class LogPatternTest {

    @Test
    void correlationPatternOfTheThreeServicesShowsTheMdc() throws Exception {
        String api = correlation(Path.of("src/main/resources/application.yml"));
        assertThat(correlation(Path.of("../rag/src/main/resources/application.yml"))).isEqualTo(api);
        assertThat(correlation(Path.of("../mcp/src/main/resources/application.yml"))).isEqualTo(api);

        PatternLayout layout = new PatternLayout();
        layout.setContext(new LoggerContext());
        layout.setPattern(api + "%m");
        layout.start();

        assertThat(layout.doLayout(event(Map.of("requestId", "req-1", "fileId", "f-9"))))
                .startsWith("[").contains("requestId=req-1").contains("fileId=f-9").endsWith("] Arquivo lido");
        assertThat(layout.doLayout(event(Map.of()))).isEqualTo("Arquivo lido");
    }

    private static LoggingEvent event(Map<String, String> mdc) {
        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.INFO);
        event.setMessage("Arquivo lido");
        event.setMDCPropertyMap(mdc);
        return event;
    }

    /** The value of logging.pattern.correlation, as YAML reads a double-quoted string. */
    private static String correlation(Path yml) throws Exception {
        Matcher m = Pattern.compile("correlation: \"(.*)\"").matcher(Files.readString(yml));
        assertThat(m.find()).as(yml.toString()).isTrue();
        return m.group(1).replace("\\\\", "\\");
    }
}
