# New Scenario Template

Use this template when adding a new scenario to [SCENARIOS.md](../../SCENARIOS.md).

---

## S{N}: {Scenario Name}

{One-sentence description of what this scenario tests.}

| Step | Method | Path | Expected |
|------|--------|------|----------|
| {Step 1} | {GET/POST/PUT/DELETE} | `{path}` | {status code}, {assertion} |
| {Step 2} | {GET/POST/PUT/DELETE} | `{path}` | {status code}, {assertion} |
| ... | ... | ... | ... |

Entity relationships: {describe FK dependencies and required deletion order}

---

## Implementation Checklist

After adding the definition to `SCENARIOS.md`:

- [ ] Implement in `curl/` (reference implementation)
- [ ] Implement in `java/scenarios/.../manual/`
- [ ] Implement in `java/scenarios/.../generated/`
- [ ] Add scenario ID to `.matrix.json` `scenarios` array and each client's `scenarios` list
- [ ] Verify parity: `bash .ci/validate-parity.sh`
- [ ] Add test data naming convention to `SCENARIOS.md` conventions table
