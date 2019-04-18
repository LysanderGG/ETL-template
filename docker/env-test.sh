#!/bin/bash

# Check if docker-compose is installed
if ! [ -x "$(command -v docker-compose)" ]; then
  echo 'Error: docker-compose is not installed.' >&2
  exit 1
fi

export POSTGRES_HOST=localhost
export POSTGRES_PORT=$(docker-compose port postgres-test 5432 | cut -d: -f2)
export POSTGRES_DB=models_test
