<template>
    <div class="auth-layout">
        <div class="left-section">
            <div class="floating-emojis">
                <span class="emoji emoji-1">✨</span>
                <span class="emoji emoji-2">🌱</span>
                <span class="emoji emoji-3">💚</span>
                <span class="emoji emoji-4">✨</span>
                <span class="emoji emoji-5">🌿</span>
            </div>
            <div class="content">
                <h2 class="title">💚 心理AI助手</h2>
                <p class="text">每个深夜，每个焦虑的时刻，我们都在这里。不必独自承受，让心与心的连接温暖您的每一天</p>
                <div class="robot">
                    <div class="robot-glow"></div>
                    <CuteRobot expression="happy" :size="120" :wave="true" />
                </div>
            </div>
        </div>
        <div class="right-section">
            <router-view></router-view>
        </div>
    </div>
</template>
<script setup>
import CuteRobot from './CuteRobot.vue'
</script>
<style scoped lang="scss">
.auth-layout {
    display: flex;
    .left-section {
        position: relative;
        display: flex;
        justify-content: center;
        align-items: center;
        flex: 1;
        background: linear-gradient(135deg, #2ECC71 0%, #58D68D 100%);
        height: 100vh;
        overflow: hidden;

        .floating-emojis {
            position: absolute;
            inset: 0;
            pointer-events: none;

            .emoji {
                position: absolute;
                font-size: 32px;
                opacity: 0.6;
                animation: float 6s ease-in-out infinite;
            }

            .emoji-1 {
                top: 12%;
                left: 15%;
                animation-delay: 0s;
            }
            .emoji-2 {
                top: 22%;
                right: 18%;
                font-size: 40px;
                animation-delay: 1.2s;
            }
            .emoji-3 {
                bottom: 25%;
                left: 20%;
                font-size: 36px;
                animation-delay: 2.4s;
            }
            .emoji-4 {
                bottom: 18%;
                right: 22%;
                font-size: 28px;
                animation-delay: 0.6s;
            }
            .emoji-5 {
                top: 45%;
                left: 8%;
                font-size: 30px;
                animation-delay: 1.8s;
            }
        }

        .content {
            position: relative;
            z-index: 2;
            display: flex;
            flex-direction: column;
            justify-content: center;
            align-items: center;
            .title {
                margin-bottom: 20px;
                font-size: 40px;
                font-weight: bold;
                color: #fff;
                text-shadow: 0 4px 16px rgba(46, 204, 113, 0.4);
                letter-spacing: 1px;
            }
            .text {
                margin-bottom: 30px;
                width: 460px;
                font-size: 20px;
                color: #fff;
                text-align: center;
                line-height: 1.7;
                text-shadow: 0 2px 8px rgba(46, 204, 113, 0.3);
            }
            .robot {
                position: relative;
                display: flex;
                justify-content: center;
                align-items: center;
                width: 160px;
                height: 160px;
                border-radius: 50%;
                border: 2px solid rgba(255, 255, 255, 0.3);
                background: linear-gradient(135deg, rgba(255, 255, 255, 0.25) 0%, rgba(255, 255, 255, 0.1) 100%);
                box-shadow: 0 15px 35px rgba(46, 204, 113, 0.3), inset 0 1px 0 rgba(255, 255, 255, 0.4);
                animation: robotPulse 3s ease-in-out infinite;

                .robot-glow {
                    position: absolute;
                    inset: -20px;
                    border-radius: 50%;
                    background: radial-gradient(circle, rgba(255, 255, 255, 0.4) 0%, rgba(255, 255, 255, 0) 70%);
                    animation: glowPulse 3s ease-in-out infinite;
                    pointer-events: none;
                }
            }
        }
    }
    .right-section {
        display: flex;
        justify-content: center;
        align-items: center;
        flex: 1;
        height: 100vh;
        background-color: #E8F8F0;
    }
}

@keyframes float {
    0%, 100% {
        transform: translateY(0) rotate(0deg);
        opacity: 0.6;
    }
    50% {
        transform: translateY(-20px) rotate(10deg);
        opacity: 0.9;
    }
}

@keyframes robotPulse {
    0%, 100% {
        transform: scale(1);
        box-shadow: 0 15px 35px rgba(46, 204, 113, 0.3), inset 0 1px 0 rgba(255, 255, 255, 0.4);
    }
    50% {
        transform: scale(1.05);
        box-shadow: 0 20px 45px rgba(46, 204, 113, 0.5), inset 0 1px 0 rgba(255, 255, 255, 0.5);
    }
}

@keyframes glowPulse {
    0%, 100% {
        opacity: 0.5;
        transform: scale(1);
    }
    50% {
        opacity: 0.9;
        transform: scale(1.15);
    }
}
</style>