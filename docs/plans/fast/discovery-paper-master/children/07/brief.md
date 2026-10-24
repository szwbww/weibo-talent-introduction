# Child07 — Apollo saved settings UI and per-run channel log

- Exact approved child docs/plans/2026-10-23/discovery-paper-07-apollo-frontend.md at commitc18dbc060dcf4c8440f853ede4d1d3aaf737994f; read fullplan including verbatimS1/S2DOM andS3CSS.
- Master docs/plans/2026-10-23/discovery-paper-master.md at0199430a0b4f30f89b8fb240d58880167471b512, amendmentsA1–A14. Userrequests continuousfix until READY_FOR_HUMAN_REVIEW so review-fast-p canrun separately, notfinalreview here.
- Worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master, branchfast/discovery-paper-master. child_base_sha=f28acd2d1ae1cc2a7ae8b84d0b843d2a4061a9a0. Prior06b LIGHT_PASS evidence24b68bad1af4038add786364767da4d78549364a; fullmanifestdirty/clean checkpointb1584a30ef271b6280533cb1d6f648a8b8917933 VALID. No completedchildreplay.

## Authorized Files — exactlyfive
- src/main/resources/static/index.html
- src/main/resources/static/styles.css
- src/main/resources/static/app.js
- src/test/js/discoveryVerification.test.js
- src/test/js/discoveryApolloSettings.test.js (new)
No backend, otherfrontendmodule/test, config/migration changes. Read relevantpatterns and references beforechangingexportedsymbols; LSP ifavailable. Beforeproductedits batch boundedpreflight directoldDOM/launchhelpers/assethardcodedtestkey consumers to identify any provennecessaryomissions atonce, no speculativeexpansion. No planediting byimplementer; reportexactscopeconflict ifunavoidable.

## Prior interfaces
06a DiscoveryApolloSettingsController/Service are authoritativeAPI/defaults/CAS; readactualsymbols/DTOs, no frontendlayerdefaults orlocalStorage. Independentsearch/detail, false/truepreserved, effectivefalse;401/400/409/503. No save-timeproviderrequest orkeyexposure. SettingsAPIreadiness separatefromexistingcapabilitiesdeployment/key/worker booleans.
06b summary optionalapolloAccess FrozenApolloAccess(schemaVersion1,settingRevision,searchEnabled,detailEnabled,sourceSAVED/DEPLOYMENT_DEFAULT/LEGACY), apolloAttempts(search/detail/other/pendingDispatch Long; pendingoverlapschannels). Existingrunmissingmember explicitLEGACYfromrun.apolloRequested; missing/malformedrun nullunknown. Knownemptyrun channel0, absentunknownnot0. Queryconstructorandcapabilitiesready/apolloDefaultcompatibility/admissioncountsunchanged. paid-disabledreasonhumantext supplied. Do notderivehistoricalsnapshotfromcurrentsavedform.
05 papersUI/module/CSSmustremainunchanged and usable. 1440×1080 modal1180px/80vh,643×900 narrow595px/80vh retained. Lifecycleoldresponses/timers/DOMURLs mustnotleak.

## Binding behavior
FullI1–I4/T1–T3/A1–A3. saved/draft/revision/status/requestSeq/dirty state onlyUI, capabilities+settingsGET bothgeneration/taskTypeisolated; checkboxdraftonly, noimplicitPUT. validGETrequired; GETfailure retry/no deploymentsavedfallback. Draftdirtyblocksallpage launchpaths, reloadrestores; PUTCASonlyonceonrepeatclick, disableinputwhileinflight, successserverreadbacksaved,409exactguidance/503draftretained. Searchfalse/detailtrueindependence and effectivewarning; keyconfiguredbooleanonly. Purelocaldiscovery allowed when searchoff, no saved/draftapollobool override inlaunch.
postDiscoveryLaunch defaultnull; onlyexplicitexternalnonnullwritesapolloAssist forcompatibility. All pagecalls(default/keyword/continuous)omitargument. Dispose/close/switchclearsstate/bindings and staleGET/PUTcannotmutate newhost; summarypollneveroverwritesdraft. EscapeallHTML.
VerbatimS1DOM/S2summarylineandS3CSS; noinline, undeclaredclasses/globalstylechange. Keepoldadmissioncounts andruleshint. LEGACYexplicit,unknownunrecorded, channelsattemptsnotcredits. indexALLlocalCSS/JSsamekey20261023-discovery-paper-apollo-settings includingpapersmodule; preservemoduleorder/unversionedruntime policyperexistingtests.

## Required finalchecks and actualsurface
Useexecute-p. Skipbuild/lint/tests/formattersmidflight. Afterfinaledits run:
- node --check src/main/resources/static/app.js
- node --test src/test/js/discoveryApolloSettings.test.js src/test/js/discoveryVerification.test.js src/test/js/discoveryContinuousRun.test.js src/test/js/discoveryReview.test.js src/test/js/discoveryPapers.test.js
- CompleteaffectedJS gate: node --test src/test/js/*.test.js (masterrequirescompleteaffectedJS; includesresourcecontractconsumers). Preserve meaningfulcontracts, don'trepinincidentaldefaults/wording.
- RequiredmasterWARbuild onceinfinalchild: env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn package -DskipTests . This explicitlyskipsJVMtestexecution, notcompile; reportexactskip/buildstatus, WARpath/hash. Do notcallthiswhole-systemsuite/PASS; independentlyproven06bchecksarepreviouschildevidence, notfresh07JVMverification. No controller-ownedfinalsuite.
- actualChromium browser.open realindex/app/styles viaisolatedloopbackserver andcontrolledAPI(no livecredentials/Apollo), exerciseactualmodal/settings/save/reopen/launch/log/lifecycle at1440×1080,643×900 and<=640 breakpoint. Capture retainedscreenshots under child07 artifactsubdir; verify2colsabove640,1at<=640,14pxcheckbox/disabledopacity.45/focus#3b82f6, fourindependentcombos,dirtylaunchblock/noPUTuntilsave,duplicateonePUT,409/503draft,slowAignoredafterB/close,summaryoldpaidversuscurrentfree/channels3/2/1/pending1/missingunknown, papers/review/history/othertaskretained. Browserhelpersmustclose tabs/servicesafter; no testsalone asvisualproof. GET404backendnotreadydisabled. Realassets/fixtureHTTPscopeonly, nofakeclaimrealbackendSQL/restart/vendorbilling.
Keeppermanenttestsconsumerbehavior/boundaries/transitions, actualHTMLIDexistence(extraassertionsneededbecauseDOMstubs),notmockecho/sourcewiring-only. ExactCSScontractsourcecomparisonisexplicitplanrequirementexception. No privateoriginals/LLM/paidcalls.

## Output and permissions
Commitonlycompletedfiveauthorizedproduct/testfiles locally feat(fast-p): implement 07. Excludefast-preports/screenshots/ledger. Appendfullresult to docs/plans/fast/discovery-paper-master/children/07/execution.md (commands/exits/counts/browserproof/limits/WARhash/currentidentities/bareSHA). Featureplan+executionreportservesdocumentation; noextraunlistedchangelogfile. Return READY_FOR_VERIFICATION|BLOCKED|PLAN_CONFLICT,commitSHA,commandsummary,reportpath. No fullverify-p/review-p/repair-p/review-fast-p, push/merge/rebase/amend/reset/deployment/newworktree oraggregateaudit. Freshindependentlightverifiercontroller-owned.
