<template>
    <div>
        <PageHead title="RAG 调试台" />

        <!-- 索引状态 + 重建 -->
        <el-card class="block-card" shadow="never">
            <template #header>
                <div class="card-header">
                    <span>索引状态</span>
                    <el-button type="primary" :loading="rebuilding" @click="handleRebuild" :disabled="!!indexStatus && indexStatus.indexOf('未配置') !== -1">
                        重建索引
                    </el-button>
                </div>
            </template>
            <el-alert
                v-if="statusMsg && statusMsg.type === 'error'"
                :title="statusMsg.text" type="error" show-icon :closable="false" />
            <div v-else class="status-line">
                <el-icon class="status-icon" :class="indexReady ? 'is-ready' : 'is-warn'">
                    <SuccessFilled v-if="indexReady" />
                    <WarningFilled v-else />
                </el-icon>
                <span>{{ indexStatus || '加载中…' }}</span>
            </div>
            <p class="tip">说明：重建会扫描所有「已发布」文章 → 分块 → 向量化 → 写入 PgVector。文章较多时耗时较长，期间请勿重复点击。</p>
        </el-card>

        <!-- 检索调试 -->
        <el-card class="block-card" shadow="never">
            <template #header>
                <div class="card-header">
                    <span>检索调试</span>
                </div>
            </template>
            <div class="search-row">
                <el-input
                    v-model="query"
                    placeholder="输入用户可能问的问题，如：最近总是焦虑失眠怎么办"
                    clearable
                    @keyup.enter="handleSearch"
                />
                <el-button type="primary" :loading="searching" @click="handleSearch">检索 Top-K</el-button>
            </div>

            <el-empty v-if="!searched" description="输入问题并点击「检索 Top-K」查看命中的知识片段" />
            <el-empty v-else-if="searchResults.length === 0" description="未检索到相关片段（相似度低于阈值或索引为空）" />

            <div v-else class="result-list">
                <div v-for="(r, i) in searchResults" :key="i" class="result-item">
                    <div class="result-head">
                        <el-tag size="small" :type="scoreType(r.score)">#{{ i + 1 }} 相似度 {{ r.score.toFixed(4) }}</el-tag>
                        <span class="result-title">{{ r.title }}</span>
                    </div>
                    <div class="result-content">{{ r.content }}</div>
                </div>
            </div>
        </el-card>
    </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/PageHead.vue'
import { getRagStatus, rebuildRagIndex, ragSearch } from '@/api/admin'

const indexStatus = ref('')
const statusMsg = ref(null)
const rebuilding = ref(false)

const indexReady = computed(() => indexStatus.value.startsWith('索引已加载'))

const loadStatus = async () => {
    try {
        const data = await getRagStatus()
        indexStatus.value = data?.status || ''
        statusMsg.value = null
    } catch (e) {
        statusMsg.value = { type: 'error', text: '获取索引状态失败：' + (e?.message || e) }
    }
}

const handleRebuild = () => {
    ElMessageBox.confirm(
        '重建会重新向量化全部已发布文章并覆盖现有索引，耗时较长。确定继续？',
        '重建索引确认',
        { confirmButtonText: '确定重建', cancelButtonText: '取消', type: 'warning' }
    ).then(async () => {
        rebuilding.value = true
        try {
            const data = await rebuildRagIndex()
            ElMessage.success(`索引重建完成：${data?.chunkCount ?? 0} 个分块`)
            await loadStatus()
        } catch (e) {
            ElMessage.error('重建失败：' + (e?.message || e))
        } finally {
            rebuilding.value = false
        }
    }).catch(() => {})
}

// 检索
const query = ref('')
const searching = ref(false)
const searched = ref(false)
const searchResults = ref([])

const handleSearch = async () => {
    if (!query.value.trim()) {
        ElMessage.warning('请输入检索问题')
        return
    }
    searching.value = true
    try {
        const data = await ragSearch(query.value.trim())
        searchResults.value = Array.isArray(data) ? data : []
        searched.value = true
    } catch (e) {
        ElMessage.error('检索失败：' + (e?.message || e))
    } finally {
        searching.value = false
    }
}

const scoreType = (score) => {
    if (score >= 0.7) return 'success'
    if (score >= 0.5) return 'warning'
    return 'danger'
}

onMounted(loadStatus)
</script>

<style lang="scss" scoped>
.block-card {
    margin-bottom: 20px;
}
.card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
}
.status-line {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 15px;
    .status-icon {
        font-size: 20px;
        &.is-ready { color: #67c23a; }
        &.is-warn { color: #e6a23c; }
    }
}
.tip {
    margin: 12px 0 0;
    font-size: 12px;
    color: #909399;
    line-height: 1.6;
}
.search-row {
    display: flex;
    gap: 10px;
}
.result-list {
    margin-top: 20px;
    .result-item {
        border: 1px solid #ebeef5;
        border-radius: 6px;
        padding: 12px 14px;
        margin-bottom: 12px;
        .result-head {
            display: flex;
            align-items: center;
            gap: 8px;
            margin-bottom: 8px;
            .result-title {
                font-weight: 600;
                color: #303133;
            }
        }
        .result-content {
            font-size: 13px;
            color: #606266;
            line-height: 1.7;
            max-height: 120px;
            overflow: auto;
        }
    }
}
</style>
