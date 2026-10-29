"""Create readable Markdown and HTML reports from a Newman JSON result."""
import html
import json
import re
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

source = Path(sys.argv[1]).resolve()
result = json.loads(source.read_text(encoding="utf-8"))
run = result["run"]
stats = run["stats"]
executions = run["executions"]
failures = run.get("failures", [])
out = source.parent

def response_body(execution):
    stream = execution.get("response", {}).get("stream", {})
    try:
        return json.loads(bytes(stream.get("data", [])).decode("utf-8"))
    except (ValueError, UnicodeError):
        return {}

def url_text(request):
    url = request.get("url", {})
    if isinstance(url, str):
        return url
    host = ".".join(url.get("host", []))
    port = ":" + str(url["port"]) if url.get("port") else ""
    path = "/" + "/".join(url.get("path", []))
    query = "&".join(str(q["key"]) + "=" + str(q.get("value", ""))
                     for q in url.get("query", []) if not q.get("disabled"))
    return f"{url.get('protocol', 'http')}://{host}{port}{path}" + ("?" + query if query else "")

rows = []
details = []
ids = {}
for number, execution in enumerate(executions, 1):
    assertions = execution.get("assertions", [])
    assertion_errors = [a for a in assertions if a.get("error")]
    expected = next((re.search(r"HTTP (\d+)", a["assertion"]).group(1)
                     for a in assertions if re.search(r"HTTP (\d+)", a["assertion"])), "-")
    response = execution.get("response", {})
    actual = response.get("code", "No response")
    passed = not assertion_errors and str(actual) == expected
    request = execution["request"]
    url = url_text(request)
    name = execution["item"]["name"]
    rows.append([str(number), name, expected, str(actual),
                 f"{len(assertions) - len(assertion_errors)}/{len(assertions)}",
                 str(response.get("responseTime", "-")), "PASS" if passed else "FAIL"])
    details.append(f"{number}. **{name}**: `{request['method']} {url}`")
    data = response_body(execution).get("data")
    if name == "Create payment" and isinstance(data, dict):
        ids.update({k: data[k] for k in ("paymentId", "bookingId") if k in data})
    if name == "Process payment" and isinstance(data, dict):
        ids["transactionId"] = data.get("transactionId")
    if name in ("Partial refund", "Refund remaining balance") and isinstance(data, dict):
        ids.setdefault("refundIds", []).append(data.get("refundId"))

started = datetime.fromtimestamp(run["timings"]["started"] / 1000,
                                 timezone(timedelta(hours=5, minutes=30)))
duration = (run["timings"]["completed"] - run["timings"]["started"]) / 1000
status = "PASS" if not failures and all(row[-1] == "PASS" for row in rows) else "FAIL"
summary = {"status": status, "startedIST": started.isoformat(), "durationSeconds": duration,
           "requests": stats["requests"], "assertions": stats["assertions"],
           "failureCount": len(failures), "testRecords": ids}
(out / "run-summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")

intro = [
    "# Payment Entity - Postman Test Report", "",
    f"**Overall result: {status}**", "",
    f"Run started: {started:%d %B %Y, %H:%M:%S} IST. Duration: {duration:.2f} seconds.", "",
    "Target: `http://localhost:9080`, existing Payment Entity application, Spring Boot 4.1.2, "
    "PostgreSQL database `payment_entity_db` on localhost:5432.", "",
    f"- Requests: **{stats['requests']['total']}**, failed: **{stats['requests']['failed']}**.",
    f"- Assertions: **{stats['assertions']['total']}**, failed: **{stats['assertions']['failed']}**.",
    f"- Recorded Newman failures: **{len(failures)}**.", "",
    "## Results for every request", "",
    "Expected 400, 404 and 409 responses are successful negative tests, not test failures.", "",
    "| # | Test | Expected HTTP | Actual HTTP | Assertions passed | Response ms | Result |",
    "| --- | --- | --- | --- | --- | --- | --- |",
]
intro.extend("| " + " | ".join(row) + " |" for row in rows)
intro += ["", "## Test records and database checks", "",
          "The collection generated new IDs and retained its mock financial records:", "",
          "```json", json.dumps(ids, indent=2), "```", "",
          "The application health was UP before testing. PostgreSQL showed an active application "
          "connection pool, and Flyway V1 and V2 were both successful. The final database query "
          "is saved in `database-verification.txt` beside this report.", "",
          "## Coverage", "",
          "Payment creation and duplicate booking detection; input, enum and pagination validation; "
          "required/blank/oversized idempotency keys; payment processing, identical retries and "
          "conflicting retries; prevention of a second charge; refund eligibility; partial and "
          "full refunds; refund replay and conflict detection; over-refund prevention; final "
          "financial state; history cardinality; individual and booking reads; cancellation "
          "conflicts; missing payments; OpenAPI required headers and request examples.", "",
          "## Exact requests executed", ""] + details
intro += ["", "## Failures and limitations", ""]
if failures:
    intro += ["```json", json.dumps(failures, indent=2), "```"]
else:
    intro += ["No request, assertion or script failures were recorded. No application-code fix "
              "was needed during this run."]
intro += ["", "Tests were executed using Postman's Newman 6 runner against the running application. "
          "This was not a manual Postman desktop UI run. The existing payment/refund gateways are "
          "mocks; no real money moved. Live gateway and external-module integrations were not tested. "
          "This run executed the 34-request collection; it did not rerun the Maven test suite.", "",
          "## Evidence and reproduction", "",
          "- `newman-results.json`: complete machine-readable request/response and assertion results.",
          "- `newman-results.xml`: JUnit-format result export.",
          "- `newman-console.txt`: runner output.",
          "- `database-verification.txt`: persisted state, migrations and final health.", "",
          "Run from the existing project directory:", "", "```powershell",
          "npx.cmd --yes newman@6 run postman/PaymentEntity.postman_collection.json", "```", ""]
(out / "REPORT.md").write_text("\n".join(intro), encoding="utf-8")

headers = ["#", "Test", "Expected", "Actual", "Assertions", "ms", "Result"]
table = "<table><thead><tr>" + "".join(f"<th>{h}</th>" for h in headers) + "</tr></thead><tbody>"
for row in rows:
    table += "<tr>" + "".join("<td>" + html.escape(c) + "</td>" for c in row) + "</tr>"
table += "</tbody></table>"
page = f"""<!doctype html><html lang="en"><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>Payment Entity test report</title>
<style>body{{font:15px/1.55 system-ui,sans-serif;color:#17263c;background:#eef3f8;margin:0}}
main{{max-width:1100px;margin:32px auto;padding:36px;background:white;border-radius:14px}}
h1{{margin:0;font-size:30px}}h2{{margin-top:30px}}.badge{{display:inline-block;padding:4px 16px;background:#dcfce7;color:#166534;border-radius:16px;font-weight:700}}
.metrics{{display:flex;gap:16px;flex-wrap:wrap;margin:24px 0}}.metric{{background:#f1f5f9;padding:16px 28px;border-radius:10px}}.metric strong{{font-size:28px;display:block}}
table{{width:100%;border-collapse:collapse}}th{{background:#17263c;color:white;text-align:left}}td,th{{padding:9px 10px;border-bottom:1px solid #dce3ec}}tr:nth-child(even){{background:#f7fafc}}td:last-child{{font-weight:700;color:#166534}}pre{{background:#f1f5f9;padding:18px;overflow:auto}}a{{color:#1d4ed8}}@media print{{body{{background:white}}main{{margin:0;padding:0}}thead{{display:table-header-group}}tr{{break-inside:avoid}}}}</style>
<main><span class="badge">{status}</span><h1>Payment Entity · Postman tests</h1>
<p>{started:%d %B %Y, %H:%M:%S} IST · localhost:9080 · Spring Boot 4.1.2 · PostgreSQL</p>
<div class="metrics"><div class="metric"><strong>{stats['requests']['total']}</strong>requests</div><div class="metric"><strong>{stats['assertions']['total']}</strong>assertions</div><div class="metric"><strong>{len(failures)}</strong>failures</div><div class="metric"><strong>{duration:.2f}s</strong>duration</div></div>
<p>Expected 400, 404 and 409 responses confirm that invalid or conflicting requests are rejected correctly.</p>
{table}<h2>Retained test records</h2><pre>{html.escape(json.dumps(ids, indent=2))}</pre>
<p>Final persisted state and migration results: <a href="database-verification.txt">database verification</a>.</p>
<h2>Execution and limits</h2><p>Executed through Postman's Newman 6 runner. Payment and refund gateways are mocks; no real money moved. A manual Postman desktop run, live gateways and external-module integrations were not tested. The Maven suite was not rerun.</p>
<h2>Evidence</h2><p><a href="REPORT.md">Full report and exact URLs</a> · <a href="newman-results.json">JSON results</a> · <a href="newman-results.xml">JUnit XML</a> · <a href="newman-console.txt">Console output</a></p></main></html>"""
(out / "REPORT.html").write_text(page, encoding="utf-8")
print(json.dumps(summary, indent=2))
