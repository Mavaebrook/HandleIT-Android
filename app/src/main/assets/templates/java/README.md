# HandleIT Java 项目

这是一个使用标准 Gradle 构建的 Java 项目模板。

## 项目结构

```
handleit-java-project/
├── build.gradle.kts          # Gradle 构建配置
├── settings.gradle.kts       # Gradle 项目配置
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/handleit/app/
│   │   │       ├── Main.java           # 主程序入口
│   │   │       └── Calculator.java     # 示例类
│   │   └── resources/
│   │       └── application.properties  # 配置文件
│   └── test/
│       └── java/
│           └── com/handleit/app/
│               └── CalculatorTest.java # 单元测试
└── .gitignore                # Git 忽略文件
```

## 快速开始

### 1️⃣ 安装依赖（首次使用）
前往 **终端 → 环境配置**，安装以下工具：
- ✅ OpenJDK 17
- ✅ Gradle

### 2️⃣ 初始化项目
1. 点击 **"🔧 初始化 Gradle Wrapper"** 按钮
   - 这会生成 `gradlew` 和 `gradle/` 目录
   - 首次运行会自动下载 Gradle 8.5

### 3️⃣ 构建和运行
- **构建项目**: 点击 "🔨 构建项目"
- **运行程序**: 点击 "▶️ 运行程序"
- **运行测试**: 点击 "🧪 运行测试"
- **打包 JAR**: 点击 "📦 打包 JAR"
- **清理构建**: 点击 "🧹 清理构建"

### 手动命令
```bash
# 使用 Gradle Wrapper（推荐）
./gradlew build
./gradlew run
./gradlew test

# 或直接使用 gradle
gradle build
gradle run
```

### 生成可执行 JAR
```bash
./gradlew jar
java -jar build/libs/handleit-java-project-1.0.0.jar
```

## 功能特性

✅ **标准 Gradle 项目结构**  
✅ **Java 17** 支持  
✅ **JUnit 5** 单元测试框架  
✅ **包管理** - Maven Central + 阿里云镜像  
✅ **Fat JAR** - 包含所有依赖的可执行 JAR  

## 添加依赖

在 `build.gradle.kts` 中添加依赖：

```kotlin
dependencies {
    implementation("com.google.guava:guava:32.1.2-jre")
    implementation("com.google.code.gson:gson:2.10.1")
}
```

## 自定义配置

- 修改 `build.gradle.kts` 更改构建配置
- 在 `src/main/java` 中添加新的 Java 类
- 在 `src/test/java` 中添加单元测试
- 编辑 `.handleit/config.json` 自定义 HandleIT 命令

Happy Coding! ☕
