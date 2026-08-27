<template>
    <div class="frontend-layout">
        <div class="navbar-wrapper">
            <div class="navbar-container">
                <div class="brand-section" @click="$router.push('/')">
                    <div class="brand-logo">
                        <svg viewBox="0 0 24 24" width="32" height="32" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" fill="white"/>
                        </svg>
                    </div>
                    <h1 class="brand-name">青少年心理健康</h1>
                </div>
                <div class="nav-section">
                    <router-link to="/" class="nav-link" exact-active-class="active">
                        <span class="nav-icon">🏠</span> 首页
                    </router-link>
                    <router-link to="/knowledge" class="nav-link" active-class="active">
                        <span class="nav-icon">📖</span> 心理科普
                    </router-link>
                    <router-link to="/consultation" class="nav-link" v-if="isLoggedIn" active-class="active">
                        <span class="nav-icon">💬</span> AI助手
                    </router-link>
                    <router-link to="/emotion-diary" class="nav-link" v-if="isLoggedIn" active-class="active">
                        <span class="nav-icon">📝</span> 情绪日记
                    </router-link>
                    
                    <div class="nav-right">
                        <template v-if="isLoggedIn">
                            <el-dropdown @command="handleCommand">
                                <div class="user-info">
                                    <el-avatar :size="32" src="" class="user-avatar">
                                        <span class="avatar-text">{{ userInfo.nickname || 'U' }}</span>
                                    </el-avatar>
                                    <span class="user-name">{{ userInfo.nickname || '用户' }}</span>
                                    <el-icon class="arrow-icon"><ArrowDown /></el-icon>
                                </div>
                                <template #dropdown>
                                    <el-dropdown-menu>
                                        <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                                        <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                                    </el-dropdown-menu>
                                </template>
                            </el-dropdown>
                        </template>
                        <template v-else>
                            <router-link to="/auth/login" class="nav-link login-link">登录</router-link>
                            <router-link to="/auth/register" class="register-btn">注册</router-link>
                        </template>
                    </div>
                </div>
            </div>
        </div>
        <div class="main-content">
            <router-view></router-view>
        </div>
        <div class="footer-container">
            <div class="footer-content">
                <div class="footer-section">
                    <h4>关于我们</h4>
                    <p>青少年心理健康AI助手</p>
                    <p>陪伴每一个心灵成长</p>
                </div>
                <div class="footer-section">
                    <h4>快捷导航</h4>
                    <router-link to="/" class="footer-link">首页</router-link>
                    <router-link to="/knowledge" class="footer-link">心理科普</router-link>
                    <router-link v-if="isLoggedIn" to="/consultation" class="footer-link">AI助手</router-link>
                </div>
                <div class="footer-section">
                    <h4>温馨提示</h4>
                    <p>如有紧急心理危机，请立即拨打：</p>
                    <p class="hotline">全国心理援助热线：400-161-9995</p>
                </div>
            </div>
            <div class="footer-bottom">
                <p>© 2026 青少年心理健康AI助手. 用心陪伴每一个你 💚</p>
            </div>
        </div>
    </div>
</template>
<script setup>
import { ref, onMounted, computed } from 'vue'
import { logout } from '@/api/admin'
import { useRouter } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'

const router = useRouter()

const isLoggedIn = ref(false)
const userInfo = ref({})

const handleLogout = () => {
    ElMessageBox.confirm('确定退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(() => {
        // token 可能已过期：先清除本地登录态并跳转（不依赖后端成功），logout 仅尽力通知
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        isLoggedIn.value = false
        router.push('/auth/login')
        logout().catch(() => {})
    })
}

const handleCommand = (command) => {
    if (command === 'logout') {
        handleLogout()
    }
}

onMounted(() => {
   const token = localStorage.getItem('token')
   isLoggedIn.value = token !== null
   if (isLoggedIn.value) {
       userInfo.value = JSON.parse(localStorage.getItem('userInfo') || '{}')
   }
})
</script>
<style scoped lang="scss">
.frontend-layout {
    background: var(--bg-body);
    min-height: 100vh;
    display: flex;
    flex-direction: column;
    font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif;

    .navbar-wrapper {
        position: sticky;
        top: 0;
        z-index: 100;
        background: linear-gradient(135deg, #2ECC71 0%, #27AE60 100%);
        box-shadow: 0 4px 20px rgba(46, 204, 113, 0.2);
    }

    .navbar-container {
        max-width: 1200px;
        margin: 0 auto;
        padding: 0 24px;
        height: 64px;
        display: flex;
        align-items: center;
        justify-content: space-between;

        .brand-section {
            display: flex;
            align-items: center;
            cursor: pointer;

            .brand-logo {
                width: 40px;
                height: 40px;
                background: rgba(255, 255, 255, 0.25);
                border-radius: 12px;
                display: flex;
                align-items: center;
                justify-content: center;
                transition: all 0.3s ease;

                &:hover {
                    background: rgba(255, 255, 255, 0.35);
                    transform: scale(1.05);
                }
            }

            .brand-name {
                margin-left: 12px;
                font-size: 20px;
                font-weight: 700;
                color: #fff;
                letter-spacing: 1px;
            }
        }

        .nav-section {
            display: flex;
            align-items: center;
            gap: 4px;

            .nav-link {
                color: rgba(255, 255, 255, 0.85);
                font-size: 15px;
                font-weight: 500;
                padding: 8px 16px;
                border-radius: var(--radius-md);
                transition: all 0.3s ease;
                display: flex;
                align-items: center;
                gap: 6px;

                &:hover {
                    background: rgba(255, 255, 255, 0.15);
                    color: #fff;
                }

                &.active {
                    background: rgba(255, 255, 255, 0.25);
                    color: #fff;
                    font-weight: 600;
                }

                .nav-icon {
                    font-size: 16px;
                }
            }

            .nav-right {
                display: flex;
                align-items: center;
                gap: 12px;
                margin-left: 16px;
                padding-left: 16px;
                border-left: 1px solid rgba(255, 255, 255, 0.2);

                .user-info {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    cursor: pointer;
                    padding: 4px 12px 4px 4px;
                    border-radius: var(--radius-full);
                    background: rgba(255, 255, 255, 0.15);
                    transition: all 0.3s ease;

                    &:hover {
                        background: rgba(255, 255, 255, 0.25);
                    }

                    .user-avatar {
                        background: rgba(255, 255, 255, 0.9);
                        
                        .avatar-text {
                            color: var(--primary);
                            font-weight: 600;
                            font-size: 14px;
                        }
                    }

                    .user-name {
                        color: #fff;
                        font-size: 14px;
                        font-weight: 500;
                    }

                    .arrow-icon {
                        color: rgba(255, 255, 255, 0.7);
                        font-size: 12px;
                    }
                }

                .login-link {
                    color: #fff !important;
                    
                    &:hover {
                        background: rgba(255, 255, 255, 0.15) !important;
                    }
                }

                .register-btn {
                    background: #fff;
                    color: var(--primary);
                    font-weight: 600;
                    font-size: 14px;
                    padding: 8px 24px;
                    border-radius: var(--radius-full);
                    text-decoration: none;
                    transition: all 0.3s ease;
                    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);

                    &:hover {
                        transform: translateY(-2px);
                        box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
                    }
                }
            }
        }
    }

    .main-content {
        flex: 1;
    }

    .footer-container {
        background: #2C3E50;
        color: rgba(255, 255, 255, 0.8);
        margin-top: auto;

        .footer-content {
            max-width: 1200px;
            margin: 0 auto;
            padding: 48px 24px 32px;
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 48px;

            .footer-section {
                h4 {
                    color: #fff;
                    font-size: 16px;
                    font-weight: 600;
                    margin-bottom: 16px;
                }

                p {
                    font-size: 14px;
                    color: rgba(255, 255, 255, 0.6);
                    margin-bottom: 8px;
                    line-height: 1.6;
                }

                .hotline {
                    color: var(--primary-light);
                    font-weight: 600;
                    font-size: 15px !important;
                }

                .footer-link {
                    display: block;
                    font-size: 14px;
                    color: rgba(255, 255, 255, 0.6);
                    margin-bottom: 8px;
                    transition: color 0.3s ease;

                    &:hover {
                        color: var(--primary-light);
                    }
                }
            }
        }

        .footer-bottom {
            border-top: 1px solid rgba(255, 255, 255, 0.1);
            padding: 20px 24px;
            text-align: center;

            p {
                margin: 0;
                font-size: 13px;
                color: rgba(255, 255, 255, 0.5);
            }
        }
    }
}
</style>
