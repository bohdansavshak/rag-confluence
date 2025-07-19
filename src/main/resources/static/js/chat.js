// Chat application JavaScript
class ChatApp {
    constructor() {
        this.initializeElements();
        this.initializeEventListeners();
        this.checkServerStatus();
        this.autoResizeTextarea();
    }

    initializeElements() {
        this.chatMessages = document.getElementById('chatMessages');
        this.chatMain = this.chatMessages.parentElement; // Get the scrollable container
        this.messageInput = document.getElementById('messageInput');
        this.chatForm = document.getElementById('chatForm');
        this.sendBtn = document.getElementById('sendBtn');
        this.docsBtn = document.getElementById('docsBtn');
        this.typingIndicator = document.getElementById('typingIndicator');
        this.relevantDocs = document.getElementById('relevantDocs');
        this.docsList = document.getElementById('docsList');
        this.statusDot = document.getElementById('statusDot');
        this.statusText = document.getElementById('statusText');
        this.errorToast = document.getElementById('errorToast');
        this.errorMessage = document.getElementById('errorMessage');
    }

    initializeEventListeners() {
        // Form submission
        this.chatForm.addEventListener('submit', (e) => this.handleSubmit(e));
        
        // Input events
        this.messageInput.addEventListener('input', () => this.handleInputChange());
        this.messageInput.addEventListener('keydown', (e) => this.handleKeyDown(e));
        
        // Button events
        this.docsBtn.addEventListener('click', () => this.toggleRelevantDocs());
        
        // Auto-hide error toast
        setTimeout(() => this.hideErrorToast(), 5000);
    }

    handleSubmit(e) {
        e.preventDefault();
        const message = this.messageInput.value.trim();
        
        if (!message) {
            this.showError('Please enter a message');
            return;
        }

        if (message.length > 1000) {
            this.showError('Message is too long (max 1000 characters)');
            return;
        }

        this.sendMessage(message);
    }

    handleInputChange() {
        const length = this.messageInput.value.length;
        
        // Enable/disable send button
        this.sendBtn.disabled = length === 0 || length > 1000;
        
        // Auto-resize textarea
        this.autoResizeTextarea();
    }

    handleKeyDown(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            this.handleSubmit(e);
        }
    }

    autoResizeTextarea() {
        this.messageInput.style.height = 'auto';
        this.messageInput.style.height = Math.min(this.messageInput.scrollHeight, 120) + 'px';
    }

    async sendMessage(message) {
        // Add user message to chat
        this.addMessage(message, 'user');
        
        // Clear input and reset form
        this.messageInput.value = '';
        this.handleInputChange();
        
        // Show typing indicator
        this.showTypingIndicator();
        
        // Disable send button
        this.sendBtn.disabled = true;
        
        // Create a placeholder for the streaming response
        const botMessageDiv = this.createStreamingMessagePlaceholder();
        let sourcePages = [];
        let fullResponse = '';
        
        try {
            // Use EventSource for streaming (GET request with query parameter)
            const encodedMessage = encodeURIComponent(message);
            const eventSource = new EventSource(`/api/chat/ask-stream?question=${encodedMessage}`);

            // Handle different event types
            eventSource.addEventListener('sources', (event) => {
                try {
                    const data = JSON.parse(event.data);
                    sourcePages = data.sourcePages || [];
                } catch (error) {
                    console.error('Error parsing sources:', error);
                }
            });

            eventSource.addEventListener('chunk', (event) => {
                try {
                    const data = JSON.parse(event.data);
                    const content = data.content || '';
                    fullResponse += content;
                    this.updateStreamingMessage(botMessageDiv, fullResponse);
                } catch (error) {
                    console.error('Error parsing chunk:', error);
                }
            });

            eventSource.addEventListener('complete', (event) => {
                try {
                    const data = JSON.parse(event.data);
                    fullResponse = data.fullResponse || fullResponse;
                    this.finalizeStreamingMessage(botMessageDiv, fullResponse, sourcePages);
                    eventSource.close();
                } catch (error) {
                    console.error('Error parsing completion:', error);
                    this.finalizeStreamingMessage(botMessageDiv, fullResponse, sourcePages);
                    eventSource.close();
                }
            });

            eventSource.addEventListener('error', (event) => {
                try {
                    const data = JSON.parse(event.data);
                    this.showError(data.message || 'Failed to get response');
                    this.removeStreamingMessage(botMessageDiv);
                    this.addMessage('I apologize, but I encountered an error processing your question. Please try again.', 'bot');
                } catch (error) {
                    console.error('Error parsing error event:', error);
                    this.showError('Failed to get response');
                    this.removeStreamingMessage(botMessageDiv);
                    this.addMessage('I apologize, but I encountered an error processing your question. Please try again.', 'bot');
                }
                eventSource.close();
            });

            eventSource.onerror = (error) => {
                console.error('EventSource error:', error);
                this.showError('Network error. Please check your connection and try again.');
                this.removeStreamingMessage(botMessageDiv);
                this.addMessage('I apologize, but I\'m having trouble connecting to the server. Please try again later.', 'bot');
                eventSource.close();
            };

        } catch (error) {
            console.error('Error setting up streaming:', error);
            this.showError('Network error. Please check your connection and try again.');
            this.removeStreamingMessage(botMessageDiv);
            this.addMessage('I apologize, but I\'m having trouble connecting to the server. Please try again later.', 'bot');
        } finally {
            this.hideTypingIndicator();
            this.sendBtn.disabled = false;
        }
    }

    async toggleRelevantDocs() {
        const message = this.messageInput.value.trim();
        
        if (!message) {
            this.showError('Please enter a question to find relevant documents');
            return;
        }

        const isActive = this.docsBtn.classList.contains('active');
        
        if (isActive) {
            // Hide relevant docs
            this.relevantDocs.style.display = 'none';
            this.docsBtn.classList.remove('active');
            return;
        }

        // Show loading state
        this.docsBtn.classList.add('active');
        this.relevantDocs.style.display = 'block';
        this.docsList.innerHTML = '<div class="loading">Searching for relevant documents...</div>';

        try {
            const response = await fetch('/api/chat/relevant-docs', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ question: message })
            });

            const data = await response.json();
            
            if (data.status === 'success') {
                this.displayRelevantDocs(data.relevantDocuments);
            } else {
                this.showError(data.message || 'Failed to get relevant documents');
                this.docsList.innerHTML = '<div class="error">Failed to load documents</div>';
            }
        } catch (error) {
            console.error('Error fetching relevant docs:', error);
            this.showError('Network error while fetching documents');
            this.docsList.innerHTML = '<div class="error">Network error</div>';
        }
    }

    displayRelevantDocs(documents) {
        if (!documents || documents.length === 0) {
            this.docsList.innerHTML = '<div class="no-docs">No relevant documents found</div>';
            return;
        }

        this.docsList.innerHTML = documents
            .map(doc => `<span class="doc-tag">${this.escapeHtml(doc)}</span>`)
            .join('');
    }

    addMessage(text, sender) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${sender}-message`;
        
        const currentTime = new Date().toLocaleTimeString('en-US', { 
            hour: '2-digit', 
            minute: '2-digit',
            hour12: false 
        });
        
        const avatarIcon = sender === 'user' ? 'fas fa-user' : 'fas fa-dice';
        
        messageDiv.innerHTML = `
            <div class="message-avatar">
                <i class="${avatarIcon}"></i>
            </div>
            <div class="message-content">
                <div class="message-text">${this.escapeHtml(text)}</div>
                <div class="message-time">${currentTime}</div>
            </div>
        `;
        
        this.chatMessages.appendChild(messageDiv);
        this.scrollToBottom();
    }

    addMessageWithSources(text, sourcePages, sender) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${sender}-message`;
        
        const currentTime = new Date().toLocaleTimeString('en-US', { 
            hour: '2-digit', 
            minute: '2-digit',
            hour12: false 
        });
        
        const avatarIcon = sender === 'user' ? 'fas fa-user' : 'fas fa-dice';
        
        // Create source pages HTML if available
        let sourcePagesHtml = '';
        if (sourcePages && sourcePages.length > 0) {
            const sourceLinks = sourcePages
                .filter(page => page.url && page.title) // Only show pages with valid URLs and titles
                .map(page => `
                    <a href="${this.escapeHtml(page.url)}" target="_blank" class="source-link" title="Open in Confluence">
                        <i class="fas fa-external-link-alt"></i>
                        ${this.escapeHtml(page.title)}
                        ${page.spaceName ? `<span class="space-name">(${this.escapeHtml(page.spaceName)})</span>` : ''}
                    </a>
                `).join('');
            
            if (sourceLinks) {
                sourcePagesHtml = `
                    <div class="source-pages">
                        <div class="source-pages-header">
                            <i class="fas fa-book"></i>
                            Sources:
                        </div>
                        <div class="source-pages-list">
                            ${sourceLinks}
                        </div>
                    </div>
                `;
            }
        }
        
        messageDiv.innerHTML = `
            <div class="message-avatar">
                <i class="${avatarIcon}"></i>
            </div>
            <div class="message-content">
                <div class="message-text">${this.escapeHtml(text)}</div>
                ${sourcePagesHtml}
                <div class="message-time">${currentTime}</div>
            </div>
        `;
        
        this.chatMessages.appendChild(messageDiv);
        this.scrollToBottom();
    }

    showTypingIndicator() {
        this.typingIndicator.style.display = 'block';
        this.scrollToBottom();
    }

    hideTypingIndicator() {
        this.typingIndicator.style.display = 'none';
    }

    scrollToBottom() {
        // Use requestAnimationFrame for better timing and multiple attempts for reliability
        const scroll = () => {
            this.chatMain.scrollTo({
                top: this.chatMain.scrollHeight,
                behavior: 'smooth'
            });
        };
        
        // Immediate scroll attempt
        requestAnimationFrame(scroll);
        
        // Additional scroll attempt after a short delay to handle dynamic content
        setTimeout(() => {
            requestAnimationFrame(scroll);
        }, 150);
    }

    async checkServerStatus() {
        try {
            const response = await fetch('/api/chat/health');
            const data = await response.json();
            
            if (data.status === 'healthy') {
                this.updateStatus('connected', 'Connected');
            } else {
                this.updateStatus('disconnected', 'Service unavailable');
            }
        } catch (error) {
            console.error('Health check failed:', error);
            this.updateStatus('disconnected', 'Connection failed');
        }
    }

    updateStatus(status, text) {
        this.statusDot.className = `status-dot ${status === 'connected' ? 'connected' : ''}`;
        this.statusText.textContent = text;
    }

    showError(message) {
        this.errorMessage.textContent = message;
        this.errorToast.classList.add('show');
        
        // Auto-hide after 5 seconds
        setTimeout(() => this.hideErrorToast(), 5000);
    }

    hideErrorToast() {
        this.errorToast.classList.remove('show');
    }

    createStreamingMessagePlaceholder() {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'message bot-message streaming';
        
        const currentTime = new Date().toLocaleTimeString('en-US', { 
            hour: '2-digit', 
            minute: '2-digit',
            hour12: false 
        });
        
        messageDiv.innerHTML = `
            <div class="message-avatar">
                <i class="fas fa-dice"></i>
            </div>
            <div class="message-content">
                <div class="message-text"></div>
                <div class="message-time">${currentTime}</div>
            </div>
        `;
        
        this.chatMessages.appendChild(messageDiv);
        this.scrollToBottom();
        return messageDiv;
    }

    updateStreamingMessage(messageDiv, content) {
        const messageText = messageDiv.querySelector('.message-text');
        if (messageText) {
            messageText.textContent = content;
            this.scrollToBottom();
        }
    }

    finalizeStreamingMessage(messageDiv, fullResponse, sourcePages) {
        messageDiv.classList.remove('streaming');
        
        const messageContent = messageDiv.querySelector('.message-content');
        if (messageContent) {
            // Create source pages HTML if available
            let sourcePagesHtml = '';
            if (sourcePages && sourcePages.length > 0) {
                const sourceLinks = sourcePages
                    .filter(page => page.url && page.title) // Only show pages with valid URLs and titles
                    .map(page => `
                        <a href="${this.escapeHtml(page.url)}" target="_blank" class="source-link" title="Open in Confluence">
                            <i class="fas fa-external-link-alt"></i>
                            ${this.escapeHtml(page.title)}
                            ${page.spaceName ? `<span class="space-name">(${this.escapeHtml(page.spaceName)})</span>` : ''}
                        </a>
                    `).join('');
                
                if (sourceLinks) {
                    sourcePagesHtml = `
                        <div class="source-pages">
                            <div class="source-pages-header">
                                <i class="fas fa-book"></i>
                                Sources:
                            </div>
                            <div class="source-pages-list">
                                ${sourceLinks}
                            </div>
                        </div>
                    `;
                }
            }
            
            const currentTime = new Date().toLocaleTimeString('en-US', { 
                hour: '2-digit', 
                minute: '2-digit',
                hour12: false 
            });
            
            messageContent.innerHTML = `
                <div class="message-text">${this.escapeHtml(fullResponse)}</div>
                ${sourcePagesHtml}
                <div class="message-time">${currentTime}</div>
            `;
        }
        
        this.scrollToBottom();
    }

    removeStreamingMessage(messageDiv) {
        if (messageDiv && messageDiv.parentNode) {
            messageDiv.parentNode.removeChild(messageDiv);
        }
    }

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

// Global function for closing error toast (called from HTML)
function hideErrorToast() {
    if (window.chatApp) {
        window.chatApp.hideErrorToast();
    }
}

// Initialize the chat application when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    window.chatApp = new ChatApp();
});

// Handle page visibility changes to check connection status
document.addEventListener('visibilitychange', () => {
    if (!document.hidden && window.chatApp) {
        window.chatApp.checkServerStatus();
    }
});

// Handle online/offline events
window.addEventListener('online', () => {
    if (window.chatApp) {
        window.chatApp.checkServerStatus();
    }
});

window.addEventListener('offline', () => {
    if (window.chatApp) {
        window.chatApp.updateStatus('disconnected', 'Offline');
    }
});