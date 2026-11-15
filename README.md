# exchange-service

Cotações de câmbio via AwesomeAPI — cache Redis, retry e circuit breaker.

Manifests Kubernetes em `k8s/k8s.yaml`; CI em `.github/workflows/ci.yml`.

```bash
./mvnw package          # compila e roda os testes
docker build -t <usuario>/exchange-service .   # imagem (eclipse-temurin:25-jre)
```

Parte do projeto **Store** (Plataformas — Insper 2026.2, Enzo Canavero). Documentação completa,
`docker compose` e scripts de EKS no repositório principal `platform`.
