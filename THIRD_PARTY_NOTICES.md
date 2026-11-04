# Componentes e licenças

O código e o ícone vetorial deste projeto são distribuídos sob Apache-2.0. O arquivo LICENSE contém a licença; NOTICE identifica o projeto e o responsável.

Não há bibliotecas de execução de terceiros, serviços de autenticação ou SDKs de análise incluídos no APK. O Android e seu WebView são fornecidos pelo sistema e não são incorporados ao pacote de fontes.

Ferramentas de compilação:

- Gradle Wrapper e Gradle 8.11.1 — Apache-2.0; https://github.com/gradle/gradle
- Android Gradle Plugin 8.9.2 — Apache-2.0; https://android.googlesource.com/platform/tools/base/
- OpenJDK 17 — GPL-2.0 com Classpath Exception; ferramenta externa de compilação
- Android SDK Platform 36 e Build Tools 35.0.0 — ferramentas externas distribuídas conforme seus próprios termos

As ferramentas não são dependências de execução do app. O wrapper JAR é o bootstrap oficial do Gradle; sua distribuição é fixada por URL e checksum. Não inclui chave de assinatura, configuração privada de SDK ou credenciais.

As imagens PNG são derivadas do SVG fornecido com o projeto. A ferramenta opcional CairoSVG 2.9.1 é usada apenas para reconstruir essas imagens. Playwright e Google Chrome/Chromium são usados apenas pelos testes de interface; nenhum deles é incluído no APK ou necessário para executá-lo.
