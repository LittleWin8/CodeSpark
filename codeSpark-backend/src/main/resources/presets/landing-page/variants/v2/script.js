(function () {
      'use strict';

      /* ---------- 1. 导航栏滚动状态 ---------- */
      var header = document.getElementById('siteHeader');
      function handleScroll() {
        header.classList.toggle('scrolled', window.scrollY > 24);
      }
      handleScroll();
      window.addEventListener('scroll', handleScroll, { passive: true });

      /* ---------- 2. 移动端菜单开合 ---------- */
      var menuToggle = document.getElementById('menuToggle');
      var navLinks = document.getElementById('navLinks');

      function closeMenu() {
        navLinks.classList.remove('open');
        menuToggle.classList.remove('active');
        menuToggle.setAttribute('aria-expanded', 'false');
        menuToggle.setAttribute('aria-label', '打开导航菜单');
      }

      menuToggle.addEventListener('click', function () {
        var isOpen = navLinks.classList.toggle('open');
        menuToggle.classList.toggle('active', isOpen);
        menuToggle.setAttribute('aria-expanded', String(isOpen));
        menuToggle.setAttribute('aria-label', isOpen ? '关闭导航菜单' : '打开导航菜单');
      });

      // 点击菜单内的链接后自动收起
      navLinks.addEventListener('click', function (e) {
        if (e.target.tagName === 'A') closeMenu();
      });

      // 桌面端尺寸变化时重置菜单
      window.addEventListener('resize', function () {
        if (window.innerWidth > 860) closeMenu();
      });

      /* ---------- 3. 滚动入场动画 ---------- */
      var revealItems = document.querySelectorAll('.reveal');

      // 特色卡片依次延迟出现，形成错落感
      document.querySelectorAll('.feature-grid').forEach(function (grid) {
        Array.prototype.forEach.call(grid.children, function (card, index) {
          card.style.transitionDelay = (index * 90) + 'ms';
        });
      });

      if ('IntersectionObserver' in window) {
        var observer = new IntersectionObserver(function (entries, obs) {
          entries.forEach(function (entry) {
            if (entry.isIntersecting) {
              entry.target.classList.add('visible');
              obs.unobserve(entry.target);
            }
          });
        }, { threshold: 0.15, rootMargin: '0px 0px -60px 0px' });

        revealItems.forEach(function (el) { observer.observe(el); });
      } else {
        // 降级处理：直接显示
        revealItems.forEach(function (el) { el.classList.add('visible'); });
      }

      /* ---------- 4. 会员订阅表单 ---------- */
      var form = document.getElementById('ctaForm');
      var msg = document.getElementById('formMsg');
      var emailInput = document.getElementById('email');
      var EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

      form.addEventListener('submit', function (e) {
        e.preventDefault();
        var value = emailInput.value.trim();

        if (!EMAIL_RE.test(value)) {
          msg.textContent = '请输入有效的邮箱地址，例如 hello@example.com';
          msg.className = 'form-msg error';
          emailInput.focus();
          return;
        }

        msg.textContent = '订阅成功！每周二的豆单会发送到 ' + value;
        msg.className = 'form-msg success';
        form.reset();
      });

      // 用户重新输入时清空提示
      emailInput.addEventListener('input', function () {
        if (msg.textContent) {
          msg.textContent = '';
          msg.className = 'form-msg';
        }
      });
    })();
