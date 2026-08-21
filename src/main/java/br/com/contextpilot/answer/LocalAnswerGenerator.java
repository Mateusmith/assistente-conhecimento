package br.com.contextpilot.answer;

import static br.com.contextpilot.answer.AnswerModels.RESPOSTA_SEM_CONTEXTO;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import br.com.contextpilot.answer.AnswerModels.FonteContexto;
import br.com.contextpilot.answer.AnswerModels.MensagemMemoria;
import br.com.contextpilot.answer.AnswerModels.PapelMemoria;
import br.com.contextpilot.answer.AnswerModels.ResultadoGeracao;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "contextpilot.ia.provedor", havingValue = "local", matchIfMissing = true)
class LocalAnswerGenerator implements AnswerGenerator {

    private static final String VERSAO_PROMPT = "extrativo-local-v3";
    private static final String IMPRESSAO_PROMPT = PromptTrace.impressao(VERSAO_PROMPT);
    private static final Set<String> PALAVRAS_COMUNS = Set.of(
            "a", "as", "o", "os", "de", "da", "das", "do", "dos", "e", "em", "no", "na",
            "nos", "nas", "um", "uma", "para", "por", "com", "que", "qual", "quais", "como",
            "quando", "onde", "pode", "deve", "devem", "ser", "sao", "se", "ao", "aos");

    @Override
    public ResultadoGeracao gerar(String pergunta, List<FonteContexto> fontes) {
        Set<String> termosPergunta = termosRelevantes(pergunta);
        List<FraseCandidata> candidatas = new ArrayList<>();

        for (FonteContexto fonte : fontes) {
            Arrays.stream(fonte.conteudo().split("(?<=[.!?])\\s+|\\n+"))
                    .map(String::trim)
                    .filter(frase -> frase.length() >= 25)
                    .filter(frase -> !frase.matches("^#{1,6}\\s+.*"))
                    .map(frase -> {
                        Set<String> termosFrase = termosRelevantes(frase);
                        Set<String> termosTitulo = termosRelevantes(fonte.tituloDocumento());
                        return new FraseCandidata(
                                fonte,
                                frase,
                                intersecao(termosPergunta, termosFrase),
                                intersecaoExclusiva(termosPergunta, termosTitulo, termosFrase),
                                pontosDaResposta(pergunta, frase));
                    })
                    .filter(candidata -> candidata.acertosFrase() > 0)
                    .forEach(candidatas::add);
        }

        List<FraseCandidata> ordenadas = candidatas.stream()
                .sorted(Comparator.comparingDouble(FraseCandidata::pontuacaoLexical).reversed()
                        .thenComparingDouble(candidata -> -candidata.fonte().pontuacao()))
                .toList();

        List<FraseCandidata> selecionadas = selecionarMelhores(ordenadas, limiteFrases(pergunta));

        if (selecionadas.isEmpty()) {
            return resultado(RESPOSTA_SEM_CONTEXTO);
        }

        StringBuilder resposta = new StringBuilder("Com base no conhecimento disponivel: ");
        for (int indice = 0; indice < selecionadas.size(); indice++) {
            FraseCandidata candidata = selecionadas.get(indice);
            if (indice > 0) {
                resposta.append(' ');
            }
            resposta.append(candidata.frase());
            if (!candidata.frase().endsWith(".")) {
                resposta.append('.');
            }
            resposta.append(" [").append(candidata.fonte().marcador()).append(']');
        }
        return resultado(resposta.toString());
    }

    private List<FraseCandidata> selecionarMelhores(List<FraseCandidata> ordenadas, int limite) {
        if (ordenadas.isEmpty()) {
            return List.of();
        }
        double corte = Math.max(2.0, ordenadas.getFirst().pontuacaoLexical() * 0.80);
        Set<String> frasesIncluidas = new HashSet<>();
        List<FraseCandidata> selecionadas = new ArrayList<>();
        for (FraseCandidata candidata : ordenadas) {
            if (candidata.pontuacaoLexical() < corte || selecionadas.size() == limite) {
                break;
            }
            if (frasesIncluidas.add(normalizar(candidata.frase()))) {
                selecionadas.add(candidata);
            }
        }
        return List.copyOf(selecionadas);
    }

    private int limiteFrases(String pergunta) {
        Set<String> termos = termosRelevantes(pergunta);
        return termos.stream().anyMatch(Set.of("resuma", "resum", "liste", "list", "compare", "compar")::contains)
                || normalizar(pergunta).contains("quais ") ? 3 : 1;
    }

    @Override
    public ResultadoGeracao gerar(
            String pergunta,
            List<FonteContexto> fontes,
            List<MensagemMemoria> memoria) {
        StringBuilder perguntaContextualizada = new StringBuilder();
        memoria.stream()
                .filter(mensagem -> mensagem.papel() == PapelMemoria.USUARIO)
                .map(MensagemMemoria::conteudo)
                .forEach(texto -> perguntaContextualizada.append(texto).append(' '));
        perguntaContextualizada.append(pergunta);
        return gerar(perguntaContextualizada.toString(), fontes);
    }

    private Set<String> termosRelevantes(String texto) {
        String normalizado = normalizar(texto);
        Set<String> termos = new HashSet<>();
        for (String termo : normalizado.split("\\s+")) {
            if (termo.length() >= 3 && !PALAVRAS_COMUNS.contains(termo)) {
                termos.add(radicalizar(termo));
            }
        }
        return termos;
    }

    private String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private String radicalizar(String termo) {
        String radical = termo;
        if (radical.length() > 5 && radical.endsWith("ais")) {
            radical = radical.substring(0, radical.length() - 3) + "al";
        } else if (radical.length() > 5 && radical.endsWith("eis")) {
            radical = radical.substring(0, radical.length() - 3) + "el";
        } else if (radical.length() > 6 && radical.endsWith("oes")) {
            radical = radical.substring(0, radical.length() - 3) + "ao";
        } else if (radical.length() > 4 && radical.endsWith("s")) {
            radical = radical.substring(0, radical.length() - 1);
        }

        String[] sufixos = {"acoes", "icoes", "acao", "icao", "ando", "endo", "indo",
                "ado", "ada", "ido", "ida", "ar", "er", "ir", "em"};
        for (String sufixo : sufixos) {
            if (radical.length() - sufixo.length() >= 4 && radical.endsWith(sufixo)) {
                return radical.substring(0, radical.length() - sufixo.length());
            }
        }
        return radical;
    }

    private int intersecao(Set<String> esquerda, Set<String> direita) {
        int quantidade = 0;
        for (String termo : esquerda) {
            if (direita.contains(termo)) {
                quantidade++;
            }
        }
        return quantidade;
    }

    private int intersecaoExclusiva(
            Set<String> termosPergunta,
            Set<String> termosTitulo,
            Set<String> termosFrase) {
        int quantidade = 0;
        for (String termo : termosPergunta) {
            if (termosTitulo.contains(termo) && !termosFrase.contains(termo)) {
                quantidade++;
            }
        }
        return quantidade;
    }

    private int pontosDaResposta(String pergunta, String frase) {
        String perguntaNormalizada = normalizar(pergunta);
        String fraseNormalizada = normalizar(frase);
        boolean perguntaSobrePrazo = perguntaNormalizada.contains("prazo")
                || perguntaNormalizada.contains("quanto tempo")
                || perguntaNormalizada.startsWith("quando ");
        boolean possuiDuracao = fraseNormalizada.matches(
                ".*\\b\\d+(?:[.,]\\d+)?\\s*(?:minuto|minutos|hora|horas|dia|dias|semana|semanas|mes|meses|ano|anos)\\b.*");

        if (perguntaSobrePrazo && possuiDuracao) {
            return 3;
        }
        if (perguntaNormalizada.matches(".*\\b(?:quanto|quantos|quantas)\\b.*")
                && fraseNormalizada.matches(".*\\b\\d+(?:[.,]\\d+)?\\b.*")) {
            return 2;
        }
        return 0;
    }

    private ResultadoGeracao resultado(String texto) {
        return new ResultadoGeracao(texto, "local-extrativo-v3", VERSAO_PROMPT,
                IMPRESSAO_PROMPT, 0, 0, 0, java.math.BigDecimal.ZERO);
    }

    private record FraseCandidata(
            FonteContexto fonte,
            String frase,
            int acertosFrase,
            int acertosTitulo,
            int pontosResposta) {

        double pontuacaoLexical() {
            return acertosFrase * 2.0 + acertosTitulo + pontosResposta;
        }
    }
}
