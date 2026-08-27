<template>
    <div class="home-container">
        <!-- Banner Section -->
        <div class="banner-section">
            <div class="banner-content">
                <div class="banner-text">
                    <h2 class="banner-title">🌸 每日心理小贴士</h2>
                    <p class="banner-desc">陪伴每一个心灵，温暖每一段成长</p>
                    <div class="banner-tags">
                        <span class="tag">情绪管理</span>
                        <span class="tag">压力释放</span>
                        <span class="tag">心灵成长</span>
                    </div>
                </div>
                <div class="banner-illustration">
                    <div class="cloud cloud-1">☁️</div>
                    <div class="cloud cloud-2">☁️</div>
                    <div class="cloud cloud-3">☁️</div>
                    <div class="hearts">
                        <span class="heart heart-1">💚</span>
                        <span class="heart heart-2">💕</span>
                        <span class="heart heart-3">✨</span>
                    </div>
                    <div class="mascot">🐰</div>
                </div>
            </div>
        </div>

        <!-- Function Icons Section -->
        <div class="functions-section">
            <div class="container">
                <div class="functions-grid">
                    <div class="function-item" @click="$router.push('/knowledge')">
                        <div class="function-icon icon-green">
                            <el-icon :size="28"><Reading /></el-icon>
                        </div>
                        <span class="function-name">心理科普</span>
                    </div>
                    <div class="function-item" @click="handleClick('心理测试')">
                        <div class="function-icon icon-blue">
                            <el-icon :size="28"><Document /></el-icon>
                        </div>
                        <span class="function-name">心理测试</span>
                    </div>
                    <div class="function-item" @click="handleClick('心理活动')">
                        <div class="function-icon icon-purple">
                            <el-icon :size="28"><Calendar /></el-icon>
                        </div>
                        <span class="function-name">心理活动</span>
                    </div>
                    <div class="function-item" @click="goToAI">
                        <div class="function-icon icon-orange">
                            <el-icon :size="28"><ChatDotRound /></el-icon>
                        </div>
                        <span class="function-name">AI助手</span>
                    </div>
                    <div class="function-item" @click="handleClick('意见反馈')">
                        <div class="function-icon icon-yellow">
                            <el-icon :size="28"><ChatLineSquare /></el-icon>
                        </div>
                        <span class="function-name">意见反馈</span>
                    </div>
                    <div class="function-item" @click="handleClick('通知公告')">
                        <div class="function-icon icon-red">
                            <el-icon :size="28"><Bell /></el-icon>
                        </div>
                        <span class="function-name">通知公告</span>
                    </div>
                </div>
            </div>
        </div>

        <!-- Trending Articles Section -->
        <div class="trending-section">
            <div class="container">
                <div class="section-header">
                    <h3 class="section-title">
                        <span class="title-icon">📚</span> 热门科普
                    </h3>
                    <router-link to="/knowledge" class="view-more">
                        查看更多 <el-icon><ArrowRight /></el-icon>
                    </router-link>
                </div>
                <div class="articles-grid">
                    <div v-for="article in articleList" :key="article.id" class="article-card" @click="goToArticle(article.id)">
                        <div class="article-header" :style="{ background: getArticleGradient(article) }">
                            <div class="article-emotion">{{ article.emotionTag || getEmotionTag(article) }}</div>
                            <div class="article-title">{{ article.title }}</div>
                        </div>
                        <div class="article-body">
                            <p class="article-summary">{{ truncateText(article.summary || article.content, 80) }}</p>
                            <div class="article-meta">
                                <span><el-icon><View /></el-icon> {{ article.readCount || 0 }}</span>
                                <span><el-icon><Star /></el-icon> {{ article.favoriteCount || 0 }}</span>
                                <span>{{ formatDate(article.updatedAt) }}</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Reading, Document, Calendar, ChatDotRound, ChatLineSquare, Bell, ArrowRight, View, Star } from '@element-plus/icons-vue'
import { getKnowledgeList } from '@/api/frontend'

const router = useRouter()
const articleList = ref([])

const gradients = [
    'linear-gradient(135deg, #FF6B6B 0%, #FF8E8E 100%)',
    'linear-gradient(135deg, #4ECDC4 0%, #6ED5CE 100%)',
    'linear-gradient(135deg, #667EEA 0%, #764BA2 100%)',
    'linear-gradient(135deg, #F093FB 0%, #F5576C 100%)',
    'linear-gradient(135deg, #43E97B 0%, #38F9D7 100%)',
    'linear-gradient(135deg, #FA709A 0%, #FEE140 100%)'
]

const emotionTags = ['情绪认知', '情绪管理', '人际交往', '学习压力', '自我成长', '睡眠改善']

const getArticleGradient = (article) => {
    const index = article.id % gradients.length
    return gradients[index]
}

const getEmotionTag = (article) => {
    const index = article.id % emotionTags.length
    return emotionTags[index]
}

const truncateText = (text, maxLength) => {
    if (!text) return ''
    return text.length > maxLength ? text.substring(0, maxLength) + '...' : text
}

const formatDate = (dateStr) => {
    if (!dateStr) return ''
    const date = new Date(dateStr)
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

const goToArticle = (id) => {
    router.push(`/knowledge/article/${id}`)
}

const goToAI = () => {
    const token = localStorage.getItem('token')
    if (!token) {
        router.push('/auth/login')
    } else {
        router.push('/consultation')
    }
}

const handleClick = (name) => {
    ElMessage.info(`${name}模块即将上线，敬请期待~`)
}

onMounted(() => {
    getKnowledgeList({
        sortField: 'readCount',
        sortDirection: 'desc',
        currentPage: 1,
        size: 6
    }).then(res => {
        articleList.value = res.records || []
    }).catch(() => {
        articleList.value = [
            { id: 1, title: '如何正确认识和管理情绪', summary: '情绪是人类心理活动的重要组成部分，学会正确认识和管理情绪对青少年的健康成长至关重要...', readCount: 256, favoriteCount: 128, updatedAt: '2026-02-03' },
            { id: 2, title: '青少年人际交往技巧', summary: '良好的人际关系是青少年健康成长的重要保障，掌握人际交往技巧有助于建立和谐的同伴关系...', readCount: 315, favoriteCount: 189, updatedAt: '2026-02-03' },
            { id: 3, title: '学习压力的应对策略', summary: '学习压力是青少年面临的主要压力来源之一，学会正确应对学习压力对于学业和心理健康都非常重要...', readCount: 189, favoriteCount: 98, updatedAt: '2026-02-03' }
        ]
    })
})
</script>

<style scoped lang="scss">
.home-container {
    .banner-section {
        background: linear-gradient(135deg, #E8F8F0 0%, #D5F5E3 50%, #F0FFF4 100%);
        padding: 60px 24px;
        position: relative;
        overflow: hidden;

        .banner-content {
            max-width: 1200px;
            margin: 0 auto;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 40px;

            .banner-text {
                flex: 1;

                .banner-title {
                    font-size: 36px;
                    font-weight: 700;
                    color: #2C3E50;
                    margin-bottom: 16px;
                }

                .banner-desc {
                    font-size: 18px;
                    color: #566573;
                    margin-bottom: 24px;
                    line-height: 1.6;
                }

                .banner-tags {
                    display: flex;
                    gap: 12px;

                    .tag {
                        padding: 6px 16px;
                        background: #fff;
                        border-radius: var(--radius-full);
                        font-size: 13px;
                        color: var(--primary);
                        font-weight: 500;
                        box-shadow: var(--shadow-sm);
                    }
                }
            }

            .banner-illustration {
                width: 340px;
                height: 220px;
                position: relative;
                display: flex;
                align-items: center;
                justify-content: center;

                .cloud {
                    position: absolute;
                    font-size: 40px;
                    opacity: 0.6;
                }

                .cloud-1 {
                    top: 20px;
                    left: 20px;
                    animation: float 4s ease-in-out infinite;
                }

                .cloud-2 {
                    top: 60px;
                    right: 30px;
                    animation: float 5s ease-in-out infinite 1s;
                }

                .cloud-3 {
                    bottom: 30px;
                    left: 60px;
                    animation: float 6s ease-in-out infinite 2s;
                }

                .mascot {
                    font-size: 90px;
                    z-index: 2;
                    animation: bounce 2s ease-in-out infinite;
                }

                .hearts {
                    position: absolute;
                    width: 100%;
                    height: 100%;

                    .heart {
                        position: absolute;
                        font-size: 24px;
                        animation: floatHeart 3s ease-in-out infinite;
                    }

                    .heart-1 {
                        top: 10px;
                        right: 40px;
                        animation-delay: 0s;
                    }

                    .heart-2 {
                        bottom: 40px;
                        left: 10px;
                        animation-delay: 0.5s;
                    }

                    .heart-3 {
                        top: 50%;
                        right: 10px;
                        animation-delay: 1s;
                    }
                }
            }
        }
    }

    .functions-section {
        padding: 48px 24px;

        .container {
            max-width: 1200px;
            margin: 0 auto;
        }

        .functions-grid {
            display: grid;
            grid-template-columns: repeat(6, 1fr);
            gap: 16px;
        }

        .function-item {
            background: #fff;
            border-radius: var(--radius-lg);
            padding: 24px 16px;
            text-align: center;
            cursor: pointer;
            transition: all 0.3s ease;
            border: 1px solid var(--border-light);

            &:hover {
                transform: translateY(-6px);
                box-shadow: var(--shadow-lg);
            }

            .function-icon {
                width: 56px;
                height: 56px;
                border-radius: var(--radius-md);
                display: flex;
                align-items: center;
                justify-content: center;
                margin: 0 auto 12px;
                color: #fff;

                &.icon-green {
                    background: linear-gradient(135deg, #2ECC71, #27AE60);
                }

                &.icon-blue {
                    background: linear-gradient(135deg, #5DADE2, #3498DB);
                }

                &.icon-purple {
                    background: linear-gradient(135deg, #BB8FCE, #9B59B6);
                }

                &.icon-orange {
                    background: linear-gradient(135deg, #F5B041, #E67E22);
                }

                &.icon-yellow {
                    background: linear-gradient(135deg, #F7DC6F, #F1C40F);
                }

                &.icon-red {
                    background: linear-gradient(135deg, #EC7063, #E74C3C);
                }
            }

            .function-name {
                font-size: 14px;
                font-weight: 500;
                color: var(--text-primary);
            }
        }
    }

    .trending-section {
        padding: 24px 24px 48px;

        .container {
            max-width: 1200px;
            margin: 0 auto;
        }

        .section-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 24px;

            .section-title {
                font-size: 22px;
                font-weight: 700;
                color: var(--text-primary);
                display: flex;
                align-items: center;
                gap: 8px;

                .title-icon {
                    font-size: 24px;
                }
            }

            .view-more {
                display: flex;
                align-items: center;
                gap: 4px;
                color: var(--primary);
                font-size: 14px;
                font-weight: 500;
                text-decoration: none;
                transition: gap 0.3s ease;

                &:hover {
                    gap: 8px;
                }
            }
        }

        .articles-grid {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 20px;
        }

        .article-card {
            background: #fff;
            border-radius: var(--radius-lg);
            overflow: hidden;
            cursor: pointer;
            transition: all 0.3s ease;
            box-shadow: var(--shadow-sm);
            border: 1px solid var(--border-light);

            &:hover {
                transform: translateY(-4px);
                box-shadow: var(--shadow-md);
            }

            .article-header {
                padding: 20px;
                color: #fff;

                .article-emotion {
                    display: inline-block;
                    padding: 4px 12px;
                    background: rgba(255, 255, 255, 0.25);
                    border-radius: var(--radius-full);
                    font-size: 12px;
                    font-weight: 500;
                    margin-bottom: 12px;
                }

                .article-title {
                    font-size: 18px;
                    font-weight: 600;
                    line-height: 1.4;
                    display: -webkit-box;
                    -webkit-line-clamp: 2;
                    -webkit-box-orient: vertical;
                    overflow: hidden;
                }
            }

            .article-body {
                padding: 16px 20px;

                .article-summary {
                    font-size: 14px;
                    color: var(--text-secondary);
                    line-height: 1.6;
                    margin-bottom: 12px;
                    display: -webkit-box;
                    -webkit-line-clamp: 2;
                    -webkit-box-orient: vertical;
                    overflow: hidden;
                }

                .article-meta {
                    display: flex;
                    align-items: center;
                    gap: 16px;
                    font-size: 12px;
                    color: var(--text-muted);

                    span {
                        display: flex;
                        align-items: center;
                        gap: 4px;
                    }
                }
            }
        }
    }
}

@keyframes float {
    0%, 100% { transform: translateY(0); }
    50% { transform: translateY(-10px); }
}

@keyframes bounce {
    0%, 100% { transform: translateY(0); }
    50% { transform: translateY(-12px); }
}

@keyframes floatHeart {
    0%, 100% { transform: translateY(0) scale(1); opacity: 1; }
    50% { transform: translateY(-8px) scale(1.1); opacity: 0.8; }
}
</style>
