# RAG Confluence Docker Setup with Ollama

This Docker Compose setup provides a PostgreSQL database with PGVector extension and Ollama LLM service running in Docker, while the RAG Confluence application runs locally on your machine.

## Prerequisites

- Docker and Docker Compose installed with GPU support (for Ollama)
- NVIDIA Docker runtime (nvidia-docker2) for GPU acceleration
- Java 24 or compatible JDK installed locally
- Maven installed locally (or use the included Maven wrapper)
- Confluence credentials (email and API token)
- Hardware requirements: RTX 4060 or better, 32GB+ RAM recommended

## Setup Instructions

1. **Start the PostgreSQL database and Ollama service:**
   ```bash
   docker-compose up -d
   ```

2. **Pull the required Ollama models manually:**
   After the services are running, pull the required models:
   ```bash
   docker exec rag-confluence-ollama ollama pull llama3.2
   docker exec rag-confluence-ollama ollama pull nomic-embed-text
   ```

   Or on Windows PowerShell:
   ```powershell
   docker exec rag-confluence-ollama ollama pull llama3.2
   docker exec rag-confluence-ollama ollama pull nomic-embed-text
   ```

   Note: This step may take several minutes depending on your internet connection, as these are large language models.

3. **Set up environment variables for the Java application:**
   Set the following environment variables in your system or IDE:
   - `CONFLUENCE_USERNAME`: Your Confluence email
   - `CONFLUENCE_PASS`: Your Confluence API token (not password)
   - `CONFLUENCE_BASE_URL`: Your Confluence instance URL
   - `DB_USERNAME`: Database username
   - `DB_PASSWORD`: Database password

4. **Run the Java application locally:**
   ```bash
   ./mvnw spring-boot:run
   ```
   Or on Windows:
   ```cmd
   mvnw.cmd spring-boot:run
   ```

## Services

- **PostgreSQL Database**: Runs in Docker container on port 5432 with PGVector extension
- **Ollama LLM Service**: Runs in Docker container on port 11434 with GPU acceleration
- **RAG Confluence App**: Runs locally on port 8080

## What happens when you start:

1. PostgreSQL starts in Docker with PGVector extension enabled
2. Ollama starts in Docker with GPU acceleration (models need to be pulled manually as described above)
3. The Java application connects to both the containerized database and Ollama service
4. Automatically begins indexing Confluence pages from specified spaces
5. Creates embeddings using Ollama's local LLM (llama3.2 for chat, nomic-embed-text for embeddings) and stores them in the vector database

## Accessing the Application

- Application: http://localhost:8080
- Database: localhost:5432 (accessible for pgAdmin and other database tools)
- Ollama API: http://localhost:11434 (for direct API access)

## Database Access

The PostgreSQL database is accessible from your host machine on `localhost:5432`, making it easy to:
- Connect with pgAdmin for database management
- Use other database tools for monitoring and administration
- Debug and inspect the vector embeddings

**pgAdmin Connection Details:**
- Host: localhost
- Port: 5432
- Database: confluence_embeddings
- Username: confluence_user (or your custom value from .env)
- Password: confluence_pass (or your custom value from .env)

## Logs

- Application logs are displayed in your terminal when running locally
- Database data is persisted in the `postgres_data` Docker volume

## Stopping the Services

```bash
docker-compose down
```

To also remove volumes (this will delete all data):
```bash
docker-compose down -v
```
