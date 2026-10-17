#!/bin/bash
# Load bien moi truong tu file .env o thu muc goc
if [ -f ../.env ]; then
    export $(cat ../.env | grep -v '^#' | xargs)
fi
./mvnw spring-boot:run
