# Política de privacidade

Aplicativo: Nado no meu ritmo, versão 1.6.0 em preparação.
Responsável: Tiago Assis Brasil.
Contato: tiago@tabinformatica.com.br
Site: https://tabinformatica.com.br/nadonoseuritmo

## Armazenamento local

O aplicativo registra as informações fornecidas por você e as sessões cuja importação você autorizar: plano de natação, datas, duração, esforço, observações, peso e atividades extras. O e-mail para backup é guardado localmente se informado. O Android armazena esses dados na área privada do aplicativo.

Não há conta, sincronização, servidor, rastreamento, anúncios ou acesso à internet pelo aplicativo. Ele não pede acesso a contatos, localização, câmera ou microfone. A permissão SET_ALARM serve para abrir o timer do Android por sua solicitação.

Os dados locais não têm criptografia adicional implementada pelo app. Dependem das proteções do Android, do bloqueio de tela e da integridade do aparelho. As configurações do app desativam o backup automático do sistema e a transferência automática de dados entre aparelhos.

## Importação opcional de treinos

No Android 14 ou superior, a função Relógios consulta o Health Connect local, se disponível. As permissões são READ_EXERCISE (necessária), READ_DISTANCE e READ_HEART_RATE (opcionais). A consulta de exercícios lê os últimos 28 dias e exibe somente natação. A distância e a frequência cardíaca média são agregadas pelo horário e aplicativo de origem da sessão; ausência de dados não é convertida em zero ou estimada. A duração exibida é o intervalo entre início e fim, arredondado em minutos; não é o tempo ativo calculado pelo fabricante.

A consulta ocorre por ação do usuário, com o app aberto. Não há leitura em segundo plano, escrita ou exclusão no Health Connect, acesso Bluetooth direto, envio de planos ao relógio ou envio de dados para servidores pelo diário. O Health Connect precisa receber os dados do Mi Fitness, Samsung Health ou outro aplicativo autorizado. As contas, o compartilhamento e a eventual nuvem desses aplicativos são regidos pelas políticas deles.

Você revisa e marca quais treinos guardar. As cópias salvas incluem o aplicativo e o identificador do registro de origem, horários e métricas disponíveis. Esses campos permitem reconhecer importações repetidas e entram no backup manual. Revogar o acesso nas configurações do Health Connect impede novas consultas, mas não apaga cópias já importadas. Excluir uma sessão ou apagar o diário remove apenas os registros locais; não altera as origens nem backups já exportados.

Esta primeira implementação consulta no máximo 2.000 registros de exercício e apresenta até 100 sessões de natação por busca. A tela informa quando o limite é atingido. Dados temporários da consulta são descartados ao fechar a janela ou reiniciar o aplicativo. Nenhum dado real de saúde deve ser incluído em logs, capturas, testes ou no repositório.

## Backups manuais

Você escolhe quando salvar ou compartilhar um arquivo. Por padrão, o backup usa uma senha escolhida por você e criptografia AES-256-GCM. A senha não fica salva, não é incluída no arquivo e não pode ser recuperada. Um backup sem proteção contém registros em texto legível.

Ao solicitar envio por e-mail, o Android apresenta aplicativos de compartilhamento. O app selecionado recebe o arquivo e o destinatário informado. Você revisa e confirma o envio nesse aplicativo. O Nado no meu ritmo não envia mensagens sozinho e não controla as práticas de privacidade do provedor de e-mail, do gerenciador de arquivos ou de outros aplicativos escolhidos por você.

Um anexo temporário fica no cache privado para viabilizar o compartilhamento. Seu acesso é somente de leitura e depende de uma autorização temporária do Android ao destinatário selecionado. Arquivos com mais de 24 horas são removidos ao preparar outro compartilhamento; o Android também pode limpar o cache. Isso não remove cópias já salvas ou enviadas.

## Exclusão e migração

Use Configurações → Apagar todos os registros deste aparelho para apagar o banco local e o e-mail de backup. Desinstalar ou limpar os dados do aplicativo também apaga o armazenamento local. Backups exportados, anexos e mensagens enviados precisam ser apagados separadamente nos aplicativos e locais em que foram guardados.

Na atualização de versões antigas, registros anteriormente armazenados no próprio aparelho podem ser migrados para o banco local. Credenciais locais antigas são descartadas. Nenhum dado é enviado a servidores durante essa migração. Dados que tenham sido enviados por versões anteriores continuam sujeitos ao serviço usado anteriormente; esta versão não os consulta nem os apaga.

O responsável pelo projeto não recebe seus registros pelo aplicativo. Caso você envie voluntariamente informações ao suporte, use apenas o necessário para explicar o problema e evite anexar dados sensíveis.
