<template>
    <div class="consultation-container">
        <!-- Breadcrumb -->
        <div class="breadcrumb-bar">
            <div class="breadcrumb-inner">
                <el-breadcrumb separator="/">
                    <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
                    <el-breadcrumb-item>AI助手</el-breadcrumb-item>
                </el-breadcrumb>
            </div>
        </div>

        <div class="page-content">
            <!-- Left Sidebar -->
            <div class="sidebar">
                <!-- AI Info Card -->
                <div class="ai-info-card">
                    <div class="ai-avatar">
                        <CuteRobot :expression="currentExpression" :size="64" :talking="isAiTyping" />
                    </div>
                    <h3 class="ai-name">AI心理助手</h3>
                    <p class="ai-desc">{{ currentAiStatus }}</p>
                </div>

                <!-- Session List -->
                <div class="session-card">
                    <div class="card-header">
                        <h4>💬 历史会话</h4>
                        <el-button text size="small" @click="createNewFrontendSession" class="new-btn">
                            <el-icon><Plus /></el-icon> 新建
                        </el-button>
                    </div>
                    <div class="session-list">
                        <div 
                            v-for="session in sessionList" 
                            :key="session.id" 
                            class="session-item"
                            :class="{ active: currentSession?.sessionId == session.id }"
                            @click="handleSessionClick(session)"
                        >
                            <div class="session-icon">💭</div>
                            <div class="session-info">
                                <div class="session-title">{{ session.sessionTitle }}</div>
                                <div class="session-time">{{ formatTime(session.startedAt) }}</div>
                            </div>
                            <el-button 
                                text 
                                size="small" 
                                class="delete-btn"
                                @click.stop="handleDeleteSession(session.id)"
                            >
                                <el-icon><Delete /></el-icon>
                            </el-button>
                        </div>
                        <div v-if="sessionList.length === 0" class="no-sessions">
                            <p>暂无历史会话</p>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Chat Main -->
            <div class="chat-main">
                <!-- Chat Header -->
                <div class="chat-header">
                    <div class="header-left">
                        <div class="chat-avatar">
                            <CuteRobot :expression="currentExpression" :size="36" :talking="isAiTyping" />
                        </div>
                        <div class="chat-info">
                            <h2>AI心理助手</h2>
                            <p>{{ isAiTyping ? '正在思考中...' : '在线 · 随时为您服务' }}</p>
                        </div>
                    </div>
                </div>

                <!-- Chat Messages -->
                <div class="chat-messages" ref="messagesContainer">
                    <!-- Welcome Message -->
                    <div class="message-row ai-row" v-if="messages.length === 0">
                        <div class="message-avatar ai-avatar">
                            <CuteRobot expression="happy" :size="36" :wave="true" />
                        </div>
                        <div class="message-content">
                            <div class="message-bubble ai-bubble">
                                <p>你好呀~ 我是AI心理助手 🤗</p>
                                <p>一个愿意认真成长小烦恼的朋友！不管今天你心里装着开心的事、有点小困扰，还是只是想随便聊聊——耐心和温暖都等你说点什么呢 💚</p>
                            </div>
                            <span class="message-time">{{ currentTime }}</span>
                        </div>
                    </div>

                    <!-- Messages -->
                    <div 
                        v-for="msg in messages" 
                        :key="msg.id" 
                        class="message-row"
                        :class="msg.senderType === 1 ? 'user-row' : 'ai-row'"
                    >
                        <div class="message-avatar" :class="msg.senderType === 1 ? 'user-avatar' : 'ai-avatar'">
                            <CuteRobot v-if="msg.senderType === 2" :expression="msg.expression || 'happy'" :size="32" />
                            <span v-else class="user-emoji">😊</span>
                        </div>
                        <div class="message-content">
                            <div 
                                class="message-bubble"
                                :class="msg.senderType === 1 ? 'user-bubble' : 'ai-bubble'"
                            >
                                <!-- AI Typing Indicator -->
                                <div v-if="msg.senderType === 2 && isAiTyping && !msg.content" class="typing-indicator">
                                    <span></span><span></span><span></span>
                                </div>
                                <!-- Error Message -->
                                <div v-else-if="msg.isError" class="error-message">
                                    <p>{{ msg.content }}</p>
                                </div>
                                <!-- 正在流式的最后一条 AI 消息：纯文本渐进显示（避免每帧全量 Markdown 重解析 + v-html 重建 DOM 导致卡顿/整段跳） -->
                                <div v-else-if="msg.senderType === 2 && isAiTyping && lastMessage && msg.id === lastMessage.id" class="streaming-text">
                                    {{ msg.content }}<span class="caret">▌</span>
                                </div>
                                <!-- AI Message (Markdown) 流已结束/历史消息再渲染 Markdown -->
                                <MarkdownRenderer v-else-if="msg.senderType === 2 && !msg.isError" :content="msg.content" :is-ai-message="true" />
                                <!-- User Message -->
                                <p v-else-if="msg.content">{{ msg.content }}</p>
                            </div>
                            <span class="message-time">{{ msg.createdAt || '' }}</span>
                        </div>
                    </div>
                </div>

                <!-- Chat Input -->
                <div class="chat-input">
                    <div class="input-wrapper">
                        <el-input
                            v-model="userMessage"
                            placeholder="输入您想咨询的问题..."
                            type="textarea"
                            :rows="2"
                            :disabled="isAiTyping"
                            @keydown="handleKeyDown"
                            class="message-input"
                            resize="none"
                        />
                        <div class="input-tools">
                            <span class="char-count">{{ userMessage.length }}/500</span>
                            <el-button 
                                :disabled="!userMessage.trim() || userMessage.length > 500" 
                                type="primary" 
                                class="send-btn"
                                @click="sendMessage"
                            >
                                发送
                            </el-button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, nextTick, onMounted, computed, watch } from 'vue'
import { startSession, getSessionList, deleteSession, getSessionDetail, getSessionEmotion } from '@/api/frontend'
import { ElMessage } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import CuteRobot from '@/components/CuteRobot.vue'
import { analyzeEmotion, analyzeAiResponseEmotion, getDefaultExpression, getThinkingExpression } from '@/utils/emotionAnalyzer'

// AI 当前表情状态
const currentExpression = ref(getDefaultExpression())
const currentAiStatus = ref('我是您的AI心理咨询助手，有什么可以帮您的吗？')

// 更新 AI 状态文字
const updateAiStatus = (text) => {
  currentAiStatus.value = text
}

// 根据用户输入更新 AI 表情
const updateExpressionFromUserInput = (text) => {
  const expr = analyzeEmotion(text)
  currentExpression.value = expr
}

// 根据 AI 回复更新表情
const updateExpressionFromAiResponse = (text) => {
  const expr = analyzeAiResponseEmotion(text)
  currentExpression.value = expr
}

const createNewFrontendSession = () => {
    const newSession = {
        sessionId: `temp_${Date.now()}`,
        status: 'TEMP',
        sessionTitle: '新对话'
    }
    currentSession.value = newSession
    messages.value = []
    currentExpression.value = getDefaultExpression()
    updateAiStatus('你好呀~ 有什么可以帮你的吗？')
}

const currentSession = ref(null)
const sessionList = ref([])
const messages = ref([])
const userMessage = ref('')
const isAiTyping = ref(false)
const messagesContainer = ref(null)

// 最后一条消息：仅用于流式期间标记「正在输出」的那条 AI 消息走纯文本渐进渲染，其余走 Markdown
const lastMessage = computed(() => messages.value.length ? messages.value[messages.value.length - 1] : null)

const currentTime = ref('')

const updateCurrentTime = () => {
    const now = new Date()
    currentTime.value = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
}

const formatTime = (time) => {
    if (!time) return ''
    const date = new Date(time)
    return `${date.getMonth() + 1}月${date.getDate()}日 ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

const scrollToBottom = () => {
    nextTick(() => {
        if (messagesContainer.value) {
            messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
        }
    })
}

const loadSessionEmotion = (sessionId) => {
    const id = sessionId.toString().replace(/^session_/, '')
    getSessionEmotion(id).then(res => {
        // emotion data available
    })
}

const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault()
        sendMessage()
    }
}

const sendMessage = () => {
    if (!userMessage.value.trim()) return
    if (isAiTyping.value) {
        ElMessage.error('AI助手正在输入中，请稍后')
        return
    }

    const message = userMessage.value.trim()
    userMessage.value = ''
    updateCurrentTime()

    // 根据用户输入更新 AI 表情
    updateExpressionFromUserInput(message)

    if (currentSession.value?.status === 'TEMP') {
        startNewSession(message)
    } else {
        messages.value.push({
            id: Date.now(),
            senderType: 1,
            content: message,
            createdAt: currentTime.value
        })
        startAIResponse(currentSession.value.sessionId, message)
    }
    scrollToBottom()
}

const startNewSession = (message) => {
    const sessionParams = {
        initialMessage: message,
        sessionTitle: currentSession.value?.sessionTitle === '新对话' 
            ? `AI助手 - ${new Date().toLocaleString()}` 
            : currentSession.value?.sessionTitle
    }

    startSession(sessionParams).then(res => {
        const sessionData = {
            sessionId: res.sessionId,
            status: res.status,
            sessionTitle: sessionParams.sessionTitle
        }

        if (currentSession.value?.status === 'TEMP') {
            Object.assign(currentSession.value, sessionData)
        } else {
            currentSession.value = sessionData
        }

        getSessionPage()

        updateCurrentTime()
        messages.value.push({
            id: Date.now(),
            senderType: 1,
            content: message,
            createdAt: currentTime.value
        })

        startAIResponse(currentSession.value.sessionId, message)
        scrollToBottom()
    })
}

const startAIResponse = async (sessionId, userMessageText) => {
    if (isAiTyping.value) return

    isAiTyping.value = true

    // 根据用户输入设置表情，并保持不变
    updateExpressionFromUserInput(userMessageText)
    updateAiStatus('正在思考中...')
    // 不覆盖表情，保持用户情绪对应的表情

    const aiMessage = {
        id: `ai_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        senderType: 2,
        content: '',
        createdAt: '',
        isError: false,
        expression: currentExpression.value  // 跟随用户情绪
    }
    messages.value.push(aiMessage)
    scrollToBottom()

    try {
        const response = await fetch('/api/psychological-chat/stream', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Bearer ' + localStorage.getItem('token'),
                'Accept': 'text/event-stream'
            },
            body: JSON.stringify({ sessionId, userMessage: userMessageText })
        })

        if (!response.ok || !response.body) {
            if (response.status === 401) {
                ElMessage.error('登录已过期，请重新登录')
                localStorage.removeItem('token')
                localStorage.removeItem('userInfo')
                window.location.href = '/auth/login'
                return
            }
            handleError('服务器响应异常')
            return
        }

        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''

        while (true) {
            const { done, value } = await reader.read()
            if (done) break

            buffer += decoder.decode(value, { stream: true })
            const lines = buffer.split('\n')
            buffer = lines.pop() || ''

            for (const line of lines) {
                const trimmed = line.trim()
                if (!trimmed || !trimmed.startsWith('data:')) continue

                const data = trimmed.substring(5).trim()
                if (!data || data === '{}') continue

                try {
                    const payload = JSON.parse(data)
                    if (String(payload.code) === '200' && payload.data && payload.data.content) {
                        aiMessage.content += payload.data.content
                    } else if (String(payload.code) !== '200') {
                        handleError(payload.msg || 'AI回复失败')
                    }
                } catch (e) {
                    // non-JSON data, skip
                }
            }
            scrollToBottom()
        }

        updateCurrentTime()
        aiMessage.createdAt = currentTime.value

        // AI 回复完成，保持用户情绪对应的表情不变
        aiMessage.expression = currentExpression.value
        updateAiStatus('有什么其他问题吗？随时告诉我~')

        loadSessionEmotion(sessionId)
    } catch (err) {
        handleError(err.message || 'AI回复失败')
    } finally {
        isAiTyping.value = false
    }
}

const handleError = (error) => {
    const aiMessage = messages.value[messages.value.length - 1]
    if (aiMessage) {
        aiMessage.content = 'AI回复失败，请重试'
        aiMessage.isError = true
        aiMessage.expression = 'concern'
    }
    isAiTyping.value = false
    currentExpression.value = 'concern'
    updateAiStatus('抱歉，出了点问题，请重试~')
    ElMessage.error(error)
}

const getSessionPage = () => {
    getSessionList({
        pageNum: 1,
        pageSize: 50
    }).then(res => {
        sessionList.value = res.records || []
    })
}

const handleSessionClick = (session) => {
    getSessionDetail(session.id).then(res => {
        messages.value = res || []
        scrollToBottom()
    })
    loadSessionEmotion(session.id)
    currentSession.value = {
        sessionId: session.id,
        status: 'ACTIVE',
        sessionTitle: session.sessionTitle
    }
}

const handleDeleteSession = (sessionId) => {
    const id = sessionId.toString().replace(/^session_/, '')
    deleteSession(id).then(() => {
        ElMessage.success('删除成功')
        getSessionPage()
        if (currentSession.value?.sessionId == sessionId) {
            createNewFrontendSession()
        }
    })
}

const formatMessageContent = (content) => {
    return content.replace(/\n/g, '<br>')
}

onMounted(() => {
    updateCurrentTime()
    getSessionPage()
    createNewFrontendSession()
})
</script>

<style scoped lang="scss">
.consultation-container {
    min-height: calc(100vh - 200px);
    background: var(--bg-body);

    .breadcrumb-bar {
        background: #fff;
        padding: 12px 24px;
        border-bottom: 1px solid var(--border-light);

        .breadcrumb-inner {
            max-width: 1200px;
            margin: 0 auto;
        }

        :deep(.el-breadcrumb__inner) {
            color: var(--text-secondary);
        }

        :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
            color: var(--text-primary);
            font-weight: 500;
        }
    }

    .page-content {
        max-width: 1200px;
        margin: 0 auto;
        padding: 24px;
        display: flex;
        gap: 20px;
        height: calc(100vh - 260px);
        min-height: 600px;
    }

    .sidebar {
        width: 280px;
        display: flex;
        flex-direction: column;
        gap: 16px;

        .ai-info-card {
            background: linear-gradient(135deg, #2ECC71 0%, #27AE60 100%);
            border-radius: var(--radius-lg);
            padding: 24px 20px;
            text-align: center;
            color: #fff;

            .ai-avatar {
                width: 72px;
                height: 72px;
                background: rgba(255, 255, 255, 0.3);
                border-radius: 50%;
                display: flex;
                align-items: center;
                justify-content: center;
                margin: 0 auto 12px;
                padding: 4px;
                box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
                overflow: visible;
            }

            .ai-name {
                font-size: 18px;
                font-weight: 600;
                margin-bottom: 8px;
            }

            .ai-desc {
                font-size: 13px;
                line-height: 1.5;
                opacity: 0.9;
            }
        }

        .session-card {
            background: #fff;
            border-radius: var(--radius-lg);
            padding: 16px;
            flex: 1;
            overflow: hidden;
            display: flex;
            flex-direction: column;
            border: 1px solid var(--border-light);

            .card-header {
                display: flex;
                align-items: center;
                justify-content: space-between;
                margin-bottom: 12px;

                h4 {
                    font-size: 15px;
                    font-weight: 600;
                    color: var(--text-primary);
                }

                .new-btn {
                    color: var(--primary) !important;
                    font-size: 13px;
                }
            }

            .session-list {
                flex: 1;
                overflow-y: auto;

                .session-item {
                    display: flex;
                    align-items: center;
                    gap: 10px;
                    padding: 10px 12px;
                    border-radius: var(--radius-md);
                    cursor: pointer;
                    transition: all 0.2s ease;
                    margin-bottom: 6px;

                    &:hover {
                        background: var(--primary-bg);
                    }

                    &.active {
                        background: var(--primary-bg);
                        border: 1px solid var(--primary);
                    }

                    .session-icon {
                        font-size: 16px;
                        flex-shrink: 0;
                    }

                    .session-info {
                        flex: 1;
                        min-width: 0;

                        .session-title {
                            font-size: 13px;
                            font-weight: 500;
                            color: var(--text-primary);
                            white-space: nowrap;
                            overflow: hidden;
                            text-overflow: ellipsis;
                        }

                        .session-time {
                            font-size: 11px;
                            color: var(--text-muted);
                            margin-top: 2px;
                        }
                    }

                    .delete-btn {
                        opacity: 0;
                        transition: opacity 0.2s ease;
                        color: var(--text-muted) !important;

                        &:hover {
                            color: var(--accent-red) !important;
                        }
                    }

                    &:hover .delete-btn {
                        opacity: 1;
                    }
                }

                .no-sessions {
                    text-align: center;
                    padding: 40px 20px;
                    color: var(--text-muted);
                    font-size: 13px;
                }
            }
        }
    }

    .chat-main {
        flex: 1;
        background: #fff;
        border-radius: var(--radius-lg);
        display: flex;
        flex-direction: column;
        overflow: hidden;
        border: 1px solid var(--border-light);

        .chat-header {
            padding: 16px 24px;
            border-bottom: 1px solid var(--border-light);

            .header-left {
                display: flex;
                align-items: center;
                gap: 12px;

                .chat-avatar {
                    width: 44px;
                    height: 44px;
                    background: linear-gradient(135deg, #2ECC71, #27AE60);
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    color: #fff;
                    padding: 4px;
                    box-shadow: 0 2px 8px rgba(46, 204, 113, 0.3);
                }

                .chat-info {
                    h2 {
                        font-size: 16px;
                        font-weight: 600;
                        color: var(--text-primary);
                        margin-bottom: 2px;
                    }

                    p {
                        font-size: 12px;
                        color: var(--primary);
                    }
                }
            }
        }

        .chat-messages {
            flex: 1;
            overflow-y: auto;
            padding: 24px;
            background: #FAFBFC;
            display: flex;
            flex-direction: column;
            gap: 20px;

            .message-row {
                display: flex;
                gap: 10px;
                max-width: 100%;

                &.user-row {
                    flex-direction: row-reverse;

                    .message-content {
                        align-items: flex-end;
                    }

                    .message-bubble {
                        background: linear-gradient(135deg, #2ECC71, #27AE60);
                        color: #fff;
                        border-radius: 18px 18px 4px 18px;
                    }

                    .message-time {
                        text-align: right;
                    }
                }

                &.ai-row {
                    .message-bubble {
                        background: #fff;
                        color: var(--text-primary);
                        border-radius: 18px 18px 18px 4px;
                        border: 1px solid var(--border-light);
                        box-shadow: var(--shadow-sm);
                    }
                }

                .message-avatar {
                    width: 40px;
                    height: 40px;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 18px;
                    flex-shrink: 0;
                    padding: 2px;

                    .user-emoji {
                        font-size: 20px;
                    }

                    &.user-avatar {
                        background: linear-gradient(135deg, #A8E6CF, #67C9A4);
                    }

                    &.ai-avatar {
                        background: linear-gradient(135deg, #2ECC71, #27AE60);
                        box-shadow: 0 2px 6px rgba(46, 204, 113, 0.3);
                    }
                }

                .message-content {
                    max-width: 70%;
                    display: flex;
                    flex-direction: column;
                    gap: 4px;

                    .message-bubble {
                        padding: 12px 16px;
                        font-size: 14px;
                        line-height: 1.6;
                        word-break: break-word;

                        p {
                            margin: 0;
                        }

                        .typing-indicator {
                            display: flex;
                            gap: 4px;
                            padding: 4px 0;

                            span {
                                width: 8px;
                                height: 8px;
                                background: #ccc;
                                border-radius: 50%;
                                animation: typing 1.4s infinite;

                                &:nth-child(2) {
                                    animation-delay: 0.2s;
                                }

                                &:nth-child(3) {
                                    animation-delay: 0.4s;
                                }
                            }
                        }

                        .error-message {
                            color: var(--accent-red);
                        }
                    }

                    .message-time {
                        font-size: 11px;
                        color: var(--text-muted);
                    }
                }
            }
        }

        .chat-input {
            padding: 16px 24px;
            border-top: 1px solid var(--border-light);
            background: #fff;

            .input-wrapper {
                .message-input {
                    :deep(.el-textarea__inner) {
                        border-radius: var(--radius-md);
                        border: 1px solid var(--border-color);
                        padding: 12px 14px;
                        font-size: 14px;
                        line-height: 1.6;
                        resize: none;

                        &:focus {
                            border-color: var(--primary);
                        }
                    }
                }

                .input-tools {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    margin-top: 8px;

                    .char-count {
                        font-size: 12px;
                        color: var(--text-muted);
                    }

                    .send-btn {
                        border-radius: var(--radius-full);
                        background: var(--primary);
                        border: none;
                        padding: 8px 28px;
                        font-weight: 500;

                        &:hover:not(:disabled) {
                            background: var(--primary-light);
                        }
                    }
                }
            }
        }
    }
}

@keyframes typing {
    0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
    30% { transform: translateY(-6px); opacity: 1; }
}

/* 流式纯文本渲染：跟随 token 逐字显示,零 Markdown 重解析开销 */
.streaming-text {
    white-space: pre-wrap;
    word-break: break-word;
    line-height: 1.75;
    color: #303133;
    font-size: 14px;
}

.streaming-text .caret {
    display: inline-block;
    margin-left: 2px;
    color: #3b82f6;
    animation: caret-blink 1s steps(1) infinite;
}

@keyframes caret-blink {
    50% { opacity: 0; }
}
</style>
