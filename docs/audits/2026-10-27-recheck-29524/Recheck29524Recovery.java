import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository;
import com.weibo.talentintroduction.discovery.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;

/** One-off repair. No Spring application, ES, Apollo, schedulers or mail. Credentials stay in server env. */
public class Recheck29524Recovery {
  static final long ID=29524, COUNT=8594;
  static void require(boolean ok,String reason) { if(!ok) throw new IllegalStateException(reason); }
  public static void main(String[] args) {
    ((ch.qos.logback.classic.LoggerContext)org.slf4j.LoggerFactory.getILoggerFactory())
      .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(ch.qos.logback.classic.Level.WARN);
    boolean apply=args.length==2 && args[0].equals("apply");
    require(apply || (args.length==1 && args[0].equals("check")),"mode");
    DriverManagerDataSource ds=new DriverManagerDataSource(System.getenv("DB_URL"),System.getenv("DB_USERNAME"),System.getenv("DB_PASSWORD"));
    JdbcTemplate jdbc=new JdbcTemplate(ds); jdbc.setQueryTimeout(60);
    DiscoveryVerificationRepository repository=new DiscoveryVerificationRepository(jdbc);
    TransactionTemplate tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
    tx.setReadOnly(!apply);
    tx.executeWithoutResult(status -> {
      jdbc.execute("SET SESSION innodb_lock_wait_timeout=10");
      if(apply) jdbc.queryForObject("SELECT execution_id FROM discovery_verification_run WHERE execution_id=? FOR UPDATE",Long.class,ID);
      VerificationRun run=repository.findRun(ID);
      RecheckControl control=repository.recheck(run).getControl();
      require((control.getPhase()==RecheckPhase.PREPARING || control.getPhase()==RecheckPhase.INTERRUPTED) && !run.getInputClosed(),"not unfinished preparation");
      require(control.getScopeHash()==null && control.getScanned()==COUNT && control.getPreparationStage().equals("EVIDENCE"),"unexpected control");
      require(jdbc.queryForObject("SELECT COUNT(*) FROM discovery_recheck_input WHERE execution_id=?",Long.class,ID)==COUNT,"input count");
      require(jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_item WHERE owner_execution_id=?",Long.class,ID)==COUNT,"item count");
      require(jdbc.queryForObject("SELECT COUNT(DISTINCT expert_doc_id) FROM discovery_verification_item WHERE owner_execution_id=?",Long.class,ID)==COUNT,"duplicate doc");
      require(jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_item WHERE owner_execution_id=? AND (state<>'RESERVED' OR review_epoch<>? OR request_count<>0 OR admission_revision IS NOT NULL OR activated_at IS NOT NULL OR lease_token IS NOT NULL OR outcome IS NOT NULL OR projection_state<>'NOT_REQUIRED')",Long.class,ID,ID)==0,"items changed");
      require(jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_step s JOIN discovery_verification_item i ON i.id=s.item_id WHERE i.owner_execution_id=?",Long.class,ID)==0,"steps already exist");
      require(jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_attempt a JOIN discovery_verification_item i ON i.id=a.item_id WHERE i.owner_execution_id=?",Long.class,ID)==0,"requests already exist");
      com.fasterxml.jackson.databind.ObjectMapper mapper = com.fasterxml.jackson.module.kotlin.ExtensionsKt.jacksonObjectMapper()
          .findAndRegisterModules().disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
      long[] checked={0}; java.util.Map<String,Integer> normalizedFields=new java.util.TreeMap<>();
      jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)connection -> {
        try(java.sql.PreparedStatement statement=connection.prepareStatement("SELECT p.payload_json,i.snapshot_json,i.expert_doc_id,i.identity_hash,i.fact_fingerprint FROM discovery_verification_item i STRAIGHT_JOIN discovery_recheck_input p ON p.execution_id=i.owner_execution_id AND p.doc_id=i.expert_doc_id WHERE i.owner_execution_id=?",java.sql.ResultSet.TYPE_FORWARD_ONLY,java.sql.ResultSet.CONCUR_READ_ONLY)) {
          statement.setLong(1,ID);statement.setFetchSize(Integer.MIN_VALUE);statement.setQueryTimeout(60);
          try(java.sql.ResultSet rs=statement.executeQuery()) {
            while(rs.next()) {
              try {
                com.fasterxml.jackson.databind.JsonNode input=mapper.readTree(rs.getString(1)), frozen=mapper.readTree(rs.getString(2));
                // Exactly the existing prepare path: deserialize Input.profile, then serialize the typed profile.
                com.fasterxml.jackson.databind.JsonNode profile=mapper.valueToTree(mapper.treeToValue(input.path("profile"),com.weibo.talentintroduction.expert.domain.ExpertProfile.class));
                require(profile.equals(frozen.path("profile")),"normalized profile mismatch");
                input.path("profile").fieldNames().forEachRemaining(k -> { if(!input.path("profile").path(k).equals(profile.path(k))) { require(k.equals("hindex") && input.path("profile").path(k).isIntegralNumber() && profile.path(k).isNull(),"unexpected serialization difference"); normalizedFields.merge(k,1,Integer::sum); } });
                require(input.path("admissionRevision").equals(frozen.path("reviewInput").path("expectedAdmissionRevision")),"admission revision mismatch");
                require(input.path("beforeDecision").equals(frozen.path("reviewInput").path("beforeDecision")),"before decision mismatch");
                require(input.path("docId").asText().equals(rs.getString(3)) && frozen.path("docId").asText().equals(rs.getString(3)),"doc mismatch");
                require(frozen.path("identityHash").asText().equals(rs.getString(4)),"identity mismatch");
                require(frozen.path("factFingerprint").asText().equals(rs.getString(5)),"fingerprint mismatch");
                checked[0]++;
              } catch(java.io.IOException e) { throw new IllegalStateException("invalid frozen JSON",e); }
            }
          }
        }
        return null;
      });
      require(checked[0]==COUNT,"input roster mismatch");
      System.out.println("NORMALIZED_INPUT_OK rows="+checked[0]+" existingSerializationDifferences="+normalizedFields);
      String hash=repository.recheckScopeHash(ID);
      System.out.println("CHECK_OK id="+ID+" rows="+COUNT+" revision="+run.getRevision()+" hash="+hash+" maxHeap="+Runtime.getRuntime().maxMemory());
      if(!apply) return;
      require(hash.equals(args[1]),"hash changed since check");
      // Only the fully persisted, still unactivated incident roster is recoverable. No generic replay permission.
      if(control.getPhase()==RecheckPhase.INTERRUPTED) {
        require(jdbc.update("UPDATE discovery_verification_run SET recheck_json=JSON_SET(recheck_json,'$.control.phase','PREPARING'),revision=revision+1 WHERE execution_id=? AND revision=? AND input_closed=0",ID,run.getRevision())==1,"recovery CAS");
        run=repository.findRun(ID);
      }
      require(repository.compareAndSetRecheckControl(ID,run.getRevision(),new RecheckControl(RecheckPhase.READY,hash,"EVIDENCE",COUNT),Instant.now()),"ready CAS");
      require(jdbc.update("UPDATE task_execution SET status='SUCCESS',success_count=?,failure_count=0,error_message=NULL,finished_at=NOW(3),updated_at=NOW(3),handled_by='codex-29524-oom-repair',handled_at=NOW(3),result_summary=? WHERE id=? AND task_type='DISCOVERY_RECHECK_PREPARE' AND status IN ('RUNNING','INTERRUPTED')",COUNT,"{\"runId\":29524,\"frozen\":8594,\"phase\":\"PREPARATION_ONLY\",\"recovery\":\"BOUNDED_HASH_OOM_REPAIR\"}",ID)==1,"task CAS");
      System.out.println("RECOVERED_READY awaiting user confirmation; no items activated");
    });
  }
}
