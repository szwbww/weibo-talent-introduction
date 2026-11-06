# Microsoft email routing implementation plan

> Execution: inline, on local main as requested. User approved MX identification, retained evidence, domain caching, discovery integration, historical lookup and list display/filtering. No deployment or historical production mutation requested.

**Goal:** Mark confirmed Microsoft inbound routing, including university custom domains, without mistaking third-party gateways or DNS failures for non-Microsoft hosting.

**Architecture:** Extend the existing MX client with complete records; preserve the existing FOUND / NOT_FOUND / DNS_ERROR validity API. Bounded process-local domain cache (5,000 domains, six-hour successful lookup lifetime, one-minute DNS-error lifetime, same-domain single-flight; restart clears cache). Persist independent `emailRouting` evidence in existing ES documents, no new database/table/job framework. Classification concerns inbound routing, not proof of mailbox hosting.

**Contract:**
- I1: MICROSOFT only for known consumer domains or all highest-priority MX servers matching official Microsoft suffixes. Mixed/third-party/failed/absent MX => UNKNOWN, never a false negative claim. Store domain, status, reason, MX records and UTC checkedAt.
- I2: No change to email validity, identity, qualification, admission or sending rules. Invalid email must not produce DNS traffic. Existing MxLookupClient lambda/test consumers stay compatible.
- I3: New discovery paper and ORCID RAW maps carry evidence after snapshot freezing; promotion continues whole-source passthrough. Three mappings and both profile readers include the field. Evidence for a different current email domain is treated as unchecked.
- I4: Historical lookup is an authenticated, bounded per-document action from the review list, applied by CAS only to existing RAW/CANDIDATE/APPLICATION documents with the same domain. Only emailRouting changes. Missing layers are not created. Failed/conflicting layers are reported, not counted as successful.
- I5: Review list has Microsoft / unknown / unchecked filter, evidence display and a “补查本页收信服务” button with progress. Filter propagates through list, snapshot, review-all and recheck-all-matching; ALL_PENDING retains its explicit all-pending semantics. Page reads never invoke DNS.
- I6: No overwrite of pre-existing unrelated working-tree changes. No online update or publish in this task. Local commit authorized by the subsequent user request.

## File responsibilities / steps
- [x] MX details/cache/classification tests first; `expert/service/{MxLookupClient,DnsMxLookupClient}.kt`, new `expert/domain/EmailRouting.kt` and `expert/service/EmailRoutingService.kt`. Preserve `EmailValidationService` validity flow; DNS client lookup and lookupDetails share one cache.
- [x] Add `emailRouting` to `ExpertProfile`, `ExpertSearchService` sourceFields/converter, `ExpertIndexWriterService.discoveryProfile` and CAS-only writer, all `resources/es/orcid_info_*.json` mappings. Add discovery toIndexMap integration through existing EmailValidationService dependency, after qualification snapshot freezing. Exclude the non-qualification emailRouting field from DiscoveryVerificationRegistrationService.factsWithoutCountry equality so historical lookup cannot invalidate resuming a reservation.
- [x] Add narrow `discovery/service/DiscoveryEmailRoutingService.kt` and controller for authenticated historical lookup; test complete/partial/conflict/mismatching-domain behavior using mocked ES boundaries.
- [x] Propagate review DTO/filter through DiscoveryReviewScanService, DiscoveryReviewController, DiscoveryReviewService and DiscoveryRecheckService. Query snapshot serialization automatically includes the field. Test ES query filtering and payload parity.
- [x] Frontend app.js/index.html/styles.css and discovery-recheck.js filter allowlist: label/evidence inside existing expert/email cell, select in filter toolbar, per-page lookup button. Text via textContent, existing bsc-input/button styles; routing badge inline-flex, max-width 100%, font-size12px, line-height1.5, padding2px8px, border1px solid, radius6px; Microsoft blue (#1d4ed8/#eff6ff/#bfdbfe), unknown slate (#475569/#f8fafc/#cbd5e1). Evidence wraps overflow-wrap:anywhere. Responsive existing toolbar wraps. Disable lookup while running; capture displayed IDs and page generation; navigation stops launching more requests. Do not repurpose review selection/action semantics. Bump shared static cache keys together.
- [x] Verify targeted JVM tests (Java11 Maven), JS behavior tests + syntax, migration-free startup mapping declarations, write-path audit and git diff --check. Record actual results under docs/audits/2026-11-06-email-routing/. Human acceptance: after user deployment inspect live discovery labels, history lookup and filter counts.

## Source evidence
- DnsMxLookupClient.lookup currently discards MX hosts after first valid record.
- ExpertDiscoveryService.buildProfile/buildOrcidProfile -> toIndexMap -> indexToRaw.
- ExpertIndexService.bootstrapMappings loads all three es/orcid_info_*.json declarations.
- DiscoveryReviewScanService.searchBody/enrich are shared list/all-matching scan seam.
- DiscoveryReviewQuerySnapshotService hashes serialized filter and stores row_json; no schema change needed.
- ExpertIndexWriterService.readDiscoveryDocument exposes seqNo/primaryTerm for compare-and-set patching.
- Official rules: https://learn.microsoft.com/en-us/microsoft-365/enterprise/urls-and-ip-address-ranges ; gateways: https://learn.microsoft.com/en-gb/exchange/mail-flow-best-practices/manage-mail-flow-using-third-party-cloud

## Completion
Implemented on local main. Machine checks: 309 JVM tests and 2,058 frontend tests passed; diff whitespace check passed. Final source hashes and verification scope: docs/audits/2026-11-06-email-routing/verification.json. Production/manual acceptance remains pending; local commit authorized by the subsequent user request; no deployment performed.
