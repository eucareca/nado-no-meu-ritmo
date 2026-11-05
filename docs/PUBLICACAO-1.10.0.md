# Publicação 1.10.0 — 05/11/2026

- Código integrado à main: commit e4caef948eed9d3ac5dec964191d893a40e1887b, PR #1 mesclado.
- Tag v1.10.0 aponta para esse commit e foi preservada após a publicação.
- Release pública: https://github.com/eucareca/nado-no-meu-ritmo/releases/tag/v1.10.0
- Testes, debug/release, lintRelease e assinatura de teste aprovados: https://github.com/eucareca/nado-no-meu-ritmo/actions/runs/42119652834
- Publicação automática aprovada: https://github.com/eucareca/nado-no-meu-ritmo/actions/runs/42119951371
- Fonte ZIP/TAR.GZ, receita do F-Droid, pedido técnico, manifestos SHA-256 e APK de produção sem assinatura publicados como assets.
- Receita normalizada e validada localmente com fdroidserver 2.4.5; compilação e revisão pela infraestrutura do F-Droid pendentes.
- Pedido oficial ao F-Droid criado por eucareca e confirmado aberto: https://gitlab.com/fdroid/rfp/-/work_items/4743

## Pendências

O F-Droid recebeu uma solicitação de empacotamento (RFP), não uma aprovação. A inclusão na loja e a assinatura da distribuição por eles ainda dependem de revisão e compilação. Não há prazo de aprovação prometido.

A release do GitHub publica o código-fonte e o APK **sem assinatura**. Não há APK instalável de produção assinado nesta release. Para atualizar o aplicativo principal instalado, é necessário usar a chave original de assinatura, fora do repositório. Nenhuma chave nova foi gerada e nenhuma credencial foi publicada. A chave e suas senhas não devem ser enviadas por chat ou issues. O APK .preview de teste continua separado do principal e não é tratado como produção.

Integrações com conta real, Drive e dispositivos continuam exigindo validação física.
