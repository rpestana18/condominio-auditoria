"""Logs of the reader (ADR 0008): the standard library `logging`, no new dependency.

Text locally and JSON in the cloud (LOG_FORMATO=json), one line per event, with the request id that came in the
X-Request-Id header (the rag sends the one of the queue message). Messages in Portuguese, keys in English.
Never logged: the file name, the content of the document, tokens.
"""
import json
import logging
import os
import re
import sys
import uuid
from contextvars import ContextVar
from datetime import datetime, timezone

HEADER = "X-Request-Id"
_VALID = re.compile(r"^[A-Za-z0-9._-]{1,64}$")
request_id: ContextVar[str] = ContextVar("request_id", default="-")


def accept_or_create(received: str | None) -> str:
    """The received id when valid (no log injection), otherwise a new one."""
    if received and _VALID.match(received):
        return received
    return str(uuid.uuid4())


class _ContextFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.requestId = request_id.get()
        return True


class JsonFormatter(logging.Formatter):
    """One JSON object per line, with the fields of the Java services (service, level, requestId, message)."""

    def format(self, record: logging.LogRecord) -> str:
        event = {
            "@timestamp": datetime.fromtimestamp(record.created, timezone.utc).isoformat(),
            "service": "reader",
            "level": record.levelname,
            "logger": record.name,
            "requestId": getattr(record, "requestId", "-"),
            "message": record.getMessage(),
        }
        if record.exc_info:
            event["stackTrace"] = self.formatException(record.exc_info)
        return json.dumps(event, ensure_ascii=False)


def configure() -> None:
    """Configures the "reader" logger once (text, or JSON with LOG_FORMATO=json)."""
    logger = logging.getLogger("reader")
    if logger.handlers:
        return
    handler = logging.StreamHandler(sys.stdout)
    handler.addFilter(_ContextFilter())
    if os.environ.get("LOG_FORMATO", "").lower() == "json":
        handler.setFormatter(JsonFormatter())
    else:
        handler.setFormatter(logging.Formatter(
            "%(asctime)s %(levelname)5s --- [reader] [requestId=%(requestId)s] %(name)s : %(message)s"))
    logger.addHandler(handler)
    logger.setLevel(os.environ.get("LOG_NIVEL", "INFO").upper())
    logger.propagate = False
