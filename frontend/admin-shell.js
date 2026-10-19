/* Shared admin sidebar + header — single source of truth for all admin pages.
 * Structure and labels mirror admin.html (Dashboard), the frozen visual reference.
 * Usage: AdminShell.mount('circles') renders the sidebar (with Circles active)
 * and the top header into #adminSide / #adminHeader placeholders. */
window.AdminShell = (function () {
  var NAV = [
    { view: 'dashboard', label: 'Dashboard', href: 'admin.html' },
    { view: 'circles', label: 'Circles', href: 'circles.html' },
    { view: 'events', label: 'Events', href: 'events.html' },
    { group: 'Community' },
    { view: 'chat', label: 'General Chat', href: 'admin.html' },
    { view: 'discussions', label: 'Book Discussions', href: 'admin.html' },
    { view: 'recommend', label: 'Book Recommendations', href: 'admin.html' },
    { group: 'Users' },
    { view: 'usershome', label: 'Users', href: 'admin.html' },
    { group: 'Platform' },
    { view: 'analytics', label: 'Analytics', href: 'admin.html' },
    { view: 'settings', label: 'Settings', href: 'admin.html' }
  ];

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
    });
  }

  function renderSidebar(activeView) {
    var h = '<div class="brandrow"><span class="mark">R</span><div><b>READER\'S CIRCLE</b><span>Admin Panel</span></div></div>';
    h += '<nav id="nav">';
    NAV.forEach(function (item) {
      if (item.group) {
        h += '<div class="navgroup">' + esc(item.group) + '</div>';
      } else {
        var cur = item.view === activeView ? ' aria-current="page"' : '';
        h += '<button class="navbtn" data-view="' + item.view + '" data-href="' + item.href + '"' + cur + '>' + esc(item.label) + '</button>';
      }
    });
    h += '</nav>';
    h += '<div class="sidefoot"><b id="meName">Admin User</b><span id="meRole">Administrator</span><br><button class="linkbtn" id="signOut">Sign Out</button></div>';
    return h;
  }

  function renderHeader() {
    return '<span class="sync"><span class="dot"></span><span id="syncLbl">Real-Time Sync Active</span></span>'
      + '<span class="sp"></span>'
      + '<input class="apibase" id="apiBase" value="http://localhost:8080" aria-label="API base URL" />'
      + '<span class="userchip"><span class="avatar" id="meAv">A</span><span id="meTop">Admin User</span></span>';
  }

  function mount(activeView) {
    var side = document.getElementById('adminSide');
    var head = document.getElementById('adminHeader');
    if (side) {
      side.innerHTML = renderSidebar(activeView);
      side.querySelectorAll('.navbtn').forEach(function (b) {
        b.addEventListener('click', function () {
          var href = b.getAttribute('data-href');
          var here = window.location.pathname.split('/').pop() || 'admin.html';
          if (href !== here) window.location.href = href;
        });
      });
    }
    if (head) head.innerHTML = renderHeader();
  }

  function clearLogin() {
    localStorage.removeItem('rc_access');
    localStorage.removeItem('rc_refresh');
  }

  return { NAV: NAV, renderSidebar: renderSidebar, renderHeader: renderHeader, mount: mount, clearLogin: clearLogin };
})();
