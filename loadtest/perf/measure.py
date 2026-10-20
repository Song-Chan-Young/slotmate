"""화면별 API 의 요청 1회당 SQL 개수와 응답 시간(30회, 중앙값·p95) — setup.sh 로 띄운 앱(8082)에 측정
사용: python3 loadtest/perf/measure.py [라벨]   결과는 표로 출력
"""
import json
import pathlib
import statistics
import sys
import time
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASE = 'http://localhost:8082/api/v1'
LOG = ROOT / 'build/perf-app.log'
RUNS = 30

env = dict(line.split('=', 1) for line in (ROOT / '.env').read_text().splitlines() if '=' in line and not line.startswith('#'))


def call(path, token=None, body=None):
    req = urllib.request.Request(BASE + path, method='POST' if body else 'GET',
                                 data=json.dumps(body).encode() if body else None,
                                 headers={'Content-Type': 'application/json', **({'Authorization': f'Bearer {token}'} if token else {})})
    with urllib.request.urlopen(req) as res:
        return json.loads(res.read())


def login(email, password):
    return call('/auth/login', body={'email': email, 'password': password})['accessToken']


def sql_count(path, token):
    before = LOG.read_text(errors='ignore').count('org.hibernate.SQL')
    call(path, token)
    time.sleep(0.3)
    return LOG.read_text(errors='ignore').count('org.hibernate.SQL') - before


def timing(path, token):
    samples = []
    for _ in range(RUNS):
        start = time.perf_counter()
        call(path, token)
        samples.append((time.perf_counter() - start) * 1000)
    samples.sort()
    return statistics.median(samples), samples[int(len(samples) * 0.95) - 1]


member = login(env['MEMBER_EMAIL'], env['MEMBER_PASSWORD'])
admin = login(env['ADMIN_EMAIL'], env['ADMIN_PASSWORD'])
# 대기자가 가장 많은 회차 — 대기 순번 계산이 가장 많이 일어나는 경우
busy_session = max(call('/admin/sessions?from=2026-10-19&to=2026-10-25&size=500', admin)['items'],
                   key=lambda s: s['waitingCount'])['sessionId']

CASES = [
    ('회원 수업 찾기 (하루)', '/sessions?date=2026-10-21', member),
    ('회원 수업 찾기 (검색어)', '/sessions?date=2026-10-21&keyword=' + urllib.parse.quote('요가'), member),
    ('내 예약 (전체 20건)', '/my/reservations?size=20', member),
    ('내 예약 (대기만)', '/my/reservations?status=WAITING&size=20', member),
    ('알림 (20건)', '/my/notifications?size=20', member),
    ('읽지 않은 알림 수', '/my/notifications?unreadOnly=true&size=1', member),
    ('관리자 주간 시간표', '/admin/sessions?from=2026-10-19&to=2026-10-25&size=500', admin),
    ('관리자 회차별 예약자', f'/admin/sessions/{busy_session}/reservations?size=50', admin),
    ('관리자 회원 목록', '/admin/members?size=20', admin),
]

label = sys.argv[1] if len(sys.argv) > 1 else ''
print(f'| API {label} | SQL 개수 | 중앙값 | p95 |')
print('|---|---|---|---|')
for name, path, token in CASES:
    timing(path, token)  # 워밍업
    sqls = sql_count(path, token)
    median, p95 = timing(path, token)
    print(f'| {name} | {sqls} | {median:.1f}ms | {p95:.1f}ms |')
