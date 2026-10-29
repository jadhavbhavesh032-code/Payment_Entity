"""Regenerate the local Postman/Newman regression collection (no credentials)."""
import json
from pathlib import Path

items = []
def case(name, method, path, status, body=None, key=None, checks=(), save=None):
    headers = []
    if body is not None:
        headers.append({"key": "Content-Type", "value": "application/json"})
    if key is not None:
        headers.append({"key": "Idempotency-Key", "value": key})
    request = {"method": method, "header": headers, "url": "{{baseUrl}}" + path}
    if body is not None:
        request["body"] = {"mode": "raw", "raw": json.dumps(body), "options": {"raw": {"language": "json"}}}
    tests = [f"pm.test('HTTP {status}', () => pm.response.to.have.status({status}));", "const b = pm.response.json();"]
    tests.extend("pm.test(" + json.dumps(label) + ", () => { " + assertion + " });" for label, assertion in checks)
    if save:
        tests.append(f"if (pm.response.code === {status}) pm.collectionVariables.set('{save[0]}', b.data.{save[1]});")
    items.append({"name": name, "request": request, "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": tests}}]})

p = "/api/payments"
pay = p + "/{{paymentId}}"
tx = pay + "/transactions"
rf = pay + "/refunds"
payment = {"bookingId": "{{bookingId}}", "userId": 1, "bookingAmount": 500, "discountAmount": 0, "currency": "INR"}
# Numeric bookingId replacement must not be quoted in the raw request.
case("Create payment", "POST", p, 201, payment, save=("paymentId", "paymentId"), checks=[("CREATED", "pm.expect(b.data.paymentStatus).to.eql('CREATED');")])
items[-1]["request"]["body"]["raw"] = items[-1]["request"]["body"]["raw"].replace('"{{bookingId}}"', '{{bookingId}}')
case("Duplicate booking", "POST", p, 409, payment)
items[-1]["request"]["body"]["raw"] = items[-1]["request"]["body"]["raw"].replace('"{{bookingId}}"', '{{bookingId}}')
case("Invalid negative amount and IDs", "POST", p, 400, {"bookingId": -1, "userId": 0, "bookingAmount": -10})
case("Unknown JSON property", "POST", p, 400, {**payment, "unexpected": True})
case("Invalid pagination", "GET", p + "?size=101", 400)
case("Invalid sort", "GET", p + "?sort=unknown,desc", 400)
case("Invalid enum", "POST", tx, 400, {"paymentMethod": "INVALID"}, "{{runId}}-invalid")
case("Missing idempotency header", "POST", tx, 400, {"paymentMethod": "UPI"})
case("Blank idempotency header", "POST", tx, 400, {"paymentMethod": "UPI"}, " ")
case("Oversized idempotency header", "POST", tx, 400, {"paymentMethod": "UPI"}, "x" * 101)
case("Refund before payment", "POST", rf, 409, {"refundAmount": 10, "refundReason": "Too early"}, "{{runId}}-early")
case("Process payment", "POST", tx, 201, {"paymentMethod": "UPI"}, "{{runId}}-pay", [("SUCCESS", "pm.expect(b.data.transactionStatus).to.eql('SUCCESS');")], ("transactionId", "transactionId"))
case("Replay payment", "POST", tx, 200, {"paymentMethod": "UPI"}, "{{runId}}-pay", [("Same transaction", "pm.expect(String(b.data.transactionId)).to.eql(String(pm.collectionVariables.get('transactionId')));")])
case("Conflicting payment replay", "POST", tx, 409, {"paymentMethod": "CARD"}, "{{runId}}-pay")
case("Prevent second charge", "POST", tx, 409, {"paymentMethod": "UPI"}, "{{runId}}-second")
case("Read paid payment", "GET", pay, 200, checks=[("PAID and amount 500", "pm.expect(b.data.paymentStatus).to.eql('PAID'); pm.expect(Number(b.data.amountPaid)).to.eql(500);")])
case("Missing refund header", "POST", rf, 400, {"refundAmount": 100, "refundReason": "Test"})
case("Invalid refund", "POST", rf, 400, {"refundAmount": -1, "refundReason": ""}, "{{runId}}-invalid-refund")
case("Partial refund", "POST", rf, 201, {"refundAmount": 100, "refundReason": "Test cancellation"}, "{{runId}}-refund", [("COMPLETED", "pm.expect(b.data.refundStatus).to.eql('COMPLETED');")], ("refundId", "refundId"))
case("Replay refund", "POST", rf, 200, {"refundAmount": 100, "refundReason": "Test cancellation"}, "{{runId}}-refund", [("Same refund", "pm.expect(String(b.data.refundId)).to.eql(String(pm.collectionVariables.get('refundId')));")])
case("Conflicting refund replay", "POST", rf, 409, {"refundAmount": 101, "refundReason": "Test cancellation"}, "{{runId}}-refund")
case("Over-refund rejected", "POST", rf, 409, {"refundAmount": 401, "refundReason": "Too much"}, "{{runId}}-over")
case("Read partially refunded", "GET", pay, 200, checks=[("PARTIALLY_REFUNDED", "pm.expect(b.data.paymentStatus).to.eql('PARTIALLY_REFUNDED');")])
case("Refund remaining balance", "POST", rf, 201, {"refundAmount": 400, "refundReason": "Remaining balance"}, "{{runId}}-remaining")
case("Read fully refunded", "GET", pay, 200, checks=[("REFUNDED", "pm.expect(b.data.paymentStatus).to.eql('REFUNDED');")])
case("Reject refund after full refund", "POST", rf, 409, {"refundAmount": 1, "refundReason": "Too late"}, "{{runId}}-late")
case("Transaction history has one charge", "GET", tx, 200, checks=[("One transaction", "pm.expect(b.data.length).to.eql(1);")])
case("Refund history has two refunds", "GET", rf, 200, checks=[("Two refunds", "pm.expect(b.data.length).to.eql(2);")])
case("Read transaction", "GET", tx + "/{{transactionId}}", 200)
case("Read refund", "GET", rf + "/{{refundId}}", 200)
case("Read by booking", "GET", p + "/booking/{{bookingId}}", 200)
case("Reject cancelling refunded payment", "POST", pay + "/cancel", 409)
case("Missing payment", "GET", p + "/9223372036854775807", 404)
case("OpenAPI required idempotency headers", "GET", "/v3/api-docs", 200, checks=[("Required headers and examples", "['transactions','refunds'].forEach(endpoint => { const op = b.paths['/api/payments/{paymentId}/' + endpoint].post; pm.expect(op.parameters.find(p => p.name === 'Idempotency-Key').required).to.eql(true); pm.expect(op.requestBody.content['application/json'].example).to.exist; });")])

collection = {"info": {"name": "Payment Entity - PostgreSQL regression", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json", "description": "Local mock gateway tests. Creates and retains identifiable test financial records. No credentials required. Run in order."}, "variable": [{"key": "baseUrl", "value": "http://localhost:9080"}], "event": [{"listen": "prerequest", "script": {"type": "text/javascript", "exec": ["if (pm.info.requestName === 'Create payment') { pm.collectionVariables.set('runId', pm.variables.replaceIn('{{$guid}}')); pm.collectionVariables.set('bookingId', String(Date.now())); }"]}}], "item": items}
out = Path(__file__).resolve().parents[1] / "postman"
out.mkdir(exist_ok=True)
(out / "PaymentEntity.postman_collection.json").write_text(json.dumps(collection, indent=2) + "\n", encoding="utf-8")
print(f"Generated {len(items)} requests")
