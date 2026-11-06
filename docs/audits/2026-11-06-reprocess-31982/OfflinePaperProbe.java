import com.fasterxml.jackson.databind.*;
import com.weibo.talentintroduction.config.PdfExtractionProperties;
import com.weibo.talentintroduction.discovery.archive.*;
import com.weibo.talentintroduction.discovery.service.DiscoveryPaperIdentityReplayTool;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import com.weibo.talentintroduction.discovery.domain.PaperAuthor;
import com.weibo.talentintroduction.discovery.service.PdfAuthorContactLayout;

/** File-only diagnostic. No Spring context, DB connection or business writer. */
public class OfflinePaperProbe {
    static final ObjectMapper mapper = new ObjectMapper();
    static String hash(String s) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format("%02x", b));
        return out.substring(0,24);
    }
    public static void main(String[] args) throws Exception {
        JsonNode input = mapper.readTree(System.in);
        Function0<Unit> noNetwork = () -> { throw new IllegalStateException("NETWORK_FORBIDDEN"); };
        Method factory = Arrays.stream(DiscoveryPaperIdentityReplayTool.class.getDeclaredMethods())
            .filter(m -> m.getName().startsWith("analyzer$") && m.getParameterCount()==2
                && m.getParameterTypes()[0]==PdfExtractionProperties.class)
            .findFirst().orElseThrow();
        PaperBodyAnalyzer analyzer = (PaperBodyAnalyzer)factory.invoke(
            DiscoveryPaperIdentityReplayTool.INSTANCE, new PdfExtractionProperties(), noNetwork);
        Function0<Unit> checkpoint = () -> Unit.INSTANCE;
        for (JsonNode row : input) {
            String bodyHash = row.path("bodyHash").asText();
            Path path = Path.of(args[0],bodyHash.substring(0,2),bodyHash+".bin");
            if (!Files.exists(path)) path=Path.of(args[0],bodyHash+".bin");
            PaperBodyInput body = new PaperBodyInput(bodyHash,
                ArchiveAttemptKind.valueOf(row.path("kind").asText()), Files.readAllBytes(path));
            PaperBodyComputation result = analyzer.parse(body,row.path("metadata"),checkpoint);
            var output = mapper.createObjectNode();
            output.put("recordId",row.path("recordId").asLong());
            output.put("bodyHash",bodyHash);
            output.set("parserVersions",mapper.valueToTree(analyzer.versions()));
            output.set("reasons",mapper.valueToTree(result.getReasons()));
            if (row.path("recordId").asLong()==31863) {
                try (PDDocument document=PDDocument.load(path.toFile())) {
                    var positions=new ArrayList<TextPosition>();
                    var stripper=new PDFTextStripper() {
                        protected void processTextPosition(TextPosition p) {
                            positions.add(p); super.processTextPosition(p);
                        }
                    };
                    stripper.setStartPage(1);stripper.setEndPage(1);stripper.getText(document);
                    var mapperField=DiscoveryPaperIdentityReplayTool.class.getDeclaredField("mapper");
                    mapperField.setAccessible(true);
                    var km=(ObjectMapper)mapperField.get(null);
                    var authors=new ArrayList<PaperAuthor>();
                    for (var a:row.path("metadata").path("authors")) authors.add(km.treeToValue(a,PaperAuthor.class));
                    var cls=PdfAuthorContactLayout.class;
                    var toLines=cls.getDeclaredMethod("toLines",int.class,List.class,float.class);toLines.setAccessible(true);
                    var lines=(List<?>)toLines.invoke(PdfAuthorContactLayout.INSTANCE,1,positions,document.getPage(0).getMediaBox().getWidth());
                    var contactLines=output.putArray("contactLines");
                    for(var line:lines) {
                        String text=line.toString();
                        if(text.contains("@") || text.contains("* Corresponding"))
                            contactLines.add(text.replaceAll("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}","[EMAIL]"));
                    }
                    var contacts=PdfAuthorContactLayout.INSTANCE.collect(document,2,authors,1,checkpoint,(page,text)->Unit.INSTANCE);
                    output.put("layoutContacts",contacts.size());
                    var contactHashes=output.putArray("layoutEmailHashes");
                    for(var c:contacts) contactHashes.add(hash(c.getEmail().trim().toLowerCase(Locale.ROOT)));
                    output.put("layoutMailboxMatchesExtracted",contacts.stream().anyMatch(c ->
                        result.getEmails().stream().anyMatch(e -> e.getEmail().equalsIgnoreCase(c.getEmail()))));
                    var method=Arrays.stream(cls.getDeclaredMethods()).filter(m->m.getName().equals("authorHeader")).findFirst().orElseThrow();method.setAccessible(true);
                    var header=method.invoke(PdfAuthorContactLayout.INSTANCE,lines,document.getPage(0).getMediaBox().getHeight(),authors);
                    output.put("header",Objects.toString(header,"MISSING"));
                    if(header!=null) {
                        var areaMethod=Arrays.stream(cls.getDeclaredMethods()).filter(m->m.getName().equals("authorArea")).findFirst().orElseThrow();areaMethod.setAccessible(true);
                        var area=areaMethod.invoke(PdfAuthorContactLayout.INSTANCE,lines,header,document.getPage(0).getMediaBox().getHeight());
                        output.put("authorArea",Objects.toString(area,"MISSING"));
                        var ownersMethod=cls.getDeclaredMethod("markerOwners",String.class,List.class);ownersMethod.setAccessible(true);
                        output.set("markerOwners",mapper.valueToTree(ownersMethod.invoke(PdfAuthorContactLayout.INSTANCE,Objects.toString(area,""),authors)));
                    }
                    var fonts=output.putArray("fonts");
                    for(var key:document.getPage(0).getResources().getFontNames()) {
                        var font=document.getPage(0).getResources().getFont(key);
                        fonts.add(font.getName()+" embedded="+font.isEmbedded());
                    }
                }
            }
            var emails=output.putArray("emails");
            for (var contact: result.getEmails()) {
                var item=emails.addObject();
                item.put("emailHash",hash(contact.getEmail().trim().toLowerCase(Locale.ROOT)));
                item.put("bound",contact.getGivenNames()!=null && !contact.getGivenNames().isBlank()
                    && contact.getFamilyNames()!=null && !contact.getFamilyNames().isBlank());
                item.put("nameHash",hash((Objects.toString(contact.getGivenNames(),"")+" "+
                    Objects.toString(contact.getFamilyNames(),"")).trim().toLowerCase(Locale.ROOT)));
            }
            System.out.println(mapper.writeValueAsString(output));
        }
    }
}
