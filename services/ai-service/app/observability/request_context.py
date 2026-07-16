"""Bounded request correlation for the async Python service."""

from __future__ import annotations

import logging
import re
import uuid
from collections.abc import Iterable, Iterator
from contextlib import contextmanager
from contextvars import ContextVar
from typing import Any


REQUEST_ID_HEADER = "x-request-id"
MAX_REQUEST_ID_CHARS = 128
_UNSAFE_REQUEST_ID_CHARS = re.compile(r"[^A-Za-z0-9._:-]")
_request_id_ctx: ContextVar[str | None] = ContextVar(
    "request_id",
    default=None,
)


def normalize_request_id(value: Any) -> str | None:
    """Return a log/metadata-safe bounded request id, or None when absent."""

    if not isinstance(value, str) or not value.strip():
        return None
    normalized = _UNSAFE_REQUEST_ID_CHARS.sub("_", value.strip())
    return normalized[:MAX_REQUEST_ID_CHARS] or None


def request_id_from_metadata(metadata: Iterable[Any] | None) -> str | None:
    """Read the canonical gRPC header from tuple or grpc metadata entries."""

    if metadata is None:
        return None
    try:
        entries = iter(metadata)
    except TypeError:
        return None
    for item in entries:
        try:
            key, value = item
        except (TypeError, ValueError):
            key = getattr(item, "key", None)
            value = getattr(item, "value", None)
        if isinstance(key, str) and key.lower() == REQUEST_ID_HEADER:
            return normalize_request_id(value)
    return None


def resolve_request_id(
    metadata: Iterable[Any] | None,
    *fallback_values: Any,
) -> str:
    """Prefer gRPC metadata, then protobuf identifiers, then a generated UUID."""

    metadata_value = request_id_from_metadata(metadata)
    if metadata_value:
        return metadata_value
    for value in fallback_values:
        normalized = normalize_request_id(value)
        if normalized:
            return normalized
    return str(uuid.uuid4())


def current_request_id() -> str | None:
    return _request_id_ctx.get()


@contextmanager
def bind_request_id(request_id: str) -> Iterator[None]:
    token = _request_id_ctx.set(request_id)
    try:
        yield
    finally:
        _request_id_ctx.reset(token)


class RequestContextFilter(logging.Filter):
    """Populate request_id for log formatters without adding metric labels."""

    def filter(self, record: logging.LogRecord) -> bool:
        record.request_id = current_request_id() or "-"
        return True


def configure_request_context_logging() -> None:
    """Attach the context filter to every configured root handler once."""

    root = logging.getLogger()
    for handler in root.handlers:
        if not any(
            isinstance(item, RequestContextFilter)
            for item in handler.filters
        ):
            handler.addFilter(RequestContextFilter())
