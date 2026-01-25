import smtplib
from email.mime.text import MIMEText
from email.header import Header

# 配置
SMTP_HOST = 'localhost'
SMTP_PORT = 2525
SENDER = 'scanner@security-lab.com'
RECEIVER = 'admin@local.com'

# 邮件内容：这是一封完全不符合正则表达式定义的扫描报告
subject = '紧急安全通告：发现未知设备接入'
body_content = """您好，

系统检测到您的网络中有一个未知设备（MAC: 00:1A:2B:3C:4D:5E）接入。
位置：北京数据中心 A1 柜。
请及时核对该设备是否为您授权接入。

如果这不是您的操作，请立即联系安全团队。
---
安全审计系统
2026-01-25 11:20:00
"""

def send_atypical_email():
    try:
        # 创建 MIMEText 对象
        message = MIMEText(body_content, 'plain', 'utf-8')
        message['From'] = SENDER
        message['To'] = RECEIVER
        message['Subject'] = Header(subject, 'utf-8')

        print(f"正在发送非标准格式邮件到 {SMTP_HOST}:{SMTP_PORT} ...")
        
        # 连接并发送
        with smtplib.SMTP(SMTP_HOST, SMTP_PORT) as server:
            server.sendmail(SENDER, [RECEIVER], message.as_string())
            print("邮件发送成功！")
            print(f"预期结果：应用应识别项目名为 '未知格式: {subject}'，并保存原文内容。")
            
    except ConnectionRefusedError:
        print(f"错误：无法连接到 {SMTP_HOST}:{SMTP_PORT}。")
    except Exception as e:
        print(f"发送失败：{e}")

if __name__ == '__main__':
    send_atypical_email()
