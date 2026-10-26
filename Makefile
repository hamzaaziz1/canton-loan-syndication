.RECIPEPREFIX = >
.PHONY: build test clean

build:
> dpm build --all

test: build
> cd daml/loan-tests && dpm test

clean:
> rm -rf daml/*/.daml
