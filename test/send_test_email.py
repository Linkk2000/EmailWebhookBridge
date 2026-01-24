import smtplib
from email.mime.text import MIMEText
from email.header import Header

# 配置
SMTP_HOST = 'localhost'
SMTP_PORT = 2525
SENDER = 'test@local.com'
RECEIVER = 'admin@local.com'

# 邮件内容 (模拟提供了 docs/邮件内容实例.txt 中的内容)
subject = 'SCA Report'
body_content = """Link Check
tangerine，您好：
【GitLab_V4】风险检测已完成，风险结果如下：
项目名称：测试用，应用名称：管理系统企业前端，应用版本：master
共检测出564个组件，其中严重10个，高危19个，中危13个，低危2个，无漏洞520个。
共检测出48个漏洞，其中严重7个，高危20个，中危18个，低危3个。
共检测出9个许可证，其中高风险0个，中风险0个，低风险9个。
检测开始时间：2026-01-22 14:35:45
检测完成时间：2026-01-22 14:35:57
.
"""

def send_email():
    try:
        # 创建 MIMEText 对象
        message = MIMEText(body_content, 'plain', 'utf-8')
        message['From'] = SENDER
        message['To'] = RECEIVER
        message['Subject'] = Header(subject, 'utf-8')

        print(f"正在连接到 {SMTP_HOST}:{SMTP_PORT} ...")
        
        # 连接并发送
        with smtplib.SMTP(SMTP_HOST, SMTP_PORT) as server:
            # server.set_debuglevel(1) # 取消注释以查看调试信息
            print("连接成功，正在发送邮件...")
            server.sendmail(SENDER, [RECEIVER], message.as_string())
            print("邮件发送成功！")
            
    except ConnectionRefusedError:
        print(f"错误：无法连接到 {SMTP_HOST}:{SMTP_PORT}。请确保 EmailWebhookBridge 应用程序正在运行。")
    except Exception as e:
        print(f"发送失败：{e}")

if __name__ == '__main__':
    send_email()
