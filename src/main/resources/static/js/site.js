// 云合官网 · 公开站点交互（头部滚动 / 移动端菜单 / 首页轮播）
(function () {
  'use strict';

  // 头部滚动变实底
  var hdr = document.getElementById('hdr');
  if (hdr) {
    var onScroll = function () {
      hdr.classList.toggle('scrolled', window.scrollY > 40);
    };
    window.addEventListener('scroll', onScroll);
    onScroll();
  }

  // 悬浮全宽导航白块（特斯拉式 · 按项切换 + 选中反馈）
  var nav = document.getElementById('nav');
  var mm = document.getElementById('megamenu');
  var burger = document.getElementById('burger');
  if (hdr && mm) {
    var navItems = nav ? nav.querySelectorAll('.nav-item') : [];
    var panels = mm.querySelectorAll('.mm-panel');
    var closeTimer = null;

    var setPanel = function (key) {
      panels.forEach(function (p) { p.classList.toggle('active', p.getAttribute('data-panel') === key); });
      navItems.forEach(function (n) { n.classList.toggle('active', n.getAttribute('data-mm') === key); });
    };
    var clearPanel = function () {
      panels.forEach(function (p) { p.classList.remove('active'); });
      navItems.forEach(function (n) { n.classList.remove('active'); });
    };
    var openMM = function () {
      clearTimeout(closeTimer);
      hdr.classList.add('nav-hover');
      mm.classList.add('open');
    };
    var scheduleClose = function () {
      clearTimeout(closeTimer);
      closeTimer = setTimeout(function () {
        hdr.classList.remove('nav-hover');
        mm.classList.remove('open');
        clearPanel();
      }, 160);
    };
    var closeMMNow = function () {
      clearTimeout(closeTimer);
      hdr.classList.remove('nav-hover');
      mm.classList.remove('open');
      if (burger) burger.classList.remove('open');
      clearPanel();
    };

    // 桌面：悬浮具体导航项 -> 展开其对应面板 + 选中反馈
    navItems.forEach(function (item) {
      item.addEventListener('mouseenter', function () {
        setPanel(item.getAttribute('data-mm'));
        openMM();
      });
      item.addEventListener('click', closeMMNow);
    });
    // 头部 / 白块范围内保持展开，离开延迟收起
    hdr.addEventListener('mouseleave', scheduleClose);
    mm.addEventListener('mouseenter', openMM);
    mm.addEventListener('mouseleave', scheduleClose);

    // 点击白块内链接：立即收起
    mm.querySelectorAll('a').forEach(function (a) {
      a.addEventListener('click', closeMMNow);
    });
  }

  // 移动端汉堡：展开 / 收起全宽导航白块
  if (burger && mm && hdr) {
    burger.addEventListener('click', function () {
      var open = mm.classList.toggle('open');
      burger.classList.toggle('open', open);
    });
  }

  // 可复用的横滑轮播组件（自动播 5s，hover/focus 暂停，圆点/箭头切换 + scroll-snap 同步）
  // 用法：createCarousel({ stage, dots, prev, next })，支持一页多实例互不干扰
  function createCarousel(cfg) {
    var stage = cfg.stage;
    var dots = cfg.dots || [];
    if (!stage || !dots.length) return;
    var cur = 0, total = dots.length, autoTimer = null;
    var show = function (n) {
      cur = (n + total) % total;
      stage.scrollTo({ left: stage.clientWidth * cur, behavior: 'smooth' });
      dots.forEach(function (d, k) { d.classList.toggle('active', k === cur); });
    };
    var startAuto = function () { if (!autoTimer) autoTimer = setInterval(function () { show(cur + 1); }, 5000); };
    var stopAuto = function () { if (autoTimer) { clearInterval(autoTimer); autoTimer = null; } };
    var restartAuto = function () { stopAuto(); startAuto(); };
    if (cfg.prev) cfg.prev.addEventListener('click', function () { show(cur - 1); restartAuto(); });
    if (cfg.next) cfg.next.addEventListener('click', function () { show(cur + 1); restartAuto(); });
    dots.forEach(function (d, k) { d.addEventListener('click', function () { show(k); restartAuto(); }); });
    stage.addEventListener('scroll', function () {
      var w = stage.clientWidth;
      if (!w) return;
      var ni = Math.round(stage.scrollLeft / w);
      if (ni !== cur) { cur = ni; dots.forEach(function (d, k2) { d.classList.toggle('active', k2 === cur); }); }
    });
    startAuto();
    stage.addEventListener('mouseenter', stopAuto);
    stage.addEventListener('mouseleave', startAuto);
    stage.addEventListener('focusin', stopAuto);
    stage.addEventListener('focusout', startAuto);
    document.addEventListener('visibilitychange', function () {
      if (document.hidden) stopAuto(); else startAuto();
    });
  }

  // 首页首屏 hero 轮播
  var heroStage = document.getElementById('heroStage');
  if (heroStage) {
    createCarousel({
      stage: heroStage,
      dots: Array.prototype.slice.call(document.querySelectorAll('#heroDots .dot')),
      prev: document.getElementById('heroPrev'),
      next: document.getElementById('heroNext')
    });
  }

  // About(Group Overview) 顶部全宽轮播
  var aboutStage = document.getElementById('aboutStage');
  if (aboutStage) {
    createCarousel({
      stage: aboutStage,
      dots: Array.prototype.slice.call(document.querySelectorAll('#aboutDots .dot')),
      prev: document.getElementById('aboutPrev'),
      next: document.getElementById('aboutNext')
    });
  }

  // Contact(/about/contact) 顶部全宽轮播
  var contactStage = document.getElementById('contactStage');
  if (contactStage) {
    createCarousel({
      stage: contactStage,
      dots: Array.prototype.slice.call(document.querySelectorAll('#contactDots .dot')),
      prev: document.getElementById('contactPrev'),
      next: document.getElementById('contactNext')
    });
  }

  // References(/references) 顶部全宽轮播
  var refStage = document.getElementById('refStage');
  if (refStage) {
    createCarousel({
      stage: refStage,
      dots: Array.prototype.slice.call(document.querySelectorAll('#refDots .dot')),
      prev: document.getElementById('refPrev'),
      next: document.getElementById('refNext')
    });
  }
})();
