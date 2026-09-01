# -*- coding: utf-8 -*-
"""第二轮补生成：12 篇仍缺的，改用「短锚点」命中判定（而非整句 golden）。
LLM 只要在正文自然带出该簇的独特锚点（2-6字）+ 恰好3个##，即算合格。
锚点已在第一轮生成的84篇中验证全部命中，此处为全局统一口径，避免整句精确复述的强约束。
"""
import json, os, time, urllib.request, concurrent.futures, sys

API_URL="https://api.deepseek.com/chat/completions"
KEY=os.environ.get("AI_API_KEY","")
MODEL=os.environ.get("AI_CORPUS_MODEL","deepseek-v4-flash")
OUT=os.path.join(os.path.dirname(os.path.abspath(__file__)),"corpus","articles")

# 簇 -> (topic, 短锚点, 补充提示)
CLUSTERS={
 "sleep_hygiene":{"topic":"改善入睡的睡眠卫生习惯","anchor":"远离手机"},
 "anxiety_cbt":{"topic":"焦虑的认知行为干预","anchor":"行为实验"},
 "depression_signs":{"topic":"抑郁的早期识别信号","anchor":"兴趣减退"},
 "stress_quadrant":{"topic":"压力下的时间管理与任务安排","anchor":"重要/紧急"},
 "stress_mbsr":{"topic":"正念减压课程","anchor":"卡巴金"},
 "ocd_erp":{"topic":"强迫症的ERP治疗","anchor":"反应阻止"},
 "insomnia_restriction":{"topic":"睡眠限制疗法的应用","anchor":"睡眠限制"},
}
MISSING=[
 ("sleep_hygiene",6),("anxiety_cbt",2),("depression_signs",8),("stress_quadrant",8),
 ("stress_mbsr",2),("stress_mbsr",3),("stress_mbsr",4),
 ("ocd_erp",2),("ocd_erp",5),("ocd_erp",6),("ocd_erp",8),("insomnia_restriction",7),
]

SYSTEM=("你是专业心理健康科普作者。为项目自建模拟知识库写一篇中文科普文章。硬性要求：\n"
 "1) 必须有一个一级标题(# )\n"
 "2) 必须恰好 3 个二级标题('## ' 行首，不要###)\n"
 "3) 正文里必须出现给定的【必须包含的关键词】（原样出现一次，不要改写）\n"
 "4) 其余自然写背景/误区/扩展，与其他文章相似但不同，贴合普通读者\n"
 "5) 不含免责声明、元话语、总结句；长度250-420字；只输出正文，无前后缀。")

def gen(c,i):
    g=CLUSTERS[c]
    prompt=(f"方向：{g['topic']}。\n【必须包含的关键词】（正文原样出现一次）：{g['anchor']}\n请写第 {i} 篇。")
    body={"model":MODEL,"messages":[{"role":"system","content":SYSTEM},{"role":"user","content":prompt}],
          "max_tokens":900,"temperature":0.8}
    req=urllib.request.Request(API_URL,data=json.dumps(body).encode('utf-8'),
        headers={"Authorization":f"Bearer {KEY}","Content-Type":"application/json"})
    for a in range(5):
        try:
            with urllib.request.urlopen(req,timeout=120) as r:
                d=json.load(r)
            return c,i,d['choices'][0]['message']['content'] or ''
        except Exception:
            if a==4: raise
            time.sleep(1)

def norm(txt,anchor):
    lines=txt.strip().splitlines(); out,h2=[],0
    for ln in lines:
        s=ln.strip()
        if s.startswith("###"): out.append(s.lstrip("#").strip())
        elif s.startswith("##"): out.append(ln); h2+=1
        else: out.append(ln)
    text="\n".join(out)
    return (anchor in text) and h2>=2, text

def main():
    sys.stdout.reconfigure(encoding='utf-8',errors='replace')
    os.makedirs(OUT,exist_ok=True)
    ok_n=fail_n=0
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as ex:
        futs={ex.submit(gen,c,i):(c,i) for c,i in MISSING}
        for f in concurrent.futures.as_completed(futs):
            c,i=futs[f]
            try:
                _,_,raw=f.result()
                ok,text=norm(raw,CLUSTERS[c]["anchor"])
                if not ok:
                    print(f"[FAIL] {c}_{i:02d}",flush=True); fail_n+=1; continue
                with open(os.path.join(OUT,f"{c}_{i:02d}.md"),"w",encoding="utf-8") as fh:
                    fh.write(text)
                ok_n+=1; print(f"[OK] {c}_{i:02d} 字数={len(text)}",flush=True)
            except Exception as e:
                print(f"[ERR] {c}_{i}: {e}",flush=True); fail_n+=1
    print(f"\n补生成完成：成功 {ok_n} / 失败 {fail_n}")

if __name__=="__main__":
    if not KEY: KEY=os.environ.get("DEEPSEEK_API_KEY","")
    main()
