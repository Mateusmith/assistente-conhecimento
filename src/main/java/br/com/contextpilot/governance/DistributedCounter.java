package br.com.contextpilot.governance;

import java.time.Duration;

interface DistributedCounter {

    ResultadoContador incrementar(String chave, long valorInicial, Duration validade);

    record ResultadoContador(long valor, Duration validadeRestante) {
    }
}
