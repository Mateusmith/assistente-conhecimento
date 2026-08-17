package br.com.contextpilot.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LocalEmbeddingProviderTest {

    @Test
    void deveGerarEmbeddingDeterministicoENormalizado() {
        var provedor = new LocalEmbeddingProvider(384);

        float[] primeiro = provedor.gerar("Politica de reembolso em trinta dias");
        float[] segundo = provedor.gerar("Politica de reembolso em trinta dias");
        double norma = Math.sqrt(java.util.stream.IntStream.range(0, primeiro.length)
                .mapToDouble(indice -> primeiro[indice] * primeiro[indice])
                .sum());

        assertThat(primeiro).containsExactly(segundo);
        assertThat(primeiro).hasSize(384);
        assertThat(norma).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void textosRelacionadosDevemCompartilharMaisSinais() {
        var provedor = new LocalEmbeddingProvider(384);
        float[] consulta = provedor.gerar("prazo para reembolso");
        float[] relacionado = provedor.gerar("o reembolso possui prazo de trinta dias");
        float[] diferente = provedor.gerar("servidor linux e memoria ram");

        assertThat(produtoEscalar(consulta, relacionado)).isGreaterThan(produtoEscalar(consulta, diferente));
    }

    private double produtoEscalar(float[] a, float[] b) {
        double resultado = 0;
        for (int indice = 0; indice < a.length; indice++) {
            resultado += a[indice] * b[indice];
        }
        return resultado;
    }
}
