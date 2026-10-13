# AshGo v2 ("Little Orbit") — Documentation Index

| Document | Status |
|---|---|
| [CURRENT_STATE_AUDIT.md](CURRENT_STATE_AUDIT.md) | ✅ Complete: code, build, lint and history audit of `main` @ `fc1070e` |
| [SECURITY_PRIVACY_AUDIT.md](SECURITY_PRIVACY_AUDIT.md) | ✅ Complete: confirmed and likely findings, plus owner actions |
| [FEATURE_MATRIX.md](FEATURE_MATRIX.md) | ✅ Complete: 25 existing features classified, with recommended decisions |
| [ZERO_COST_DEPENDENCIES.md](ZERO_COST_DEPENDENCIES.md) | ✅ Complete: services, licences, quotas, cost gates |
| [PRODUCT_DISCOVERY.md](PRODUCT_DISCOVERY.md) | ✅ Complete: thesis, research, 26 opportunities, principles |
| [FEATURE_OPPORTUNITY_MATRIX.md](FEATURE_OPPORTUNITY_MATRIX.md) | ✅ Complete: weighted scoring, P0/P1/P2, acceptance criteria |
| [USER_JOURNEYS.md](USER_JOURNEYS.md) | ✅ Complete: 12 journeys with failure paths |
| [PRODUCT_DECISIONS.md](PRODUCT_DECISIONS.md) | ✅ D-1, D-6, D-14 decided; Q-2 (repo visibility) still open |
| [ARCHITECTURE_PROPOSAL.md](ARCHITECTURE_PROPOSAL.md) | ✅ Approved 2026-10-13 |
| [REDESIGN_SPEC.md](REDESIGN_SPEC.md) | ✅ Approved 2026-10-13 |
| [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) | 🟢 Stages 0–2 implemented (except Task 2.2, Nunito, awaiting download OK); Stages 3–11 roadmap |

All work lives on branch `v2`. Implemented so far: build baseline, pure domain logic and the design system (`domain/`, `core/`, `ui/theme`, `ui/components`) with 120 unit tests and 23 UI tests.
The app still runs v1 until the Stage 11 cutover; v2 code is not yet reachable from the launcher, except through the debug-only design catalog.
