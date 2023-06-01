package hardwar.branch.prediction.judged.SAs;

import hardwar.branch.prediction.shared.*;
import hardwar.branch.prediction.shared.devices.*;

import java.util.Arrays;

public class SAs implements BranchPredictor {

    private final int branchInstructionSize;
    private final int KSize;
    private final ShiftRegister SC;
    private final RegisterBank PSBHR; // per set branch history register
    private final Cache<Bit[], Bit[]> PSPHT; // per set predication history table
    private final HashMode hashMode;

    public SAs() {
        this(4, 2, 8, 4, HashMode.XOR);
    }

    public SAs(int BHRSize, int SCSize, int branchInstructionSize, int KSize, HashMode hashMode) {
        this.branchInstructionSize = branchInstructionSize;
        this.KSize = KSize;
        this.hashMode = HashMode.XOR;

        // Initialize the PSBHR with the given bhr and branch instruction size
        PSBHR = new RegisterBank(KSize, BHRSize);

        // Initializing the PAPHT with BranchInstructionSize as PHT Selector and 2^BHRSize row as each PHT entries
        // number and SCSize as block size
        PSPHT = new PerAddressPredictionHistoryTable(KSize, 1<<BHRSize, SCSize);

        // Initialize the SC register
        Bit[] defaultValue = new Bit[SCSize];
        for (int i = 0; i < SCSize; i++) {
            defaultValue[i] = Bit.ZERO;
        }
        SC = new SIPORegister("PAg SC", SCSize, defaultValue);
    }

    @Override
    public BranchResult predict(BranchInstruction branchInstruction) {
        ShiftRegister BHR = PSBHR.read(CombinationalLogic.hash(branchInstruction.getInstructionAddress(), KSize, HashMode.XOR));

        Bit[] PCSegment = CombinationalLogic.hash(branchInstruction.getInstructionAddress(), KSize, hashMode);
        Bit[] BHRSegment = CombinationalLogic.hash(BHR.read(), KSize, hashMode);
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
        PSPHT.setDefault(key, defaultValue);
        SC.load(PSPHT.get(key));

        return BranchResult.of(SC.read()[0].getValue());
    }

    @Override
    public void update(BranchInstruction branchInstruction, BranchResult actual) {
        ShiftRegister BHR = PSBHR.read(CombinationalLogic.hash(branchInstruction.getInstructionAddress(), KSize, HashMode.XOR));
        Bit[] countResult = CombinationalLogic.count(SC.read(), BranchResult.isTaken(actual), CountMode.SATURATING);
        SC.load(countResult);

        Bit[] PCSegment = CombinationalLogic.hash(branchInstruction.getInstructionAddress(), KSize, hashMode);
        Bit[] BHRSegment = BHR.read();
        Bit[] key = new Bit[PCSegment.length + BHRSegment.length];
        for (int i = 0; i < key.length; i++) {
            if (i < PCSegment.length) {
                key[i] = PCSegment[i];
            } else {
                key[i] = BHRSegment[i - PCSegment.length];
            }
        }
        PSPHT.put(key, SC.read());

        BHR.insert(Bit.of(BranchResult.isTaken(actual)));
        PSBHR.write(CombinationalLogic.hash(branchInstruction.getInstructionAddress(), KSize, HashMode.XOR), BHR.read());
    }


    private Bit[] getAddressLine(Bit[] branchAddress) {
        // hash the branch address
        return CombinationalLogic.hash(branchAddress, KSize, hashMode);
    }

    private Bit[] getCacheEntry(Bit[] branchAddress, Bit[] BHRValue) {
        // Concatenate the branch address bits with the BHR bits
        Bit[] cacheEntry = new Bit[branchAddress.length + BHRValue.length];
        System.arraycopy(branchAddress, 0, cacheEntry, 0, KSize);
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
        return null;
    }
}
