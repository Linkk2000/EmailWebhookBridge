import http.server
import socketserver
import json

PORT = 9090

class WebhookReceiverHandler(http.server.SimpleHTTPRequestHandler):
    def do_POST(self):
        if self.path == '/callback':
            print("\n[收到 Webhook] -----------------------------")
            # 打印头信息以供调试
            for key, value in self.headers.items():
                print(f"{key}: {value}")
            print("------------------------------------------------")

            post_data = b''
            # 处理分块传输编码 (Transfer-Encoding: chunked)
            if self.headers.get('Transfer-Encoding') == 'chunked':
                try:
                    while True:
                        line = self.rfile.readline().strip()
                        if not line: break
                        chunk_size = int(line, 16)
                        if chunk_size == 0:
                            self.rfile.readline() # 跳过最后的空白行
                            break
                        post_data += self.rfile.read(chunk_size)
                        self.rfile.readline() # 跳过块末尾的 CRLF
                except Exception as e:
                    print(f"[解块错误] {e}")
            else:
                length_header = self.headers.get('Content-Length')
                content_length = int(length_header) if length_header else 0
                if content_length > 0:
                    post_data = self.rfile.read(content_length)

            if post_data:
                try:
                    # 尝试解析 JSON
                    json_data = json.loads(post_data.decode('utf-8'))
                    
                    # 识别探活/Ping 报文
                    if json_data.get('scaProjectName') == 'CONNECTION_TEST':
                        print("[探活确认] 接收到连接测试请求（Ping）。")
                    
                    # 格式化打印所有接收到的 JSON
                    print(json.dumps(json_data, indent=4, ensure_ascii=False))
                except Exception as e:
                    # 解析失败则回退到打印原始文本
                    print(f"[数据处理出错] {e}")
                    print(post_data.decode('utf-8'))
            else:
                print("(无消息体)")

            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            response_body = b'{"status":"ok"}'
            self.send_header('Content-Length', str(len(response_body)))
            self.end_headers()
            self.wfile.write(response_body)
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        #以此覆盖默认日志方法以减少噪音，仅在需要时打印特定日志
        pass

print(f"正如火如荼地监听端口 {PORT} 中...")
print(f"URL: http://localhost:{PORT}/callback")
print("等待数据中...\n")

with socketserver.TCPServer(("", PORT), WebhookReceiverHandler) as httpd:
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n服务已停止。")
