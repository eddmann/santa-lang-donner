.DEFAULT_GOAL := help

##@ Development
.PHONY: build
build: ## Build CLI
	./gradlew :cli:build

.PHONY: run
run: ## Run a script (FILE=path/to/script.santa)
	./gradlew :cli:run --args="$(FILE)"

.PHONY: run-test
run-test: ## Run script in test mode (FILE=path/to/script.santa)
	./gradlew :cli:run --args="-t $(FILE)"

##@ Testing/Linting
.PHONY: can-release
can-release: test ## Run all CI checks

.PHONY: test
test: ## Run all tests
	./gradlew test

##@ CLI Distribution
.PHONY: cli/jar
cli/jar: ## Build fat JAR
	./gradlew :cli:shadowJar

.PHONY: cli/jpackage
cli/jpackage: ## Build native binary (current platform only)
	./gradlew :cli:jpackage

.PHONY: cli/install
cli/install: ## Install to local directory
	./gradlew :cli:installDist

##@ Docker
.PHONY: docker/build
docker/build: cli/jar ## Build Docker image
	docker build -t santa-lang-donner:cli-latest -f cli/Dockerfile .

##@ Utilities
.PHONY: clean
clean: ## Clean build artifacts
	./gradlew clean

.PHONY: help
help: ## Show this help
	@awk 'BEGIN {FS = ":.*##"; printf "\nUsage:\n  make \033[36m<target>\033[0m\n"} /^[a-zA-Z_\/-]+:.*?##/ { printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2 } /^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5) }' $(MAKEFILE_LIST)
