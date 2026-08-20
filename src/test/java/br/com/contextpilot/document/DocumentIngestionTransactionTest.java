package br.com.contextpilot.document;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import br.com.contextpilot.document.DocumentModels.OrigemTexto;
import br.com.contextpilot.document.DocumentModels.TarefaIngestao;
import br.com.contextpilot.reindex.EmbeddingIndexService;
import br.com.contextpilot.shared.domain.ConflictException;
import org.junit.jupiter.api.Test;

class DocumentIngestionTransactionTest {

    @Test
    void deveConfirmarIndiceAtivoAntesDePersistirTrechos() {
        var repositorio = mock(DocumentRepository.class);
        var indices = mock(EmbeddingIndexService.class);
        var transacao = novaTransacao(repositorio, indices);
        UUID espacoId = UUID.randomUUID();
        UUID indiceId = UUID.randomUUID();
        var tarefa = new TarefaIngestao(UUID.randomUUID(), UUID.randomUUID(), 1);
        var semVisao = VisionAnalyzer.ResultadoVisao.naoAplicada();

        transacao.concluir(tarefa, espacoId, indiceId, List.of("conteudo"),
                List.of("[0.0]"), List.of(false), OrigemTexto.NATIVO, 0, semVisao);

        var ordem = inOrder(indices, repositorio);
        ordem.verify(indices).exigirIndiceAtivo(espacoId, indiceId);
        ordem.verify(repositorio).substituirTrechos(
                eq(tarefa.documentoId()), eq(espacoId), eq(indiceId), any(), any(), any(), any());
        ordem.verify(repositorio).concluir(eq(tarefa), eq(OrigemTexto.NATIVO), eq(0), eq(semVisao), any());
    }

    @Test
    void naoDevePersistirQuandoIndiceMudarDuranteIngestao() {
        var repositorio = mock(DocumentRepository.class);
        var indices = mock(EmbeddingIndexService.class);
        var transacao = novaTransacao(repositorio, indices);
        UUID espacoId = UUID.randomUUID();
        UUID indiceId = UUID.randomUUID();
        var tarefa = new TarefaIngestao(UUID.randomUUID(), UUID.randomUUID(), 1);
        var semVisao = VisionAnalyzer.ResultadoVisao.naoAplicada();
        doThrow(new ConflictException("Indice alterado."))
                .when(indices).exigirIndiceAtivo(espacoId, indiceId);

        assertThatThrownBy(() -> transacao.concluir(
                tarefa, espacoId, indiceId, List.of("conteudo"), List.of("[0.0]"),
                List.of(false), OrigemTexto.NATIVO, 0, semVisao))
                .isInstanceOf(ConflictException.class);

        verify(repositorio, never()).substituirTrechos(any(), any(), any(), any(), any(), any(), any());
    }

    private DocumentIngestionTransaction novaTransacao(
            DocumentRepository repositorio, EmbeddingIndexService indices) {
        Clock relogio = Clock.fixed(Instant.parse("2026-08-17T20:00:00Z"), ZoneOffset.UTC);
        return new DocumentIngestionTransaction(repositorio, indices, relogio);
    }
}
