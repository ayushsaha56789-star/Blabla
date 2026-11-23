// Apply the saved theme before the app starts, so dark mode never flashes white.
// A separate file (not inline) so the site's Content Security Policy can forbid inline scripts.
try {
  var m = localStorage.getItem('sc.theme');
  if (m === 'dark' || ((!m || m === 'system') && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
    document.documentElement.classList.add('dark');
  }
} catch (e) {}
