# Web Crawler Service

Aplicação Java que realiza buscas em websites por termos específicos, rastreando links e listando as URLs onde o termo foi encontrado. Segue princípios SOLID e arquitetura em camadas.

## 🛠 Tecnologias

- Java 14
- Spark Framework (Web Server)
- SLF4J + Logback (Logging)
- Gson (JSON)
- JUnit 5 (Testes)

## 🏗 Arquitetura

- **Controller**: Endpoints HTTP (`CrawlController`)
- **Service**: Lógica de negócio e crawling (`CrawlerService`)
- **Model**: Entidades e objetos de domínio
- **Configuration**: Propriedades e rotas (`ApplicationProperties`, `RouteConfiguration`)

## 📋 Requisitos

- Docker (para executar via container) ou Maven + JDK 14 (para executar local)
- Porta 4567 disponível (configurável)

## ⚙️ Configuração

As propriedades ficam em `src/main/resources/application.properties` e podem ser sobrescritas por variáveis de ambiente.

Principais propriedades e valores padrão:

- `server.port=4567` (ENV: `PORT`)
- `base.url=http://hiring.axreng.com/` (ENV: `BASE_URL`)
- `crawler.max.depth=50`
- `crawler.idle.timeout=30000` (ms)
- `crawler.connect.timeout=5000` (ms)
- `crawler.read.timeout=5000` (ms)
- `crawler.timeout.seconds=30`
- `crawler.limit.results=true` → ativa limite de resultados
- `crawler.max.results=100` → quantidade máxima de URLs retornadas quando o limite está ativo

## 🚀 Como executar

### Via Docker

1) Build da imagem

```bash
docker build . -t axreng/backend
```

2) Executar o container

```bash
docker run -e BASE_URL=http://hiring.axreng.com/ -p 4567:4567 --rm axreng/backend
```

### Via Maven (local)

```bash
mvn clean compile exec:java
```

## 🔍 API Endpoints

### Iniciar nova busca

`POST /crawl`

Request:

```json
{ "keyword": "security" }
```

Response:

```json
{ "id": "30vbllyb" }
```

### Consultar resultados

`GET /crawl/{id}`

Response (exemplo):

```json
{
   "id": "30vbllyb",
   "status": "active",
   "urls": [
      "http://hiring.axreng.com/index2.html",
      "http://hiring.axreng.com/htmlman1/chcon.1.html"
   ]
}
```

## � Coleção Postman

O projeto inclui uma coleção do Postman (`web-crawler-service.postman_collection`) para facilitar os testes da API.

### Como usar:

1. Importe o arquivo `web-crawler-service.postman_collection` no Postman
2. Execute as requisições:
   - **"Start search"**: Inicia uma nova busca e salva automaticamente o ID retornado
   - **"Get search"**: Consulta os resultados usando o ID da busca anterior

A coleção está configurada para ambientes LOCAL e PROD, facilitando o teste em diferentes cenários.

## �📊 Logs

**Console** 
Níveis: INFO, DEBUG, ERROR, WARN, TRACE.

## 🧪 Testes

Executar testes unitários:

```bash
mvn test
```

## ⚠️ Limitações e Considerações

1. Dados mantidos apenas em memória (sem persistência após reinício)
2. Sem paginação de resultados; pode haver limite por configuração
3. Somente links do mesmo domínio de `base.url` são seguidos