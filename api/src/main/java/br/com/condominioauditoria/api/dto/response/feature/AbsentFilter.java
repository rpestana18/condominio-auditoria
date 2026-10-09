package br.com.condominioauditoria.api.dto.response.feature;

/**
 * Marks a JSON field as "absent" (different from null): Jackson skips the field when the value is this instance.
 * Compared by identity, on purpose.
 */
public final class AbsentFilter {

    @SuppressWarnings("StringOperationCanBeSimplified")
    public static final String ABSENT = new String("ausente");

    @Override
    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    public boolean equals(Object other) {
        return other == ABSENT;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
