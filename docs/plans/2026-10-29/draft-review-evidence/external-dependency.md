# 新依赖核验（设计选择，尚未安装）

https://jsoup.org/download：查阅时Maven坐标org.jsoup:jsoup:1.23.2，Java8及以上，无必须runtime依赖。
https://jsoup.org/apidocs/org/jsoup/safety/Cleaner：Cleaner按Safelist清洗body fragment。
https://jsoup.org/apidocs/org/jsoup/safety/Safelist：可限定tag/attribute/protocol。

此依赖只用于新审核草稿；旧管理员正文不改变。JDK11编译及安全清洗测试属于01执行门禁，当前未运行。
