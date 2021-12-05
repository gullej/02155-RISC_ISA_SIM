import java.util.ArrayList;
import java.util.List;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class IsaSim {

    static int reg[] = new int[32];
    static byte memory[] = new byte[262144 * 4];

    public static void main(String[] args) {
        String inputFile = args[0];

        try (DataInputStream inputStream = new DataInputStream(new FileInputStream(inputFile))) {
            int temp = inputStream.available();
            for (int i = 0; i < temp; i++) {
                memory[i] = (byte) inputStream.readUnsignedByte();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        runSim(memory);
    }

    static void runSim(byte[] program) {

        int instr, opcode, rd;
        int pc = 0;
        int buffer[] = new int[4];

        while (true) {
            reg[0] = 0; // x0 is always 0

            for (int j = 0; j < 4; j++) {
                buffer[j] = Byte.toUnsignedInt(program[pc + j]);
            }
            instr = ((buffer[3]) << 24 |
                    (buffer[2]) << 16 |
                    (buffer[1]) << 8 |
                    (buffer[0]));

            opcode = instr & 0x7f;
            rd = (instr >> 7) & 0x1f;

            switch (opcode) {
                case 0b0000011: // load memory instruction instruction
                    reg[rd] = load_from_memory(instr);
                    break;
                case 0b0010011: // integer register immediate operations
                    reg[rd] = reg_imm_instr(instr);
                    break;
                case 0b0100011: // store memory instruction
                    store_to_memory(instr);
                    break;
                case 0b0110011: // integer register register operations
                    reg[rd] = reg_reg_instr(instr);
                    break;
                case 0b0010111: // auippc
                    reg[rd] = ((instr >>> 12) << 12) + pc + 4;
                    break;
                case 0b0110111: // load insinged immediate
                    reg[rd] = load_unsigned_i(instr);
                    break;
                case 0b1100011:
                    pc += branch_instruction(instr);
                    continue;
                case 0b1100111: // jump-and-link register
                    reg[rd] = pc + 4;
                    pc = jump_and_link_reg(instr);
                    continue;
                case 0b1101111: // jump-and-link
                    reg[rd] = pc + 4;
                    pc += jump_and_link(instr);
                    continue;
                case 0b1110011:
                    System.out.println("Program exit with ecall.");
                    print_registers();
                    dump_registers();
                    System.exit(0);
                default:
                    System.out.println("opcode " + Integer.toBinaryString(opcode) + " is not supported.");
            }

            pc += 4; // incease program counter

        }
    }

    private static int load_from_memory(int instr) {
        int funct3 = (instr >> 12) & 0b111;
        int rs1 = (instr >> 15) & 0x1f;
        int imm = (instr >> 20);

        int loaded;
        int buffer[] = new int[4];

        switch (funct3) {
            case 0b000: // load byte
                return (Byte.toUnsignedInt(memory[reg[rs1] + imm]) << 24) >> 24;
            case 0b001: // load half-word
                for (int i = 0; i < 2; i++) {
                    buffer[i] = Byte.toUnsignedInt(memory[reg[rs1] + imm + i]);
                }
                loaded = ((buffer[0] << 8) | buffer[1]);
                return (loaded << 16) >> 16;
            case 0b010: // load word
                for (int i = 0; i < 4; i++) {
                    buffer[i] = Byte.toUnsignedInt(memory[(reg[rs1] + imm) + i]);
                }

                loaded = ((buffer[0]) |
                        (buffer[1]) << 8 |
                        (buffer[2]) << 16 |
                        (buffer[3]) << 24);
                return loaded;
            case 0b011: // load double-word
                System.out.println("funct3 " + funct3 + " is not supported for opcode LOAD");
                return 0;
            case 0b100: // load byte unsigned
                return (memory[reg[rs1] + imm] << 24) >>> 24;
            case 0b101: // load half-word unsigned
                for (int i = 0; i < 2; i++) {
                    buffer[i] = memory[reg[rs1] + imm + i];
                }
                loaded = ((buffer[0] << 8) | buffer[1]);
                return (loaded << 16) >>> 16;
            case 0b110: // load word unsigned
                for (int i = 0; i < 4; i++) {
                    buffer[i] = memory[reg[rs1] - imm + i];
                }
                loaded = ((buffer[3]) << 24 |
                        (buffer[2]) << 16 |
                        (buffer[1]) << 8 |
                        (buffer[0]));
                return loaded;
            default:
                System.out.println("funct3 " + funct3 + " is not supported for opcode SAVE");
                return 0;
        }
    }

    private static void store_to_memory(int instr) {
        int funct3 = (instr >> 12) & 0b111;
        int rs1 = (instr >> 15) & 0x1f;
        int rs2 = (instr >> 20) & 0x1f;

        int imm = ((instr >> 7) & 0x1f |
                ((instr >> 25) & 0b1111111) << 5) << 20 >> 20;

        switch (funct3) {
            case 0b000: // save byte
                memory[reg[rs1] + imm] = (byte) (reg[rs2] & 0xff);
            case 0b001: // save half-word
                for (int i = 0; i < 2; i++) {
                    memory[reg[rs1] + imm + i] = (byte) ((reg[rs2] >>> (i * 8)) & 0xff);
                }
            case 0b010: // save word
                byte buffer[] = new byte[4];
                for (int i = 0; i < 4; i++) {
                    byte temp = (byte) ((reg[rs2] >>> (i * 8)) & 0xff);
                    buffer[i] = temp;
                }
                for (int i = 0; i < 4; i++) {
                    memory[reg[rs1] + imm + i] = buffer[i];
                }
                break;
            case 0b011: // save double-word
                System.out.println("funct3 " + funct3 + " is not supported for opcode LOAD");
                break;
            default:
                System.out.println("funct3 " + funct3 + " is not supported for opcode LOAD");
        }
    }

    private static void print_registers() {
        for (int i : reg) {
            System.out.print(i + " ");
        }
        System.out.println();
    }

    private static void dump_registers() {
        try (DataOutputStream outputStream = new DataOutputStream(new FileOutputStream("out.res"))) {
            for (int value : reg) {
                int buffer[] = new int[4];
                buffer[3] = value >>> 24;
                buffer[2] = (value >>> 16) & 0xff;
                buffer[1] = (value >>> 8) & 0xff;
                buffer[0] = value & 0xff;

                for (int i = 0; i < 4; i++) {
                    outputStream.writeByte(buffer[i]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int reg_imm_instr(int instr) {
        int funct3 = (instr >> 12) & 0b111;
        int rs1 = (instr >> 15) & 0x1f;
        int imm = (instr >> 20);

        switch (funct3) {
            case 0b000: // add sub

                return reg[rs1] + imm;
            case 0b001: // sll
                imm &= 0b11111;
                return reg[rs1] << imm;
            case 0b010: // slt
                return reg[rs1] < imm ? 1 : 0;
            case 0b011: // slt unsigned
                return Integer.compareUnsigned(reg[rs1], imm) < 0 ? 1 : 0;
            case 0b100: // xor
                return reg[rs1] ^ imm;
            case 0b101: // sr
                int funct7 = instr >> 25;
                imm &= 0b11111;
                return funct7 > 0 ? reg[rs1] >> imm : reg[rs1] >>> imm;
            case 0b110: // or
                return reg[rs1] | imm;
            case 0b111: // and
                return reg[rs1] & imm;
            default:
                System.out.println("funct3 " + funct3 + " is not supported for opcode OP_IMM");
                return 0;
        }
    }

    private static int reg_reg_instr(int instr) {
        int funct3 = (instr >> 12) & 0b111;
        int rs1 = (instr >> 15) & 0x1f;
        int rs2 = (instr >> 20) & 0b11111;

        switch (funct3) {
            case 0b000: // add sub
                int funct7 = instr >> 25;
                return funct7 > 0 ? reg[rs1] - reg[rs2] : reg[rs1] + reg[rs2];
            case 0b001: // sll
                return reg[rs1] << reg[rs2];
            case 0b010: // slt
                return reg[rs1] < reg[rs2] ? 1 : 0;
            case 0b011: // slt unsigned
                return Integer.compareUnsigned(reg[rs1], reg[rs2]) < 0 ? 1 : 0;
            case 0b100: // xor
                return reg[rs1] ^ reg[rs2];
            case 0b101: // sr
                funct7 = rs2 >> 25;
                return funct7 > 0 ? reg[rs1] >> reg[rs2] : reg[rs1] >>> reg[rs2];
            case 0b110: // or
                return reg[rs1] | reg[rs2];
            case 0b111: // and
                return reg[rs1] & reg[rs2];
            default:
                System.out.println("funct3 " + funct3 + " is not supported for opcode OP_IMM");
                return 0;
        }
    }

    private static int load_unsigned_i(int instr) {
        int imm = (instr >>> 12) << 12;
        return imm;
    }

    private static int branch_instruction(int instr) {
        int funct3 = (instr >> 12) & 0b111;
        int rs1 = (instr >> 15) & 0b11111;
        int rs2 = (instr >> 20) & 0b11111;
        int offset = calculate_offset(instr);

        switch (funct3) {
            case 0b000: // beq
                return reg[rs1] == reg[rs2] ? offset : 4;
            case 0b001: // bne
                return reg[rs1] != reg[rs2] ? offset : 4;
            case 0b100: // blt
                return reg[rs1] < reg[rs2] ? offset : 4;
            case 0b101: // bge
                return reg[rs1] >= reg[rs2] ? offset : 4;
            case 0b110: // bltu
                return Integer.compareUnsigned(reg[rs1], reg[rs2]) < 0 ? offset : 4;
            case 0b111: // bgeu
                return Integer.compareUnsigned(reg[rs1], reg[rs2]) > 0 ? offset : 4;
            default:
                System.out.println("funct3 " + funct3 + " is not supported for opcode BRANCH");
                return 0;
        }
    }

    private static int calculate_offset(int instr) {
        int offset_1 = (instr >>> 7) & 0b11111;
        int offset_2 = (instr >>> 25) & 0b1111111;

        int offset = (((offset_1 & 0b1) << 11 |
                (offset_1 & 0b11110) |
                (offset_2 & 0b111111) << 5 |
                (offset_2 & 0b1000000) << 6) << 19) >> 19;

        return offset;
    }

    private static int jump_and_link_reg(int instr) {

        int rs1 = (instr >> 15) & 0x1f;
        int imm = (instr >> 20);

        int new_pc = (reg[rs1] + imm);
        // if (new_pc % 4 != 0) {
        // System.out.println("instruction-address-misaligned1");
        // System.exit(1);
        // }

        return new_pc;
    }

    private static int jump_and_link(int instr) {
        int imm = (instr >> 12);

        int offset = (((imm & 0x80000) |
                ((imm & 0x7fe00) >>> 9) |
                ((imm & 0x100) << 2) |
                ((imm & 0xff) << 11)) << 12) >> 11;

        // if (offset % 4 != 0) {
        // System.out.println("instruction-address-misaligned2");
        // System.exit(1);
        // }

        return offset;
    }

    private static void print_regs_from_dump(String filename) {
        List<Integer> read_reg = new ArrayList<>();

        try (DataInputStream inputStream = new DataInputStream(
                new FileInputStream(filename))) {
            while (inputStream.available() > 0) {
                int buffer[] = new int[4];
                for (int i = 0; i < 4; i++) {
                    buffer[i] = inputStream.readUnsignedByte();
                }
                int regs = ((buffer[3]) << 24 |
                        (buffer[2]) << 16 |
                        (buffer[1]) << 8 |
                        (buffer[0]));
                read_reg.add(regs);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int i : read_reg) {
            System.out.print(i + " ");
        }
        System.out.println();
    }
}