# Politica de seguranca

Nao publique vulnerabilidades em issues abertas. Envie um aviso privado pelo recurso **Security > Report a vulnerability** do repositorio, incluindo impacto, pre-condicoes e uma reproducao minima.

Nao inclua documentos reais, tokens, senhas ou chaves no relatorio. O mantenedor confirmara o recebimento e coordenara correcao e divulgacao responsavel.

Versoes de demonstracao usam credenciais locais conhecidas. Elas nao sao adequadas para ambientes expostos ou compartilhados.

Em producao, ative o perfil `prod`, termine TLS no gateway confiavel e forneca segredos
por `/run/secrets`. A aplicacao interrompe o boot quando encontra configuracao local,
issuer HTTP ou encaminhamento inseguro. Swagger fica desabilitado no overlay. O benchmark em
`src/test/resources/adversarial/benchmark.json` e defesa em profundidade e deve ser
ampliado com casos sinteticos do dominio sem incluir informacao real.
