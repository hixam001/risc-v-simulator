package com.riscv.service;

import com.riscv.model.CpuState;
import com.riscv.model.ExecutionStep;
import com.riscv.model.Instruction;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class InstructionExecutor {

    public void execute(Instruction instr, CpuState state, ExecutionStep step) {
        try {
            switch (instr.getFormat()) {
                case "R"  -> executeR(instr.getMnemonic(), instr, state, step);
                case "SB" -> executeSB(instr.getMnemonic(), instr, state, step);
                default   -> executeI(instr.getMnemonic(), instr, state, step);
            }
        } catch (Exception e) {
            step.setError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            state.setPc(state.getPc() + 1); // advance past faulting instruction
        }

        step.setRegisters(state.getRegisterSnapshot());
        step.setMemory(buildMemorySnapshot(state));
    }

    private void executeR(String mnemonic, Instruction i, CpuState s, ExecutionStep step) {
        int rs1Val = s.getRegister(i.getRs1());
        int rs2Val = s.getRegister(i.getRs2());

        int result = switch (mnemonic) {
            case "add"  -> rs1Val + rs2Val;
            case "sub"  -> rs1Val - rs2Val;
            case "and"  -> rs1Val & rs2Val;
            case "or"   -> rs1Val | rs2Val;
            case "xor"  -> rs1Val ^ rs2Val;
            case "sll"  -> rs1Val << (rs2Val & 0x1F);
            case "srl"  -> rs1Val >>> (rs2Val & 0x1F);
            case "sra"  -> rs1Val >> (rs2Val & 0x1F);
            case "slt"  -> (rs1Val < rs2Val) ? 1 : 0;
            case "sltu" -> (Integer.compareUnsigned(rs1Val, rs2Val) < 0) ? 1 : 0;
            default     -> throw new IllegalArgumentException("Unknown R-format mnemonic: " + mnemonic);
        };

        s.setRegister(i.getRd(), result);
        s.setPc(s.getPc() + 1);
        step.setDescription(String.format("x%d = x%d %s x%d = %d",
                i.getRd(), i.getRs1(), opSymbol(mnemonic), i.getRs2(), result));
    }

    private void executeI(String mnemonic, Instruction i, CpuState s, ExecutionStep step) {
        switch (mnemonic) {
            case "addi","andi","ori","xori","slti","sltiu","slli","srli","srai" -> executeIArith(mnemonic, i, s, step);
            case "lw","lh","lb","lhu","lbu"                                     -> executeLoad(mnemonic, i, s, step);
            case "sw","sh","sb"                                                  -> executeStore(mnemonic, i, s, step);
            case "jalr"                                                           -> executeJalr(i, s, step);
            default -> throw new IllegalArgumentException("Unknown I-format mnemonic: " + mnemonic);
        }
    }

    private void executeIArith(String mnemonic, Instruction i, CpuState s, ExecutionStep step) {
        int rs1Val = s.getRegister(i.getRs1());
        int imm    = i.getImm();

        int result = switch (mnemonic) {
            case "addi"  -> rs1Val + imm;
            case "andi"  -> rs1Val & imm;
            case "ori"   -> rs1Val | imm;
            case "xori"  -> rs1Val ^ imm;
            case "slti"  -> (rs1Val < imm) ? 1 : 0;
            case "sltiu" -> (Integer.compareUnsigned(rs1Val, imm) < 0) ? 1 : 0;
            case "slli"  -> rs1Val << (imm & 0x1F);
            case "srli"  -> rs1Val >>> (imm & 0x1F);
            case "srai"  -> rs1Val >> (imm & 0x1F);
            default      -> throw new IllegalArgumentException("Unknown I-arith mnemonic: " + mnemonic);
        };

        s.setRegister(i.getRd(), result);
        s.setPc(s.getPc() + 1);
        step.setDescription(String.format("x%d = x%d %s %d = %d",
                i.getRd(), i.getRs1(), mnemonic, imm, result));
    }

    private void executeLoad(String mnemonic, Instruction i, CpuState s, ExecutionStep step) {
        int addr   = s.getRegister(i.getRs1()) + i.getImm();
        int result = switch (mnemonic) {
            case "lw"  -> s.readWord(addr);
            case "lh"  -> s.readHalfSigned(addr);
            case "lhu" -> s.readHalfUnsigned(addr);
            case "lb"  -> s.readByteSigned(addr);
            case "lbu" -> s.readByteUnsigned(addr);
            default    -> throw new IllegalArgumentException("Unknown load mnemonic: " + mnemonic);
        };

        s.setRegister(i.getRd(), result);
        s.setPc(s.getPc() + 1);
        step.setDescription(String.format("x%d = mem[x%d + %d] = 0x%08X",
                i.getRd(), i.getRs1(), i.getImm(), result));
    }

    private void executeStore(String mnemonic, Instruction i, CpuState s, ExecutionStep step) {
        int addr   = s.getRegister(i.getRs1()) + i.getImm();
        int srcVal = s.getRegister(i.getRd()); // rs2 was packed into the rd field at parse time

        switch (mnemonic) {
            case "sw" -> s.writeWord(addr, srcVal);
            case "sh" -> s.writeHalf(addr, srcVal);
            case "sb" -> s.writeByte(addr, srcVal);
            default   -> throw new IllegalArgumentException("Unknown store mnemonic: " + mnemonic);
        }

        s.setPc(s.getPc() + 1);
        step.setDescription(String.format("mem[x%d + %d] = x%d = 0x%08X",
                i.getRs1(), i.getImm(), i.getRd(), srcVal));
    }

    private void executeJalr(Instruction i, CpuState s, ExecutionStep step) {
        int nextPc   = s.getPc() + 1;
        int targetPc = (s.getRegister(i.getRs1()) + i.getImm()) >> 2; // byte addr → instruction index

        s.setRegister(i.getRd(), nextPc);
        s.setPc(targetPc);
        step.setDescription(String.format("jalr: x%d = %d, PC → %d", i.getRd(), nextPc, targetPc));
    }

    private void executeSB(String mnemonic, Instruction i, CpuState s, ExecutionStep step) {
        int rs1Val = s.getRegister(i.getRs1());
        int rs2Val = s.getRegister(i.getRs2());

        boolean taken = switch (mnemonic) {
            case "beq"  -> rs1Val == rs2Val;
            case "bne"  -> rs1Val != rs2Val;
            case "blt"  -> rs1Val < rs2Val;
            case "bge"  -> rs1Val >= rs2Val;
            case "bltu" -> Integer.compareUnsigned(rs1Val, rs2Val) < 0;
            case "bgeu" -> Integer.compareUnsigned(rs1Val, rs2Val) >= 0;
            default     -> throw new IllegalArgumentException("Unknown branch mnemonic: " + mnemonic);
        };

        step.setBranchTaken(taken);

        if (taken) {
            int targetPc = s.getPc() + i.getImm();
            s.setPc(targetPc);
            step.setDescription(String.format("%s: x%d=%d, x%d=%d → branch TAKEN (target PC=%d)",
                    mnemonic, i.getRs1(), rs1Val, i.getRs2(), rs2Val, targetPc));
        } else {
            s.setPc(s.getPc() + 1);
            step.setDescription(String.format("%s: x%d=%d, x%d=%d → branch NOT TAKEN",
                    mnemonic, i.getRs1(), rs1Val, i.getRs2(), rs2Val));
        }
    }

    // Only non-zero words are included to keep the snapshot compact
    private Map<Integer, Integer> buildMemorySnapshot(CpuState s) {
        Map<Integer, Integer> mem = new HashMap<>();
        byte[] snapshot = s.getMemorySnapshot();
        for (int addr = 0; addr < 4096; addr += 4) {
            int word = (snapshot[addr] & 0xFF)
                     | ((snapshot[addr + 1] & 0xFF) << 8)
                     | ((snapshot[addr + 2] & 0xFF) << 16)
                     | ((snapshot[addr + 3] & 0xFF) << 24);
            if (word != 0) mem.put(addr, word);
        }
        return mem;
    }

    private String opSymbol(String mnemonic) {
        return switch (mnemonic) {
            case "add"  -> "+";
            case "sub"  -> "-";
            case "and"  -> "&";
            case "or"   -> "|";
            case "xor"  -> "^";
            case "sll"  -> "<<";
            case "srl"  -> ">>>";
            case "sra"  -> ">>";
            case "slt"  -> "<(s)";
            case "sltu" -> "<(u)";
            default     -> mnemonic;
        };
    }
}
