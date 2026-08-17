package br.com.contextpilot.document;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import br.com.contextpilot.document.DocumentModels.ConcederPermissaoRequest;
import br.com.contextpilot.document.DocumentModels.DocumentoResponse;
import br.com.contextpilot.document.DocumentModels.VisibilidadeDocumento;
import br.com.contextpilot.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/espacos/{espacoId}/documentos")
public class DocumentController {

    private final DocumentService servico;
    private final CurrentUser usuarioAtual;

    public DocumentController(DocumentService servico, CurrentUser usuarioAtual) {
        this.servico = servico;
        this.usuarioAtual = usuarioAtual;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DocumentoResponse> enviar(
            @PathVariable UUID espacoId,
            @RequestParam String titulo,
            @RequestParam(defaultValue = "ESPACO") VisibilidadeDocumento visibilidade,
            @RequestPart("arquivo") MultipartFile arquivo) {
        DocumentoResponse criado = servico.enviar(espacoId, titulo, visibilidade, arquivo, usuarioAtual.obterId());
        return ResponseEntity.created(URI.create("/api/v1/espacos/" + espacoId + "/documentos/" + criado.id())).body(criado);
    }

    @GetMapping
    List<DocumentoResponse> listar(@PathVariable UUID espacoId) {
        return servico.listar(espacoId, usuarioAtual.obterId());
    }

    @GetMapping("/{documentoId}")
    DocumentoResponse buscar(@PathVariable UUID espacoId, @PathVariable UUID documentoId) {
        return servico.buscar(espacoId, documentoId, usuarioAtual.obterId());
    }

    @GetMapping("/{documentoId}/conteudo")
    ResponseEntity<byte[]> baixar(@PathVariable UUID espacoId, @PathVariable UUID documentoId) {
        DocumentoResponse documento = servico.buscar(espacoId, documentoId, usuarioAtual.obterId());
        byte[] conteudo = servico.baixar(espacoId, documentoId, usuarioAtual.obterId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(documento.tipoMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(documento.nomeArquivo(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(conteudo);
    }

    @PostMapping("/{documentoId}/permissoes")
    ResponseEntity<Void> concederPermissao(
            @PathVariable UUID espacoId,
            @PathVariable UUID documentoId,
            @Valid @RequestBody ConcederPermissaoRequest requisicao) {
        servico.concederPermissao(espacoId, documentoId, requisicao, usuarioAtual.obterId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{documentoId}/reprocessamento")
    ResponseEntity<DocumentoResponse> reprocessar(@PathVariable UUID espacoId, @PathVariable UUID documentoId) {
        return ResponseEntity.accepted().body(servico.reprocessar(espacoId, documentoId, usuarioAtual.obterId()));
    }
}
