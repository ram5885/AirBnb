"""
Test double of the Razorpay REST API, used ONLY by the automated end-to-end tests (compose.e2e.yaml).

It implements the calls booking-service makes (create order, fetch payment, capture, refund) with Razorpay's
response shapes, and one test-control endpoint, POST /__test/payments, that plays the customer completing
Checkout (Razorpay itself would record the payment). booking-service's verification code runs unchanged: it
checks the checkout signature with its key secret, fetches the payment from here and compares order, amount and
currency. Requests must use Basic auth with a rzp_test_ key id, like the real API. Never used for real money.
"""
import base64
import json
import secrets
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ORDERS = {}
PAYMENTS = {}
LOCK = threading.Lock()


def new_id(prefix):
    return prefix + "_" + secrets.token_hex(7)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):  # keep container logs short
        print("razorpay-mock", self.command, self.path, flush=True)

    def send(self, status, payload):
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def body(self):
        if "chunked" in (self.headers.get("Transfer-Encoding") or "").lower():
            data = b""
            while True:  # Java's HTTP client streams JSON bodies with chunked transfer encoding
                size = int(self.rfile.readline().strip().split(b";")[0], 16)
                if size == 0:
                    self.rfile.readline()
                    break
                data += self.rfile.read(size)
                self.rfile.readline()
            return json.loads(data or b"{}")
        length = int(self.headers.get("Content-Length") or 0)
        return json.loads(self.rfile.read(length) or b"{}")

    def authorized(self):
        header = self.headers.get("Authorization", "")
        if not header.startswith("Basic "):
            return False
        key_id = base64.b64decode(header[6:]).decode().split(":", 1)[0]
        return key_id.startswith("rzp_test_")

    def error(self, status, description):
        self.send(status, {"error": {"code": "BAD_REQUEST_ERROR", "description": description}})

    def do_GET(self):
        if self.path == "/health":
            return self.send(200, {"status": "ok"})
        if not self.authorized():
            return self.error(401, "Authentication failed")
        parts = self.path.strip("/").split("/")
        if len(parts) == 3 and parts[:2] == ["v1", "payments"]:
            with LOCK:
                payment = PAYMENTS.get(parts[2])
            return self.send(200, payment) if payment else self.error(400, "The id provided does not exist")
        self.error(404, "Not found")

    def do_POST(self):
        parts = self.path.strip("/").split("/")
        if parts == ["__test", "payments"]:  # test control: the customer paid through Checkout
            p = self.body()
            payment = {"id": p["id"], "entity": "payment", "order_id": p["order_id"], "amount": p["amount"],
                       "currency": p.get("currency", "INR"), "status": p.get("status", "captured"),
                       "error_code": None, "error_description": p.get("error_description")}
            with LOCK:
                PAYMENTS[payment["id"]] = payment
            return self.send(200, payment)
        if not self.authorized():
            return self.error(401, "Authentication failed")
        if parts == ["v1", "orders"]:
            req = self.body()
            order = {"id": new_id("order"), "entity": "order", "amount": req["amount"], "currency": req["currency"],
                     "receipt": req.get("receipt"), "status": "created", "notes": req.get("notes", {})}
            with LOCK:
                ORDERS[order["id"]] = order
            return self.send(200, order)
        if len(parts) == 4 and parts[:2] == ["v1", "payments"] and parts[3] in ("capture", "refund"):
            with LOCK:
                payment = PAYMENTS.get(parts[2])
                if not payment:
                    return self.error(400, "The id provided does not exist")
                req = self.body()
                if parts[3] == "capture":
                    payment["status"] = "captured"
                    return self.send(200, payment)
                payment["status"] = "refunded"
                refund = {"id": new_id("rfnd"), "entity": "refund", "payment_id": payment["id"],
                          "amount": req.get("amount", payment["amount"]), "status": "processed"}
            return self.send(200, refund)
        self.error(404, "Not found")


if __name__ == "__main__":
    print("razorpay-mock listening on :9000", flush=True)
    ThreadingHTTPServer(("0.0.0.0", 9000), Handler).serve_forever()
