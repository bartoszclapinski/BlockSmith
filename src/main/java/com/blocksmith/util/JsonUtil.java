package com.blocksmith.util;

import java.io.IOException;
import java.security.PublicKey;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * Shared Gson configuration for chain data (blocks and transactions).
 *
 * THEORY: Why a custom adapter for public keys
 *
 * A signed Transaction carries the sender's java.security.PublicKey. A plain
 * Gson serializes objects by reflecting over their private fields, but the
 * JDK's EC key class (sun.security.ec.ECPublicKeyImpl) lives in a module that
 * does not open its internals, so reflection fails on Java 17+:
 *
 *   JsonIOException: Failed making field 'sun.security.ec.ECPublicKeyImpl#w'
 *   accessible
 *
 * The fix is the same encoding the rest of the project already uses for keys:
 * the standard X.509 bytes as hex (SignatureUtil.publicKeyToHex). That form is
 * portable, compact and round-trips to an equal key, so a signature still
 * verifies after the transaction is stored and loaded again.
 *
 *   PublicKey  --publicKeyToHex-->  "3059301306072a8648ce3d0201..."
 *   "3059..."  --hexToPublicKey-->  PublicKey (equal to the original)
 */
public final class JsonUtil {

    /** Gson that can (de)serialize blocks and transactions, signed or not. */
    public static final Gson GSON = new GsonBuilder()
            .registerTypeHierarchyAdapter(PublicKey.class, new PublicKeyAdapter().nullSafe())
            .create();

    private JsonUtil() {}

    /** Writes a PublicKey as its hex X.509 encoding and reads it back. */
    private static final class PublicKeyAdapter extends TypeAdapter<PublicKey> {

        @Override
        public void write(JsonWriter out, PublicKey key) throws IOException {
            out.value(SignatureUtil.publicKeyToHex(key));
        }

        @Override
        public PublicKey read(JsonReader in) throws IOException {
            String hex = in.nextString();
            try {
                return SignatureUtil.hexToPublicKey(hex);
            } catch (Exception e) {
                throw new JsonParseException("Invalid public key encoding", e);
            }
        }
    }
}
