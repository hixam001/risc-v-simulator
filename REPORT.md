# RV32I Simulator — Project Report

**Course:** Computer Architecture  
**Language:** Java (Backend) · HTML / CSS / JavaScript (Frontend)  
**Stack:** Spring Boot 3.2 · Vanilla JS · Maven Wrapper  

---

## 1. Overview

This project is an interactive, browser-based simulator for the **RV32I** (RISC-V 32-bit base integer) instruction set. It allows students to write assembly programs, execute them step-by-step, and observe every change to the CPU's register file and memory in real time — without installing any software beyond a JDK.

The design follows the Venus simulator paradigm: a two-tab interface separating the **Editor** (write code, inspect decoded fields) from the **Simulator** (step through execution, track state).

---

## 2. Project Structure

```
risc-v-simulator/
├── backend/
│   ├── mvnw / mvnw.cmd              ← Maven Wrapper (no system Maven needed)
│   ├── pom.xml
│   └── src/main/java/com/riscv/
│       ├── RiscVSimulatorApplication.java
│       ├── WebConfig.java
│       ├── controller/
│       │   └── SimulatorController.java
│       ├── service/
│       │   ├── AssemblyParser.java
│       │   ├── InstructionExecutor.java
│       │   └── SimulatorService.java
│       └── model/
│           ├── CpuState.java
│           ├── Instruction.java
│           ├── ExecutionStep.java
│           └── SimulationResult.java
└── frontend/
    ├── index.html
    ├── style.css
    └── script.js
```

**Total source lines:** ~1,640 (excluding Maven/build artifacts)

---

## 3. Supported Instruction Set

| Format | Instructions |
|--------|-------------|
| **R** | `add` `sub` `and` `or` `xor` `sll` `srl` `sra` `slt` `sltu` |
| **I (arithmetic)** | `addi` `andi` `ori` `xori` `slti` `sltiu` `slli` `srli` `srai` |
| **I (load)** | `lw` `lh` `lb` `lhu` `lbu` |
| **I (store)** | `sw` `sh` `sb` |
| **I (jump)** | `jalr` |
| **SB (branch)** | `beq` `bne` `blt` `bge` `bltu` `bgeu` |

**Register naming:** Both `x0`–`x31` and full ABI names (`zero`, `ra`, `sp`, `gp`, `tp`, `t0`–`t6`, `s0`–`s11`, `a0`–`a7`) are accepted.

**Immediate formats:** Decimal, negative, hexadecimal (`0x`), binary (`0b`).

**Out of scope (by design):** `lui`, `auipc`, `jal`, pipeline simulation, floating point, privileged instructions.

---

## 4. Architecture

### 4.1 Backend — Spring Boot REST API

The backend is a **stateless REST service**. Each request is independent; no session or database is used.

```
POST /api/simulate  →  AssemblyParser → InstructionExecutor → SimulationResult
POST /api/validate  →  AssemblyParser → ValidateResult
GET  /api/health    →  { "status": "ok", "simulator": "RV32I" }
```

**CORS** is open to all origins so `index.html` can be served as a plain file or from any dev server.

#### AssemblyParser (`225 lines`)

Two-pass parser:

1. **Pass 1 — Label collection:** Scans all lines, strips comments (`#`), ignores directives (`.`), and maps each label to its instruction index.
2. **Pass 2 — Instruction decoding:** Tokenises each instruction line (handling `imm(rs1)` syntax and comma separation), identifies the format, and constructs an `Instruction` object.

Branch targets are resolved to instruction-index offsets (not byte offsets) at parse time.

#### InstructionExecutor (`186 lines`)

Dispatches each `Instruction` to a format-specific handler:

- **R-format:** Both operands from register file, result written to `rd`.
- **I-format arithmetic:** `rs1` + sign-extended immediate.
- **I-format load:** Computes `rs1 + imm` as byte address, reads from `CpuState` memory with correct sign/zero extension per width.
- **I-format store:** Writes `rs2` (packed into the `rd` field at parse time) to `rs1 + imm`.
- **SB-format:** Evaluates branch condition; if taken, `PC ← PC + offset`; records `branchTaken` flag.

Runtime exceptions (e.g. out-of-bounds memory) are caught per-step, recorded in `ExecutionStep.error`, and execution continues.

#### CpuState (`97 lines`)

Models the entire CPU:

- **Registers:** 32 × 32-bit integers. `x0` is hardwired to zero — reads always return `0`, writes are silently discarded.
- **Memory:** 4 KB flat byte-array, accessed as bytes / halfwords / words in little-endian order.
- **PC:** Tracked as an instruction index (0, 1, 2…), not a byte address.

#### SimulatorService (`102 lines`)

Orchestrates a full simulation run:

1. Calls `AssemblyParser.parse()`.
2. Runs the fetch-execute loop until `PC ≥ instructions.size()` or the 10,000-step safety limit is hit.
3. Captures a register snapshot and a memory snapshot (non-zero words only) after every step.
4. Returns a `SimulationResult` containing the full `ExecutionStep` trace.

### 4.2 Frontend — Vanilla JS Single-Page App

Three files, no framework, no build step.

| File | Lines | Role |
|------|-------|------|
| `index.html` | 112 | Structure only — no inline script or style |
| `style.css` | 373 | VSCode-palette dark theme, flex/grid layout |
| `script.js` | 349 | All UI logic, API calls, DOM rendering |

#### Two-tab layout

**Editor tab**
- Monospace `<textarea>` with a synchronised line-number gutter
- Right sidebar: decoded instruction table populated by `POST /api/validate`

**Simulator tab**
- Toolbar: Prev / Next buttons, timeline scrubber, step counter
- Three columns: Execution Trace · Register File · Memory

#### Key behaviours

| Behaviour | Implementation |
|-----------|---------------|
| Auto-switch to Simulator on Run | `switchTab('simulator')` called after successful simulate response |
| Register change highlighting | `prevRegs` snapshot compared per-step; changed rows get `reg-changed` CSS class (blue flash animation) |
| x0 never highlights | `i !== 0` guard in `buildRegTable` |
| Branch colour-coding | `branch-taken` (teal left border) / `branch-not-taken` (red left border) CSS classes |
| Click any trace row | `jumpToStep(i)` re-renders registers and memory for that step |
| Keyboard shortcuts | `Ctrl+Enter` → Run · `←` `→` → step (when editor not focused) |
| Backend unreachable | `catch` block sets status: *"Cannot reach backend (port 8080)"* |

---

## 5. Data Flow

```
User types assembly
        │
        ▼
POST /api/simulate { code: "..." }
        │
        ▼
AssemblyParser.parse()
  ├── Pass 1: build label map
  └── Pass 2: decode → List<Instruction>
        │
        ▼
SimulatorService.run()
  └── for each instruction:
        InstructionExecutor.execute()
          ├── update CpuState (registers, memory, PC)
          └── capture ExecutionStep (snapshot + description)
        │
        ▼
SimulationResult { steps[], finalRegisters[], finalMemory{} }
        │
        ▼
Frontend receives JSON
  ├── renderTrace()   → clickable trace rows
  ├── buildRegTable() → register file with highlights
  └── renderMemory()  → non-zero memory cells
```

---

## 6. API Reference

### `POST /api/simulate`

**Request:**
```json
{ "code": "addi x1, x0, 5\nadd x2, x1, x1" }
```

**Response:**
```json
{
  "success": true,
  "totalSteps": 2,
  "errorMessage": null,
  "steps": [
    {
      "stepNumber": 1,
      "pc": 0,
      "instruction": "addi x1, x0, 5",
      "description": "x1 = x0 addi 5 = 5",
      "registers": [0, 5, 0, ...],
      "memory": {},
      "branchTaken": null,
      "error": null
    }
  ],
  "finalRegisters": [0, 5, 10, 0, ...],
  "finalMemory": {}
}
```

### `POST /api/validate`

**Response:**
```json
{
  "valid": true,
  "instructionCount": 2,
  "instructions": [
    { "lineNumber": 1, "raw": "addi x1, x0, 5", "mnemonic": "addi",
      "format": "I", "rd": 1, "rs1": 0, "rs2": 0, "imm": 5 }
  ],
  "errorMessage": null
}
```

### `GET /api/health`
```json
{ "status": "ok", "simulator": "RV32I" }
```

---

## 7. Design Decisions

| Decision | Rationale |
|----------|-----------|
| **PC as instruction index, not byte address** | Eliminates the ×4 multiply/divide everywhere in the control flow. Branch offsets are stored as instruction-index deltas at parse time. |
| **S-format rs2 packed into rd field** | The parser has no separate rs2 field for store instructions; the source register is stored in `rd` at decode time, matching how the executor reads it. |
| **Stateless backend** | No session management, no database. Each `/simulate` call is fully self-contained. Makes the server trivially restartable and horizontally scalable. |
| **10,000-step halt limit** | Prevents infinite loops from hanging the server. The limit is named `MAX_STEPS` in `SimulatorService` for easy adjustment. |
| **Memory snapshot contains non-zero words only** | Sending 4 KB of zeros on every step would bloat the JSON response. The frontend only receives and displays cells that have been written. |
| **Maven Wrapper bundled in repo** | `mvnw` + `.mvn/wrapper/` are committed so any developer with only a JDK can build and run without installing Maven separately. |
| **No frontend framework** | The UI is simple enough that React/Vue would add build complexity with no benefit. Vanilla JS keeps the project self-contained and instantly openable as a plain HTML file. |
| **Errors in response body, not HTTP 4xx** | The frontend always gets a JSON object it can render. A 400 or 500 would require the JS to parse error bodies separately. |

---

## 8. Running the Project

### Prerequisites
- JDK 17 or later
- No system Maven required (wrapper is included)

### Start the backend
```bash
cd backend
./mvnw spring-boot:run
# Server starts on http://localhost:8080
```

### Serve the frontend
```bash
cd frontend
python3 -m http.server 3000
# Open http://localhost:3000
```

Or open `frontend/index.html` directly in a browser — CORS is open so the API calls will still work.

### Keyboard shortcuts
| Shortcut | Action |
|----------|--------|
| `Ctrl+Enter` | Assemble & Run |
| `←` / `→` | Previous / Next step (when editor not focused) |

---

## 9. Known Limitations

| Limitation | Notes |
|------------|-------|
| `lui`, `auipc`, `jal` not supported | U-format and J-format are out of scope for this version |
| PC tracked as index, not byte address | `jalr` target is computed as `(rs1 + imm) >> 2` to convert a byte address back to an index |
| Memory is 4 KB | Sufficient for the programs in scope; large data programs will hit bounds |
| No assembler directives executed | `.data`, `.word`, etc. are silently skipped, not processed |
| No breakpoints | Execution always runs to completion; step navigation is post-hoc |

---

## 10. File Reference

| File | Purpose |
|------|---------|
| `backend/pom.xml` | Maven project config — Spring Boot 3.2, Java 17, single dependency (`spring-boot-starter-web`) |
| `backend/mvnw` | Unix Maven Wrapper script |
| `backend/mvnw.cmd` | Windows Maven Wrapper script |
| `RiscVSimulatorApplication.java` | Spring Boot entry point |
| `WebConfig.java` | CORS configuration — allows all origins on `/api/**` |
| `SimulatorController.java` | REST endpoint mapping (`/api/simulate`, `/api/validate`, `/api/health`) |
| `AssemblyParser.java` | Two-pass assembly parser — label resolution + instruction decoding |
| `InstructionExecutor.java` | Executes a single instruction against `CpuState`, captures step snapshot |
| `SimulatorService.java` | Orchestrates parse + execute loop, enforces step limit |
| `CpuState.java` | CPU model — 32 registers, 4 KB memory, PC |
| `Instruction.java` | Decoded instruction record (mnemonic, format, rd, rs1, rs2, imm) |
| `ExecutionStep.java` | Single step snapshot (PC, registers, memory, description, branchTaken, error) |
| `SimulationResult.java` | Full simulation output returned by the API |
| `frontend/index.html` | Two-tab HTML shell — Editor tab + Simulator tab |
| `frontend/style.css` | VSCode-palette dark theme, flex/grid layout, register flash animation |
| `frontend/script.js` | All UI logic — tab switching, API calls, trace/register/memory rendering |
