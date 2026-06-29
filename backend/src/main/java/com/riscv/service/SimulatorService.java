package com.riscv.service;

import com.riscv.model.CpuState;
import com.riscv.model.ExecutionStep;
import com.riscv.model.Instruction;
import com.riscv.model.SimulationResult;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SimulatorService {

    private static final int MAX_STEPS = 10_000; // halt limit to prevent infinite loops

    private final AssemblyParser parser;
    private final InstructionExecutor executor;

    public SimulatorService(AssemblyParser parser, InstructionExecutor executor) {
        this.parser   = parser;
        this.executor = executor;
    }

    public SimulationResult run(String source) {
        List<Instruction> instructions;
        try {
            instructions = parser.parse(source);
        } catch (AssemblyParser.ParseException e) {
            return errorResult(e.getMessage());
        } catch (Exception e) {
            return errorResult("Parse error: " + e.getMessage());
        }

        if (instructions.isEmpty()) {
            return errorResult("No instructions found in the provided source.");
        }

        CpuState state = new CpuState();
        List<ExecutionStep> steps = new ArrayList<>();
        int stepNumber = 1;

        while (state.getPc() < instructions.size() * 4 && stepNumber <= MAX_STEPS) {
            int pc = state.getPc();
            ExecutionStep step = new ExecutionStep(
                    stepNumber, pc, instructions.get(pc / 4).getRaw(), "", null, null, null, null);
            executor.execute(instructions.get(pc / 4), state, step);
            steps.add(step);
            stepNumber++;
        }

        boolean success     = true;
        String errorMessage = null;

        if (stepNumber > MAX_STEPS && state.getPc() < instructions.size() * 4) {
            success      = false;
            errorMessage = "Execution halted: step limit of " + MAX_STEPS + " reached. "
                         + "Your program may contain an infinite loop.";
        }

        int[] finalRegisters = steps.isEmpty() ? new int[32] : steps.get(steps.size() - 1).getRegisters();
        Map<Integer, Integer> finalMemory = steps.isEmpty() ? Collections.emptyMap() : steps.get(steps.size() - 1).getMemory();

        return new SimulationResult(success, steps.size(), errorMessage, steps, finalRegisters, finalMemory);
    }

    public ValidateResult validate(String source) {
        try {
            List<Instruction> instructions = parser.parse(source);
            return new ValidateResult(true, instructions.size(), instructions, null);
        } catch (AssemblyParser.ParseException e) {
            return new ValidateResult(false, 0, Collections.emptyList(), e.getMessage());
        } catch (Exception e) {
            return new ValidateResult(false, 0, Collections.emptyList(), "Parse error: " + e.getMessage());
        }
    }

    // Inline DTO for POST /api/validate
    public static class ValidateResult {
        private final boolean valid;
        private final int instructionCount;
        private final List<Instruction> instructions;
        private final String errorMessage;

        public ValidateResult(boolean valid, int instructionCount,
                              List<Instruction> instructions, String errorMessage) {
            this.valid            = valid;
            this.instructionCount = instructionCount;
            this.instructions     = instructions;
            this.errorMessage     = errorMessage;
        }

        public boolean          isValid()             { return valid; }
        public int              getInstructionCount() { return instructionCount; }
        public List<Instruction> getInstructions()   { return instructions; }
        public String           getErrorMessage()     { return errorMessage; }
    }

    private SimulationResult errorResult(String message) {
        return new SimulationResult(false, 0, message,
                Collections.emptyList(), new int[32], Collections.emptyMap());
    }
}
