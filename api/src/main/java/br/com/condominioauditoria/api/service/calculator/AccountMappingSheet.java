package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.MappingTarget;
import br.com.condominioauditoria.api.model.enums.MappingTargetType;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Account mapping suggestion sheet uploaded by the Admin (RF-03.1.5, approved proposal; ADR 0004, Decision 4). CSV in
 * the format of the pilot's {@code mapa-contas-fluxo-para-PO.csv}: {@code cash flow account;budget line[;anything]},
 * separated by semicolons, with or without a header. The target is the effective code of a budget expense line or a
 * special target: "AJUSTE (...)", "REALOCAR (...)" or "TRANSFERENCIA (...)". An invalid line is listed with the reason
 * and not loaded. Pure function: not AI and not the reading of an accounting document (it is the Admin's
 * configuration).
 */
public final class AccountMappingSheet {

    private static final Pattern ACCOUNT = Pattern.compile("^\\d{1,20}$");
    private static final Pattern LINE_CODE = Pattern.compile("^\\d+(\\.\\d+)+$");
    private static final Pattern SPECIAL = Pattern.compile("^(AJUSTE|A[ _]REALOCAR|REALOCAR|TRANSFERENCIA)\\s*(?:\\((.*)\\))?$");

    public record Item(int line, String account, MappingTarget target) {
    }

    public record Rejection(int line, String content, String reason) {
    }

    public record Reading(List<Item> items, List<Rejection> rejections) {
    }

    private AccountMappingSheet() {
    }

    /**
     * @param targets budget lines that can be a target (expense lines, without the 1.9 funds)
     *
     * @param fundLines 1.9.x lines, rejected as a target with their own reason
     */
    public static Reading read(String content, List<BudgetLine> targets, List<BudgetLine> fundLines) {
        Map<String, BudgetLine> byCode = new HashMap<>();
        targets.forEach(l -> byCode.put(l.getEffectiveCode(), l));
        Map<String, BudgetLine> funds = new HashMap<>();
        fundLines.forEach(l -> funds.put(l.getEffectiveCode(), l));

        List<Item> items = new ArrayList<>();
        List<Rejection> rejections = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        String text = content == null ? "" : content.replace("﻿", "");
        String[] lines = text.split("\\r?\\n|\\r", -1);
        for (int i = 0; i < lines.length; i++) {
            int number = i + 1;
            String raw = lines[i];
            if (raw.isBlank()) {
                continue;
            }
            String[] columns = raw.split(";", -1);
            String account = columns[0].trim();
            if (i == 0 && !ACCOUNT.matcher(account).matches() && !account.isEmpty() && Character.isLetter(account.charAt(0))) {
                continue; // header
            }
            if (columns.length < 2) {
                rejections.add(new Rejection(number, raw, "linha sem as duas colunas \"conta do fluxo;linha da PO\""));
                continue;
            }
            if (!ACCOUNT.matcher(account).matches()) {
                rejections.add(new Rejection(number, raw,
                        "conta do fluxo \"" + account + "\" não é um código numérico"));
                continue;
            }
            Integer previous = seen.putIfAbsent(account, number);
            if (previous != null) {
                rejections.add(new Rejection(number, raw, "conta " + account + " repetida (já na linha " + previous
                        + "): cada conta do fluxo tem um destino só"));
                continue;
            }
            String targetText = columns[1].trim();
            if (LINE_CODE.matcher(targetText).matches()) {
                BudgetLine l = byCode.get(targetText);
                if (l != null) {
                    items.add(new Item(number, account, MappingTarget.line(l)));
                } else if (funds.containsKey(targetText)) {
                    rejections.add(new Rejection(number, raw, "a linha " + targetText + " é de fundo: as linhas 1.9 são"
                            + " comparadas com a arrecadação do fundo, nunca com débitos"));
                } else {
                    rejections.add(new Rejection(number, raw,
                            "a linha " + targetText + " não existe nesta versão da PO"));
                }
                continue;
            }
            Matcher m = SPECIAL.matcher(stripAccents(targetText));
            if (m.matches()) {
                MappingTargetType type = switch (m.group(1)) {
                    case "AJUSTE" -> MappingTargetType.AJUSTE;
                    case "TRANSFERENCIA" -> MappingTargetType.TRANSFERENCIA;
                    default -> MappingTargetType.A_REALOCAR;
                };
                // The detail stays as written (with accents), only without the parentheses
                int open = targetText.indexOf('(');
                int close = targetText.lastIndexOf(')');
                String detail = open >= 0 && close > open ? targetText.substring(open + 1, close) : null;
                items.add(new Item(number, account, MappingTarget.special(type, detail)));
                continue;
            }
            rejections.add(new Rejection(number, raw, "destino \"" + targetText + "\" não é código de linha da PO nem"
                    + " AJUSTE (...), REALOCAR (...) ou TRANSFERENCIA (...)"));
        }
        return new Reading(List.copyOf(items), List.copyOf(rejections));
    }

    private static String stripAccents(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    }
}
