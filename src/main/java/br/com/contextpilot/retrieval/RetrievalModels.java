package br.com.contextpilot.retrieval;

import java.util.UUID;

public final class RetrievalModels {

    private RetrievalModels() {
    }

    public record FonteRecuperada(
            UUID trechoId,
            UUID documentoId,
            String tituloDocumento,
            int ordemTrecho,
            String conteudo,
            double pontuacao) {
    }
}
