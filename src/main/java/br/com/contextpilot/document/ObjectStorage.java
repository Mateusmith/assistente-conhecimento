package br.com.contextpilot.document;

interface ObjectStorage {

    void armazenar(String chave, byte[] conteudo, String tipoMime, String hashSha256);

    byte[] obter(String chave);

    void remover(String chave);
}
