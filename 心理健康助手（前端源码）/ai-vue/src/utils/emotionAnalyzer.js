/**
 * 情绪分析工具 - 根据对话内容推荐机器人表情
 */

// 情绪关键词映射
const emotionKeywords = {
  // 开心/快乐
  happy: [
    '开心', '高兴', '快乐', '笑', '棒', '好', '喜欢', '爱', '幸福',
    '谢谢', '感谢', '哈哈', '嘻嘻', '好玩', '有趣', '成功', '完美',
    '厉害', '真棒', '加油', '努力', '进步'
  ],

  // 关心/担忧
  concern: [
    '担心', '焦虑', '害怕', '恐惧', '紧张', '不安', '忧郁', '烦躁',
    '压力', '累', '疲惫', '无聊', '孤独', '寂寞', '没人', '倾诉',
    '烦恼', '困扰', '问题', '困难', '挫折'
  ],

  // 伤心/难过
  sad: [
    '难过', '伤心', '哭', '眼泪', '痛苦', '悲伤', '失望', '沮丧',
    '崩溃', '绝望', '无助', '委屈', '心碎', '失恋', '分手', '离开',
    '失去', '孤单', '沉默'
  ],

  // 危机/自伤
  crisis: [
    '自杀', '自伤', '想死', '不想活', '结束生命', '轻生', '自残',
    '割腕', '跳楼', '活不下去', '撑不住', '没意义', '活着好累'
  ],

  // 思考/疑问
  think: [
    '为什么', '怎么', '如何', '怎么办', '什么原因', '原因', '解释',
    '分析', '建议', '方法', '方案', '想法', '思考', '考虑', '理解',
    '区别', '对比', '选择'
  ],

  // 安慰/支持
  comfort: [
    '安慰', '支持', '鼓励', '加油', '坚持', '相信', '可以', '能行',
    '没问题', '别担心', '不要紧', '有我在', '陪伴', '倾听', '温暖'
  ],

  // 惊讶/意外
  surprise: [
    '惊讶', '震惊', '没想到', '竟然', '突然', '意外', '奇怪',
    '吃惊', '不可思议', '难以置信'
  ],

  // 友好/打招呼
  greet: [
    '你好', '嗨', '哈喽', '早上好', '下午好', '晚上好', '晚安',
    '在吗', '在不', '聊', '谈谈', '说说', '介绍'
  ]
}

/**
 * 分析用户输入的情绪，决定机器人应该用什么表情回应
 * 注意：机器人是安慰者，不应跟着用户一起难过
 * @param {string} text - 用户输入文本
 * @returns {string} 推荐的表情类型
 */
export function analyzeEmotion(text) {
  if (!text || typeof text !== 'string') {
    return 'default'
  }

  const lowerText = text.toLowerCase()

  // 先检查危机情绪（优先级最高）→ 机器人表示关切
  for (const keyword of emotionKeywords.crisis) {
    if (lowerText.includes(keyword)) {
      return 'concern'
    }
  }

  // 计算每种情绪的匹配数
  const scores = {}
  for (const [emotion, keywords] of Object.entries(emotionKeywords)) {
    if (emotion === 'crisis') continue
    scores[emotion] = 0
    for (const keyword of keywords) {
      if (lowerText.includes(keyword)) {
        scores[emotion] += 1
      }
    }
  }

  // 如果是问候 → 开心
  if (scores.greet > 0 && scores.greet >= 1) {
    return 'happy'
  }

  // 找到得分最高的情绪
  let maxScore = 0
  let dominantEmotion = 'default'

  for (const [emotion, score] of Object.entries(scores)) {
    if (score > maxScore) {
      maxScore = score
      dominantEmotion = emotion
    }
  }

  if (maxScore === 0) {
    return 'default'
  }

  // 机器人跟随用户情绪
  const emotionToExpression = {
    happy: 'happy',        // 用户开心 → 机器人也开心
    sad: 'concern',        // 用户难过 → 机器人也难过
    concern: 'concern',    // 用户焦虑 → 机器人也担忧
    think: 'think',        // 用户提问 → 机器人思考
    comfort: 'comfort',    // 用户寻求安慰 → 机器人安慰
    surprise: 'surprise',  // 用户惊讶 → 机器人也惊讶
    greet: 'happy'         // 用户打招呼 → 机器人开心
  }

  return emotionToExpression[dominantEmotion] || 'default'
}

/**
 * 根据 AI 回复内容分析应该使用的表情
 * AI 是安慰者，默认应该表现出温暖、安慰、开心的表情
 * @param {string} text - AI 回复文本
 * @returns {string} 推荐的表情类型
 */
export function analyzeAiResponseEmotion(text) {
  if (!text || typeof text !== 'string') {
    return 'comfort'
  }

  const lowerText = text.toLowerCase()

  // AI 回复中包含危机干预/热线信息 → 机器人表示关切
  if (lowerText.includes('400') || lowerText.includes('热线') || lowerText.includes('援助') || lowerText.includes('紧急')) {
    return 'concern'
  }

  // AI 回复中包含安慰/支持内容 → 机器人安慰表情
  if (emotionKeywords.comfort.some(kw => lowerText.includes(kw))) {
    return 'comfort'
  }

  // AI 回复中包含鼓励/积极词汇 → 机器人开心
  if (emotionKeywords.happy.some(kw => lowerText.includes(kw))) {
    return 'happy'
  }

  // AI 回复中包含分析/建议 → 机器人思考
  if (emotionKeywords.think.slice(0, 5).some(kw => lowerText.includes(kw))) {
    return 'think'
  }

  // 默认：AI 回复都是温暖的，用安慰表情
  return 'comfort'
}

/**
 * 获取机器人默认表情（打招呼时使用）
 */
export function getDefaultExpression() {
  return 'happy'
}

/**
 * 获取 AI 正在"思考"时的表情
 */
export function getThinkingExpression() {
  return 'think'
}
