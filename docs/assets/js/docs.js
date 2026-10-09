/**
 * Aivo SDK - Modern Interactive Documentation Logic
 */

document.addEventListener('DOMContentLoaded', () => {
  initThemeToggle();
  initAudienceSwitcher();
  initCodeCopyButtons();
  initCodeTabs();
  initSearch();
  initPlayground();
  initScrollSpy();
  initMobileDrawer();
  initSmoothNavLinks();
  initScrollReveal();
});

/* --------------------------------------------------------------------------
   1. Theme Toggle (Dark / Light)
   -------------------------------------------------------------------------- */
function initThemeToggle() {
  const toggleBtn = document.getElementById('theme-toggle');
  const storedTheme = localStorage.getItem('aivo_docs_theme') || 'dark';
  document.documentElement.setAttribute('data-theme', storedTheme);
  updateThemeIcon(storedTheme);

  if (toggleBtn) {
    toggleBtn.addEventListener('click', () => {
      const currentTheme = document.documentElement.getAttribute('data-theme') || 'dark';
      const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
      document.documentElement.setAttribute('data-theme', newTheme);
      localStorage.setItem('aivo_docs_theme', newTheme);
      updateThemeIcon(newTheme);
    });
  }
}

function updateThemeIcon(theme) {
  const toggleBtn = document.getElementById('theme-toggle');
  if (!toggleBtn) return;
  toggleBtn.innerHTML = theme === 'dark' 
    ? `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z"/></svg>`
    : `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>`;
}

/* --------------------------------------------------------------------------
   2. Audience Mode Switcher (User Guide vs SDK Contributor)
   -------------------------------------------------------------------------- */
function initAudienceSwitcher() {
  const userTab = document.getElementById('tab-user-guide');
  const devTab = document.getElementById('tab-dev-guide');
  const userSections = document.querySelectorAll('.audience-user');
  const devSections = document.querySelectorAll('.audience-dev');
  const userNav = document.getElementById('nav-user-guide');
  const devNav = document.getElementById('nav-dev-guide');

  function setAudience(mode) {
    const heroBanner = document.querySelector('.hero-banner');
    heroBanner?.classList.add('switching');

    setTimeout(() => {
      const heroTagline = document.querySelector('.hero-tagline');
      const heroTitle = document.querySelector('.hero-title');
      const heroDesc = document.querySelector('.hero-description');
      const heroActions = document.querySelector('.hero-actions');

      if (mode === 'user') {
        userTab?.classList.add('active');
        devTab?.classList.remove('active');
        userSections.forEach(el => el.style.display = '');
        devSections.forEach(el => el.style.display = 'none');
        if (userNav) userNav.style.display = '';
        if (devNav) devNav.style.display = 'none';

        if (heroTagline) heroTagline.textContent = 'Kotlin Multiplatform AI Framework';
        if (heroTitle) heroTitle.textContent = 'Build Resilient Agentic AI Applications';
        if (heroDesc) heroDesc.textContent = 'Aivo SDK brings autonomous reasoning, multi-agent collaboration, reactive Jetpack Compose streaming, and robust human confirmation gates to Android, iOS, and JVM.';
        if (heroActions) {
          heroActions.innerHTML = `
            <a href="#interactive-playground" class="btn-primary">Try Interactive Configurator</a>
            <a href="#getting-started" class="btn-secondary">Explore User Guide</a>
          `;
        }
        localStorage.setItem('aivo_docs_audience', 'user');
      } else {
        devTab?.classList.add('active');
        userTab?.classList.remove('active');
        devSections.forEach(el => el.style.display = '');
        userSections.forEach(el => el.style.display = 'none');
        if (devNav) devNav.style.display = '';
        if (userNav) userNav.style.display = 'none';

        if (heroTagline) heroTagline.textContent = 'Core SDK Architecture & Engineering';
        if (heroTitle) heroTitle.textContent = 'Contribute to the Aivo Multiplatform Engine';
        if (heroDesc) heroDesc.textContent = 'Deep-dive into the 4-layer clean architecture, zero-dependency SSE streaming post-decoders, reactive execution lifecycles, and contributor invariants that power Aivo SDK.';
        if (heroActions) {
          heroActions.innerHTML = `
            <a href="#architecture-layers" class="btn-primary">View 4-Layer Architecture</a>
            <a href="#execution-flows" class="btn-secondary">Explore Execution Diagrams</a>
          `;
        }
        localStorage.setItem('aivo_docs_audience', 'dev');
      }

      heroBanner?.classList.remove('switching');
      updateToc();
      updateActiveSidebarLink();
      if (window.Prism) Prism.highlightAll();
    }, 150);
  }

  userTab?.addEventListener('click', () => {
    setAudience('user');
    const firstUserSec = document.querySelector('.audience-user .doc-section');
    if (firstUserSec) firstUserSec.scrollIntoView({ behavior: 'smooth' });
  });

  devTab?.addEventListener('click', () => {
    setAudience('dev');
    const firstDevSec = document.querySelector('.audience-dev .doc-section');
    if (firstDevSec) firstDevSec.scrollIntoView({ behavior: 'smooth' });
  });

  const saved = localStorage.getItem('aivo_docs_audience') || 'user';
  setAudience(saved);
}

/* --------------------------------------------------------------------------
   3. Code Copy Buttons
   -------------------------------------------------------------------------- */
function initCodeCopyButtons() {
  document.querySelectorAll('.copy-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const targetId = btn.getAttribute('data-target');
      let text = '';
      if (targetId) {
        text = document.getElementById(targetId)?.innerText || '';
      } else {
        const codeBlock = btn.closest('.code-block')?.querySelector('pre code');
        text = codeBlock ? codeBlock.innerText : '';
      }

      navigator.clipboard.writeText(text).then(() => {
        const originalText = btn.innerHTML;
        btn.innerHTML = `✓ Copied!`;
        btn.classList.add('copied');
        setTimeout(() => {
          btn.innerHTML = originalText;
          btn.classList.remove('copied');
        }, 2000);
      });
    });
  });
}

/* --------------------------------------------------------------------------
   4. Code Block Tab Switching
   -------------------------------------------------------------------------- */
function initCodeTabs() {
  document.querySelectorAll('.code-tabs').forEach(tabGroup => {
    const tabs = tabGroup.querySelectorAll('.code-tab');
    tabs.forEach(tab => {
      tab.addEventListener('click', () => {
        tabs.forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        const targetId = tab.getAttribute('data-pane');
        const codeBlock = tab.closest('.code-block');
        codeBlock.querySelectorAll('.code-pane').forEach(pane => {
          pane.style.display = (pane.id === targetId) ? 'block' : 'none';
        });
      });
    });
  });
}

/* --------------------------------------------------------------------------
   5. Interactive Playground / Code Generator
   -------------------------------------------------------------------------- */
function initPlayground() {
  const providerInput = document.getElementById('pg-provider');
  const modelSelect = document.getElementById('pg-model');
  const toolsCheck = document.getElementById('pg-tools');
  const memoryCheck = document.getElementById('pg-memory');
  const streamingCheck = document.getElementById('pg-streaming');
  const gateCheck = document.getElementById('pg-gate');
  const codeOutput = document.getElementById('pg-code-output');
  const codeBlock = codeOutput?.closest('.playground-code-block');
  const providerPills = document.querySelectorAll('.provider-pill');
  const presetChips = document.querySelectorAll('.preset-chip');

  if (!providerInput || !modelSelect || !codeOutput) return;

  const modelOptions = {
    ollama: [
      { id: 'OllamaModel.GPT_OSS_120B.modelId', label: 'gpt-oss-120b (Recommended Cloud)' },
      { id: 'OllamaModel.LLAMA_3_3_70B.modelId', label: 'llama3.3:70b' },
      { id: 'OllamaModel.MISTRAL_SMALL_24B.modelId', label: 'mistral-small:24b' },
      { id: '"my-custom-model:latest"', label: 'Custom Model ID (String)' }
    ],
    gemini: [
      { id: 'GeminiModel.GEMINI_2_5_FLASH.modelId', label: 'gemini-2.5-flash (Fast & Multimodal)' },
      { id: 'GeminiModel.GEMINI_2_5_PRO.modelId', label: 'gemini-2.5-pro (Deep Reasoning)' },
      { id: '"gemini-custom"', label: 'Custom Model ID (String)' }
    ],
    openrouter: [
      { id: 'OpenRouterModel.LING_3_0_FLASH_SANTE_FREE.modelId', label: 'openrouter/free (Ling 3.0 Flash)' },
      { id: 'OpenRouterModel.CLAUDE_3_5_SONNET.modelId', label: 'anthropic/claude-3.5-sonnet' },
      { id: '"openai/gpt-4o"', label: 'Custom Model ID (String)' }
    ]
  };

  function setProvider(providerKey) {
    providerInput.value = providerKey;
    providerPills.forEach(pill => {
      pill.classList.toggle('active', pill.getAttribute('data-provider') === providerKey);
    });
    updateModels();
  }

  providerPills.forEach(pill => {
    pill.addEventListener('click', () => {
      const p = pill.getAttribute('data-provider');
      if (p) setProvider(p);
      clearActivePreset();
    });
  });

  function clearActivePreset() {
    presetChips.forEach(c => c.classList.remove('active'));
  }

  // Preset buttons handler
  presetChips.forEach(chip => {
    chip.addEventListener('click', () => {
      clearActivePreset();
      chip.classList.add('active');
      const preset = chip.getAttribute('data-preset');

      if (preset === 'quick-stream') {
        setProvider('ollama');
        modelSelect.value = 'OllamaModel.GPT_OSS_120B.modelId';
        if (toolsCheck) toolsCheck.checked = false;
        if (streamingCheck) streamingCheck.checked = true;
        if (memoryCheck) memoryCheck.checked = false;
        if (gateCheck) gateCheck.checked = false;
      } else if (preset === 'full-agent') {
        setProvider('ollama');
        modelSelect.value = 'OllamaModel.GPT_OSS_120B.modelId';
        if (toolsCheck) toolsCheck.checked = true;
        if (streamingCheck) streamingCheck.checked = true;
        if (memoryCheck) memoryCheck.checked = true;
        if (gateCheck) gateCheck.checked = false;
      } else if (preset === 'safe-mutation') {
        setProvider('gemini');
        modelSelect.value = 'GeminiModel.GEMINI_2_5_FLASH.modelId';
        if (toolsCheck) toolsCheck.checked = true;
        if (streamingCheck) streamingCheck.checked = true;
        if (memoryCheck) memoryCheck.checked = true;
        if (gateCheck) gateCheck.checked = true;
      }
      updateGeneratedCode();
    });
  });

  function updateModels() {
    const selectedProvider = providerInput.value;
    const models = modelOptions[selectedProvider] || [];
    modelSelect.innerHTML = '';
    models.forEach(m => {
      const opt = document.createElement('option');
      opt.value = m.id;
      opt.textContent = m.label;
      modelSelect.appendChild(opt);
    });
    updateGeneratedCode();
  }

  function updateGeneratedCode() {
    const providerKey = providerInput.value;
    const modelId = modelSelect.value || 'OllamaModel.GPT_OSS_120B.modelId';
    const hasTools = toolsCheck?.checked;
    const hasMemory = memoryCheck?.checked;
    const hasStreaming = streamingCheck?.checked;
    const hasGate = gateCheck?.checked;

    let providerEnum = 'AivoProvider.OLLAMA';
    let keyProperty = 'BuildConfig.OLLAMA_API_KEY';
    if (providerKey === 'gemini') {
      providerEnum = 'AivoProvider.GEMINI';
      keyProperty = 'BuildConfig.GEMINI_API_KEY';
    } else if (providerKey === 'openrouter') {
      providerEnum = 'AivoProvider.OPEN_ROUTER';
      keyProperty = 'BuildConfig.OPENROUTER_API_KEY';
    }

    let code = `// 1. Initialize Aivo SDK with strongly-typed configuration\n`;
    code += `val aivo = AivoSdk.create(\n`;
    code += `    provider = ${providerEnum},\n`;
    code += `    model = ${modelId},\n`;
    code += `    apiKey = ${keyProperty}\n`;
    code += `)\n\n`;

    if (hasTools) {
      code += `// 2. Define custom tool${hasGate ? ' with Human Confirmation Gate' : ''}\n`;
      code += `val actionTool = tool("${hasGate ? 'transfer_funds' : 'get_weather'}", "${hasGate ? 'Transfer bank funds' : 'Fetches live weather'}") {\n`;
      if (hasGate) {
        code += `    riskLevel = RiskLevel.HIGH\n`;
        code += `    requireConfirmation = true  // Runtime suspends and prompts user in UI\n\n`;
      }
      code += `    parameters {\n`;
      if (hasGate) {
        code += `        string("recipient", "Target account", required = true)\n`;
        code += `        double("amount", "Transfer amount", required = true, minimum = 1.0)\n`;
      } else {
        code += `        string("city", "Target city name", required = true)\n`;
      }
      code += `    }\n`;
      code += `    execute { args ->\n`;
      if (hasGate) {
        code += `        val to = args.string("recipient")\n`;
        code += `        val amt = args.double("amount")\n`;
        code += `        ToolResult.Success("Transferred $$amt to $to successfully")\n`;
      } else {
        code += `        val city = args.string("city")\n`;
        code += `        ToolResult.Success("Weather in $city: 24°C, Clear")\n`;
      }
      code += `    }\n`;
      code += `}\n\n`;
    }

    if (hasMemory) {
      code += `// 3. Attach conversation memory store (retains sliding window)\n`;
      code += `val memory = InMemoryMemoryStore(maxMessages = 20)\n\n`;
    }

    if (hasStreaming) {
      code += `// 4. Reactive token streaming in Jetpack Compose ViewModel\n`;
      code += `viewModelScope.launch {\n`;
      code += `    aivo.stream("${hasGate ? 'Transfer $50 to Alice' : 'Explain quantum computing in 3 sentences'}")\n`;
      code += `        .collect { event ->\n`;
      code += `            when (event) {\n`;
      code += `                is AgentEvent.Delta -> uiState.update { it + event.text }\n`;
      if (hasGate) {
        code += `                is AgentEvent.RequiresConfirmation -> {\n`;
        code += `                    // Prompt user with Biometric/Alert dialog, then approve:\n`;
        code += `                    aivo.confirm(event.toolCallId, approved = true)\n`;
        code += `                }\n`;
      }
      code += `                is AgentEvent.Completed -> println("Done! Tokens: \${event.usage.totalTokens}")\n`;
      code += `                is AgentEvent.Error -> println("Error: \${event.error.message}")\n`;
      code += `                else -> Unit\n`;
      code += `            }\n`;
      code += `        }\n`;
      code += `}`;
    } else {
      code += `// 4. Synchronous one-shot prompt\n`;
      code += `val response = aivo.chat("${hasGate ? 'Transfer $50 to Alice' : 'Explain quantum computing in 3 sentences'}")\n`;
      code += `println(response.text)`;
    }

    codeOutput.textContent = code;
    codeOutput.className = 'language-kotlin';
    if (window.Prism) {
      Prism.highlightElement(codeOutput);
    }

    // Trigger pulse animation on code block
    if (codeBlock) {
      codeBlock.classList.remove('code-regenerating');
      void codeBlock.offsetWidth; // Force reflow
      codeBlock.classList.add('code-regenerating');
    }
  }

  modelSelect.addEventListener('change', () => {
    clearActivePreset();
    updateGeneratedCode();
  });
  toolsCheck?.addEventListener('change', () => {
    clearActivePreset();
    updateGeneratedCode();
  });
  memoryCheck?.addEventListener('change', () => {
    clearActivePreset();
    updateGeneratedCode();
  });
  streamingCheck?.addEventListener('change', () => {
    clearActivePreset();
    updateGeneratedCode();
  });
  gateCheck?.addEventListener('change', () => {
    clearActivePreset();
    if (gateCheck.checked && toolsCheck) {
      toolsCheck.checked = true; // Gate requires tools
    }
    updateGeneratedCode();
  });

  updateModels();
}

/* --------------------------------------------------------------------------
   6. Comprehensive Search Index & Modal (Ctrl+K / Cmd+K)
   -------------------------------------------------------------------------- */
function initSearch() {
  const modalBackdrop = document.getElementById('search-modal-backdrop');
  const searchBtn = document.getElementById('search-trigger');
  const searchInput = document.getElementById('search-modal-input');
  const resultsContainer = document.getElementById('search-results');

  const searchableIndex = [
    // User Guide Topics
    { title: '📦 Installation & Gradle Setup', section: 'getting-started', category: 'User Guide', text: 'Maven repository dependency com.aivo:aivo-sdk Android KMP multiplatform BuildConfig local.properties secure keys' },
    { title: '⚡ Interactive Code Configurator', section: 'interactive-playground', category: 'User Guide', text: 'Live code generator Ollama Gemini OpenRouter tools memory streaming compose' },
    { title: '🚀 Quick Start & 3 DX Levels', section: 'quick-start', category: 'User Guide', text: 'Level 1 Raw LLM Level 2 Single Agent Tools Level 3 Multi Agent Teams OllamaModel GPT_OSS_120B' },
    { title: '🌐 Providers & Typed Models', section: 'providers-models', category: 'User Guide', text: 'Ollama Cloud Gemini 2.5 Flash OpenRouter custom base url custom model string' },
    { title: '🛠️ Tools & Safety Confirmation Gates', section: 'tools-capabilities', category: 'User Guide', text: 'Type-safe tool DSL parameters schema risk level requireConfirmation Human in the loop confirmation gate execute args' },
    { title: '🤖 Creating Single Agents', section: 'single-agents', category: 'User Guide', text: 'Agent DSL Markdown Frontmatter YAML catalog JSON catalog instructions systemPrompt prompt variables scoped tools' },
    { title: '👥 Multi-Agent Teams & Supervisor', section: 'multi-agent-teams', category: 'User Guide', text: 'Supervisor pattern team routing BlackboardState StateKey delegation multi-agent coordination' },
    { title: '🌊 Reactive Streaming & Compose UI', section: 'streaming-ui', category: 'User Guide', text: 'Flow AgentEvent Delta Compose ViewModel StateFlow chat interface Swift iOS async await ChatScreen' },
    { title: '🧠 Persistent Memory & Context Window', section: 'memory-context', category: 'User Guide', text: 'Room SQLDelight InMemory sliding window token budget CharCountEstimator atomic tool pairing invariant' },
    { title: '🛡️ Resilience, Guardrails & Security', section: 'resilience-security', category: 'User Guide', text: 'Retry policy exponential backoff maxSteps bound concurrency Mutex SecretString regex redaction' },
    { title: '🧪 Offline Unit Testing', section: 'offline-testing', category: 'User Guide', text: 'FakeLlmProvider InMemoryTelemetry offline unit testing ViewModel runTest enqueueStreaming' },
    { title: '🔄 Custom Reasoning Loops & Pipelines', section: 'custom-loops', category: 'User Guide', text: 'Custom Agent Loop SequenceLoop ToolCallingLoop AgentRole StateKey ReAct blackboard pipeline' },
    { title: '🌟 Full Master Example: All Features Combined', section: 'master-example', category: 'User Guide', text: 'Complete production app master sample using Ollama memory tools multi-agent compose flow confirmation gate' },

    // Dev / Contributor Topics
    { title: '🏛️ 4-Layer Clean Architecture & Module Matrix', section: 'architecture-layers', category: 'Dev Portal', text: 'Core Transport Providers Runtime inward dependency rule SOLID clean architecture module matrix' },
    { title: '🗂️ File Inventory & Core Invariants', section: 'file-inventory', category: 'Dev Portal', text: 'File by file breakdown sdk-core pure kotlin no reflection invariant zero dependencies' },
    { title: '🔄 Execution Lifecycles & Sequence Flows', section: 'execution-flows', category: 'Dev Portal', text: 'Level 1 to Level 4 execution sequences tool safety validation pipeline SSE decoders raw frames' },
    { title: '🧩 Extending the SDK: Adding Providers', section: 'extending-sdk', category: 'Dev Portal', text: 'WireProtocol SseDecoder custom provider Mistral DeepSeek HttpRequestPayload decodeStreamFrame' },
    { title: '📜 Architecture Decision Records (ADRs)', section: 'adrs', category: 'Dev Portal', text: 'ADR 0001 ADR adoption ADR 0002 custom SSE POST decoder ADR 0003 Gemini Interactions statefulness' },
    { title: '🧰 Debugging & Troubleshooting Playbook', section: 'troubleshooting-playbook', category: 'Dev Portal', text: 'Diagnostic flowchart HTTP 401 429 SSE buffer drop CRLF split tool schema rejection concurrency deadlock' },
    { title: '🤝 Contributing Guidelines & Quality Bar', section: 'contributing-guide', category: 'Dev Portal', text: 'Conventional Commits PR checklist Zero reflection policy offline testing MockEngine' }
  ];

  function openSearch() {
    modalBackdrop?.classList.add('open');
    searchInput?.focus();
    renderSearchResults('');
  }

  function closeSearch() {
    modalBackdrop?.classList.remove('open');
    if (searchInput) searchInput.value = '';
  }

  searchBtn?.addEventListener('click', openSearch);
  modalBackdrop?.addEventListener('click', (e) => {
    if (e.target === modalBackdrop) closeSearch();
  });

  window.addEventListener('keydown', (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
      e.preventDefault();
      openSearch();
    }
    if (e.key === 'Escape' && modalBackdrop?.classList.contains('open')) {
      closeSearch();
    }
  });

  searchInput?.addEventListener('input', (e) => {
    renderSearchResults(e.target.value);
  });

  function renderSearchResults(query) {
    if (!resultsContainer) return;
    const cleanQ = query.trim().toLowerCase();
    const filtered = cleanQ === ''
      ? searchableIndex.slice(0, 6)
      : searchableIndex.filter(item => 
          item.title.toLowerCase().includes(cleanQ) || 
          item.text.toLowerCase().includes(cleanQ) ||
          item.category.toLowerCase().includes(cleanQ)
        );

    if (filtered.length === 0) {
      resultsContainer.innerHTML = `<li style="padding:1.5rem; text-align:center; color:var(--text-dim);">No documentation matches found for "${query}"</li>`;
      return;
    }

    resultsContainer.innerHTML = filtered.map(item => `
      <li class="search-result-item" data-section="${item.section}" data-category="${item.category}">
        <div class="search-result-category">${item.category}</div>
        <div class="search-result-title">${item.title}</div>
      </li>
    `).join('');

    resultsContainer.querySelectorAll('.search-result-item').forEach(li => {
      li.addEventListener('click', () => {
        const sectionId = li.getAttribute('data-section');
        const cat = li.getAttribute('data-category');
        if (cat === 'User Guide') {
          document.getElementById('tab-user-guide')?.click();
        } else {
          document.getElementById('tab-dev-guide')?.click();
        }
        closeSearch();
        setTimeout(() => {
          const targetEl = document.getElementById(sectionId);
          if (targetEl) {
            targetEl.scrollIntoView({ behavior: 'smooth' });
          }
        }, 100);
      });
    });
  }
}

/* --------------------------------------------------------------------------
   7. Smooth Anchor Nav Links
   -------------------------------------------------------------------------- */
function initSmoothNavLinks() {
  document.querySelectorAll('a[href^="#"]').forEach(link => {
    link.addEventListener('click', (e) => {
      const href = link.getAttribute('href');
      if (href && href.length > 1) {
        e.preventDefault();
        const targetId = href.substring(1);
        const targetEl = document.getElementById(targetId);
        if (targetEl) {
          targetEl.scrollIntoView({ behavior: 'smooth' });
          history.pushState(null, '', href);
          updateActiveSidebarLink(href);
          document.querySelectorAll('.toc-link').forEach(tocLink => {
            tocLink.classList.toggle('active', tocLink.getAttribute('href') === href);
          });
        }
      }
    });
  });
}

function updateActiveSidebarLink(activeHref) {
  const currentNav = (document.getElementById('tab-user-guide')?.classList.contains('active'))
    ? document.getElementById('nav-user-guide')
    : document.getElementById('nav-dev-guide');

  if (!currentNav) return;

  currentNav.querySelectorAll('.sidebar-link').forEach(link => {
    if (activeHref) {
      link.classList.toggle('active', link.getAttribute('href') === activeHref);
    }
  });
}

/* --------------------------------------------------------------------------
   8. Table of Contents & ScrollSpy
   -------------------------------------------------------------------------- */
function initScrollSpy() {
  updateToc();
  window.addEventListener('scroll', () => {
    const isUserGuide = document.getElementById('tab-user-guide')?.classList.contains('active');
    const parentSelector = isUserGuide ? '.audience-user' : '.audience-dev';
    const activeSections = Array.from(document.querySelectorAll(`${parentSelector} .doc-section`))
      .filter(sec => sec.offsetParent !== null);
    
    if (activeSections.length === 0) return;

    let activeId = '';
    const scrollPos = window.scrollY + 140;
    const isAtBottom = (window.innerHeight + window.scrollY) >= (document.documentElement.scrollHeight - 70);

    if (isAtBottom) {
      // Prioritize the last section when reaching the bottom of the page
      activeId = activeSections[activeSections.length - 1].id;
    } else {
      activeSections.forEach(sec => {
        if (sec.offsetTop <= scrollPos) {
          activeId = sec.id;
        }
      });
      if (!activeId) {
        activeId = activeSections[0].id;
      }
    }

    if (activeId) {
      document.querySelectorAll('.toc-link').forEach(link => {
        link.classList.toggle('active', link.getAttribute('href') === `#${activeId}`);
      });
      updateActiveSidebarLink(`#${activeId}`);
    }
  });
}

function updateToc() {
  const tocList = document.getElementById('toc-list');
  if (!tocList) return;

  const isUserGuide = document.getElementById('tab-user-guide')?.classList.contains('active');
  const parentSelector = isUserGuide ? '.audience-user' : '.audience-dev';

  const activeSections = Array.from(document.querySelectorAll(`${parentSelector} .doc-section`))
    .filter(sec => sec.style.display !== 'none');

  tocList.innerHTML = activeSections.map(sec => {
    const h2 = sec.querySelector('h2');
    const title = h2 ? h2.textContent.replace(/^[^\w\s]+/, '').trim() : sec.id;
    return `<li><a href="#${sec.id}" class="toc-link">${title}</a></li>`;
  }).join('');

  // Rebind TOC click behavior
  tocList.querySelectorAll('.toc-link').forEach(link => {
    link.addEventListener('click', (e) => {
      e.preventDefault();
      const targetId = link.getAttribute('href')?.substring(1);
      const targetEl = document.getElementById(targetId);
      if (targetEl) {
        targetEl.scrollIntoView({ behavior: 'smooth' });
        history.pushState(null, '', `#${targetId}`);
        tocList.querySelectorAll('.toc-link').forEach(l => l.classList.remove('active'));
        link.classList.add('active');
        updateActiveSidebarLink(`#${targetId}`);
      }
    });
  });
}

/* --------------------------------------------------------------------------
   9. Mobile Drawer
   -------------------------------------------------------------------------- */
function initMobileDrawer() {
  const menuBtn = document.getElementById('menu-toggle');
  const sidebar = document.getElementById('docs-sidebar');
  if (!menuBtn || !sidebar) return;

  menuBtn.addEventListener('click', () => {
    sidebar.classList.toggle('open');
  });

  document.querySelectorAll('.sidebar-link').forEach(link => {
    link.addEventListener('click', () => {
      if (window.innerWidth <= 840) {
        sidebar.classList.remove('open');
      }
    });
  });
}

/* --------------------------------------------------------------------------
   10. Page-wide Scroll Reveal Animations
   -------------------------------------------------------------------------- */
function initScrollReveal() {
  const elementsToReveal = document.querySelectorAll(
    '.doc-section, .feature-card, .architecture-diagram-card, .flow-node, .playground-box, .callout, .data-table-wrapper'
  );

  elementsToReveal.forEach(el => {
    el.classList.add('reveal-on-scroll');
  });

  const observer = new IntersectionObserver((entries, obs) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('revealed');
        obs.unobserve(entry.target);
      }
    });
  }, {
    threshold: 0.05,
    rootMargin: '0px 0px -30px 0px'
  });

  elementsToReveal.forEach(el => observer.observe(el));
}

