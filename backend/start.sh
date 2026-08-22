#!/bin/bash
cd "$(dirname "$0")"
source .env
export AI_PROVIDER MIMO_API_BASE_URL MIMO_API_KEY MIMO_MODEL MIMO_TIMEOUT MIMO_MAX_TOKENS
mvn spring-boot:run
