import java.time.*;
import java.time.zone.ZoneRulesProvider;
import java.util.*;

// Standalone read-only JDK 11 probe; not application code.
class TimezoneProbe {
    public static void main(String[] args) {
        System.out.println("java=" + System.getProperty("java.version"));
        System.out.println("tzdb=" + ZoneRulesProvider.getVersions("America/Sao_Paulo").keySet());
        if (args.length > 0) {
            try {
                Set<Integer> br = new TreeSet<>(Collections.reverseOrder());
                for (String id : java.nio.file.Files.readAllLines(java.nio.file.Paths.get(args[0]))) {
                    br.add(ZoneId.of(id).getRules().getValidOffsets(LocalDateTime.parse("2026-10-07T09:00")).get(0).getTotalSeconds());
                }
                System.out.println("BR 2026-10-07T09:00 uniqueOffsetsSeconds=" + br);
            } catch (java.io.IOException ex) { throw new RuntimeException(ex); }
        }
        for (String item : new String[]{
            "America/Sao_Paulo|2026-10-07T09:00|2026-10-07T09:30",
            "Brazil/East|2026-10-07T09:00|2026-10-07T09:30",
            "America/New_York|2026-03-08T01:30|2026-03-08T03:30",
            "America/New_York|2026-03-08T02:30|2026-03-08T03:30",
            "America/New_York|2026-11-01T01:30|2026-11-01T02:30",
            "America/Denver|2026-01-15T09:00|2026-01-15T09:30",
            "America/Phoenix|2026-01-15T09:00|2026-01-15T09:30",
            "America/Denver|2026-07-15T09:00|2026-07-15T09:30",
            "America/Phoenix|2026-07-15T09:00|2026-07-15T09:30"
        }) {
            String[] p = item.split("\\|");
            ZoneId z = ZoneId.of(p[0]);
            LocalDateTime a = LocalDateTime.parse(p[1]), b = LocalDateTime.parse(p[2]);
            List<ZoneOffset> ao = z.getRules().getValidOffsets(a), bo = z.getRules().getValidOffsets(b);
            System.out.println(item + " validStart=" + ao + " validEnd=" + bo +
                " noon=" + z.getRules().getOffset(a.toLocalDate().atTime(12,0).toInstant(ZoneOffset.UTC)));
            if (ao.size() == 1 && bo.size() == 1) {
                Instant start = a.toInstant(ao.get(0));
                System.out.println(" startUtc=" + start + " beijing=" + start.atZone(ZoneId.of("Asia/Shanghai")));
            }
        }
    }
}
