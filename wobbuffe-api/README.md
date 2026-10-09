# Wobbuffe API (Plano 2)

API demonstrativa em Java 25 e Spring Boot 3.5.16. A aplicação mantém Pokémon e estados de operações em H2 em memória, consulta a PokéAPI por um client HTTP resiliente e pode encaminhar imports elegíveis para a fila SQS do LocalStack.

O frontend da Pokédex é servido pela própria API em `http://localhost:8080/`. Ele oferece cadastro, edição, remoção e busca de Pokémon, importação da PokéAPI e consulta por ID de operação.

## Executar localmente

Clone `wobbuffe-starter` ao lado de `wobbuffe-demo` conforme o README da raiz. Para executar a API fora do Compose a partir do diretório pai que contém ambos:

```bash
cd wobbuffe-starter
./mvnw -B verify install
cd ../wobbuffe-demo/wobbuffe-api
./mvnw -B verify
./mvnw spring-boot:run
```

Por padrão, o encaminhamento está desativado e a API usa a PokéAPI pública. `POKEAPI_BASE_URL` permite apontar para outro servidor compatível. Não há dependência de acesso à PokéAPI para executar os testes.

Para subir API, LocalStack, fila e DLQ:

```bash
cp infra/.env.example infra/.env
docker compose -f infra/compose.yaml up --build -d
docker compose -f infra/compose.yaml ps
```

O Compose configura o publisher SQS, habilita o roteamento e consulta a PokéAPI pública real (`https://pokeapi.co/api/v2`). Os testes automatizados usam WireMock isoladamente, sem substituir o upstream desta execução local. Credenciais `test/test` servem apenas para o LocalStack. O endpoint SQS interno usa o nome de serviço `localstack`; a porta 4566 é publicada somente em `127.0.0.1`.

## API

| Método e rota | Resultado |
| --- | --- |
| `POST /api/pokemon` | Cria Pokémon (`201`) |
| `GET /api/pokemon` | Lista Pokémon (`200`) |
| `GET /api/pokemon/{id}` | Consulta por ID (`200`, `404`) |
| `PUT /api/pokemon/{id}` | Atualiza (`200`, `404`) |
| `DELETE /api/pokemon/{id}` | Exclui (`204`, `404`) |
| `GET /api/integrations/pokeapi` | Consulta se a integração está ligada (`200`) |
| `PUT /api/integrations/pokeapi` | Liga/desliga a integração com `{"enabled":true/false}` (`200`) |
| `POST /api/pokemon/import/{name}` | Importa de forma síncrona ou aceita para fila (`201`/`200`/`202`) |
| `GET /api/operations/{operationId}` | Consulta o registro local da operação (`200`, `404`) |
| `GET /actuator/health` | Healthcheck da aplicação |

Exemplo de CRUD:

```bash
curl -i -X POST http://localhost:8080/api/pokemon \
  -H 'Content-Type: application/json' \
  -d '{"name":"eevee","pokedexId":133,"height":3,"weight":65}'
curl http://localhost:8080/api/pokemon
```

Controle a integração durante uma demonstração:

```bash
curl http://localhost:8080/api/integrations/pokeapi
curl -X PUT http://localhost:8080/api/integrations/pokeapi \
  -H 'Content-Type: application/json' -d '{"enabled":false}'
curl -i -X POST http://localhost:8080/api/pokemon/import/pikachu
curl -X PUT http://localhost:8080/api/integrations/pokeapi \
  -H 'Content-Type: application/json' -d '{"enabled":true}'
```

O botão de simulação representa instabilidade no caminho síncrono da integração: novos imports elegíveis são enviados diretamente à fila e respondem `202` depois da confirmação do SQS; o consumer do starter processa a mensagem e consulta a PokéAPI real no próprio processo da API. Assim, é possível controlar a demonstração sem substituir nem causar falhas artificiais na API pública. Se publisher/store não estiverem configurados ou a publicação falhar, a API responde `503` e não informa aceitação. O estado do controle é mantido em memória e volta ao caminho direto quando a API reinicia. Este controle é destinado à demonstração local e, como a API demo, não possui autenticação própria.

Exemplo de import com dados da PokéAPI real (normalmente síncrono com o breaker fechado):

```bash
curl -i -X POST http://localhost:8080/api/pokemon/import/natu \
  -H 'Idempotency-Key: demo-natu-001' \
  -H 'X-Correlation-Id: demo-natu-correlation'
```

Quando a degradação real abrir o breaker, uma operação elegível pode responder `202`; isso não é garantido em chamadas saudáveis. O `202` só é emitido depois que o store reserva a chave idempotente, o publisher confirma o envio à fila e o registro local muda para `QUEUED`. Uma chave repetida retorna `409`; falha no store ou na publicação não retorna `202`. O payload SQS é versionado e mínimo (`name`), sem dados sensíveis. A entrega SQS é ao menos uma vez.

Confira as filas e o redrive:

```bash
aws --endpoint-url=http://localhost:4566 sqs list-queues
aws --endpoint-url=http://localhost:4566 sqs get-queue-attributes \
  --queue-url http://localhost:4566/queue/us-east-1/000000000000/wobbuffe-operations \
  --attribute-names RedrivePolicy
```

## Integração com a PokéAPI real

O Compose, o perfil padrão e o handler de operações usam `https://pokeapi.co/api/v2`. `POKEAPI_BASE_URL` pode apontar para outra instância compatível quando necessário. A API real determina respostas, disponibilidade e limites de uso; nomes inexistentes retornam 404 e não são repetidos. Os testes automatizados simulam falhas com WireMock em processo, sem afetar o uso da PokéAPI pelo aplicativo.

Com roteamento ativado, falhas transitórias reais podem levar o breaker a abrir e a elegibilidade adaptativa pode encaminhar parte dos imports. O limite de 25% e a amostragem probabilística são os defaults da biblioteca; observe o código HTTP antes de esperar `202` durante o ramp-up. Não há rota especial para forçar erro da PokéAPI. Também é possível configurar os percentuais no `application.yml`/variáveis `WOBBUFFE_ROUTING_*`.

## Persistência e operação

O banco usa `jdbc:h2:mem:pokemon;DB_CLOSE_DELAY=-1` dentro do processo da API. Pokémon e operações desaparecem ao encerrar/reiniciar o processo ou container. `GET /api/operations/{operationId}` consulta somente registros que ainda estão no H2 local. O starter consome SQS no próprio processo da API; o handler Pokémon chama a PokéAPI sob as políticas de timeout, retry e circuit breaker do starter. O adaptador de ciclo de vida da API atualiza status e, no sucesso, salva o Pokémon na mesma transação local. Não há callback HTTP entre serviços.

Não existe outbox. H2 e SQS não participam de uma transação distribuída; se o processo cair entre o commit do store e a publicação, ou após a confirmação e antes da resposta HTTP, pode haver registro pendente ou resultado ambíguo. A reserva idempotente reduz publicações duplicadas enquanto o registro existir, mas H2 volátil não oferece recuperação após reinício. Um adaptador de `OperationStore` durável e estratégia transacional ficam para uma evolução futura.

No perfil `prod`, o H2 console fica desativado e o Hibernate não cria/atualiza schema (`ddl-auto=none`); este MVP não inclui migrações de produção.

## Validação

```bash
./mvnw -B verify
```

Os testes de integração da API usam H2 e WireMock em processo; nenhum Pokémon/AWS real é requisitado. O teste do publisher SQS da biblioteca exercita LocalStack via Testcontainers.
