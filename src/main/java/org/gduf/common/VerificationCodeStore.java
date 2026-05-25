package org.gduf.common;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VerificationCodeStore {

    private final ConcurrentHashMap<String, CodeEntry> store = new ConcurrentHashMap<>();

    public void put(String email, String code, long expireMs) {
        store.put(email.toLowerCase(), new CodeEntry(code, System.currentTimeMillis() + expireMs));
    }

    public boolean verify(String email, String code) {
        CodeEntry entry = store.get(email.toLowerCase());
        if (entry == null) return false;
        if (System.currentTimeMillis() > entry.expiresAt) {
            store.remove(email.toLowerCase());
            return false;
        }
        if (entry.code.equals(code)) {
            store.remove(email.toLowerCase());
            return true;
        }
        return false;
    }

    public boolean canResend(String email, long cooldownSeconds) {
        CodeEntry entry = store.get(email.toLowerCase());
        if (entry == null) return true;
        return (System.currentTimeMillis() - entry.createdAt) > cooldownSeconds * 1000;
    }

    private static class CodeEntry {
        final String code;
        final long createdAt;
        final long expiresAt;
        CodeEntry(String code, long expiresAt) {
            this.code = code;
            this.createdAt = System.currentTimeMillis();
            this.expiresAt = expiresAt;
        }
    }
}
