import SwiftUI
import SharedAiKit

// MARK: - ViewModel bridging Kotlin Flow to SwiftUI
@MainActor
class IosChatViewModel: ObservableObject {
    @Published var messages: [(id: UUID, text: String, isUser: Bool)] = []
    @Published var inputText: String = ""
    @Published var isStreaming: Bool = false
    
    private var activeJob: CancellableJob? = nil
    
    // Create AivoSdk instance using iOS shim factory
    private lazy var aivo: AivoSdk = {
        let key = ProcessInfo.processInfo.environment["GEMINI_API_KEY"] ?? ""
        return AivoSdkKt.create(
            provider: AivoProvider.gemini,
            model: GeminiModel.gemini38Flash.modelId,
            apiKey: key,
            baseUrl: nil,
            systemPrompt: "You are a concise iOS AI assistant.",
            tools: []
        )
    }()

    func send() {
        let text = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !isStreaming else { return }
        
        inputText = ""
        messages.append((id: UUID(), text: text, isUser: true))
        
        let assistantMsgId = UUID()
        messages.append((id: assistantMsgId, text: "", isUser: false))
        isStreaming = true
        
        // Active streaming job
        activeJob?.cancel()
        activeJob = aivo.stream(
            conversationId: ConversationId(value: "ios-session"),
            message: text,
            agentId: nil,
            variables: [:]
        ).subscribe(
            onEach: { [weak self] event in
                guard let self = self else { return }
                if let delta = event as? AgentEvent.TextDelta {
                    if let index = self.messages.firstIndex(where: { $0.id == assistantMsgId }) {
                        self.messages[index].text += delta.text
                    }
                }
            },
            onError: { [weak self] error in
                guard let self = self else { return }
                self.isStreaming = false
                print("Aivo SDK stream error: \(error.localizedDescription)")
            },
            onComplete: { [weak self] in
                guard let self = self else { return }
                self.isStreaming = false
            }
        )
    }
    
    func cancel() {
        activeJob?.cancel()
        activeJob = nil
        isStreaming = false
    }
}

// MARK: - Native SwiftUI Chat Screen
struct IosChatView: View {
    @StateObject private var viewModel = IosChatViewModel()

    var body: some View {
        NavigationView {
            VStack {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(viewModel.messages, id: \.id) { msg in
                                HStack {
                                    if msg.isUser { Spacer() }
                                    Text(msg.text)
                                        .padding(12)
                                        .background(msg.isUser ? Color.blue : Color(.secondarySystemBackground))
                                        .foregroundColor(msg.isUser ? .white : .primary)
                                        .cornerRadius(14)
                                        .frame(maxWidth: 280, alignment: msg.isUser ? .trailing : .leading)
                                    if !msg.isUser { Spacer() }
                                }
                                .id(msg.id)
                            }
                        }
                        .padding()
                    }
                    .onChange(of: viewModel.messages.last?.text) { _ in
                        if let lastId = viewModel.messages.last?.id {
                            proxy.scrollTo(lastId, anchor: .bottom)
                        }
                    }
                }

                // Input bar
                HStack {
                    TextField("Ask assistant...", text: $viewModel.inputText)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                    
                    Button(action: viewModel.send) {
                        Image(systemName: "paperplane.fill")
                    }
                    .disabled(viewModel.inputText.isEmpty || viewModel.isStreaming)
                }
                .padding()
            }
            .navigationTitle("Aivo iOS")
            .onDisappear {
                viewModel.cancel()
            }
        }
    }
}
