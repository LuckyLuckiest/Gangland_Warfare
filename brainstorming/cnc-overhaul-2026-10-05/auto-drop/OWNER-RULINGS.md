# 0.15.2 owner rulings (2026-10-07)

Precedent from 0.15: every open decision takes its RECOMMENDED answer (section 10 of PLAN.md, first option).

- D1: own release 0.15.2. 0.15.1 is now in master (merge 970dfa96), so branch `0.15.2` is cut from master 629af929,
  not from `0.15.1`. TR is done: `<revision>0.15.2</revision>`, no api bump (api stays 2.2).
- D2: shipped `Drop_Mode` stays ONE_STAR.
- D3: learning runs only while `Drop_Mode` is AUTO.
- D4: no public event now.
- D5: any chase reaching 4 stars is locked until 180 s quiet.
- D6: up to 2 crimes and 2 stars go at once.
- D7: no explain command in 0.15.2.
- D8: keep the shorter timer for always-caught players (bounded as planned).
- D9: fix later; T11 files the docket row (P3).
- D10: accept server-wide level stats on shared MySQL.

Any line in PLAN.md that says "branch from 0.15.1" or "0.15.1 is not in master" is superseded by D1 above.
