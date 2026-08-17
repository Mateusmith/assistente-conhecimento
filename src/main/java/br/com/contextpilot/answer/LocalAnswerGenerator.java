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
import br.com.contextpilot.answer.AnswerModels.ResultadoGeracao;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "contextpilot.ia.provedor", havingValue = "local", matchIfMissing = true)
class LocalAnswerGenerator implements AnswerGenerator {

    private static final Set<String> PALAVRAS_COMUNS = Set.of(
            "a", "as", "o", "os", "de", "da", "das", "do", "dos", "e", "em", "no", "na",
            "nos", "nas", "um", "uma", "para", "por", "com", "que", "qual", "quais", "como",
            "quando", "onde", "pode", "ser", "sao", "se", "ao", "aos");

    @Override
    public ResultadoGeracao gerar(String pergunta, List<FonteContexto> fontes) {
        Set<String> termosPergunta = termosRelevantes(pergunta);
        List<FraseCandidata> candidatas = new ArrayList<>();

        for (FonteContexto fonte : fontes) {
            Arrays.stream(fonte.conteudo().split("(?<=[.!?])\\s+|\\n+"))
                    .map(String::trim)
                    .filter(frase -> frase.length() >= 25)
                    .map(frase -> new FraseCandidata(fonte, frase, intersecao(termosPergunta, termosRelevantes(frase))))
                    .filter(candidata -> candidata.acertos() > 0)
                    .forEach(candidatas::add);
        }

        List<FraseCandidata> selecionadas = candidatas.stream()
                .sorted(Comparator.comparingInt(FraseCandidata::acertos).reversed()
                        .thenComparingDouble(c -> -c.fonte().pontuacao()))
                .filter(new java.util.function.Predicate<>() {
                    private final Set<String> marcadores = new HashSet<>();

                    @Override
                    public boolean test(FraseCandidata candidata) {
                        return marcadores.add(candidata.fonte().marcador());
                    }
                })
                .limit(3)
                .toList();

        if (selecionadas.isEmpty()) {
            return new ResultadoGeracao(RESPOSTA_SEM_CONTEXTO, "local-extrativo-v1");
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
        return new ResultadoGeracao(resposta.toString(), "local-extrativo-v1");
    }

    private Set<String> termosRelevantes(String texto) {
        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ");
        Set<String> termos = new HashSet<>();
        for (String termo : normalizado.split("\\s+")) {
            if (termo.length() >= 3 && !PALAVRAS_COMUNS.contains(termo)) {
                termos.add(termo);
            }
        }
        return termos;
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

    private record FraseCandidata(FonteContexto fonte, String frase, int acertos) {
    }
}
