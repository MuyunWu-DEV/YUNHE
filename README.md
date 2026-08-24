# YUNHE 后台管理系统

基于 **Spring Boot 3 + Spring Data JPA + Spring Security + Thymeleaf** 的外贸后台管理系统，包含登录、用户/角色/权限管理（RBAC），以及「报价单 → Proforma Invoice → 销售订单 / 商业发票 / 装箱单」外贸单据链管理。

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 基础框架 | Spring Boot 3.3.x（Java 21） |
| 数据访问 | Spring Data JPA + MySQL 8（测试用 H2） |
| 安全 | Spring Security（Session 表单登录 + 方法级授权） |
| 模板引擎 | Thymeleaf（服务端渲染，**SEO 友好**，便于将来扩展企业官网） |
| 对象映射 | MapStruct 1.6.3（实体 ↔ DTO） |
| 其他 | Lombok、Bean Validation、Bootstrap 5 |

## 项目结构

```
com.yunhe.website
├── common                  # 通用层：统一响应、异常、基类、工具
│   ├── base/BaseEntity     # 审计基类（id/createdAt/updatedAt）
│   ├── result/Result       # 统一响应体
│   ├── exception/          # BusinessException、GlobalExceptionHandler
│   ├── validation/         # ReviseGroup 校验分组
│   ├── web/PageUtil        # 分页参数与页码工具
│   └── sequence/           # SysSequence + SequenceStore（通用序号取号）
├── config                  # SecurityConfig、DataInitializer、I18nConfig、WebContextAdvice
├── security                # 权限模块（RBAC）
│   ├── Permissions         # 权限码常量集（唯一事实来源）
│   ├── entity              # SysUser / SysRole / SysPermission（多对多）
│   ├── repository          # JPA 仓储
│   ├── dto                 # 响应 DTO（record）+ 表单 DTO（校验）
│   ├── mapper              # MapStruct 映射器
│   ├── service             # 业务服务 + 认证服务
│   ├── auth                # 自定义用户详情、登录处理器
│   └── controller          # 控制器
├── crm                     # 核心业务：客户 / 报价单 / PI / 订单 / CI / PL 单据链
│   ├── entity              # 领域实体与状态枚举
│   ├── repository          # JPA 仓储
│   ├── dto                 # 响应 DTO + 表单 DTO
│   ├── mapper              # MapStruct 映射器
│   ├── service             # 业务服务（含单据链、变更传播、版本快照）
│   ├── controller          # 控制器
│   └── support             # DocNumberGenerator（单据号格式化）
└── system                  # SystemSettings（系统设置：Seller/Incoterms/Terms/Bank）
```

## 快速开始

1. 本地启动 MySQL，创建数据库：
   ```sql
   CREATE DATABASE yunhe DEFAULT CHARACTER SET utf8mb4;
   ```
2. 修改 `src/main/resources/application-dev.yml` 中的数据库账号密码
   （或通过环境变量 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` 覆盖）。
3. 启动应用：
   ```bash
   mvn spring-boot:run
   ```
4. 访问 http://localhost:8080/login

## 默认账号

| 账号 | 密码 | 说明 |
| --- | --- | --- |
| admin | Admin@123456 | 超级管理员（登录后请及时修改密码） |

首次启动会自动初始化权限、角色（SUPER_ADMIN / ADMIN / USER）及管理员账号。

## 权限模型

- 用户 ↔ 角色：多对多（`sys_user_role`）
- 角色 ↔ 权限：多对多（`sys_role_permission`）
- 权限编码示例：`user:list`、`user:create`、`role:delete` 等（集中在 `security/Permissions` 常量类）
- 通过 `@PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).USER_CREATE)")` 做方法级按钮/接口级授权
- 页面通过 `sec:authorize` 控制菜单与按钮的显隐

## SEO 说明

后台管理页与未来企业官网统一采用 Thymeleaf 服务端渲染，输出完整 HTML，
对 Google 等搜索引擎友好。将来开放官网页面时，只需在 `SecurityConfig` 中将
对应公开路径加入 `permitAll()` 即可。
