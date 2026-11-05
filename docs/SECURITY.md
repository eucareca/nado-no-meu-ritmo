# Segurança e limites

Relate problemas de forma privada a tiago@tabinformatica.com.br. Não inclua senhas ou registros reais em issues públicas.

A aplicação Android não declara INTERNET. O WebView só serve arquivos incluídos no APK, bloqueia navegação e recursos externos, desativa acesso a arquivos e conteúdo por URL e não expõe uma interface JavaScript nativa. A política de conteúdo impede conexões e scripts remotos.

Os fluxos nativos são limitados à interface empacotada e incluem timer, backup, fotos, agenda e importação de saúde autorizados. O documento é salvo pelo seletor de arquivos do Android; a importação usa o seletor do sistema. Não são necessárias permissões amplas de armazenamento.

O provedor de anexos não é exportado, aceita apenas nomes UUID de arquivos JSON na própria pasta de cache e permite somente leitura a aplicativos com permissão temporária do Android. Ele não permite gravar, remover ou consultar arquivos de outros diretórios.

A importação limita arquivos a 5 MB, valida datas, números, quantidades e textos e só substitui o banco depois da confirmação. O backup criptografado autentica o conteúdo com AES-GCM; senha incorreta, conteúdo alterado e parâmetros de derivação não reconhecidos são rejeitados. A derivação usa salt e nonce aleatórios, e a senha nunca é persistida.

Essas medidas não protegem contra sistema comprometido, acesso root, captura de tela, aplicativo de teclado malicioso, leitura em um aparelho desbloqueado ou divulgação voluntária do backup e da senha. Não houve auditoria independente de segurança. Não existe garantia absoluta de ausência de vulnerabilidades ou vazamentos. Os dados locais permanecem sem criptografia adicional em repouso.

O controle de exclusão é lógico; não promete sobrescrita segura de memória flash. Backups enviados por outro aplicativo não podem ser revogados por este app. O timer web depende do ciclo de vida do WebView; para aviso ao alternar apps ou bloquear a tela, utilize o timer nativo do Android.

O backup automático é opcional. Usa somente um arquivo novo escolhido pelo usuário e uma autorização persistente específica do Android. A chave AES não exportável pela WebCrypto é persistida no IndexedDB privado, com salt por ativação e nonce aleatório a cada cópia. Só envelopes criptografados limitados a 5 MB passam para a fila nativa. O serviço de fundo é protegido por BIND_JOB_SERVICE e não lê o diário remoto nem faz restaurações. Cópias locais são gravadas com AtomicFile; falhas ao escrever no provedor podem afetar o arquivo externo, e a cópia pendente é preservada para nova tentativa. O status confirma a entrega ao provedor, sem afirmar que ele concluiu o upload. A restauração e a mesclagem nunca são automáticas.
