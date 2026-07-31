# Competition poster generator

`generate_inner_cosmos_poster_v4.py` is the current A1 poster source. Versions 1–3 are retained as
design history so changes remain reviewable; do not present their rendered output as current.

Requirements:

- Python 3.12+
- `pypdf`
- `reportlab`
- Windows Segoe UI and Georgia fonts
- the supplied NUS A1 PDF template
- the checked-in product screenshots referenced from `evidence/g9/FINAL-E2E-001/screenshots`

Example:

```powershell
python .\scripts\poster\generate_inner_cosmos_poster_v4.py `
  --template C:\path\to\nus-a1-template.pdf `
  --output .\output\pdf\inner-cosmos-cloud-native-poster-v4.pdf
```

Generated `output/`, preview PNGs, and temporary render directories are intentionally ignored.
Publish the reviewed PDF as a competition/release artifact rather than committing iterative binary
renders into the source tree.
