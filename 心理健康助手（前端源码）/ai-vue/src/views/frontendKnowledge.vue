<template>
  <div class="knowledge-container">
    <!-- Breadcrumb -->
    <div class="breadcrumb-section">
      <div class="container">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item>心理科普</el-breadcrumb-item>
        </el-breadcrumb>
      </div>
    </div>

    <!-- Category Filter -->
    <div class="filter-section">
      <div class="container">
        <div class="filter-header">
          <div class="category-tabs">
            <div 
              v-for="cat in categories" 
              :key="cat.id" 
              class="tab-item"
              :class="{ active: currentCategory === cat.id }"
              @click="selectCategory(cat.id)"
            >
              {{ cat.icon }} {{ cat.name }}
            </div>
          </div>
          <div class="search-box">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索科普文章"
              :prefix-icon="Search"
              clearable
              @keyup.enter="handleSearch"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- Articles Grid -->
    <div class="articles-section">
      <div class="container">
        <div class="articles-grid">
          <div 
            v-for="article in articleList" 
            :key="article.id" 
            class="article-card"
            @click="goToArticle(article.id)"
          >
            <div class="card-header" :style="{ background: getGradient(article) }">
              <div class="emotion-tags">
                <span class="tag">{{ getEmotionTag(article) }}</span>
              </div>
              <h3 class="article-title">{{ article.title }}</h3>
            </div>
            <div class="card-body">
              <p class="article-summary">{{ truncateText(article.summary || article.content, 100) }}</p>
              <div class="article-footer">
                <div class="meta-info">
                  <span class="meta-item">
                    <el-icon><View /></el-icon>
                    {{ article.readCount || 0 }} 阅读
                  </span>
                  <span class="meta-item">
                    <el-icon><Star /></el-icon>
                    {{ article.favoriteCount || 0 }} 收藏
                  </span>
                  <span class="meta-item">
                    <el-icon><Clock /></el-icon>
                    {{ formatDate(article.updatedAt) }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Empty state -->
        <div v-if="articleList.length === 0" class="empty-state">
          <div class="empty-icon">📖</div>
          <p>暂无相关文章</p>
        </div>

        <!-- Pagination -->
        <div class="pagination-wrapper" v-if="pagination.total > pagination.size">
          <el-pagination
            layout="prev, pager, next"
            :total="pagination.total"
            :page-size="pagination.size"
            :current-page="pagination.currentPage"
            @current-change="handlePageChange"
            background
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search, View, Star, Clock } from '@element-plus/icons-vue'
import { getKnowledgeList } from '@/api/frontend'

const router = useRouter()

const categories = ref([
  { id: null, name: '全部', icon: '📋' },
  { id: 1, name: '情绪管理', icon: '😊' },
  { id: 2, name: '人际交往', icon: '🤝' },
  { id: 3, name: '学习压力', icon: '📚' },
  { id: 4, name: '自我认知', icon: '🔍' },
  { id: 5, name: '亲子关系', icon: '👨‍👩‍👧' },
  { id: 6, name: '睡眠健康', icon: '😴' },
  { id: 7, name: '青春期心理', icon: '🌱' }
])

const gradients = [
  'linear-gradient(135deg, #FF6B6B 0%, #FF8E8E 100%)',
  'linear-gradient(135deg, #4ECDC4 0%, #6ED5CE 100%)',
  'linear-gradient(135deg, #667EEA 0%, #764BA2 100%)',
  'linear-gradient(135deg, #F093FB 0%, #F5576C 100%)',
  'linear-gradient(135deg, #43E97B 0%, #38F9D7 100%)',
  'linear-gradient(135deg, #FA709A 0%, #FEE140 100%)'
]

const emotionTags = ['情绪认知', '情绪管理', '人际交往', '学习压力', '自我成长', '睡眠改善']

const currentCategory = ref(null)
const searchKeyword = ref('')
const articleList = ref([])

const pagination = reactive({
  currentPage: 1,
  size: 9,
  total: 0
})

const getGradient = (article) => {
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

const selectCategory = (id) => {
  currentCategory.value = id
  pagination.currentPage = 1
  loadArticles()
}

const handleSearch = () => {
  pagination.currentPage = 1
  loadArticles()
}

const handlePageChange = (page) => {
  pagination.currentPage = page
  loadArticles()
}

const loadArticles = () => {
  const params = {
    currentPage: pagination.currentPage,
    pageSize: pagination.size,
    sortField: 'publishedAt',
    sortDirection: 'desc'
  }
  if (currentCategory.value) {
    params.categoryId = currentCategory.value
  }
  if (searchKeyword.value) {
    params.keyword = searchKeyword.value
  }
  getKnowledgeList(params).then(res => {
    articleList.value = res.records || []
    pagination.total = res.total || 0
  }).catch(() => {
    articleList.value = []
    pagination.total = 0
  })
}

const goToArticle = (id) => {
  router.push(`/knowledge/article/${id}`)
}

onMounted(() => {
  loadArticles()
})
</script>

<style lang="scss" scoped>
.knowledge-container {
  min-height: calc(100vh - 200px);

  .breadcrumb-section {
    background: #fff;
    padding: 16px 24px;
    border-bottom: 1px solid var(--border-light);

    .container {
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

  .filter-section {
    background: #fff;
    padding: 0 24px 20px;

    .container {
      max-width: 1200px;
      margin: 0 auto;
    }

    .filter-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 24px;
      flex-wrap: wrap;

      .category-tabs {
        display: flex;
        gap: 8px;
        flex-wrap: wrap;

        .tab-item {
          padding: 8px 20px;
          border-radius: var(--radius-full);
          font-size: 14px;
          font-weight: 500;
          color: var(--text-secondary);
          cursor: pointer;
          transition: all 0.3s ease;
          background: var(--bg-body);

          &:hover {
            background: var(--primary-bg);
            color: var(--primary);
          }

          &.active {
            background: var(--primary);
            color: #fff;
            box-shadow: var(--shadow-green);
          }
        }
      }

      .search-box {
        width: 280px;

        :deep(.el-input__wrapper) {
          border-radius: var(--radius-full);
          background: var(--bg-body);
          box-shadow: none;

          &:hover, &.is-focus {
            background: #fff;
            box-shadow: 0 0 0 1px var(--primary);
          }
        }
      }
    }
  }

  .articles-section {
    padding: 24px 24px 48px;

    .container {
      max-width: 1200px;
      margin: 0 auto;
    }

    .articles-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 24px;
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

      .card-header {
        padding: 24px 20px;
        color: #fff;

        .emotion-tags {
          margin-bottom: 12px;

          .tag {
            display: inline-block;
            padding: 4px 12px;
            background: rgba(255, 255, 255, 0.25);
            border-radius: var(--radius-full);
            font-size: 12px;
            font-weight: 500;
          }
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

      .card-body {
        padding: 16px 20px 20px;

        .article-summary {
          font-size: 14px;
          color: var(--text-secondary);
          line-height: 1.6;
          margin-bottom: 16px;
          display: -webkit-box;
          -webkit-line-clamp: 3;
          -webkit-box-orient: vertical;
          overflow: hidden;
        }

        .article-footer {
          .meta-info {
            display: flex;
            gap: 16px;
            font-size: 12px;
            color: var(--text-muted);
            padding-top: 12px;
            border-top: 1px solid var(--border-light);

            .meta-item {
              display: flex;
              align-items: center;
              gap: 4px;

              .el-icon {
                font-size: 14px;
              }
            }
          }
        }
      }
    }

    .empty-state {
      text-align: center;
      padding: 80px 20px;
      color: var(--text-muted);

      .empty-icon {
        font-size: 64px;
        margin-bottom: 16px;
      }

      p {
        font-size: 16px;
      }
    }

    .pagination-wrapper {
      display: flex;
      justify-content: center;
      margin-top: 40px;

      :deep(.el-pagination.is-background .btn-next),
      :deep(.el-pagination.is-background .btn-prev),
      :deep(.el-pagination.is-background .el-pager li) {
        border-radius: var(--radius-sm);
      }

      :deep(.el-pagination.is-background .el-pager li.is-active) {
        background-color: var(--primary);
      }
    }
  }
}
</style>
