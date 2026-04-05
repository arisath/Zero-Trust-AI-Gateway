#!/bin/bash

# Build script for Secure LLM Gateway

echo "Building Secure LLM Gateway..."

# Clean previous build
mvn clean

# Build the project
mvn package

echo "Build completed successfully!"
echo "Jar file created at: target/secure-llm-gateway-0.0.1-SNAPSHOT.jar"
