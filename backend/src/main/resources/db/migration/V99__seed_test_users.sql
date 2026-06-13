-- 初始化测试用户数据
-- 密码: password123 (BCrypt 加密)

-- 插入用户
INSERT INTO user_account (username, password_hash, display_name, created_at, updated_at)
VALUES
  ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '管理员', NOW(), NOW()),
  ('member', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '成员', NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

-- 插入租户
INSERT INTO tenant (name, code, status, created_at, updated_at)
VALUES ('默认租户', 'default', 'active', NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- 关联用户与租户
INSERT INTO tenant_member (tenant_id, user_id, role, status, created_at, updated_at)
SELECT t.id, u.id, 'OWNER', 'active', NOW(), NOW()
FROM tenant t, user_account u
WHERE t.code = 'default' AND u.username = 'admin'
ON CONFLICT (tenant_id, user_id) DO NOTHING;

INSERT INTO tenant_member (tenant_id, user_id, role, status, created_at, updated_at)
SELECT t.id, u.id, 'MEMBER', 'active', NOW(), NOW()
FROM tenant t, user_account u
WHERE t.code = 'default' AND u.username = 'member'
ON CONFLICT (tenant_id, user_id) DO NOTHING;
