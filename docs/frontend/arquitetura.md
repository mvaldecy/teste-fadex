# Arquitetura do frontend

Como a interface está organizada e as decisões que não se leem no código. O contrato consumido está
em [`../backend/api.md`](../backend/api.md).

## Organização

Next.js 15 com App Router. As rotas ficam em `app/`, e **todo o código de aplicação em `src/`** —
`app/` só monta a página e delega para uma feature. Isso mantém as rotas legíveis como índice do
sistema.

```
app/
  (auth)/login, (auth)/trocar-senha      telas sem sessão
  (dashboard)/home, dashboard, tickets,  telas com sessão, dentro do shell
             usuarios, admin/jobs
src/
  features/    tickets, users, indicators, ai-jobs, notifications, auth, choices
  components/  layout (shell, menu, paginação) e ui (primitivos Radix + Tailwind)
  services/    um arquivo por recurso da API, mais o cliente HTTP e o leitor de SSE
  stores/      sessão (Zustand com persist)
  routes/      caminhos e o saneamento do destino pós-login
  types/       tipos do contrato da API
```

Cada feature tem o mesmo formato: um `use-*.ts` com o estado e as chamadas, um `*-page.tsx` que
compõe, e componentes de apresentação sem estado próprio. Quem procura "onde isso acontece" acha
pelo nome da feature, não pelo tipo do arquivo.

## Sessão

Estado em Zustand com `persist` no `sessionStorage` — e não no `localStorage`, porque o token expira
em uma hora e sobreviver ao fechamento do navegador só aumenta a janela de exposição sem ganho.

O token de acesso vive **em memória** no cliente HTTP. Reidratar a store sem reidratar o cliente
deixaria a sessão válida na tela e ausente na API, então as duas coisas acontecem juntas na
reidratação.

`isHydrated` existe para as guardas de rota: sem ele, o primeiro render acontece antes de o
`sessionStorage` carregar e a guarda expulsaria o usuário a cada F5.

## Rotas e permissão

`AdminRouteGuard` protege as telas exclusivas de ADMIN — painel, usuários e fila de jobs. A guarda
espera a hidratação antes de decidir.

A regra geral da interface é **esconder o que a pessoa não pode fazer**, em vez de deixar o botão e
deixar a API recusar. Não é segurança — a autorização real está no backend —, é não oferecer um
caminho que termina em erro. O filtro de atribuição, por exemplo, não aparece para o SOLICITANTE:
ele só vê os chamados que abriu e nunca é responsável, então as opções responderiam todas a mesma
coisa.

O destino pretendido é preservado no login como `?redirect=`. Quem abre o link de um chamado vindo
do e-mail autentica e cai **no chamado**, não no painel. O valor é saneado: URL absoluta ou `//host`
é descartada, senão `?redirect=https://site-falso` levaria o usuário para fora logo depois de ele
digitar a senha.

## Tempo real

O `EventSource` nativo não envia cabeçalho `Authorization`, e o stream é autenticado. O cliente é
então `fetch` com `ReadableStream` e um parser de quadros SSE próprio (`sse-parser.ts`).

Receber o evento é metade do trabalho; a outra é **o usuário perceber**. Cada evento recarrega o que
mudou e dispara retorno visível: um aviso que nomeia o chamado, um destaque temporário na linha e um
selo com o horário da última atualização. Antes disso a tela recarregava em silêncio e parecia
quebrada.

Os avisos usam `id` por chamado: uma rajada de eventos do mesmo chamado substitui o próprio aviso em
vez de empilhar seis toasts iguais.

O alerta de prioridade `ALTA` mora no shell, não na listagem — ele precisa alcançar quem está em
qualquer tela — e **não expira sozinho**: some com um clique. Com duração automática ele
desaparecia antes de alguém notar, que é o oposto do requisito.

## Listagem e filtros

Os filtros aplicam sozinhos: os selects na hora, a busca depois de 400 ms de pausa na digitação.
Como as buscas saem em rajada e nada garante a ordem das respostas, o hook guarda a sequência da
última requisição e **descarta resposta antiga** — sem isso, a resposta de uma busca já abandonada
sobrescreveria a lista atual.

Trocar de página preserva os filtros; trocar de filtro volta para a primeira página, porque a página
3 herdada de outra consulta pode nem existir no novo resultado.

A prioridade é comunicada por cor com legenda visível, e nunca só por cor: o rótulo em texto está na
linha, para quem não distingue as cores.

## Configuração pública

Só as variáveis `NEXT_PUBLIC_*` chegam ao navegador, e há uma armadilha registrada em
`config/public-env.ts`: **o Next só substitui no bundle o acesso literal** `process.env.NEXT_PUBLIC_X`.
Uma referência solta a `process.env` some no empacotamento, a validação não encontra nada e o valor
padrão assume — o sintoma era o navegador chamar `localhost:8080` mesmo com outra URL configurada.
Por isso os acessos são declarados um a um, e a verificação é feita construindo com um valor falso e
procurando ele em `.next/static`.

Duas variáveis ligam recursos que só existem na demonstração e **somem sozinhas** quando não estão
definidas: o atalho para a caixa de e-mail e os atalhos de login das contas do seed.
