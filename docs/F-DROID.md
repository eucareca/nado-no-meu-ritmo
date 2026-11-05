# Envio ao F-Droid

Esta árvore contém o código completo da versão local 1.5.0 em preparação (versionCode 6), sob Apache-2.0. Não utiliza servidor, conta, chave de API, SDK proprietário ou permissão de internet. O único acesso externo solicitado pelo app é abrir o timer do sistema ou o seletor de arquivos/compartilhamento por ação do usuário.

## Repositório e publicação

Repositório público informado e verificado: https://github.com/eucareca/nado-no-meu-ritmo  
Issues: https://github.com/eucareca/nado-no-meu-ritmo/issues

O fonte da versão 1.4.0 e a tag `v1.4.0` estão publicados. A receita incluída continua apontando para essa versão já existente. A versão 1.5.0 só deve entrar na receita após validação e criação da sua tag. O envio ao F-Droid ainda está pendente. O site do responsável é https://tabinformatica.com.br/nadonoseuritmo.

O F-Droid exige fonte público e compila a versão referenciada pela receita; um repositório vazio, um ZIP ou um APK isolado não atende esse requisito. A inclusão depende da revisão dos mantenedores.

## Metadados da loja

`fastlane/metadata/android/pt-BR/` e `en-US/` contêm título, resumo, descrição, ícone, changelog e capturas de tela. As capturas usam exclusivamente registros fictícios de teste. Confira o conteúdo antes de publicar.

`fdroid/br.com.tabinformatica.nadonomeritmo.yml` é a receita preenchida com o repositório acima. Copie esse arquivo para `metadata/` no checkout de fdroiddata. O arquivo `.yml.in` é apenas o modelo; para regenerar a receita:

```sh
python3 scripts/fdroid-metadata.py \
  --repo-url https://github.com/eucareca/nado-no-meu-ritmo.git \
  --issues-url https://github.com/eucareca/nado-no-meu-ritmo/issues \
  --output metadata/br.com.tabinformatica.nadonomeritmo.yml
```

No checkout de fdroiddata, valide a receita com `fdroid lint br.com.tabinformatica.nadonomeritmo` e `fdroid build --server br.com.tabinformatica.nadonomeritmo:5`. As ferramentas e a infraestrutura do F-Droid podem exigir ajustes de receita. Use uma merge request para adicionar a receita final e siga os comentários dos mantenedores.

## Compilação e assinatura

O build usa OpenJDK 17, Gradle 8.11.1, AGP 8.9.2, SDK Platform 36 e Build Tools 35.0.0. A receita sincroniza os arquivos web antes de compilar `android/`. Não há etapa que baixe código de execução para o aplicativo.

O Gradle Wrapper oficial e seu checksum são incluídos. `./gradlew assembleRelease` gera um APK sem assinatura. O F-Droid assina a sua distribuição; esse APK não é uma atualização instalável sobre builds assinados com outra chave. O APK debug validado localmente é separado da árvore de fontes.

A compilação em ambiente local não equivale a validação na infraestrutura do F-Droid nem comprova identidade bit a bit entre toolchains diferentes. Uma comparação local de builds só cobre o ambiente em que foi executada.

## Conferência antes da publicação

- Confirme a titularidade do domínio e do applicationId `br.com.tabinformatica.nadonomeritmo`.
- Publique a política de privacidade e mantenha o contato `tiago@tabinformatica.com.br`.
- Publique os arquivos de fonte selecionados, incluindo LICENSE, NOTICE, o SVG e o wrapper verificado; não publique caches, keystores, arquivos pessoais ou APKs de teste dentro da árvore.
- Confira a licença dos recursos e a descrição do produto. Não alegue auditoria independente ou garantias de segurança que não foram obtidas.
- Crie a tag de versão após conferir o commit e gere a receita com as URLs verdadeiras.

Referências oficiais:

- https://f-droid.org/en/docs/Inclusion_Policy/
- https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- https://f-droid.org/en/docs/Build_Metadata_Reference/
