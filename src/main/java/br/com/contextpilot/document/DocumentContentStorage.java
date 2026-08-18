package br.com.contextpilot.document;

import br.com.contextpilot.document.DocumentModels.ArmazenamentoDocumento;
import br.com.contextpilot.document.DocumentModels.ReferenciaConteudo;
import br.com.contextpilot.shared.domain.ResourceNotFoundException;
import org.springframework.stereotype.Component;

@Component
class DocumentContentStorage {

    private final ObjectStorage objetos;

    DocumentContentStorage(ObjectStorage objetos) {
        this.objetos = objetos;
    }

    byte[] obter(ReferenciaConteudo referencia) {
        if (referencia.armazenamento() == ArmazenamentoDocumento.S3) {
            if (referencia.chaveArmazenamento() == null || referencia.chaveArmazenamento().isBlank()) {
                throw new IllegalStateException("Documento S3 sem chave de armazenamento.");
            }
            return objetos.obter(referencia.chaveArmazenamento());
        }
        if (referencia.conteudoLegado() == null) {
            throw new ResourceNotFoundException("Conteudo legado do documento nao foi encontrado.");
        }
        return referencia.conteudoLegado();
    }
}
