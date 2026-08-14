# Diretrizes do Backend

## Estrutura

O backend usa Java 21 e Spring Boot. Mantenha pacotes sob `br.org.fadex.helpdesk`.

Use camadas por responsabilidade:

- `controller`: endpoints REST.
- `service`: regras de aplicação e orquestração.
- `repository`: acesso a dados.
- `model`: entidades, DTOs, mappers, filtros, fields e enums.
- `security`: autenticação, autorização e JWT.
- `config`: configurações Spring.
- `exception`: exceções e handler global.

Cada subdomínio deve concentrar entidade e DTOs dentro de `model`, por exemplo `model/ticket`, `model/user` e `model/comment`. Enums ficam em `model/enums`.

## Controllers

Controllers devem retornar `ResponseEntity` para manter controle explícito de status e corpo.

Listagens devem nascer com paginação e filtros dinâmicos. O padrão é tamanho 10 e ordenação decrescente por `createdAt`, salvo necessidade específica do fluxo.

## DTOs e Mappers

Use o padrão `NomeCreationDto`, `NomeDto` e `NomeMinDto`.

DTOs de resposta devem representar agregados com DTOs mínimos, como `UserMinDto`, em vez de expor ids soltos de relacionamento. Ids de relacionamento podem existir em filtros quando fizer sentido para busca ou dropdown.

Conversões devem ficar em mappers do próprio subdomínio, com métodos como `toResponseDto`, `toMinDto` e `toEntity`.

## Services

Services devem manter a lógica em variáveis intermediárias, evitando concentrar criação de specification, chamada de repository e mapping diretamente no `return`.

Entidades não devem concentrar regra de negócio.

## Filtros e Specifications

Filtros devem ter métodos `hasCampo` para cada campo opcional.

Specifications devem ficar em classe própria, com método `createSpecification`, adicionando predicates apenas quando o filtro tiver valor.

Use classes `Fields`, como `TicketFields`, para evitar strings soltas em criteria queries.

## Enums e Choices

Enums devem expor label quando forem apresentados ao frontend.

O frontend deve consumir choices por endpoint próprio, sem replicar regra ou label de enum.

## Erros

Erros devem passar pelo `GlobalExceptionHandler` e retornar a estrutura padrão de erro da API.

Novas exceções de negócio devem estender a base da aplicação e usar `HttpStatusCode`/status explícito quando aplicável.

## Testes

Os testes usam JUnit Platform via Gradle. Coloque testes no pacote correspondente em `backend/src/test/java` e nomeie como `*Test.java`.

Prefira testes focados em serviços, controllers, configuração e persistência para comportamentos alterados.
