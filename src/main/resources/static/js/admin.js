/**
 * YUNHE 后台 - 交互脚本
 * 负责移动端抽屉侧边栏的开关与遮罩，以及侧边栏分组菜单的折叠展开。
 */
(function () {
    'use strict';

    // ===== 移动端抽屉侧边栏 =====
    var sidebar = document.getElementById('sidebar');
    var overlay = document.getElementById('sidebarOverlay');
    var toggle = document.getElementById('menuToggle');

    if (sidebar && overlay && toggle) {
        function openSidebar() {
            sidebar.classList.add('open');
            overlay.classList.add('show');
            document.body.style.overflow = 'hidden';
        }

        function closeSidebar() {
            sidebar.classList.remove('open');
            overlay.classList.remove('show');
            document.body.style.overflow = '';
        }

        toggle.addEventListener('click', function (e) {
            e.stopPropagation();
            if (sidebar.classList.contains('open')) {
                closeSidebar();
            } else {
                openSidebar();
            }
        });

        overlay.addEventListener('click', closeSidebar);

        // 点击导航链接后自动收起（仅移动端）
        sidebar.addEventListener('click', function (e) {
            if (e.target.closest('a.nav-link')) {
                closeSidebar();
            }
        });

        // 窗口回到桌面宽度时复位
        window.addEventListener('resize', function () {
            if (window.innerWidth > 991.98) {
                closeSidebar();
            }
        });

        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') {
                closeSidebar();
            }
        });
    }

    // ===== 侧边栏分组菜单折叠 =====
    var menuGroups = document.querySelectorAll('.menu-group');
    Array.prototype.forEach.call(menuGroups, function (group) {
        var title = group.querySelector('.menu-group-title');

        // 默认展开包含当前激活项的分组
        if (group.querySelector('.nav-link.active')) {
            group.classList.add('open');
            if (title) {
                title.setAttribute('aria-expanded', 'true');
            }
        }

        if (title) {
            title.addEventListener('click', function () {
                var isOpen = group.classList.contains('open');
                group.classList.toggle('open', !isOpen);
                title.setAttribute('aria-expanded', String(!isOpen));
            });
        }
    });

    // ===== 日期选择框：点击输入框任意位置弹出日期选择器 =====
    var dateInputs = document.querySelectorAll('input[type="date"]');
    Array.prototype.forEach.call(dateInputs, function (input) {
        input.addEventListener('click', function () {
            if (typeof input.showPicker === 'function') {
                try {
                    input.showPicker();
                } catch (e) {
                    // 选择器已弹出或浏览器不支持，忽略
                }
            }
        });
    });
})();
