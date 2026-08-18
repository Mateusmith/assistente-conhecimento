package br.com.contextpilot.configuration;

import java.net.URI;

import br.com.contextpilot.shared.domain.ServiceUnavailableException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration(proxyBeanMethods = false)
class ObjectStorageConfig {

    @Bean(destroyMethod = "close")
    S3Client s3Client(StorageProperties propriedades) {
        validar(propriedades);
        return S3Client.builder()
                .endpointOverride(URI.create(propriedades.endpoint()))
                .region(Region.of(propriedades.regiao()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        propriedades.chaveAcesso(), propriedades.chaveSecreta())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(propriedades.acessoPorCaminho())
                        .build())
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(propriedades.timeout())
                        .apiCallAttemptTimeout(propriedades.timeout())
                        .build())
                .build();
    }

    private void validar(StorageProperties propriedades) {
        if (propriedades.endpoint() == null || propriedades.endpoint().isBlank()
                || propriedades.regiao() == null || propriedades.regiao().isBlank()
                || propriedades.chaveAcesso() == null || propriedades.chaveAcesso().isBlank()
                || propriedades.chaveSecreta() == null || propriedades.chaveSecreta().isBlank()
                || propriedades.bucket() == null || propriedades.bucket().isBlank()
                || propriedades.timeout() == null || propriedades.timeout().isNegative()
                || propriedades.timeout().isZero()) {
            throw new ServiceUnavailableException("A configuracao do armazenamento de objetos esta incompleta.");
        }
    }
}
