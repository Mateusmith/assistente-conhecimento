package br.com.contextpilot.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class SensitiveDataProtectorTest {

    @Test
    void deveTokenizarValoresRepetidosERestaurarRespostaLocalmente() {
        var metricas = new SimpleMeterRegistry();
        var protetor = new SensitiveDataProtector(metricas);
        String original = "Contato ana@empresa.com.br ou ana@empresa.com.br, CPF 123.456.789-00, "
                + "CNPJ 12.345.678/0001-99 e telefone (11) 99876-5432.";

        var protegido = protetor.proteger(original);

        assertThat(protegido.texto())
                .contains("[[DADO_EMAIL_1]]", "[[DADO_CPF_1]]", "[[DADO_CNPJ_1]]", "[[DADO_TELEFONE_1]]")
                .doesNotContain("ana@empresa.com.br", "123.456.789-00", "12.345.678/0001-99", "99876-5432");
        assertThat(protegido.texto()).containsSequence("[[DADO_EMAIL_1]]", " ou ", "[[DADO_EMAIL_1]]");
        assertThat(protegido.totalProtegido()).isEqualTo(4);
        assertThat(protegido.restaurar("Envie para [[DADO_EMAIL_1]] e confirme [[DADO_CPF_1]]."))
                .isEqualTo("Envie para ana@empresa.com.br e confirme 123.456.789-00.");
        assertThat(metricas.counter("contextpilot.ia.dados_sensiveis_protegidos", "tipo", "email").count())
                .isEqualTo(1.0);
    }

    @Test
    void deveProtegerCredenciaisSemRegistrarOsValoresEmMetricas() {
        var metricas = new SimpleMeterRegistry();
        var protetor = new SensitiveDataProtector(metricas);

        var protegido = protetor.proteger(
                "Use sk-abcdefghijklmnopqrstuvwxyz123456 e AKIAABCDEFGHIJKLMNOP somente no cofre.");

        assertThat(protegido.texto()).contains("[[DADO_CHAVE_API_1]]", "[[DADO_CHAVE_API_2]]");
        assertThat(protegido.quantidadesPorTipo()).containsEntry("CHAVE_API", 2);
        assertThat(metricas.getMeters()).allMatch(medidor -> !medidor.getId().getTags().toString().contains("sk-"));
    }
}
