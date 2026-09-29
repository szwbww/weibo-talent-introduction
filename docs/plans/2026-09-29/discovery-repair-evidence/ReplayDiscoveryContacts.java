import java.util.*;
import java.util.zip.*;
import java.nio.file.*;
import com.fasterxml.jackson.databind.*;
import com.weibo.talentintroduction.discovery.domain.*;
import com.weibo.talentintroduction.discovery.service.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import kotlin.Unit;

class ReplayDiscoveryContacts {
 public static void main(String[] args) throws Exception {
  ObjectMapper mapper=new ObjectMapper();
  try(ZipFile z=new ZipFile(args[0])) {
   for(String key:new String[]{"crea","nguyen","everett","lumma","kumar","debie"}) {
    JsonNode meta=mapper.readTree(z.getInputStream(z.getEntry(key+".openalex.json")));
    List<PaperAuthor> authors=new ArrayList<>();
    for(JsonNode a:meta.path("authorships")) {
     String name=a.path("author").path("display_name").asText();
     int at=name.indexOf(' ');
     String given=at<0?name:name.substring(0,at),family=at<0?null:name.substring(at+1);
     String orcid=a.path("author").path("orcid").asText(null);
     if(orcid!=null)orcid=orcid.replace("https://orcid.org/","");
     String id=a.path("author").path("id").asText(null);
     if(id!=null)id=id.replace("https://openalex.org/","");
     authors.add(new PaperAuthor(given,family,orcid,null,a.path("is_corresponding").asBoolean(),null,null,id));
    }
    byte[] bytes=z.getInputStream(z.getEntry(key+".pdf")).readAllBytes();
    try(PDDocument pdf=PDDocument.load(bytes)) {
     StringBuilder text=new StringBuilder();
     List<PdfAuthorContactLayout.Contact> contacts=PdfAuthorContactLayout.INSTANCE.collect(pdf,2,authors,1,()->Unit.INSTANCE,(p,s)->{if(p<=2)text.append(s).append('\n');return Unit.INSTANCE;});
     List<AuthorEmail> out=SourceAuthorEmailResolver.INSTANCE.resolvePdf(text.toString(),authors,contacts,Collections.emptyList());
     Map<String,Object> row=new LinkedHashMap<>(); row.put("case",key);row.put("contacts",contacts);row.put("resolved",out);
     System.out.println(mapper.writeValueAsString(row));
    }
   }
  }
 }
}
