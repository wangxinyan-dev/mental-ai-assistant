<template>
    <div class="auth-container">
        <div class="auth-card">
            <div class="auth-header">
                <div class="logo">💚</div>
                <h2>欢迎回来</h2>
                <p>登录青少年心理健康助手</p>
            </div>
            <el-form
                ref="ruleFormRef"
                :model="formData"
                :rules="rules"
                label-position="top"
                class="auth-form"
            >
                <el-form-item label="用户名或邮箱" prop="username">
                    <el-input v-model="formData.username" size="large" placeholder="请输入用户名或邮箱" />
                </el-form-item>
                <el-form-item label="密码" prop="password">
                    <el-input v-model="formData.password" size="large" placeholder="请输入密码" type="password" show-password />
                </el-form-item>
                <el-button class="submit-btn" size="large" type="primary" @click="submitForm(ruleFormRef)">登录</el-button>
            </el-form>
            <div class="auth-footer">
                <p>还没有账户？<router-link to="/auth/register">立即注册</router-link></p>
                <router-link to="/" class="back-link">← 返回首页</router-link>
            </div>
        </div>
    </div>
</template>
<script setup>
import { ref, reactive } from 'vue'
import { login } from '@/api/admin'
import { useRouter } from 'vue-router'

const ruleFormRef = ref()

const formData = reactive({
    username: '',
    password: ''
})
const rules = reactive({
    username: [
        { required: true, message: '请输入用户名', trigger: 'blur' }
    ],
    password: [
        { required: true, message: '请输入密码', trigger: 'blur' }
    ]
})

const router = useRouter()
const submitForm = async (formEl) => {
    if (!formEl) return
    await formEl.validate((valid) => {
        if (!valid) return
        login(formData).then(data => {
            localStorage.setItem('token', data.token)
            localStorage.setItem('userInfo', JSON.stringify(data.userInfo))
            if (data.userInfo.userType === 2) {
                router.push('/back/dashboard')
            } else {
                router.push('/')
            }
        }).catch(() => {})
    })
}
</script>
<style scoped lang="scss">
.auth-container {
    min-height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, #E8F8F0 0%, #D5F5E3 50%, #F0FFF4 100%);
    padding: 20px;

    .auth-card {
        width: 420px;
        background: #fff;
        border-radius: 24px;
        padding: 48px 40px;
        box-shadow: 0 16px 48px rgba(46, 204, 113, 0.12);

        .auth-header {
            text-align: center;
            margin-bottom: 32px;

            .logo {
                font-size: 48px;
                margin-bottom: 12px;
            }

            h2 {
                font-size: 24px;
                font-weight: 700;
                color: var(--text-primary);
                margin-bottom: 8px;
            }

            p {
                font-size: 14px;
                color: var(--text-muted);
                margin: 0;
            }
        }

        .auth-form {
            :deep(.el-form-item__label) {
                color: var(--text-primary);
                font-weight: 500;
            }

            :deep(.el-input__wrapper) {
                border-radius: var(--radius-md);
                background: var(--bg-body);
                box-shadow: none;

                &:hover {
                    box-shadow: 0 0 0 1px var(--primary);
                }

                &.is-focus {
                    box-shadow: 0 0 0 2px var(--primary);
                }
            }

            .submit-btn {
                margin-top: 24px;
                width: 100%;
                border-radius: var(--radius-full);
                background: var(--primary);
                border: none;
                font-size: 16px;
                font-weight: 600;
                padding: 14px;
                transition: all 0.3s ease;

                &:hover {
                    background: var(--primary-light);
                    transform: translateY(-2px);
                    box-shadow: 0 8px 24px rgba(46, 204, 113, 0.3);
                }
            }
        }

        .auth-footer {
            text-align: center;
            margin-top: 24px;

            p {
                color: var(--text-muted);
                font-size: 14px;
                margin-bottom: 12px;

                a {
                    color: var(--primary);
                    font-weight: 500;
                    text-decoration: none;

                    &:hover {
                        color: var(--primary-light);
                    }
                }
            }

            .back-link {
                display: inline-block;
                color: var(--text-muted);
                font-size: 13px;
                text-decoration: none;

                &:hover {
                    color: var(--primary);
                }
            }
        }
    }
}
</style>
