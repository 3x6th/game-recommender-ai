import logging
import os
import uuid
from contextvars import ContextVar
from grpc_interceptor import ServerInterceptor

# Загружаем имя заголовка из env или берём дефолт
REQUEST_ID_HEADER = os.getenv("REQUEST_ID_HEADER", "rquid")

# создаём контекст-переменную (аналог MDC)
request_id = os.getenv("REQUEST_ID_LOGGING_PARAM", "RqUID")
request_id_ctx = ContextVar(request_id, default=None)

class RequestIdInterceptor(ServerInterceptor):
    def intercept(self, method, request, context, method_name):
        # достаём rqUID из metadata
        metadata = dict(context.invocation_metadata())
        rqid = metadata.get(REQUEST_ID_HEADER) or str(uuid.uuid4())

        # кладём в contextvars (будет доступно внутри логов)
        request_id_ctx.set(rqid)

        # продолжаем выполнение
        return method(request, context)

# настраиваем логгер
class RequestIdFilter(logging.Filter):
    def filter(self, record):
        record.request_id = request_id_ctx.get() or "-"
        return True

logger = logging.getLogger("ai-service")
handler = logging.StreamHandler()
formatter = logging.Formatter(
    "%(asctime)s [%(levelname)s] [rqUID:%(request_id)s] %(name)s - %(message)s"
)
handler.setFormatter(formatter)
logger.addHandler(handler)
logger.addFilter(RequestIdFilter())
logger.setLevel(logging.INFO)
