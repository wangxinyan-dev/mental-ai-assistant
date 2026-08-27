<template>
    <div class="articleDetail-container">
        <div class="header-section">
            <div class="header-content">
                <el-image :src="iconUrl" style="width: 60px;height: 60px"></el-image>
                <h1>知识文章详情</h1>
            </div>
        </div>
        <div class="content">
            <div class="diary-card">
                <p class="title">文章信息</p>
                <div class="sub-title">
                    <el-tag size="large" class="category-tag">{{ articleDetail.categoryName }}</el-tag>
                    <div class="flex-box">
                        <el-icon><List /></el-icon>
                        <span>{{ dayjs(articleDetail.updatedAt).format('YYYY-MM-DD') }}</span>
                    </div>
                </div>
                <h1 class="article-title">{{ articleDetail.title }}</h1>
                <div class="summary-content" v-if="articleDetail.summary ">
                    <p>{{ articleDetail.summary }}</p>
                </div>
                <div :style="{marginTop: '20px'}" class="flex-box">
                   <div class="item flex-box">
                        <el-icon><Avatar /></el-icon>
                        <span>{{ articleDetail.authorName }}</span>
                    </div>
                    <div class="item flex-box">
                        <el-icon><Platform /></el-icon>
                        <span>{{ articleDetail.readCount }} 次阅读</span>
                    </div>
                </div>
            </div>
            <div class="diary-card">
                <div class="title">正文内容</div>
                <div class="content-wrapper" v-html="formatContent(articleDetail.content)"></div>
                <div class="tags-content" v-if="articleDetail.tagArray && articleDetail.tagArray.length">
                    <h4 class="tags-title"> 相关标签 </h4>
                    <div class="tags-list">
                        <el-tag v-for="tag in articleDetail.tagArray" :key="tag" type="info" effect="light" class="tag-item">{{ tag }}</el-tag>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { getKnowledgeDetail } from '@/api/frontend'
import { dayjs } from 'element-plus'
import { Avatar } from '@element-plus/icons-vue'

const iconUrl = new URL('@/assets/images/book.png', import.meta.url).href

const props = defineProps({
    id: String
})

const articleDetail = ref({})

const formatContent = (content) => {
  if (!content) return ''
  
  // 基本的HTML清理和格式化
  let formatted = content
      .replace(/\n/g, '<br>')
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.*?)\*/g, '<em>$1</em>')
  
  return formatted
}

onMounted(() => {
    console.log(props)
    getKnowledgeDetail(props.id).then(res => {
        articleDetail.value = res
    })
})
</script>

<style lang="scss" scoped>
.articleDetail-container {
    background: var(--bg-body);
    min-height: calc(100vh - 200px);
    .flex-box {
        display: flex;
        align-items: center;
        .item {
            margin-right: 20px;
            span {
                margin-left: 5px;
            }
        }
    }
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
            background: white;
            border-radius: var(--radius-lg);
            padding: 24px;
            box-shadow: var(--shadow-sm);
            border: 1px solid var(--border-light);
            .title {
                margin-bottom: 15px;
                font-size: 18px;
                font-weight: 600;
                color: var(--text-primary);
            }
            .sub-title {
                margin-top: 20px;
                display: flex;
                align-items: center;
                .category-tag {
                    margin-right: 20px;
                }
            }
            .article-title {
                font-size: 28px;
                font-weight: bold;
                color: var(--text-primary);
                margin-top: 24px;
                margin-bottom: 10px;
            }
            .summary-content {
                background: var(--primary-bg);
                border-left: 4px solid var(--primary);
                padding: 12px 16px;
                border-radius: 0 8px 8px 0;
                position: relative;
            }
            .content-wrapper {
                font-size: 15px;
                color: var(--text-secondary);
                line-height: 1.8;
                :deep(p) {
                    margin-bottom: 10px;
                }
                :deep(h1),
                :deep(h2),
                :deep(h3),
                :deep(h4),
                :deep(h5),
                :deep(h6) {
                    margin: 15px 0 10px;
                    color: var(--text-primary);
                    font-weight: 600;
                }
                :deep(h2) {
                    font-size: 16px;
                    border-bottom: 2px solid var(--border-light);
                    padding-bottom: 8px;
                }
                :deep(h3) {
                    font-size: 14px;
                }
                :deep(ul),
                :deep(ol) {
                    padding-left: 20px;
                    margin-bottom: 10px;
                }
                :deep(li) {
                    margin-bottom: 5px;
                }
            }
            .tags-content {
                margin-top: 20px;
                padding-top: 16px;
                border-top: 1px solid var(--border-light);
                .tags-title {
                    margin-bottom: 10px;
                    font-size: 14px;
                    font-weight: 600;
                    color: var(--text-primary);
                }
                .tags-list {
                    display: flex;
                    flex-wrap: wrap;
                    gap: 10px;
                }
            }
        }
    }
}
</style>