# SQLingo 🌐🗣️

**SQLingo** is an advanced **NL2SQL** (Natural Language to SQL) engine that enables users to interact intuitively with relational databases using natural language. The project seamlessly bridges the gap between human intent and complex database schemas by combining modern Large Language Models (LLMs) with a robust, enterprise-ready software architecture, making it an exceptional showcase of full-stack engineering and production-grade AI integration.

Built with a fully decoupled, scalable architecture, SQLingo dynamically inspects arbitrary relational databases, intelligently selects relevant schema context using database-centric **Retrieval-Augmented Generation (RAG)**, validates incoming queries for security and syntax, and implements a deterministic self-correction loop to gracefully handle failures.

---

## 🚀 Key Features & Architectural Highlights

* **Natural Language to SQL Translation:** Implements sophisticated prompt engineering pipelines (supporting Zero-shot, Few-shot, and Chain-of-Thought reasoning techniques) leveraging the OpenAI API to translate complex business questions into precise, executable SQL queries.
* **Database-Centric RAG (Schema Augmentation):** Utilizes semantic vector embeddings to build a high-fidelity vector representation of database schemas. The system dynamically matches, filters, and retrieves only the core tables and columns relevant to the user's prompt. This optimizes context windows, significantly reduces LLM token costs, and substantially improves translation accuracy.
* **Automated Schema Introspection:** Features an autonomous metadata inspection engine that extracts tables, data types, primary keys, and foreign key relationships directly from any target database to build an accurate context layer dynamically.
* **Multi-Tiered Security & Validation:** Includes an active validation layer designed to sanitize generated SQL, detect dangerous or unauthorized non-read statements, and systematically mitigate SQL Injection (SQLi) vulnerabilities before execution.
* **Autonomous Self-Correction Loop (Self-Repair):** When a generated query triggers a validation or runtime execution error, the system traps the database exception, feeds the error logs back into the LLM context, and executes an automated healing loop to correct, re-validate, and complete the execution.
* **Explainability & Technical Inspection Dashboards:** Beyond raw data presentation, the user interface features an explainability panel outlining the logical reasoning behind the generated SQL, alongside a real-time Technical Inspector tracking execution metadata, prompt configurations, and RAG retrieval scores.

---

## 🛠️ Tech Stack & Production-Grade Practices

SQLingo is built upon an industry-standard enterprise stack, strictly enforcing clean code patterns, decoupling of concerns, and full containerization.

### Backend Engine
* **Java 21 & Spring Boot 3:** Delivers a modern, high-performance foundation utilizing virtual threads, record patterns, and robust dependency injection.
* **OpenAI API (GPT-4 / GPT-3.5) & Spring AI:** Drives advanced cognitive workflows, structured JSON processing, and text embedding mappings.
* **Vector Store Integration:** Handles schema chunk indexing and highly localized metadata matching.
* **SQLite / PostgreSQL Compatibility:** Supports multi-dialect query mapping, tested thoroughly with complex enterprise relations (including the *Spider Hospital* evaluation schema).
* **Maven:** Controls enterprise dependency management, life cycle verification, and rigorous testing profiles.

### Frontend Application
* **Angular 17+:** Architected around a clean, modular structure utilizing standalone components, strict typing, and optimized lifecycle management.
* **RxJS & Reactive State Management:** Manages asynchronous data streams, query histories, system statuses, and granular loading states reactively.
* **TailwindCSS & PostCSS:** Provides a sleek, responsive dashboard utilizing a customized dark aesthetic tailored for modern engineering panels.

### DevOps, CI/CD & Research QA
* **Docker & Docker Compose:** Containerizes both tiers alongside the database environment, guaranteeing immediate multi-environment orchestration with a single command.
* **GitHub Actions CI:** Fully integrated automated pipelines that build, test, and lint both the Java backend and Angular frontend code bases upon every commit.
* **Empirical Research & Benchmark Framework:** Equipped with a standalone quantitative evaluation engine. It automatically benchmarks prompt accuracy against the academic *Spider* dataset, measuring the statistical impact of RAG versus full-schema serialization on token consumption and accuracy.
