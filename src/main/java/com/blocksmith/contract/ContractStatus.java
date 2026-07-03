package com.blocksmith.contract;

/**
 * Lifecycle of a contract's locked funds.
 *
 * OPEN    - funds are locked; anyone who satisfies the locking script can claim
 * CLAIMED - funds have been released to a claimer; the contract is spent
 */
public enum ContractStatus {
    OPEN,
    CLAIMED
}
