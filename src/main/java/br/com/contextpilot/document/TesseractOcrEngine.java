package br.com.contextpilot.document;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import br.com.contextpilot.configuration.OcrProperties;
import br.com.contextpilot.shared.domain.BusinessRuleException;
import br.com.contextpilot.shared.domain.ServiceUnavailableException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

@Component
class TesseractOcrEngine implements OcrEngine {

    private final OcrProperties propriedades;

    TesseractOcrEngine(OcrProperties propriedades) {
        this.propriedades = propriedades;
    }

    @Override
    public ResultadoOcr extrair(PDDocument documento) {
        if (!propriedades.ativo()) {
            throw new BusinessRuleException("O PDF nao possui texto nativo suficiente e o OCR esta desativado.");
        }
        validarConfiguracao();
        int paginas = documento.getNumberOfPages();
        if (paginas == 0) {
            throw new BusinessRuleException("O PDF nao possui paginas para processar.");
        }
        if (paginas > propriedades.maximoPaginas()) {
            throw new BusinessRuleException("O PDF digitalizado excede o limite de "
                    + propriedades.maximoPaginas() + " paginas para OCR.");
        }

        Path diretorio = criarDiretorioTemporario();
        try {
            var renderizador = new PDFRenderer(documento);
            var texto = new StringBuilder();
            for (int indice = 0; indice < paginas; indice++) {
                BufferedImage imagem = renderizador.renderImageWithDPI(indice, propriedades.dpi(), ImageType.RGB);
                Path arquivoImagem = diretorio.resolve("pagina-%03d.png".formatted(indice + 1));
                if (!ImageIO.write(imagem, "png", arquivoImagem.toFile())) {
                    throw new ServiceUnavailableException("Nao foi possivel preparar uma pagina para OCR.");
                }
                texto.append(executarTesseract(arquivoImagem, diretorio, indice + 1)).append('\n');
            }
            return new ResultadoOcr(texto.toString(), paginas);
        } catch (IOException excecao) {
            throw new ServiceUnavailableException("Nao foi possivel renderizar o PDF para OCR.", excecao);
        } finally {
            removerDiretorio(diretorio);
        }
    }

    @Override
    public ResultadoOcr extrair(BufferedImage imagem) {
        if (!propriedades.ativo()) {
            throw new BusinessRuleException("O OCR de imagens esta desativado.");
        }
        validarConfiguracao();
        Path diretorio = criarDiretorioTemporario();
        try {
            Path arquivoImagem = diretorio.resolve("imagem.png");
            if (!ImageIO.write(imagem, "png", arquivoImagem.toFile())) {
                throw new ServiceUnavailableException("Nao foi possivel preparar a imagem para OCR.");
            }
            return new ResultadoOcr(executarTesseract(arquivoImagem, diretorio, 1), 1);
        } catch (IOException excecao) {
            throw new ServiceUnavailableException("Nao foi possivel preparar a imagem para OCR.", excecao);
        } finally {
            removerDiretorio(diretorio);
        }
    }

    @Override
    public boolean ativo() {
        return propriedades.ativo();
    }

    private String executarTesseract(Path imagem, Path diretorio, int pagina) {
        Path baseSaida = diretorio.resolve("ocr-%03d".formatted(pagina));
        Path arquivoErro = diretorio.resolve("ocr-%03d.erro.log".formatted(pagina));
        var processo = new ProcessBuilder(
                propriedades.executavel(),
                imagem.toString(),
                baseSaida.toString(),
                "-l", propriedades.idiomas(),
                "--oem", "1",
                "--psm", "3")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(arquivoErro.toFile());
        try {
            Process execucao = processo.start();
            boolean terminou = execucao.waitFor(propriedades.timeoutPorPagina().toMillis(), TimeUnit.MILLISECONDS);
            if (!terminou) {
                execucao.destroyForcibly();
                throw new ServiceUnavailableException("O OCR excedeu o tempo limite na pagina " + pagina + ".");
            }
            if (execucao.exitValue() != 0) {
                String detalhe = Files.exists(arquivoErro)
                        ? Files.readString(arquivoErro, StandardCharsets.UTF_8).trim()
                        : "sem detalhe";
                throw new ServiceUnavailableException("O OCR falhou na pagina " + pagina + ": "
                        + detalhe.substring(0, Math.min(detalhe.length(), 300)));
            }
            return Files.readString(Path.of(baseSaida + ".txt"), StandardCharsets.UTF_8);
        } catch (IOException excecao) {
            throw new ServiceUnavailableException("O executavel do OCR nao esta disponivel.", excecao);
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("O processamento OCR foi interrompido.", excecao);
        }
    }

    private void validarConfiguracao() {
        if (propriedades.executavel() == null || propriedades.executavel().isBlank()
                || propriedades.idiomas() == null || propriedades.idiomas().isBlank()
                || propriedades.dpi() < 150 || propriedades.dpi() > 600
                || propriedades.maximoPaginas() < 1
                || propriedades.timeoutPorPagina() == null || propriedades.timeoutPorPagina().isNegative()
                || propriedades.timeoutPorPagina().isZero()) {
            throw new ServiceUnavailableException("A configuracao do OCR e invalida.");
        }
    }

    private Path criarDiretorioTemporario() {
        try {
            return Files.createTempDirectory("contextpilot-ocr-");
        } catch (IOException excecao) {
            throw new ServiceUnavailableException("Nao foi possivel criar a area temporaria do OCR.", excecao);
        }
    }

    private void removerDiretorio(Path diretorio) {
        try (var caminhos = Files.walk(diretorio)) {
            caminhos.sorted(Comparator.reverseOrder()).forEach(caminho -> {
                try {
                    Files.deleteIfExists(caminho);
                } catch (IOException ignorada) {
                    // A limpeza de temporarios nao deve esconder o resultado principal do OCR.
                }
            });
        } catch (IOException ignorada) {
            // O diretorio temporario sera limpo pelo sistema operacional.
        }
    }
}
