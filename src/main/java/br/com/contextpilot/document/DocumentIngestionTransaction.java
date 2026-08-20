package br.com.contextpilot.document;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.com.contextpilot.document.DocumentModels.TarefaIngestao;
import br.com.contextpilot.document.DocumentModels.OrigemTexto;
import br.com.contextpilot.document.VisionAnalyzer.ResultadoVisao;
import br.com.contextpilot.reindex.EmbeddingIndexService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DocumentIngestionTransaction {

    private final DocumentRepository repositorio;
    private final EmbeddingIndexService indices;
    private final Clock relogio;

    DocumentIngestionTransaction(
            DocumentRepository repositorio,
            EmbeddingIndexService indices,
            Clock relogio) {
        this.repositorio = repositorio;
        this.indices = indices;
        this.relogio = relogio;
    }

    @Transactional
    public void concluir(
            TarefaIngestao tarefa,
            UUID espacoId,
            UUID indiceId,
            List<String> trechos,
            List<String> vetores,
            List<Boolean> riscosPrompt,
            OrigemTexto origemTexto,
            int paginasOcr,
            ResultadoVisao resultadoVisao) {
        Instant instante = Instant.now(relogio);
        indices.exigirIndiceAtivo(espacoId, indiceId);
        repositorio.substituirTrechos(
                tarefa.documentoId(), espacoId, indiceId, trechos, vetores, riscosPrompt, instante);
        repositorio.concluir(tarefa, origemTexto, paginasOcr, resultadoVisao, instante);
    }

    @Transactional
    public void falhar(TarefaIngestao tarefa, RuntimeException excecao) {
        Instant agora = Instant.now(relogio);
        long segundos = Math.min(60, 1L << Math.min(tarefa.tentativa(), 5));
        String mensagem = excecao.getMessage() == null ? excecao.getClass().getSimpleName() : excecao.getMessage();
        repositorio.falhar(tarefa, mensagem.substring(0, Math.min(mensagem.length(), 1000)),
                agora, agora.plus(Duration.ofSeconds(segundos)));
    }
}
