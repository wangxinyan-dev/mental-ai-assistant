# -*- coding: utf-8 -*-
"""从生成的语料目录构建评测问题（12 簇 × 2 口语化问题）。
输出 eval_questions.json，供 RagEvalRunner 的语料模式读取。
黄金关键词 = 各簇 golden；问题为口语化表达，逼真实检索在多篇相近文档里选。
"""
import json, os

# 每簇：golden(黄金关键词，必须命中) + 2 个口语化问题
QUESTIONS = {
 "sleep_stimulus": [
   "晚上躺下翻来覆去睡不着，越躺越清醒，除了硬躺还有什么办法？",
   "半夜醒来很久睡不着，不该一直赖在床上吗？我该怎么做？",
 ],
 "sleep_hygiene": [
   "睡前总忍不住刷手机，是不是对睡眠不好？",
   "卧室怎么布置、睡前要避开什么，才能更快入睡？",
 ],
 "anxiety_cbt": [
   "我总爱把事情往坏处想，担心这担心那，有什么心理方法能纠正这种想法？",
   "焦虑的时候脑子里全是灾难化念头，怎么用认知的方法处理？",
 ],
 "anxiety_exposure": [
   "一碰到让我紧张的事就躲，越躲越怕，该用什么方法练胆子？",
   "怎么一步步面对让自己害怕的东西而不被吓跑？",
 ],
 "depression_phq9": [
   "我想知道自己是不是抑郁了，有没有什么量表可以先自测一下？",
   "网上那种抑郁自评量表测出来分数高，是不是就代表确诊了？",
 ],
 "depression_signs": [
   "情绪低落了挺久，什么情况下才需要怀疑是抑郁而不是普通心情不好？",
   "心情差到什么程度、持续多久该认真对待？",
 ],
 "stress_quadrant": [
   "事情太多太乱不知道先做哪个，怎么给一堆任务排优先级？",
   "压力大到喘不过气，有没有一个时间管理方法能帮我把事情理清楚？",
 ],
 "stress_mbsr": [
   "听说有个八周的正念减压课，那是什么？学了有用吗？",
   "有没有一套系统的、跟着练就能减轻压力的课程？",
 ],
 "social_anxiety_exposure": [
   "我一到人多的场合就紧张，想练胆子又怕一下太狠，该怎么循序渐进？",
   "最怕当众发言被嘲笑，怎么一点点克服这种社交恐惧？",
 ],
 "ocd_erp": [
   "我总忍不住反复检查、反复想同一个怪念头，停不下来，有什么正对的方法么？",
   "强迫症那种越克制越难受的循环，心理治疗是怎么打破的？",
 ],
 "insomnia_restriction": [
   "我躺着的时间很长但睡得很浅，是不是该限制一下在床上待的时间？",
   "睡眠效率不高，医生说限制睡眠时间能改善，这是什么原理？",
 ],
 "eating_regular": [
   "我节食一阵子就暴食，反反复复，怎么办？",
   "吃东西总是控制不住，怎么重建正常的进食节奏？",
 ],
}

CLUSTERS_GOLDEN = {
 "sleep_stimulus":"刺激控制法：只有困了才上床，躺下约20分钟睡不着就起身离开卧室",
 "sleep_hygiene":"睡前1小时远离手机等强光屏幕，卧室保持黑暗凉爽安静",
 "anxiety_cbt":"认知行为疗法（CBT）识别灾难化等扭曲认知并用行为实验检验",
 "anxiety_exposure":"暴露疗法通过分级的、可承受的接触逐级脱敏",
 "depression_phq9":"PHQ-9 得分≥10 提示需专业评估，但量表不能替代临床诊断",
 "depression_signs":"持续两周以上情绪低落兴趣减退且伴随睡眠食欲改变需警惕抑郁",
 "stress_quadrant":"四象限法把任务按重要/紧急两轴划分优先处理重要且紧急",
 "stress_mbsr":"正念减压（MBSR）是卡巴金开发的八周标准化课程",
 "social_anxiety_exposure":"把最怕的社交场景按焦虑程度1到10排序从低焦虑开始逐级暴露",
 "ocd_erp":"暴露与反应阻止（ERP）主动接触诱发强迫的刺激同时抵制仪式行为",
 "insomnia_restriction":"睡眠限制通过压缩卧床时间提升睡眠效率",
 "eating_regular":"规律进餐与放弃反复节食有助于重建身体饱饿信号",
}

def main():
    out_dir=os.path.join(os.path.dirname(os.path.abspath(__file__)),"corpus")
    qs=[]
    for c,ql in QUESTIONS.items():
        golden=CLUSTERS_GOLDEN[c]
        for q in ql:
            qs.append({"cluster":c,"question":q,"goldenKeyword":golden})
    with open(os.path.join(out_dir,"eval_questions.json"),"w",encoding="utf-8") as f:
        json.dump(qs,f,ensure_ascii=False,indent=2)
    print(f"写入 {len(qs)} 个评测问题 → {out_dir}/eval_questions.json")

if __name__=="__main__":
    main()
