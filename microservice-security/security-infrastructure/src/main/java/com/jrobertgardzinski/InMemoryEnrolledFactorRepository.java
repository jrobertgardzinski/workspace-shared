package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * In-memory {@link EnrolledFactorRepository} used when no database is configured (tests). The JDBC
 * adapter takes over once a datasource is present. Keyed by the string value of the email.
 */
@Singleton
@Requires(missingBeans = DataSource.class)
public final class InMemoryEnrolledFactorRepository implements EnrolledFactorRepository {

    private final Map<String, List<EnrolledFactor>> byUser = new ConcurrentHashMap<>();

    /** email -> (recovery code hash -> spent?) */
    private final Map<String, Map<String, Boolean>> recoveryCodesByUser = new ConcurrentHashMap<>();

    @Override
    public List<EnrolledFactor> findByUser(Email userEmail) {
        return byUser.getOrDefault(userEmail.value(), List.of()).stream()
                .sorted(Comparator.comparingInt(EnrolledFactor::order))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public void enrol(EnrolledFactor factor) {
        byUser.compute(factor.userEmail().value(), (email, existing) -> {
            List<EnrolledFactor> list = existing == null ? new CopyOnWriteArrayList<>() : existing;
            list.removeIf(f -> f.type().equals(factor.type()));   // replace same type
            list.add(factor);
            return list;
        });
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

    /** Mirrors the JDBC adapter, target re-mapping included — the dubles must not diverge. */
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
                .collect(Collectors.toCollection(CopyOnWriteArrayList::new)));
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
