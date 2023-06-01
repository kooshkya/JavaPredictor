package hardwar.branch.prediction.judged.PAp;


import hardwar.branch.prediction.shared.*;
import hardwar.branch.prediction.shared.devices.*;

import java.util.Arrays;

public class PAp implements BranchPredictor {

    private final int branchInstructionSize;

    private final ShiftRegister SC; // saturating counter register

    private final RegisterBank PABHR; // per address branch history register

    private final Cache<Bit[], Bit[]> PAPHT; // Per Address Predication History Table

    public PAp() {
        this(4, 2, 8);
    }

    public PAp(int BHRSize, int SCSize, int branchInstructionSize) {
        this.branchInstructionSize = branchInstructionSize;

        // Initialize the PABHR with the given bhr and branch instruction size
        PABHR = new RegisterBank(branchInstructionSize, BHRSize);

        // Initializing the PAPHT with BranchInstructionSize as PHT Selector and 2^BHRSize row as each PHT entries
        // number and SCSize as block size
        PAPHT = new PerAddressPredictionHistoryTable(branchInstructionSize, 1<<BHRSize, SCSize);

        // Initialize the SC register
        Bit[] defaultValue = new Bit[SCSize];
        for (int i = 0; i < SCSize; i++) {
            defaultValue[i] = Bit.ZERO;
        }
        SC = new SIPORegister("PAg SC", SCSize, defaultValue);
    }

    @Override
    public BranchResult predict(BranchInstruction branchInstruction) {
        ShiftRegister BHR = PABHR.read(branchInstruction.getInstructionAddress());

        Bit[] PCSegment = branchInstruction.getInstructionAddress();
        Bit[] BHRSegment = BHR.read();
        Bit[] key = new Bit[PCSegment.length + BHRSegment.length];
        for (int i = 0; i < key.length; i++) {
            if (i < PCSegment.length) {
                key[i] = PCSegment[i];
            } else {
                key[i] = BHRSegment[i - PCSegment.length];
            }
        }

        Bit[] defaultValue = new Bit[SC.getLength()];
        for (int i = 0; i < SC.getLength(); i++) {
            defaultValue[i] = Bit.ZERO;
        }
        PAPHT.setDefault(key, defaultValue);
        SC.load(PAPHT.get(key));

        return BranchResult.of(SC.read()[0].getValue());
    }

    @Override
    public void update(BranchInstruction instruction, BranchResult actual) {
        ShiftRegister BHR = PABHR.read(instruction.getInstructionAddress());
        Bit[] countResult = CombinationalLogic.count(SC.read(), BranchResult.isTaken(actual), CountMode.SATURATING);
        SC.load(countResult);

        Bit[] PCSegment = instruction.getInstructionAddress();
        Bit[] BHRSegment = BHR.read();
        Bit[] key = new Bit[PCSegment.length + BHRSegment.length];
        for (int i = 0; i < key.length; i++) {
            if (i < PCSegment.length) {
                key[i] = PCSegment[i];
            } else {
                key[i] = BHRSegment[i - PCSegment.length];
            }
        }
        PAPHT.put(key, SC.read());

        BHR.insert(Bit.of(BranchResult.isTaken(actual)));
        PABHR.write(instruction.getInstructionAddress(), BHR.read());
    }


    private Bit[] getCacheEntry(Bit[] branchAddress, Bit[] BHRValue) {
        // Concatenate the branch address bits with the BHR bits
        Bit[] cacheEntry = new Bit[branchAddress.length + BHRValue.length];
        System.arraycopy(branchAddress, 0, cacheEntry, 0, branchInstructionSize);
        System.arraycopy(BHRValue, 0, cacheEntry, branchAddress.length, BHRValue.length);
        return cacheEntry;
    }

    /**
     * @return a zero series of bits as default value of cache block
     */
    private Bit[] getDefaultBlock() {
        Bit[] defaultBlock = new Bit[SC.getLength()];
        Arrays.fill(defaultBlock, Bit.ZERO);
        return defaultBlock;
    }

    @Override
    public String monitor() {
        return "PAp predictor snapshot: \n" + PABHR.monitor() + SC.monitor() + PAPHT.monitor();
    }
}
