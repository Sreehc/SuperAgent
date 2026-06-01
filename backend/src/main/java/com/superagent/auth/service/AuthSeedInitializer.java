package com.superagent.auth.service;

import com.superagent.auth.domain.TenantRole;
import com.superagent.auth.repository.TenantMemberRepository;
import com.superagent.auth.repository.TenantRepository;
import com.superagent.auth.repository.UserAccountRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.event.EventListener;

@Component
public class AuthSeedInitializer {

    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthSeedInitializer(
            TenantRepository tenantRepository,
            UserAccountRepository userAccountRepository,
            TenantMemberRepository tenantMemberRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.tenantRepository = tenantRepository;
        this.userAccountRepository = userAccountRepository;
        this.tenantMemberRepository = tenantMemberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedDefaultUsers() {
        long tenantId = tenantRepository.findByCode("default")
                .map(tenant -> tenant.id())
                .orElseGet(() -> tenantRepository.create("默认租户", "default", "active"));

        long adminUserId = userAccountRepository.findByUsername("admin")
                .map(user -> user.id())
                .orElseGet(() -> userAccountRepository.create(
                        "admin",
                        passwordEncoder.encode("password123"),
                        "管理员",
                        "admin@superagent.local",
                        "active"
                ));

        ensureMembership(adminUserId, tenantId, TenantRole.OWNER);
        userAccountRepository.updateDefaultTenantId(adminUserId, tenantId);

        long memberUserId = userAccountRepository.findByUsername("member")
                .map(user -> user.id())
                .orElseGet(() -> userAccountRepository.create(
                        "member",
                        passwordEncoder.encode("password123"),
                        "普通成员",
                        "member@superagent.local",
                        "active"
                ));

        ensureMembership(memberUserId, tenantId, TenantRole.MEMBER);
        userAccountRepository.updateDefaultTenantId(memberUserId, tenantId);
    }

    private void ensureMembership(long userId, long tenantId, TenantRole role) {
        if (tenantMemberRepository.findByUserIdAndTenantId(userId, tenantId).isEmpty()) {
            tenantMemberRepository.create(tenantId, userId, role, "active");
        }
    }
}
