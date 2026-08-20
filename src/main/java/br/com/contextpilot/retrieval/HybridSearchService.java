package br.com.contextpilot.retrieval;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.contextpilot.reindex.EmbeddingIndexService;
import br.com.contextpilot.governance.GovernanceService;
import br.com.contextpilot.retrieval.RetrievalModels.ComparacaoBuscaResponse;
import br.com.contextpilot.retrieval.RetrievalModels.EstrategiaBusca;
import br.com.contextpilot.retrieval.RetrievalModels.FiltrosBusca;
import br.com.contextpilot.retrieval.RetrievalModels.FonteRecuperada;
import br.com.contextpilot.retrieval.RetrievalModels.ResultadoBusca;
import br.com.contextpilot.retrieval.RetrievalModels.ResultadoEstrategia;
import br.com.contextpilot.shared.domain.BusinessRuleException;
import br.com.contextpilot.workspace.WorkspaceAccessService;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HybridSearchService {

    private final HybridSearchRepository repositorio;
    private final EmbeddingIndexService indices;
    private final EmbeddingProviderRegistry provedores;
    private final WorkspaceAccessService acessoEspaco;
    private final ObjectMapper json;
    private final MeterRegistry metricas;
    private final GovernanceService governanca;
    private final int limiteFontes;
    private final double pontuacaoMinima;
    private final int janelaContexto;
    private final int limiteAncoras;

    public HybridSearchService(
            HybridSearchRepository repositorio,
            EmbeddingIndexService indices,
            EmbeddingProviderRegistry provedores,
            WorkspaceAccessService acessoEspaco,
            ObjectMapper json,
            MeterRegistry metricas,
            GovernanceService governanca,
            @Value("${contextpilot.busca.limite-fontes}") int limiteFontes,
            @Value("${contextpilot.busca.pontuacao-minima}") double pontuacaoMinima,
            @Value("${contextpilot.busca.janela-contexto:1}") int janelaContexto,
            @Value("${contextpilot.busca.limite-ancoras:3}") int limiteAncoras) {
        this.repositorio = repositorio;
        this.indices = indices;
        this.provedores = provedores;
        this.acessoEspaco = acessoEspaco;
        this.json = json;
        this.metricas = metricas;
        this.governanca = governanca;
        this.limiteFontes = limiteFontes;
        this.pontuacaoMinima = pontuacaoMinima;
        this.janelaContexto = Math.max(0, Math.min(janelaContexto, 3));
        this.limiteAncoras = Math.max(1, Math.min(limiteAncoras, limiteFontes));
    }

    public List<FonteRecuperada> buscar(UUID espacoId, String pergunta, String usuarioId) {
        return buscar(espacoId, pergunta, usuarioId, EstrategiaBusca.HIBRIDA, FiltrosBusca.vazios()).fontes();
    }

    public ResultadoBusca buscar(
            UUID espacoId,
            String pergunta,
            String usuarioId,
            EstrategiaBusca estrategia,
            FiltrosBusca filtros) {
        acessoEspaco.exigirMembro(espacoId, usuarioId);
        EstrategiaBusca estrategiaValida = estrategia == null ? EstrategiaBusca.HIBRIDA : estrategia;
        if (pergunta == null || pergunta.isBlank()) {
            return new ResultadoBusca(null, null, estrategiaValida, 0, List.of());
        }
        FiltrosBusca filtrosValidos = normalizar(filtros);
        ContextoBusca contexto = preparar(espacoId, pergunta.trim());
        return executar(espacoId, usuarioId, estrategiaValida, filtrosValidos, contexto);
    }

    public ComparacaoBuscaResponse comparar(UUID espacoId, String pergunta, FiltrosBusca filtros, String usuarioId) {
        acessoEspaco.exigirMembro(espacoId, usuarioId);
        FiltrosBusca filtrosValidos = normalizar(filtros);
        ContextoBusca contexto = preparar(espacoId, pergunta.trim());
        List<ResultadoEstrategia> resultados = java.util.Arrays.stream(EstrategiaBusca.values())
                .map(estrategia -> {
                    ResultadoBusca resultado = executar(
                            espacoId, usuarioId, estrategia, filtrosValidos, contexto);
                    return new ResultadoEstrategia(estrategia, resultado.fontes());
                })
                .toList();
        Set<UUID> semanticos = ids(resultados, EstrategiaBusca.SEMANTICA);
        Set<UUID> textuais = ids(resultados, EstrategiaBusca.TEXTUAL);
        Set<UUID> uniao = new HashSet<>(semanticos);
        uniao.addAll(textuais);
        Set<UUID> intersecao = new HashSet<>(semanticos);
        intersecao.retainAll(textuais);
        double sobreposicao = uniao.isEmpty() ? 1.0 : (double) intersecao.size() / uniao.size();
        return new ComparacaoBuscaResponse(
                contexto.indiceId(), contexto.modeloEmbedding(), resultados, sobreposicao);
    }

    private ContextoBusca preparar(UUID espacoId, String pergunta) {
        var indice = indices.obterAtivo(espacoId);
        var provedor = provedores.obter(indice.modelo());
        var embedding = provedor.gerarComUso(pergunta);
        governanca.registrarConsumoIa(espacoId, provedor.provedor(), provedor.nome(), "EMBEDDING",
                embedding.tokensEntrada(), 0, embedding.custoEstimadoUsd());
        return new ContextoBusca(indice.id(), indice.modelo(), pergunta, VectorText.serializar(embedding.vetor()));
    }

    private ResultadoBusca executar(
            UUID espacoId,
            String usuarioId,
            EstrategiaBusca estrategia,
            FiltrosBusca filtros,
            ContextoBusca contexto) {
        List<FonteRecuperada> candidatas = repositorio.buscar(
                espacoId, contexto.indiceId(), usuarioId, contexto.pergunta(), contexto.embedding(), estrategia, filtros,
                serializar(filtros.metadados()), serializar(filtros.tags()), limiteFontes * 4);
        List<FonteRecuperada> elegiveis = reranquear(candidatas, contexto.pergunta()).stream()
                .filter(fonte -> fonte.pontuacao() >= pontuacaoMinima)
                .toList();
        List<FonteRecuperada> fontes = selecionarContexto(
                espacoId, usuarioId, estrategia, contexto, elegiveis);
        metricas.counter("contextpilot.busca.total", "estrategia", estrategia.name().toLowerCase())
                .increment();
        metricas.summary("contextpilot.busca.candidatos").record(candidatas.size());
        metricas.summary("contextpilot.busca.fontes_contexto").record(fontes.size());
        return new ResultadoBusca(contexto.indiceId(), contexto.modeloEmbedding(), estrategia,
                candidatas.size(), fontes);
    }

    private List<FonteRecuperada> selecionarContexto(
            UUID espacoId,
            String usuarioId,
            EstrategiaBusca estrategia,
            ContextoBusca contexto,
            List<FonteRecuperada> elegiveis) {
        if (elegiveis.isEmpty()) {
            return List.of();
        }

        List<FonteRecuperada> ancoras = selecionarDiversas(elegiveis, Math.min(limiteAncoras, limiteFontes));
        Map<UUID, FonteRecuperada> selecionadas = new LinkedHashMap<>();
        ancoras.forEach(fonte -> selecionadas.put(fonte.trechoId(), fonte));

        if (janelaContexto > 0 && selecionadas.size() < limiteFontes) {
            for (FonteRecuperada ancora : ancoras) {
                List<FonteRecuperada> vizinhas = repositorio.buscarVizinhos(
                        espacoId, contexto.indiceId(), usuarioId, ancora.documentoId(),
                        ancora.ordemTrecho() - janelaContexto, ancora.ordemTrecho() + janelaContexto,
                        contexto.pergunta(), contexto.embedding(), estrategia);
                for (FonteRecuperada vizinha : vizinhas) {
                    int distancia = Math.abs(vizinha.ordemTrecho() - ancora.ordemTrecho());
                    if (distancia == 0) {
                        continue;
                    }
                    double pontuacaoContextual = Math.max(vizinha.pontuacao(),
                            ancora.pontuacao() * Math.pow(0.78, distancia));
                    FonteRecuperada contextual = new FonteRecuperada(
                            vizinha.trechoId(), vizinha.documentoId(), vizinha.tituloDocumento(),
                            vizinha.ordemTrecho(), vizinha.conteudo(), vizinha.pontuacaoSemantica(),
                            vizinha.pontuacaoTextual(), Math.min(1.0, pontuacaoContextual));
                    selecionadas.putIfAbsent(contextual.trechoId(), contextual);
                }
            }
        }

        for (FonteRecuperada candidata : elegiveis) {
            if (selecionadas.size() >= limiteFontes) {
                break;
            }
            selecionadas.putIfAbsent(candidata.trechoId(), candidata);
        }
        return selecionadas.values().stream()
                .sorted(Comparator.comparingDouble(FonteRecuperada::pontuacao).reversed()
                        .thenComparing(FonteRecuperada::documentoId)
                        .thenComparingInt(FonteRecuperada::ordemTrecho))
                .limit(limiteFontes)
                .toList();
    }

    private List<FonteRecuperada> selecionarDiversas(List<FonteRecuperada> candidatas, int limite) {
        List<FonteRecuperada> restantes = new ArrayList<>(candidatas);
        List<FonteRecuperada> selecionadas = new ArrayList<>();
        while (!restantes.isEmpty() && selecionadas.size() < limite) {
            FonteRecuperada melhor = restantes.stream()
                    .max(Comparator.<FonteRecuperada>comparingDouble(
                                    candidata -> pontuacaoMmr(candidata, selecionadas))
                            .thenComparing(FonteRecuperada::trechoId))
                    .orElseThrow();
            selecionadas.add(melhor);
            restantes.remove(melhor);
        }
        return List.copyOf(selecionadas);
    }

    private double pontuacaoMmr(FonteRecuperada candidata, List<FonteRecuperada> selecionadas) {
        if (selecionadas.isEmpty()) {
            return candidata.pontuacao();
        }
        double maiorSimilaridade = selecionadas.stream()
                .mapToDouble(selecionada -> similaridade(candidata.conteudo(), selecionada.conteudo()))
                .max().orElse(0.0);
        return candidata.pontuacao() * 0.82 + (1.0 - maiorSimilaridade) * 0.18;
    }

    private double similaridade(String esquerda, String direita) {
        Set<String> termosEsquerda = termos(esquerda);
        Set<String> termosDireita = termos(direita);
        if (termosEsquerda.isEmpty() || termosDireita.isEmpty()) {
            return 0.0;
        }
        Set<String> uniao = new HashSet<>(termosEsquerda);
        uniao.addAll(termosDireita);
        Set<String> intersecao = new HashSet<>(termosEsquerda);
        intersecao.retainAll(termosDireita);
        return (double) intersecao.size() / uniao.size();
    }

    private List<FonteRecuperada> reranquear(List<FonteRecuperada> candidatas, String pergunta) {
        Set<String> termos = termos(pergunta);
        return candidatas.stream()
                .map(fonte -> {
                    double cobertura = cobertura(termos, fonte.conteudo());
                    double titulo = cobertura(termos, fonte.tituloDocumento());
                    double pontuacao = Math.min(1.0, fonte.pontuacao() * 0.85 + cobertura * 0.10 + titulo * 0.05);
                    return new FonteRecuperada(fonte.trechoId(), fonte.documentoId(), fonte.tituloDocumento(),
                            fonte.ordemTrecho(), fonte.conteudo(), fonte.pontuacaoSemantica(),
                            fonte.pontuacaoTextual(), pontuacao);
                })
                .sorted(Comparator.comparingDouble(FonteRecuperada::pontuacao).reversed()
                        .thenComparing(FonteRecuperada::documentoId)
                        .thenComparingInt(FonteRecuperada::ordemTrecho))
                .toList();
    }

    private FiltrosBusca normalizar(FiltrosBusca filtros) {
        if (filtros == null) {
            return FiltrosBusca.vazios();
        }
        if (filtros.criadoDe() != null && filtros.criadoAte() != null
                && filtros.criadoDe().isAfter(filtros.criadoAte())) {
            throw new BusinessRuleException("A data inicial do filtro nao pode ser posterior a data final.");
        }
        List<UUID> documentos = filtros.documentos() == null ? List.of() : filtros.documentos().stream().distinct().toList();
        Map<String, String> metadados = filtros.metadados() == null ? Map.of()
                : filtros.metadados().entrySet().stream().collect(Collectors.toMap(
                        entrada -> entrada.getKey().trim(), entrada -> entrada.getValue().trim(),
                        (primeiro, segundo) -> segundo, LinkedHashMap::new));
        List<String> tags = filtros.tags() == null ? List.of() : filtros.tags().stream()
                .map(String::trim).filter(valor -> !valor.isBlank()).distinct().toList();
        String tipoMime = filtros.tipoMime() == null || filtros.tipoMime().isBlank() ? null : filtros.tipoMime().trim();
        return new FiltrosBusca(documentos, metadados, tags, tipoMime, filtros.criadoDe(), filtros.criadoAte());
    }

    private Set<UUID> ids(List<ResultadoEstrategia> resultados, EstrategiaBusca estrategia) {
        return resultados.stream().filter(item -> item.estrategia() == estrategia).findFirst().orElseThrow()
                .fontes().stream().map(FonteRecuperada::trechoId).collect(Collectors.toSet());
    }

    private double cobertura(Set<String> termosPergunta, String texto) {
        if (termosPergunta.isEmpty()) {
            return 0;
        }
        Set<String> termosTexto = termos(texto);
        long encontrados = termosPergunta.stream().filter(termosTexto::contains).count();
        return (double) encontrados / termosPergunta.size();
    }

    private Set<String> termos(String texto) {
        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").trim();
        return java.util.Arrays.stream(normalizado.split("\\s+"))
                .filter(termo -> termo.length() > 2).collect(Collectors.toSet());
    }

    private String serializar(Object valor) {
        try {
            return json.writeValueAsString(valor);
        } catch (JacksonException excecao) {
            throw new IllegalStateException("Nao foi possivel serializar os filtros da busca.", excecao);
        }
    }

    private record ContextoBusca(UUID indiceId, String modeloEmbedding, String pergunta, String embedding) {
    }
}
