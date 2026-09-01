# -*- coding: utf-8 -*-
"""心理健康语料生成器（自建高仿真模拟库）
用 DeepSeek 对话 LLM 批量生成「答案非唯一、多篇冗余」的中文心理科普语料。
目的：制造 RAG 评测的区分度——同一黄金知识点在多篇文章里以不同措辞出现，
逼 chunking / rerank / 混合检索在「多篇相近文档」间做选择。
产物：corpus/articles/*.md（每篇一条）+ corpus/gold_mapping.json（黄金知识点→簇信息）
仅用于技术验证，自建模拟库，不含版权第三方内容。
"""
import json, os, time, urllib.request, urllib.error, concurrent.futures

API_URL = "https://api.deepseek.com/chat/completions"
KEY = os.environ.get("AI_API_KEY", "")        # 从 env 读，不写进文件
MODEL = os.environ.get("AI_CORPUS_MODEL", "deepseek-v4-flash")
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "corpus")

# ---- 主题簇：每簇 = 一个黄金知识点 + 生成方向 ----
CLUSTERS = [
  {"id":"sleep_stimulus","golden":"刺激控制法：只有困了才上床，躺下约20分钟睡不着就起身离开卧室","topic":"失眠时的一些行为调整"},
  {"id":"sleep_hygiene","golden":"睡前1小时远离手机等强光屏幕，卧室保持黑暗凉爽安静","topic":"改善入睡的睡眠卫生习惯"},
  {"id":"anxiety_cbt","golden":"认知行为疗法（CBT）识别灾难化等扭曲认知并用行为实验检验","topic":"焦虑的认知行为干预"},
  {"id":"anxiety_exposure","golden":"暴露疗法通过分级的、可承受的接触逐级脱敏","topic":"焦虑的暴露与脱敏"},
  {"id":"depression_phq9","golden":"PHQ-9 得分≥10 提示需专业评估，但量表不能替代临床诊断","topic":"抑郁的自评筛查量表"},
  {"id":"depression_signs","golden":"持续两周以上情绪低落兴趣减退且伴随睡眠食欲改变需警惕抑郁","topic":"抑郁的早期识别信号"},
  {"id":"stress_quadrant","golden":"四象限法把任务按重要/紧急两轴划分优先处理重要且紧急","topic":"压力下的时间管理与任务安排"},
  {"id":"stress_mbsr","golden":"正念减压（MBSR）是卡巴金开发的八周标准化课程","topic":"正念减压课程"},
  {"id":"social_anxiety_exposure","golden":"把最怕的社交场景按焦虑程度1到10排序从低焦虑开始逐级暴露","topic":"社交焦虑的分级暴露"},
  {"id":"ocd_erp","golden":"暴露与反应阻止（ERP）主动接触诱发强迫的刺激同时抵制仪式行为","topic":"强迫症的ERP治疗"},
  {"id":"insomnia_restriction","golden":"睡眠限制通过压缩卧床时间提升睡眠效率","topic":"睡眠限制疗法的应用"},
  {"id":"eating_regular","golden":"规律进餐与放弃反复节食有助于重建身体饱饿信号","topic":"进食障碍的饮食重建"},
]

SYSTEM_PROMPT = (
  "你是一名专业的心理健康科普写作者。请为本项目的自建模拟知识库撰写一篇中文科普文章。"
  "要求：① 全文自然、口语、贴合普通读者；② 必须有一个一级标题(# 开头)；"
  "③ 用 Markdown，恰好 3 个二级标题('## ' 小节)，不要使用 ### 或更高级别标题；"
  "④ 其中一个小节必须包含给定的【核心知识点】原文（在正文里完整出现一次），另两个小节写相关背景、误区或扩展，"
  "制造与其他文章相似又不同的内容；⑤ 不含免责声明、不含'以上内容'等元话语、不以总结句结尾；"
  "⑥ 长度 250-420 字。只输出文章正文，不要任何前后缀或编号。"
)

def normalize(content, golden):
    """归一化：去掉 ### 残留，确保 golden 前 8 字存在，保留 ##/## 标题。返回 (ok, text)。"""
    lines = content.strip().splitlines()
    out, h2 = [], 0
    for ln in lines:
        s = ln.strip()
        if s.startswith("###"):
            out.append(s.lstrip("#").strip())
        elif s.startswith("##"):
            out.append(ln); h2 += 1
        elif s.startswith("#"):
            out.append(ln)
        else:
            out.append(ln)
    text = "\n".join(out)
    ok = (golden[:8] in text) and (h2 >= 3)
    return ok, text

def generate(cluster, idx):
    identities = ["普通上班族","学生","新手父母","独居青年","中年职场人","退休老人","异地恋青年","备考学生"]
    ident = identities[(idx - 1) % len(identities)]
    prompt = (f"方向：{cluster['topic']}。\n【核心知识点】{cluster['golden']}\n"
              f"请写第 {idx} 篇，这一篇的情境侧重：{ident}。"
              f"请在正文自然体现该身份，但不要把【核心知识点】原文改掉。")
    body = {"model":MODEL,
            "messages":[{"role":"system","content":SYSTEM_PROMPT},{"role":"user","content":prompt}],
            "max_tokens":900, "temperature":0.9}
    req = urllib.request.Request(API_URL, data=json.dumps(body).encode('utf-8'),
        headers={"Authorization":f"Bearer {KEY}","Content-Type":"application/json"})
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                d = json.load(r)
            return cluster, idx, normalize(d['choices'][0]['message']['content'] or '', cluster['golden'])
        except urllib.error.HTTPError as e:
            if e.code == 429:
                time.sleep(2*(attempt+1)); continue
            raise
        except Exception:
            if attempt == 4: raise
            time.sleep(1)
    return None

def main():
    import sys
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    # 支持 CLI: gen_corpus.py [簇id] [每簇篇数]  便于小规模试跑
    only_cluster = sys.argv[1] if len(sys.argv) > 1 else None
    n_default = 8
    n_override = int(sys.argv[2]) if len(sys.argv) > 2 else None
    clusters = [c for c in CLUSTERS if only_cluster is None or c["id"] == only_cluster]

    os.makedirs(os.path.join(OUT_DIR,"articles"), exist_ok=True)
    tasks=[]
    for c in clusters:
        n = n_override if n_override is not None else n_default
        for i in range(1, n+1):
            tasks.append((c,i))

    gold_map={}
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as ex:
        futs={ex.submit(generate,c,i):(c,i) for c,i in tasks}
        done=0; failed=0
        for f in concurrent.futures.as_completed(futs):
            c,i=futs[f]
            try:
                _,_,(ok,content)=f.result()
                if not ok:
                    # 格式不合格 → 自动重试（LLM 有概率不严格遵循，重跑大概率达标）
                    fixed = None
                    for _ in range(3):
                        r = generate(c, i)
                        if r:
                            _,_,(ok2, txt2) = r
                            if ok2:
                                fixed = (ok2, txt2); break
                    if fixed is None:
                        print(f"[SKIP 重试仍不合格] {c['id']}_{i:02d}", flush=True); failed+=1; continue
                    ok, content = fixed
                fn=f"{c['id']}_{i:02d}.md"
                with open(os.path.join(OUT_DIR,"articles",fn),"w",encoding="utf-8") as fh:
                    fh.write(content)
                gold_map.setdefault(c["id"], {"golden":c["golden"],"topic":c["topic"],"titles":[]})
                gold_map[c["id"]]["titles"].append(content.strip().splitlines()[0].lstrip('#').strip()[:50])
                done+=1
                print(f"[{done}] {fn} 字数={len(content)}", flush=True)
            except Exception as e:
                print(f"[FAIL] {c['id']}_{i}: {e}", flush=True); failed+=1
    with open(os.path.join(OUT_DIR,"gold_mapping.json"),"w",encoding="utf-8") as fh:
        json.dump({"generated":done,"failed":failed,"clusters":gold_map},fh,ensure_ascii=False,indent=2)
    print(f"\n完成：成功 {done} / 失败 {failed} → {OUT_DIR}")

if __name__ == "__main__":
    if not KEY:
        KEY = os.environ.get("DEEPSEEK_API_KEY", "")
    main()
