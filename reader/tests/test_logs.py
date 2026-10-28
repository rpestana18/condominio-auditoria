"""Logs of the reader (ADR 0008): request id, WARN for a bad file, ERROR with stack for a bug, never the file name."""
import logging

import pytest
from fastapi.testclient import TestClient

from app import logs, main
from app.main import app

client = TestClient(app)
NAME = "Recibo Fulano 123.456.789-00.pdf"


class _Records(logging.Handler):
    def __init__(self):
        super().__init__()
        self.records = []

    def emit(self, record):
        self.records.append(record)


@pytest.fixture
def records():
    handler = _Records()
    handler.addFilter(logs._ContextFilter())
    logger = logging.getLogger("reader")
    logger.addHandler(handler)
    yield handler.records
    logger.removeHandler(handler)


def text(records):
    return "\n".join(f"{r.levelname} {r.requestId} {r.getMessage()}" for r in records)


def test_request_id_received_goes_back_and_into_the_log(records):
    response = client.post("/v2/read", files={"file": ("x.txt", b"ola")}, headers={logs.HEADER: "req-reader"})

    assert response.headers[logs.HEADER] == "req-reader"
    assert all(r.requestId == "req-reader" for r in records)
    assert "POST /v2/read → 415" in text(records)


def test_invalid_request_id_is_replaced():
    response = client.get("/health", headers={logs.HEADER: "x y=forjado"})

    assert response.headers[logs.HEADER] != "x y=forjado"
    assert logs.accept_or_create(response.headers[logs.HEADER]) == response.headers[logs.HEADER]


def test_corrupted_file_is_a_warning_without_the_name(records):
    response = client.post("/v2/read", files={"file": (NAME, b"%PDF-1.4 quebrado")})

    assert response.status_code == 422
    warnings = [r for r in records if r.levelno == logging.WARNING]
    assert warnings and "não pôde ser lido" in warnings[0].getMessage()
    assert not [r for r in records if r.levelno >= logging.ERROR]
    assert "Recibo" not in text(records) and "123.456.789-00" not in text(records)


def test_unexpected_error_is_an_error_with_the_stack(records, monkeypatch):
    def bug(content):
        raise RuntimeError("defeito do leitor")

    monkeypatch.setattr(main, "read_pdf", bug)
    response = client.post("/v2/read", files={"file": (NAME, b"%PDF-1.4 qualquer")})

    assert response.status_code == 422
    errors = [r for r in records if r.levelno == logging.ERROR]
    assert len(errors) == 1 and errors[0].exc_info is not None
    assert "Recibo" not in text(records)


def test_json_format_has_the_request_id():
    record = logging.LogRecord("reader", logging.INFO, __file__, 1, "Arquivo %s lido", ("pdf",), None)
    record.requestId = "req-json"
    line = logs.JsonFormatter().format(record)

    assert '"requestId": "req-json"' in line and '"service": "reader"' in line and "Arquivo pdf lido" in line
