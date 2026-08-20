package br.com.contextpilot.privacy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class SensitiveDataProtector {

    private static final Pattern DADOS_SENSIVEIS = Pattern.compile("""
            (?<EMAIL>\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b)
            |(?<CNPJ>\\b\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}\\b)
            |(?<CPF>\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b)
            |(?<TELEFONE>(?<!\\d)(?:\\+?55[\\s.-]?)?(?:\\(?\\d{2}\\)?[\\s.-]?)?9?\\d{4}[\\s.-]?\\d{4}(?!\\d))
            |(?<JWT>\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b)
            |(?<CHAVEAPI>\\b(?:sk-[A-Za-z0-9_-]{16,}|AKIA[A-Z0-9]{16})\\b)
            """, Pattern.COMMENTS);

    private final MeterRegistry metricas;

    public SensitiveDataProtector(MeterRegistry metricas) {
        this.metricas = metricas;
    }

    public TextoProtegido proteger(String texto) {
        if (texto == null || texto.isEmpty()) {
            return new TextoProtegido(texto == null ? "" : texto, Map.of(), Map.of());
        }

        Matcher correspondencia = DADOS_SENSIVEIS.matcher(texto);
        Map<String, String> valorParaMarcador = new LinkedHashMap<>();
        Map<String, String> marcadorParaValor = new LinkedHashMap<>();
        Map<String, Integer> quantidades = new LinkedHashMap<>();
        StringBuffer protegido = new StringBuffer();

        while (correspondencia.find()) {
            String tipo = tipo(correspondencia);
            String valor = correspondencia.group();
            String chave = tipo + '\u0000' + valor;
            String marcador = valorParaMarcador.computeIfAbsent(chave, ignorada -> {
                int sequencia = quantidades.merge(tipo, 1, Integer::sum);
                String novoMarcador = "[[DADO_" + tipo + "_" + sequencia + "]]";
                marcadorParaValor.put(novoMarcador, valor);
                return novoMarcador;
            });
            correspondencia.appendReplacement(protegido, Matcher.quoteReplacement(marcador));
        }
        correspondencia.appendTail(protegido);

        quantidades.forEach((tipo, quantidade) -> metricas.counter(
                "contextpilot.ia.dados_sensiveis_protegidos", "tipo", tipo.toLowerCase())
                .increment(quantidade));
        return new TextoProtegido(protegido.toString(), Map.copyOf(marcadorParaValor), Map.copyOf(quantidades));
    }

    private String tipo(Matcher correspondencia) {
        for (String tipo : new String[] {"EMAIL", "CNPJ", "CPF", "TELEFONE", "JWT"}) {
            if (correspondencia.group(tipo) != null) {
                return tipo;
            }
        }
        if (correspondencia.group("CHAVEAPI") != null) {
            return "CHAVE_API";
        }
        throw new IllegalStateException("Tipo de dado sensivel nao reconhecido.");
    }

    public record TextoProtegido(
            String texto,
            Map<String, String> valoresPorMarcador,
            Map<String, Integer> quantidadesPorTipo) {

        public String restaurar(String resposta) {
            String restaurada = resposta;
            for (Map.Entry<String, String> entrada : valoresPorMarcador.entrySet()) {
                restaurada = restaurada.replace(entrada.getKey(), entrada.getValue());
            }
            return restaurada;
        }

        public int totalProtegido() {
            return quantidadesPorTipo.values().stream().mapToInt(Integer::intValue).sum();
        }
    }
}
