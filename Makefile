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

.PHONY: proof
# searches every node's database for the last demo run's contract ids
proof:
> ./scripts/privacy-proof.sh

.PHONY: console
# runs canton/admin/$(SCRIPT).sc against the live nodes, e.g. make console SCRIPT=hello
console:
> $(COMPOSE) run --rm console run /app/admin/$(SCRIPT).sc -c /app/remote.conf --log-file-appender=off
