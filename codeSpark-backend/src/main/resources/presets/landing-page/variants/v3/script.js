(function () {
      'use strict';

      /* ---------- 1. 滚动时给导航加背景 ---------- */
      const header = document.getElementById('siteHeader');
      function handleScroll() {
        header.classList.toggle('scrolled', window.scrollY > 20);
      }
      window.addEventListener('scroll', handleScroll, { passive: true });
      handleScroll();

      /* ---------- 2. 移动端菜单开关 ---------- */
      const navToggle = document.getElementById('navToggle');
      const navLinks  = document.getElementById('navLinks');

      function closeMenu() {
        navLinks.classList.remove('open');
        navToggle.classList.remove('active');
        header.classList.remove('menu-open');
        navToggle.setAttribute('aria-expanded', 'false');
        navToggle.setAttribute('aria-label', '打开菜单');
      }

      navToggle.addEventListener('click', function () {
        const isOpen = navLinks.classList.toggle('open');
        navToggle.classList.toggle('active', isOpen);
        header.classList.toggle('menu-open', isOpen);
        navToggle.setAttribute('aria-expanded', String(isOpen));
        navToggle.setAttribute('aria-label', isOpen ? '关闭菜单' : '打开菜单');
      });

      // 点击导航链接后自动收起菜单
      navLinks.querySelectorAll('a').forEach(function (link) {
        link.addEventListener('click', closeMenu);
      });

      // 点击页面其他区域收起菜单
      document.addEventListener('click', function (e) {
        if (!navLinks.classList.contains('open')) return;
        if (!header.contains(e.target)) closeMenu();
      });

      /* ---------- 3. 滚动入场动画 ---------- */
      const revealEls = document.querySelectorAll('.reveal');

      // 为网格中的卡片设置错落的延迟
      document.querySelectorAll('.features-grid, .gallery-grid').forEach(function (group) {
        Array.prototype.forEach.call(group.children, function (child, index) {
          child.style.transitionDelay = (index * 90) + 'ms';
        });
      });

      if ('IntersectionObserver' in window) {
        const observer = new IntersectionObserver(function (entries) {
          entries.forEach(function (entry) {
            if (entry.isIntersecting) {
              entry.target.classList.add('visible');
              observer.unobserve(entry.target);
            }
          });
        }, { threshold: 0.15, rootMargin: '0px 0px -40px 0px' });

        revealEls.forEach(function (el) { observer.observe(el); });
      } else {
        // 不支持时直接显示
        revealEls.forEach(function (el) { el.classList.add('visible'); });
      }

      /* ---------- 4. 订阅表单（纯前端校验与提示） ---------- */
      const form  = document.getElementById('subscribeForm');
      const msgEl = document.getElementById('formMsg');
      const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

      form.addEventListener('submit', function (e) {
        e.preventDefault();
        const input = form.querySelector('input');
        const email = input.value.trim();

        if (!EMAIL_RE.test(email)) {
          msgEl.style.color = '#E8A98F';
          msgEl.textContent = '请输入有效的邮箱地址，例如 name@example.com';
          input.focus();
          return;
        }

        msgEl.style.color = '#A8BF86';
        msgEl.textContent = '订阅成功！最新的豆单会发送到 ' + email + '。';
        form.reset();
      });
    })();
