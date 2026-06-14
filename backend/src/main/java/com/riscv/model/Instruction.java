package com.riscv.model;

// S-format stores are encoded as I-format: the source register (rs2) is packed into the rd field.
public class Instruction {

    private final int    lineNumber;
    private final String raw;
    private final String mnemonic;
    private final String format;
    private final int    rd;
    private final int    rs1;
    private final int    rs2;
    private final int    imm;

    public Instruction(int lineNumber, String raw, String mnemonic,
                       String format, int rd, int rs1, int rs2, int imm) {
        this.lineNumber = lineNumber;
        this.raw        = raw;
        this.mnemonic   = mnemonic;
        this.format     = format;
        this.rd         = rd;
        this.rs1        = rs1;
        this.rs2        = rs2;
        this.imm        = imm;
    }

    public int    getLineNumber() { return lineNumber; }
    public String getRaw()        { return raw; }
    public String getMnemonic()   { return mnemonic; }
    public String getFormat()     { return format; }
    public int    getRd()         { return rd; }
    public int    getRs1()        { return rs1; }
    public int    getRs2()        { return rs2; }
    public int    getImm()        { return imm; }

    @Override
    public String toString() {
        return String.format("Instruction{line=%d, raw='%s', format=%s, rd=%d, rs1=%d, rs2=%d, imm=%d}",
                lineNumber, raw, format, rd, rs1, rs2, imm);
    }
}
