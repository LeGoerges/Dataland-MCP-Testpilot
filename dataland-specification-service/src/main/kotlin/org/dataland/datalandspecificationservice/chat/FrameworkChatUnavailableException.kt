package org.dataland.datalandspecificationservice.chat

import org.dataland.datalandbackendutils.exceptions.SingleApiException
import org.dataland.datalandbackendutils.model.ErrorDetails
import org.springframework.http.HttpStatus

/** Uses the shared API error handler so an unconfigured model remains an HTTP 503. */
class FrameworkChatUnavailableException : SingleApiException("Framework chat model is not configured.") {
    override fun getErrorResponse(): ErrorDetails =
        ErrorDetails(
            errorType = "framework-chat-unavailable",
            summary = "Framework chat unavailable",
            message = "Framework chat model is not configured.",
            httpStatus = HttpStatus.SERVICE_UNAVAILABLE,
        )
}
