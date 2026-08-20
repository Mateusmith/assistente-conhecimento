package br.com.contextpilot.reindex;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import br.com.contextpilot.reindex.EmbeddingIndexModels.IndiceParaProcessar;
import br.com.contextpilot.reindex.EmbeddingIndexModels.TrechoParaIndexar;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class EmbeddingIndexTransaction {

    private final EmbeddingIndexRepository repositorio;
    private final Clock relogio;

    EmbeddingIndexTransaction(EmbeddingIndexRepository repositorio, Clock relogio) {
        this.repositorio = repositorio;
        this.relogio = relogio;
    }

    @Transactional
    public void salvarLote(IndiceParaProcessar indice, List<TrechoVetor> vetores) {
        Instant agora = Instant.now(relogio);
        for (TrechoVetor vetor : vetores) {
            repositorio.salvarVetor(indice.id(), vetor.trecho().id(), vetor.vetor(), agora);
        }
        repositorio.atualizarProgresso(indice.id(), indice.espacoId());
    }

    record TrechoVetor(TrechoParaIndexar trecho, String vetor) {
    }
}
