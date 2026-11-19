'use strict';
document.querySelectorAll('form[data-confirm]').forEach(form => {
  form.addEventListener('submit', event => {
    if (!window.confirm(form.dataset.confirm)) event.preventDefault();
  });
});
document.querySelectorAll('[data-back]').forEach(button => {
  button.addEventListener('click', () => {
    if (history.length > 1) history.back(); else location.assign('/app/forums');
  });
});
