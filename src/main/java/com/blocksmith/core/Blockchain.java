package com.blocksmith.core;

import com.blocksmith.contract.Contract;
import com.blocksmith.contract.ContractStatus;
import com.blocksmith.contract.ScriptVM;
import com.blocksmith.util.BlockchainConfig;
import com.blocksmith.util.HashUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages the blockchain - a linked list of blocks.
 * 
 * THEORY: A blockchain is a linked list of blocks where each block
 * contains the hash of the previous block, creating a chain.
 * 
 * STRUCTURE:
 * [Genesis] -> [Block 1] -> [Block 2] -> ... -> [Block N]
 *    ↑            ↑            ↑                    ↑
 * prevHash=0   prevHash=    prevHash=           prevHash=
 *              genesis.hash  block1.hash        blockN-1.hash
 * 
 * TRANSACTION FLOW:
 * 1. User creates a transaction
 * 2. Transaction is added to the PENDING POOL
 * 3. Miner collects pending transactions
 * 4. Miner creates a new block with those transactions
 * 5. Miner mines the block (Proof-of-Work)
 * 6. Block is added to chain, transactions are confirmed
 * 
 * MINING REWARD:
 * When a miner successfully mines a block, they receive a reward.
 * This is implemented as a special "coinbase" transaction from
 * the system to the miner's address. 
 */
public class Blockchain {

    private final List<Block> chain;
    private final List<Transaction> pendingTransactions;

    /**
     * THEORY: CONTRACT REGISTRY (Sprint 14)
     *
     * Contract state is DERIVED from the chain, never stored independently:
     * every appended block is scanned for deploy transactions (recipient =
     * CONTRACT:<id>, carrying a locking script) and claim transactions
     * (sender = CONTRACT:<id>). Since all nodes replay the same blocks, all
     * nodes derive the same registry - like balances, contract state is a
     * view over the chain.
     */
    private final Map<String, Contract> contracts = new LinkedHashMap<>();

    /** Evaluates contract scripts; never throws (consensus safety). */
    private final ScriptVM scriptVM = new ScriptVM();

    /**
     * Largest number of orphan blocks held at once. Caps memory if a peer
     * floods us with forward blocks whose parents never arrive.
     */
    static final int MAX_ORPHANS = 50;

    /**
     * THEORY: ORPHAN BLOCKS
     *
     * Over a gossip network a block can arrive before its parent. Such a
     * block is an "orphan" - valid in isolation but not yet connectable to
     * our chain. We park it here, keyed by the previousHash it is waiting
     * for, and attach it once that parent block is appended.
     *
     * A LinkedHashMap in access-order eviction mode drops the oldest orphan
     * when the buffer is full, bounding memory.
     */
    private final Map<String, Block> orphans =
            new LinkedHashMap<>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Block> eldest) {
                    return size() > MAX_ORPHANS;
                }
            };

    /**
     * Creates a new blockchain with the Genesis block.
     * 
     * THEORY: Genesis Block is the first block in any blockchain.
     * 
     * SPECIAL PROPERTIES:
     * - index = 0 (first block)
     * - previousHash = "0" (no previous block exists)
     * - Usually hardcoded or has special data
     * 
     * BITCOIN'S GENESIS: Contains the message:
     * "The Times 03/Jan/2009 Chancellor on brink of second bailout for banks"
     * This proves the block couldn't have been created before that date.
     */
    public Blockchain() {
        this.chain = new ArrayList<>();
        this.pendingTransactions = new ArrayList<>();

        // Create and mine the Genesis block
        Block genesis = Block.createGenesisBlock();
        genesis.mineBlock(BlockchainConfig.MINING_DIFFICULTY);
        chain.add(genesis);
    }
        
    /**
     * Returns the latest (most recent) block in the chain
     *   
     * @return The latest block added to the chain
     */
    public Block getLatestBlock() {
        return chain.get(chain.size() - 1);
    }

    /**
     * Adds a new block to the blockchain (Legacy method for string data).    
     * 
     * @param data The data to store in the new block
     * @return The newly created and mined block
     */ 
    public Block addBlock(String data) {
        Block latestBlock = getLatestBlock();
        int newIndex = latestBlock.getIndex() + 1;

        Block newBlock = new Block(newIndex, data, latestBlock.getHash());
        newBlock.mineBlock(BlockchainConfig.MINING_DIFFICULTY);

        chain.add(newBlock);

        return newBlock;
    }

    /**
     * Appends a block mined elsewhere (e.g. received from a peer) after
     * validating it against the current chain tip.
     *
     * THEORY: ACCEPTING EXTERNAL BLOCKS
     *
     * Unlike addBlock(String), which mines a block locally, this method
     * takes an already-mined block and accepts it only if it extends our
     * chain cleanly. The block arrives from the untrusted network, so we
     * re-verify everything rather than trust the sender:
     *   1. Index is exactly tip + 1 (no gaps, no duplicates)
     *   2. previousHash links to our current tip
     *   3. The stored hash matches a fresh recomputation (integrity)
     *   4. The hash satisfies the Proof-of-Work target (it was really mined)
     *
     * Returns false instead of throwing: a bad block is a normal network
     * event, not a programming error.
     *
     * @param block A block mined elsewhere
     * @return true if the block was valid and appended, false otherwise
     */
    public boolean addBlock(Block block) {
        if (block == null) return false;

        // A block must be internally sound (hash integrity + PoW) before we
        // either append or buffer it. A malformed block is dropped outright.
        if (!isWellFormed(block)) return false;

        Block tip = getLatestBlock();

        // Extends our current tip cleanly: append, then pull in any orphans
        // that were waiting on this new block.
        if (block.getIndex() == tip.getIndex() + 1
                && block.getPreviousHash().equals(tip.getHash())) {
            chain.add(block);
            removeConfirmed(block);
            registerContracts(block);
            attachOrphans();
            return true;
        }

        // Arrived ahead of its parent (index beyond tip+1): park as an orphan
        // until the missing parent shows up.
        if (block.getIndex() > tip.getIndex() + 1) {
            orphans.put(block.getPreviousHash(), block);
        }

        return false;
    }

    /**
     * Checks a block is internally consistent: its stored hash matches a
     * fresh recomputation and satisfies the Proof-of-Work target. Says
     * nothing about how it links to our chain.
     */
    private boolean isWellFormed(Block block) {
        if (!block.getHash().equals(block.calculateHash())) return false;
        String target = "0".repeat(BlockchainConfig.MINING_DIFFICULTY);
        return block.getHash().startsWith(target);
    }

    /**
     * Drains the orphan buffer onto the chain: while an orphan is waiting on
     * the current tip's hash, append it and advance. A run of consecutive
     * out-of-order blocks resolves in a single pass.
     */
    private void attachOrphans() {
        while (true) {
            Block tip = getLatestBlock();
            Block child = orphans.get(tip.getHash());
            if (child == null) break;

            orphans.remove(tip.getHash());
            if (child.getIndex() == tip.getIndex() + 1 && isWellFormed(child)) {
                chain.add(child);
                removeConfirmed(child);
                registerContracts(child);
            } else {
                break; // stale/ill-fitting orphan: drop and stop
            }
        }
    }

    /**
     * Removes a newly-appended block's transactions from the pending pool.
     *
     * A transaction gossiped to us earlier may still be sitting in our mempool
     * when the block that confirms it arrives from a peer. Without this it
     * would linger and be re-gossiped, or even mined again into a later block.
     * The local mine path clears the whole pool itself, so this covers the
     * externally-appended (network) case.
     */
    private void removeConfirmed(Block block) {
        if (block.getTransactions().isEmpty()) return;

        Set<String> confirmedIds = new HashSet<>();
        for (Transaction tx : block.getTransactions()) {
            confirmedIds.add(tx.getTransactionId());
        }
        pendingTransactions.removeIf(tx -> confirmedIds.contains(tx.getTransactionId()));
    }

    /**
     * @return number of blocks currently held in the orphan buffer
     */
    public int getOrphanCount() {
        return orphans.size();
    }

    /**
     * Adds a transaction to the pending pool.
     * 
     * THEORY: MEMPOOL (Memory Pool)
     * 
     * In real blockchains, pending transactions wait in a "mempool"
     * until a miner includes them in a block. Miners typically
     * prioritize transactions wiht higher fees.
     * 
     * VALIDATION: 
     * 1. Transaction must be valid (amount > 0, non-empty addresses)
     * 2. COINBASE transactions are rejected (only created by mining)
     * 3. Sender must have sufficient balance
     * 
     * BALANCE CHECK:
     * We check both confirmed balance (in blockchain) and pending
     * outgoind transactions to prevent double-spending.
     * 
     * @param transaction The transaction to add
     * @return true if transaction was added, false if invalid
     */
    public boolean addTransaction(Transaction transaction) {
        // Validate the transaction
        if (transaction == null || !transaction.isValid()) {
            System.out.println("Transaction rejected: Invalid transaction");
            return false;
        }

        // Reject COINBASE transactions (only mining creates these)
        if (transaction.getSender().equals(BlockchainConfig.COINBASE_ADDRESS)) {
            System.out.println("Transaction rejected: Cannot manually create COINBASE transactions");
            return false;
        }

        // Contract rules (Sprint 14). A transfer TO a contract address must
        // carry a locking script (otherwise the funds would be burned); a
        // transfer FROM one is a claim and must satisfy the contract's script.
        if (transaction.getRecipient().startsWith(BlockchainConfig.CONTRACT_ADDRESS_PREFIX)
                && (transaction.getLockingScript() == null
                    || transaction.getLockingScript().isBlank())) {
            System.out.println("Transaction rejected: Contract deploy requires a locking script");
            return false;
        }
        if (transaction.getSender().startsWith(BlockchainConfig.CONTRACT_ADDRESS_PREFIX)
                && !isValidClaim(transaction)) {
            return false;
        }

        // Reject duplicates already waiting in the pool. This also stops a
        // gossiped transaction from being relayed twice (returns false).
        String txId = transaction.getTransactionId();
        for (Transaction pending : pendingTransactions) {
            if (pending.getTransactionId().equals(txId)) {
                return false;
            }
        }

        // Check sender has sufficient balance
        double senderBalance = getBalance(transaction.getSender());
        double pendingOutgoing = getPendingOutgoing(transaction.getSender());
        double availableBalance = senderBalance - pendingOutgoing;

        if (availableBalance < transaction.getAmount()) {
            System.out.println("Transacion rejected: Insufficient funds. " + 
                "Available: " + availableBalance + " " + BlockchainConfig.CURRENCY_SYMBOL + 
                ", Required: " + transaction.getAmount() + " " + BlockchainConfig.CURRENCY_SYMBOL);
                return false;
        }

        // Add to pending pool
        pendingTransactions.add(transaction);
        System.out.println("Transaction added to pending pool: " + transaction);
        return true;
    }

    /**
     * Calculates total outgoing amount in pending transactions for an address
     * 
     * THEORY: PENDING BALANCE
     * 
     * When checking if someone can afford a transaction, we must also
     * consider funds they've already "promised" in pending transactions.
     * Otherwise, they could submit multiple transactions thath together
     * exceed their balance.
     * 
     * @param address Ther address to check
     * @return Total amount being sent in pending transactions
     */
    private double getPendingOutgoing(String address) {
        double pending = 0;
         for (Transaction tx : pendingTransactions) {
            if (tx.getSender().equals(address)) pending += tx.getAmount();
         }
         return pending;
    }

    /**
     * Mines all pending transactions into a new block.
     * 
     * THEORY: MINING PROCESS
     * 
     * 1. Collect pending transactions
     * 2. Add a COINBASE transaction (mining reward)
     * 3. Create a new block with all transactions
     * 4. Mine the block (find valid nonce)
     * 5. Add block to chain
     * 6. Clear pending transactions
     * 
     * COINBASE TRANSACTION:
     * A special transaction with no sender (from "COINBASE")
     * that rewards the miner. This is how new coins are created.
     * 
     * @param minerAddress Address to receive mining reward
     * @return The newly mined block, or null if no transactions
     */
    public Block minePendingTransactions(String minerAddress) {
        // Create coinbase (reward) transaction
        Transaction rewardTx = new Transaction(
            BlockchainConfig.COINBASE_ADDRESS,
            minerAddress,
            BlockchainConfig.MINING_REWARD
        );

        // Create transaction list with reward + pending transactions
        List<Transaction> blockTransactions = new ArrayList<>();
        blockTransactions.add(rewardTx);
        blockTransactions.addAll(pendingTransactions);

        // Create and mine the new block
        Block latestBlock = getLatestBlock();
        int newIndex = latestBlock.getIndex() + 1;

        Block newBlock = new Block (newIndex, blockTransactions, latestBlock.getHash());

        System.out.println("\n⛏️  Mining block #" + newIndex + " with " + blockTransactions.size() + " transactions...");
        newBlock.mineBlock(BlockchainConfig.MINING_DIFFICULTY);

        // Add block to chain
        chain.add(newBlock);
        registerContracts(newBlock);

        // Clear pending transactions (they're now in a block)
        pendingTransactions.clear();

        System.out.println("✅ Block mined and added to chain!");
        System.out.println("   Miner " + minerAddress + " received " + 
            BlockchainConfig.MINING_REWARD + " " + BlockchainConfig.CURRENCY_SYMBOL);
        
        return newBlock;        
    }

    /**
     * Calculates the balance of an address.
     * 
     * THEORY: ACCOUNT BALANCE
     * 
     * In our simple model, balance = incoming - outgoing.
     * We scan ALL transactions in ALL blocks to calculate this.
     * 
     * BITCOIN uses UTXO (Unspent Transaction Outputs) instead,
     * which is more efficient for large chains but more complex.
     * 
     * @param address The addres to check
     * @return The current balance
     */
    public double getBalance(String address) {
        double balance = 0;

        // Scan all blocks
        for (Block block : chain) {
            // scan all transactions in block
            for (Transaction tx : block.getTransactions()) {
                // if address is sender, substract amount
                if (tx.getSender().equals(address)) balance -= tx.getAmount();

                // if address is recipient, add amount
                if (tx.getRecipient().equals(address)) balance += tx.getAmount();                       
            }
        }
        return balance;
    }

    // ===== CONTRACTS (Sprint 14) =====

    /**
     * Locks {@code amount} of the funder's balance behind a script.
     *
     * THEORY: DEPLOY = A TRANSFER TO A CONDITION
     *
     * The deploy is an ordinary transaction whose recipient is the contract's
     * address (CONTRACT:<id>) and which carries the locking script. It goes
     * through the pending pool and into a block like any other transfer, so
     * the funder's balance check, gossip, and mining need no special cases.
     * The contract becomes OPEN (claimable) once the deploy is mined.
     *
     * @param funder Address funding the contract
     * @param lockingScript Script that guards the funds (e.g. a hashlock)
     * @param amount Amount to lock
     * @return The pending deploy transaction, or null if rejected
     */
    public Transaction deployContract(String funder, String lockingScript, double amount) {
        if (funder == null || funder.isBlank()
                || lockingScript == null || lockingScript.isBlank()) {
            System.out.println("Contract rejected: funder and locking script are required");
            return null;
        }

        // The contract id seeds the contract's address; hashing the deploy
        // parameters keeps it unique and lets every node derive the same id
        // from the transaction once it arrives in a block.
        String contractId = HashUtil.applySha256(
                funder + lockingScript + amount + System.currentTimeMillis());

        Transaction deploy = new Transaction(
                funder,
                BlockchainConfig.CONTRACT_ADDRESS_PREFIX + contractId,
                amount);
        deploy.setLockingScript(lockingScript);

        return addTransaction(deploy) ? deploy : null;
    }

    /**
     * Claims an OPEN contract by presenting unlocking data.
     *
     * The claim is a transaction FROM the contract's address TO the claimer.
     * addTransaction() runs the unlocking data and the locking script through
     * the VM; the funds move once the claim is mined. The pending-outgoing
     * balance check doubles as double-claim protection: a second claim on the
     * same contract finds no available funds.
     *
     * @param contractId Id of the contract to claim
     * @param claimer Address to credit
     * @param unlockingScript Data satisfying the locking script (may be empty
     *                        for pure timelocks)
     * @return The pending claim transaction, or null if rejected
     */
    public Transaction claimContract(String contractId, String claimer, String unlockingScript) {
        Contract contract = contracts.get(contractId);
        if (contract == null || claimer == null || claimer.isBlank()) {
            System.out.println("Claim rejected: Unknown contract or missing claimer");
            return null;
        }

        Transaction claim = new Transaction(contract.getAddress(), claimer, contract.getAmount());
        claim.setUnlockingScript(unlockingScript);

        return addTransaction(claim) ? claim : null;
    }

    /**
     * Validates a claim transaction against the contract registry and the
     * script VM. Used by addTransaction for locally-created claims and for
     * claims gossiped by peers alike - every node re-verifies every claim.
     */
    private boolean isValidClaim(Transaction claim) {
        String contractId = claim.getSender()
                .substring(BlockchainConfig.CONTRACT_ADDRESS_PREFIX.length());

        Contract contract = contracts.get(contractId);
        if (contract == null) {
            System.out.println("Claim rejected: Unknown contract " + contractId);
            return false;
        }
        if (contract.getStatus() != ContractStatus.OPEN) {
            System.out.println("Claim rejected: Contract already claimed");
            return false;
        }
        if (claim.getAmount() != contract.getAmount()) {
            System.out.println("Claim rejected: Amount must equal the locked amount");
            return false;
        }

        // The claim height is the index the NEXT block will have - the
        // earliest height the claim could be mined at (CHECKLOCKTIME).
        if (!scriptVM.execute(claim.getUnlockingScript(), contract.getLockingScript(), chain.size())) {
            System.out.println("Claim rejected: Script evaluated to false");
            return false;
        }
        return true;
    }

    /**
     * Scans an appended block for contract deploys and claims and updates the
     * registry. Called on every block-append path (local mine, external
     * append, orphan attach), so contract state always mirrors the chain.
     */
    private void registerContracts(Block block) {
        for (Transaction tx : block.getTransactions()) {
            String recipient = tx.getRecipient();
            if (recipient.startsWith(BlockchainConfig.CONTRACT_ADDRESS_PREFIX)
                    && tx.getLockingScript() != null && !tx.getLockingScript().isBlank()) {
                String id = recipient.substring(BlockchainConfig.CONTRACT_ADDRESS_PREFIX.length());
                contracts.putIfAbsent(id, new Contract(
                        id, tx.getSender(), tx.getLockingScript(),
                        tx.getAmount(), block.getIndex()));
            }

            String sender = tx.getSender();
            if (sender.startsWith(BlockchainConfig.CONTRACT_ADDRESS_PREFIX)) {
                Contract contract = contracts.get(
                        sender.substring(BlockchainConfig.CONTRACT_ADDRESS_PREFIX.length()));
                if (contract != null && contract.getStatus() == ContractStatus.OPEN) {
                    contract.markClaimed(tx.getRecipient());
                }
            }
        }
    }

    /**
     * Looks up a contract by id.
     *
     * @return The contract, or null if no deploy for this id has been mined
     */
    public Contract getContract(String contractId) {
        return contracts.get(contractId);
    }

    /**
     * @return Unmodifiable view of all contracts derived from the chain
     */
    public List<Contract> getContracts() {
        return Collections.unmodifiableList(new ArrayList<>(contracts.values()));
    }

    /**
     * Return the pending transaction pool.
     *
     * @return Unmodifiable list of pending transactions
     */
    public List<Transaction> getPendingTransactions() {
        return Collections.unmodifiableList(pendingTransactions);
    }

    /**
     * Returns the number of pending transactions.
     * 
     * @return Number of transactions waiting to be mined
     */
    public int getPendingCount() {
        return pendingTransactions.size();
    }

    /**
     * Validates the entire blockchain integrity.
     * 
     * THEORY: Chain validation checks:
     * 1. Genesis block is valid (index=0, previousHash="0")
     * 2. Each block's hash matches its calculated hash
     * 3. Each block's previousHash matches previous block's hash
     * 4. Each block was properly mined (hash meets difficulty)
     * 
     * WHY THIS MATTERS: 
     * - Detects any tampering with block data
     * - Detects broken links between blocks
     * - Ensures Proof-of-Work was performed correctly
     * 
     * @return true if chain is valid, false if tampered
     */
    public boolean isChainValid() {
        // Check Genesis block
        Block genesis = chain.get(0);
        if (genesis.getIndex() != 0) return false;
        if (!genesis.getPreviousHash().equals(BlockchainConfig.GENESIS_PREV_HASH)) return false;

        // Build target string for difficulty check
        String target = "0".repeat(BlockchainConfig.MINING_DIFFICULTY);

        // Check Genesis block hash validity
        if (!genesis.getHash().equals(genesis.calculateHash())) return false;
        if (!genesis.getHash().startsWith(target)) return false;

        // Check rest of chain (from block 1 onwards)
        for (int i = 1; i < chain.size(); i++) {
            Block currentBlock = chain.get(i);
            Block previousBlock = chain.get(i - 1);

            // Verify current block's hash is correctly calculated
            if (!currentBlock.getHash().equals(currentBlock.calculateHash())) return false;

            // Verify link to previous block
            if (!currentBlock.getPreviousHash().equals(previousBlock.getHash())) return false;

            // Verify block was mined (hash meets difficulty)
            if (!currentBlock.getHash().startsWith(target)) return false;
        }

        return true;
    }

    /**
     * Returns an unmodifiable copy of the chain.
     * 
     * THEORY: We return an unmodifiable list to preserve immutability.
     * External code should not be able to directly modify the chain.
     * 
     * @return Unmodifiable view of the blockchain
     */
    public List<Block> getChain() {
        return Collections.unmodifiableList(chain);
    }

    /**
     * Returns the number of blocks in the chain.
     * 
     * @return Chain length (including Genesis block)
     */
    public int getChainSize() {
        return chain.size();
    }

    /**
     * Gets a block by its index.
     * 
     * @param index Block index (0 = Genesis)
     * @return The block at the specified index
     * @throws IndexOutOfBoundsException if index is invalid
     */
    public Block getBlock(int index) {
        return chain.get(index);
    }

    /**
     * Prints a summary of the entire blockchain.
     */
    public void printChain() {
        System.out.println("\n═══════════════════════════════════════════════════════════");
        System.out.println("                     BLOCKCHAIN STATE                        ");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("Chain length: " + chain.size() + " blocks");
        System.out.println("Pending transactions: " + pendingTransactions.size());
        System.out.println("Chain valid: " + (isChainValid() ? "✓ YES" : "✗ NO"));
        System.out.println("───────────────────────────────────────────────────────────");

        for (Block block : chain) {
            System.out.println("Block #" + block.getIndex());
            if (block.getTransactions().size() > 0) {
                System.out.println("  Transactions: " + block.getTransactionCount());
                for (Transaction tx : block.getTransactions()) {
                    System.out.println("    " + tx);
                }
            } else {
                System.out.println("  Data: " + block.getData());
            }            
            System.out.println("  Nonce: " + block.getNonce());
            System.out.println("  Hash: " + block.getHash().substring(0, 16) + "...");
            System.out.println("  Prev: " + block.getPreviousHash().substring(0, Math.min(16, block.getPreviousHash().length())) + "...");
            System.out.println();
        }
    }

    @Override
    public String toString() {
        return "Blockchain{" +
            "chainSize= " + chain.size() +
            ", pendingTx= " + pendingTransactions.size() +
            ", isValid= " + isChainValid() +
            ", latestBlockHash= " + getLatestBlock().getHash().substring(0, 16) + "..." +
            "}";
    }
}