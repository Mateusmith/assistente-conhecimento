package br.com.contextpilot.evaluation;

import java.util.List;
import java.util.UUID;

import br.com.contextpilot.evaluation.EvaluationModels.CasoAvaliacao;
import br.com.contextpilot.evaluation.EvaluationModels.ComparacaoExecucoes;
import br.com.contextpilot.evaluation.EvaluationModels.ConjuntoAvaliacao;
import br.com.contextpilot.evaluation.EvaluationModels.CriarCasoRequest;
import br.com.contextpilot.evaluation.EvaluationModels.CriarConjuntoRequest;
import br.com.contextpilot.evaluation.EvaluationModels.ExecucaoAvaliacao;
import br.com.contextpilot.evaluation.EvaluationModels.AgendarExecucaoRequest;
import br.com.contextpilot.evaluation.EvaluationModels.ImportacaoCasosResponse;
import br.com.contextpilot.evaluation.EvaluationModels.ImportarCasosRequest;
import br.com.contextpilot.evaluation.EvaluationModels.PaginaResultados;
import br.com.contextpilot.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/espacos/{espacoId}/avaliacoes")
public class EvaluationController {

    private final EvaluationService servico;
    private final CurrentUser usuarioAtual;

    public EvaluationController(EvaluationService servico, CurrentUser usuarioAtual) {
        this.servico = servico;
        this.usuarioAtual = usuarioAtual;
    }

    @PostMapping
    ResponseEntity<ConjuntoAvaliacao> criarConjunto(
            @PathVariable UUID espacoId,
            @Valid @RequestBody CriarConjuntoRequest requisicao) {
        return ResponseEntity.status(201).body(servico.criarConjunto(espacoId, requisicao, usuarioAtual.obterId()));
    }

    @GetMapping
    List<ConjuntoAvaliacao> listarConjuntos(@PathVariable UUID espacoId) {
        return servico.listarConjuntos(espacoId, usuarioAtual.obterId());
    }

    @PostMapping("/{conjuntoId}/casos")
    ResponseEntity<CasoAvaliacao> adicionarCaso(
            @PathVariable UUID espacoId,
            @PathVariable UUID conjuntoId,
            @Valid @RequestBody CriarCasoRequest requisicao) {
        return ResponseEntity.status(201).body(
                servico.adicionarCaso(espacoId, conjuntoId, requisicao, usuarioAtual.obterId()));
    }

    @GetMapping("/{conjuntoId}/casos")
    List<CasoAvaliacao> listarCasos(@PathVariable UUID espacoId, @PathVariable UUID conjuntoId) {
        return servico.listarCasos(espacoId, conjuntoId, usuarioAtual.obterId());
    }

    @PostMapping("/{conjuntoId}/casos/importacoes")
    ResponseEntity<ImportacaoCasosResponse> importarCasos(
            @PathVariable UUID espacoId,
            @PathVariable UUID conjuntoId,
            @Valid @RequestBody ImportarCasosRequest requisicao) {
        return ResponseEntity.status(201).body(
                servico.importarCasos(espacoId, conjuntoId, requisicao, usuarioAtual.obterId()));
    }

    @PostMapping("/{conjuntoId}/execucoes")
    ResponseEntity<ExecucaoAvaliacao> executar(
            @PathVariable UUID espacoId,
            @PathVariable UUID conjuntoId,
            @RequestBody(required = false) AgendarExecucaoRequest requisicao) {
        return ResponseEntity.accepted().body(
                servico.executar(espacoId, conjuntoId, requisicao, usuarioAtual.obterId()));
    }

    @GetMapping("/{conjuntoId}/execucoes")
    List<ExecucaoAvaliacao> listarExecucoes(
            @PathVariable UUID espacoId,
            @PathVariable UUID conjuntoId,
            @RequestParam(defaultValue = "50") int limite) {
        return servico.listarExecucoes(espacoId, conjuntoId, limite, usuarioAtual.obterId());
    }

    @DeleteMapping("/{conjuntoId}/execucoes/{execucaoId}")
    ExecucaoAvaliacao cancelar(
            @PathVariable UUID espacoId,
            @PathVariable UUID conjuntoId,
            @PathVariable UUID execucaoId) {
        return servico.cancelar(espacoId, conjuntoId, execucaoId, usuarioAtual.obterId());
    }

    @GetMapping("/{conjuntoId}/execucoes/{execucaoId}")
    ExecucaoAvaliacao buscarExecucao(
            @PathVariable UUID espacoId,
            @PathVariable UUID conjuntoId,
            @PathVariable UUID execucaoId) {
        return servico.buscarExecucao(espacoId, conjuntoId, execucaoId, usuarioAtual.obterId());
    }

    @GetMapping("/{conjuntoId}/execucoes/{execucaoId}/resultados")
    PaginaResultados listarResultados(
            @PathVariable UUID espacoId,
            @PathVariable UUID conjuntoId,
            @PathVariable UUID execucaoId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "100") int tamanho) {
        return servico.listarResultados(
                espacoId, conjuntoId, execucaoId, pagina, tamanho, usuarioAtual.obterId());
    }

    @GetMapping("/{conjuntoId}/execucoes/{execucaoId}/comparacoes/{execucaoBaseId}")
    ComparacaoExecucoes comparar(
            @PathVariable UUID espacoId,
            @PathVariable UUID conjuntoId,
            @PathVariable UUID execucaoId,
            @PathVariable UUID execucaoBaseId) {
        return servico.comparar(espacoId, conjuntoId, execucaoId, execucaoBaseId, usuarioAtual.obterId());
    }
}
