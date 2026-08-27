<template>
    <div class="auth-container">
        <div class="auth-card">
            <div class="auth-header">
                <div class="logo">💚</div>
                <h2>加入我们</h2>
                <p>开启你的心灵成长之旅</p>
            </div>
            <el-form label-position="top" :model="formData" :rules="rules" ref="submitFormRef" class="auth-form">
                <el-form-item label="用户名" prop="username">
                    <el-input v-model="formData.username" placeholder="字母、数字、下划线，3-50字符" size="large" />
                </el-form-item>
                <el-form-item label="邮箱" prop="email">
                    <el-input v-model="formData.email" placeholder="example@mail.com" size="large" />
                </el-form-item>
                <el-form-item label="昵称" prop="nickname">
                    <el-input v-model="formData.nickname" placeholder="怎么称呼你？（可选）" size="large" />
                </el-form-item>
                <el-form-item label="手机号" prop="phone">
                    <el-input v-model="formData.phone" placeholder="11位手机号（可选）" size="large" />
                </el-form-item>
                <el-form-item label="密码" prop="password">
                    <el-input v-model="formData.password" placeholder="6-50个字符" size="large" type="password" show-password />
                </el-form-item>
                <el-form-item label="确认密码" prop="confirmPassword">
                    <el-input v-model="formData.confirmPassword" placeholder="再次输入密码" size="large" type="password" show-password />
                </el-form-item>
                <el-form-item>
                    <el-button class="submit-btn" type="primary" size="large" @click="submitForm(submitFormRef)">注册</el-button>
                </el-form-item>
            </el-form>
            <div class="auth-footer">
                <p>已有账户？<router-link to="/auth/login">立即登录</router-link></p>
                <router-link to="/" class="back-link">← 返回首页</router-link>
            </div>
        </div>
    </div>
</template>
<script setup>
import { ref, reactive, getCurrentInstance } from 'vue'
import { register } from '@/api/frontend'
import { useRouter } from 'vue-router'

const { proxy: vm } = getCurrentInstance()

const router = useRouter()
const formData = reactive({
    "username": "",
    "email": "",
    "nickname": "",
    "phone": "",
    "password": "",
    "confirmPassword": "",
    "gender": 0,
    "userType": 1
})

const rules = reactive({
    "username": [
        { required: true, message: "请输入用户名", trigger: "blur" },
        { min: 3, max: 50, message: "用户名长度3-50个字符", trigger: "blur" },
        { pattern: /^[a-zA-Z0-9_]+$/, message: "用户名只能包含字母、数字、下划线", trigger: "blur" }
    ],
    "email": [
        { required: true, message: "请输入邮箱", trigger: "blur" },
        { type: "email", message: "请输入正确的邮箱格式", trigger: "blur" }
    ],
    "password": [
        { required: true, message: "请输入密码", trigger: "blur" },
        { min: 6, max: 50, message: "密码长度6-50个字符", trigger: "blur" }
    ],
    "confirmPassword": [
        { required: true, message: "请输入确认密码", trigger: "blur" },
        { validator: (rule, value, callback) => {
            if (value !== formData.password) {
                callback(new Error('两次密码不一致'))
            } else {
                callback()
            }
        }, trigger: "blur" }
    ],
    "phone": [
        { validator: (rule, value, callback) => {
            if (!value) {
                callback()
            } else if (/^1[3-9]\d{9}$/.test(value)) {
                callback()
            } else {
                callback(new Error('请输入正确的手机号'))
            }
        }, trigger: "blur" }
    ]
})

const submitFormRef = ref(null)
const submitForm = async (formEl) => {
    if (!formEl) return
    formEl.validate((valid) => {
        if (!valid) return
        const payload = {
            username: formData.username,
            email: formData.email,
            password: formData.password,
            confirmPassword: formData.confirmPassword,
            gender: formData.gender,
            userType: formData.userType
        }
        if (formData.nickname) payload.nickname = formData.nickname
        if (formData.phone) payload.phone = formData.phone
        
        register(payload).then(() => {
            vm.$message.success('注册成功！请登录')
            router.push('/auth/login')
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
    padding: 40px 20px;

    .auth-card {
        width: 460px;
        background: #fff;
        border-radius: 24px;
        padding: 40px;
        box-shadow: 0 16px 48px rgba(46, 204, 113, 0.12);

        .auth-header {
            text-align: center;
            margin-bottom: 28px;

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
                margin-top: 16px;
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
            margin-top: 20px;

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
