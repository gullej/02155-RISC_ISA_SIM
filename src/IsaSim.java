import java.util.ArrayList;
import java.util.List;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;

public class IsaSim {

    static int reg[] = new int[31];
    public static int rd;
    //static int pc, rd, rs1, rs2, imm, funct3, funct7; 
    static List<Integer> program = new ArrayList<>();

    public static void main(String[] args) {
        String inputFile = args[0];

        try (DataInputStream inputStream = new DataInputStream(new FileInputStream(inputFile))) {
            while(inputStream.available() > 0) {
                int buffer[] = new int[4];
                for (int i = 0; i < 4; i++) {
                    buffer[i] = inputStream.readUnsignedByte();
                }
                int instr = ( 
                    (buffer[3]) << 24 | 
                    (buffer[2]) << 16 |
                    (buffer[1]) << 8 |
                    (buffer[0]));
                program.add(instr);
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }

        runSim(program);
    }

    static void runSim(List<Integer> program2) {

        int pc = 0;

        while(true) {
            int instr = program2.get(pc >> 2);
            System.out.println(Integer.toBinaryString(instr));
            int opcode = instr & 0x7f;
            rd = (instr >> 7) & 0x1f;

            switch (opcode) {
                case 0b10011: // integer register immediate operations
                    reg[rd] = reg_imm_instr(instr);
                    break;
                case 0b110011: // integer register register operations
                    reg[rd] = reg_reg_instr(instr);
                    break;
                case 0b110111: // load insinged immediate
                    reg[rd] = load_unsigned_i(instr);
                    break;
                case 0b1100011:
                    pc += branch_instruction(instr);
                    continue;
                case 0b1100111: // jump-and-link register
                    reg[rd] = pc + 4;
                    pc += jump_and_link_reg(instr);
                    continue;
                case 0b1101111: // jump-and-link
                    reg[rd] = pc + 4;
                    pc += jump_and_link(instr);
                    continue;

                case 0b1110011:
                    System.out.println("Program exit with ecall.");
                    System.exit(0);
                    break;
                default:
                    System.out.println("opcode " + Integer.toBinaryString(opcode) + " is not supported.");
            }

            reg[0] = 0; // x0 is always 0
            pc += 4;    // incease program counter

            for (int i = 0; i< reg.length; ++i) {
                System.out.print(reg[i] + " ");
            }
            System.out.println();

            if ((pc >> 2) >= program2.size()) { // exit if at the end of program
                System.out.println("Program exit.");
                System.exit(0);
            }
        }
    }

    private static int reg_imm_instr(int instr) {
        int funct3 = (instr >> 12) & 0b111;
        int rs1 = (instr >> 15) & 0x1f;
        int imm  = (instr >> 20);
        System.out.println("OP-IMM " + Integer.toBinaryString(funct3) + " x" + rd + " x" + rs1 + " " + imm);

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
                return  funct7 > 0 ? reg[rs1] >> imm : reg[rs1] >>> imm;
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
        int rs2  = (instr >> 20) & 0b11111;
        System.out.println("OP " + Integer.toBinaryString(funct3) + " x" + rd + " x" + rs1 + " x" + rs2);

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
                return  funct7 > 0 ? reg[rs1] >> reg[rs2] : reg[rs1] >>> reg[rs2];
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
        System.out.println("lui x" + rd + " " + imm);
        return imm;
    }

    
    private static int branch_instruction(int instr) {
        int funct3 = (instr >> 12) & 0b111;
        int rs1 = (instr >> 15) & 0b11111;
        int rs2 = (instr >> 20) & 0b11111;

        int offset = calculate_offset(instr);

        System.out.println("BRANCH " + Integer.toBinaryString(funct3) + " x" + rs1 + " x" + rs2 + " " + offset);

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
        int offset_1 = (instr >>> 7) & 0b11111 ;
        int offset_2 = (instr >>> 25) & 0b1111111;

        int offset = ((
            (offset_1 & 0b1) << 11 |
            (offset_1 & 0b11110)  |
            (offset_2 & 0b111111) << 5 |
            (offset_2 & 0b1000000) << 6
        ) << 19 ) >> 19;
        return offset;
    }

    private static int jump_and_link_reg(int instr) {
        int rs1 = (instr >> 15) & 0x1f;
        int imm  = (instr >> 20);
        int new_pc = reg[rs1] + imm;
        if (new_pc % 4 == 0) {
            return new_pc;
        } else {
            System.out.println("instruction-address-misaligned");
            System.exit(1);
            return 0;
        }
    }

    private static int jump_and_link(int instr) {
        int rs1 = (instr >> 15) & 0x1f;
        int imm  = (instr >> 12);

        int offset = ((
            (imm & 0x80000) |
            ((imm & 0x7fe00 ) >>> 9) |
            ((imm & 0x100) << 2) |
            ((imm & 0xff) << 11)
        ) << 12) >> 11;

        int new_pc = reg[rs1] + offset;
        if (new_pc % 4 == 0) {
            return new_pc;
        } else {
            System.out.println("instruction-address-misaligned");
            System.exit(1);
            return 0;
        }
    }

}