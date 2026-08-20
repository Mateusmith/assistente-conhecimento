package br.com.contextpilot.answer;

import static br.com.contextpilot.answer.AnswerModels.RESPOSTA_SEM_CONTEXTO;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import br.com.contextpilot.answer.AnswerModels.FonteContexto;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
class AnswerIntegrityValidator {

    private static final Pattern CITACAO = Pattern.compile("\\[(F\\d+)]");

    private final MeterRegistry metricas;

    AnswerIntegrityValidator(MeterRegistry metricas) {
        this.metricas = metricas;
    }

    Validacao validar(String texto, List<FonteContexto> fontes) {
        if (texto == null || RESPOSTA_SEM_CONTEXTO.equals(texto.trim())) {
            return new Validacao(RESPOSTA_SEM_CONTEXTO, true, Set.of());
        }
        Set<String> permitidos = fontes.stream().map(FonteContexto::marcador)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> encontrados = new HashSet<>();
        var matcher = CITACAO.matcher(texto);
        while (matcher.find()) {
            encontrados.add(matcher.group(1));
        }
        if (encontrados.isEmpty() || !permitidos.containsAll(encontrados)) {
            metricas.counter("contextpilot.rag.validacao", "resultado", "citacao_invalida").increment();
            return new Validacao(RESPOSTA_SEM_CONTEXTO, true, Set.of());
        }
        return new Validacao(texto.trim(), false, Set.copyOf(encontrados));
    }

    record Validacao(String texto, boolean recusa, Set<String> marcadores) {
    }
}
