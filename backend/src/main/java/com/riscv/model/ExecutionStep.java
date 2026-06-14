package com.riscv.model;

import java.util.Map;

public class ExecutionStep {

    private int stepNumber;
    private int pc;
    private String instruction;
    private String description;
    private int[] registers;
    private Map<Integer, Integer> memory;
    private Boolean branchTaken; // null for non-branch instructions
    private String error;        // null unless a runtime error occurred on this step

    public ExecutionStep(int stepNumber, int pc, String instruction, String description,
                         int[] registers, Map<Integer, Integer> memory,
                         Boolean branchTaken, String error) {
        this.stepNumber  = stepNumber;
        this.pc          = pc;
        this.instruction = instruction;
        this.description = description;
        this.registers   = registers;
        this.memory      = memory;
        this.branchTaken = branchTaken;
        this.error       = error;
    }

    public int     getStepNumber()                    { return stepNumber; }
    public void    setStepNumber(int stepNumber)      { this.stepNumber = stepNumber; }

    public int     getPc()                            { return pc; }
    public void    setPc(int pc)                      { this.pc = pc; }

    public String  getInstruction()                   { return instruction; }
    public void    setInstruction(String instruction) { this.instruction = instruction; }

    public String  getDescription()                   { return description; }
    public void    setDescription(String description) { this.description = description; }

    public int[]   getRegisters()                     { return registers; }
    public void    setRegisters(int[] registers)      { this.registers = registers; }

    public Map<Integer, Integer> getMemory()                        { return memory; }
    public void                  setMemory(Map<Integer, Integer> m) { this.memory = m; }

    public Boolean getBranchTaken()                       { return branchTaken; }
    public void    setBranchTaken(Boolean branchTaken)    { this.branchTaken = branchTaken; }

    public String  getError()                { return error; }
    public void    setError(String error)    { this.error = error; }
}
