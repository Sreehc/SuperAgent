package com.superagent.auth.domain;

public enum TenantRole {
    OWNER,
    ADMIN,
    MEMBER;

    public String authority() {
        return "ROLE_" + name();
    }
}
