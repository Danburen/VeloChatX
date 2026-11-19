package org.nitrowater.velochatx.testsupport;

import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.spi.PermissionService;

import java.util.Optional;

/** Fixed-value {@link PermissionService}. */
public final class FakePermissionService implements PermissionService {

    private String prefix = "";
    private String suffix = "";
    private String group = "";
    private String groupDisplay;

    public FakePermissionService prefix(String prefix) {
        this.prefix = prefix;
        return this;
    }

    public FakePermissionService suffix(String suffix) {
        this.suffix = suffix;
        return this;
    }

    public FakePermissionService group(String group) {
        this.group = group;
        return this;
    }

    public FakePermissionService groupDisplay(String groupDisplay) {
        this.groupDisplay = groupDisplay;
        return this;
    }

    @Override
    public Optional<String> getPrefix(UPlayer player) {
        return blankToEmpty(prefix);
    }

    @Override
    public Optional<String> getSuffix(UPlayer player) {
        return blankToEmpty(suffix);
    }

    @Override
    public Optional<String> getGroup(UPlayer player) {
        return blankToEmpty(group);
    }

    @Override
    public Optional<String> getGroupDisplay(UPlayer player) {
        return blankToEmpty(groupDisplay);
    }

    private static Optional<String> blankToEmpty(String value) {
        return value == null || value.isEmpty() ? Optional.empty() : Optional.of(value);
    }
}
