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

## Xiaomi e Samsung (importação opcional)

Em **Relógios**, consulte treinos de natação compartilhados pelo Mi Fitness ou Samsung Health com o Health Connect. Esta primeira implementação exige **Android 14+** com Health Connect disponível; o restante do diário continua funcionando em Android 6+.

Autorize a leitura, revise os últimos 28 dias e marque os treinos a importar. Exercícios são necessários; distância e frequência cardíaca média são opcionais. Nenhuma sessão é salva sem seleção, e registros já importados são reconhecidos pelo identificador de origem ou horários idênticos. A importação preserva a origem no histórico e no backup. Os dados não são escritos de volta no Health Connect. Não há conexão direta com o relógio nem envio de planos para ele. Métricas dependem do que o aplicativo de origem compartilhar.

A duração importada corresponde ao intervalo início/fim, arredondado em minutos, e pode incluir pausas. A compatibilidade com dados reais de cada modelo ainda precisa ser validada em aparelho. Veja [privacidade](docs/PRIVACY.md).

## Dias, agenda e frequência

Em **Dias de natação e frequência**, escolha os dias, horário, duração e antecedência do aviso. Salvar o plano não ativa notificações nem cria eventos externos. **Ativar lembretes** solicita permissão no Android 13+ e programa avisos locais. **Testar notificação**, **Consultar lembretes** e **Desativar lembretes** permitem conferir e controlar os avisos. O Android pode atrasar alarmes por economia de bateria; não há promessa de precisão de minuto. O aplicativo tenta reprogramar os avisos após reiniciar o aparelho, mudar o fuso/horário ou atualizar o pacote. Forçar a parada do aplicativo pode impedir avisos até que ele seja aberto novamente.

**Conectar Agenda Google** solicita acesso ao calendário no Android e permite escolher uma agenda Google editável já cadastrada no aparelho. Ao selecionar a agenda, o diário cria e vincula uma série semanal própria. Dias, horário e duração dessa série são sincronizados ao abrir o diário ou tocar em **Sincronizar agora**. Alterações no diário atualizam a série vinculada; alterações da série no Google, quando recebidas pelo calendário do Android, viram uma nova revisão no diário. Se os dois lados mudarem, o usuário escolhe qual manter. Os alertas da agenda e os lembretes locais são independentes.

A sincronização usa o Calendar Provider do Android e a sincronização da conta Google configurada no aparelho, sem OAuth próprio, backend, SDK adicional ou custo de serviço do diário. Não é instantânea e não roda em segundo plano no diário. Excluir/cancelar a série na agenda pausa o planejamento e desativa lembretes locais na próxima sincronização. Desconectar, apagar o diário ou restaurar um backup remove o vínculo local sem apagar eventos externos. A conexão não entra no backup; reconectar cria uma nova série. A primeira versão aceita apenas séries semanais simples com dias definidos, duração em minutos, fuso equivalente ao aparelho e sem exceções ou data de término. Padrões não suportados são informados sem sobrescrever o evento.

O botão alternativo **Adicionar evento sem sincronizar** só abre um cadastro na agenda para confirmação, sem conceder acesso ao Calendar Provider. No navegador, o planejamento pode ser baixado como `.ics`. Esses eventos independentes não são reconhecidos pela conexão automática e podem duplicar uma série já existente.

O painel mostra dias previstos na semana, dias com registro e dias passados ainda sem registro. Uma sessão registrada/importada confirma somente a data informada: não comprova presença na aula agendada. Mais de uma sessão no mesmo dia conta como um dia de frequência. Dias extras aparecem separadamente. Mudanças no plano passam a valer no momento de salvar e preservam as revisões anteriores. O backup inclui esse histórico; restaurar um backup mantém os lembretes desativados até nova ativação no aparelho.

## Tema e fotos para redes sociais

No seletor **Tema**, escolha claro, escuro ou automático. A preferência é salva neste aparelho; automático acompanha o tema do sistema, inclusive no Android. Não faz parte do backup do diário.

Em **Foto para compartilhar**, escolha uma imagem do aparelho, selecione feed 4:5 (1080×1350), quadrado (1080×1080) ou stories 9:16 (1080×1920), moldura clara/escura e texto opcional. A composição preserva o enquadramento completo e aplica a logo existente do aplicativo. O PNG é criado localmente, sem alterar o original ou incluir dados do diário automaticamente.

No Android, **Salvar foto com logo** abre o seletor de destino do sistema; **Compartilhar nas redes** abre o compartilhamento para o usuário escolher o aplicativo e confirmar a publicação. Não exige permissão de acesso amplo à galeria nem câmera. No navegador, salva por download e usa o compartilhamento de arquivos quando suportado, com download como alternativa.

A imagem escolhida permanece apenas na memória do editor, que é limpa ao fechar. Fotos não entram nos backups do diário. O Android prepara um PNG temporário no cache privado para salvar/compartilhar; arquivos temporários de compartilhamento com mais de 24 horas são removidos na próxima exportação. Salvar ou publicar cria uma cópia externa, administrada pelo destino escolhido. Limites: fonte de até 25 MB e 60 megapixels, exportação de até 12 MB; JPEG, PNG e WebP são recomendados. Outros formatos dependem do decodificador do WebView/navegador.

## Atualização pelo celular

A rotina de compilação no GitHub Actions disponibiliza um APK de teste que instala ao lado do diário principal e um release sem assinatura. Para atualizar o diário principal, é necessária a chave original. Veja [como baixar, testar e preparar a atualização](docs/ATUALIZACAO-ANDROID.md).

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
.venv/bin/python tests/test_health_import.py
.venv/bin/python tests/test_training_planner.py
.venv/bin/python tests/test_calendar_sync.py
.venv/bin/python tests/test_appearance_photo.py
```

Os testes usam somente dados fictícios e bloqueiam requisições externas. Leia `docs/PRIVACY.md`, `docs/SECURITY.md` e `docs/F-DROID.md` para os limites, permissões e passos de submissão.

## Licença e suporte

Apache-2.0. Consulte LICENSE e NOTICE. O ícone vetorial está em `icon.svg`; as versões PNG podem ser reconstruídas com `scripts/render-icons.py`.

Tiago Assis Brasil · tiago@tabinformatica.com.br  
https://tabinformatica.com.br/nadonoseuritmo

Repositório público: https://github.com/eucareca/nado-no-meu-ritmo  
Problemas e sugestões: https://github.com/eucareca/nado-no-meu-ritmo/issues
