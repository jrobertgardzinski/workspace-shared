package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.vo.FactorType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory enrolled-factor repository for the application-level scenarios. */
public final class FakeEnrolledFactorRepository implements EnrolledFactorRepository {

    private final Map<String, List<EnrolledFactor>> byUser = new ConcurrentHashMap<>();

    /** email -> (recovery code hash -> spent?) */
    private final Map<String, Map<String, Boolean>> recoveryCodesByUser = new ConcurrentHashMap<>();

    @Override
    public List<EnrolledFactor> findByUser(Email userEmail) {
        return byUser.getOrDefault(userEmail.value(), List.of()).stream()
                .sorted(Comparator.comparingInt(EnrolledFactor::order))
                .toList();
    }

    @Override
    public void enrol(EnrolledFactor factor) {
        byUser.computeIfAbsent(factor.userEmail().value(), e -> new ArrayList<>());
        List<EnrolledFactor> list = byUser.get(factor.userEmail().value());
        list.removeIf(f -> f.type().equals(factor.type()));
        list.add(factor);
    }

    @Override
    public void remove(Email userEmail, FactorType type) {
        List<EnrolledFactor> list = byUser.get(userEmail.value());
        if (list != null) {
            list.removeIf(f -> f.type().equals(type));
        }
    }

    @Override
    public void removeAll(Email userEmail) {
        byUser.remove(userEmail.value());
    }

    @Override
    public void reassign(Email fromEmail, Email toEmail) {
        // spent flags move with the codes, same as the JDBC adapter
        Map<String, Boolean> movingCodes = recoveryCodesByUser.remove(fromEmail.value());
        if (movingCodes != null) {
            recoveryCodesByUser.put(toEmail.value(), movingCodes);
        }
        List<EnrolledFactor> moving = byUser.remove(fromEmail.value());
        if (moving == null) {
            return;
        }
        byUser.put(toEmail.value(), moving.stream()
                .map(f -> new EnrolledFactor(toEmail, f.type(), f.label(), f.order(),
                        fromEmail.value().equals(f.secretMaterial()) ? toEmail.value() : f.secretMaterial()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
    }

    @Override
    public void replaceRecoveryCodes(Email userEmail, List<String> codeHashes) {
        Map<String, Boolean> fresh = new ConcurrentHashMap<>();
        codeHashes.forEach(hash -> fresh.put(hash, Boolean.FALSE));
        recoveryCodesByUser.put(userEmail.value(), fresh);
    }

    @Override
    public boolean consumeRecoveryCode(Email userEmail, String codeHash) {
        Map<String, Boolean> codes = recoveryCodesByUser.get(userEmail.value());
        // the atomic unused->used flip is the single-use guarantee, same as the JDBC UPDATE
        return codes != null && codes.replace(codeHash, Boolean.FALSE, Boolean.TRUE);
    }

    @Override
    public int unusedRecoveryCodes(Email userEmail) {
        return (int) recoveryCodesByUser.getOrDefault(userEmail.value(), Map.of())
                .values().stream().filter(spent -> !spent).count();
    }

    @Override
    public void purge(Email userEmail) {
        byUser.remove(userEmail.value());
        recoveryCodesByUser.remove(userEmail.value());
    }
}
