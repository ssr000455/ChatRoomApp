package com.chatroom.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chatroom.app.R
import com.chatroom.app.data.model.ApiAccount

enum class WizardStep { SELECT_API, SYSTEM_PROMPT, CONNECT_REPO }

@Composable
fun CodingAssistantWizardScreen(
    apiAccounts: List<ApiAccount>,
    currentCodingAssistantCount: Int,
    onCreate: (apiAccountId: String, systemPrompt: String, repoUrl: String, repoOwner: String, repoName: String, repoToken: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableStateOf(WizardStep.SELECT_API) }

    // Wizard state
    var selectedApiAccountId by remember { mutableStateOf<String?>(null) }
    var systemPrompt by remember { mutableStateOf("You are an expert coding assistant. Help the user understand, write, and debug code. You have access to their git repository and can read files, make changes, and commit code.") }
    var repoUrl by remember { mutableStateOf("") }
    var repoConnected by remember { mutableStateOf(false) }
    var repoOwner by remember { mutableStateOf("") }
    var repoName by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf<String?>(null) }
    var repoToken by remember { mutableStateOf("") }

    val canProceedStep1 = selectedApiAccountId != null
    val hasValidRepoUrl = repoConnected && repoUrl.isNotBlank() &&
        repoUrl.count { it == '/' } >= 3 // e.g. https://github.com/owner/repo
    val canProceedStep3 = hasValidRepoUrl

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 48.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.close),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.coding_assistant_setup_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Step indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StepDot(
                label = "API",
                isActive = currentStep == WizardStep.SELECT_API,
                isCompleted = currentStep.ordinal > WizardStep.SELECT_API.ordinal
            )
            StepDot(
                label = "Prompt",
                isActive = currentStep == WizardStep.SYSTEM_PROMPT,
                isCompleted = currentStep.ordinal > WizardStep.SYSTEM_PROMPT.ordinal
            )
            StepDot(
                label = "Repo",
                isActive = currentStep == WizardStep.CONNECT_REPO,
                isCompleted = currentStep.ordinal > WizardStep.CONNECT_REPO.ordinal
            )
        }

        // Content area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (currentStep) {
                WizardStep.SELECT_API -> SelectApiStep(
                    accounts = apiAccounts,
                    selectedId = selectedApiAccountId,
                    onSelect = { selectedApiAccountId = it }
                )
                WizardStep.SYSTEM_PROMPT -> SystemPromptStep(
                    prompt = systemPrompt,
                    onPromptChange = { systemPrompt = it }
                )
                WizardStep.CONNECT_REPO -> ConnectRepoStep(
                    repoUrl = repoUrl,
                    repoToken = repoToken,
                    repoConnected = repoConnected,
                    selectedProvider = selectedProvider,
                    onSelectProvider = { provider ->
                        selectedProvider = provider
                        val baseUrl = getProviderBaseUrl(provider)
                        if (!repoConnected) {
                            repoUrl = baseUrl
                        }
                    },
                    onRepoUrlChange = { repoUrl = it },
                    onRepoTokenChange = { repoToken = it },
                    onConnect = {
                        if (repoUrl.isNotBlank() && repoToken.isNotBlank()) {
                            val uri = Uri.parse(repoUrl)
                            val segments = uri.pathSegments
                            if (segments.size >= 2) {
                                repoOwner = segments[0]
                                repoName = segments[1].removeSuffix(".git")
                            }
                            repoConnected = true
                        }
                    },
                    onDisconnect = {
                        repoConnected = false
                        repoOwner = ""
                        repoName = ""
                        repoToken = ""
                        repoUrl = selectedProvider?.let { getProviderBaseUrl(it) } ?: ""
                    }
                )
            }
        }

        // Navigation buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentStep.ordinal > 0) {
                OutlinedButton(
                    onClick = {
                        currentStep = WizardStep.values()[currentStep.ordinal - 1]
                    }
                ) {
                    Text(stringResource(R.string.wizard_back))
                }
            } else {
                Spacer(modifier = Modifier.size(1.dp))
            }

            if (currentStep == WizardStep.CONNECT_REPO) {
                Button(
                    onClick = {
                        val accountId = selectedApiAccountId
                        if (accountId != null) {
                            // Parse repo URL to get owner/name
                            val uri = Uri.parse(repoUrl)
                            val segments = uri.pathSegments
                            val parsedOwner = if (segments.size >= 2) segments[0] else repoOwner
                            val parsedName = if (segments.size >= 2) segments[1].removeSuffix(".git") else repoName
                            onCreate(
                                accountId,
                                systemPrompt,
                                repoUrl,
                                parsedOwner,
                                parsedName,
                                repoToken
                            )
                        }
                    },
                    enabled = canProceedStep3 && currentCodingAssistantCount < 3
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.wizard_create))
                }
            } else if (currentStep == WizardStep.SYSTEM_PROMPT) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            currentStep = WizardStep.values()[currentStep.ordinal + 1]
                        }
                    ) {
                        Text(stringResource(R.string.wizard_skip))
                    }
                    Button(
                        onClick = {
                            currentStep = WizardStep.values()[currentStep.ordinal + 1]
                        }
                    ) {
                        Text(stringResource(R.string.wizard_next))
                    }
                }
            } else {
                Button(
                    onClick = {
                        currentStep = WizardStep.values()[currentStep.ordinal + 1]
                    },
                    enabled = canProceedStep1
                ) {
                    Text(stringResource(R.string.wizard_next))
                }
            }
        }
    }
}

@Composable
private fun StepDot(
    label: String,
    isActive: Boolean,
    isCompleted: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    when {
                        isCompleted -> MaterialTheme.colorScheme.primary
                        isActive -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = label.first().toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SelectApiStep(
    accounts: List<ApiAccount>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = stringResource(R.string.wizard_step_api),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.wizard_select_api),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (accounts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.wizard_no_api),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn {
                items(accounts) { account ->
                    val isSelected = account.id == selectedId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelect(account.id) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isSelected)
                            androidx.compose.foundation.BorderStroke(
                                1.dp, MaterialTheme.colorScheme.primary
                            ) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelect(account.id) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = account.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = account.model,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (account.isActive) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemPromptStep(
    prompt: String,
    onPromptChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.wizard_step_prompt),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.wizard_customize_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(12.dp),
            placeholder = {
                Text(
                    stringResource(R.string.wizard_prompt_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ConnectRepoStep(
    repoUrl: String,
    repoToken: String,
    repoConnected: Boolean,
    selectedProvider: String?,
    onSelectProvider: (String) -> Unit,
    onRepoUrlChange: (String) -> Unit,
    onRepoTokenChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.wizard_step_repo),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "选择代码托管平台，填入个人访问令牌（Personal Access Token）和仓库地址。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Provider selection cards
        val providers = listOf(
            Triple("GitHub", "github.com", 0xFF24292F),
            Triple("Gitee", "gitee.com", 0xFFC71D23)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            providers.forEach { (name, host, _) ->
                val isSelected = selectedProvider == name
                val isDisabled = repoConnected
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = !isDisabled) { onSelectProvider(name) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    ),
                    border = if (isSelected)
                        androidx.compose.foundation.BorderStroke(
                            1.dp, MaterialTheme.colorScheme.primary
                        ) else null
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isSelected && repoConnected) "✓" else
                                   if (isSelected) "●" else "",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (repoConnected) {
            // Connected state
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "已连接：${selectedProvider ?: "Git Provider"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = repoUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    OutlinedButton(
                        onClick = onDisconnect,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.wizard_disconnect))
                    }
                }
            }
        } else {
            // Not connected - show PAT input + repo URL
            Text(
                text = stringResource(R.string.wizard_pat_label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (selectedProvider) {
                    "GitHub" -> "在 GitHub Settings → Developer settings → Personal access tokens 生成"
                    "Gitee" -> "在 Gitee 设置 → 安全设置 → 私人令牌 生成"
                    else -> "请先生成个人访问令牌"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = repoToken,
                onValueChange = onRepoTokenChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("ghp_xxxxxxxxxxxxxxxxxxxx", style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "仓库地址",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = repoUrl,
                onValueChange = onRepoUrlChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                placeholder = {
                    Text(
                        when (selectedProvider) {
                            "GitHub" -> "https://github.com/owner/repo"
                            "Gitee" -> "https://gitee.com/owner/repo"
                            else -> "https://github.com/owner/repo"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onConnect,
                enabled = repoUrl.isNotBlank() && repoToken.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.wizard_connect_repo_btn))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.wizard_token_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

// Provider helper functions
private fun getProviderBaseUrl(provider: String): String {
    return when (provider) {
        "GitHub" -> "https://github.com/"
        "Gitee" -> "https://gitee.com/"
        else -> "https://"
    }
}

private fun getProviderAuthUrl(provider: String?, repoUrl: String): String {
    return when (provider) {
        "GitHub" -> "https://github.com/login"
        "Gitee" -> "https://gitee.com/login"
        else -> repoUrl
    }
}
