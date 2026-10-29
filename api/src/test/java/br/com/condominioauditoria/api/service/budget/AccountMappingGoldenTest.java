package br.com.condominioauditoria.api.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.api.dto.request.budget.BudgetConfirmationRequest;
import br.com.condominioauditoria.api.dto.response.budget.AccountMappingResponse;
import br.com.condominioauditoria.api.dto.response.budget.AccountMappingSummaryResponse;
import br.com.condominioauditoria.api.messaging.GoldenMessages;
import br.com.condominioauditoria.api.model.budget.AccountMapping;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.enums.AccountMappingFilter;
import br.com.condominioauditoria.api.model.enums.AccountMappingSource;
import br.com.condominioauditoria.api.model.enums.AccountMappingStatus;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.service.calculator.EffectiveAccountMapping;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Account mapping golden test (RF-03.1.4, RF-03.1.5, RF-03.1.15 and Q54) with the real budget 2026/2027, its support
 * sheet and the cash flow of September 2026. The pilot's map of 73 accounts (confirmed by the user) is the answer key:
 * the automatic mapping is measured <b>without</b> the map as a source and, separately, with it. Skipped without
 * data/golden/privado.
 */
class AccountMappingGoldenTest {

    /** The measurement of the automatic mapping against the pilot's map, over the debit accounts of September/2026. */
    record Measurement(int accounts, int linked, int lowConfidence, int withoutLink, int hits, int errors, int misses,
            Map<String, String> chosen) {
    }

    @Test
    void sheetOf73AccountsComesInAutomaticAndCountsWithoutConfirmation() {
        SeptemberGolden g = golden();
        Optional<String> map = SeptemberGolden.map();
        assumeTrue(map.isPresent(), "mapa do piloto ausente no golden privado");

        var r = g.scenario.accountMapping.loadSheet(g.scenario.condominiumId, g.budget.getId(),
                "mapa-contas-fluxo-para-PO.csv", map.get(), "admin");

        assertThat(r.rejected()).isEmpty();
        assertThat(r.skipped()).isEmpty();
        assertThat(r.accepted()).isEqualTo(73);
        var list = g.scenario.accountMapping.list(g.scenario.condominiumId, g.budget.getId(), AccountMappingFilter.ALL);
        assertThat(list.summary()).isEqualTo(new AccountMappingSummaryResponse(73, 73, 0, 0, 0, 0));
        assertThat(g.scenario.mappings).allMatch(d -> d.getStatus() == AccountMappingStatus.AUTOMATIC);
        // Q54: the links count in the numbers without a confirmation
        assertThat(EffectiveAccountMapping.effective(g.scenario.mappings)).hasSize(73);

        AccountMapping plumbing = mapping(g, "1621");
        assertThat(plumbing.getBudgetLineId()).isEqualTo(g.line("1.7.8").getId()).isNotEqualTo(g.line("1.3.23").getId());
        assertThat(mapping(g, "1073").getBudgetLineId()).isEqualTo(g.line("1.3.25").getId());
        assertThat(g.scenario.mappingEvents).hasSize(73);
    }

    @Test
    void withoutSupportSheetTheNameRulesLink1621To178AndNeverTo1323() {
        SeptemberGolden g = golden();

        g.scenario.accountMapping.suggestPending(g.scenario.condominiumId);

        AccountMapping plumbing = mapping(g, "1621");
        assertThat(plumbing.getBudgetLineId()).isEqualTo(g.line("1.7.8").getId());
        assertThat(plumbing.getSource()).isEqualTo(AccountMappingSource.NAME);
        assertThat(g.scenario.mappings).noneMatch(d -> g.line("1.3.23").getId().equals(d.getBudgetLineId()));
        assertThat(g.scenario.mappings).allMatch(d -> d.getStatus() == AccountMappingStatus.AUTOMATIC);
        Optional<String> map = SeptemberGolden.map();
        assumeTrue(map.isPresent());
        Measurement m = measure(g, g.budget, expected(map.get()));
        print("sem a planilha de apoio e sem o mapa", m);
        // Name rules only (no support sheet): 55 right; the 3 links that disagree with the map are all of low
        // confidence, so they show in "só exceções" (1038 → 1.6.9, 1225 → 1.1.12, 1436 → 1.6.10)
        assertThat(m.accounts()).isEqualTo(73);
        assertThat(m.hits()).isEqualTo(55);
        assertThat(m.errors()).isEqualTo(3);
        assertThat(m.lowConfidence()).isEqualTo(9);
        assertThat(m.chosen()).containsEntry("1038", "1.6.9 (baixa confiança)")
                .containsEntry("1225", "1.1.12 (baixa confiança)").containsEntry("1436", "1.6.10 (baixa confiança)");
    }

    /**
     * Q54, pilot measurement: PO 2026/2027 + support sheet + cash flow of September/2026, <b>without</b> the map. Locks
     * the numbers: a rule change that links less, or links wrong, fails here.
     */
    @Test
    void withTheSupportSheetAndWithoutTheMapTheSystemLinks63Of73AccountsAndNoneWrong() {
        SeptemberGolden g = golden();
        Optional<String> map = SeptemberGolden.map();
        assumeTrue(map.isPresent(), "mapa do piloto ausente no golden privado");
        assumeTrue(loadSupportSheet(g), "planilha de apoio ausente no golden privado");

        var screen = g.scenario.accountMapping.open(g.scenario.condominiumId, g.budget.getId(), AccountMappingFilter.ALL);

        Measurement m = measure(g, g.budget, expected(map.get()));
        print("com a planilha de apoio, sem o mapa", m);
        assertThat(m.accounts()).isEqualTo(73);
        assertThat(m.linked()).isEqualTo(63);
        assertThat(m.lowConfidence()).isEqualTo(4);
        assertThat(m.withoutLink()).isEqualTo(10);
        assertThat(m.hits()).isEqualTo(63);
        assertThat(m.errors()).isZero();
        // 3 of the 10 have no line in the map either (1064 REALOCAR, 1324 and 1327 AJUSTE): the Admin decides them
        assertThat(m.misses()).isEqualTo(10);
        assertThat(screen.summary()).isEqualTo(new AccountMappingSummaryResponse(73, 63, 0, 4, 0, 10));
        assertThat(screen.automaticBySource().budgetSheet() + screen.automaticBySource().name()).isEqualTo(63);
        // The exceptions are only the low-confidence links and the accounts without a link found
        var exceptions = g.scenario.accountMapping.list(g.scenario.condominiumId, g.budget.getId(),
                AccountMappingFilter.EXCEPTIONS).accounts();
        assertThat(exceptions).extracting(AccountMappingResponse::account).containsExactly("1038", "1042", "1064",
                "1108", "1232", "1241", "1242", "1324", "1327", "1477", "1534", "1737", "3934", "4365");
        assertThat(m.chosen()).containsEntry("1038", "1.1.9 (baixa confiança)").containsEntry("1108", "1.3.20 (baixa confiança)")
                .containsEntry("3934", "1.6.1 (baixa confiança)").containsEntry("4365", "1.3.16 (baixa confiança)");

        // Group 1.6 (most of the monthly spending, P9): each account of the map and the line chosen without the map
        Map<String, String> expected = expected(map.get());
        Map<String, String> group16 = new TreeMap<>();
        expected.forEach((account, line) -> {
            if (line.startsWith("1.6.") && m.chosen().containsKey(account)) {
                group16.put(account, line + " → " + m.chosen().get(account));
            }
        });
        group16.forEach((a, l) -> System.out.println("1.6 | " + a + " | " + l));
        assertThat(group16).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("0028", "1.6.1 → 1.6.1"), Map.entry("1087", "1.6.3 → 1.6.3"),
                Map.entry("1105", "1.6.16 → 1.6.16"), Map.entry("1107", "1.6.14 → 1.6.14"),
                Map.entry("1110", "1.6.5 → 1.6.5"), Map.entry("1114", "1.6.6 → 1.6.6"),
                Map.entry("1146", "1.6.18 → 1.6.18"), Map.entry("1242", "1.6.5 → sem linha encontrada"),
                Map.entry("1317", "1.6.20 → 1.6.20"), Map.entry("1323", "1.6.19 → 1.6.19"),
                Map.entry("1398", "1.6.19 → 1.6.19"), Map.entry("1460", "1.6.8 → 1.6.8"),
                Map.entry("1477", "1.6.5 → sem linha encontrada"), Map.entry("1534", "1.6.5 → sem linha encontrada"),
                Map.entry("1629", "1.6.19 → 1.6.19"), Map.entry("1668", "1.6.2 → 1.6.2"),
                Map.entry("1737", "1.6.9 → sem linha encontrada"), Map.entry("3934", "1.6.1 → 1.6.1 (baixa confiança)"),
                Map.entry("4065", "1.6.4 → 1.6.4")));
    }

    /** Q54, the same measurement with the stored map: every account linked as the user confirmed it. */
    @Test
    void withTheStoredMapEveryAccountFollowsTheMap() {
        SeptemberGolden g = golden();
        Optional<String> map = SeptemberGolden.map();
        assumeTrue(map.isPresent());
        assumeTrue(loadSupportSheet(g));
        g.scenario.accountMapping.loadSheet(g.scenario.condominiumId, g.budget.getId(), "mapa-contas-fluxo-para-PO.csv",
                map.get(), "admin");

        g.scenario.accountMapping.suggestPending(g.scenario.condominiumId);

        Measurement m = measure(g, g.budget, expected(map.get()));
        print("com a planilha de apoio e com o mapa", m);
        assertThat(m.accounts()).isEqualTo(73);
        assertThat(m.linked()).isEqualTo(73);
        assertThat(m.lowConfidence()).isZero();
        assertThat(m.hits()).isEqualTo(73);
        assertThat(m.errors()).isZero();
    }

    @Test
    void storedSheetLinksThe73AccountsInANewVersionAndConfirmingIsOptional() {
        SeptemberGolden g = golden();
        Optional<String> map = SeptemberGolden.map();
        assumeTrue(map.isPresent());
        // The sheet loaded once in version 1 is the condominium's sheet from then on
        g.scenario.accountMapping.loadSheet(g.scenario.condominiumId, g.budget.getId(), "mapa-contas-fluxo-para-PO.csv",
                map.get(), "admin");
        assertThat(g.scenario.sheetItems).hasSize(73);
        var readAgain = GoldenMessages.read(GoldenMessages.BUDGET_2026_2027).orElseThrow();
        Budget v2 = g.scenario.readBudget(readAgain.budget(), readAgain.totalsChecks());
        var pilot = g.scenario.pilotRequest(v2);
        g.scenario.confirmation.confirm(g.scenario.condominiumId, v2.getId(), new BudgetConfirmationRequest("2026-10",
                "2027-04", pilot.minutesFileId(), false, pilot.approvalDate(), pilot.effectiveCodes(), pilot.funds(),
                true, false, null), "admin");

        g.scenario.accountMapping.suggestPending(g.scenario.condominiumId);

        var screen = g.scenario.accountMapping.list(g.scenario.condominiumId, v2.getId(), AccountMappingFilter.ALL);
        assertThat(screen.summary()).isEqualTo(new AccountMappingSummaryResponse(73, 73, 0, 0, 0, 0));
        assertThat(screen.automaticBySource().spreadsheet()).isEqualTo(73);
        Map<String, String> expected = expected(map.get());
        List<AccountMapping> ofV2 = g.scenario.mappings.stream().filter(d -> d.getBudgetId().equals(v2.getId()))
                .toList();
        assertThat(ofV2).hasSize(73).allMatch(d -> d.getSource() == AccountMappingSource.SPREADSHEET
                && d.getStatus() == AccountMappingStatus.AUTOMATIC);
        assertThat(EffectiveAccountMapping.effective(ofV2)).hasSize(73);
        assertThat(measure(g, v2, expected).errors()).isZero();

        // "Confirmar todas" stays as an optional action, with one event per account
        int events = g.scenario.mappingEvents.size();
        var r = g.scenario.accountMapping.confirmAllSuggested(g.scenario.condominiumId, v2.getId(), 73, "admin");
        assertThat(r.changed()).isEqualTo(73);
        assertThat(g.scenario.mappingEvents).hasSize(events + 73);
    }

    private static boolean loadSupportSheet(SeptemberGolden g) {
        var sheet = GoldenMessages.read("po-2026-2027-planilha-apoio");
        if (sheet.isEmpty()) {
            return false;
        }
        var file = g.scenario.file(FileCategory.PO, "PO-2026-2027-planilha-apoio.xlsx");
        var saved = g.scenario.lineOriginService.saveFromSheet(file, sheet.get().budgetLineOrigins());
        assertThat(saved.budget().getId()).isEqualTo(g.budget.getId());
        assertThat(g.scenario.sheetAccounts).hasSize(133);
        return true;
    }

    /** Compares the effective target of each debit account of the cash flow with the pilot's map. */
    private static Measurement measure(SeptemberGolden g, Budget budget, Map<String, String> expected) {
        var accounts = g.scenario.accountMapping.list(g.scenario.condominiumId, budget.getId(), AccountMappingFilter.ALL)
                .accounts().stream().filter(c -> c.ledgerEntries() > 0).toList();
        int linked = 0;
        int low = 0;
        int hits = 0;
        int errors = 0;
        int misses = 0;
        Map<String, String> chosen = new LinkedHashMap<>();
        for (AccountMappingResponse c : accounts) {
            boolean counts = c.status() != null && c.status().effective();
            String target = counts ? key(c) : null;
            String answer = expected.get(c.account());
            chosen.put(c.account(), target == null ? "sem linha encontrada"
                    : target + (c.lowConfidence() ? " (baixa confiança)" : ""));
            if (target == null) {
                System.out.println("sem linha encontrada: " + c.account() + " " + c.name() + " (mapa: " + answer
                        + ") — " + c.reason());
                if (answer != null) {
                    misses++;
                }
                continue;
            }
            linked++;
            if (c.lowConfidence()) {
                low++;
            }
            if (c.lowConfidence()) {
                System.out.println("baixa confiança: " + c.account() + " " + c.name() + " → " + target + " (mapa: "
                        + answer + ") — " + c.reason());
            }
            if (target.equals(answer)) {
                hits++;
            } else {
                errors++;
                System.out.println("de-para diferente do mapa: " + c.account() + " " + c.name() + " → " + target
                        + " (mapa: " + answer + ") — " + c.reason());
            }
        }
        return new Measurement(accounts.size(), linked, low, accounts.size() - linked, hits, errors, misses, chosen);
    }

    private static String key(AccountMappingResponse c) {
        return switch (c.target().type()) {
            case BUDGET_LINE -> c.target().code();
            case ADJUSTMENT -> "AJUSTE";
            case TO_REALLOCATE -> "REALOCAR";
            case TRANSFER -> "TRANSFERÊNCIA";
            case WITHOUT_LINE -> "SEM LINHA";
        };
    }

    private static void print(String title, Measurement m) {
        System.out.println("De-para automático (" + title + "): " + m.accounts() + " contas; " + m.linked()
                + " ligadas (" + m.lowConfidence() + " de baixa confiança); " + m.withoutLink()
                + " sem linha encontrada; contra o mapa: " + m.hits() + " acertos, " + m.errors() + " erros, "
                + m.misses() + " sem ligação");
    }

    private static Map<String, String> expected(String map) {
        Map<String, String> expected = new LinkedHashMap<>();
        map.lines().skip(1).map(l -> l.split(";")).forEach(c -> expected.put(c[0], c[1].startsWith("AJUSTE")
                ? "AJUSTE" : c[1].startsWith("REALOCAR") ? "REALOCAR" : c[1]));
        return expected;
    }

    private static AccountMapping mapping(SeptemberGolden g, String account) {
        return g.scenario.mappings.stream().filter(d -> d.getAccountCode().equals(account)).findFirst().orElseThrow();
    }

    private static SeptemberGolden golden() {
        Optional<SeptemberGolden> g = SeptemberGolden.load();
        assumeTrue(g.isPresent(), "golden privado ausente");
        return g.get();
    }
}
