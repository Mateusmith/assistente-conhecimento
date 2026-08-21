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
        assertThat(resultado.provedor()).isEqualTo("local-extrativo-v3");
    }

    @Test
    void deveRecusarQuandoNaoHaRelacaoEntrePerguntaEContexto() {
        var fonte = new FonteContexto("F1", UUID.randomUUID(), UUID.randomUUID(),
                "Infraestrutura", 0, "O servidor possui memoria redundante.", 0.40);

        var resultado = gerador.gerar("Qual e o prazo para reembolso?", List.of(fonte));

        assertThat(resultado.texto()).isEqualTo(RESPOSTA_SEM_CONTEXTO);
    }

    @Test
    void devePriorizarARegraQueCobreTodosOsTermosDaPergunta() {
        var alta = fonte("F1", "Guia de atendimento ao cliente",
                "Chamados de prioridade alta recebem primeira resposta em ate 1 hora.", 0.93);
        var normal = fonte("F2", "Guia de atendimento ao cliente",
                "Chamados normais recebem primeira resposta em ate 8 horas uteis.", 0.88);
        var contrato = fonte("F3", "Manual de fornecedores",
                "O contrato deve indicar prazo, escopo e condicoes de pagamento.", 0.84);

        var resultado = gerador.gerar(
                "Qual e o prazo da primeira resposta para um chamado normal?",
                List.of(alta, normal, contrato));

        assertThat(resultado.texto())
                .contains("8 horas uteis", "[F2]")
                .doesNotContain("1 hora", "[F1]", "[F3]");
    }

    @Test
    void deveUsarTituloComoContextoSemResponderComCabecalhoMarkdown() {
        var privacidade = fonte("F1", "Guia interno de privacidade", """
                ## Incidentes de privacidade
                Suspeitas de acesso indevido devem ser comunicadas ao encarregado de dados em ate 24 horas.
                """, 0.90);
        var remoto = fonte("F2", "Politica de trabalho remoto",
                "A solicitacao de monitor deve ser aprovada pela lideranca.", 0.86);
        var incidente = fonte("F3", "Procedimento de resposta a incidentes",
                "Atualizar as partes interessadas a cada 30 minutos em incidentes P1.", 0.95);

        var resultado = gerador.gerar(
                "Em quanto tempo um incidente de privacidade deve ser comunicado?",
                List.of(privacidade, remoto, incidente));

        assertThat(resultado.texto())
                .contains("24 horas", "[F1]")
                .doesNotContain("##", "monitor", "30 minutos", "[F2]", "[F3]");
    }

    @Test
    void devePreferirPoliticaEspecificaDaViagemARegraGenericaConflitante() {
        var generica = fonte("F1", "Procedimento restrito",
                "O colaborador deve solicitar o reembolso em ate 30 dias corridos depois da despesa.", 0.94);
        var viagem = fonte("F2", "Politica de viagens e reembolsos",
                "A pessoa colaboradora deve enviar a solicitacao de reembolso em ate 10 dias corridos apos retornar da viagem.", 0.83);

        var resultado = gerador.gerar(
                "Qual e o prazo para solicitar reembolso de viagem?",
                List.of(generica, viagem));

        assertThat(resultado.texto())
                .contains("10 dias corridos", "[F2]")
                .doesNotContain("30 dias", "[F1]");
    }

    @Test
    void devePriorizarDuracaoConcretaAoResponderSobrePrazo() {
        var regraComDuracao = fonte("F1", "Politica de reembolso para viagens",
                "O colaborador deve solicitar o reembolso em ate 30 dias corridos depois da data da despesa.",
                0.86);
        var regraDeExcecao = fonte("F2", "Politica de reembolso para viagens",
                "Solicitacoes fora do prazo exigem justificativa formal da diretoria financeira.",
                0.94);

        var resultado = gerador.gerar(
                "Qual e o prazo para solicitar reembolso?",
                List.of(regraComDuracao, regraDeExcecao));

        assertThat(resultado.texto())
                .contains("30 dias corridos", "[F1]")
                .doesNotContain("justificativa formal", "[F2]");
    }

    private FonteContexto fonte(String marcador, String titulo, String conteudo, double pontuacao) {
        return new FonteContexto(marcador, UUID.randomUUID(), UUID.randomUUID(),
                titulo, 0, conteudo, pontuacao);
    }
}
