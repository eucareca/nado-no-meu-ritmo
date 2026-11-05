# Envio ao F-Droid

Esta árvore contém o código completo da versão 1.10.0 (versionCode 11), sob Apache-2.0. Não utiliza servidor, conta, chave de API, SDK proprietário ou permissão de internet. O app abre o timer e o seletor de arquivos/compartilhamento por ação do usuário. O backup automático opcional usa um arquivo autorizado pelo seletor de documentos do Android, chave local não exportável pela WebCrypto e JobScheduler, sem SDK do Drive ou OAuth próprio. O Google Drive não é obrigatório: outros provedores compatíveis ou um arquivo local também podem ser escolhidos. Também pode ler dados locais do Health Connect em Android 14+, mediante permissões opcionais de leitura e confirmação dos registros a importar. Não adiciona SDK proprietário ou dependência de serviços Google para as demais funções.

## Repositório e publicação

Repositório público informado e verificado: https://github.com/eucareca/nado-no-meu-ritmo  
Issues: https://github.com/eucareca/nado-no-meu-ritmo/issues

A receita referencia a versão 1.10.0 (versionCode 11) na tag `v1.10.0`. A publicação automática cria essa tag somente depois de um build aprovado da branch principal. O pedido oficial foi enviado em 05/11/2026: https://gitlab.com/fdroid/rfp/-/work_items/4743 . Está aberto para análise; ainda não há aprovação, compilação ou publicação na loja confirmadas. O texto técnico de referência está em `docs/PEDIDO-F-DROID.md`. O site do responsável é https://tabinformatica.com.br/nadonoseuritmo.

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

No checkout de fdroiddata, valide a receita com `fdroid lint br.com.tabinformatica.nadonomeritmo` e `fdroid build --server br.com.tabinformatica.nadonomeritmo:11`. As ferramentas e a infraestrutura do F-Droid podem exigir ajustes de receita. Use uma merge request para adicionar a receita final e siga os comentários dos mantenedores.

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

A integração de calendário usa apenas o Calendar Provider do Android e permissões opcionais READ_CALENDAR/WRITE_CALENDAR. A sincronização da conta Google é externa ao diário; o usuário pode negar essa integração e usar as funções locais. Não foi adicionado SDK Google proprietário nem permissão de internet. A compatibilidade final continua dependendo da revisão do F-Droid.

A receita declara NonFreeNet e NonFreeAdd pelas integrações opcionais e sugestões de serviços/aplicativos proprietários. Isso não afirma a inclusão de bibliotecas proprietárias no APK; informa ao usuário sobre serviços externos promovidos. A classificação final depende dos mantenedores.

Preparação da versão 1.10.0: a receita foi normalizada com `fdroid rewritemeta` e passou em `fdroid lint --format` (fdroidserver 2.4.5, nomes de categorias/AntiFeatures do fdroiddata oficial). Isso valida os metadados localmente; a compilação no servidor do F-Droid e a revisão dos mantenedores continuam pendentes.
