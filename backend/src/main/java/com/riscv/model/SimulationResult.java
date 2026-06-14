package com.riscv.model;

import java.util.List;
import java.util.Map;

public class SimulationResult {

    private boolean success;
    private int totalSteps;
    private String errorMessage;
    private List<ExecutionStep> steps;
    private int[] finalRegisters;
    private Map<Integer, Integer> finalMemory;

    public SimulationResult(boolean success, int totalSteps, String errorMessage,
                            List<ExecutionStep> steps, int[] finalRegisters,
                            Map<Integer, Integer> finalMemory) {
        this.success        = success;
        this.totalSteps     = totalSteps;
        this.errorMessage   = errorMessage;
        this.steps          = steps;
        this.finalRegisters = finalRegisters;
        this.finalMemory    = finalMemory;
    }

    public boolean              isSuccess()                                  { return success; }
    public void                 setSuccess(boolean success)                  { this.success = success; }

    public int                  getTotalSteps()                              { return totalSteps; }
    public void                 setTotalSteps(int totalSteps)                { this.totalSteps = totalSteps; }

    public String               getErrorMessage()                            { return errorMessage; }
    public void                 setErrorMessage(String errorMessage)         { this.errorMessage = errorMessage; }

    public List<ExecutionStep>  getSteps()                                   { return steps; }
    public void                 setSteps(List<ExecutionStep> steps)          { this.steps = steps; }

    public int[]                getFinalRegisters()                          { return finalRegisters; }
    public void                 setFinalRegisters(int[] finalRegisters)      { this.finalRegisters = finalRegisters; }

    public Map<Integer, Integer> getFinalMemory()                            { return finalMemory; }
    public void                  setFinalMemory(Map<Integer, Integer> m)     { this.finalMemory = m; }
}
