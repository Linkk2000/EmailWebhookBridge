# EmailWebhookBridge 📧 🔗

EmailWebhookBridge 是一个轻量级、高性能的中间件服务，旨在将传统的 **SMTP 邮件通知** 转换为现代化的 **Webhook 推送**。

本项目特别针对 **SCA（软件成分分析）** 报告进行了深度适配，能够自动从邮件正文中提取项目信息、漏洞统计和任务状态，并实时广播给多个下游系统（如飞书、钉钉、企业微信或自定义管理系统）。

---

## 🚀 核心特性

- **SMTP 接收端**：内置轻量级 SMTP 服务器（默认监听 2525 端口），像收邮件一样收数据。
- **智能解析**：基于高性能正则引擎，自动从 SCA 邮件中提取项目名称、应用版本、组件数及漏洞统计。
- **动态广播**：支持多目标 Webhook 管理，一处邮件到达，多处实时同步。
- **健康监控**：完善的探活（Health Check）机制，支持手动/自动心跳检测，故障自动熔断跳过。
- **极致性能**：集成 Spring Cache 内存级缓存，高频率推送下依然保持超低延迟。
- **管理中控台**：直观的 Web UI 界面，提供 Webhook 配置、放行记录审计及详细的推送日志追踪。

---

## 🛠 如何集成 Webhook

要让您的系统接收来自 EmailWebhookBridge 的数据，您只需要实现一个标准的 HTTP POST 接口。

### 1. 接收数据格式 (Payload)

本系统会向您的 URL 发送 `application/json` 格式的数据。典型的 Payload 如下：

```json
{
  "scaProjectName": "示例项目",
  "scaApplicationName": "管理前端",
  "scaBranch": "master",
  "scaComponentCount": 564,
  "scaVulnerabilityCount": 48,
  "scaLicenseCount": 9,
  "scaStartTime": "2026-01-22 14:35:45",
  "scaEndTime": "2026-01-22 14:35:57",
  "scaTaskId": "TASK-12345",
  "scaAppId": "APP-6789"
}
```

### 2. 实现探活机制 (Ping)

为了确保系统的稳定性，EmailWebhookBridge 会定期或手动发起探活请求。

**实现逻辑：**
- 当收到 POST 请求且 `scaProjectName` 为 `CONNECTION_TEST` 时，表示这是一个探活包。
- **您的处理**：直接返回 HTTP `200 OK` 即可，不需要进行任何业务逻辑处理或入库操作。

**Python 示例参考：**
```python
if json_data.get('scaProjectName') == 'CONNECTION_TEST':
    print("接收到探活请求，返回 200")
    return 200
```

---

## 🖥 快速开始

### 开发环境启动

1.  **编译项目**：
    ```bash
    mvn clean install -DskipTests
    ```
2.  **启动后端**：
    运行 `app` 模块下的 `Main.java`。
3.  **访问管理页面**：
    浏览器打开 `http://localhost:8080`。系统已启用安全认证，默认凭据为：
    *   **用户名**：`admin`
    *   **密码**：`admin@2026`

### 测试工具

在 `test` 目录下，我们为您准备了完备的测试脚本：
- `webhook_server.py`：模拟一个带探活支持的下游 Webhook 接收服务器。
- `send_test_email.py`：模拟发送一封符合 SCA 格式的测试邮件。

### 部署建议

1.  **Docker 部署 (推荐)**
    项目提供 `docker-compose.yml` 支持一键部署：
    ```bash
    docker compose up -d
    ```
    *   **持久化**：
        *   `./data`：存放 H2 数据库文件。
        *   `./logs`：存放应用运行日志（`spring.log`）。
    *   **安全认证**：默认用户名 `admin`，密码 `admin@2026`。可通过 `SPRING_SECURITY_USER_NAME` 和 `SPRING_SECURITY_USER_PASSWORD` 环境变量修改。
    *   **外部数据库**：在 `docker-compose.yml` 的 `environment` 中指定 `SPRING_DATASOURCE_URL` 等变量。

2.  **二进制部署**
    ```bash
    mvn clean package -DskipTests
    java -jar app/target/app-1.0-SNAPSHOT-exec.jar
    ```

---

