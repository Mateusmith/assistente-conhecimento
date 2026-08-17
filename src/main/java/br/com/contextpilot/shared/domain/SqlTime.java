package br.com.contextpilot.shared.domain;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class SqlTime {

    private SqlTime() {
    }

    public static OffsetDateTime instante(Instant valor) {
        return OffsetDateTime.ofInstant(valor, ZoneOffset.UTC);
    }
}
