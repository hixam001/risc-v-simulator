# RV32I Simulator: Project Report

**Course:** Computer Architecture
**Technology Stack:** Java 17, Spring Boot 3.2, HTML, CSS, JavaScript
**Build Tool:** Maven Wrapper (no system Maven required)

---

## 1. Project Overview

This project is an interactive, browser-based simulator for the RV32I instruction set, which is the 32-bit base integer subset of the RISC-V architecture. It allows students to write RISC-V assembly programs directly in a web browser, assemble them, and then step through execution one instruction at a time, observing every change to the CPU register file and memory at each step.

The tool requires nothing more than a JDK to run. There is no database, no cloud dependency, and no frontend build pipeline. The entire system starts with a single Maven command.

The interface is inspired by educational tools like the Venus RISC-V simulator. It provides a two-tab layout: an Editor tab where code is written and decoded, and a Simulator tab where execution is replayed interactively. The backend is a stateless Spring Boot REST API that parses and simulates a full program run on each request, returning a complete trace of every execution step.

---

## 2. Project Structure

The project is divided into two top-level directories. The backend directory contains the Spring Boot application built with Maven and structured under the com.riscv package. It holds the main application entry point, a CORS configuration class, a REST controller, three service classes responsible for parsing, executing, and orchestrating simulations, and four model classes that represent instructions, CPU state, execution steps, and simulation results. The frontend directory contains three plain files: index.html for structure, style.css for visual design, and script.js for all user interface logic and API communication. There is no build step on the frontend side and the files can be opened directly in a browser.

---

## 3. Supported Instruction Set

The simulator supports the core RV32I instructions across four encoding formats. The R format covers arithmetic and logic operations between two registers and includes add, sub, and, or, xor, sll, srl, sra, slt, and sltu. The I format is used in three contexts: arithmetic operations (addi, andi, ori, xori, slti, sltiu, slli, srli, srai), memory load operations (lw, lh, lb, lhu, lbu), and the register-indirect jump instruction jalr. The S format covers memory store operations: sw, sh, and sb. The SB format covers all six conditional branch instructions: beq, bne, blt, bge, bltu, and bgeu.

Registers can be named either with their numeric identifiers from x0 through x31, or with their ABI aliases such as zero, ra, sp, gp, tp, the temporary registers t0 through t6, the saved registers s0 through s11, and the argument registers a0 through a7. Immediate values can be written in decimal, negative decimal, hexadecimal with a 0x prefix, or binary with a 0b prefix.

Instructions not implemented in this version by design include lui, auipc, jal, CSR instructions, floating-point instructions, pipeline modeling, and privileged operations.

---

## 4. System Architecture

The project is split into a backend REST API and a frontend single-page application. The two layers communicate over HTTP using JSON. There is no shared state between requests, and every simulate call is fully self-contained.

### 4.1 Backend

The backend is a Spring Boot application that exposes three endpoints under the /api path. It has three service classes each with a focused responsibility, and four model classes that carry data between those services and the HTTP layer.

#### AssemblyParser

The parser uses a two-pass approach to correctly handle forward label references in branch instructions. In the first pass, the parser scans every line of the source, strips comments (anything following a hash character), ignores assembler directives (lines beginning with a dot), and records each label name alongside the instruction index it points to. This label map is what allows a branch instruction early in the program to reference a label that appears further down in the source.

In the second pass, each non-empty non-label line is tokenised. The comma separator and the imm(rs1) memory addressing syntax are normalised into uniform whitespace-separated tokens before splitting. The mnemonic is matched against format-specific sets to select the appropriate decode method, which constructs an Instruction record with all fields populated.

Branch targets are resolved to instruction-index offsets at parse time. If a branch target is a label, the offset is computed as the label index minus the current instruction index. If it is a raw numeric value, it is divided by four to convert a byte offset to an instruction-index offset. Any parse failure produces a ParseException that includes the line number where the error occurred.

#### InstructionExecutor

The executor receives a single decoded instruction and a mutable CPU state object, performs the operation, advances the program counter, and records an execution step snapshot. Each instruction format has its own private handler method.

For R-format instructions, both source registers are read from the register file and the arithmetic or logical operation is applied. The result is written to the destination register and the PC advances by one. For I-format arithmetic instructions, the rs1 register and the sign-extended immediate value are combined, and the result is written to the destination register. For I-format load instructions, the effective byte address is computed as rs1 plus the immediate, the appropriate number of bytes is read from memory with correct signed or unsigned extension for the access width, and the result is written to the destination register.

For S-format store instructions, the effective byte address is computed as rs1 plus the immediate. Because the Instruction model does not have a dedicated slot for the store source register in the rs2 position, the parser places that register index into the rd field at decode time. The executor reads it back from rd and writes the register value to memory. For SB-format branch instructions, the comparison between rs1 and rs2 is evaluated. If the condition is true the PC is set to PC plus the pre-computed index offset, otherwise the PC increments by one. A boolean recording whether the branch was taken is stored on the step for frontend rendering. For jalr, the value of PC plus one is saved into the destination register and the PC is set to the result of shifting rs1 plus the immediate right by two bits, which converts the programmer-specified byte address back into an instruction index.

Any runtime exception such as an out-of-bounds memory access is caught at the step level. The error message is recorded in the step error field and execution continues from the next instruction rather than aborting the simulation.

#### CpuState

This class models the entire processor state for a single simulation run. The register file is an array of 32 signed 32-bit integers. Register x0 is hardwired to zero: reads always return zero regardless of the stored value, and writes are silently discarded. Memory is a flat 4 KB byte array. All accesses are bounds-checked and any violation throws an IllegalArgumentException. Reads and writes are available at byte, halfword, and word granularity, all in little-endian byte order. The program counter is stored as an instruction index rather than a byte address, which avoids repeated division and multiplication by four throughout the control flow logic.

#### SimulatorService

This service coordinates the full simulation lifecycle. It begins by calling the parser on the source string, and any parse error is returned immediately as a failed result. It then initialises a fresh CPU state and enters the fetch-execute loop. At each iteration, the current PC is used to fetch the instruction from the parsed list, the executor is called, and the resulting step is appended to the trace. The loop terminates when the PC moves past the last instruction or when the 10,000-step safety limit is reached. A final result object containing the full step trace, final register values, and final memory state is then returned to the controller.

### 4.2 Frontend

The frontend consists of three plain files with no framework and no build step. The application can be opened directly from the filesystem or served from any static file server.

The Editor tab contains a monospace text area for writing assembly code. A synchronised line-number gutter scrolls in lockstep with the editor content. A dropdown allows loading one of five built-in example programs covering basic arithmetic, immediate operations, memory loads and stores, a branching loop, and a Fibonacci sequence. When the Parse button is pressed, the code is sent to the validate endpoint and the decoded instruction list is rendered in a right sidebar showing each instruction mnemonic, format, register fields, and immediate value.

The Simulator tab is activated automatically after a successful assemble-and-run. It has three side-by-side panels. The Execution Trace panel lists every instruction execution as a clickable row. Branch instructions are colour-coded: teal when the branch was taken and red when it was not. Clicking any row updates the other two panels to show the CPU state at that exact step. The Register File panel shows all 32 registers with their ABI name, signed decimal value, and hexadecimal value. Registers that changed since the previous step are highlighted with a brief blue flash animation. Register x0 is never highlighted because it cannot change. The Memory panel shows only the non-zero word-aligned memory cells and remains empty until a store instruction has been executed.

Step navigation is provided by Prev and Next buttons alongside a step counter. Ctrl+Enter triggers assemble-and-run, and the left and right arrow keys step backwards and forwards through execution when the editor is not focused.

---

## 5. Data Flow

A simulation begins when the user presses the Assemble and Run button. The frontend reads the contents of the editor and sends a POST request to the simulate endpoint with the assembly source as a JSON string.

On the server, the assembly parser runs its two passes to produce a list of decoded instructions. The simulator service then initialises a clean CPU state and steps through the instruction list, calling the executor on each one. After every instruction, a snapshot of the register file, a compact map of non-zero memory words, and a human-readable description are saved into an execution step record. When execution finishes, the complete list of steps and the final CPU state are wrapped into a result object and serialised to JSON.

The frontend receives the response and uses it to populate all three simulator panels. The trace panel is built from the step list. The register panel and memory panel are populated for the first step by default, and clicking any trace row re-populates them for the selected step. The simulator tab is activated automatically so the user sees the results without any extra navigation.

---

## 6. API Reference

The backend exposes three endpoints, all under the /api path. CORS is open to all origins so the frontend can call the API whether it is served from a local file or a development server.

The simulate endpoint accepts a POST request with a JSON body containing a field named code whose value is the assembly source text. It returns a JSON object with a success boolean, a totalSteps integer, an errorMessage string that is null on success, a steps array where each entry holds the step number, the PC at that step, the raw instruction text, a description of the operation, a 32-element register array, a map of non-zero memory words, a nullable branchTaken boolean, and a nullable error string. The response also includes finalRegisters and finalMemory fields mirroring the last step state. When success is false, errorMessage explains the failure and steps contains whatever completed before the error.

The validate endpoint accepts a POST request with the same body format. It returns a JSON object with a valid boolean, an instructionCount integer, an instructions array where each entry contains the line number, raw text, mnemonic, format, rd, rs1, rs2, and imm fields, and an errorMessage string that is null when parsing succeeded.

The health endpoint accepts a GET request and returns a JSON object with a status field set to ok and a simulator field set to RV32I. It is used to confirm the backend is reachable.

---

## 7. Design Decisions

The PC is tracked as an instruction index rather than a byte address. In a real processor the PC holds a byte address and instructions are four bytes wide. Tracking the PC as an index simplifies almost every part of the simulator: the fetch loop indexes directly into the instruction list, branch offsets are stored as index deltas at parse time, and there are no repeated divisions by four in the execution logic. The only conversion needed is in jalr, which receives a programmer-specified byte address and divides by four once to obtain an index.

Store instructions pack rs2 into the rd field. The Instruction model has fields for rd, rs1, rs2, and imm. Store instructions in the RISC-V specification do not use rd, but they need a source register that is not used by other formats in that position. Rather than adding a separate field to the model and complicating every format, the parser places the store source register into rd at decode time and the executor reads it back from there consistently.

The backend is stateless per request. Each call to the simulate endpoint creates a brand new CPU state and runs the program from scratch. There is no concept of a session or a running simulation on the server. This makes the server trivially restartable, eliminates concurrency concerns entirely, and means the frontend can re-simulate with different code at any time without any cleanup.

A 10,000-step halt limit prevents infinite loops from blocking the server. Without it, a program with an unconditional branch to itself would run indefinitely. The limit is defined as a named constant in the simulator service so it can be adjusted in one place without touching execution logic.

Memory snapshots contain only non-zero words. After each instruction the simulator builds a compact map of only those four-byte-aligned memory words that hold a non-zero value. Sending a full 4 KB array on every step for every instruction would bloat the JSON responses significantly. The frontend only receives and renders cells that have actually been written.

The frontend uses no framework. The UI has three logical views and a handful of DOM update routines. A framework like React or Vue would add a build step, a node_modules directory, and a compilation pipeline for functionality that is straightforward to write in plain JavaScript. The current approach keeps the project completely self-contained with no npm, no bundler, and no version conflicts.

Errors are embedded in the response body rather than using HTTP error status codes. The frontend always receives a JSON object it can render directly. If the backend returned a 400 or 500 status code, the JavaScript fetch handler would need to detect the status, attempt to parse an error body, and handle cases where the error body might not be valid JSON. Returning a structured object with a success flag and an errorMessage string on every outcome simplifies the client considerably.

---

## 8. Running the Project

To run the project a JDK 17 or later installation is required. No system Maven installation is needed because the Maven Wrapper is bundled in the repository.

To start the backend, open a terminal in the backend directory and run ./mvnw spring-boot:run on Unix or mvnw.cmd spring-boot:run on Windows. Spring Boot will start on port 8080 and a confirmation message will appear in the console once the server is ready.

To use the frontend, the simplest option is to open frontend/index.html directly in a browser. CORS is configured to allow all origins so the API calls to localhost on port 8080 will work without any additional configuration. Alternatively the frontend can be served with a local file server by running python3 -m http.server 3000 inside the frontend directory and opening http://localhost:3000 in a browser.

---

## 9. Known Limitations

The U-format and J-format instructions lui, auipc, and jal are not supported. Programs that require loading 32-bit constants or using the full standard calling convention will not assemble in this version.

The jalr instruction assumes the programmer provides a byte-addressed target. The simulator converts it to an instruction index by shifting right by two. If a programmer writes the target as an instruction index rather than a byte address, the jump will land at the wrong instruction.

Memory is limited to 4 KB. Any access outside that range is caught and recorded as a step error rather than crashing the simulation, but programs that store large data arrays will reach this limit quickly.

Assembler directives such as .data and .word are silently ignored. No data is initialised from directives. Memory can only be populated through explicit store instructions executed within the program body.

There are no breakpoints or conditional pause capability. The backend always runs the entire program to completion before returning a response. Step navigation in the frontend is performed by replaying pre-captured snapshots, not by interrupting a live execution. There is no way to inject a register value or modify state mid-simulation.

The simulator models no pipeline. Instructions execute one at a time in strict program order with no concept of stages, hazards, forwarding, or branch prediction. This is intentional for an introductory course context where the focus is on instruction semantics rather than microarchitecture behaviour.

---

## 10. File Reference

backend/pom.xml is the Maven project definition specifying Spring Boot 3.2, Java 17, and a single dependency on spring-boot-starter-web.

backend/mvnw and backend/mvnw.cmd are the Unix and Windows Maven Wrapper scripts that allow the project to be built without a system Maven installation.

RiscVSimulatorApplication.java is the Spring Boot entry point that launches the embedded Tomcat server.

WebConfig.java holds the CORS configuration, which allows all origins on all endpoints under the /api path.

SimulatorController.java maps HTTP requests to service method calls and handles the three endpoints: simulate, validate, and health.

AssemblyParser.java implements the two-pass assembly parser. The first pass builds the label-to-index map and the second pass decodes each instruction line into an Instruction record.

InstructionExecutor.java executes a single instruction against a CpuState object, updates all relevant state fields, and populates an ExecutionStep with a register snapshot, a memory snapshot, and a human-readable description of the operation.

SimulatorService.java orchestrates the full parse-and-execute loop, enforces the step limit, and constructs the final SimulationResult returned by the API.

CpuState.java models the CPU, holding 32 registers, 4 KB of byte-addressable memory, and the program counter. It enforces the x0 hardwired-zero rule and bounds-checks every memory access.

Instruction.java is an immutable record for a decoded instruction carrying the line number, raw source text, mnemonic, format tag, and the four operand fields rd, rs1, rs2, and imm.

ExecutionStep.java is a snapshot of a single execution step carrying the step number, PC, raw instruction text, description string, register array, memory map, branch taken flag, and error string.

SimulationResult.java is the complete output of one simulation run containing the step trace, final register state, final memory state, total step count, and an overall success flag.

frontend/index.html is the HTML shell for the two-tab interface and contains no inline script or style.

frontend/style.css implements the visual design with a VSCode-palette dark theme, flexbox and grid layout, a register change flash animation, and branch colour-coding for the trace panel.

frontend/script.js contains all client-side logic and handles tab switching, API requests, trace rendering, register table updates, memory panel rendering, keyboard shortcut handling, and example program loading.
