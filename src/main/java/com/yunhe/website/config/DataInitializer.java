package com.yunhe.website.config;

import com.yunhe.website.security.Permissions;
import com.yunhe.website.security.entity.SysPermission;
import com.yunhe.website.security.entity.SysRole;
import com.yunhe.website.security.entity.SysUser;
import com.yunhe.website.security.repository.SysPermissionRepository;
import com.yunhe.website.security.repository.SysRoleRepository;
import com.yunhe.website.security.repository.SysUserRepository;
import com.yunhe.website.crm.entity.CrmTermsLib;
import com.yunhe.website.crm.repository.CrmTermsLibRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 初始化种子数据：权限、角色、超级管理员账号。
 * <p>所有初始化操作均幂等，可安全重复启动。</p>
 * <p>默认管理员账号的播种由配置 {@code app.security.seed-default-admin} 控制（默认开启，
 * 生产建议关闭并由运维用更安全的方式创建管理员）；初始密码从配置
 * {@code app.security.admin-initial-password} 读取（默认仅用于开发），创建后
 * {@code mustChangePassword=true}，首次登录强制改密，避免默认凭据长期驻留。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    /** 默认超级管理员账号 */
    private static final String ADMIN_USERNAME = "admin";

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysPermissionRepository permissionRepository;
    private final CrmTermsLibRepository termsLibRepository;
    private final PasswordEncoder passwordEncoder;

    /** 是否播种默认 admin 账号（prod 建议关闭） */
    @Value("${app.security.seed-default-admin:true}")
    private boolean seedDefaultAdmin;

    /** 默认 admin 初始密码（仅开发用；播种的账号必须改密） */
    @Value("${app.security.admin-initial-password:Admin@123456}")
    private String adminInitialPassword;

    @Override
    @Transactional
    public void run(String... args) {
        initPermissions();
        initRoles();
        initAdminUser();
        initTermsLib();
    }

    private void initPermissions() {
        createPermissionIfAbsent(Permissions.USER_LIST, Permissions.USER_LIST, "查看用户", "user", "/admin/users", "list", 1);
        createPermissionIfAbsent(Permissions.USER_CREATE, Permissions.USER_CREATE, "新增用户", "user", "/admin/users", "create", 2);
        createPermissionIfAbsent(Permissions.USER_UPDATE, Permissions.USER_UPDATE, "编辑用户", "user", "/admin/users", "update", 3);
        createPermissionIfAbsent(Permissions.USER_DELETE, Permissions.USER_DELETE, "删除用户", "user", "/admin/users", "delete", 4);
        createPermissionIfAbsent(Permissions.ROLE_LIST, Permissions.ROLE_LIST, "查看角色", "role", "/admin/roles", "list", 5);
        createPermissionIfAbsent(Permissions.ROLE_CREATE, Permissions.ROLE_CREATE, "新增角色", "role", "/admin/roles", "create", 6);
        createPermissionIfAbsent(Permissions.ROLE_UPDATE, Permissions.ROLE_UPDATE, "编辑角色", "role", "/admin/roles", "update", 7);
        createPermissionIfAbsent(Permissions.ROLE_DELETE, Permissions.ROLE_DELETE, "删除角色", "role", "/admin/roles", "delete", 8);
        createPermissionIfAbsent(Permissions.PERMISSION_LIST, Permissions.PERMISSION_LIST, "查看权限", "permission", "/admin/permissions", "list", 9);
        createPermissionIfAbsent(Permissions.PERMISSION_CREATE, Permissions.PERMISSION_CREATE, "新增权限", "permission", "/admin/permissions", "create", 10);
        createPermissionIfAbsent(Permissions.PERMISSION_UPDATE, Permissions.PERMISSION_UPDATE, "编辑权限", "permission", "/admin/permissions", "update", 11);
        createPermissionIfAbsent(Permissions.PERMISSION_DELETE, Permissions.PERMISSION_DELETE, "删除权限", "permission", "/admin/permissions", "delete", 12);
        createPermissionIfAbsent(Permissions.CUSTOMER_LIST, Permissions.CUSTOMER_LIST, "查看客户", "customer", "/crm/customers", "list", 13);
        createPermissionIfAbsent(Permissions.CUSTOMER_CREATE, Permissions.CUSTOMER_CREATE, "新增客户", "customer", "/crm/customers", "create", 14);
        createPermissionIfAbsent(Permissions.CUSTOMER_UPDATE, Permissions.CUSTOMER_UPDATE, "编辑客户", "customer", "/crm/customers", "update", 15);
        createPermissionIfAbsent(Permissions.CUSTOMER_DELETE, Permissions.CUSTOMER_DELETE, "删除客户", "customer", "/crm/customers", "delete", 16);
        createPermissionIfAbsent(Permissions.QUOTATION_LIST, Permissions.QUOTATION_LIST, "查看报价", "quotation", "/crm/quotations", "list", 17);
        createPermissionIfAbsent(Permissions.QUOTATION_CREATE, Permissions.QUOTATION_CREATE, "新增报价", "quotation", "/crm/quotations", "create", 18);
        createPermissionIfAbsent(Permissions.QUOTATION_UPDATE, Permissions.QUOTATION_UPDATE, "编辑报价", "quotation", "/crm/quotations", "update", 19);
        createPermissionIfAbsent(Permissions.QUOTATION_DELETE, Permissions.QUOTATION_DELETE, "删除报价", "quotation", "/crm/quotations", "delete", 20);
        createPermissionIfAbsent(Permissions.PROFORMA_INVOICE_LIST, Permissions.PROFORMA_INVOICE_LIST, "查看形式发票", "proforma-invoice", "/crm/proforma-invoices", "list", 21);
        createPermissionIfAbsent(Permissions.PROFORMA_INVOICE_CREATE, Permissions.PROFORMA_INVOICE_CREATE, "新增形式发票", "proforma-invoice", "/crm/proforma-invoices", "create", 22);
        createPermissionIfAbsent(Permissions.PROFORMA_INVOICE_UPDATE, Permissions.PROFORMA_INVOICE_UPDATE, "编辑形式发票", "proforma-invoice", "/crm/proforma-invoices", "update", 23);
        createPermissionIfAbsent(Permissions.PROFORMA_INVOICE_DELETE, Permissions.PROFORMA_INVOICE_DELETE, "删除形式发票", "proforma-invoice", "/crm/proforma-invoices", "delete", 24);
        createPermissionIfAbsent(Permissions.SALES_ORDER_LIST, Permissions.SALES_ORDER_LIST, "查看订单", "sales-order", "/crm/sales-orders", "list", 26);
        createPermissionIfAbsent(Permissions.SALES_ORDER_CREATE, Permissions.SALES_ORDER_CREATE, "新增订单", "sales-order", "/crm/sales-orders", "create", 27);
        createPermissionIfAbsent(Permissions.SALES_ORDER_UPDATE, Permissions.SALES_ORDER_UPDATE, "编辑订单", "sales-order", "/crm/sales-orders", "update", 28);
        createPermissionIfAbsent(Permissions.SALES_ORDER_DELETE, Permissions.SALES_ORDER_DELETE, "删除订单", "sales-order", "/crm/sales-orders", "delete", 29);
        createPermissionIfAbsent(Permissions.COMMERCIAL_INVOICE_LIST, Permissions.COMMERCIAL_INVOICE_LIST, "查看商业发票", "commercial-invoice", "/crm/commercial-invoices", "list", 30);
        createPermissionIfAbsent(Permissions.COMMERCIAL_INVOICE_CREATE, Permissions.COMMERCIAL_INVOICE_CREATE, "新增商业发票", "commercial-invoice", "/crm/commercial-invoices", "create", 31);
        createPermissionIfAbsent(Permissions.COMMERCIAL_INVOICE_UPDATE, Permissions.COMMERCIAL_INVOICE_UPDATE, "编辑商业发票", "commercial-invoice", "/crm/commercial-invoices", "update", 32);
        createPermissionIfAbsent(Permissions.COMMERCIAL_INVOICE_DELETE, Permissions.COMMERCIAL_INVOICE_DELETE, "删除商业发票", "commercial-invoice", "/crm/commercial-invoices", "delete", 33);
        createPermissionIfAbsent(Permissions.PACKING_LIST_LIST, Permissions.PACKING_LIST_LIST, "查看装箱单", "packing-list", "/crm/packing-lists", "list", 34);
        createPermissionIfAbsent(Permissions.PACKING_LIST_CREATE, Permissions.PACKING_LIST_CREATE, "新增装箱单", "packing-list", "/crm/packing-lists", "create", 35);
        createPermissionIfAbsent(Permissions.PACKING_LIST_UPDATE, Permissions.PACKING_LIST_UPDATE, "编辑装箱单", "packing-list", "/crm/packing-lists", "update", 36);
        createPermissionIfAbsent(Permissions.PACKING_LIST_DELETE, Permissions.PACKING_LIST_DELETE, "删除装箱单", "packing-list", "/crm/packing-lists", "delete", 37);
        createPermissionIfAbsent(Permissions.TERMS_LIB_LIST, Permissions.TERMS_LIB_LIST, "查看条款库", "terms-lib", "/crm/terms-lib", "list", 38);
        createPermissionIfAbsent(Permissions.TERMS_LIB_CREATE, Permissions.TERMS_LIB_CREATE, "新增条款库", "terms-lib", "/crm/terms-lib", "create", 39);
        createPermissionIfAbsent(Permissions.TERMS_LIB_UPDATE, Permissions.TERMS_LIB_UPDATE, "编辑条款库", "terms-lib", "/crm/terms-lib", "update", 40);
        createPermissionIfAbsent(Permissions.TERMS_LIB_DELETE, Permissions.TERMS_LIB_DELETE, "删除条款库", "terms-lib", "/crm/terms-lib", "delete", 41);
    }

    private void initRoles() {
        List<SysPermission> all = permissionRepository.findAllByOrderByModuleAscSortOrderAsc();

        SysRole superAdmin = createRoleIfAbsent("SUPER_ADMIN", "Super Admin", "超级管理员",
                "拥有系统全部权限", true, 1);
        // 超级管理员始终拥有全部权限
        superAdmin.getPermissions().addAll(all);

        SysRole admin = createRoleIfAbsent("ADMIN", "Admin", "管理员",
                "拥有用户、角色的日常管理权限", true, 2);
        admin.getPermissions().addAll(filterPermissions(all,
                Permissions.USER_LIST, Permissions.USER_CREATE, Permissions.USER_UPDATE,
                Permissions.ROLE_LIST, Permissions.ROLE_CREATE, Permissions.ROLE_UPDATE,
                Permissions.PERMISSION_LIST,
                Permissions.CUSTOMER_LIST, Permissions.CUSTOMER_CREATE, Permissions.CUSTOMER_UPDATE, Permissions.CUSTOMER_DELETE,
                Permissions.QUOTATION_LIST, Permissions.QUOTATION_CREATE, Permissions.QUOTATION_UPDATE, Permissions.QUOTATION_DELETE,
                Permissions.PROFORMA_INVOICE_LIST, Permissions.PROFORMA_INVOICE_CREATE, Permissions.PROFORMA_INVOICE_UPDATE, Permissions.PROFORMA_INVOICE_DELETE,
                Permissions.SALES_ORDER_LIST, Permissions.SALES_ORDER_CREATE, Permissions.SALES_ORDER_UPDATE, Permissions.SALES_ORDER_DELETE,
                Permissions.COMMERCIAL_INVOICE_LIST, Permissions.COMMERCIAL_INVOICE_CREATE, Permissions.COMMERCIAL_INVOICE_UPDATE, Permissions.COMMERCIAL_INVOICE_DELETE,
                Permissions.PACKING_LIST_LIST, Permissions.PACKING_LIST_CREATE, Permissions.PACKING_LIST_UPDATE, Permissions.PACKING_LIST_DELETE,
                Permissions.TERMS_LIB_LIST, Permissions.TERMS_LIB_CREATE, Permissions.TERMS_LIB_UPDATE, Permissions.TERMS_LIB_DELETE));

        createRoleIfAbsent("USER", "User", "普通用户", "仅可登录查看", true, 3);
    }

    private void initAdminUser() {
        // S3 密码治理：默认 admin 播种默认关闭于生产（seed-default-admin:false），避免自动建弱凭据账号
        if (!seedDefaultAdmin) {
            return;
        }
        if (userRepository.existsByUsername(ADMIN_USERNAME)) {
            return;
        }
        SysRole superAdmin = roleRepository.findByCode(SysRole.CODE_SUPER_ADMIN)
                .orElseThrow(() -> new IllegalStateException("SUPER_ADMIN 角色未初始化"));
        SysUser admin = new SysUser();
        admin.setUsername(ADMIN_USERNAME);
        admin.setPassword(passwordEncoder.encode(adminInitialPassword));
        admin.setFullName("系统管理员");
        admin.setEnabled(true);
        // 首次登录强制改密，杜绝默认凭据长期驻留（配合 AccountStatusFilter 的强制改密跳转）
        admin.setMustChangePassword(true);
        admin.setRoles(new LinkedHashSet<>(Set.of(superAdmin)));
        userRepository.save(admin);
        // 日志去敏感：绝不打明文密码，只打用户名并提示须改密
        log.info("已创建默认管理员账号：{}（首次登录将强制修改密码）", ADMIN_USERNAME);
    }

    /** 初始化条款库单例记录（存在则跳过） */
    private void initTermsLib() {
        if (termsLibRepository.findFirstByOrderByIdAsc().isPresent()) {
            return;
        }
        termsLibRepository.save(new CrmTermsLib());
    }

    private void createPermissionIfAbsent(String code, String name, String nameZh,
                                          String module, String resource, String action, int sortOrder) {
        if (permissionRepository.existsByCode(code)) {
            return;
        }
        SysPermission permission = new SysPermission();
        permission.setCode(code);
        permission.setName(name);
        permission.setNameZh(nameZh);
        permission.setModule(module);
        permission.setResource(resource);
        permission.setAction(action);
        permission.setSortOrder(sortOrder);
        permissionRepository.save(permission);
    }

    private SysRole createRoleIfAbsent(String code, String name, String nameZh,
                                       String description, boolean builtIn, int sortOrder) {
        return roleRepository.findByCode(code).orElseGet(() -> {
            SysRole role = new SysRole();
            role.setCode(code);
            role.setName(name);
            role.setNameZh(nameZh);
            role.setDescription(description);
            role.setBuiltIn(builtIn);
            role.setEnabled(true);
            role.setSortOrder(sortOrder);
            return roleRepository.save(role);
        });
    }

    private Set<SysPermission> filterPermissions(List<SysPermission> all, String... codes) {
        Set<String> wanted = Set.of(codes);
        Set<SysPermission> result = new LinkedHashSet<>();
        for (SysPermission p : all) {
            if (wanted.contains(p.getCode())) {
                result.add(p);
            }
        }
        return result;
    }
}
