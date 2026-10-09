# HandleIT TypeScript 项目

这是一个使用 HandleIT 创建的 TypeScript + pnpm 项目。

## 快速开始

### 1. 安装依赖
```bash
点击 "pnpm install" 按钮
```

### 2. 开发模式（实时编译）
```bash
点击 "tsc watch" 按钮
# TypeScript 会自动监听文件变化并编译
```

### 3. 编译项目
```bash
点击 "pnpm build" 按钮
```

### 4. 运行项目
```bash
点击 "pnpm start" 按钮
然后点击 "浏览器预览" 查看效果
```

## 项目结构

```
.
├── src/
│   └── index.ts          # TypeScript 源代码
├── dist/                 # 编译输出目录
├── package.json          # 项目配置
├── tsconfig.json         # TypeScript 配置
└── .handleit/config.json   # HandleIT 工作区配置
```

## 技术栈

- 🔷 **TypeScript** - 类型安全的 JavaScript 超集
- 📦 **pnpm** - 快速、节省磁盘空间的包管理器
- 🟢 **Node.js** - JavaScript 运行时

## 为什么选择 pnpm？

- ⚡ 更快的安装速度
- 💾 节省磁盘空间（硬链接共享依赖）
- 🔒 严格的依赖管理
- 🎯 与 npm/yarn 命令兼容

## 常用命令

- `pnpm install` - 安装依赖
- `pnpm build` - 编译 TypeScript
- `tsc watch` - 监听模式编译
- `pnpm start` - 运行编译后的代码

## 开发提示

- TypeScript 源代码放在 `src/` 目录
- 编译后的 JavaScript 在 `dist/` 目录
- 修改代码后需要重新编译
- 使用 watch 模式可以自动编译

Happy Coding with TypeScript! 🎉
