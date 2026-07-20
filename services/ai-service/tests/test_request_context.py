import logging

from app.observability.request_context import (
    RequestContextFilter,
    bind_request_id,
    current_request_id,
    normalize_request_id,
    request_id_from_metadata,
    resolve_request_id,
)


def test_request_id_resolution_is_bounded_and_prefers_metadata() -> None:
    long_id = "trace " + "x" * 200

    assert normalize_request_id(long_id) == ("trace_" + "x" * 122)
    assert request_id_from_metadata(
        (("other", "ignored"), ("X-Request-ID", "metadata-id"))
    ) == "metadata-id"
    assert resolve_request_id(
        (("x-request-id", "metadata-id"),),
        "protobuf-id",
    ) == "metadata-id"
    assert resolve_request_id((), "protobuf-id") == "protobuf-id"


def test_request_context_filter_binds_and_resets() -> None:
    record = logging.LogRecord(
        name="test",
        level=logging.INFO,
        pathname=__file__,
        lineno=1,
        msg="event",
        args=(),
        exc_info=None,
    )
    context_filter = RequestContextFilter()

    assert current_request_id() is None
    with bind_request_id("trace-id"):
        assert current_request_id() == "trace-id"
        assert context_filter.filter(record) is True
        assert getattr(record, "request_id") == "trace-id"
    assert current_request_id() is None
