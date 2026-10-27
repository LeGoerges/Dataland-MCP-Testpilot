import { useMutation, type UseMutationReturnType } from '@tanstack/vue-query';
import { type FrameworkChatRequest, type FrameworkChatResponse } from '@clients/specificationservice';
import { useApiClient } from '@/utils/useApiClient';

export interface ChatVariables {
  request: FrameworkChatRequest;
}

/** Sends a framework question with transient history. Does not invalidate server data. */
export function useChatMutation(): UseMutationReturnType<FrameworkChatResponse, Error, ChatVariables, unknown> {
  const apiClientProvider = useApiClient();
  return useMutation({
    mutationFn: async ({ request }: ChatVariables) => {
      const response = await apiClientProvider.apiClients.frameworkChatController.chat(request);
      return response.data;
    },
  });
}
