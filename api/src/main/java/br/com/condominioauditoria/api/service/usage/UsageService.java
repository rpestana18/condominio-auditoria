package br.com.condominioauditoria.api.service.usage;

import br.com.condominioauditoria.api.exception.InvalidRequestException;
import br.com.condominioauditoria.api.model.enums.AiMode;
import br.com.condominioauditoria.api.model.enums.UsageFunction;
import br.com.condominioauditoria.api.model.usage.FeatureUsage;
import br.com.condominioauditoria.api.model.usage.UsageTotal;
import br.com.condominioauditoria.api.repository.usage.FeatureUsageRepository;
import br.com.condominioauditoria.api.service.calculator.UsageCostCalculator;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Usage records of the features (RF-09.7; ADR 0003, Decision 4). Saves one row per operation and summarizes per period.
 * Never receives document text, search text or key.
 */
@Service
public class UsageService {

    /** Months and dates of the usage report follow Brasília time. */
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final FeatureUsageRepository usages;

    UsageService(FeatureUsageRepository usages) {
        this.usages = usages;
    }

    /** One file indexed by the rag (files = 1). With an embedding model = LOCAL; without vectors = OFF. */
    @Transactional
    public void recordIndexing(UUID condominiumId, Integer pages, String embeddingModel) {
        AiMode mode = embeddingModel == null || embeddingModel.isBlank() ? AiMode.OFF : AiMode.LOCAL;
        usages.save(new FeatureUsage(condominiumId, FeatureService.ASSISTANT, UsageFunction.INDEXING, null,
                Instant.now(), mode, null, mode == AiMode.LOCAL ? embeddingModel : null, null, null, 1, pages, null));
    }

    /**
     * One call to search_documents through the MCP. hybridSearch = the rag used the local embeddings (LOCAL); keyword
     * only = OFF. The search model does not come in the rag's answer, so it stays null.
     */
    @Transactional
    public void recordMcpCall(UUID condominiumId, String username, boolean hybridSearch) {
        usages.save(new FeatureUsage(condominiumId, FeatureService.ASSISTANT, UsageFunction.MCP_CALL,
                Objects.requireNonNull(username), Instant.now(), hybridSearch ? AiMode.LOCAL : AiMode.OFF, null,
                null, null, null, null, null, null));
    }

    /**
     * One question answered by the chat (ANSWERED or NOT_FOUND), in API_KEY mode, with the tokens added up over
     * every attempt, the provider, the model and the version of the instructions (prompt) the rag used. Never the
     * question.
     */
    @Transactional
    public void recordQuestion(UUID condominiumId, String username, String provider, String model, long inputTokens,
            long outputTokens, String promptVersion) {
        usages.save(new FeatureUsage(condominiumId, FeatureService.ASSISTANT, UsageFunction.QUESTION,
                Objects.requireNonNull(username),
                Instant.now(), AiMode.API_KEY, truncate(provider, 60), truncate(model, 120),
                Math.max(0, inputTokens), Math.max(0, outputTokens), null, null, truncate(promptVersion, 40)));
    }

    /** One keyword search made from the screen (RF-04.18): no AI (OFF), no tokens. Never the searched text. */
    @Transactional
    public void recordDocumentSearch(UUID condominiumId, String username) {
        usages.save(new FeatureUsage(condominiumId, FeatureService.ASSISTANT, UsageFunction.DOCUMENT_SEARCH,
                Objects.requireNonNull(username), Instant.now(), AiMode.OFF, null, null, null, null, null, null,
                null));
    }

    /**
     * Estimated cost of the period in US$ (tokens × catalog price), calculated on the fly from the records; no cost is
     * saved (ADR 0003, Decision 4). prices: by {@link UsageCostCalculator.ModelPrice#key}.
     */
    @Transactional(readOnly = true)
    public UsageCostCalculator.PeriodCost cost(UUID condominiumId, LocalDate start, LocalDate end,
            Map<String, UsageCostCalculator.ModelPrice> prices) {
        validatePeriod(start, end);
        List<UsageCostCalculator.ModelTokens> rows = usages.tokensByModel(condominiumId, startOfDay(start),
                startOfDay(end.plusDays(1))).stream()
                .map(l -> new UsageCostCalculator.ModelTokens(l.getMonth(), l.getFeature(),
                        UsageFunction.fromCode(l.getFunction()),
                        l.getProvider(), l.getModel(), l.getInputTokens(), l.getOutputTokens()))
                .toList();
        return UsageCostCalculator.calculate(rows, prices);
    }

    /** Usage of the period, from start to end (whole days in Brasília, end included), by month and by function. */
    @Transactional(readOnly = true)
    public UsageSummary summary(UUID condominiumId, LocalDate start, LocalDate end) {
        validatePeriod(start, end);
        List<UsageTotal> byMonth = usages.monthlyTotals(condominiumId, startOfDay(start), startOfDay(end.plusDays(1)))
                .stream()
                .map(l -> new UsageTotal(l.getMonth(), l.getFeature(), UsageFunction.fromCode(l.getFunction()),
                        l.getCount(), l.getInputTokens(), l.getOutputTokens(), l.getFiles(), l.getPages()))
                .toList();
        return new UsageSummary(condominiumId, start, end, UsageTotal.sumByFunction(byMonth), byMonth);
    }

    public record UsageSummary(UUID condominiumId, LocalDate start, LocalDate end, List<UsageTotal> byFunction,
            List<UsageTotal> byMonth) {
    }

    static void validatePeriod(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new InvalidRequestException("Informe o início e o fim do período");
        }
        if (start.isAfter(end)) {
            throw new InvalidRequestException("O início do período (" + start + ") é depois do fim (" + end + ")");
        }
    }

    /** Blank = null; text longer than the column is cut (the usage record never breaks the response). */
    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    public static Instant startOfDay(LocalDate day) {
        return day.atStartOfDay(ZONE).toInstant();
    }
}
