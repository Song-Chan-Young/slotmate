// 관리자 프로그램 — GET/POST /admin/programs, PATCH /{id}, PATCH /{id}/active

requireAdmin();
renderHeader('admin-programs');

const state = { type: '', keyword: '' };
const typeSelect = document.getElementById('type');
typeSelect.append(...typeOptions(null));
typeSelect.onchange = () => { state.type = typeSelect.value; load(); };
let timer;
document.getElementById('keyword').addEventListener('input', (e) => {
  clearTimeout(timer);
  timer = setTimeout(() => { state.keyword = e.target.value.trim(); load(); }, 300);
});
document.getElementById('create').onclick = () => openForm(null);

async function load() {
  const rows = document.getElementById('rows');
  try {
    const page = await api('/admin/programs', { query: { type: state.type, keyword: state.keyword, size: 500 } });
    rows.replaceChildren(...(page.items.length ? page.items.map(row)
      : [el('tr', {}, el('td', { colspan: 7, class: 'muted', style: 'text-align:center;padding:32px' }, '등록된 프로그램이 없습니다'))]));
  } catch (e) {
    if (handleAdminError(e)) return;
    rows.replaceChildren(el('tr', {}, el('td', { colspan: 7 }, e.message)));
  }
}

function row(p) {
  return el('tr', {},
    el('td', {}, el('b', {}, p.name), p.description ? el('div', { class: 'muted', style: 'font-size:13px' }, p.description) : null),
    el('td', {}, `${CATEGORIES[p.category]?.[0] || p.category} · ${PROGRAM_TYPES[p.type] || p.type}`),
    el('td', {}, `${p.defaultDurationMinutes}분`),
    el('td', {}, `${p.defaultCapacity}명`),
    el('td', {}, p.maxPartySize > 1 ? `최대 ${p.maxPartySize}명` : '1명'),
    el('td', {}, el('span', { class: 'badge ' + (p.active ? 'ok' : 'off') }, p.active ? '활성' : '비활성')),
    el('td', { style: 'white-space:nowrap;text-align:right' },
      el('button', { class: 'btn small', onclick: () => openForm(p) }, '수정'), ' ',
      el('button', { class: 'btn small' + (p.active ? ' danger' : ''), onclick: () => toggleActive(p) },
        p.active ? '비활성화' : '활성화')));
}

async function toggleActive(p) {
  try {
    await api(`/admin/programs/${p.programId}/active`, { method: 'PATCH', body: { active: !p.active } });
    toast(p.active ? '비활성화했습니다. 회원 목록에서 빠집니다' : '활성화했습니다');
    load();
  } catch (e) {
    if (!handleAdminError(e)) await modal('바꾸지 못했습니다', el('p', {}, e.message));
  }
}

async function openForm(p) {
  const editing = !!p;
  const name = el('input', { id: 'p-name', maxlength: 100, required: true, value: p?.name ?? '' });
  const type = el('select', { id: 'p-type', disabled: editing }, typeOptions(p?.type));
  const duration = el('input', { id: 'p-duration', type: 'number', min: 1, value: p?.defaultDurationMinutes ?? 50 });
  const capacity = el('input', { id: 'p-capacity', type: 'number', min: 1, value: p?.defaultCapacity ?? 10 });
  const maxParty = el('input', { id: 'p-max-party', type: 'number', min: 1, value: p?.maxPartySize ?? 1 });
  const description = el('textarea', { id: 'p-description' }, p?.description ?? '');
  // 운동 수업은 1명씩, 클래스·놀거리는 일행 예약이 흔함 — 기본값만 바꾸고 직접 고칠 수 있음
  type.onchange = () => {
    if (editing) return;
    if (type.value === 'PT') capacity.value = 1;
    maxParty.value = CATEGORIES.EXERCISE[1].includes(type.value) ? 1 : 4;
  };

  const form = el('form', { novalidate: true },
    field('이름', name, 'name'),
    field('종류', type, 'type', editing ? '(수정 불가)' : null),
    el('div', { class: 'row' }, field('기본 시간(분)', duration, 'defaultDurationMinutes'), field('기본 정원', capacity, 'defaultCapacity')),
    field('예약 1건 최대 인원', maxParty, 'maxPartySize', '(일행 예약)'),
    field('설명', description, 'description', '(선택)'));

  const num = (v) => (v === '' ? null : Number(v));
  await formModal(editing ? '프로그램 수정' : '프로그램 등록', form, editing ? '저장' : '등록', async () => {
    const body = {
      name: name.value.trim(),
      description: description.value.trim() || null,
      defaultDurationMinutes: num(duration.value),
      defaultCapacity: num(capacity.value),
      maxPartySize: num(maxParty.value),
    };
    try {
      if (editing) {
        await api(`/admin/programs/${p.programId}`, { method: 'PATCH', body });
      } else {
        await api('/admin/programs', { method: 'POST', body: { ...body, type: type.value } });
      }
      toast(editing ? '수정했습니다' : '등록했습니다');
      load();
      return true;
    } catch (e) {
      if (handleAdminError(e)) return true;
      showFormErrors(form, e);
      return false;
    }
  });
}

load();
