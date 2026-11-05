# Instalação e atualização sem cabo USB

A versão 1.10.0 será publicada na branch principal e na tag v1.10.0 após as verificações automáticas. A rotina `Compilar APK Android` compila e verifica os APKs no GitHub Actions, em cada push para `main` ou `feat/treinos-guiados`, em pull requests para `main` e por execução manual após a rotina estar na branch padrão.

## Baixar e experimentar

1. Abra https://github.com/eucareca/nado-no-meu-ritmo/actions pelo navegador e entre na sua conta do GitHub.
2. Abra a execução mais recente com sucesso de **Compilar APK Android**.
3. Na seção **Artifacts**, baixe `nado-apks-N` e extraia o ZIP no celular.
4. Abra **nado-teste.apk**. Se o Android solicitar, permita a instalação para o navegador ou gerenciador de arquivos usado.
5. O aplicativo **Nado no meu ritmo (teste)** é instalado separadamente. Os registros do diário principal permanecem no aplicativo principal. Para experimentar com uma cópia, exporte um backup no principal e importe no teste.

O APK de teste usa o identificador `br.com.tabinformatica.nadonomeritmo.preview` e uma chave de depuração descartável. Não é uma atualização do diário principal. APKs de testes gerados em execuções diferentes podem ter certificados diferentes. Nesse caso, faça backup dos dados do aplicativo de teste antes de removê-lo; o diário principal não precisa ser removido.

O pacote fica disponível por 30 dias. A rotina não publica automaticamente uma release.

## Atualizar o diário principal

Para instalar uma atualização por cima e preservar os registros, é necessário manter o identificador `br.com.tabinformatica.nadonomeritmo`, usar um versionCode maior e assinar com a mesma chave da instalação existente. A versão 1.10.0 usa versionCode 11; a versão 1.4.0 usa 5.

**nado-release-unsigned.apk não pode ser instalado diretamente.** Ele deve ser assinado com a chave original, fora do repositório. Não envie a chave nem a senha por chat, issues ou commits. Identifique a chave no computador que gerou o APK instalado. Se a versão instalada for de depuração, a chave geralmente está em `~/.android/debug.keystore` no computador de origem. Se for release, use o keystore escolhido no build original. Não gere uma chave nova para tentar atualizar uma instalação existente.

Com Android Build Tools 35.0.0 instalado, defina `ANDROID_HOME` para o SDK. Exemplo local, com prompts de senha do apksigner:

```sh
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -p -f 4 nado-release-unsigned.apk nado-aligned.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" sign --ks /caminho/da/chave-original.keystore --out nado-atualizacao.apk nado-aligned.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs nado-atualizacao.apk
```

Antes da primeira atualização, exporte um backup no diário instalado. Se o Android recusar a atualização por assinatura incompatível, não desinstale o aplicativo principal para contornar o erro. Confira o certificado e use a chave correta.

Depois de validar a assinatura e testar o APK em um aparelho, o APK assinado pode ser anexado a uma release no GitHub para download direto pelo celular. O F-Droid usa sua própria assinatura; atualizações devem seguir o mesmo canal de distribuição.

## Compilar no computador

Requisitos: Java 17, Android SDK Platform 36 e Build Tools 35.0.0. Depois de definir `ANDROID_HOME`, execute:

```sh
./scripts/build-apk.sh
```

A rotina sincroniza os arquivos web, audita a fonte, compila os dois APKs, executa lintRelease e gera os hashes SHA-256 em `artifacts/apk/`. O parâmetro `-PnadoPreview=true` muda apenas o identificador e o nome do build debug; o release conserva o identificador principal e continua sem assinatura. O fluxo não armazena chaves privadas nem usa segredos do GitHub.

Referências: https://developer.android.com/studio/publish/app-signing e https://developer.android.com/tools/apksigner
