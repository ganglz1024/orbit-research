# 研迹 Orbit — 看见研究的脉络

面向研究生的 AI 论文阅读与理解工作台。支持在研究空间组织论文、阅读 PDF、选中原文向 AI 提问、追溯来源并保存阅读笔记；也提供科研 Skills 和成果中心。

## 技术栈与准备

- 前端：Vue 3、Pinia、TypeScript、Vite；Node.js `^20.19.0` 或 `>=22.12.0`。
- 后端：Java 17、Spring Boot 4.0.9、Maven；默认使用本地 H2 数据库。
- AI：用户自行配置 OpenAI 兼容接口、模型及 API Key。模型调用需要网络与供应商服务可用。

本机系统 `node -v` 为 18.19.0；项目启动脚本在此次检查中找到了本机已有的兼容运行时并成功构建。其他电脑不应依赖此回退，应安装上述兼容版本。

## 本机启动

1. 在 IntelliJ IDEA 中打开 `backend/pom.xml`，选择 Java 17，运行 `com.orbit.research.OrbitApplication`。保持后端进程运行。默认地址为 `http://127.0.0.1:8080`，可访问 `/api/v1/health` 检查健康状态。
2. 双击项目根目录的 `启动前端.cmd`，或在根目录运行 `npm ci` 后执行 `npm run dev`。保持窗口开启；默认地址为 `http://127.0.0.1:5173`。
3. 首次使用时在「空间设置 → 个人账户」创建账户并登录，再在「模型与 API」填写供应商接口基础地址、模型标识及密钥，保存后可测试连接。后端配置模板见 `backend/.env.example`；该文件不会被 Spring 自动加载。

如端口已被占用，请先检查是否已有 Orbit 在运行。以上地址仅能在运行服务的电脑本机访问，不是远程演示链接；跨设备部署和安全配置需另行验证。

## 推荐演示

1. 从首页进入研究工作台；若当前浏览器没有论文，通过「添加论文」导入公开 PDF。源码包不附带用户 PDF；可自行下载公开论文，例如 GPT-3：https://arxiv.org/abs/2005.14165 。
2. 在论文卡片点击「阅读原文」进入阅读器，使用目录或页码导航找到段落。
3. 划选原文，点击「问 AI」，检查引用卡片中的论文、页码和选区；输入问题并发送。
4. 等待模型回答后继续追问，点击来源定位原文，再保存 AI 笔记；刷新后查看对话及笔记。

## 数据与隐私

PDF 和阅读批注可保存在当前浏览器；浏览器清理或换设备可能无法恢复。模型配置和模型对话保存在后端。明确引用的资料片段、消息以及用户保存的研究偏好会发送给所配置的模型供应商；引用片段不等于授权全文。模型答案及 PDF 文本层的公式、表格、双栏顺序均应对照原文核验。

不要提交真实 API Key、`.env` 文件、`backend/data/` 数据库、加密密钥或用户 PDF；打包前还需人工检查截图、日志和报告是否包含私人资料。`.gitignore` 不能自动保护手工压缩包。

## 检查命令

在项目根目录执行 `npm run typecheck` 和 `npm run build`；在 `backend` 目录执行 `mvn test`。项目另有 `scripts/check-*.cjs` 回归检查。测试通过不代表其他设备或生产环境已完成部署验证。


## 从源码包安装与运行

压缩包统一根目录为 `Orbit-Source-Final/`。前端就在根目录，不存在单独的 frontend 目录。
需要 Node.js ^20.19.0 或 >=22.12.0、JDK 17、Maven 3.6.3+，推荐使用已验证的 Maven 3.9.4。项目没有 Maven Wrapper，请自行安装 Maven 并将 mvn 加入 PATH。浏览器使用当前 Chrome 或 Edge；本次真实交互验证使用 Chrome，不承诺未测试浏览器的兼容性。首次安装依赖需要网络。

在 PowerShell 或 CMD 中打开解压根目录：

```text
npm ci
npm run typecheck
npm run build
```

打开第二个终端，工作目录切换到 backend：

```text
cd backend
mvn test
mvn spring-boot:run
```

默认 local 配置使用 H2 文件数据库，无需安装数据库。Flyway 在启动时自动执行 `backend/src/main/resources/db/migration/` 的 V1–V8 迁移，运行数据在 backend/data 中创建。启动时请保持工作目录为 backend。健康地址： http://127.0.0.1:8080/api/v1/health 。也可在 IDEA 中导入 backend/pom.xml，设置工作目录 backend，运行 OrbitApplication。

回到根目录的另一个终端：

```text
npm run dev
```

访问 http://127.0.0.1:5173 。Windows 也可双击「启动前端.cmd」。两项服务需要同时保持运行；退出终端按 Ctrl+C。`npm run preview` 只用于静态构建预览，不含开发服务器的 API 代理；完整演示请使用上述 dev 启动方式。

可选端口设置（无需创建真实 .env）：PowerShell 使用 `$env:ORBIT_FRONTEND_PORT='5174'`；CMD 使用 `set ORBIT_FRONTEND_PORT=5174`。对应后端地址使用 ORBIT_BACKEND_URL，例如 http://127.0.0.1:8080。根目录 `.env.example` 仅为变量说明；不要假定配置模板会自动加载。

### AI 配置与存储

首次在空间设置创建自己的账户，登录后保存模型 API 配置。API Key 应由评审者自行提供，源码包不包含默认账户、用户模型密钥或历史数据库。可填写示例基础地址 https://api.example.com/v1、模型标识 your-model-id 和自己的密钥，系统会追加 /chat/completions；实际地址及模型需遵循供应商要求。

也可在启动后端的终端设置 ORBIT_LLM_BASE_URL、ORBIT_LLM_MODEL、ORBIT_LLM_API_KEY；仅在本机终端填写真实值，具体模板见 backend/.env.example。Spring 不会自动读取 .env.example。PDF 阅读、搜索和本地批注不要求模型密钥；AI 解释、聊天、AI 整理和模型生成成果依赖已配置的第三方模型服务、网络和额度。

数据库和自动生成的 data/config.key 必须一起保管才能恢复已加密配置，不包含在公开源码包中。可选 MySQL 模式需先自行创建空数据库，设置 SPRING_PROFILES_ACTIVE=mysql、ORBIT_DB_URL、ORBIT_DB_USER 和 ORBIT_DB_PASSWORD 后启动；本次解压验收使用 H2，未独立验收新的 MySQL 环境。

### 实际组件与限制

PDF 渲染/文本层使用 pdfjs-dist 5.6.205，批注导出使用 pdf-lib；后端文档处理使用 PDFBox 3.0.8，数据访问采用 Spring JDBC、Flyway 与 H2/MySQL。PDF.js worker 由 Vite 从锁定依赖中打包，不需要手动复制 node_modules。科研 Skill 定义位于后端资源与服务中，保留在源码包。

当前重点支持桌面阅读。复杂公式、表格、双栏和扫描件不能保证可靠文本提取；无可靠 OCR 时不能将扫描图像当作已解析正文。来源定位以实际 PDF 版本与来源锚点为准。浏览器中的 PDF/本地批注不会随源码包移交；换电脑需重新导入，源码包不会带走个人研究空间。

本机已进行真实 PDF-AI 阅读验收；干净解压构建与启动结果见源码包外的交付报告。源码包未配置真实 AI 密钥，因此解压验证不包含新的真实模型调用。
