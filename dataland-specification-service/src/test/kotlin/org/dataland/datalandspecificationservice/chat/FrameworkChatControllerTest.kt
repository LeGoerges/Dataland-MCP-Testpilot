package org.dataland.datalandspecificationservice.chat

import org.dataland.datalandbackendutils.exceptions.InternalServerErrorApiException
import org.dataland.datalandspecification.database.SpecificationDatabase
import org.dataland.datalandspecificationservice.controller.FrameworkChatController
import org.dataland.datalandspecificationservice.model.FrameworkChatMessage
import org.dataland.datalandspecificationservice.model.FrameworkChatRequest
import org.dataland.datalandspecificationservice.model.FrameworkChatRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.MessageType
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus

/** Tests history role isolation and unavailable/empty model responses without a cloud dependency. */
class FrameworkChatControllerTest {
    private val models = mock<ObjectProvider<ChatModel>>()
    private val model = mock<ChatModel>()
    private val controller = FrameworkChatController(object : SpecificationDatabase() {}, models)

    @Test
    fun `missing model is reported as unavailable`() {
        val error = assertThrows<FrameworkChatUnavailableException> { controller.chat(FrameworkChatRequest("What is SFDR?")) }
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getErrorResponse().httpStatus)
    }

    @Test
    fun `chat preserves history roles and does not invent source links`() {
        whenever(model.options).thenReturn(OpenAiChatOptions.builder().model("test").build())
        whenever(models.ifAvailable).thenReturn(model)
        whenever(model.call(any<Prompt>())).thenReturn(ChatResponse(listOf(Generation(AssistantMessage("Answer")))))
        val response =
            controller.chat(
                FrameworkChatRequest(
                    "Follow-up",
                    listOf(
                        FrameworkChatMessage(FrameworkChatRole.USER, "Question"),
                        FrameworkChatMessage(FrameworkChatRole.ASSISTANT, "Previous answer"),
                    ),
                ),
            )
        assertEquals("Answer", response.answer)
        assertTrue(response.sources.isEmpty())
        val prompt = argumentCaptor<Prompt>()
        verify(model).call(prompt.capture())
        assertEquals(
            listOf(MessageType.SYSTEM, MessageType.USER, MessageType.ASSISTANT, MessageType.USER),
            prompt.firstValue.instructions.map { it.messageType },
        )
        assertEquals(
            "Follow-up",
            prompt.firstValue.instructions
                .last()
                .text,
        )
    }

    @Test
    fun `empty answer is reported as upstream failure`() {
        whenever(model.options).thenReturn(OpenAiChatOptions.builder().model("test").build())
        whenever(models.ifAvailable).thenReturn(model)
        whenever(model.call(any<Prompt>())).thenReturn(ChatResponse(listOf(Generation(AssistantMessage(" ")))))
        assertThrows<InternalServerErrorApiException> { controller.chat(FrameworkChatRequest("Question")) }
    }
}
