// M07 — POST /auth/login, /auth/signup. 오류는 필드 옆에 표시

renderHeader();
const next = new URLSearchParams(location.search).get('next');
const loginForm = document.getElementById('login-form');
const signupForm = document.getElementById('signup-form');

function showTab(signup) {
  document.getElementById('tab-login').classList.toggle('active', !signup);
  document.getElementById('tab-signup').classList.toggle('active', signup);
  loginForm.classList.toggle('hidden', signup);
  signupForm.classList.toggle('hidden', !signup);
}
document.getElementById('tab-login').onclick = () => showTab(false);
document.getElementById('tab-signup').onclick = () => showTab(true);

function clearErrors(form) {
  form.querySelector('.form-error').classList.add('hidden');
  form.querySelectorAll('.field-error').forEach((e) => { e.textContent = ''; });
}

function showError(form, e) {
  e.fieldErrors.forEach((f) => {
    const slot = form.querySelector(`.field-error[data-for="${f.field}"]`);
    if (slot) slot.textContent = f.reason;
  });
  const box = form.querySelector('.form-error');
  box.textContent = e.message;
  box.classList.remove('hidden');
}

/** 관리자는 관리자 화면, 회원은 원래 보던 화면으로 (외부 주소로는 보내지 않음) */
function goNext(user) {
  const safeNext = next && next.startsWith('/') && !next.startsWith('//') && !next.startsWith('/login') ? next : null;
  location.href = safeNext || (user.role === 'ADMIN' ? '/admin/home.html' : '/');
}

async function login(email, password) {
  const res = await api('/auth/login', { method: 'POST', body: { email, password } });
  Auth.save(res);
  goNext(res.user);
}

loginForm.addEventListener('submit', async (ev) => {
  ev.preventDefault();
  clearErrors(loginForm);
  const data = Object.fromEntries(new FormData(loginForm));
  try {
    await login(data.email.trim(), data.password);
  } catch (e) {
    showError(loginForm, e);
  }
});

signupForm.addEventListener('submit', async (ev) => {
  ev.preventDefault();
  clearErrors(signupForm);
  const data = Object.fromEntries(new FormData(signupForm));
  data.displayName = data.displayName.trim();
  data.email = data.email.trim();
  try {
    await api('/auth/signup', { method: 'POST', body: data });
    await login(data.email, data.password);
  } catch (e) {
    showError(signupForm, e);
  }
});
