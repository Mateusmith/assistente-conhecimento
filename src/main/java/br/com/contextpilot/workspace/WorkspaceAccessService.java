package br.com.contextpilot.workspace;

import java.util.UUID;

import br.com.contextpilot.shared.domain.ForbiddenOperationException;
import br.com.contextpilot.shared.domain.ResourceNotFoundException;
import br.com.contextpilot.workspace.WorkspaceModels.PapelMembro;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceAccessService {

    private final WorkspaceRepository repositorio;

    public WorkspaceAccessService(WorkspaceRepository repositorio) {
        this.repositorio = repositorio;
    }

    public PapelMembro exigirMembro(UUID espacoId, String usuarioId) {
        return repositorio.buscarPapel(espacoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Espaco nao encontrado."));
    }

    public void exigirCuradoria(UUID espacoId, String usuarioId) {
        PapelMembro papel = exigirMembro(espacoId, usuarioId);
        if (papel == PapelMembro.LEITOR) {
            throw new ForbiddenOperationException("Somente proprietarios e curadores podem alterar o conhecimento.");
        }
    }

    public void exigirProprietario(UUID espacoId, String usuarioId) {
        if (exigirMembro(espacoId, usuarioId) != PapelMembro.PROPRIETARIO) {
            throw new ForbiddenOperationException("Somente o proprietario pode gerenciar membros.");
        }
    }
}
