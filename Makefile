SHELL := /bin/bash

.PHONY: start

start: start-containers
	source ./docker/env.sh && sbt etl/run

.PHONY: start-containers stop-containers destroy-containers

start-containers:
	docker-compose up -d

stop-containers:
	docker-compose stop

destroy-containers:
	docker-compose down --volumes

.PHONY: sbt test/start-containers test

sbt: start-containers
	source ./docker/env.sh && sbt

test/start-containers:
	docker-compose up --detach postgres-test &&\
	source docker/env-test.sh

test: test/start-containers
	source docker/env-test.sh && sbt test
