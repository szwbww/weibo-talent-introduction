# 项目元信息（供 multi-ai-kit 使用）

test_command: JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

build_command: JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

frontend_paths:
  - src/main/resources/static

exclude_paths:
  - target
  - .idea
  - .antigravitycli
  - docs/reviews
  - docs/plans
  - docs/multi-ai-runs

frontend_enabled: true
