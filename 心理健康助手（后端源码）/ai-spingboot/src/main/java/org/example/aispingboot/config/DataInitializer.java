package org.example.aispingboot.config;

import org.example.aispingboot.entity.KnowledgeArticle;
import org.example.aispingboot.entity.KnowledgeCategory;
import org.example.aispingboot.mapper.KnowledgeArticleMapper;
import org.example.aispingboot.mapper.KnowledgeCategoryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private KnowledgeCategoryMapper categoryMapper;

    @Autowired
    private KnowledgeArticleMapper articleMapper;

    @Override
    public void run(String... args) {
        if (categoryMapper.selectCount(null) == 0) {
            seedCategories();
        }
        if (articleMapper.selectCount(null) == 0) {
            seedArticles();
        }
    }

    private void seedCategories() {
        String[] names = {"情绪管理", "压力应对", "冥想正念", "人际关系", "睡眠改善", "自我成长", "心理健康知识", "危机干预"};
        for (int i = 0; i < names.length; i++) {
            KnowledgeCategory cat = new KnowledgeCategory();
            cat.setCategoryName(names[i]);
            cat.setParentId(0L);
            cat.setSortOrder(i + 1);
            cat.setCreatedAt(LocalDateTime.now());
            cat.setUpdatedAt(LocalDateTime.now());
            categoryMapper.insert(cat);
        }
    }

    private void seedArticles() {
        KnowledgeArticle a1 = new KnowledgeArticle();
        a1.setTitle("如何应对日常焦虑情绪");
        a1.setContent("<h2>认识焦虑</h2><p>焦虑是我们生活中常见的情绪反应。适度的焦虑可以帮助我们更好地应对挑战，但过度的焦虑则会影响生活质量。</p><h2>应对方法</h2><p>1. <strong>深呼吸练习</strong>：每天花5-10分钟进行腹式呼吸，能有效缓解紧张感。</p><p>2. <strong>正念冥想</strong>：将注意力集中在当下，不评判地观察自己的想法和感受。</p><p>3. <strong>规律运动</strong>：每周至少150分钟的中等强度运动可以显著降低焦虑水平。</p><p>4. <strong>社交支持</strong>：与信任的朋友或家人分享你的感受，不要独自承受。</p><p>5. <strong>专业帮助</strong>：当焦虑严重影响日常生活时，请及时寻求心理咨询师的帮助。</p><p>记住，寻求帮助是勇敢的表现，不是软弱。</p>");
        a1.setSummary("介绍焦虑的本质和五种实用的应对方法，帮助你更好地管理日常焦虑情绪");
        a1.setCategoryId(1L);
        a1.setTags("焦虑,情绪管理,正念");
        a1.setAuthorName("心灵守护者");
        a1.setAuthorId(0L);
        a1.setReadCount(256);
        a1.setStatus(1);
        a1.setCreatedAt(LocalDateTime.now());
        a1.setUpdatedAt(LocalDateTime.now());
        articleMapper.insert(a1);

        KnowledgeArticle a2 = new KnowledgeArticle();
        a2.setTitle("改善睡眠质量的十个技巧");
        a2.setContent("<h2>为什么睡眠如此重要</h2><p>优质的睡眠是心理健康的基石。长期睡眠不足会导致情绪波动、注意力下降，甚至增加抑郁风险。</p><h2>改善睡眠的十个技巧</h2><p>1. 保持规律的作息时间，即使在周末</p><p>2. 睡前1小时避免使用电子产品</p><p>3. 创造舒适的睡眠环境（温度、光线、噪音）</p><p>4. 避免睡前摄入咖啡因和酒精</p><p>5. 建立放松的睡前仪式，如阅读或泡澡</p><p>6. 规律运动，但避免睡前剧烈运动</p><p>7. 睡前写日记，清空脑海中的烦恼</p><p>8. 练习渐进式肌肉放松</p><p>9. 如果20分钟内无法入睡，起来做些安静的活动</p><p>10. 午睡控制在30分钟以内</p>");
        a2.setSummary("从作息、环境、饮食等多维度提供睡眠改善建议，助你拥有高质量睡眠");
        a2.setCategoryId(5L);
        a2.setTags("睡眠,健康,放松");
        a2.setAuthorName("心灵守护者");
        a2.setAuthorId(0L);
        a2.setReadCount(189);
        a2.setStatus(1);
        a2.setCreatedAt(LocalDateTime.now());
        a2.setUpdatedAt(LocalDateTime.now());
        articleMapper.insert(a2);

        KnowledgeArticle a3 = new KnowledgeArticle();
        a3.setTitle("建立健康的人际关系边界");
        a3.setContent("<h2>什么是人际关系边界</h2><p>人际关系边界是个人在与他人交往中设立的界限，它帮助我们保护自己的情感和心理健康。</p><h2>如何建立健康的边界</h2><p>1. <strong>认识自己的需求</strong>：了解什么让你感到舒适，什么让你感到压力。</p><p>2. <strong>学会说\"不\"</strong>：拒绝他人的请求不代表你不够好，而是你在保护自己。</p><p>3. <strong>清晰表达</strong>：用\"我\"的句式表达你的感受和需求，例如\"我需要一些独处的时间\"。</p><p>4. <strong>尊重他人的边界</strong>：你希望别人尊重你的边界，你也应该尊重别人的。</p><p>5. <strong>不要害怕冲突</strong>：设定边界可能会引起短暂的不适，但长期来看有助于建立更健康的关系。</p><p>记住，健康的边界不是墙，而是门——你可以选择什么时候打开，什么时候关闭。</p>");
        a3.setSummary("探讨人际关系边界的重要性，提供建立和维护健康边界的实用方法");
        a3.setCategoryId(4L);
        a3.setTags("人际关系,边界,自我成长");
        a3.setAuthorName("心灵守护者");
        a3.setAuthorId(0L);
        a3.setReadCount(312);
        a3.setStatus(1);
        a3.setCreatedAt(LocalDateTime.now());
        a3.setUpdatedAt(LocalDateTime.now());
        articleMapper.insert(a3);
    }
}
