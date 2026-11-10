package com.blocksmith.storage;

/**
 * A storage operation failed (database unavailable, disk full, bad row...).
 *
 * Unchecked on purpose: callers in the chain logic cannot do anything useful
 * with a half-written block except stop, and wrapping SQLException keeps the
 * JDBC details inside the storage package.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
