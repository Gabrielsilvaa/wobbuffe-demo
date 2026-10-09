# Wobbuffe Demo — API e infraestrutura

API demonstrativa da biblioteca [Wobbuffe Starter](https://github.com/Gabrielsilvaa/wobbuffe-starter). Este repositório contém a aplicação Spring Boot, frontend, configuração Docker Compose e LocalStack com SQS/DLQ. O starter fica no repositório separado.

## Clonar os dois projetos

Mantenha ambos no mesmo diretório pai. O Compose usa esse contexto comum para compilar a biblioteca e a API:

```bash
git clone https://github.com/Gabrielsilvaa/wobbuffe-starter.git
git clone https://github.com/Gabrielsilvaa/wobbuffe-demo.git
cd wobbuffe-demo
```

## Subir API e infraestrutura

Requisitos: Docker com Compose, Java 25/Maven Wrapper para execução fora do container, e AWS CLI para consultar as filas.

```bash
cp infra/.env.example infra/.env
docker compose --env-file infra/.env -f infra/compose.yaml up --build -d
docker compose --env-file infra/.env -f infra/compose.yaml ps
```

A aplicação estará em `http://localhost:8080/`. O Compose chama a PokéAPI pública real (`https://pokeapi.co/api/v2`); não há mock no fluxo executado localmente. Os testes automatizados da API usam WireMock e não requisitam a PokéAPI.

Para conferir as filas locais:

```bash
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 \
  aws --endpoint-url=http://localhost:4566 sqs list-queues
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 \
  aws --endpoint-url=http://localhost:4566 sqs get-queue-attributes \
  --queue-url http://localhost:4566/queue/us-east-1/000000000000/wobbuffe-operations \
  --attribute-names RedrivePolicy
```

`test/test` são credenciais fictícias para LocalStack. Não use essas credenciais para AWS.

## Demonstração de resiliência

A interface permite criar Pokémon, consultar operações e controlar a simulação de instabilidade da integração com a PokéAPI. Quando um import elegível é aceito para a fila, a API só responde `202` após a confirmação do SQS; o consumer que roda dentro da API processa a mensagem através do Wobbuffe Starter.

```bash
curl http://localhost:8080/api/integrations/pokeapi
curl -i -X POST http://localhost:8080/api/pokemon/import/natu \
  -H 'Idempotency-Key: demo-natu-001' \
  -H 'X-Correlation-Id: demo-natu-correlation'
```

Consulte os detalhes de execução, rotas, configuração e limites no [README da API](wobbuffe-api/README.md). A documentação executiva e os diagramas arquiteturais estão no [repositório do starter](https://github.com/Gabrielsilvaa/wobbuffe-starter).

## Testes

```bash
cd wobbuffe-api
./mvnw -B verify
```

Os testes não dependem de Pokémon ou AWS reais. Para detalhes sobre H2 em memória, idempotência, LocalStack e limites de consistência, leia o README da API e a documentação do starter.
