package br.com.contextpilot.document;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class PromptInjectionDetector {

    private static final List<Pattern> PADROES = List.of(
            Pattern.compile("\\b(ignore|desconsidere|ignore todas?)\\b.{0,80}\\b(instrucoes|instructions|regras)\\b"),
            Pattern.compile("\\b(system prompt|prompt do sistema|developer message|mensagem do desenvolvedor)\\b"),
            Pattern.compile("\\b(revele|exiba|vaze|leak)\\b.{0,80}\\b(segredo|token|senha|credencial|chave)\\b"),
            Pattern.compile("\\b(execute|chame|call)\\b.{0,60}\\b(ferramenta|tool|comando|shell)\\b"),
            Pattern.compile("\\b(voce agora e|you are now|novo papel|new role)\\b"));

    public boolean suspeito(String texto) {
        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        return PADROES.stream().anyMatch(padrao -> padrao.matcher(normalizado).find());
    }
}
