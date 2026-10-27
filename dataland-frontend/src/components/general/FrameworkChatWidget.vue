<template>
  <Button
    class="framework-chat-launcher"
    icon="pi pi-comments"
    label="Framework help"
    data-test="framework-chat-open"
    @click="visible = true"
  />
  <Dialog v-model:visible="visible" header="Framework assistant" :style="{ width: '36rem', maxWidth: '95vw' }">
    <p>Ask about the frameworks and data fields in the Dataland specification catalog.</p>
    <div ref="conversation" class="framework-chat-messages" role="log" aria-live="polite" aria-label="Conversation">
      <article v-for="(message, index) in messages" :key="index" class="mb-3">
        <strong>{{ message.role === 'USER' ? 'You' : 'Dataland assistant' }}</strong>
        <p class="framework-chat-text" data-test="framework-chat-message">{{ message.content }}</p>
        <ul v-if="message.sources?.length" aria-label="Retrieved specifications">
          <li v-for="source in message.sources" :key="source">
            <a :href="source" target="_blank" rel="noopener noreferrer">{{ source.split('/').at(-1) }}</a>
          </li>
        </ul>
      </article>
      <p v-if="isPending" role="status">Looking up framework specifications…</p>
    </div>
    <Message v-if="error" severity="error" class="mb-3" data-test="framework-chat-error">
      {{ errorMessage }} Your question is still in the input so you can try again.
    </Message>
    <form @submit.prevent="send">
      <label for="framework-chat-question">Your question</label>
      <Textarea
        id="framework-chat-question"
        v-model="question"
        class="w-full mt-2 mb-2"
        rows="3"
        maxlength="2000"
        :disabled="isPending"
        data-test="framework-chat-input"
      />
      <div class="flex justify-content-between gap-2">
        <Button label="Clear conversation" severity="secondary" :disabled="isPending" @click="clear" />
        <Button
          type="submit"
          label="Send"
          icon="pi pi-send"
          :loading="isPending"
          :disabled="!question.trim() || isPending"
          data-test="framework-chat-send"
        />
      </div>
    </form>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue';
import Button from 'primevue/button';
import Dialog from 'primevue/dialog';
import Message from 'primevue/message';
import Textarea from 'primevue/textarea';
import { isAxiosError } from 'axios';
import { type FrameworkChatMessage } from '@clients/specificationservice';
import { useChatMutation } from '@/api-queries/specification-service/chat/useChatMutation';

interface DisplayMessage extends FrameworkChatMessage {
  sources?: string[];
}

const visible = ref(false);
const question = ref('');
const messages = ref<DisplayMessage[]>([]);
const conversation = ref<HTMLElement>();
const { mutateAsync, isPending, error, reset } = useChatMutation();
const errorMessage = computed(() =>
  isAxiosError(error.value) && error.value.response?.status === 503
    ? 'The framework assistant is not configured yet.'
    : 'The framework assistant could not answer. Please try again.'
);

/** Sends only the bounded plain-text history and keeps failed submissions retryable. */
async function send(): Promise<void> {
  const content = question.value.trim();
  if (!content || isPending.value) return;
  try {
    const response = await mutateAsync({
      request: {
        message: content,
        history: messages.value.slice(-10).map(({ role, content: text }) => ({ role, content: text.slice(0, 6000) })),
      },
    });
    messages.value.push(
      { role: 'USER', content },
      { role: 'ASSISTANT', content: response.answer, sources: response.sources.filter(isSpecificationPath) }
    );
    question.value = '';
    await nextTick();
    conversation.value?.scrollTo({ top: conversation.value.scrollHeight });
  } catch {
    // The mutation error is rendered above; retain the question for retry.
  }
}

/** Accepts only local specification links, independently of model-generated content. */
function isSpecificationPath(source: string): boolean {
  return /^\/specifications\/(frameworks|data-point-types|data-point-base-types)(\/[a-zA-Z0-9_-]+)?$/.test(source);
}

/** Clears the transient conversation without a server round-trip. */
function clear(): void {
  messages.value = [];
  question.value = '';
  reset();
}
</script>

<style scoped>
.framework-chat-launcher {
  position: fixed;
  bottom: 1rem;
  right: 1rem;
  z-index: 100;
}

.framework-chat-messages {
  max-height: 45vh;
  overflow-y: auto;
}

.framework-chat-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
