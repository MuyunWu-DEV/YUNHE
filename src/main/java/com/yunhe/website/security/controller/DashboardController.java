package com.yunhe.website.security.controller;

import com.yunhe.website.security.repository.SysPermissionRepository;
import com.yunhe.website.security.repository.SysRoleRepository;
import com.yunhe.website.security.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 仪表盘控制器。
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class DashboardController {

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysPermissionRepository permissionRepository;

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("userCount", userRepository.count());
        model.addAttribute("roleCount", roleRepository.count());
        model.addAttribute("permissionCount", permissionRepository.count());
        return "dashboard";
    }
}
