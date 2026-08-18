package br.com.contextpilot.document;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import br.com.contextpilot.configuration.StorageProperties;
import br.com.contextpilot.shared.domain.ServiceUnavailableException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
class S3ObjectStorage implements ObjectStorage {

    private static final Logger logger = LoggerFactory.getLogger(S3ObjectStorage.class);

    private final S3Client cliente;
    private final StorageProperties propriedades;
    private final MeterRegistry metricas;

    S3ObjectStorage(S3Client cliente, StorageProperties propriedades, MeterRegistry metricas) {
        this.cliente = cliente;
        this.propriedades = propriedades;
        this.metricas = metricas;
    }

    @PostConstruct
    void prepararBucket() {
        try {
            cliente.headBucket(HeadBucketRequest.builder().bucket(propriedades.bucket()).build());
        } catch (S3Exception excecao) {
            if (excecao.statusCode() != 404 || !propriedades.criarBucket()) {
                throw indisponivel("Nao foi possivel acessar o bucket de documentos.", excecao);
            }
            try {
                cliente.createBucket(CreateBucketRequest.builder().bucket(propriedades.bucket()).build());
                logger.info("Bucket {} criado no armazenamento de objetos.", propriedades.bucket());
            } catch (RuntimeException causa) {
                throw indisponivel("Nao foi possivel criar o bucket de documentos.", causa);
            }
        } catch (RuntimeException excecao) {
            throw indisponivel("O armazenamento de objetos esta indisponivel.", excecao);
        }
    }

    @Override
    public void armazenar(String chave, byte[] conteudo, String tipoMime, String hashSha256) {
        try {
            cliente.putObject(PutObjectRequest.builder()
                            .bucket(propriedades.bucket())
                            .key(chave)
                            .contentType(tipoMime)
                            .contentLength((long) conteudo.length)
                            .metadata(java.util.Map.of("sha256", hashSha256))
                            .build(),
                    RequestBody.fromBytes(conteudo));
            metricas.counter("contextpilot.armazenamento.operacoes", "operacao", "gravar", "resultado", "sucesso")
                    .increment();
        } catch (RuntimeException excecao) {
            metricas.counter("contextpilot.armazenamento.operacoes", "operacao", "gravar", "resultado", "falha")
                    .increment();
            throw indisponivel("Nao foi possivel armazenar o documento.", excecao);
        }
    }

    @Override
    public byte[] obter(String chave) {
        try {
            var resposta = cliente.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(propriedades.bucket())
                            .key(chave)
                            .build());
            byte[] conteudo = resposta.asByteArray();
            String hashEsperado = resposta.response().metadata().get("sha256");
            if (hashEsperado == null || !hashEsperado.equalsIgnoreCase(calcularHash(conteudo))) {
                throw new IllegalStateException("O objeto nao corresponde ao hash SHA-256 registrado.");
            }
            metricas.counter("contextpilot.armazenamento.operacoes", "operacao", "ler", "resultado", "sucesso")
                    .increment();
            return conteudo;
        } catch (RuntimeException excecao) {
            metricas.counter("contextpilot.armazenamento.operacoes", "operacao", "ler", "resultado", "falha")
                    .increment();
            throw indisponivel("Nao foi possivel recuperar o documento armazenado.", excecao);
        }
    }

    @Override
    public void remover(String chave) {
        try {
            cliente.deleteObject(DeleteObjectRequest.builder()
                    .bucket(propriedades.bucket())
                    .key(chave)
                    .build());
            metricas.counter("contextpilot.armazenamento.operacoes", "operacao", "remover", "resultado", "sucesso")
                    .increment();
        } catch (RuntimeException excecao) {
            metricas.counter("contextpilot.armazenamento.operacoes", "operacao", "remover", "resultado", "falha")
                    .increment();
            throw indisponivel("Nao foi possivel remover o documento armazenado.", excecao);
        }
    }

    private ServiceUnavailableException indisponivel(String mensagem, Throwable causa) {
        return new ServiceUnavailableException(mensagem, causa);
    }

    private String calcularHash(byte[] conteudo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (NoSuchAlgorithmException excecao) {
            throw new IllegalStateException("SHA-256 nao esta disponivel.", excecao);
        }
    }
}
