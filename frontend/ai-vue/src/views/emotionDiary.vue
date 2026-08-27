<template>
    <div class="emotionDiary-container">
        <div class="header-section">
            <div class="header-content">
                <el-image :src="iconUrl" style="width: 60px;height: 60px"></el-image>
                <h1>📝 情绪日志</h1>
            </div>
        </div>
        <div class="content">
            <!-- 情绪评分 -->
            <div class="diary-card">
                <div class="title">📝 今日情绪评分</div>
                <div class="section">
                    <p>您今天的整体情绪状态如何？(1-10分)</p>
                    <div class="rate">
                        <el-rate 
                            v-model="diaryForm.moodScore"
                            :texts="emotionStatus"
                            show-texts
                            :max="10"
                            size="large"
                        />
                    </div>
                </div>
            </div>
            <!-- 主要情绪 -->
            <div class="diary-card">
                <div class="title">💭 主要情绪</div>
                <div class="emotion-grid">
                    <div v-for="emotion in emotionOptions" :key="emotion.name" class="emotion-card" :class="{'selected': emotion.name === diaryForm.dominantEmotion}" @click="selectEmotion(emotion.name)">
                        <el-image :src="emotion.url" style="width: 50px;height: 50px"></el-image>
                        <div class="emotion-name">{{emotion.name}}</div>
                    </div>
                </div>
            </div>
            <!-- 详细记录 -->
            <div class="diary-card">
                <div class="title">✨ 详细记录</div>
                <div class="detail-form">
                    <div class="form-group">
                        <div class="form-label">情绪触发因素</div>
                        <el-input v-model="diaryForm.emotionTriggers" placeholder="今天什么事情影响了您的情绪？" type="textarea" :rows="3" maxLength="1000" show-word-limit></el-input>
                    </div>
                     <div class="form-group">
                        <div class="form-label">今日感想</div>
                        <el-input v-model="diaryForm.diaryContent" placeholder="写下您今天的想法、感受或发生的有趣事情..." type="textarea" :rows="5" maxLength="2000" show-word-limit></el-input>
                    </div>
                    <!-- 生活指标 -->
                    <div class="life-indicators">
                        <div class="indicator-group">
                            <div class="form-label">睡眠质量</div>
                           <el-select v-model="diaryForm.sleepQuality" placeholder="请选择">
                                <el-option label="很差" :value="1"></el-option>
                                <el-option label="较差" :value="2"></el-option>
                                <el-option label="一般" :value="3"></el-option>
                                <el-option label="良好" :value="4"></el-option>
                                <el-option label="优秀" :value="5"></el-option>
                            </el-select>
                        </div>
                        <div class="indicator-group">
                            <div class="form-label">压力水平</div>
                            <el-select v-model="diaryForm.stressLevel" placeholder="请选择">
                                <el-option label="很低" :value="1"></el-option>
                                <el-option label="较低" :value="2"></el-option>
                                <el-option label="中等" :value="3"></el-option>
                                <el-option label="较高" :value="4"></el-option>
                                <el-option label="很高" :value="5"></el-option>
                            </el-select>
                        </div>
                    </div>
                    <div class="action-buttons">
                        <el-button class="reset-btn" @click="resetForm">重置</el-button>
                        <el-button class="submit-btn" type="primary" @click="submit">提交记录</el-button>
                    </div>
                </div>
            </div>
            <!-- 情绪趋势关怀提示（主动关怀） -->
            <div v-if="trendData && trendMeta" class="diary-card care-card" :class="'care-' + trendData.level">
                <div class="care-header">
                    <span class="care-icon">{{ trendMeta.icon }}</span>
                    <div class="care-title-wrap">
                        <div class="care-title">{{ trendMeta.title }}</div>
                        <div class="care-subtitle">为您分析近14天的情绪变化趋势</div>
                    </div>
                </div>
                <div class="care-message-box">
                    <div class="care-message">{{ trendData.careMessage }}</div>
                </div>
                <div v-if="trendData.recentScores && trendData.recentScores.length > 0" class="care-body">
                    <div class="care-chart-section">
                        <div class="chart-label">📈 近期评分走势</div>
                        <div class="care-line-chart">
                            <svg viewBox="0 0 340 120" class="line-chart-svg" preserveAspectRatio="none">
                                <defs>
                                    <linearGradient id="lineArea" x1="0" y1="0" x2="0" y2="1">
                                        <stop offset="0%" stop-color="#2ECC71" stop-opacity="0.3"/>
                                        <stop offset="100%" stop-color="#2ECC71" stop-opacity="0.02"/>
                                    </linearGradient>
                                </defs>
                                <!-- 网格线 -->
                                <line x1="30" y1="15" x2="330" y2="15" stroke="#E8F8F0" stroke-width="1"/>
                                <line x1="30" y1="59" x2="330" y2="59" stroke="#E8F8F0" stroke-width="1"/>
                                <line x1="30" y1="103" x2="330" y2="103" stroke="#E8F8F0" stroke-width="1"/>
                                <!-- Y轴标签 -->
                                <text x="28" y="18" text-anchor="end" font-size="10" fill="#95A5A6">10</text>
                                <text x="28" y="62" text-anchor="end" font-size="10" fill="#95A5A6">5</text>
                                <text x="28" y="106" text-anchor="end" font-size="10" fill="#95A5A6">0</text>
                                <!-- 面积填充 -->
                                <path :d="lineAreaPath" fill="url(#lineArea)" />
                                <!-- 折线 -->
                                <path :d="linePath" fill="none" stroke="#2ECC71" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                                <!-- 数据点 -->
                                <g v-for="(point, idx) in linePoints" :key="'pt'+idx">
                                    <circle :cx="point.x" :cy="point.y" r="3" fill="#fff" stroke="#2ECC71" stroke-width="1.5"/>
                                    <text :x="point.x" :y="point.y - 8" text-anchor="middle" font-size="10" font-weight="700" fill="#239B56">{{ point.score }}</text>
                                </g>
                                <!-- X轴标签 -->
                                <g v-for="(point, idx) in linePoints" :key="'xlbl'+idx">
                                    <text :x="point.x" y="116" text-anchor="middle" font-size="11" fill="#7F8C8D">{{ point.date }}</text>
                                </g>
                            </svg>
                        </div>
                    </div>
                    <div class="care-stats">
                        <div class="stat-card">
                            <div class="stat-value">{{ trendData.latestScore }}</div>
                            <div class="stat-label">最近评分</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-value">{{ trendData.averageScore }}</div>
                            <div class="stat-label">近期平均</div>
                        </div>
                        <div class="stat-card warn-card" v-if="trendData.consecutiveDeclineDays > 0">
                            <div class="stat-value warn">{{ trendData.consecutiveDeclineDays }}</div>
                            <div class="stat-label">连续下降天数</div>
                        </div>
                    </div>
                </div>
                <div v-if="trendData.level === 'warning'" class="care-hotline">
                    <div class="hotline-icon">📞</div>
                    <div class="hotline-text">
                        <div class="hotline-title">24小时心理援助热线</div>
                        <div class="hotline-number">400-161-9995</div>
                    </div>
                </div>
            </div>
            <!-- 历史记录 -->
            <div class="diary-card history-card">
                <div class="title">📖 历史记录</div>
                <div v-if="historyList.length === 0" class="empty-history">
                    <p>暂无历史记录，开始记录今天的情绪吧~</p>
                </div>
                <div v-else class="history-list">
                    <div v-for="(group, date) in groupedHistory" :key="date" class="history-group">
                        <div class="group-header">{{ date }}</div>
                        <div v-for="item in group" :key="item.id" class="history-item" :class="'score-' + getScoreLevel(item.moodScore)" @click="viewDetail(item)">
                            <div class="history-left">
                                <div class="history-emoji">{{ getEmotionEmoji(item.dominantEmotion) }}</div>
                                <div class="history-info">
                                    <div class="history-time">{{ formatTime(item.createdAt) }}</div>
                                    <div class="history-emotion">
                                        <span class="emotion-tag">{{ item.dominantEmotion || '未选择' }}</span>
                                        <span class="mood-score">情绪评分: {{ item.moodScore || '-' }}/10</span>
                                    </div>
                                    <div class="history-preview" v-if="item.diaryContent">{{ item.diaryContent.substring(0, 80) }}{{ item.diaryContent.length > 80 ? '...' : '' }}</div>
                                </div>
                            </div>
                            <div class="history-right">
                                <div class="indicators">
                                    <span v-if="item.sleepQuality" class="indicator">睡眠: {{ getQualityText(item.sleepQuality) }}</span>
                                    <span v-if="item.stressLevel" class="indicator">压力: {{ getStressText(item.stressLevel) }}</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="pagination" v-if="historyList.length > 0">
                    <el-pagination
                        v-model:current-page="pageInfo.current"
                        :page-size="pageInfo.size"
                        :total="pageInfo.total"
                        layout="prev, pager, next"
                        @current-change="loadHistory"
                    />
                </div>
            </div>
            <!-- 详情弹窗 -->
            <el-dialog v-model="detailVisible" title="日记详情" width="600px" class="detail-dialog">
                <div class="detail-content" v-if="currentDetail">
                    <div class="detail-row">
                        <span class="detail-label">日期:</span>
                        <span>{{ currentDetail.diaryDate }}</span>
                    </div>
                    <div class="detail-row">
                        <span class="detail-label">情绪评分:</span>
                        <span>{{ currentDetail.moodScore || '-' }}/10</span>
                    </div>
                    <div class="detail-row">
                        <span class="detail-label">主要情绪:</span>
                        <span>{{ currentDetail.dominantEmotion || '未选择' }}</span>
                    </div>
                    <div class="detail-row">
                        <span class="detail-label">睡眠质量:</span>
                        <span>{{ currentDetail.sleepQuality ? getQualityText(currentDetail.sleepQuality) : '-' }}</span>
                    </div>
                    <div class="detail-row">
                        <span class="detail-label">压力水平:</span>
                        <span>{{ currentDetail.stressLevel ? getStressText(currentDetail.stressLevel) : '-' }}</span>
                    </div>
                    <div class="detail-section" v-if="currentDetail.emotionTriggers">
                        <div class="detail-label">情绪触发因素:</div>
                        <p>{{ currentDetail.emotionTriggers }}</p>
                    </div>
                    <div class="detail-section" v-if="currentDetail.diaryContent">
                        <div class="detail-label">今日感想:</div>
                        <p>{{ currentDetail.diaryContent }}</p>
                    </div>
                </div>
            </el-dialog>
        </div>
    </div>
</template>
<script setup>
    import { dayjs, ElMessage } from 'element-plus'
    import { ref, reactive, onMounted, computed } from 'vue'
    import { addEmotionDiary, getMyEmotionDiaryList, getEmotionTrend } from '@/api/frontend'

    // 情绪评分
    const emotionStatus = ['绝望崩溃', '消沉抑郁', '焦虑烦躁', '低落不悦', '平静淡然', '轻松惬意', '愉悦舒心', '欢欣满足', '兴奋欣喜', '极致幸福']

    // 情绪选项
    const emotionOptions = [
        { name: '开心', url: new URL('@/assets/images/开心.png', import.meta.url).href },
        { name: '平静', url: new URL('@/assets/images/平静.png', import.meta.url).href },
        { name: '焦虑', url: new URL('@/assets/images/焦虑.png', import.meta.url).href },
        { name: '悲伤', url: new URL('@/assets/images/悲伤.png', import.meta.url).href },
        { name: '兴奋', url: new URL('@/assets/images/兴奋.png', import.meta.url).href },
        { name: '疲惫', url: new URL('@/assets/images/疲惫.png', import.meta.url).href },
        { name: '惊讶', url: new URL('@/assets/images/惊讶.png', import.meta.url).href },
        { name: '困惑', url: new URL('@/assets/images/困惑.png', import.meta.url).href },
    ]

    const selectEmotion = (emotion) => {
        diaryForm.dominantEmotion = emotion
    }

    const diaryForm = reactive({    
        diaryDate: dayjs().format('YYYY-MM-DD'),
        moodScore: null,
        dominantEmotion: '',
        emotionTriggers: '',
        diaryContent: '',
        sleepQuality: null,
        stressLevel: null
    })

    const resetForm = () => {
        Object.assign(diaryForm, {
            diaryDate: dayjs().format('YYYY-MM-DD'),
            moodScore: null,
            dominantEmotion: '',
            emotionTriggers: '',
            diaryContent: '',
            sleepQuality: null,
            stressLevel: null
        })
    }

    const submit = () => {
        if (!diaryForm.moodScore) {
            ElMessage.error('请选择情绪评分')
            return
        }
        addEmotionDiary(diaryForm).then(() => {
            ElMessage.success('提交成功')
            resetForm()
            loadHistory()
            loadTrend()
        })
    }

    // 情绪趋势分析 & 关怀提示
    const trendData = ref(null)

    const loadTrend = () => {
        getEmotionTrend().then(res => {
            trendData.value = res
        }).catch(() => {})
    }

    // 趋势卡片图标与标题
    const trendMeta = computed(() => {
        const t = trendData.value
        if (!t) return null
        const map = {
            declining: { icon: '🫂', title: '主动关怀 · 情绪持续走低' },
            low: { icon: '💙', title: '主动关怀 · 关注您的情绪' },
            slight_decline: { icon: '🌱', title: '情绪小提醒' },
            improving: { icon: '✨', title: '状态向好' },
            stable: { icon: '🍃', title: '情绪平稳' },
            insufficient: { icon: '📝', title: '坚持记录' }
        }
        return map[t.trendType] || { icon: '🍃', title: '情绪趋势' }
    })

    // 趋势折线图坐标计算
    const CHART_W = 340
    const CHART_H = 120
    const PAD_LEFT = 30
    const PAD_RIGHT = 20
    const PAD_TOP = 15
    const PAD_BOTTOM = 18
    const PLOT_W = CHART_W - PAD_LEFT - PAD_RIGHT
    const PLOT_H = CHART_H - PAD_TOP - PAD_BOTTOM

    const linePoints = computed(() => {
        const t = trendData.value
        if (!t || !t.recentScores || t.recentScores.length === 0) return []
        const scores = t.recentScores
        const n = scores.length
        return scores.map((p, i) => {
            const x = n === 1 ? PAD_LEFT + PLOT_W / 2 : PAD_LEFT + (i / (n - 1)) * PLOT_W
            const y = PAD_TOP + PLOT_H - (p.score / 10) * PLOT_H
            return {
                x: Math.round(x),
                y: Math.round(y),
                score: p.score,
                date: p.date.slice(5)
            }
        })
    })

    const linePath = computed(() => {
        if (linePoints.value.length === 0) return ''
        return linePoints.value.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x},${p.y}`).join(' ')
    })

    const lineAreaPath = computed(() => {
        if (linePoints.value.length === 0) return ''
        const pts = linePoints.value
        const start = `M${pts[0].x},${PAD_TOP + PLOT_H}`
        const line = pts.map(p => `L${p.x},${p.y}`).join(' ')
        const end = `L${pts[pts.length - 1].x},${PAD_TOP + PLOT_H} Z`
        return `${start} ${line} ${end}`
    })

    // 历史记录
    const historyList = ref([])
    const pageInfo = reactive({ current: 1, size: 5, total: 0 })
    const detailVisible = ref(false)
    const currentDetail = ref(null)

    const loadHistory = () => {
        getMyEmotionDiaryList({ current: pageInfo.current, size: pageInfo.size }).then(res => {
            historyList.value = res.records || []
            pageInfo.total = res.total || 0
        })
    }

    // 按日期分组
    const groupedHistory = computed(() => {
        const groups = {}
        historyList.value.forEach(item => {
            const date = item.diaryDate || '未知日期'
            if (!groups[date]) groups[date] = []
            groups[date].push(item)
        })
        return groups
    })

    const formatTime = (datetime) => {
        if (!datetime) return ''
        return dayjs(datetime).format('HH:mm')
    }

    const viewDetail = (item) => {
        currentDetail.value = item
        detailVisible.value = true
    }

    const getEmotionEmoji = (emotion) => {
        const map = { '开心': '😊', '平静': '😌', '焦虑': '😰', '悲伤': '😢', '兴奋': '🤩', '疲惫': '😴', '惊讶': '😮', '困惑': '😕' }
        return map[emotion] || '📝'
    }

    const getQualityText = (val) => {
        return ['', '很差', '较差', '一般', '良好', '优秀'][val] || '-'
    }

    const getStressText = (val) => {
        return ['', '很低', '较低', '中等', '较高', '很高'][val] || '-'
    }

    const getScoreLevel = (score) => {
        if (!score) return 'na'
        if (score >= 7) return 'high'
        if (score >= 4) return 'mid'
        return 'low'
    }

    onMounted(() => {
        loadHistory()
        loadTrend()
    })

    const iconUrl = new URL('@/assets/images/like.png', import.meta.url).href
</script>
<style lang="scss" scoped>
    .emotionDiary-container {
    background: var(--bg-body);
    min-height: calc(100vh - 200px);
    .header-section {
        background: linear-gradient(135deg, #2ECC71 0%, #27AE60 100%);
        color: white;
        padding: 40px 24px;
        .header-content {
            max-width: 980px;
            margin: 0 auto;
            display: flex;
            align-items: center;
            gap: 12px;

            h1 {
                font-size: 24px;
                font-weight: 700;
                margin: 0;
            }
        }
    }
    .content {
        margin: 0 auto;
        max-width: 980px;
        padding: 24px;
        .diary-card {
            margin-bottom: 20px;
            background: #FFFFFF;
            border-radius: var(--radius-lg);
            padding: 24px;
            box-shadow: var(--shadow-sm);
            border: 1px solid var(--border-light);
            .title {
                margin-bottom: 20px;
                font-size: 18px;
                font-weight: 600;
                color: var(--text-primary);
            }
            .section {
                margin-bottom: 20px;
                p {
                    font-size: 14px;
                    color: var(--text-secondary);
                    margin-bottom: 15px;
                }
            }
            .emotion-grid {
                display: flex;
                flex-wrap: wrap;
                gap: 12px;
                .emotion-card {
                    padding: 16px 12px;
                    border: 2px solid var(--border-light);
                    border-radius: var(--radius-md);
                    text-align: center;
                    cursor: pointer;
                    background: #FFFFFF;
                    transition: all 0.3s ease;
                    min-width: 80px;

                    .emotion-name {
                        margin-top: 8px;
                        color: var(--text-primary);
                        font-size: 13px;
                    }
                    &:hover {
                        transform: translateY(-3px);
                        box-shadow: var(--shadow-md);
                    }
                    &.selected {
                        border-color: var(--primary);
                        background: var(--primary-bg);
                        transform: translateY(-3px);
                        box-shadow: var(--shadow-green);
                    }
                }
            }
            .detail-form {
                .form-label {
                    margin: 10px 0;
                    color: var(--text-primary);
                    font-weight: 500;
                }
                .life-indicators {
                    display: flex;
                    gap: 20px;
                    .indicator-group {
                        flex: 1;
                    }
                }
                .action-buttons {
                    margin-top: 32px;
                    display: flex;
                    gap: 16px;
                    justify-content: flex-end;
                    .reset-btn {
                        border-radius: var(--radius-full);
                        background: #FFFFFF;
                        border: 1px solid var(--border-color);
                        color: var(--text-secondary);
                        padding: 12px 30px;
                        &:hover {
                            border-color: var(--primary);
                            color: var(--primary);
                        }
                    }
                    .submit-btn {
                        border-radius: var(--radius-full);
                        background: var(--primary);
                        border: none;
                        color: #FFFFFF;
                        padding: 12px 30px;
                        font-weight: 600;
                        box-shadow: var(--shadow-green);
                        &:hover {
                            background: var(--primary-light);
                            transform: translateY(-2px);
                            box-shadow: 0 8px 28px rgba(46, 204, 113, 0.25);
                        }
                    }
                }
            }
            // 情绪趋势关怀卡片
            &.care-card {
                border: 2px solid #2ECC71 !important;
                border-radius: 14px !important;
                background: #FFFFFF !important;
                box-shadow: 0 2px 12px rgba(46, 204, 113, 0.08) !important;
                transition: all 0.3s ease;
                overflow: hidden;
                .care-header {
                    display: flex;
                    align-items: flex-start;
                    gap: 12px;
                    margin-bottom: 14px;
                    padding-bottom: 12px;
                    border-bottom: 1px solid #E8F8F0;
                    .care-icon {
                        font-size: 26px;
                        line-height: 1;
                        flex-shrink: 0;
                    }
                    .care-title-wrap {
                        flex: 1;
                    }
                    .care-title {
                        font-size: 16px !important;
                        font-weight: 700 !important;
                        color: #2C3E50 !important;
                        margin-bottom: 2px;
                    }
                    .care-subtitle {
                        font-size: 12px;
                        color: #95A5A6;
                    }
                }
                .care-message-box {
                    background: linear-gradient(135deg, #F4FBF7 0%, #FFFFFF 100%);
                    border: 2px solid #2ECC71;
                    border-radius: 12px;
                    padding: 16px 20px 16px 24px;
                    margin-bottom: 14px;
                    box-shadow: 0 3px 14px rgba(46, 204, 113, 0.15);
                    position: relative;
                    &::before {
                        content: '';
                        position: absolute;
                        left: 0;
                        top: 12px;
                        bottom: 12px;
                        width: 5px;
                        background: linear-gradient(180deg, #2ECC71 0%, #27AE60 50%, #239B56 100%);
                        border-radius: 0 4px 4px 0;
                    }
                    &::after {
                        content: '💬';
                        position: absolute;
                        right: 14px;
                        top: 14px;
                        font-size: 14px;
                        opacity: 0.35;
                    }
                }
                .care-message {
                    font-size: 13px !important;
                    line-height: 1.75 !important;
                    color: #2C3E50 !important;
                    font-weight: 500 !important;
                    position: relative;
                    z-index: 1;
                }
                .care-body {
                    display: flex !important;
                    flex-direction: row !important;
                    gap: 14px !important;
                    align-items: center !important;
                    .care-stats {
                        display: flex !important;
                        flex-direction: column !important;
                        gap: 10px !important;
                        justify-content: center !important;
                        flex-wrap: nowrap !important;
                        flex-shrink: 0 !important;
                        .stat-card {
                            background: #fff;
                            border: 1px solid #D5D8DC;
                            border-radius: 8px;
                            padding: 8px 14px;
                            text-align: center;
                            min-width: 70px;
                            box-shadow: 0 1px 3px rgba(0,0,0,0.04);
                            &.warn-card {
                                border-color: #F39C12;
                                background: #FEF9E7;
                            }
                            .stat-value {
                                font-size: 22px !important;
                                font-weight: 700 !important;
                                color: #239B56 !important;
                                line-height: 1.2;
                                &.warn {
                                    color: #E67E22 !important;
                                }
                            }
                            .stat-label {
                                font-size: 11px !important;
                                color: #7F8C8D !important;
                                margin-top: 4px;
                                font-weight: 500;
                            }
                        }
                    }
                    .care-chart-section {
                        flex: 1 !important;
                        min-width: 0 !important;
                        .chart-label {
                            font-size: 12px;
                            font-weight: 600;
                            color: #2C3E50;
                            margin-bottom: 6px;
                        }
                    }
                    .care-line-chart {
                        background: #FAFBFC;
                        border: 1px solid #E8F8F0;
                        border-radius: 8px;
                        padding: 6px 10px 2px;
                        .line-chart-svg {
                            width: 100%;
                            height: 120px;
                            display: block;
                        }
                    }
                }
                .care-hotline {
                    margin-top: 18px;
                    padding: 14px 18px;
                    background: linear-gradient(135deg, #FDEDEC, #FEF9E7);
                    border: 2px solid #E74C3C;
                    border-radius: 10px;
                    display: flex;
                    align-items: center;
                    gap: 14px;
                    .hotline-icon {
                        font-size: 28px;
                        flex-shrink: 0;
                    }
                    .hotline-text {
                        flex: 1;
                    }
                    .hotline-title {
                        font-size: 13px;
                        color: #922B21;
                        font-weight: 600;
                        margin-bottom: 2px;
                    }
                    .hotline-number {
                        font-size: 22px;
                        font-weight: 700;
                        color: #C0392B;
                        letter-spacing: 1px;
                    }
                }
                // 预警级别
                &.care-warning {
                    border-left-color: #E67E22;
                    background: linear-gradient(0deg, rgba(230, 126, 34, 0.04), rgba(230, 126, 34, 0.04)), #FFFFFF;
                    .care-header .care-title {
                        color: #B9770E !important;
                    }
                    .care-message-box {
                        background: linear-gradient(135deg, #FEF9E7 0%, #FFFFFF 100%);
                        border: 2px solid #F39C12;
                        box-shadow: 0 3px 14px rgba(230, 126, 34, 0.15);
                        &::before {
                            background: linear-gradient(180deg, #F39C12 0%, #E67E22 100%);
                        }
                    }
                    .care-line-chart {
                        .line-chart-svg {
                            filter: drop-shadow(0 0 3px rgba(46, 204, 113, 0.12));
                        }
                    }
                    animation: carePulse 2.4s ease-in-out infinite;
                }
                &.care-info {
                    border-left-color: #2ECC71;
                }
                &.care-success {
                    border-left-color: #27AE60;
                    .care-header .care-title {
                        color: #1E8449 !important;
                    }
                }
            }
            @keyframes carePulse {
                0%, 100% { box-shadow: 0 0 0 0 rgba(230, 126, 34, 0); }
                50% { box-shadow: 0 0 0 6px rgba(230, 126, 34, 0.10); }
            }
            // 历史记录卡片
            .history-card {
                .empty-history {
                    text-align: center !important;
                    padding: 40px 20px !important;
                    color: #95A5A6 !important;
                    font-size: 14px !important;
                }
                .history-list {
                    display: flex !important;
                    flex-direction: column !important;
                    gap: 16px !important;
                }
                .history-group {
                    .group-header {
                        font-size: 15px !important;
                        font-weight: 700 !important;
                        color: #1E8449 !important;
                        padding: 8px 16px !important;
                        background: #E8F8F0 !important;
                        border-left: 5px solid #2ECC71 !important;
                        border-radius: 6px !important;
                        margin-bottom: 14px !important;
                        display: inline-block !important;
                        width: 100% !important;
                    }
                }
                .history-item {
                    display: flex !important;
                    justify-content: space-between !important;
                    align-items: center !important;
                    padding: 18px 20px !important;
                    border-radius: 12px !important;
                    border: 2px solid #D5D8DC !important;
                    border-left: 5px solid #2ECC71 !important;
                    background: #FAFBFC !important;
                    cursor: pointer !important;
                    transition: all 0.25s ease !important;
                    box-shadow: 0 2px 8px rgba(0,0,0,0.06) !important;
                    margin-bottom: 10px !important;

                    &.score-high {
                        border-left-color: #2ECC71 !important;
                        background: linear-gradient(0deg, #F0FFF4, #FAFBFC) !important;
                    }
                    &.score-mid {
                        border-left-color: #F39C12 !important;
                        background: linear-gradient(0deg, #FEF9E7, #FAFBFC) !important;
                    }
                    &.score-low {
                        border-left-color: #E74C3C !important;
                        background: linear-gradient(0deg, #FDEDEC, #FAFBFC) !important;
                    }
                    &.score-na {
                        border-left-color: #BDC3C7 !important;
                    }

                    &:hover {
                        border-color: #2ECC71 !important;
                        background: #E8F8F0 !important;
                        transform: translateX(4px) !important;
                        box-shadow: 0 6px 18px rgba(46, 204, 113, 0.18) !important;
                    }

                    .history-left {
                        display: flex !important;
                        gap: 14px !important;
                        align-items: flex-start !important;
                        flex: 1 !important;
                        min-width: 0 !important;

                        .history-emoji {
                            font-size: 32px !important;
                            flex-shrink: 0 !important;
                        }

                        .history-info {
                            flex: 1 !important;
                            min-width: 0 !important;

                            .history-time {
                                font-size: 15px !important;
                                font-weight: 700 !important;
                                color: #2C3E50 !important;
                                margin-bottom: 8px !important;
                            }

                            .history-emotion {
                                display: flex !important;
                                gap: 10px !important;
                                align-items: center !important;
                                margin-bottom: 8px !important;

                                .emotion-tag {
                                    background: #2ECC71 !important;
                                    color: #fff !important;
                                    padding: 4px 14px !important;
                                    border-radius: 9999px !important;
                                    font-size: 13px !important;
                                    font-weight: 600 !important;
                                    display: inline-block !important;
                                }

                                .mood-score {
                                    font-size: 13px !important;
                                    font-weight: 700 !important;
                                    color: #2C3E50 !important;
                                    padding: 4px 12px !important;
                                    background: #fff !important;
                                    border-radius: 9999px !important;
                                    border: 1px solid #D5D8DC !important;
                                }
                            }

                            .history-preview {
                                font-size: 13px !important;
                                color: #566573 !important;
                                line-height: 1.6 !important;
                            }
                        }
                    }

                    .history-right {
                        .indicators {
                            display: flex !important;
                            flex-direction: column !important;
                            gap: 6px !important;
                            align-items: flex-end !important;

                            .indicator {
                                font-size: 12px !important;
                                font-weight: 600 !important;
                                color: #2C3E50 !important;
                                background: #fff !important;
                                padding: 4px 12px !important;
                                border-radius: 9999px !important;
                                border: 1px solid #D5D8DC !important;
                            }
                        }
                    }
                }
                .pagination {
                    margin-top: 20px !important;
                    display: flex !important;
                    justify-content: center !important;
                }
            }
        }
    }
    // 详情弹窗
    .detail-dialog {
        .detail-content {
            .detail-row {
                display: flex;
                gap: 12px;
                padding: 10px 0;
                border-bottom: 1px solid var(--border-light);

                .detail-label {
                    font-weight: 600;
                    color: var(--text-primary);
                    min-width: 100px;
                }
            }
            .detail-section {
                margin-top: 16px;
                .detail-label {
                    font-weight: 600;
                    color: var(--text-primary);
                    margin-bottom: 8px;
                }
                p {
                    color: var(--text-secondary);
                    line-height: 1.7;
                    font-size: 14px;
                }
            }
        }
    }
}
</style>
<!-- 非 scoped 兜底样式，确保历史记录样式一定生效 -->
<style lang="scss">
.emotionDiary-container .history-card .history-group .group-header {
    font-size: 15px !important;
    font-weight: 700 !important;
    color: #1E8449 !important;
    padding: 8px 16px !important;
    background: #E8F8F0 !important;
    border-left: 5px solid #2ECC71 !important;
    border-radius: 6px !important;
    margin-bottom: 14px !important;
    display: block !important;
    width: 100% !important;
}

.emotionDiary-container .history-card .history-item {
    display: flex !important;
    justify-content: space-between !important;
    align-items: center !important;
    padding: 18px 20px !important;
    border-radius: 12px !important;
    border: 2px solid #D5D8DC !important;
    border-left: 5px solid #2ECC71 !important;
    background: #FAFBFC !important;
    cursor: pointer !important;
    transition: all 0.25s ease !important;
    box-shadow: 0 2px 8px rgba(0,0,0,0.06) !important;
    margin-bottom: 10px !important;
}

.emotionDiary-container .history-card .history-item.score-high {
    border-left-color: #2ECC71 !important;
    background: linear-gradient(0deg, #F0FFF4, #FAFBFC) !important;
}
.emotionDiary-container .history-card .history-item.score-mid {
    border-left-color: #F39C12 !important;
    background: linear-gradient(0deg, #FEF9E7, #FAFBFC) !important;
}
.emotionDiary-container .history-card .history-item.score-low {
    border-left-color: #E74C3C !important;
    background: linear-gradient(0deg, #FDEDEC, #FAFBFC) !important;
}

.emotionDiary-container .history-card .history-item:hover {
    border-color: #2ECC71 !important;
    background: #E8F8F0 !important;
    transform: translateX(4px) !important;
    box-shadow: 0 6px 18px rgba(46, 204, 113, 0.18) !important;
}

.emotionDiary-container .history-card .history-item .history-info .history-time {
    font-size: 15px !important;
    font-weight: 700 !important;
    color: #2C3E50 !important;
    margin-bottom: 8px !important;
}

.emotionDiary-container .history-card .history-item .history-info .history-emotion .emotion-tag {
    background: #2ECC71 !important;
    color: #fff !important;
    padding: 4px 14px !important;
    border-radius: 9999px !important;
    font-size: 13px !important;
    font-weight: 600 !important;
    display: inline-block !important;
}

.emotionDiary-container .history-card .history-item .history-info .history-emotion .mood-score {
    font-size: 13px !important;
    font-weight: 700 !important;
    color: #2C3E50 !important;
    padding: 4px 12px !important;
    background: #fff !important;
    border-radius: 9999px !important;
    border: 1px solid #D5D8DC !important;
}

.emotionDiary-container .history-card .history-item .history-right .indicators .indicator {
    font-size: 12px !important;
    font-weight: 600 !important;
    color: #2C3E50 !important;
    background: #fff !important;
    padding: 4px 12px !important;
    border-radius: 9999px !important;
    border: 1px solid #D5D8DC !important;
}
</style>
