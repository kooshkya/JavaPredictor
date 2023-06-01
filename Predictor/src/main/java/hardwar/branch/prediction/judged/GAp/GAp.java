package hardwar.branch.prediction.judged.GAp;

import hardwar.branch.prediction.shared.*;
import hardwar.branch.prediction.shared.devices.*;

import java.lang.reflect.Array;
import java.util.Arrays;

public class GAp implements BranchPredictor {
    private final int branchInstructionSize;
    private final ShiftRegister SC; // saturating counter register
    private final ShiftRegister BHR; // branch history register
    private final Cache<Bit[], Bit[]> PAPHT; // Per Address History Table

    public GAp() {
        this(4, 2, 8);
    }

    /**
     * Creates a new GAp predictor with the given BHR register size and initializes the PAPHT based on
     * the branch instruction length and saturating counter size
     *
     * @param BHRSize               the size of the BHR register
     * @param SCSize                the size of the register which hold the saturating counter value
     * @param branchInstructionSize the number of bits which is used for saving a branch instruction
     */
    public GAp(int BHRSize, int SCSize, int branchInstructionSize) {
        this.branchInstructionSize = branchInstructionSize;

        // Initialize the BHR register with the given size and no default value
        Bit[] defaultValue = new Bit[BHRSize];
        for (int i = 0; i < BHRSize; i++) {
            defaultValue[i] = Bit.ZERO;
        }
        this.BHR = new SIPORegister("Gag BHR", BHRSize, defaultValue);

        // Initializing the PAPHT with BranchInstructionSize as PHT Selector and 2^BHRSize row as each PHT entries
        // number and SCSize as block size
        PAPHT = new PerAddressPredictionHistoryTable(branchInstructionSize, 1<<BHRSize, SCSize);

        // Initialize the SC register
        defaultValue = new Bit[SCSize];
        for (int i = 0; i < SCSize; i++) {
            defaultValue[i] = Bit.ZERO;
        }
        SC = new SIPORegister("GAg SC", SCSize, defaultValue);
    }

    /**
     * predicts the result of a branch instruction based on the global branch history and branch address
     *
     * @param branchInstruction the branch instruction
     * @return the predicted outcome of the branch instruction (taken or not taken)
     */
    @Override
    public BranchResult predict(BranchInstruction branchInstruction) {
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

    /**
     * Updates the value in the cache based on actual branch result
     *
     * @param branchInstruction the branch instruction
     * @param actual            the actual result of branch (Taken or Not)
     */
    @Override
    public void update(BranchInstruction branchInstruction, BranchResult actual) {
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

        Bit[] countResult = CombinationalLogic.count(SC.read(), BranchResult.isTaken(actual), CountMode.SATURATING);
        SC.load(countResult);
        PAPHT.put(key, SC.read());
        BHR.insert(Bit.of(BranchResult.isTaken(actual)));
    }


    /**
     * concat the branch address and BHR to retrieve the desired address
     *
     * @param branchAddress program counter
     * @return concatenated value of first M bits of branch address and BHR
     */
    private Bit[] getCacheEntry(Bit[] branchAddress) {
        // Concatenate the branch address bits with the BHR bits
        Bit[] bhrBits = BHR.read();
        Bit[] cacheEntry = new Bit[branchAddress.length + bhrBits.length];
        System.arraycopy(branchAddress, 0, cacheEntry, 0, branchInstructionSize);
        System.arraycopy(bhrBits, 0, cacheEntry, branchAddress.length, bhrBits.length);
        return cacheEntry;
    }

    private Bit[] getDefaultBlock() {
        Bit[] defaultBlock = new Bit[SC.getLength()];
        Arrays.fill(defaultBlock, Bit.ZERO);
        return defaultBlock;
    }

    /**
     * @return snapshot of caches and registers content
     */
    @Override
    public String monitor() {
        return "GAp predictor snapshot: \n" + BHR.monitor() + SC.monitor() + PAPHT.monitor();
    }

}
