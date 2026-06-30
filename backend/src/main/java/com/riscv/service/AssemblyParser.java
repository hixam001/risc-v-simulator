package com.riscv.service;

import com.riscv.model.Instruction;
import org.springframework.stereotype.Service;

import java.util.*;

// Two-pass parser: pass 1 collects labels, pass 2 decodes instructions.
@Service
public class AssemblyParser {

    private static final Map<String, Integer> ABI_NAMES = new HashMap<>();

    static {
        ABI_NAMES.put("zero", 0); ABI_NAMES.put("ra",  1); ABI_NAMES.put("sp",  2);
        ABI_NAMES.put("gp",   3); ABI_NAMES.put("tp",  4); ABI_NAMES.put("t0",  5);
        ABI_NAMES.put("t1",   6); ABI_NAMES.put("t2",  7); ABI_NAMES.put("s0",  8);
        ABI_NAMES.put("fp",   8); ABI_NAMES.put("s1",  9); ABI_NAMES.put("a0", 10);
        ABI_NAMES.put("a1",  11); ABI_NAMES.put("a2", 12); ABI_NAMES.put("a3", 13);
        ABI_NAMES.put("a4",  14); ABI_NAMES.put("a5", 15); ABI_NAMES.put("a6", 16);
        ABI_NAMES.put("a7",  17); ABI_NAMES.put("s2", 18); ABI_NAMES.put("s3", 19);
        ABI_NAMES.put("s4",  20); ABI_NAMES.put("s5", 21); ABI_NAMES.put("s6", 22);
        ABI_NAMES.put("s7",  23); ABI_NAMES.put("s8", 24); ABI_NAMES.put("s9", 25);
        ABI_NAMES.put("s10", 26); ABI_NAMES.put("s11",27); ABI_NAMES.put("t3", 28);
        ABI_NAMES.put("t4",  29); ABI_NAMES.put("t5", 30); ABI_NAMES.put("t6", 31);
    }

    private static final Set<String> R_FORMAT      = Set.of("add","sub","and","or","xor","sll","srl","sra","slt","sltu");
    private static final Set<String> I_FORMAT_ARITH = Set.of("addi","andi","ori","xori","slti","sltiu","slli","srli","srai");
    private static final Set<String> LOAD_FORMAT    = Set.of("lw","lh","lb","lhu","lbu");
    private static final Set<String> S_FORMAT       = Set.of("sw","sh","sb");
    private static final Set<String> UJ_FORMAT      = Set.of("jalr");
    private static final Set<String> SB_FORMAT      = Set.of("beq","bne","blt","bge","bltu","bgeu");

    public static class ParseException extends RuntimeException {
        private final int lineNumber;

        public ParseException(int lineNumber, String message) {
            super("Line " + lineNumber + ": " + message);
            this.lineNumber = lineNumber;
        }

        public int getLineNumber() { return lineNumber; }
    }

    public List<Instruction> parse(String source) {
        String[] lines = source.split("\n", -1);
        Map<String, Integer> labelMap = collectLabels(lines);
        return decodeInstructions(lines, labelMap);
    }

    // Pass 1 — build label → instruction-index map
    private Map<String, Integer> collectLabels(String[] lines) {
        Map<String, Integer> labelMap = new LinkedHashMap<>();
        int instrIndex = 0;

        for (String rawLine : lines) {
            String line = stripComment(rawLine).strip();
            if (line.isEmpty() || isDirective(line)) continue;

            if (line.contains(":")) {
                int colonPos = line.indexOf(':');
                String label = line.substring(0, colonPos).strip();
                if (!label.isEmpty()) labelMap.put(label, instrIndex);

                String rest = line.substring(colonPos + 1).strip();
                if (!rest.isEmpty() && !isDirective(rest)) instrIndex++;
            } else {
                instrIndex++;
            }
        }

        return labelMap;
    }

    // Pass 2 — decode each instruction line into an Instruction record
    private List<Instruction> decodeInstructions(String[] lines, Map<String, Integer> labelMap) {
        List<Instruction> instructions = new ArrayList<>();

        for (int lineNum = 1; lineNum <= lines.length; lineNum++) {
            String line = stripComment(lines[lineNum - 1]).strip();
            if (line.isEmpty() || isDirective(line)) continue;

            String instrPart = line.contains(":") ? line.substring(line.indexOf(':') + 1).strip() : line;
            if (instrPart.isEmpty()) continue;

            instructions.add(decodeLine(lineNum, instrPart, labelMap, instructions.size()));
        }

        return instructions;
    }

    private Instruction decodeLine(int lineNumber, String line,
                                   Map<String, Integer> labelMap, int currentIndex) {
        // Normalise imm(rs1) syntax and commas into whitespace-separated tokens
        String[] tokens = line.replace(",", " ").replace("(", " ").replace(")", " ")
                              .trim().split("\\s+");

        if (tokens.length == 0) throw new ParseException(lineNumber, "Empty instruction");

        String mnemonic = tokens[0].toLowerCase();

        if      (R_FORMAT.contains(mnemonic))       return decodeR(lineNumber, line, mnemonic, tokens);
        else if (I_FORMAT_ARITH.contains(mnemonic)) return decodeIArith(lineNumber, line, mnemonic, tokens);
        else if (LOAD_FORMAT.contains(mnemonic))    return decodeLoad(lineNumber, line, mnemonic, tokens);
        else if (S_FORMAT.contains(mnemonic))       return decodeStore(lineNumber, line, mnemonic, tokens);
        else if (UJ_FORMAT.contains(mnemonic))      return decodeJalr(lineNumber, line, mnemonic, tokens);
        else if (SB_FORMAT.contains(mnemonic))      return decodeSB(lineNumber, line, mnemonic, tokens, labelMap, currentIndex);
        else throw new ParseException(lineNumber, "Unknown mnemonic: '" + mnemonic + "'");
    }

    private Instruction decodeR(int lineNum, String raw, String mnemonic, String[] tokens) {
        requireTokens(lineNum, tokens, 4);
        return new Instruction(lineNum, raw, mnemonic, "R",
                parseRegister(lineNum, tokens[1]),
                parseRegister(lineNum, tokens[2]),
                parseRegister(lineNum, tokens[3]), 0);
    }

    private Instruction decodeIArith(int lineNum, String raw, String mnemonic, String[] tokens) {
        requireTokens(lineNum, tokens, 4);
        return new Instruction(lineNum, raw, mnemonic, "I",
                parseRegister(lineNum, tokens[1]),
                parseRegister(lineNum, tokens[2]),
                0, parseImmediate(lineNum, tokens[3]));
    }

    // Tokens after normalisation: [mnemonic, rd, imm, rs1]
    private Instruction decodeLoad(int lineNum, String raw, String mnemonic, String[] tokens) {
        requireTokens(lineNum, tokens, 4);
        return new Instruction(lineNum, raw, mnemonic, "LOAD",
                parseRegister(lineNum, tokens[1]),
                parseRegister(lineNum, tokens[3]),
                0, parseImmediate(lineNum, tokens[2]));
    }

    // Tokens after normalisation: [mnemonic, rs2, imm, rs1] — rs2 packed into rd field
    private Instruction decodeStore(int lineNum, String raw, String mnemonic, String[] tokens) {
        requireTokens(lineNum, tokens, 4);
        return new Instruction(lineNum, raw, mnemonic, "S",    // S-format
                parseRegister(lineNum, tokens[1]),  // rs2 → rd slot
                parseRegister(lineNum, tokens[3]),
                0, parseImmediate(lineNum, tokens[2]));
    }

    private Instruction decodeJalr(int lineNum, String raw, String mnemonic, String[] tokens) {
        requireTokens(lineNum, tokens, 4);
        int rd = parseRegister(lineNum, tokens[1]);
        int rs1, imm;
        try {
            imm = parseImmediate(lineNum, tokens[2]);
            rs1 = parseRegister(lineNum, tokens[3]);
        } catch (ParseException e) {
            rs1 = parseRegister(lineNum, tokens[2]);
            imm = parseImmediate(lineNum, tokens[3]);
        }
        return new Instruction(lineNum, raw, mnemonic, "UJ", rd, rs1, 0, imm); // UJ-format
    }

    private Instruction decodeSB(int lineNum, String raw, String mnemonic, String[] tokens,
                                  Map<String, Integer> labelMap, int currentIndex) {
        requireTokens(lineNum, tokens, 4);
        return new Instruction(lineNum, raw, mnemonic, "SB", 0,
                parseRegister(lineNum, tokens[1]),
                parseRegister(lineNum, tokens[2]),
                resolveBranchTarget(lineNum, tokens[3], labelMap, currentIndex));
    }

    // Branch target: label name → byte offset, or raw byte offset literal (per RV32I spec)
    private int resolveBranchTarget(int lineNum, String target,
                                    Map<String, Integer> labelMap, int currentIndex) {
        if (labelMap.containsKey(target)) return (labelMap.get(target) - currentIndex) * 4;
        return parseImmediate(lineNum, target); // caller already provides a byte offset
    }

    private int parseRegister(int lineNum, String token) {
        String t = token.toLowerCase().trim();

        if (t.startsWith("x")) {
            try {
                int idx = Integer.parseInt(t.substring(1));
                if (idx < 0 || idx > 31) throw new ParseException(lineNum, "Register index out of range: " + token);
                return idx;
            } catch (NumberFormatException e) {
                throw new ParseException(lineNum, "Invalid register name: " + token);
            }
        }

        if (ABI_NAMES.containsKey(t)) return ABI_NAMES.get(t);

        throw new ParseException(lineNum, "Unknown register name: '" + token + "'");
    }

    // Supports decimal, negative, 0x hex, -0x hex, 0b binary, -0b binary
    private int parseImmediate(int lineNum, String token) {
        String t = token.trim();
        try {
            if      (t.startsWith("0x")  || t.startsWith("0X"))  return (int) Long.parseLong(t.substring(2), 16);
            else if (t.startsWith("-0x") || t.startsWith("-0X")) return -(int) Long.parseLong(t.substring(3), 16);
            else if (t.startsWith("0b")  || t.startsWith("0B"))  return (int) Long.parseLong(t.substring(2), 2);
            else if (t.startsWith("-0b") || t.startsWith("-0B")) return -(int) Long.parseLong(t.substring(3), 2);
            else                                                   return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            throw new ParseException(lineNum, "Invalid immediate value: '" + token + "'");
        }
    }

    private String stripComment(String line) {
        int idx = line.indexOf('#');
        return (idx >= 0) ? line.substring(0, idx) : line;
    }

    private boolean isDirective(String line) {
        return line.startsWith(".");
    }

    private void requireTokens(int lineNum, String[] tokens, int required) {
        if (tokens.length < required) {
            throw new ParseException(lineNum,
                "Instruction '" + tokens[0] + "' requires " + (required - 1)
                + " operands but found " + (tokens.length - 1));
        }
    }
}
