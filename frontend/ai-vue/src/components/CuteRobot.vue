<template>
  <div class="cute-robot" :class="{ 'is-thinking': isThinking, 'is-talking': isTalking }">
    <svg :width="size" :height="size" viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
      <!-- 天线 -->
      <g class="antenna" :class="{ 'antenna-wave': isTalking }">
        <line x1="50" y1="15" x2="50" y2="28" stroke="#2ECC71" stroke-width="3" stroke-linecap="round"/>
        <circle cx="50" cy="12" r="5" :fill="isTalking ? '#FF6B6B' : '#2ECC71'">
          <animate v-if="isTalking" attributeName="r" values="5;7;5" dur="0.5s" repeatCount="indefinite"/>
        </circle>
      </g>

      <!-- 头部 -->
      <rect x="18" y="25" width="64" height="55" rx="16" ry="16" :fill="bodyColor" stroke="#1ABC9C" stroke-width="2"/>

      <!-- 脸颊腮红 -->
      <circle cx="28" cy="58" r="5" :fill="blushColor" opacity="0.5"/>
      <circle cx="72" cy="58" r="5" :fill="blushColor" opacity="0.5"/>

      <!-- 眼睛 -->
      <g class="eyes">
        <!-- 左眼 -->
        <g class="eye left-eye" :class="eyeExpression">
          <ellipse cx="37" cy="50" rx="7" ry="8" fill="white"/>
          <circle cx="37" cy="50" r="4" fill="#2C3E50">
            <animate v-if="eyeExpression === 'happy'" attributeName="cy" values="50;52;50" dur="1s" repeatCount="indefinite"/>
            <animate v-if="eyeExpression === 'sleepy'" attributeName="ry" values="8;2;8" dur="2s" repeatCount="indefinite"/>
          </circle>
          <circle cx="35" cy="48" r="1.5" fill="white"/>
        </g>

        <!-- 右眼 -->
        <g class="eye right-eye" :class="eyeExpression">
          <ellipse cx="63" cy="50" rx="7" ry="8" fill="white"/>
          <circle cx="63" cy="50" r="4" fill="#2C3E50">
            <animate v-if="eyeExpression === 'happy'" attributeName="cy" values="50;52;50" dur="1s" repeatCount="indefinite"/>
            <animate v-if="eyeExpression === 'sleepy'" attributeName="ry" values="8;2;8" dur="2s" repeatCount="indefinite"/>
          </circle>
          <circle cx="61" cy="48" r="1.5" fill="white"/>
        </g>
      </g>

      <!-- 嘴巴 -->
      <g class="mouth" :class="mouthExpression">
        <!-- 开心笑容 -->
        <path v-if="mouthExpression === 'smile'" d="M 40 68 Q 50 78 60 68" stroke="#2C3E50" stroke-width="3" fill="none" stroke-linecap="round"/>
        <!-- 关心表情 -->
        <path v-else-if="mouthExpression === 'concern'" d="M 42 70 Q 50 65 58 70" stroke="#2C3E50" stroke-width="2.5" fill="none" stroke-linecap="round"/>
        <!-- 思考表情 -->
        <line v-else-if="mouthExpression === 'think'" x1="44" y1="70" x2="56" y2="70" stroke="#2C3E50" stroke-width="2.5" stroke-linecap="round"/>
        <!-- 安慰表情 -->
        <path v-else-if="mouthExpression === 'comfort'" d="M 40 68 Q 45 72 50 68 Q 55 72 60 68" stroke="#2C3E50" stroke-width="2.5" fill="none" stroke-linecap="round"/>
        <!-- 惊讶表情 -->
        <ellipse v-else-if="mouthExpression === 'surprise'" cx="50" cy="70" rx="4" ry="5" fill="#2C3E50"/>
        <!-- 大笑表情 -->
        <path v-else-if="mouthExpression === 'laugh'" d="M 38 66 Q 50 82 62 66 Q 50 76 38 66 Z" fill="#FF6B6B" stroke="#2C3E50" stroke-width="2"/>
        <!-- 爱心表情 -->
        <path v-else-if="mouthExpression === 'love'" d="M 50 72 C 50 68, 46 66, 46 70 C 46 74, 50 76, 50 78 C 50 76, 54 74, 54 70 C 54 66, 50 68, 50 72 Z" fill="#FF6B6B"/>
        <!-- 默认微笑 -->
        <path v-else d="M 42 68 Q 50 74 58 68" stroke="#2C3E50" stroke-width="2.5" fill="none" stroke-linecap="round"/>
      </g>

      <!-- 身体 -->
      <g class="body">
        <rect x="30" y="80" width="40" height="18" rx="8" ry="8" :fill="bodyColor" stroke="#1ABC9C" stroke-width="2"/>
        <!-- 胸口装饰 -->
        <circle cx="50" cy="89" r="4" :fill="isTalking ? '#FF6B6B' : '#58D68D'">
          <animate v-if="isTalking" attributeName="r" values="4;6;4" dur="0.8s" repeatCount="indefinite"/>
        </circle>
      </g>

      <!-- 挥手动画 -->
      <g v-if="waveHand" class="hand-wave" style="transform-origin: 78px 70px;">
        <ellipse cx="82" cy="65" rx="5" ry="8" :fill="bodyColor" stroke="#1ABC9C" stroke-width="2">
          <animateTransform attributeName="transform" type="rotate" values="-20;20;-20" dur="0.6s" repeatCount="indefinite" additive="sum"/>
        </ellipse>
      </g>
    </svg>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  // 表情类型: default, happy, concern, think, comfort, surprise, laugh, love
  expression: {
    type: String,
    default: 'default'
  },
  // 大小
  size: {
    type: [String, Number],
    default: '64'
  },
  // 是否正在说话（动画效果）
  talking: {
    type: Boolean,
    default: false
  },
  // 是否挥手
  wave: {
    type: Boolean,
    default: false
  }
})

const isTalking = computed(() => props.talking)
const waveHand = computed(() => props.wave)

// 身体颜色根据表情变化
const bodyColor = computed(() => {
  const colorMap = {
    happy: '#A8E6CF',
    laugh: '#A8E6CF',
    love: '#FFB3B3',
    concern: '#D4E9F7',
    comfort: '#FFE4B5',
    surprise: '#FFFACD',
    think: '#E6E6FA',
    default: '#B8F2E6'
  }
  return colorMap[props.expression] || colorMap.default
})

// 腮红颜色
const blushColor = computed(() => {
  const colorMap = {
    happy: '#FF9999',
    laugh: '#FF6B6B',
    love: '#FF69B4',
    concern: '#FFB6C1',
    comfort: '#FFB347',
    default: '#FFB3B3'
  }
  return colorMap[props.expression] || colorMap.default
})

// 眼睛表情
const eyeExpression = computed(() => {
  const map = {
    happy: 'happy',
    laugh: 'happy',
    love: 'happy',
    sleepy: 'sleepy',
    default: 'normal'
  }
  return map[props.expression] || map.default
})

// 嘴巴表情
const mouthExpression = computed(() => {
  const validExpressions = ['smile', 'concern', 'think', 'comfort', 'surprise', 'laugh', 'love']
  return validExpressions.includes(props.expression) ? props.expression : 'smile'
})
</script>

<style scoped>
.cute-robot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.3s ease;
}

.cute-robot.is-talking {
  animation: gentle-bounce 1s ease-in-out infinite;
}

.cute-robot.is-thinking {
  animation: gentle-sway 2s ease-in-out infinite;
}

@keyframes gentle-bounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-3px); }
}

@keyframes gentle-sway {
  0%, 100% { transform: rotate(-2deg); }
  50% { transform: rotate(2deg); }
}

.antenna-wave circle {
  filter: drop-shadow(0 0 4px rgba(255, 107, 107, 0.6));
}
</style>
