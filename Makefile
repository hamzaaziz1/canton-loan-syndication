.RECIPEPREFIX = >
.PHONY: build test clean up down reset ps logs

COMPOSE = docker compose -f infra/docker-compose.yml

build:
> dpm build --all

test: build
> cd daml/loan-tests && dpm test

clean:
> rm -rf daml/*/.daml

up: build
> $(COMPOSE) up -d

down:
> $(COMPOSE) down

# wipes all ledger state, including node identities
reset:
> $(COMPOSE) down -v

ps:
> $(COMPOSE) ps

logs:
> $(COMPOSE) logs -f --tail=100

.PHONY: demo
# runs the m1 flow across the three participant nodes (needs make up)
demo: build
> ./scripts/run-network-demo.sh
