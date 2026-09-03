/**
 * YUNHE 后台 - 登录页脚本
 * 负责 Vanta.js CLOUDS2 动态云背景的初始化；库脚本加载失败时在左下角报红条并回退到 CSS 浅蓝背景。
 */
(function () {
    'use strict';

    function showError(msg) {
        var bar = document.createElement('div');
        bar.style.cssText = 'position:fixed;left:12px;bottom:12px;z-index:9999;background:#dc2626;color:#fff;'
            + 'padding:8px 14px;border-radius:8px;font-size:13px;box-shadow:0 4px 12px rgba(0,0,0,.3)';
        bar.textContent = 'Vanta 加载失败：' + msg;
        document.body.appendChild(bar);
    }

    function startClouds() {
        try {
            if (typeof THREE === 'undefined') {
                return showError('three.js 未加载');
            }
            if (!window.VANTA || typeof VANTA.CLOUDS2 !== 'function') {
                return showError('VANTA.CLOUDS2 未定义');
            }
            VANTA.CLOUDS2({
                el: '.login-page',
                THREE: THREE,
                texturePath: '/images/noise.png',
                // 「明净蔚蓝」配色：与 admin.css 的 .login-page 兜底浅蓝相衔接
                backgroundColor: 0x6fb9ef,
                skyColor: 0x7fc9ff,
                cloudColor: 0x4f7fc4,
                lightColor: 0xffffff,
                speed: 1.0
            });
        } catch (e) {
            showError(e && e.message ? e.message : String(e));
        }
    }

    // 库脚本为同步引入，load 时必已就绪；取 load 确保布局尺寸稳定后再建画布
    if (document.readyState === 'complete') {
        startClouds();
    } else {
        window.addEventListener('load', startClouds);
    }
})();
