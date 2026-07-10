# Sherlock — developer task runner.
# Windows note: `make` is not installed by default. Either install it
# (choco install make, or use WSL/Git-Bash + make), or run the raw
# `docker compose ...` commands shown under each target in the README.

DC              := docker compose
INFRA_FILE      := docker-compose.infra.yml
SMOKE_FILE      := docker-compose.smoke.yml

.PHONY: help infra-up infra-down infra-logs infra-ps infra-reset smoke topics contracts

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

infra-up: ## Start infra (Kafka, Schema Registry, Kafka UI, Redis, Postgres, MinIO)
	$(DC) -f $(INFRA_FILE) up -d

infra-down: ## Stop infra (keep volumes)
	$(DC) -f $(INFRA_FILE) down

infra-reset: ## Stop infra and delete all data volumes
	$(DC) -f $(INFRA_FILE) down -v

infra-ps: ## Show infra container status
	$(DC) -f $(INFRA_FILE) ps

infra-logs: ## Tail infra logs
	$(DC) -f $(INFRA_FILE) logs -f

smoke: ## Run the Kafka produce->consume smoke test (proves infra + envelope)
	$(DC) -f $(SMOKE_FILE) run --rm --build smoke

contracts: ## Generate Java + Python stubs from proto (requires buf)
	cd contracts && buf generate
