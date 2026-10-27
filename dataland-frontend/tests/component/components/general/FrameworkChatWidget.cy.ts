import FrameworkChatWidget from '@/components/general/FrameworkChatWidget.vue';
import { minimalKeycloakMock } from '@ct/testUtils/Keycloak';

describe('Framework assistant', () => {
  beforeEach(() => {
    cy.mountWithPlugins(FrameworkChatWidget, { keycloak: minimalKeycloakMock({}) });
    cy.get('[data-test="framework-chat-open"]').click();
  });

  it('prevents empty questions and submits follow-ups with history and verified source links', () => {
    cy.get('[data-test="framework-chat-send"]').should('be.disabled');
    cy.intercept('POST', '**/specifications/chat', {
      body: {
        answer: 'SFDR includes emissions fields.',
        sources: ['/specifications/frameworks/sfdr', 'https://example.com/untrusted'],
      },
      delay: 200,
    }).as('chat');
    cy.get('[data-test="framework-chat-input"]').type('What is SFDR?');
    cy.get('[data-test="framework-chat-send"]').click();
    cy.get('[data-test="framework-chat-input"]').should('be.disabled');
    cy.wait('@chat').its('request.body').should('deep.equal', { message: 'What is SFDR?', history: [] });
    cy.contains('SFDR includes emissions fields.').should('be.visible');
    cy.get('a[href="/specifications/frameworks/sfdr"]').should('be.visible');
    cy.get('a[href="https://example.com/untrusted"]').should('not.exist');
    cy.get('[data-test="framework-chat-input"]').type('Which fields?');
    cy.get('[data-test="framework-chat-send"]').click();
    cy.wait('@chat')
      .its('request.body.history')
      .should('deep.equal', [
        { role: 'USER', content: 'What is SFDR?' },
        { role: 'ASSISTANT', content: 'SFDR includes emissions fields.' },
      ]);
    cy.contains('button', 'Clear conversation').click();
    cy.get('[data-test="framework-chat-message"]').should('not.exist');
  });

  it('keeps failed questions retryable and explains an unconfigured model', () => {
    cy.intercept('POST', '**/specifications/chat', { statusCode: 503, body: {} }).as('chat');
    cy.get('[data-test="framework-chat-input"]').type('What is PCAF?');
    cy.get('[data-test="framework-chat-send"]').click();
    cy.wait('@chat');
    cy.get('[data-test="framework-chat-error"]').should('contain.text', 'not configured');
    cy.get('[data-test="framework-chat-input"]').should('have.value', 'What is PCAF?').and('not.be.disabled');
    cy.get('[data-test="framework-chat-message"]').should('not.exist');
  });
});
