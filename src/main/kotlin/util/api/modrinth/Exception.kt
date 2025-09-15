package com.magnariuk.util.api.modrinth

open class ApiException(
    val msg: String = "An error occurred, but no specific reason was provided.",
    val method: String = "GET",
    val url: String? = null,
    val params: Map<String, Any>? = null,
    val data: Map<String, Any>? = null
) : Exception() {
    override fun toString(): String {
        return "API Exception:\nMessage: $msg\nMethod: $method\nURL: $url\nParams: $params\nData: $data"
    }
}

open class ResponseCodeException(
    val statusCode: Int,
    msg: String = "An error occurred, but no specific reason was provided.",
    url: String? = null,
    params: Map<String, Any>? = null,
    data: Map<String, Any>? = null,
    method: String = "GET"
) : ApiException(msg, method, url, params, data) {
    override fun toString(): String {
        return "Response Code Exception:\nStatus Code: $statusCode\nMessage: $msg\nMethod: $method\nURL: $url\nParams: $params\nData: $data"
    }
}

class TooManyRequestsException(
    url: String,
    params: Map<String, Any>,
    data: Map<String, Any>,
    method: String
) : ResponseCodeException(
    statusCode = 429,
    msg = "Too Many Requests",
    url = url,
    params = params,
    data = data,
    method = method
)

class TimeoutException(
    url: String,
    params: Map<String, Any>,
    data: Map<String, Any>,
    method: String
) : ApiException(
    msg = "Timeout",
    url = url,
    params = params,
    data = data,
    method = method
)

class InvalidRequestException(
    url: String,
    params: Map<String, Any>,
    data: Map<String, Any>,
    method: String,
    error: String,
    description: String
) : ResponseCodeException(
    statusCode = 400,
    msg = "Invalid Request: $error $description",
    url = url,
    params = params,
    data = data,
    method = method
)
