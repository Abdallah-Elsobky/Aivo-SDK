package com.aivo.sdk.core.model

import kotlinx.serialization.Serializable

/**
 * Strongly-typed role defining an agent's identity, archetype, and baseline responsibilities.
 *
 * Developers can use prebuilt standard roles (e.g. [AgentRole.ASSISTANT], [AgentRole.RESEARCHER],
 * [AgentRole.SUPERVISOR]) or declare custom roles using [AgentRole.custom].
 */
@Serializable
public data class AgentRole(
    public val id: String,
    public val title: String,
    public val defaultResponsibilities: List<String> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "AgentRole id must not be blank" }
        require(title.isNotBlank()) { "AgentRole title must not be blank" }
    }

    override fun toString(): String = title

    public companion object {
        public val ASSISTANT: AgentRole = AgentRole(
            id = "assistant",
            title = "Helpful AI Assistant",
            defaultResponsibilities = listOf("Assist user with clear, accurate, and helpful answers.")
        )

        public val RESEARCHER: AgentRole = AgentRole(
            id = "researcher",
            title = "Research Analyst",
            defaultResponsibilities = listOf(
                "Search and verify information from authorized tools and sources",
                "Synthesize findings into concise, factual notes",
                "Cite sources and highlight conflicting evidence explicitly"
            )
        )

        public val WRITER: AgentRole = AgentRole(
            id = "writer",
            title = "Technical Writer",
            defaultResponsibilities = listOf(
                "Draft coherent, well-structured content based on verified research",
                "Follow style guidelines and tone instructions precisely",
                "Ensure clarity, flow, and absence of filler"
            )
        )

        public val REVIEWER: AgentRole = AgentRole(
            id = "reviewer",
            title = "Quality & Fact Reviewer",
            defaultResponsibilities = listOf(
                "Verify drafts against source evidence and requirements",
                "Identify hallucinations, logical errors, or formatting defects",
                "Provide actionable, concrete revisions or approval status"
            )
        )

        public val SUPERVISOR: AgentRole = AgentRole(
            id = "supervisor",
            title = "Team Supervisor & Lead",
            defaultResponsibilities = listOf(
                "Deconstruct user objectives into specialized sub-tasks",
                "Delegate tasks to team member agents based on their capabilities",
                "Synthesize member results into a unified, high-quality final response"
            )
        )

        public val ORCHESTRATOR: AgentRole = AgentRole(
            id = "orchestrator",
            title = "Workflow Orchestrator",
            defaultResponsibilities = listOf(
                "Coordinate sequential and parallel multi-agent execution steps",
                "Manage state transitions and error recovery across the workflow"
            )
        )

        public val CODER: AgentRole = AgentRole(
            id = "coder",
            title = "Software Engineer",
            defaultResponsibilities = listOf(
                "Design, write, and debug clean, modular code",
                "Follow language idioms, architectural patterns, and security best practices",
                "Provide working implementations and unit tests"
            )
        )

        public val SUPPORT: AgentRole = AgentRole(
            id = "support",
            title = "Customer Support Specialist",
            defaultResponsibilities = listOf(
                "Resolve customer inquiries with empathy, patience, and accuracy",
                "Query order, billing, and account status safely via tools",
                "Escalate complex or high-risk issues when appropriate"
            )
        )

        public val ANALYST: AgentRole = AgentRole(
            id = "analyst",
            title = "Data & Business Analyst",
            defaultResponsibilities = listOf(
                "Analyze structured datasets, metrics, and business reports",
                "Identify patterns, risks, and strategic opportunities"
            )
        )

        public val ROUTER: AgentRole = AgentRole(
            id = "router",
            title = "Intent Classifier & Router",
            defaultResponsibilities = listOf(
                "Analyze user requests and classify user intent",
                "Route request to the most qualified specialist agent"
            )
        )

        public val TRANSLATOR: AgentRole = AgentRole(
            id = "translator",
            title = "Linguistic Translator",
            defaultResponsibilities = listOf(
                "Translate content accurately while preserving tone, cultural nuances, and technical terms"
            )
        )

        public val TRIAGE: AgentRole = AgentRole(
            id = "triage",
            title = "Issue Triage Specialist",
            defaultResponsibilities = listOf(
                "Assess severity and priority of incoming tickets or incidents",
                "Assign tickets to appropriate response units"
            )
        )

        /**
         * Factory function for declaring custom domain-specific roles without hardcoded strings in application logic.
         */
        public fun custom(
            id: String,
            title: String,
            defaultResponsibilities: List<String> = emptyList(),
        ): AgentRole = AgentRole(id, title, defaultResponsibilities)
    }
}
