"""Generates the Milestone ML notebooks.

    cd ml
    python build_notebooks.py
    jupyter nbconvert --to notebook --execute --inplace 0*.ipynb

00 must run first: it downloads + preprocesses OULAD into data/processed/.
"""
import nbformat as nbf


def nb(cells):
    book = nbf.v4.new_notebook()
    book.metadata["kernelspec"] = {"name": "python3", "display_name": "Python 3", "language": "python"}
    book.cells = [nbf.v4.new_markdown_cell(c[3:]) if c.startswith("md:") else nbf.v4.new_code_cell(c)
                  for c in cells]
    return book


COMMON = """\
import json, pathlib, hashlib
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

DATA = pathlib.Path('data')
PROC = DATA / 'processed'
ASSETS = pathlib.Path('../app/src/main/assets/ml')
ASSETS.mkdir(parents=True, exist_ok=True)
RED, INK, GREY, LINE = '#E11D2A', '#111111', '#8C8C8C', '#DEDEDE'

def is_test(student_id):
    \"\"\"Deterministic 20% hold-out split by student (no student appears in both sets).\"\"\"
    return int(hashlib.md5(str(student_id).encode()).hexdigest(), 16) % 5 == 0"""

# ============================================================== 00 preprocessing
prep = nb([
"""md:# 00 · Dataset acquisition & preprocessing (OULAD)

**Dataset:** Open University Learning Analytics Dataset (Kuzilek, Hlosta & Zdrahal, 2017, *Scientific Data* 4:170171),
licensed CC-BY 4.0, mirrored by the UCI Machine Learning Repository (id 349).
It contains **real, anonymised daily virtual-learning-environment (VLE) activity** for 32,593 student enrolments across
7 modules and 4 semesters (2013–2014).

**Why this dataset:** it is the largest openly licensed log of *day-by-day study activity per student per course*. That's
exactly the structure of Milestone's data (student → subjects → daily study).

**Known mismatch, stated up front:**
* OULAD measures **clicks**, not minutes. Every model trained on it uses only *scale-free* features (ratios, shares,
  fractions, or features that are linear in the same unit), so the learnt weights transfer from clicks to minutes. Each notebook
  explains this argument for its model.
* OULAD days are counted from the module start, not calendar days. Weeks are therefore 7-day blocks from the start.
* OULAD has no user-set weekly goals. Where a model needs one, we use a documented proxy.

This notebook downloads the raw data, checks its quality, cleans it, and writes two tidy tables to `data/processed/`.""",
COMMON,
"""md:## 1 · Download (skipped if already present)""",
"""import urllib.request, zipfile
RAW = DATA / 'oulad_raw'
URL = 'https://archive.ics.uci.edu/static/public/349/open+university+learning+analytics+dataset.zip'
if not (RAW / 'studentVle.csv').exists():
    DATA.mkdir(exist_ok=True)
    zpath = DATA / 'oulad.zip'
    urllib.request.urlretrieve(URL, zpath)
    zipfile.ZipFile(zpath).extractall(RAW)
sorted(p.name for p in RAW.iterdir())""",
"""md:## 2 · Load with explicit types
OULAD encodes missing values as `?`. The big table (`studentVle`, about 10.6 M rows) is loaded with compact dtypes.""",
"""courses = pd.read_csv(RAW / 'courses.csv')
info = pd.read_csv(RAW / 'studentInfo.csv', na_values='?')
reg = pd.read_csv(RAW / 'studentRegistration.csv', na_values='?')
vle = pd.read_csv(RAW / 'vle.csv', na_values='?')
svle = pd.read_csv(RAW / 'studentVle.csv', dtype={
    'code_module': 'category', 'code_presentation': 'category', 'id_student': 'int32',
    'id_site': 'int32', 'date': 'int16', 'sum_click': 'int32'})
for name, df in [('courses', courses), ('studentInfo', info), ('studentRegistration', reg),
                 ('vle', vle), ('studentVle', svle)]:
    print(f'{name:20s} {df.shape}')""",
"""md:## 3 · Data-quality audit""",
"""audit = pd.DataFrame({
    'missing_registration_date': [reg.date_registration.isna().sum()],
    'missing_unregistration (= did not withdraw)': [reg.date_unregistration.isna().sum()],
    'studentVle exact duplicate rows': [svle.duplicated().sum()],
    'studentVle rows before module start (date<0)': [(svle.date < 0).sum()],
    'sites in studentVle missing from vle.csv': [(~svle.id_site.isin(vle.id_site)).sum()],
}).T.rename(columns={0: 'count'})
audit""",
"""md:**About the "duplicates":** OULAD logs one row per *recording event*, not one per (student, site, day). Identical rows are
separate interaction batches that happen to have the same click count. They are not copy errors. The dataset authors define a
student's activity as the **sum** of `sum_click`, so we aggregate by summing instead of dropping rows. The cell below shows how
much the choice matters, for transparency.""",
"""dedup_share = 1 - svle.drop_duplicates().sum_click.sum() / svle.sum_click.sum()
print(f'Dropping exact duplicates would remove {dedup_share:.1%} of all clicks; we keep them (summing).')""",
"""md:## 4 · Cleaning rules
1. **Remove navigation-only activity.** `homepage` clicks are course-landing visits, not study, so they're dropped.
2. **Keep only the teaching period:** `0 ≤ date < module_presentation_length`. Pre-start browsing is removed.
3. **Respect withdrawals:** activity on or after `date_unregistration` is removed.
4. **Drop enrolments with too little signal:** fewer than 14 active days, or withdrawn before day 28. These enrolments
   carry almost no weekly pattern, and the app's models only switch on after a similar amount of history.
5. **Winsorise extreme days** at the 99.9th percentile of daily clicks per enrolment. A handful of days with thousands of
   clicks (scripted downloads or bulk resource opening) would otherwise dominate squared-error losses.""",
"""types = vle.set_index('id_site').activity_type
svle['activity_type'] = svle.id_site.map(types).astype('category')
print(svle.groupby('activity_type', observed=True).sum_click.sum().sort_values(ascending=False).head(10))

n0 = len(svle)
svle = svle[svle.activity_type != 'homepage']
svle = svle.merge(courses, on=['code_module', 'code_presentation'])
svle = svle[(svle.date >= 0) & (svle.date < svle.module_presentation_length)]
svle = svle.merge(reg[['code_module', 'code_presentation', 'id_student', 'date_unregistration']],
                  on=['code_module', 'code_presentation', 'id_student'], how='left')
svle = svle[svle.date_unregistration.isna() | (svle.date < svle.date_unregistration)]
print(f'rows kept after rules 1-3: {len(svle):,} / {n0:,}')""",
"""daily = (svle.groupby(['id_student', 'code_module', 'code_presentation', 'date'], observed=True)
             .sum_click.sum().rename('clicks').reset_index())
enr = (daily.groupby(['id_student', 'code_module', 'code_presentation'], observed=True)
            .agg(active_days=('date', 'nunique')).reset_index()
            .merge(courses, on=['code_module', 'code_presentation'])
            .merge(reg[['code_module', 'code_presentation', 'id_student', 'date_unregistration']],
                   on=['code_module', 'code_presentation', 'id_student'], how='left'))
keep = (enr.active_days >= 14) & (enr.date_unregistration.isna() | (enr.date_unregistration >= 28))
print(f'enrolments kept (rule 4): {keep.sum():,} / {len(enr):,}')
enr = enr[keep].reset_index(drop=True)
enr['enrolment_id'] = np.arange(len(enr), dtype='int32')
# observation window ends at withdrawal if any, else at module end
enr['n_days'] = np.where(enr.date_unregistration.isna(), enr.module_presentation_length,
                         enr.date_unregistration).astype(int)
daily = daily.merge(enr[['id_student', 'code_module', 'code_presentation', 'enrolment_id']],
                    on=['id_student', 'code_module', 'code_presentation'])""",
"""cap = daily.clicks.quantile(0.999)
print(f'winsorising {(daily.clicks > cap).sum():,} enrolment-days above {cap:.0f} clicks (rule 5)')
daily['clicks'] = daily.clicks.clip(upper=cap).astype('float32')

fig, ax = plt.subplots(1, 2, figsize=(10, 3.2))
ax[0].hist(np.log10(daily.clicks), bins=60, color=RED); ax[0].set_xlabel('log10(daily clicks)')
ax[0].set_title('Daily activity per enrolment (cleaned)')
wk = daily.assign(week=daily.date // 7).groupby('week').clicks.sum()
ax[1].plot(wk.index, wk.values / 1e3, color=INK); ax[1].set_xlabel('week of module'); ax[1].set_ylabel('k clicks')
ax[1].set_title('Total activity per week')
plt.tight_layout(); plt.show()""",
"""md:## 5 · Learners with several concurrent modules (our "subjects")
Milestone users track several subjects at once. In OULAD, a student taking **2–3 modules in the same semester** is the closest
real analogue. Here we build that table for the recommender (notebook 02).""",
"""multi = enr.groupby(['id_student', 'code_presentation']).code_module.nunique()
multi = multi[multi >= 2].reset_index()[['id_student', 'code_presentation']]
mm = daily.merge(multi, on=['id_student', 'code_presentation'])
mm = mm.merge(enr[['enrolment_id', 'n_days']], on='enrolment_id')
learners = mm[['id_student', 'code_presentation']].drop_duplicates().reset_index(drop=True)
learners['learner_id'] = np.arange(len(learners), dtype='int32')
mm = mm.merge(learners, on=['id_student', 'code_presentation'])
print(f'{len(learners):,} learners with >=2 concurrent modules, {len(mm):,} learner-module-days')""",
"""md:## 6 · Save tidy outputs""",
"""PROC.mkdir(parents=True, exist_ok=True)
enr[['enrolment_id', 'id_student', 'code_module', 'code_presentation', 'n_days', 'active_days']] \\
    .to_parquet(PROC / 'enrolments.parquet', index=False)
daily[['enrolment_id', 'date', 'clicks']].to_parquet(PROC / 'daily_enrolment.parquet', index=False)
mm[['learner_id', 'id_student', 'code_module', 'date', 'clicks', 'n_days']] \\
    .to_parquet(PROC / 'daily_multimodule.parquet', index=False)
summary = {
    'enrolments': int(len(enr)), 'students': int(enr.id_student.nunique()),
    'enrolment_days_with_activity': int(len(daily)), 'multi_module_learners': int(len(learners)),
    'winsor_cap_clicks': float(cap),
}
(PROC / 'summary.json').write_text(json.dumps(summary, indent=2))
summary""",
])

# ============================================================== 01 forecast
forecast = nb([
"""md:# 01 · Weekly forecast (ridge regression, trained on real OULAD data)

**In the app:** the Home card shows *"Forecast: ~18h 30m by Sunday · on track"*.

**Task:** at the end of day *d* of a week, predict the activity still to come before the week ends.
The forecast is `so_far + max(0, ŷ_remaining)`, so it can never fall below what's already logged.

**Features** (with `left = 1 − (d+1)/7`):

| feature | definition |
|---|---|
| `f_hist` | left · (mean daily activity over the 14 days before this week) · 7 |
| `f_last` | left · last week's total |
| `f_pace` | left · so_far / ((d+1)/7) — the current pace extrapolated |

**Why the weights transfer from clicks to minutes:** every feature is linear in the activity unit, and there's no intercept. If
all activity is multiplied by a constant *c* (clicks → minutes), every feature and the target scale by *c*, so the least-squares
weights stay the same. This is also why the user's weekly goal isn't a feature: OULAD has no goals.

**Personalisation (on device):** once a user has enough complete weeks (chosen below from the evidence), the app refits
$w = (X^TX + \\lambda I)^{-1}(X^Ty + \\lambda w_0)$, a ridge regression shrunk towards these global weights $w_0$.""",
COMMON,
"""enr = pd.read_parquet(PROC / 'enrolments.parquet')
daily = pd.read_parquet(PROC / 'daily_enrolment.parquet')
FEATURES = ['f_hist', 'f_last', 'f_pace']

def rows_for(eid, n_days, g):
    T = np.zeros(n_days, dtype=float)
    T[g.date.to_numpy()] = g.clicks.to_numpy()
    out = []
    for w in range(2, n_days // 7):
        week = T[w*7:(w+1)*7]
        avg14, last = T[w*7-14:w*7].mean(), T[(w-1)*7:w*7].sum()
        cs = np.cumsum(week)
        for d in range(7):
            frac = (d + 1) / 7; left = 1 - frac
            out.append((eid, w, d, cs[d], cs[-1], left*avg14*7, left*last, left*cs[d]/frac))
    return out

nd = enr.set_index('enrolment_id').n_days
recs = [r for eid, g in daily.groupby('enrolment_id') for r in rows_for(eid, int(nd[eid]), g)]
df = pd.DataFrame(recs, columns=['enrolment_id', 'week', 'day', 'so_far', 'total'] + FEATURES)
df['remaining'] = df.total - df.so_far
df = df.merge(enr[['enrolment_id', 'id_student']], on='enrolment_id')
df['test'] = df.id_student.map(is_test)
train, test = df[~df.test], df[df.test]
print(f'{len(df):,} rows | train {len(train):,} | test {len(test):,} | '
      f'{df.id_student.nunique():,} students')""",
"""md:## Fit
Closed-form ridge regression. λ is chosen on a validation split carved out of the **training** students. The test students are
only used once, for the final numbers.""",
"""def ridge(X, y, lam, w0=None):
    w0 = np.zeros(X.shape[1]) if w0 is None else w0
    lam = lam + 1e-9  # numerical floor: an all-zero week would otherwise make X'X singular
    return np.linalg.solve(X.T @ X + lam * np.eye(X.shape[1]), X.T @ y + lam * w0)

def mae_total(frame, w):
    return np.abs(frame.so_far + np.maximum(frame[FEATURES].to_numpy() @ w, 0) - frame.total).mean()

val_mask = train.id_student % 7 == 0
tr, va = train[~val_mask], train[val_mask]
lams = [0, 1e2, 1e4, 1e6, 1e8]
val = {lam: mae_total(va, ridge(tr[FEATURES].to_numpy(), tr.remaining.to_numpy(), lam)) for lam in lams}
LAM = min(val, key=val.get)
W = ridge(train[FEATURES].to_numpy(), train.remaining.to_numpy(), LAM)
print('validation MAE by lambda:', {k: round(v, 2) for k, v in val.items()})
print('chosen lambda', LAM, '| weights', dict(zip(FEATURES, W.round(4))))""",
"""md:## Held-out evaluation vs baselines
* **pace:** assume the rest of the week continues at this week's pace (what most trackers show).
* **last week:** assume the week ends at last week's total (but never below what's already logged).

Error is reported relative to the size of the week (`MAE / mean weekly total`), so it reads the same in clicks or minutes.""",
"""def predictions(f):
    frac = (f.day + 1) / 7
    return {
        'ridge (ours)': f.so_far + np.maximum(f[FEATURES].to_numpy() @ W, 0),
        'pace': f.so_far / frac,
        'last week': np.maximum(f.so_far, f.f_last / (1 - frac).where(frac < 1, 1)),
    }
scale = test.groupby(['enrolment_id', 'week']).total.first().mean()
rel = pd.DataFrame({k: (v - test.total).abs().groupby(test.day).mean() / scale
                    for k, v in predictions(test).items()})
rel.index = [f'day {d+1}' for d in rel.index]
ax = rel.plot(kind='bar', figsize=(9, 3.5), color=[RED, GREY, LINE], rot=0, edgecolor=INK, linewidth=.3)
ax.set_ylabel('MAE / mean weekly total'); ax.set_title('Forecast error by day of week, held-out students')
plt.tight_layout(); plt.show()
overall = {k: float((v - test.total).abs().mean() / scale) for k, v in predictions(test).items()}
print({k: f'{v:.1%}' for k, v in overall.items()})
rel.round(3)""",
"""md:## Does personalisation help?
For each held-out enrolment: fit on its first *k* weeks, then test on its remaining weeks. We compare the global prior, a purely
personal fit, and the personal fit shrunk towards the prior.""",
"""def personal_curve(frame, w_prior, ks, lam_p, max_enrol=1500):
    res = {}
    groups = list(frame.groupby('enrolment_id'))[:max_enrol]
    for k in ks:
        acc = {'global prior': [], 'personal only': [], 'personal + prior': []}
        for _, g in groups:
            a, b = g[g.week < 2 + k], g[g.week >= 2 + k]
            if len(b) == 0: continue
            Xa, ya = a[FEATURES].to_numpy(), a.remaining.to_numpy()
            s = b.groupby('week').total.first().mean() or 1
            for name, w in [('global prior', w_prior), ('personal only', ridge(Xa, ya, 1e-6 * (Xa**2).sum())),
                            ('personal + prior', ridge(Xa, ya, lam_p * (Xa**2).sum() / len(Xa), w_prior))]:
                acc[name].append(mae_total(b, w) / s)
        res[k] = {n: np.median(v) for n, v in acc.items()}
    return pd.DataFrame(res).T

# All choices here are made on VALIDATION students (va), with a prior fitted only on the other training
# students (tr). The test students are used once, afterwards, to report the result.
W_tr = ridge(tr[FEATURES].to_numpy(), tr.remaining.to_numpy(), LAM)
# lambda is scaled by the user's own feature magnitude, so it is unit-free on the phone as well
cands = [1, 3, 10, 30, 100]
cv = {c: personal_curve(va, W_tr, [6], c, 800).loc[6, 'personal + prior'] for c in cands}
LAM_P = min(cv, key=cv.get)
print('validation relative-MAE by lambda_personal:', {k: round(v, 4) for k, v in cv.items()}, '-> chosen', LAM_P)
val_curve = personal_curve(va, W_tr, [3, 6, 10, 15], LAM_P, 800)
print('validation curve'); print(val_curve.round(4))
""",
"""# Switch personalisation on only from the amount of history where it beats the prior ON VALIDATION.
better = val_curve.index[val_curve['personal + prior'] < val_curve['global prior']]
MIN_WEEKS = int(better.min()) if len(better) else 9999
print('personalise from', MIN_WEEKS, 'weeks of history' if MIN_WEEKS < 9999 else '(never: prior is always better)')
# Final, one-time report on the held-out test students:
curve = personal_curve(test, W, [3, 6, 10, 15], LAM_P)
ax = curve.plot(marker='o', figsize=(7, 3.4), color=[GREY, INK, RED])
ax.set_xlabel('weeks of personal history'); ax.set_ylabel('median relative MAE (test)'); plt.tight_layout(); plt.show()
curve.round(4)""",
"""md:## Export → `app/src/main/assets/ml/weekly_forecast.json`
Read by `WeeklyForecaster.kt`. The personal λ is multiplied on device by `mean(x²)` of the user's own design matrix, matching
the scaling used above.""",
"""export = {
    'version': 2, 'source': 'OULAD (CC-BY 4.0), held-out by student',
    'features': FEATURES, 'weights': [float(x) for x in W],
    'lambda_personal_rel': float(LAM_P), 'min_personal_weeks': MIN_WEEKS,
    'test_relative_mae': overall['ridge (ours)'],
    'baseline_relative_mae': {k: v for k, v in overall.items() if k != 'ridge (ours)'},
}
(ASSETS / 'weekly_forecast.json').write_text(json.dumps(export, indent=2))
export""",
"""md:## Limitations
* Trained on Open University distance-learning students in 2013–14. Their weekly rhythm (assignment cycles) may differ from a
  school or university student using Milestone. That's why the app personalises once it has data.
* Clicks are only a proxy for study time. The scale-invariance argument covers the *unit*, not the fact that clicks and
  minutes measure slightly different behaviour.
* Personalisation gains are **small**. The switch-on threshold was picked on validation students. On the test students it is
  essentially neutral at the threshold (within about 0.1% of the prior) and gives a small improvement with more weeks of
  history (see the test curve above). It's a modest refinement, not a big win.
* The model is trained on rows taken at the *end* of a day. The app therefore computes features as of the end of yesterday,
  adds today's minutes, and shows no forecast until 14 days of history exist.""",
])

# ============================================================== 02 recommender
recommender = nb([
"""md:# 02 · "Study next" recommender (logistic regression, trained on real OULAD data)

**In the app:** Home shows a *Needs attention* suggestion with a **Why?** explanation, and **Log session** / **Focus timer** pre-select that subject.

**Data:** the real OULAD learners who took **2–3 modules in the same semester** (built in notebook 00). Each module plays the
role of a Milestone subject.

**Label:** did the learner study module *s* on day *t*? **Features** (computed only from days before *t*):

| feature | definition | unit-free? |
|---|---|---|
| `log_days_since` | log(1 + days since the subject was last studied), capped at 30 | ✓ |
| `goal_left` | share of this week's target not yet reached (0–1) | ✓ |
| `weekday_affinity` | share of the last 8 same weekdays on which the subject was studied | ✓ |
| `share_14d` | the subject's share of all activity in the last 14 days | ✓ |
| `yesterday` | studied yesterday (0/1) | ✓ |

**Goal proxy (honest note):** OULAD has no weekly goals. During training, a subject's weekly target is the learner's **median
weekly activity on that module over the previous 4 weeks**, floored by the median of all their *earlier* non-zero weeks on it.
Only past weeks are used, so there is no look-ahead. In the app, the user's real goal is used.""",
COMMON,
"""mm = pd.read_parquet(PROC / 'daily_multimodule.parquet')
FEATURES = ['log_days_since', 'goal_left', 'weekday_affinity', 'share_14d', 'yesterday']

def learner_rows(lid, g):
    mods = sorted(g.code_module.unique())
    n_days = int(g.n_days.max())
    M = np.zeros((n_days, len(mods)))
    for j, m in enumerate(mods):
        gm = g[g.code_module == m]
        M[gm.date.to_numpy(), j] = gm.clicks.to_numpy()
    # each module is only "active" until that enrolment ends
    active_until = np.array([int(g[g.code_module == m].n_days.max()) for m in mods])
    S = M > 0
    weekly = np.add.reduceat(M, np.arange(0, n_days, 7), axis=0)
    last = np.full(len(mods), -1)
    out = []
    for t in range(n_days):
        if t >= 28:
            w = t // 7
            # Only weeks BEFORE w are used (no peeking at the future): recent median, floored by the
            # median of all earlier non-zero weeks of that module for this learner.
            past = weekly[:w]
            floor = np.array([np.median(c[c > 0]) if (c > 0).any() else 1.0 for c in past.T])
            target = np.maximum(np.median(past[max(0, w-4):], axis=0), np.maximum(floor, 1.0))
            done = M[w*7:t].sum(0)
            goal_left = np.clip((target - done) / target, 0, 1)
            ds = np.minimum(np.where(last < 0, 30, t - last), 30)
            aff = S[[t - 7*k for k in range(1, 9) if t - 7*k >= 0]].mean(0)
            tot = M[t-14:t].sum()
            share = M[t-14:t].sum(0) / tot if tot > 0 else np.zeros(len(mods))
            for j in range(len(mods)):
                if t < active_until[j]:
                    out.append((lid, t, j, np.log1p(ds[j]), goal_left[j], aff[j], share[j], float(S[t-1, j]), int(S[t, j])))
        last = np.where(S[t], t, last)
    return out

df = pd.DataFrame([r for lid, g in mm.groupby('learner_id') for r in learner_rows(lid, g)],
                  columns=['learner_id', 'day', 'subj'] + FEATURES + ['y'])
df = df.merge(mm[['learner_id', 'id_student']].drop_duplicates(), on='learner_id')
df['test'] = df.id_student.map(is_test)
train, test = df[~df.test], df[df.test]
print(f'{len(df):,} learner-subject-days | positive rate {df.y.mean():.3f} | '
      f'{df.learner_id.nunique():,} learners ({test.learner_id.nunique()} held out)')""",
"""from sklearn.linear_model import LogisticRegression
from sklearn.metrics import roc_auc_score, log_loss

clf = LogisticRegression(C=1.0, max_iter=2000).fit(train[FEATURES], train.y)
p = clf.predict_proba(test[FEATURES])[:, 1]
print(f'held-out AUC {roc_auc_score(test.y, p):.3f} | log-loss {log_loss(test.y, p):.4f} '
      f'(constant-rate log-loss {log_loss(test.y, np.full(len(test), train.y.mean())):.4f})')
pd.Series(clf.coef_[0], index=FEATURES).round(3).to_frame('weight')""",
"""md:## Does the top suggestion match what the learner actually studied?
Only days where the learner studied at least one subject are counted. Baselines are simple rules a hand-written app might use.""",
"""def top1(frame, score):
    f = frame.assign(score=score)
    f = f[f.groupby(['learner_id', 'day']).y.transform('sum') > 0]
    f = f[f.groupby(['learner_id', 'day']).y.transform('size') > 1]   # a real choice exists
    pick = f.loc[f.groupby(['learner_id', 'day']).score.idxmax()]
    return pick.y.mean()

scores = {
    'logistic regression (ours)': p,
    'studied yesterday / most recent': -test.log_days_since.to_numpy() + 1e-3 * test.share_14d.to_numpy(),
    'most goal left': test.goal_left.to_numpy(),
    'random': np.random.default_rng(0).random(len(test)),
}
hits = pd.Series({k: top1(test, v) for k, v in scores.items()})
ax = hits.iloc[::-1].plot(kind='barh', color=[LINE, GREY, INK, RED], figsize=(7.5, 2.6))
ax.set_xlabel('top-1 hit rate (held-out learners)'); plt.tight_layout(); plt.show()
hits.round(3)""",
"""md:### Reading this honestly
As a *"guess what they'll study"* ranker, the model does **not** beat the trivial "same as yesterday" rule. Learners are
creatures of habit, and the recency rule already captures most of that. A suggestion that just repeats yesterday is also
useless advice.

So the app does **not** use the model as a ranker. It uses the model's **probability** as a measure of *how likely the
student is to get to this subject on their own today*, and suggests the subject they're **most likely to neglect** while its
weekly goal is unmet:

$$\\text{need}(s) = \\text{goal\\_left}(s)\\times\\big(1 - P(\\text{study } s\\text{ today})\\big)$$

That only works if the probabilities are **calibrated**, i.e. "30%" really means 30%. We check that next.

What we can and can't claim: calibration is measured **on OULAD learners** (activity in clicks, positive rate ~23%), not on
Milestone users. The decision rule itself can't be scored offline, because there is no ground truth for which subject a student
*should* have studied. It is a transparent product rule built on a calibrated estimate, not a validated recommender.""",
"""from sklearn.calibration import calibration_curve
frac_pos, mean_pred = calibration_curve(test.y, p, n_bins=10, strategy='quantile')
fig, ax = plt.subplots(figsize=(4.2, 4))
ax.plot([0, 1], [0, 1], ls='--', color=GREY, lw=1, label='perfect')
ax.plot(mean_pred, frac_pos, marker='o', color=RED, label='model')
ax.set_xlabel('predicted P(study)'); ax.set_ylabel('observed frequency'); ax.legend(); ax.set_title('Calibration (held-out)')
plt.tight_layout(); plt.show()
ECE = float(np.average(np.abs(frac_pos - mean_pred)))
print(f'expected calibration error ≈ {ECE:.3f}')""",
"""md:## On-device personalisation: SGD with an L2 pull towards the prior
This is the exact algorithm in `SubjectRecommender.kt`. Each held-out learner's first 8 weeks of history are used for
personalisation, and their later days for testing. Hyper-parameters are chosen on **training** learners only.""",
"""W0 = np.r_[clf.intercept_, clf.coef_[0]]

def sgd(X, y, w0, lr, epochs, l2):
    w = w0.copy(); Xb = np.c_[np.ones(len(X)), X]
    for _ in range(epochs):
        for xi, yi in zip(Xb, y):
            w -= lr * ((1 / (1 + np.exp(-xi @ w)) - yi) * xi + l2 * (w - w0))
    return w

def personal(frame, lr, epochs, l2, split_day=28 + 56, max_learners=250):
    a, b = [], []
    for _, g in list(frame.groupby('learner_id'))[:max_learners]:
        tr, te = g[g.day < split_day], g[g.day >= split_day]
        if len(te) == 0 or te.y.nunique() < 2: continue
        w = sgd(tr[FEATURES].to_numpy(), tr.y.to_numpy(), W0, lr, epochs, l2)
        Xt = np.c_[np.ones(len(te)), te[FEATURES].to_numpy()]
        a.append(log_loss(te.y, 1 / (1 + np.exp(-Xt @ W0)), labels=[0, 1]))
        b.append(log_loss(te.y, 1 / (1 + np.exp(-Xt @ w)), labels=[0, 1]))
    return np.mean(a), np.mean(b)

grid = [(lr, ep, l2) for lr in [0.005, 0.02] for ep in [1, 3] for l2 in [0.01, 0.1]]
tuned = {g: personal(train, *g, max_learners=120)[1] for g in grid}
BEST = min(tuned, key=tuned.get)
prior_ll, pers_ll = personal(test, *BEST)
print('chosen (lr, epochs, l2) =', BEST)
print(f'held-out log-loss: global prior {prior_ll:.4f} -> personalised {pers_ll:.4f} '
      f'({(prior_ll - pers_ll) / prior_ll:+.1%})')""",
"""export = {
    'version': 2, 'source': 'OULAD multi-module learners (CC-BY 4.0), held-out by student',
    'features': FEATURES, 'intercept': float(clf.intercept_[0]),
    'weights': [float(x) for x in clf.coef_[0]],
    'sgd': {'lr': BEST[0], 'epochs': BEST[1], 'l2': BEST[2]},
    'min_history_days': 14,
    'test_auc': float(roc_auc_score(test.y, p)),
    'test_top1': {k: float(v) for k, v in hits.items()},
    'test_calibration_error': ECE,
    'decision_rule': 'need = goal_left * (1 - p)',
}
(ASSETS / 'next_subject.json').write_text(json.dumps(export, indent=2))
export""",
"""md:## Limitations
* After cleaning, only **412** real learners took multiple modules at once (81 held out), and they have 2–3 "subjects", while app users may track 5–6.
  All features are per-subject and unit-free, so the model applies to any number of subjects, but there are fewer
  examples with many subjects.
* The weekly-target proxy is learnt from past behaviour, not set by the learner.""",
])

# ============================================================== 03 duration
duration = nb([
"""md:# 03 · Session length suggestion (k-nearest neighbours)

**In the app:** **Log session** pre-fills the duration and marks the closest quick chip as *Suggested*.

**Data, stated honestly:** a real public dataset with *timed* study sessions (start hour + length) would be ideal. The
American Time Use Survey has one, but the US Bureau of Labor Statistics blocks scripted downloads, and OULAD only logs
daily click counts with no times or durations. This notebook therefore **tunes** the k-NN hyper-parameters on a transparent
simulator (`milestone_sim.py`). The model itself has no trained weights. **On the phone it predicts purely from the user's own
logged sessions**, so the simulator only influences 5 hyper-parameters, never the predictions' content.

Distance between a new session *q* and a past session *p*:

$d = w_s[\\text{subject differs}] + w_h\\frac{|h_q-h_p|}{12} + w_d[\\text{weekday differs}] + w_a\\frac{\\text{age}_{days}}{30}$

Prediction = inverse-distance-weighted **mean or median** of the *k* nearest past durations, rounded to 5 minutes.
(Note: the baseline MAEs below are computed without rounding, whereas the app rounds its suggestion to 5 minutes and
only looks at the latest 400 sessions.)
Session lengths are right-skewed and we score with absolute error, which a median minimises, so both are tried. A simple
per-subject median is also included as a baseline, and if it wins, the app ships that behaviour instead.""",
COMMON,
"""from milestone_sim import SimConfig, simulate
subjects, sessions = simulate(SimConfig())
s = sessions.sort_values(['user', 'date', 'hour']).reset_index(drop=True)
s['wd'] = s.date.dt.weekday
s['day'] = (s.date - s.date.min()).dt.days
s['sid'] = s.groupby('user').subject.transform(lambda x: pd.factorize(x)[0])
print(f'{s.user.nunique()} simulated users, {len(s):,} sessions')

def wmedian(values, weights):
    o = np.argsort(values); c = np.cumsum(weights[o])
    return values[o][np.searchsorted(c, c[-1] / 2)]

def evaluate(users, k, ws, wh, wd, wa, min_hist=3, eps=0.05):
    err, err_med, b_user, b_subj, b_subj_med, b_last = [], [], [], [], [], []
    for u in users:
        g = s[s.user == u]
        sid, hr, wkd, day = (g[c].to_numpy() for c in ['sid', 'hour', 'wd', 'day'])
        dur = g.duration_min.to_numpy(float)
        for i in range(min_hist, len(g)):
            d = (ws * (sid[:i] != sid[i]) + wh * np.abs(hr[:i] - hr[i]) / 12 +
                 wd * (wkd[:i] != wkd[i]) + wa * (day[i] - day[:i]) / 30)
            o = np.argsort(d)[:k]; wt = 1 / (d[o] + eps)
            err.append(abs(np.round((wt * dur[:i][o]).sum() / wt.sum() / 5) * 5 - dur[i]))
            err_med.append(abs(np.round(wmedian(dur[:i][o], wt) / 5) * 5 - dur[i]))
            b_user.append(abs(dur[:i].mean() - dur[i]))
            same = dur[:i][sid[:i] == sid[i]]
            b_subj.append(abs((same.mean() if len(same) else dur[:i].mean()) - dur[i]))
            b_subj_med.append(abs((np.median(same) if len(same) else np.median(dur[:i])) - dur[i]))
            b_last.append(abs((same[-1] if len(same) else dur[i-1]) - dur[i]))
    return dict(knn_mean=np.mean(err), knn_median=np.mean(err_med), subject_median=np.mean(b_subj_med),
                subject_mean=np.mean(b_subj), user_mean=np.mean(b_user), last_same_subject=np.mean(b_last))

users = s.user.unique()
tune, test_users = users[users % 5 != 0][:80], users[users % 5 == 0]""",
"""grid = [(k, ws, wh, wd, wa) for k in [3, 5, 8, 12] for ws in [1, 3] for wh in [0.5, 1.5]
        for wd in [0, 0.3] for wa in [0.1, 0.5]]
grid = [g + (agg,) for g in grid for agg in ['knn_mean', 'knn_median']]
cache = {}
def score(g):
    key = g[:5]
    if key not in cache: cache[key] = evaluate(tune, *key)
    return cache[key][g[5]]
scores = {g: score(g) for g in grid}
BEST = min(scores, key=scores.get)
print('best (k, w_subject, w_hour, w_weekday, w_age, aggregate) =', BEST, f'| tuning MAE {scores[BEST]:.2f} min')
pd.Series(scores).sort_values().head(6).round(2)""",
"""res = pd.Series(evaluate(test_users, *BEST[:5])).sort_values()
ax = res.iloc[::-1].plot(kind='barh', color=[RED if k.startswith('knn') else GREY for k in res.index[::-1]], figsize=(7, 3))
ax.set_xlabel('MAE (minutes), held-out simulated users'); plt.tight_layout(); plt.show()
res.round(2)""",
"""k, ws, wh, wd, wa, agg = BEST
winner = res.index[0]
print(f'held-out winner: {winner} ({res.iloc[0]:.2f} min). k-NN {agg}: {res[agg]:.2f} min, '
      f'subject median: {res["subject_median"]:.2f} min')
# Honesty rule: if a simple baseline wins on held-out users, ship the baseline's behaviour instead.
# k-NN with a huge subject weight, no other terms and a very large k *is* the subject median: same-subject
# sessions get weight 1/eps = 20, other subjects ~1e-6, and with no same-subject history it falls back to the
# median of all sessions, exactly like the baseline. So the app needs no second code path.
if winner == 'subject_median':
    k, ws, wh, wd, wa, agg = 1000, 1e6, 0.0, 0.0, 0.0, 'knn_median'
export = {
    'version': 2, 'source': 'hyper-parameters tuned on simulated logs (see notebook); predictions use only on-device user data',
    'k': int(k), 'w_subject': float(ws), 'w_hour': float(wh), 'w_weekday': float(wd), 'w_age': float(wa),
    'aggregate': 'median' if agg == 'knn_median' else 'mean',
    'epsilon': 0.05, 'min_history': 3, 'max_history': 400, 'fallback_minutes': 45,
    'sim_test_mae_minutes': {k_: float(v) for k_, v in res.items()},
}
(ASSETS / 'duration_knn.json').write_text(json.dumps(export, indent=2))
export""",
])

import sys
only = sys.argv[1:]  # e.g. `python build_notebooks.py 03` rewrites just that notebook
for name, book in [("00_dataset_preprocessing.ipynb", prep),
                   ("01_weekly_goal_forecast.ipynb", forecast),
                   ("02_next_subject_recommender.ipynb", recommender),
                   ("03_session_duration_knn.ipynb", duration)]:
    if only and name[:2] not in only:
        continue
    nbf.write(book, name)
    print("wrote", name)
