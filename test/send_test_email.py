import argparse
import smtplib
from email.mime.text import MIMEText
from email.header import Header

# 默认值来自最初的样本邮件。实际测试时必须换成 SCA 里真实存在的那次扫描，
# 否则 Bridge 会停在「反查未命中」或「检测完成时间不匹配」——那是补全逻辑
# 在正常保护，不是缺陷。
DEFAULTS = {
    "host": "localhost",
    "port": 2525,
    "sender": "test@local.com",
    "receiver": "admin@local.com",
    "source": "GitLab_V4",
    "project": "测试用",
    "app": "管理系统企业前端",
    "version": "master",
    "components": 564,
    "vulns": 48,
    "licenses": 9,
    "start_time": "2026-01-22 14:35:45",
    "end_time": "2026-01-22 14:35:57",
}

BODY_TEMPLATE = """Link Check
tangerine，您好：
【{source}】风险检测已完成，风险结果如下：
项目名称：{project}，应用名称：{app}，应用版本：{version}
共检测出{components}个组件，其中严重10个，高危19个，中危13个，低危2个，无漏洞520个。
共检测出{vulns}个漏洞，其中严重7个，高危20个，中危18个，低危3个。
共检测出{licenses}个许可证，其中高风险0个，中风险0个，低风险9个。
检测开始时间：{start_time}
检测完成时间：{end_time}
.
"""


def parse_args():
    p = argparse.ArgumentParser(
        description="向 EmailWebhookBridge 发送一封模拟的 SCA 扫描完成通知邮件。",
        epilog="示例：python3 send_test_email.py --project 我的项目 --app 前端 "
               "--version develop --end-time '2026-08-28 10:30:00'",
    )
    p.add_argument("--host", default=DEFAULTS["host"], help="Bridge 的 SMTP 地址")
    p.add_argument("--port", type=int, default=DEFAULTS["port"], help="Bridge 的 SMTP 端口，默认 2525")
    p.add_argument("--sender", default=DEFAULTS["sender"])
    p.add_argument("--receiver", default=DEFAULTS["receiver"], help="Bridge 不校验收件人，填什么都行")
    p.add_argument("--source", default=DEFAULTS["source"],
                   help="邮件【】里的来源名，决定 vcs/list 的 type 参数。"
                        "可选：通用Git SVN GitLab_V3 GitLab_V4 Gitee开源版 Gitee企业版")
    p.add_argument("--project", default=DEFAULTS["project"], help="项目名称，必须与 SCA 中完全一致")
    p.add_argument("--app", default=DEFAULTS["app"], help="应用名称，必须与 SCA 中完全一致")
    p.add_argument("--version", default=DEFAULTS["version"], help="应用版本/分支，必须与 SCA 中完全一致")
    p.add_argument("--components", type=int, default=DEFAULTS["components"])
    p.add_argument("--vulns", type=int, default=DEFAULTS["vulns"])
    p.add_argument("--licenses", type=int, default=DEFAULTS["licenses"])
    p.add_argument("--start-time", default=DEFAULTS["start_time"])
    p.add_argument("--end-time", default=DEFAULTS["end_time"],
                   help="检测完成时间，必须与 SCA 中该次任务的 detectEndTime 一致，否则补全会被拒绝")
    p.add_argument("--dry-run", action="store_true", help="只打印邮件正文，不发送")
    return p.parse_args()


def send_email(args):
    body = BODY_TEMPLATE.format(
        source=args.source, project=args.project, app=args.app, version=args.version,
        components=args.components, vulns=args.vulns, licenses=args.licenses,
        start_time=args.start_time, end_time=args.end_time,
    )

    print("邮件正文：")
    print("-" * 60)
    print(body.rstrip())
    print("-" * 60)

    if args.dry_run:
        print("\n--dry-run，未发送。")
        return

    message = MIMEText(body, "plain", "utf-8")
    message["From"] = args.sender
    message["To"] = args.receiver
    message["Subject"] = Header("SCA Report", "utf-8")

    try:
        print(f"\n正在连接 {args.host}:{args.port} ...")
        with smtplib.SMTP(args.host, args.port, timeout=15) as server:
            server.sendmail(args.sender, [args.receiver], message.as_string())
        print("邮件发送成功。")
        print("\n接下来看三个地方：")
        print("  1. Bridge 日志：搜 'SCA 补全' 看补全结果")
        print("  2. 下游接收器：webhook_server.py 的控制台输出")
        print(f"  3. Dashboard：http://{args.host}:8080")
    except ConnectionRefusedError:
        print(f"错误：连不上 {args.host}:{args.port}，确认 Bridge 已启动且 SMTP 端口正确。")
    except Exception as e:
        print(f"发送失败：{e}")


if __name__ == "__main__":
    send_email(parse_args())
