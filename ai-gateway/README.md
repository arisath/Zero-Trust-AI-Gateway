# Secure LLM Gateway

## Overview

A production-grade gateway for secure LLM interactions built with Spring Boot and LangChain4j. This gateway implements multi-layer security to protect LLM services from various threats while providing efficient routing and rate limiting.

## Architecture

This gateway implements a filter chain approach to secure LLM interactions with three core security filters:

1. **PII Redactor Filter** - Outbound security that scans for and redacts personally identifiable information
2. **Jailbreak Classifier Filter** - Inbound security that detects prompt injection attempts using a local small language model
3. **Token Guard & Rate Limiter** - Prevents abuse and ensures fair usage with rate limiting and token tracking

## Tech Stack

- **Framework**: Spring Boot 3.x
- **Gateway Engine**: Spring Cloud Gateway
- **LLM Orchestration**: LangChain4j (supports OpenAI, Gemini, Ollama, etc.)
- **Security**: Spring Security with OAuth2/OIDC
- **PII Detection**: Apache OpenNLP
- **Rate Limiting**: Resilience4j with Redis
- **Monitoring**: Spring Boot Actuator

## Project Structure

```
ai-gateway/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/securellm/
│   │   │       ├── GatewayApplication.java
│   │   │       ├── config/
│   │   │       │   ├── GatewayConfig.java
│   │   │       │   └── SecurityConfig.java
│   │   │       ├── controller/
│   │   │       │   └── ConnectivityController.java
│   │   │       ├── filter/
│   │   │       │   ├── PiiRedactorFilter.java
│   │   │       │   ├── JailbreakClassifierFilter.java
│   │   │       │   └── TokenGuardFilter.java
│   │   │       ├── service/
│   │   │       │   ├── PiiDetectionService.java
│   │   │       │   ├── LlmService.java
│   │   │       │   └── TokenUsageService.java
│   │   │       └── util/
│   │   │           └── SecurityUtils.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-local.yml
│   └── test/
│       └── java/
│           └── com/securellm/
│               └── GatewayApplicationTests.java
├── pom.xml
├── README.md
├── Dockerfile
└── build.sh
```

## Core Features

- Multi-layer security for LLM interactions
- PII detection and redaction
- Jailbreak prompt detection using local SLM
- Token usage tracking and rate limiting
- Integration with multiple LLM providers (OpenAI, Gemini, Ollama)
- Resilience patterns for high availability
- Comprehensive monitoring and metrics

## Security Implementation

The gateway enforces security at multiple levels:
- Authentication via OAuth2/OIDC
- Authorization checks
- Input validation and sanitization
- Output redaction of sensitive data
- Rate limiting to prevent abuse
- Circuit breaking for resilience

## Deployment

The gateway can be deployed as a microservice and integrated with any CIAM solution for authentication and authorization.

## Build and Run

### Prerequisites
- Java 17
- Maven
- Redis server (for rate limiting)

### Build
```bash
./build.sh
```

### Run
```bash
mvn spring-boot:run
```

## Configuration

The gateway can be configured via `application.yml` and `application-local.yml` files. Key configuration options include:
- Redis connection settings
- OAuth2 issuer URIs
- Rate limiting thresholds
- Circuit breaker settings
- Security policies

## Usage

The gateway exposes REST endpoints that route requests to various LLM services while applying security filters to all requests.

## Monitoring

The gateway exposes metrics via Spring Boot Actuator:
- Health checks
- Metrics endpoint
- Prometheus endpoint
- Info endpoint

## License

MIT License
