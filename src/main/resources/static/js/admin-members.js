// A05 회원 관리 — GET /admin/members (조회만). 비밀번호·토큰은 API 가 반환하지 않음

requireAdmin();
renderHeader('admin-members');

const ACCOUNT_STATUS = { ACTIVE: ['활성', 'ok'], WITHDRAWN: ['탈퇴', 'off'] };
const PAGE_SIZE = 50;
const state = { keyword: '', status: '', page: 0, totalPages: 0 };

function row(m) {
  const [text, tone] = ACCOUNT_STATUS[m.accountStatus] || [m.accountStatus, 'off'];
  return el('tr', {},
    el('td', {}, el('b', {}, m.displayName)),
    el('td', {}, m.email),
    el('td', {}, el('span', { class: 'badge ' + tone }, text)),
    el('td', {}, prettyDate(ymdOf(m.createdAt))));
}

let requestSeq = 0;
async function loadPage() {
  const seq = ++requestSeq;
  const rows = document.getElementById('rows');
  try {
    const page = await api('/admin/members',
      { query: { keyword: state.keyword, status: state.status, page: state.page, size: PAGE_SIZE } });
    if (seq !== requestSeq) return;
    if (state.page === 0) rows.replaceChildren();
    rows.append(...page.items.map(row));
    if (!rows.children.length) {
      rows.append(el('tr', {}, el('td', { colspan: 4, class: 'muted', style: 'text-align:center;padding:32px' },
        '조건에 맞는 회원이 없습니다')));
    }
    state.totalPages = page.page.totalPages;
    document.getElementById('total').textContent = `${page.page.totalElements}명`;
    document.getElementById('more').classList.toggle('hidden', state.page + 1 >= state.totalPages);
  } catch (e) {
    if (handleAdminError(e)) return;
    rows.replaceChildren(el('tr', {}, el('td', { colspan: 4, style: 'text-align:center;padding:32px' }, e.message)));
  }
}

function reload() {
  state.page = 0;
  loadPage();
}

let keywordTimer;
document.getElementById('keyword').addEventListener('input', (e) => {
  clearTimeout(keywordTimer);
  keywordTimer = setTimeout(() => { state.keyword = e.target.value.trim(); reload(); }, 300);
});
document.getElementById('status').onchange = (e) => { state.status = e.target.value; reload(); };
document.getElementById('more').onclick = () => { state.page++; loadPage(); };

reload();
