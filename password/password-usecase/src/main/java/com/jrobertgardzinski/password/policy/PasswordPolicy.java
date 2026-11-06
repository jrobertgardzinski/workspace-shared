package com.jrobertgardzinski.password.policy;

import com.jrobertgardzinski.password.config.*;

/**
 * The rules a password must satisfy, held together as one value.
 */
public record PasswordPolicy(
        MinLength minLength,
        SpecialChars specialChars,
        RequiresUppercase requiresUppercase,
        RequiresLowercase requiresLowercase,
        RequiresDigit requiresDigit
) {

    public static PasswordPolicy withDefaults() {
        return new PasswordPolicy(
                MinLength.DEFAULT,
                SpecialChars.DEFAULT,
                RequiresUppercase.DEFAULT,
                RequiresLowercase.DEFAULT,
                RequiresDigit.DEFAULT
        );
    }

    /**
     * {@link #withDefaults() The default policy} with these two rules replaced. The other three —
     * uppercase, lowercase, digit — keep their {@code DEFAULT}; the name says so, where a
     * two-argument constructor used to hide it.
     */
    public static PasswordPolicy defaultsExcept(MinLength minLength, SpecialChars specialChars) {
        return new PasswordPolicy(minLength, specialChars,
                RequiresUppercase.DEFAULT, RequiresLowercase.DEFAULT, RequiresDigit.DEFAULT);
    }

}
