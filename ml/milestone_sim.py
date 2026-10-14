"""Synthetic study-log generator shared by the Milestone notebooks.

The app is fully offline, so there is no real user data to train on.
We simulate realistic students instead: each has a few subjects with weekly
goals, weekday preferences, a typical session length and time of day, and a
slowly drifting motivation level. The resulting logs have the same shape as
the app's Room tables (subjects + sessions).
"""
from __future__ import annotations

import datetime as dt
from dataclasses import dataclass

import numpy as np
import pandas as pd

SUBJECT_NAMES = ["Physics", "Mathematics", "English", "Chemistry", "Biology", "History"]


@dataclass
class SimConfig:
    n_users: int = 300
    n_weeks: int = 26
    seed: int = 7
    start: dt.date = dt.date(2026, 1, 5)  # a Monday


def simulate(cfg: SimConfig = SimConfig()):
    """Return (subjects, sessions) DataFrames.

    subjects: user, subject, goal_min
    sessions: user, subject, date, hour, duration_min
    """
    rng = np.random.default_rng(cfg.seed)
    subj_rows, sess_rows = [], []
    n_days = cfg.n_weeks * 7
    for u in range(cfg.n_users):
        n_subj = rng.integers(3, 7)
        names = rng.choice(SUBJECT_NAMES, n_subj, replace=False)
        diligence = rng.beta(4, 2)  # how reliably this user studies
        motivation = 0.0
        subjects = []
        for s in names:
            goal_h = int(rng.integers(2, 11))
            subjects.append(dict(
                name=s,
                goal=goal_h * 60,
                weekday_pref=rng.dirichlet(np.ones(7) * 1.5) * 7,  # mean 1
                dur_mu=np.log(rng.uniform(25, 110)),
                hour=rng.normal(rng.choice([10, 16, 20]), 1.5),
                hour_dur_slope=rng.normal(0, 0.04),
            ))
            subj_rows.append(dict(user=u, subject=s, goal_min=goal_h * 60))
        for week in range(cfg.n_weeks):
            motivation = 0.7 * motivation + rng.normal(0, 0.35)
            week_done = {s["name"]: 0.0 for s in subjects}
            for wd in range(7):
                day = cfg.start + dt.timedelta(days=week * 7 + wd)
                for s in subjects:
                    remaining = max(s["goal"] - week_done[s["name"]], 0) / s["goal"]
                    sessions_per_week = s["goal"] / np.exp(s["dur_mu"])
                    base = sessions_per_week / 7 * s["weekday_pref"][wd]
                    p = base * (0.4 + diligence) * np.exp(motivation) * (0.35 + remaining)
                    p = float(np.clip(p, 0, 0.95))
                    if rng.random() < p:
                        hour = int(np.clip(round(rng.normal(s["hour"], 1.8)), 6, 23))
                        mu = s["dur_mu"] + s["hour_dur_slope"] * (hour - s["hour"])
                        dur = int(np.clip(round(rng.lognormal(mu, 0.35) / 5) * 5, 5, 600))
                        week_done[s["name"]] += dur
                        sess_rows.append(dict(user=u, subject=s["name"], date=day,
                                              hour=hour, duration_min=dur))
    subjects = pd.DataFrame(subj_rows)
    sessions = pd.DataFrame(sess_rows)
    sessions["date"] = pd.to_datetime(sessions["date"])
    return subjects, sessions


def week_start(d: pd.Series) -> pd.Series:
    return (d - pd.to_timedelta(d.dt.weekday, unit="D")).dt.normalize()
