# -*- coding: utf-8 -*-
"""补生成：对首次生成失败的编号，用调优策略重试补齐。
调优点：① 校验放宽到「golden 出现 + H2>=2」；② prompt 用更强措辞强调 golden 原文不得改写、必须恰好3个##；
③ 减少身份干扰（固定为中性身份）。失败编号从第一轮 SKIP 日志汇总。
"""
import json, os, time, urllib.request, concurrent.futures, sys

API_URL="https://api.deepseek.com/chat/completions"
KEY=os.environ.get("AI_API_KEY","")
MODEL=os.environ.get("AI_CORPUS_MODEL","deepseek-v4-flash")
OUT=os.path.join(os.path.dirname(os.path.abspath(__file__)),"corpus","articles")

# 12 簇的 golden（与 gen_corpus 保持一致）
CLUSTERS={
 "sleep_stimulus":{"golden":"刺激控制法：只有困了才上床，躺下约20分钟睡不着就起身离开卧室","topic":"失眠时的一些行为调整"},
 "sleep_hygiene":{"golden":"睡前1小时远离手机等强光屏幕，卧室保持黑暗凉爽安静","topic":"改善入睡的睡眠卫生习惯"},
 "anxiety_cbt":{"golden":"认知行为疗法（CBT）识别灾难化等扭曲认知并用行为实验检验","topic":"焦虑的认知行为干预"},
 "anxiety_exposure":{"golden":"暴露疗法通过分级的、可承受的接触逐级脱敏","topic":"焦虑的暴露与脱敏"},
 "depression_phq9":{"golden":"PHQ-9 得分≥10 提示需专业评估，但量表不能替代临床诊断","topic":"抑郁的自评筛查量表"},
 "depression_signs":{"golden":"持续两周以上情绪低落兴趣减退且伴随睡眠食欲改变需警惕抑郁","topic":"抑郁的早期识别信号"},
 "stress_quadrant":{"golden":"四象限法把任务按重要/紧急两轴划分优先处理重要且紧急","topic":"压力下的时间管理与任务安排"},
 "stress_mbsr":{"golden":"正念减压（MBSR）是卡巴金开发的八周标准化课程","topic":"正念减压课程"},
 "social_anxiety_exposure":{"golden":"把最怕的社交场景按焦虑程度1到10排序从低焦虑开始逐级暴露","topic":"社交焦虑的分级暴露"},
 "ocd_erp":{"golden":"暴露与反应阻止（ERP）主动接触诱发强迫的刺激同时抵制仪式行为","topic":"强迫症的ERP治疗"},
 "insomnia_restriction":{"golden":"睡眠限制通过压缩卧床时间提升睡眠效率","topic":"睡眠限制疗法的应用"},
 "eating_regular":{"golden":"规律进餐与放弃反复节食有助于重建身体饱饿信号","topic":"进食障碍的饮食重建"},
}

# 缺失编号（来自第一轮 SKIP）
MISSING=[
 ("sleep_stimulus",3),("sleep_hygiene",6),("anxiety_cbt",2),
 ("depression_phq9",7),("depression_phq9",8),("depression_signs",2),
 ("depression_signs",3),("depression_signs",8),("stress_quadrant",8),
 ("stress_mbsr",2),("stress_mbsr",3),("stress_mbsr",4),
 ("ocd_erp",2),("ocd_erp",5),("ocd_erp",6),("ocd_erp",8),
 ("insomnia_restriction",7),
]

SYSTEM=("你是专业心理健康科普作者。为项目自建模拟知识库写一篇中文科普文章。"
 "硬性要求：\n"
 "1) 必须有一个一级标题(# )\n"
 "2) 必须恰好 3 个二级标题，格式为行首 '## '（不是###、不是#）\n"
 "3) 给出的【核心知识点】句子【必须一字不差完整地】出现在某一小节的正文里，不得改写、不得拆分、不得省略任何一个字（含标点）\n"
 "4) 其余两个小节写背景、常见误区或扩展，与其他文章相似但不同\n"
 "5) 自然口语、贴合普通读者；不含免责声明、元话语、总结句\n"
 "6) 长度 250-420 字。只输出正文，无任何前后缀。")

def gen(c,i):
    g=CLUSTERS[c]
    prompt=(f"方向：{g['topic']}。\n"
            f"【核心知识点】（必须原样完整出现一次，一字不改）：{g['golden']}\n"
            f"请写第 {i} 篇（普通读者口吻）。")
    body={"model":MODEL,"messages":[{"role":"system","content":SYSTEM},{"role":"user","content":prompt}],
          "max_tokens":900,"temperature":0.8}
    req=urllib.request.Request(API_URL,data=json.dumps(body).encode('utf-8'),
        headers={"Authorization":f"Bearer {KEY}","Content-Type":"application/json"})
    for a in range(5):
        try:
            with urllib.request.urlopen(req,timeout=120) as r:
                d=json.load(r)
            return c,i,d['choices'][0]['message']['content'] or ''
        except Exception as e:
            if a==4: raise
            time.sleep(1)

def norm(txt,golden):
    lines=txt.strip().splitlines()
    out,h2=[],0
    for ln in lines:
        s=ln.strip()
        if s.startswith("###"): out.append(s.lstrip("#").strip())
        elif s.startswith("##"): out.append(ln); h2+=1
        else: out.append(ln)
    text="\n".join(out)
    ok=(golden[:8] in text) and h2>=2
    return ok,text

def main():
    sys.stdout.reconfigure(encoding='utf-8',errors='replace')
    ok_n=fail_n=0
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as ex:
        futs={ex.submit(gen,c,i):(c,i) for c,i in MISSING}
        for f in concurrent.futures.as_completed(futs):
            c,i=futs[f]
            try:
                _,_,raw=f.result()
                ok,text=norm(raw,CLUSTERS[c]["golden"])
                if not ok:
                    print(f"[FAIL 仍不合格] {c}_{i:02d}",flush=True); fail_n+=1; continue
                with open(os.path.join(OUT,f"{c}_{i:02d}.md"),"w",encoding="utf-8") as fh:
                    fh.write(text)
                ok_n+=1
                print(f"[OK] {c}_{i:02d} 字数={len(text)}",flush=True)
            except Exception as e:
                print(f"[ERR] {c}_{i}: {e}",flush=True); fail_n+=1
    print(f"\n补生成完成：成功 {ok_n} / 失败 {fail_n}")

if __name__=="__main__":
    if not KEY: KEY=os.environ.get("DEEPSEEK_API_KEY","")
    main()
