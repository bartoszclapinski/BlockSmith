package com.blocksmith.contract;

import com.blocksmith.util.BlockchainConfig;

/**
 * THEORY: A contract is funds locked behind a script instead of an owner.
 *
 * A plain transaction gives money to an ADDRESS; a contract gives money to a
 * CONDITION. The locking script is the condition; the locked amount belongs
 * to nobody until someone presents unlocking data that makes the script
 * evaluate true, at which point the chain credits them.
 *
 * DERIVED STATE:
 * A Contract object is not gossiped or stored on its own - it is REBUILT from
 * the chain. A deploy transaction (recipient = CONTRACT:<id>, carrying the
 * locking script) opens it; a claim transaction (sender = CONTRACT:<id>)
 * closes it. Because every node replays the same blocks, every node derives
 * the same contract registry - the same trick the balance model uses.
 *
 * TWO FLAGSHIP CONDITIONS:
 * - Hashlock: "SHA256 PUSH <hash> EQUAL"    - reveal the preimage to claim
 * - Timelock: "PUSH <height> CHECKLOCKTIME" - claimable once the chain is tall enough
 */
public class Contract {

    private final String contractId;
    private final String funder;
    private final String lockingScript;
    private final double amount;
    private final long creationHeight;
    private ContractStatus status;
    private String claimer;

    public Contract(String contractId, String funder, String lockingScript,
                    double amount, long creationHeight) {
        this.contractId = contractId;
        this.funder = funder;
        this.lockingScript = lockingScript;
        this.amount = amount;
        this.creationHeight = creationHeight;
        this.status = ContractStatus.OPEN;
    }

    /** Marks the contract spent by {@code claimer}. */
    public void markClaimed(String claimer) {
        this.status = ContractStatus.CLAIMED;
        this.claimer = claimer;
    }

    /** The chain address holding this contract's locked funds. */
    public String getAddress() {
        return BlockchainConfig.CONTRACT_ADDRESS_PREFIX + contractId;
    }

    // ===== GETTERS =====

    public String getContractId() {
        return contractId;
    }

    public String getFunder() {
        return funder;
    }

    public String getLockingScript() {
        return lockingScript;
    }

    public double getAmount() {
        return amount;
    }

    public long getCreationHeight() {
        return creationHeight;
    }

    public ContractStatus getStatus() {
        return status;
    }

    public String getClaimer() {
        return claimer;
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.US, "Contract{id=%s..., %s locked %.2f, %s}",
            contractId.substring(0, Math.min(8, contractId.length())),
            funder,
            amount,
            status);
    }
}
