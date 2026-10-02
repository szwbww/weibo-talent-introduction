# 时区数据审计

2026-10-02，实际运行 `/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java /tmp/ContactTimingZoneAudit.java`，调用 `ZoneRulesProvider.getVersions("Asia/Shanghai").lastKey()` 与 `ZoneId.getAvailableZoneIds()`。

- 当前 JDK 11 tzdb：2021e。
- `/usr/share/zoneinfo/+VERSION`：2026c。
- 本地 IANA zone.tab：247 个国家/地区，418 条时区关系。
- JDK 11 不支持：America/Ciudad_Juarez, America/Coyhaique, Europe/Kyiv。
- 官方原始目录：[IANA zone.tab](https://data.iana.org/time-zones/tzdb/zone.tab)、[IANA zone1970.tab](https://data.iana.org/time-zones/tzdb/zone1970.tab)。本功能按单个 ISO 国家代码选择，采用 zone.tab 的单国家映射；不把中文标签解析成国家关系。
- 本次未检查生产 JVM 实际 tzdb，也未更新任何 JVM。目录更新不是实时网络依赖。
- 提案 JSON 的默认值：已展示的 28 个国家沿用预览默认；其余采用当前快照 zone.tab 的该国首行。这是公开、可覆盖的产品默认规则，不宣称默认值等于首都、人口最多城市或真实所在地。
- 多时区列表补齐 IANA 关系，例如 BR=16、US=29、CA=23、AU=12、MX=12、CN=2、ES=3、PT=3；原预览的列表不是完整国家映射。
- 提案文件 SHA256：`c27ad26d63ccc151a82844d757b6494bfa73ba69c772d4f45dc627c6fa6b5480`。国家中文名来自当前 JDK Locale；时区中文名来自仓库 meeting-timezones-zh.properties，未命中则直接显示 IANA id，不编造名称。

复查程序（在拟部署的同版本 JDK 11 执行）：

```java
System.out.println(java.time.zone.ZoneRulesProvider.getVersions("Asia/Shanghai").lastKey());
for (String z : new String[]{"America/Ciudad_Juarez","America/Coyhaique","Europe/Kyiv"})
    System.out.println(java.time.ZoneId.of(z));
```

G-0：执行前提供 JDK 11 更新后 tzdb >= 2026c 的记录，且全部 418 个目录 id 均 `ZoneId.of` 成功。仅“支持 id”不能证明夏令时规则新鲜；再验证 2026-07-01 墨西哥城 UTC-06、纽约 UTC-04、伦敦 UTC+01。不升级 Java 大版本、不用 UTC 常量替代失败的真实时区。生产数据时区来源另由 TimeZoneConfig/ImapMailReceiveService 证明，见 code-baseline.md。
