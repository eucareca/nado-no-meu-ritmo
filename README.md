# Nado no meu ritmo

Diário de natação para Android. Planeje sessões, registre duração e esforço, acompanhe peso e atividades extras e use o timer do aparelho.

Os registros ficam somente no aparelho. O app não tem login, servidor, anúncios, rastreadores, música ou permissão de internet. Nas configurações, é possível salvar um backup, importar registros e preparar um anexo para enviar a si mesmo pelo aplicativo de e-mail escolhido. Nenhum e-mail é enviado automaticamente.

## Treinos estruturados

Em **Modelos de treino**, escolha um ponto de partida e personalize os blocos antes de salvar. Você também pode editar o plano atual. Os modelos são conteúdo original do projeto.

- Blocos simples usam duração em minutos e detalhes livres (estilo, distância ou observações).
- Séries usam repetições, segundos de nado e segundos de pausa. Não há pausa depois da última repetição.
- **Iniciar treino guiado** acompanha cada etapa, com pausa/continuação e aviso sonoro nas transições. O treino usa uma cópia do plano ao iniciar.
- Ao concluir, **Registrar este treino** preenche a sessão para revisão. Nada é salvo no histórico sem sua confirmação.

Mantenha o aplicativo aberto e a tela ligada durante a execução. O sistema pode suspender avisos com a tela bloqueada. Ao voltar ao aplicativo, a contagem é atualizada pelo tempo transcorrido. Fechar ou recarregar o app encerra a execução; o plano salvo permanece. Encerrar manualmente descarta a execução. Os modelos são exemplos editáveis, sem prescrição automática.

Os backups incluem os campos das séries; backups anteriores continuam aceitos. O editor preserva detalhes e mudanças ao adicionar ou remover blocos, e cancelar não altera o plano salvo.

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
.venv/bin/python tests/test_workouts.py
```

Os testes usam somente dados fictícios e bloqueiam requisições externas. Leia `docs/PRIVACY.md`, `docs/SECURITY.md` e `docs/F-DROID.md` para os limites, permissões e passos de submissão.

## Licença e suporte

Apache-2.0. Consulte LICENSE e NOTICE. O ícone vetorial está em `icon.svg`; as versões PNG podem ser reconstruídas com `scripts/render-icons.py`.

Tiago Assis Brasil · tiago@tabinformatica.com.br  
https://tabinformatica.com.br/nadonoseuritmo

Repositório público: https://github.com/eucareca/nado-no-meu-ritmo  
Problemas e sugestões: https://github.com/eucareca/nado-no-meu-ritmo/issues
