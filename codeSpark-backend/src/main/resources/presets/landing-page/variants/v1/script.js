/* ============================================================
   1. 导航栏滚动样式
   ============================================================ */
(function () {
  const nav = document.getElementById('nav');
  const toTop = document.getElementById('toTop');

  function handleScroll() {
    const y = window.scrollY || document.documentElement.scrollTop;
    nav.classList.toggle('scrolled', y > 20);
    toTop.classList.toggle('show', y > 600);
  }

  handleScroll();
  window.addEventListener('scroll', handleScroll, { passive: true });

  toTop.addEventListener('click', function () {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  });
})();

/* ============================================================
   2. 移动端菜单
   ============================================================ */
(function () {
  const burger = document.getElementById('burger');
  const menu = document.getElementById('mobileMenu');

  function closeMenu() {
    menu.classList.remove('open');
    burger.setAttribute('aria-expanded', 'false');
    document.body.style.overflow = '';
  }

  burger.addEventListener('click', function () {
    const isOpen = menu.classList.toggle('open');
    burger.setAttribute('aria-expanded', String(isOpen));
    document.body.style.overflow = isOpen ? 'hidden' : '';
  });

  // 点击菜单内链接后自动关闭
  menu.querySelectorAll('a').forEach(function (link) {
    link.addEventListener('click', closeMenu);
  });

  // 视口变大时重置
  window.addEventListener('resize', function () {
    if (window.innerWidth > 880) closeMenu();
  });

  // ESC 关闭
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') closeMenu();
  });
})();

/* ============================================================
   3. 滚动淡入动画
   ============================================================ */
(function () {
  const items = document.querySelectorAll('.reveal');

  if (!('IntersectionObserver' in window)) {
    items.forEach(function (el) { el.classList.add('in'); });
    return;
  }

  const observer = new IntersectionObserver(function (entries) {
    entries.forEach(function (entry) {
      if (entry.isIntersecting) {
        entry.target.classList.add('in');
        observer.unobserve(entry.target);
      }
    });
  }, { threshold: 0.14, rootMargin: '0px 0px -60px 0px' });

  items.forEach(function (el) { observer.observe(el); });
})();

/* ============================================================
   4. 菜单 Tab 切换
   ============================================================ */
(function () {
  const tabs = document.querySelectorAll('.tab-btn');
  const panels = document.querySelectorAll('.panel');

  tabs.forEach(function (tab) {
    tab.addEventListener('click', function () {
      const targetId = tab.dataset.panel;
      if (!targetId) return;

      tabs.forEach(function (t) {
        t.classList.remove('active');
        t.setAttribute('aria-selected', 'false');
      });
      panels.forEach(function (p) { p.classList.remove('active'); });

      tab.classList.add('active');
      tab.setAttribute('aria-selected', 'true');

      const target = document.getElementById(targetId);
      if (target) target.classList.add('active');
    });
  });
})();

/* ============================================================
   5. 轻提示 & 预约表单
   ============================================================ */
(function () {
  const toastEl = document.getElementById('toast');
  let toastTimer = null;

  function showToast(message) {
    toastEl.textContent = message;
    toastEl.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () {
      toastEl.classList.remove('show');
    }, 2800);
  }

  const form = document.getElementById('reserveForm');
  const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  form.addEventListener('submit', function (e) {
    e.preventDefault();
    const input = form.querySelector('input[name="email"]');
    const value = (input.value || '').trim();

    if (!value) {
      showToast('请先填写邮箱地址 ☕');
      input.focus();
      return;
    }
    if (!emailPattern.test(value)) {
      showToast('邮箱格式看起来不太对，请再检查一下');
      input.focus();
      return;
    }

    showToast('预约成功！我们会在 24 小时内与你联系 ☕');
    form.reset();
  });
})();
