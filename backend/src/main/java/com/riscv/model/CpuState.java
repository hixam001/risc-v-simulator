package com.riscv.model;

public class CpuState {

    private final int[] registers = new int[32];
    private final byte[] memory   = new byte[4096];
    private int pc = 0;

    // Register access

    public int getRegister(int idx) {
        if (idx == 0) return 0;
        return registers[idx];
    }

    public void setRegister(int idx, int value) {
        if (idx == 0) return; // x0 is hardwired to zero
        registers[idx] = value;
    }

    public int[] getRegisterSnapshot() {
        return registers.clone();
    }

    // Memory — word (4 bytes, little-endian)

    public int readWord(int addr) {
        checkBounds(addr, 4);
        return (memory[addr] & 0xFF)
             | ((memory[addr + 1] & 0xFF) << 8)
             | ((memory[addr + 2] & 0xFF) << 16)
             | ((memory[addr + 3] & 0xFF) << 24);
    }

    public void writeWord(int addr, int value) {
        checkBounds(addr, 4);
        memory[addr]     = (byte) (value & 0xFF);
        memory[addr + 1] = (byte) ((value >> 8)  & 0xFF);
        memory[addr + 2] = (byte) ((value >> 16) & 0xFF);
        memory[addr + 3] = (byte) ((value >> 24) & 0xFF);
    }

    // Memory — halfword (2 bytes, little-endian)

    public int readHalfSigned(int addr) {
        checkBounds(addr, 2);
        int raw = (memory[addr] & 0xFF) | ((memory[addr + 1] & 0xFF) << 8);
        return (short) raw; // cast sign-extends from 16 bits
    }

    public int readHalfUnsigned(int addr) {
        checkBounds(addr, 2);
        return (memory[addr] & 0xFF) | ((memory[addr + 1] & 0xFF) << 8);
    }

    public void writeHalf(int addr, int value) {
        checkBounds(addr, 2);
        memory[addr]     = (byte) (value & 0xFF);
        memory[addr + 1] = (byte) ((value >> 8) & 0xFF);
    }

    // Memory — byte (1 byte)

    public int readByteSigned(int addr) {
        checkBounds(addr, 1);
        return memory[addr]; // Java byte is signed; auto-sign-extends to int
    }

    public int readByteUnsigned(int addr) {
        checkBounds(addr, 1);
        return memory[addr] & 0xFF;
    }

    public void writeByte(int addr, int value) {
        checkBounds(addr, 1);
        memory[addr] = (byte) (value & 0xFF);
    }

    public byte[] getMemorySnapshot() {
        return memory.clone();
    }

    // PC

    public int getPc()       { return pc; }
    public void setPc(int p) { this.pc = p; }

    // Bounds check

    private void checkBounds(int addr, int width) {
        if (addr < 0 || addr + width > 4096) {
            throw new IllegalArgumentException(
                "Memory access out of bounds: address 0x"
                + Integer.toHexString(addr) + " (width=" + width + ")");
        }
    }
}
