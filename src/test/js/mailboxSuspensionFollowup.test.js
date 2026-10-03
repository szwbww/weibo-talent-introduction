const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/resources/static/app.js', 'utf8');
function badgeHarness(api) {
  const elements = {'#unmatchedBadgeHigh': {}, '#unmatchedBadgeNormal': {}};
  const ctx = {api, $: s => elements[s], HIGH_PRIORITY_REASON_TYPES: new Set(['NOT_INTERESTED', 'QA_NO_MATCH'])};
  vm.createContext(ctx);
  vm.runInContext(source.slice(source.indexOf('let unmatchedBadgeRefreshSeq'), source.indexOf('const MAILBOX_TAG_BADGE_CLASS')), ctx);
  return {ctx, elements};
}
test('badge uses session scoped mail count and preserves high versus normal', async () => {
  const requests=[];
  const {ctx,elements}=badgeHarness(async url => { requests.push(url); return {manualReviewTotal:4,countsByReasonType:{QA_NO_MATCH:2,UNMATCHED_CONTACT:1,UNKNOWN:1}}; });
  await ctx.refreshUnmatchedBadge();
  assert.deepEqual(requests,['/api/mail/mailbox/conversations/pending-badge']);
  assert.equal(elements['#unmatchedBadgeHigh'].textContent,2);
  assert.equal(elements['#unmatchedBadgeNormal'].textContent,2);
});
test('badge zero hides both and ignores older response arriving later', async () => {
  const resolves=[];
  const {ctx,elements}=badgeHarness(() => new Promise(resolve => resolves.push(resolve)));
  const first=ctx.refreshUnmatchedBadge(); const second=ctx.refreshUnmatchedBadge();
  resolves[1]({manualReviewTotal:0,countsByReasonType:{}}); await second;
  resolves[0]({manualReviewTotal:9,countsByReasonType:{QA_NO_MATCH:9}}); await first;
  assert.equal(elements['#unmatchedBadgeHigh'].hidden,true);
  assert.equal(elements['#unmatchedBadgeNormal'].hidden,true);
});
test('initial badge update delegates to the same suspension-aware source', async () => {
  let url;
  const {ctx}=badgeHarness(async value => {url=value;return {manualReviewTotal:0,countsByReasonType:{}};});
  await ctx.updateUnmatchedBadge();
  assert.equal(url,'/api/mail/mailbox/conversations/pending-badge');
});
