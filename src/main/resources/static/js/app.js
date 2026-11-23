/* =========================================================
   课业云 · UI 交互脚本（原生 JS，无任何依赖）
   说明：当前阶段仅实现界面演示交互，不包含后端业务逻辑。
   ========================================================= */
(function () {
  'use strict';

  /* ---------------- 通用：提示 ---------------- */
  function toast(message, type) {
    var wrap = document.querySelector('.toast-wrap');
    if (!wrap) {
      wrap = document.createElement('div');
      wrap.className = 'toast-wrap';
      document.body.appendChild(wrap);
    }
    var el = document.createElement('div');
    el.className = 'toast' + (type === 'warn' ? ' warn' : '');
    el.innerHTML =
      '<svg class="icon"><use href="/img/icons.svg#' +
      (type === 'warn' ? 'i-alert' : 'i-check-circle') +
      '"></use></svg><span></span>';
    el.querySelector('span').textContent = message;
    wrap.appendChild(el);
    setTimeout(function () {
      el.style.transition = 'opacity .25s, transform .25s';
      el.style.opacity = '0';
      el.style.transform = 'translateY(8px)';
      setTimeout(function () { el.remove(); }, 260);
    }, 2600);
  }
  window.uiToast = toast;


  /* ---------------- 侧边栏（移动端抽屉） ---------------- */
  document.addEventListener('click', function (e) {
    var toggle = e.target.closest('[data-sidebar-toggle]');
    var sidebar = document.querySelector('.rail') || document.querySelector('.sidebar');
    if (toggle && sidebar) {
      sidebar.classList.toggle('open');
      return;
    }
    if (sidebar && sidebar.classList.contains('open') && !e.target.closest('.sidebar')) {
      sidebar.classList.remove('open');
    }
  });









  /* ---------------- 弹窗：Escape 关闭（动态弹窗共用） ---------------- */
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') {
      document.querySelectorAll('.modal-mask.open').forEach(function (m) { m.classList.remove('open'); });
    }
  });

  /* ---------------- 返回顶部（页面含 #backTop 时生效） ---------------- */
  var backTop = document.getElementById('backTop');
  if (backTop) {
    var syncBackTop = function () {
      backTop.classList.toggle('show', window.pageYOffset > 240);
    };
    window.addEventListener('scroll', syncBackTop, { passive: true });
    syncBackTop();
    backTop.addEventListener('click', function () {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  }

  /* ---------------- 作业表单：回顶部/去底部双按钮 ---------------- */
  var fabUp = document.getElementById('fabUp');
  var fabDown = document.getElementById('fabDown');
  if (fabUp && fabDown) {
    var syncFabs = function () {
      var y = window.pageYOffset;
      var max = document.documentElement.scrollHeight - window.innerHeight;
      fabUp.classList.toggle('show', y > 240);
      fabDown.classList.toggle('show', max > 200 && y < max - 200);
    };
    window.addEventListener('scroll', syncFabs, { passive: true });
    window.addEventListener('resize', syncFabs);
    syncFabs();
    fabUp.addEventListener('click', function () {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
    fabDown.addEventListener('click', function () {
      window.scrollTo({ top: document.documentElement.scrollHeight, behavior: 'smooth' });
    });
  }

  /* ---------------- 密码显示/隐藏切换（登录/注册页的眼睛按钮） ---------------- */
  document.querySelectorAll('[data-pw-eye]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var input = btn.parentElement.querySelector('input');
      if (!input) return;
      var showing = input.type === 'text';
      input.type = showing ? 'password' : 'text';
      var use = btn.querySelector('use');
      if (use) use.setAttribute('href', '/img/icons.svg#' + (showing ? 'i-eye-off' : 'i-eye'));
      btn.setAttribute('aria-label', showing ? '显示密码' : '隐藏密码');
    });
  });
})();

