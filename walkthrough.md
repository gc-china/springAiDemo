# Walkthrough - Smart Inventory System Demo

This walkthrough documents the implementation of a production-grade Function Calling strategy using Spring AI.

## 1. Core Implementation

We implemented a mixed strategy to handle common LLM tool calling challenges:

### Strategy 1: Parameter Correction (Read Operations)
- **Goal**: Handle fuzzy user inputs (e.g., "Apple 15" -> "iPhone 15").
- **Implementation**:
    - Created `MockSearchService` to simulate a search engine.
    - Implemented `ArgumentCorrectionAspect` (AOP) to intercept `queryStock` calls.
    - The aspect performs a fuzzy search and replaces the raw parameter with a precise ID before the tool is executed.

### Strategy 2: Human-in-the-loop (Write Operations)
- **Goal**: Prevent AI hallucinations from causing data corruption.
- **Implementation**:
    - Implemented `InventoryTools.transferStock` with a two-phase commit logic.
    - Phase 1: If `confirmed=false`, return a confirmation prompt.
    - Phase 2: Only execute business logic if `confirmed=true`.

## 2. Infrastructure

- **ToolRegistry**: Automatically scans and registers all `Function` beans, simplifying tool management.
- **AiService**: Manages the interaction with the LLM, injecting available tools dynamically.

## 3. Documentation

We created `PROJECT_ARCHITECTURE_AND_FLOW.md` which serves as the definitive guide for this project, containing:
- System Architecture Diagrams
- Detailed Sequence Flows for both strategies
- Component explanations
- Configuration details

## 4. Verification

We verified the implementation by:
- Checking code compilation and dependencies (added `spring-boot-starter-aop`).
- Verifying the logic of `ToolRegistry` (simplified to trust all Function beans).
- Ensuring the `AiService` correctly loads tools.
