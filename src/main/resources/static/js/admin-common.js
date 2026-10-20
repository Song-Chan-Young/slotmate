// 관리자 화면 공통 — ADMIN 이 아니면 로그인으로, 폼 오류 표시

function requireAdmin() {
  if (!Auth.token() || !Auth.isAdmin()) {
    Auth.goLogin();
    throw new Error('관리자 로그인 필요');
  }
}

/** 관리자 API 오류 — 401·403 이면 다시 로그인 */
function handleAdminError(e) {
  if (e.status === 401 || e.status === 403) {
    Auth.clear();
    Auth.goLogin();
    return true;
  }
  return false;
}

function field(label, input, name, hint) {
  return el('div', { class: 'field' },
    el('label', { for: input.id }, label, hint ? el('span', { class: 'muted' }, ` ${hint}`) : null),
    input,
    el('div', { class: 'field-error', 'data-for': name }));
}

function showFormErrors(form, e) {
  form.querySelectorAll('.field-error').forEach((s) => { s.textContent = ''; });
  e.fieldErrors.forEach((f) => {
    const slot = form.querySelector(`.field-error[data-for="${f.field}"]`);
    if (slot) slot.textContent = f.reason;
  });
  const box = form.querySelector('.form-error');
  box.textContent = e.message;
  box.classList.remove('hidden');
}

/** 폼 모달. onSubmit 이 true 를 돌려주면 닫힘 */
function formModal(title, form, submitLabel, onSubmit, extra) {
  return new Promise((resolve) => {
    const close = (v) => { backdrop.remove(); resolve(v); };
    const submit = el('button', { class: 'btn primary', type: 'submit' }, submitLabel);
    form.prepend(el('div', { class: 'form-error hidden' }));
    form.append(el('div', { class: 'modal-actions' },
      el('button', { class: 'btn', type: 'button', onclick: () => close(false) }, '닫기'), submit));
    form.addEventListener('submit', async (ev) => {
      ev.preventDefault();
      submit.disabled = true;
      try {
        if (await onSubmit()) close(true);
      } finally {
        submit.disabled = false;
      }
    });
    const backdrop = el('div', { class: 'modal-backdrop' },
      el('div', { class: 'modal card', role: 'dialog', 'aria-modal': 'true', style: 'max-width:560px' },
        el('h2', {}, title), extra || null, form));
    document.body.append(backdrop);
    form.querySelector('input, select')?.focus();
  });
}
