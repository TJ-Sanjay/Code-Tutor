import time


class RequestTimingMiddleware:
    """Logs how long each request takes."""

    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        start = time.time()
        response = self.get_response(request)
        response["X-Elapsed"] = str(time.time() - start)
        return response
