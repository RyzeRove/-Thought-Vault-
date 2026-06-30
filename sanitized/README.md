# 思维札记 (Thought Vault)

一款极简 Android 思考记录工具 —— 只管打字保存，AI 自动分类润色，生成日/周/月/季/年 Markdown 总结。

## 工作原理

```
你打字（口语） → APP 保存到群晖 NAS → 凌晨 DeepSeek AI 自动分类+润色 → 生成整洁日报/周报/月报/季报/年报
```

## 项目结构

```
Plan/
├── thought-vault/          # Android APP（Kotlin + Jetpack Compose）
│   └── app/src/main/java/com/example/thoughtvault/
│       ├── data/           # 数据层（WebDAV 客户端、Room 缓存）
│       ├── domain/         # 领域层（Entry 模型、Use Cases）
│       └── presentation/   # 界面层（Home/Settings/History/Detail）
├── nas-scripts/            # NAS 端 Python 脚本
│   ├── common.py           # 共享工具 + DeepSeek API 调用
│   ├── daily_summary.py    # 日报生成（分类+润色）
│   ├── weekly_summary.py   # 周报生成
│   ├── monthly_summary.py  # 月报生成
│   ├── quarterly_summary.py # 季报生成
│   ├── yearly_summary.py   # 年报生成
│   ├── categories.json     # 13 分类定义
│   └── run_all.sh          # 历史数据回填工具
└── docs/plans/             # 实施计划文档（待补充）
```

## 快速开始

### 🔧 部署前需修改的文件

> 以下两处已脱敏为占位符，部署时需替换为你自己的信息。

| 文件 | 位置 | 占位符 | 替换为 |
|------|------|--------|--------|
| `nas-scripts/common.py` | 第 16 行 `BASE_DIR` | `<USER>` | 你的 NAS 用户名 |
| `thought-vault/app/src/main/res/xml/network_security_config.xml` | 第 9 行 `domain` | `your-nas-domain` | 你的 NAS 域名 |

### 前置条件

- **Android Studio** Hedgehog (2024.1+) 或更新版本
- **群晖 NAS** 已安装 WebDAV Server 套件
- **DeepSeek API Key** ([platform.deepseek.com](https://platform.deepseek.com))

### 1. Android APP 构建

```bash
# 用 Android Studio 打开 thought-vault/ 目录
# 等待 Gradle 同步完成
# 连接手机，点击 Run 'app'
```

首次启动 APP → 设置 → 填入 NAS WebDAV 地址和账号。

### 2. NAS 端部署

```bash
# SSH 到群晖
ssh admin@your-nas-ip

# 创建目录结构
mkdir -p /var/services/homes/<USER>/thoughts/{config,scripts,raw,daily,weekly,monthly,quarterly,yearly,.sync}

# 上传脚本
scp nas-scripts/*.py nas-scripts/*.json nas-scripts/*.sh \
    admin@your-nas-ip:/var/services/homes/<USER>/thoughts/scripts/

# 配置 API Key
echo "sk-你的deepseek-api-key" > /var/services/homes/<USER>/thoughts/config/api_key.txt
chmod 600 /var/services/homes/<USER>/thoughts/config/api_key.txt

# 复制分类配置
cp /var/services/homes/<USER>/thoughts/scripts/categories.json /var/services/homes/<USER>/thoughts/config/

# 安装 Python 依赖
pip install openai

# 测试日报生成（需要先有 raw 数据）
python3 /var/services/homes/<USER>/thoughts/scripts/daily_summary.py 2026-05-30
```

### 3. 配置定时任务（DSM 任务计划）

在群晖 DSM → 控制面板 → 任务计划 → 新增 → 用户定义的脚本：

| 任务名称 | 计划 | 命令 |
|----------|------|------|
| Thought-Daily | `17 1 * * *` | `python3 /var/services/homes/<USER>/thoughts/scripts/daily_summary.py` |
| Thought-Weekly | `23 2 * * 1` | `python3 /var/services/homes/<USER>/thoughts/scripts/weekly_summary.py` |
| Thought-Monthly | `37 3 1 * *` | `python3 /var/services/homes/<USER>/thoughts/scripts/monthly_summary.py` |
| Thought-Quarterly | `41 4 1 1,4,7,10 *` | `python3 /var/services/homes/<USER>/thoughts/scripts/quarterly_summary.py` |
| Thought-Yearly | `53 5 1 1 *` | `python3 /var/services/homes/<USER>/thoughts/scripts/yearly_summary.py` |

> 路径中的 `<USER>` 替换为你的 NAS 用户名，确保与 `common.py` 中 `BASE_DIR` 默认值一致。

### 4. 测试 AI 处理效果

```bash
# 在 NAS 上创建测试原始数据
cat > /var/services/homes/<USER>/thoughts/raw/2026/05/2026-05-30.md << 'EOF'
# 2026-05-30 原始记录

---

## 09:15

今天早上想到一个idea就是把平时的想法都记下来然后让AI帮我整理感觉会很方便

## 14:30

下午跟小王开会讨论了那个新功能的设计方案 他觉得我的想法不错 下周开始开发
EOF

# 运行日报脚本
python3 /var/services/homes/<USER>/thoughts/scripts/daily_summary.py 2026-05-30

# 查看 AI 生成的日报
cat /var/services/homes/<USER>/thoughts/daily/2026/05/2026-05-30.md
```

## 技术栈

### Android APP
- Kotlin 2.0 + Jetpack Compose + Material 3
- OkHttp（WebDAV 客户端）
- Room（本地缓存）
- Hilt（依赖注入）
- EncryptedSharedPreferences（安全存储）

### NAS 脚本
- Python 3
- DeepSeek API（OpenAI 兼容格式）
- DSM Task Scheduler

## 费用

| 项目 | 估算 |
|------|------|
| DeepSeek API（日报 10 条/天） | ~$0.002/天 |
| 周/月/季/年报 AI 综述 | ~$0.01/月 |
| **月费总计** | **~$0.07** |

## 许可

MIT
