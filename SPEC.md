# PureTrans 安卓极简翻译 App 完整开发文档 (含前端 UI 源码规范)

## 1. 核心目标与产品定位
开发一款对标 Google Translate 现代 Material You (Material 3) 风格的纯原生 Android 翻译软件：
- **极致轻量**：100% 原生 Kotlin + Jetpack Compose，无广告、无后台常驻，安装包体积 `< 5MB`，冷启动 `< 300ms`。
- **智能双模路由（核心机制）**：
  1. **模式 A（纯前端 BYOK 直连模式）**：设置页填写了国内 API Key（百度翻译或 DeepSeek API）时，手机本机直连国内接口，`30ms ~ 80ms` 极速响应。
  2. **模式 B（免配置美国后端兜底模式）**：设置页 API Key **留空**时，自动降级请求自建美国服务器反代接口（对接 Google Translate `client=gtx` 免 Key 接口），零配置开箱即用。

---

## 2. 前端 UI/UX 视觉与交互设计规范 (Material 3 Expressive)

### 2.1 色彩与主题系统 (`Theme.kt`)
- **动态取色 (Dynamic Color)**：Android 12+ 开启 `dynamicDarkColorScheme` / `dynamicLightColorScheme`，自动提取用户手机壁纸主色调。
- **层级色阶分配**：
  - **页面底层背景**：`MaterialTheme.colorScheme.surface`（纯净底色，深色模式下为深灰黑 `#121316`，浅色下为微暖白 `#FAF9FD`）。
  - **原文输入区卡片**：`MaterialTheme.colorScheme.surfaceContainerLow`（圆角 `28.dp`，无生硬边框，靠微弱色差区分层级）。
  - **译文结果区卡片**：`MaterialTheme.colorScheme.primaryContainer`（圆角 `28.dp`，醒目柔和的主色容器，突出翻译结果）。
  - **语言切换胶囊按钮**：`MaterialTheme.colorScheme.secondaryContainer`（圆角 `50%` 全胶囊形状，高度 `48.dp`）。

### 2.2 字体排版与动效规范
- **输入与译文字号自适应**：
  - 文本长度 `< 50` 字：`24.sp` (`FontWeight.Medium`)，行高 `32.sp`，大气清晰。
  - 文本长度 `>= 50` 字：自动缩小至 `18.sp` (`FontWeight.Normal`)，行高 `26.sp`，适合长文阅读。
- **流畅过渡动效**：
  - **语言互换按钮**：点击中间双向箭头时，执行 `180度` 弹簧旋转动画 (`animateFloatAsState` + `Spring.DampingRatioMediumBouncy`)。
  - **译文卡片展开/收起**：使用 `AnimatedVisibility(enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically())`，避免生硬跳出。
  - **翻译加载态**：拒绝转圈弹窗阻塞屏幕，在输入卡片底部边缘显示 `2.dp` 极细的 `LinearProgressIndicator` 流光条。

### 2.3 ASCII 界面原型图 (主界面 `MainScreen`)
+---------------------------------------------------+
|  [::] PureTrans        [🟢 极速直连]  [历史] [设置] |  <- TopAppBar (含当前引擎状态微标)
+---------------------------------------------------+
|  [     自动检测 (Auto)    ]  (⇄)  [   简体中文   ]  |  <- 语言切换栏 (胶囊按钮 + 旋转动画)
+---------------------------------------------------+
|  +---------------------------------------------+  |
|  | 请输入要翻译的文本...                    (×) |  |  <- 输入大卡片 (surfaceContainerLow)
|  |                                             |  |     右上角一键清空，支持多行自适应
|  |                                             |  |
|  | [📋 粘贴并翻译: "Hello world..."]           |  |  <- 剪贴板智能感应胶囊气泡
|  | 🔊 朗读原文                        12 / 5000 |  |  <- 底部辅助栏 + 2dp 顶部加载流光条
|  +---------------------------------------------+  |
|                                                   |
|  +---------------------------------------------+  |
|  | 你好，世界                                   |  |  <- 译文结果卡片 (primaryContainer)
|  | Nǐ hǎo, shìjiè                              |  |     带拼音/音标展示 (次级透明度)
|  |                                             |  |
|  | ------------------------------------------- |  |
|  | [🔊 朗读]   [📋 复制]   [⭐ 收藏]   [⛶ 全屏] |  |  <- 译文操作工具栏 (带点击震动反馈)
|  +---------------------------------------------+  |
|                                                   |
|        (🎤 语音输入)          (📷 拍照取词)         |  <- 底部悬浮快捷胶囊 (Floating Toolbar)
+---------------------------------------------------+

---

## 3. 前端 UI 核心 Jetpack Compose 完整参考实现

AI 在生成 `ui/` 目录代码时，必须严格以此 Compose 结构作为基线实现：

### 3.1 主界面实现 (`ui/main/MainScreen.kt`)
（略，以需求中代码为准）

### 3.2 设置页双模切换 UI (`ui/settings/SettingsScreen.kt`)
- **顶部动态横幅卡片 (`ElevatedCard`)**：
  - 当检测到 `baiduAppId.isNotBlank() || deepseekKey.isNotBlank()`：卡片背景显示柔和绿，标题显示 **"🟢 当前处于：本机直连极速模式"**，副标题显示 **"请求由手机直接发往国内节点，不经过海外服务器"**。
  - 当所有 Key 均为空时：卡片背景显示柔和蓝，标题显示 **"🔵 当前处于：美国后端免密模式"**，副标题显示 **"未填写本地 Key，已自动启用 Google Translate 免密反代通道"**。
- **操作体验**：
  - 每个 Key 输入框支持右侧小眼睛切换明文/密文 (`PasswordVisualTransformation`)。
  - 提供 **【📋 从剪贴板智能识别填入】** 与 **【🗑️ 清空所有 Key（恢复免密后端模式）】** 两个底部按钮。

### 3.3 全局划词悬浮窗 UI (`ui/quickpopup/QuickTranslateActivity.kt`)
- 在 `AndroidManifest.xml` 中配置该 Activity 的 `theme` 为透明悬浮窗样式，设置 `windowIsFloating = true`。
- UI 仅为一个宽度占屏幕 `90%`、圆角 `24.dp` 的紧凑型 `ElevatedCard`：上半部分显示选中的原文（限 2 行省略），下半部分实时显示译文，右下角带【朗读】与【复制并关闭】两个小图标按钮。

---

## 4. 智能双模路由与网络接口规范

### 4.1 统一路由器 (`SmartTranslationRouter`)
1. 检查本地 `EncryptedSharedPreferences` 是否配置了 **百度翻译 (AppID + SecretKey)** -> 非空则走 `BaiduDirectEngine`。
2. 检查本地是否配置了 **DeepSeek Key** -> 非空则走 `AiDirectEngine`。
3. **若以上均留空** -> 自动降级调用 `GoogleBackendFallbackEngine`（请求美国服务器免 Key 谷歌接口）。

### 4.2 引擎一：免 Key 谷歌后端兜底接口 (`GoogleBackendFallbackEngine`)
- **请求方式**：`GET`
- **默认端点**：`https://api.yourdomain.com/google-translate`
- **Query 参数**：`client=gtx`、`sl={source}`、`tl={target}`、`dt=t`、`q={encodedText}`
- **鉴权 Header**：`X-App-Token: MySecretPin888`
- **JSON 解析**：遍历最外层 JSON 数组的第 `0` 个元素，提取所有子数组的第 `0` 项字符串并拼接为完整译文。

### 4.3 引擎二：百度翻译本机直连 (`BaiduDirectEngine`)
- **接口地址**：`POST https://fanyi-api.baidu.com/api/trans/vip/translate`
- **本机签名算法**：`sign = MD5(appid + q + salt + secretKey)`（32 位小写）。

---

## 5. 美国服务器 Nginx 免代码反代配置 (服务端参考)
server {
    listen 443 ssl http2;
    server_name api.yourdomain.com;

    location /google-translate {
        if ($http_x_app_token != "MySecretPin888") {
            return 403;
        }
        proxy_pass https://translate.googleapis.com/translate_a/single;
        proxy_ssl_server_name on;
        proxy_set_header Host translate.googleapis.com;
        proxy_set_header User-Agent "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    }
}
