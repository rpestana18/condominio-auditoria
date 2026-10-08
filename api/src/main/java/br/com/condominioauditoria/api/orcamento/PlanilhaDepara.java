package br.com.condominioauditoria.api.orcamento;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Planilha de sugestões do de-para carregada pelo Admin (RF-03.1.5, proposta aprovada; ADR 0004, Decisão 4). CSV no
 * formato do {@code mapa-contas-fluxo-para-PO.csv} do piloto: {@code conta do fluxo;linha da PO[;qualquer coisa]},
 * separado por ponto e vírgula, com ou sem cabeçalho. O destino é o código efetivo de uma linha de despesa da PO ou um
 * destino especial: "AJUSTE (...)", "REALOCAR (...)" ou "TRANSFERENCIA (...)". Linha inválida é listada com o motivo e
 * não entra. Função pura: não é IA e não é leitura de documento contábil (é configuração do Admin).
 */
public final class PlanilhaDepara {

    private static final Pattern CONTA = Pattern.compile("^\\d{1,20}$");
    private static final Pattern CODIGO_LINHA = Pattern.compile("^\\d+(\\.\\d+)+$");
    private static final Pattern ESPECIAL = Pattern.compile("^(AJUSTE|A[ _]REALOCAR|REALOCAR|TRANSFERENCIA)\\s*(?:\\((.*)\\))?$");

    public record Item(int linha, String conta, Destino destino) {
    }

    public record Recusa(int linha, String conteudo, String motivo) {
    }

    public record Leitura(List<Item> itens, List<Recusa> recusas) {
    }

    private PlanilhaDepara() {
    }

    /**
     * @param destinos linhas da PO que podem ser destino (linhas de despesa, sem os fundos 1.9)
     * @param linhasDeFundo linhas 1.9.x, recusadas como destino com motivo próprio
     */
    public static Leitura ler(String conteudo, List<LinhaPo> destinos, List<LinhaPo> linhasDeFundo) {
        Map<String, LinhaPo> porCodigo = new HashMap<>();
        destinos.forEach(l -> porCodigo.put(l.getCodigoEfetivo(), l));
        Map<String, LinhaPo> fundos = new HashMap<>();
        linhasDeFundo.forEach(l -> fundos.put(l.getCodigoEfetivo(), l));

        List<Item> itens = new ArrayList<>();
        List<Recusa> recusas = new ArrayList<>();
        Map<String, Integer> vistas = new HashMap<>();
        String texto = conteudo == null ? "" : conteudo.replace("﻿", "");
        String[] linhas = texto.split("\\r?\\n|\\r", -1);
        for (int i = 0; i < linhas.length; i++) {
            int numero = i + 1;
            String bruta = linhas[i];
            if (bruta.isBlank()) {
                continue;
            }
            String[] colunas = bruta.split(";", -1);
            String conta = colunas[0].trim();
            if (i == 0 && !CONTA.matcher(conta).matches() && !conta.isEmpty() && Character.isLetter(conta.charAt(0))) {
                continue; // cabeçalho
            }
            if (colunas.length < 2) {
                recusas.add(new Recusa(numero, bruta, "linha sem as duas colunas \"conta do fluxo;linha da PO\""));
                continue;
            }
            if (!CONTA.matcher(conta).matches()) {
                recusas.add(new Recusa(numero, bruta, "conta do fluxo \"" + conta + "\" não é um código numérico"));
                continue;
            }
            Integer anterior = vistas.putIfAbsent(conta, numero);
            if (anterior != null) {
                recusas.add(new Recusa(numero, bruta, "conta " + conta + " repetida (já na linha " + anterior
                        + "): cada conta do fluxo tem um destino só"));
                continue;
            }
            String destinoTexto = colunas[1].trim();
            if (CODIGO_LINHA.matcher(destinoTexto).matches()) {
                LinhaPo l = porCodigo.get(destinoTexto);
                if (l != null) {
                    itens.add(new Item(numero, conta, Destino.linha(l)));
                } else if (fundos.containsKey(destinoTexto)) {
                    recusas.add(new Recusa(numero, bruta, "a linha " + destinoTexto + " é de fundo: as linhas 1.9 são"
                            + " comparadas com a arrecadação do fundo, nunca com débitos"));
                } else {
                    recusas.add(new Recusa(numero, bruta, "a linha " + destinoTexto + " não existe nesta versão da PO"));
                }
                continue;
            }
            Matcher m = ESPECIAL.matcher(semAcento(destinoTexto));
            if (m.matches()) {
                TipoDestino tipo = switch (m.group(1)) {
                    case "AJUSTE" -> TipoDestino.AJUSTE;
                    case "TRANSFERENCIA" -> TipoDestino.TRANSFERENCIA;
                    default -> TipoDestino.A_REALOCAR;
                };
                // O detalhe fica como escrito (com acento), só sem os parênteses
                int abre = destinoTexto.indexOf('(');
                int fecha = destinoTexto.lastIndexOf(')');
                String detalhe = abre >= 0 && fecha > abre ? destinoTexto.substring(abre + 1, fecha) : null;
                itens.add(new Item(numero, conta, Destino.especial(tipo, detalhe)));
                continue;
            }
            recusas.add(new Recusa(numero, bruta, "destino \"" + destinoTexto + "\" não é código de linha da PO nem"
                    + " AJUSTE (...), REALOCAR (...) ou TRANSFERENCIA (...)"));
        }
        return new Leitura(List.copyOf(itens), List.copyOf(recusas));
    }

    private static String semAcento(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    }
}
