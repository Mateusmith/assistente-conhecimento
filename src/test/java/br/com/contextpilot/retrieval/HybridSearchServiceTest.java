package br.com.contextpilot.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.com.contextpilot.governance.GovernanceService;
import br.com.contextpilot.reindex.EmbeddingIndexModels.EstadoIndice;
import br.com.contextpilot.reindex.EmbeddingIndexModels.IndiceEmbeddingResponse;
import br.com.contextpilot.reindex.EmbeddingIndexService;
import br.com.contextpilot.retrieval.EmbeddingProvider.ResultadoEmbedding;
import br.com.contextpilot.retrieval.RetrievalModels.FiltrosBusca;
import br.com.contextpilot.retrieval.RetrievalModels.FonteRecuperada;
import br.com.contextpilot.workspace.WorkspaceAccessService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HybridSearchServiceTest {

    @Test
    void deveCompartilharUmEmbeddingAoCompararTresEstrategias() {
        var repositorio = mock(HybridSearchRepository.class);
        var indices = mock(EmbeddingIndexService.class);
        var provedores = mock(EmbeddingProviderRegistry.class);
        var acesso = mock(WorkspaceAccessService.class);
        var governanca = mock(GovernanceService.class);
        var provedor = mock(EmbeddingProvider.class);
        UUID espacoId = UUID.randomUUID();
        UUID indiceId = UUID.randomUUID();
        var indice = new IndiceEmbeddingResponse(
                indiceId, espacoId, "local", "local-hashing-v2", 384, EstadoIndice.ATIVO,
                1, 1, 100, 0, "ana", Instant.now(), Instant.now(), Instant.now(), Instant.now(), null);

        when(indices.obterAtivo(espacoId)).thenReturn(indice);
        when(provedores.obter("local-hashing-v2")).thenReturn(provedor);
        when(provedor.gerarComUso(anyString()))
                .thenReturn(new ResultadoEmbedding(new float[384], 8, BigDecimal.ZERO));
        when(provedor.provedor()).thenReturn("local");
        when(provedor.nome()).thenReturn("local-hashing-v2");
        when(repositorio.buscar(eq(espacoId), eq(indiceId), eq("ana"), anyString(), anyString(),
                any(), any(), anyString(), anyString(), anyInt())).thenReturn(List.of());

        var servico = new HybridSearchService(
                repositorio, indices, provedores, acesso, new ObjectMapper(),
                new SimpleMeterRegistry(), governanca, 5, 0.1, 1, 3);

        var comparacao = servico.comparar(
                espacoId, "Qual e o prazo?", FiltrosBusca.vazios(), "ana");

        assertThat(comparacao.resultados()).hasSize(3);
        verify(provedor, times(1)).gerarComUso("Qual e o prazo?");
        verify(repositorio, times(3)).buscar(eq(espacoId), eq(indiceId), eq("ana"),
                anyString(), anyString(), any(), any(), anyString(), anyString(), anyInt());
    }

    @Test
    void deveSelecionarAncorasDiversasEAdicionarContextoVizinhoAutorizado() {
        var repositorio = mock(HybridSearchRepository.class);
        var indices = mock(EmbeddingIndexService.class);
        var provedores = mock(EmbeddingProviderRegistry.class);
        var acesso = mock(WorkspaceAccessService.class);
        var governanca = mock(GovernanceService.class);
        var provedor = mock(EmbeddingProvider.class);
        UUID espacoId = UUID.randomUUID();
        UUID indiceId = UUID.randomUUID();
        UUID documentoA = UUID.randomUUID();
        UUID documentoB = UUID.randomUUID();
        var indice = new IndiceEmbeddingResponse(
                indiceId, espacoId, "local", "local-hashing-v2", 384, EstadoIndice.ATIVO,
                4, 4, 100, 0, "ana", Instant.now(), Instant.now(), Instant.now(), Instant.now(), null);
        var principal = fonte(documentoA, 2, "Prazo de reembolso de trinta dias e documentos exigidos.", 0.92);
        var repetida = fonte(documentoA, 4, "Prazo de reembolso de trinta dias e documentos exigidos.", 0.90);
        var diversa = fonte(documentoB, 1, "A aprovacao depende da nota fiscal apresentada.", 0.76);
        var vizinha = fonte(documentoA, 1, "A solicitacao deve ser registrada no portal corporativo.", 0.20);

        when(indices.obterAtivo(espacoId)).thenReturn(indice);
        when(provedores.obter("local-hashing-v2")).thenReturn(provedor);
        when(provedor.gerarComUso(anyString()))
                .thenReturn(new ResultadoEmbedding(new float[384], 8, BigDecimal.ZERO));
        when(provedor.provedor()).thenReturn("local");
        when(provedor.nome()).thenReturn("local-hashing-v2");
        when(repositorio.buscar(eq(espacoId), eq(indiceId), eq("ana"), anyString(), anyString(),
                any(), any(), anyString(), anyString(), anyInt()))
                .thenReturn(List.of(principal, repetida, diversa));
        when(repositorio.buscarVizinhos(eq(espacoId), eq(indiceId), eq("ana"), eq(documentoA),
                anyInt(), anyInt(), anyString(), anyString(), any())).thenReturn(List.of(vizinha, principal));
        when(repositorio.buscarVizinhos(eq(espacoId), eq(indiceId), eq("ana"), eq(documentoB),
                anyInt(), anyInt(), anyString(), anyString(), any())).thenReturn(List.of(diversa));

        var servico = new HybridSearchService(
                repositorio, indices, provedores, acesso, new ObjectMapper(),
                new SimpleMeterRegistry(), governanca, 4, 0.1, 1, 2);

        var resultado = servico.buscar(
                espacoId, "Qual e o prazo e como solicitar reembolso?", "ana", null, FiltrosBusca.vazios());

        assertThat(resultado.totalCandidatos()).isEqualTo(3);
        assertThat(resultado.fontes()).extracting(FonteRecuperada::trechoId)
                .contains(principal.trechoId(), diversa.trechoId(), vizinha.trechoId())
                .doesNotHaveDuplicates();
        assertThat(resultado.fontes().stream().filter(fonte -> fonte.trechoId().equals(vizinha.trechoId()))
                .findFirst().orElseThrow().pontuacao()).isGreaterThan(vizinha.pontuacao());
    }

    private FonteRecuperada fonte(UUID documentoId, int ordem, String conteudo, double pontuacao) {
        return new FonteRecuperada(UUID.randomUUID(), documentoId, "Documento " + documentoId,
                ordem, conteudo, pontuacao, pontuacao, pontuacao);
    }
}
