package br.com.contextpilot.answer;

import static br.com.contextpilot.answer.AnswerModels.RESPOSTA_SEM_CONTEXTO;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import br.com.contextpilot.answer.AnswerModels.FonteContexto;
import org.junit.jupiter.api.Test;

class LocalAnswerGeneratorTest {

    private final LocalAnswerGenerator gerador = new LocalAnswerGenerator();

    @Test
    void deveResponderComMarcadorDaFonte() {
        var fonte = new FonteContexto("F1", UUID.randomUUID(), UUID.randomUUID(),
                "Politica financeira", 0,
                "O prazo para solicitar reembolso e de 30 dias apos a compra.", 0.91);

        var resultado = gerador.gerar("Qual e o prazo para reembolso?", List.of(fonte));

        assertThat(resultado.texto()).contains("30 dias", "[F1]");
        assertThat(resultado.provedor()).isEqualTo("local-extrativo-v2");
    }

    @Test
    void deveRecusarQuandoNaoHaRelacaoEntrePerguntaEContexto() {
        var fonte = new FonteContexto("F1", UUID.randomUUID(), UUID.randomUUID(),
                "Infraestrutura", 0, "O servidor possui memoria redundante.", 0.40);

        var resultado = gerador.gerar("Qual e o prazo para reembolso?", List.of(fonte));

        assertThat(resultado.texto()).isEqualTo(RESPOSTA_SEM_CONTEXTO);
    }
}
