package br.com.contextpilot.document;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.com.contextpilot.document.DocumentModels.TarefaIngestao;
import br.com.contextpilot.document.DocumentModels.OrigemTexto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DocumentIngestionTransaction {

    private final DocumentRepository repositorio;
    private final Clock relogio;

    DocumentIngestionTransaction(DocumentRepository repositorio, Clock relogio) {
        this.repositorio = repositorio;
        this.relogio = relogio;
    }

    @Transactional
    public void concluir(
            TarefaIngestao tarefa,
            UUID espacoId,
            List<String> trechos,
            List<String> vetores,
            OrigemTexto origemTexto,
            int paginasOcr) {
        Instant instante = Instant.now(relogio);
        repositorio.substituirTrechos(tarefa.documentoId(), espacoId, trechos, vetores, instante);
        repositorio.concluir(tarefa, origemTexto, paginasOcr, instante);
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
