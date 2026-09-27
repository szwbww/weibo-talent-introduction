# HTML contact recall fixture

`html-contact-recall.zip` contains exact-byte REAL_ORIGINAL HTML from the read-only diagnostic `docs/audits/2026-09-26-deep-discovery-diagnosis/evidence/original-sources.zip` (SHA-256 `0607b402b9ffdeadcc8726710d3031277fbc7ee05f1989caf2c7da25bc0f4205`) and original OpenAlex metadata from `docs/audits/2026-09-26-deep-discovery-diagnosis/evidence/sources/<work-id>/metadata.json`. Its `manifest.json` records each byte hash, archive member and provenance. No HTML was regenerated, shortened or edited; metadata supplies names and IDs only, not mailbox ownership.

- `W3094704314`: corresponding-author-list independently names Vijay Kumar with `mailto:vijaykumarchahar@gmail.com`.
- `W3135028703` and `W3194730353`: separate works each independently name Iqbal H. Sarker with `mailto:msarker@swin.edu.au`; these are two paper relations, not two experts.
- `W4381304672`: observed Anubis challenge (title plus challenge script), not usable fulltext.

Existing Edward/Hang ownership cases remain in `source-email-ownership-cases.json`. Inline negative/URI and academic-body controls are SYNTHETIC, not original evidence. Tests exercise the actual original HTML through the parser and downloader; external transport, validation, eligibility and Elasticsearch are isolated substitutes.
