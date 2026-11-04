# Nado no meu ritmo

Diário de natação para Android. Planeje sessões, registre duração e esforço, acompanhe peso e atividades extras e use o timer do aparelho.

Os registros ficam somente no aparelho. O app não tem login, servidor, anúncios, rastreadores, música ou permissão de internet. Nas configurações, é possível salvar um backup, importar registros e preparar um anexo para enviar a si mesmo pelo aplicativo de e-mail escolhido. Nenhum e-mail é enviado automaticamente.

## Backup

A proteção por senha vem ativada. Os arquivos protegidos usam AES-256-GCM, com chave de 256 bits derivada por PBKDF2-SHA256 (310.000 iterações), salt aleatório de 16 bytes e nonce de 12 bytes. A senha deve ter ao menos 12 caracteres e não é armazenada. Guarde-a separadamente: não existe recuperação de senha.

O backup sem senha contém dados legíveis. O app de e-mail recebe o arquivo que você escolheu compartilhar; revise o destinatário antes de enviar. Não envie a senha junto com o anexo. Desinstalar o app apaga os dados locais. Faça backups antes de trocar de aparelho.

## Compilação

Requisitos: OpenJDK 17, Android SDK Platform 36, Build Tools 35.0.0 e Python 3. A versão do Gradle é fixada em 8.11.1 e o Android Gradle Plugin em 8.9.2. O wrapper verifica o SHA-256 da distribuição.

```sh
export JAVA_HOME=/caminho/para/jdk-17
export ANDROID_HOME=/caminho/para/android-sdk
./scripts/sync-android-web.sh
cd android
./gradlew --no-daemon assembleRelease lintRelease
```

O APK sem assinatura fica em `android/app/build/outputs/apk/release/app-release-unsigned.apk`. Para um APK de teste, use `./gradlew assembleDebug`. O F-Droid compila e assina a própria distribuição. Não inclua chaves de assinatura no repositório.

O primeiro build precisa baixar ferramentas e dependências de compilação. O aplicativo compilado funciona sem conexão. Não há dependências de execução de terceiros: a interface usa o WebView fornecido pelo sistema e APIs Android.

## Verificação

```sh
python3 scripts/audit-source.py
python3 -m venv .venv
.venv/bin/pip install -r tests/requirements.txt
.venv/bin/python tests/test_local.py
```

Os testes usam somente dados fictícios e bloqueiam requisições externas. Leia `docs/PRIVACY.md`, `docs/SECURITY.md` e `docs/F-DROID.md` para os limites, permissões e passos de submissão.

## Licença e suporte

Apache-2.0. Consulte LICENSE e NOTICE. O ícone vetorial está em `icon.svg`; as versões PNG podem ser reconstruídas com `scripts/render-icons.py`.

Tiago Assis Brasil · tiago@tabinformatica.com.br  
https://tabinformatica.com.br/nadonoseuritmo

Repositório público: https://github.com/eucareca/nado-no-meu-ritmo  
Problemas e sugestões: https://github.com/eucareca/nado-no-meu-ritmo/issues
