package org.openl.rules.security;

/**
 * Constants for all Privileges.
 *
 * @author Aleh Bykhavets
 * @author NSamatov
 */
public enum Privileges implements Privilege {

    ADMIN("Administrate");

    private final String displayName;

    Privileges(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return name();
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getAuthority() {
        return name();
    }

}
