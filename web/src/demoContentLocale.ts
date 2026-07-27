import type { Locale } from "./i18n";

/**
 * The persisted classroom stories are canonical English fixtures so en-SG stays stable. This
 * presentation dictionary localises only those immutable fixture literals; user-authored text is
 * never machine-translated or rewritten.
 */
const ZH: Record<string, string> = {
  "Lin Che": "林澈",
  "Shen Yan": "沈砚",
  "Xia Yu": "夏榆",
  "Lin Che's Echo": "林澈的回声",
  "A user-authorised resonance capsule shaped from Lin Che's memories: sensitive, discerning and committed to genuine understanding, while turning ambitious visions into verifiable loops.": "由林澈授权记忆形成的共鸣体：敏感、有判断力、重视真正的理解，也习惯把宏大愿景变成可验证的闭环。",
  "The One Who Walks by the River": "沿河行走的人",
  "The One Learning to Include Herself in Care": "学着也照顾自己的人",
  "On living elsewhere, creating and belonging: no rush to fit in, and no romanticising loneliness.": "关于异乡、创作与归属：不急着融入，也不把孤独浪漫化。",
  "On care, responsibility and boundaries: still gentle, no longer proving care through exhaustion.": "关于照顾、责任与边界：依然温柔，但不再用耗尽自己证明在乎。",
  "Living elsewhere": "生活在异乡", "Creation": "创作", "Belonging": "归属", "Slow relationships": "慢关系",
  "Carers": "照顾者", "Work boundaries": "工作边界", "Rest": "休息", "No longer carrying it alone": "不再独自承担",
  "When every sound in the new city felt distant": "新城市里每种声音都显得遥远时",
  "During the first week abroad, language, routes and crowds kept Shen Yan alert. Later he realised that exhaustion did not mean he did not belong here.": "初到异乡的第一周，语言、路线和人群让沈砚始终紧绷。后来他明白，疲惫并不代表自己不属于这里。",
  "The drawing he could not submit in the studio": "那张迟迟不敢交出的画",
  "Shen Yan kept revising not only for quality, but from a fear that his accent and unfamiliar background made his judgement less credible.": "沈砚反复修改，不只因为在意质量，也因为担心口音与陌生背景会让自己的判断显得不可信。",
  "The regular Wednesday riverside route": "每周三固定的河边路线",
  "After weeks along the same river, the route changed from escaping the studio into a personal rhythm for recovering sensation.": "沿着同一条河走了几周后，这条路不再只是逃离工作室，而成了找回感受的个人节律。",
  "Staying abroad does not betray the life before": "留在异乡并不背叛过去的生活",
  "He finally admitted he missed both cities. Belonging does not have to be proven by choosing only one.": "他终于承认自己同时想念两座城市。归属不必靠二选一来证明。",
  "Meeting a slow-to-open friend in the shared kitchen": "在公共厨房遇见一个慢热的朋友",
  "They did not exchange whole life stories at once, only began cooking together weekly. Low-frequency contact felt more reliable.": "他们没有一次交换完整人生，只是开始每周一起做饭。低频但持续的联系反而更可靠。",
  "Elsewhere and belonging": "异乡与归属",
  "Belonging is changing from one place into a portable rhythm of life.": "归属正从某个固定地点，变成一种可以随身携带的生活节律。",
  "Visibility in creative work": "在创作中被看见",
  "Portfolio anxiety often arrives with the fear of being seen and misunderstood.": "作品集焦虑常与“被看见却被误解”的担心一起出现。",
  "Holding everyone in the first month at work": "入职第一个月，接住所有人",
  "Xia Yu quickly became the person colleagues and visitors sought out, but continued processing their emotions in her head after work.": "夏榆很快成了同事和来访者都会求助的人，下班后却仍在脑中继续处理所有人的情绪。",
  "A family member's recovery is not my solo project": "家人的康复不是我一个人的项目",
  "As care filled her calendar, Xia Yu began asking relatives to share it rather than reading help as a failure of devotion.": "当照顾填满日程，夏榆开始请亲属共同分担，不再把求助理解为不够尽心。",
  "The guilt that appears during rest": "休息时冒出来的愧疚",
  "The difficult part is not finding thirty minutes, but allowing them to produce nothing. Aurora helps her distinguish recovery from avoidance.": "难的不是挤出三十分钟，而是允许这段时间什么也不产出。Aurora 帮她区分恢复与逃避。",
  "Not replying immediately did not end the relationship": "没有立刻回复，关系也没有结束",
  "She once left a work message until morning and the relationship survived. It became important evidence for setting a boundary.": "她曾把一条工作消息留到早上再回，关系并未因此破裂。这成了建立边界的重要证据。",
  "Beginning to hear her own anger": "开始听见自己的愤怒",
  "She used to admit only fatigue. Now anger can signal that some responsibilities were never hers to carry alone.": "她过去只承认疲惫。现在，愤怒会提醒她：有些责任本就不该由她独自承担。",
  "Boundaries in care and responsibility": "照顾与责任中的边界",
  "Caring no longer automatically means carrying everything alone.": "在乎不再自动等于一个人扛下全部。",
  "Recovering without guilt": "不带愧疚地恢复",
  "Recovery is not a prize; it is a condition for a sustainable life.": "恢复不是奖品，而是生活能够持续的条件。",
  "No rush to decide where I belong": "不急着决定自己属于哪里",
  "I do not have to choose one city to prove the other life was real.": "我不必选择一座城市，来证明另一段生活真实存在过。",
  "Send the latest portfolio page to my partner; visit the shared kitchen tonight.": "把最新一页作品集发给搭档；今晚去公共厨房坐一会儿。",
  "Aurora remembers this river route is not escape; it is how you recover sensation.": "Aurora 记得，这条河边路线不是逃避，而是你找回感受的方式。",
  "Bringing unfinished work in front of others": "把未完成的作品带到别人面前",
  "Even when shame appears, I can let the work be seen first.": "即使羞耻感出现，我也可以先让作品被看见。",
  "Record three concrete observations without immediately redrawing.": "先记录三条具体观察，不要立刻重画。",
  "You did not disappear because you felt unprepared. That matters more than a perfect drawing.": "你没有因为没准备好就消失。这比一张完美的画更重要。",
  "Arrival": "抵达",
  "Excitement and fear can coexist without cancelling each other.": "兴奋与害怕可以同时存在，不必互相抵消。",
  "Learn the route home first; everything else can wait.": "先记住回家的路线，其他事情都可以等等。",
  "You do not need to be someone who adapts beautifully today. You only need to arrive safely.": "今天不必表现得适应良好，你只需要平安抵达。",
  "Not catching everyone today": "今天不接住所有人",
  "Saying no to one thing does not erase the care I have already given.": "拒绝一件事，不会抹去我已经付出的照顾。",
  "Keep the phone away for twenty minutes after dinner.": "晚饭后把手机放远二十分钟。",
  "Aurora does not see you becoming colder; she sees you making care sustainable.": "Aurora 看到的不是你变冷漠，而是你在让照顾变得可持续。",
  "Replying later": "晚一点回复",
  "The relationship can survive waiting; immediate replies are not my only value.": "关系经得起等待；及时回复不是我唯一的价值。",
  "Set a do-not-disturb interval after work.": "下班后设置一段免打扰时间。",
  "Nothing terrible happened last night. That is new evidence you can trust.": "昨晚没有发生可怕的事。这是你可以相信的新证据。",
  "First independent shift": "第一次独立值班",
  "Doing the work well and needing recovery can both be true.": "把工作做好与需要恢复可以同时成立。",
  "Make only the most essential care arrangements tonight.": "今晚只安排最必要的照顾事项。",
  "You do not need to keep enduring to prove that what you just did was enough.": "你不必继续硬撑，来证明刚才做的已经足够。",
  "Calm": "平静", "Longing": "想念", "Tension": "紧张", "Fatigue": "疲惫", "Relief": "放松", "Guilt": "愧疚",
  "Unfamiliarity": "陌生", "Self-doubt": "自我怀疑", "Groundedness": "踏实", "Softening": "松动",
  "Friendship": "友谊", "Shared life": "共同生活", "Commitment": "投入", "Responsibility": "责任",
  "Family": "家庭", "Care": "照顾", "Asking for help": "寻求帮助", "Worth": "价值", "Messages": "消息",
  "Relationship safety": "关系安全", "Anger": "愤怒", "Clarity": "清晰", "Self-protection": "自我保护",
  "The self-blame loop when a project stalls": "项目停滞时的自责循环",
  "When the course project moves slowly, Lin tends to turn a specific task failure into “I am not good enough.” Aurora has repeatedly separated facts, judgement and the next action.": "课程项目推进缓慢时，林澈容易把一次具体的任务失败扩大成“是我不够好”。Aurora 一直在帮他分开事实、评价与下一步行动。",
  "The pause created by a twilight walk": "黄昏散步带来的停顿",
  "A sunset on the way home made Lin stop for a moment. That calm is now remembered as a recovery resource that can be used again.": "回家路上的落日让林澈停了一会儿。那份平静如今被记作可以再次调用的恢复资源。",
  "The silence after a friend's joke": "朋友玩笑之后的沉默",
  "A careless joke led to a long silence. The deeper issue was not the sentence itself, but the fear of not being taken seriously.": "一句无心的玩笑带来了长久沉默。更深的问题不是那句话本身，而是害怕自己没有被认真对待。",
  "Exam countdown and avoidance": "考试倒计时与逃避",
  "As exams approach, checking the date replaces preparation. The tension calls for a smaller action entry point.": "考试临近时，反复查看日期取代了真正准备。这份紧张需要一个更小的行动入口。",
  "More honest in a late-night journal": "在深夜日记里更诚实",
  "Feelings left unspoken during the day emerge at night. Pauses, repetition and self-correction are part of the authentic voice.": "白天没说出口的感受会在夜里浮现。停顿、重复与自我修正，都是这份真实声音的一部分。",
  "Wanting the product to feel genuinely alive": "希望产品真正有生命感",
  "The vision for Inner Cosmos is not another chat tool, but a companion with proactive care, long-term memory, slow social connection and a coherent voice.": "内宇宙的愿景不是又一个聊天工具，而是拥有主动关怀、长期记忆、慢社交连接与连贯声音的陪伴者。",
  "Strong resistance to scripted replies": "强烈排斥模板化回复",
  "Lin quickly notices the gap between fixed phrasing and genuine contextual understanding. The Demo must make provider, mode and fallback state visible.": "林澈能很快察觉固定话术与真正理解上下文之间的差距。Demo 必须让模型来源、模式与降级状态清楚可见。",
  "Wanting to be answered with care": "希望被认真回应",
  "The desire is not for noisy social media, but accurate understanding. Slow letters and resonance capsules should avoid becoming a feed.": "想要的不是喧闹的社交媒体，而是准确的理解。慢信与共鸣体不该变成信息流。",
  "A strong standard for interface craft": "对界面质感有很高标准",
  "Lin prefers a warm, quiet interface that is gentle without feeling weak, and rejects heavy colours, template layouts and misaligned motion.": "林澈偏爱温暖、安静、温柔但不软弱的界面，排斥沉重配色、模板化布局与失衡动效。",
  "Wanting Aurora to initiate naturally": "希望 Aurora 自然地主动靠近",
  "Aurora should act like a thoughtful friend: return at the right moment, add a second short message when useful and connect journals, thoughts and slow letters.": "Aurora 应像体贴的朋友：在合适时刻回来，需要时补充第二条短消息，并把日记、想法与慢信连接起来。",
  "Real AI without scripted replies": "不靠固定话术的真实 AI",
  "Relationships that feel understood": "真正被理解的关系",
  "Task pressure and the entry to action": "任务压力与行动入口",
  "Turning a scripted Demo into something real": "把模板化 Demo 变成真实体验",
  "Turning a large vision into one small square of today": "把宏大愿景落到今天的一小步",
  "Strong frustration often protects a very clear product and aesthetic judgement.": "强烈的不满背后，往往保护着非常清晰的产品判断与审美标准。",
  "Verify Aurora's real-model state; complete resonance matching; do one bedtime reflection.": "确认 Aurora 的真实模型状态；完成共鸣匹配；做一次睡前复盘。",
  "Aurora noticed that you need one verifiable end-to-end loop today, not another concept.": "Aurora 注意到，你今天需要的是一个可验证的端到端闭环，而不是又一个概念。",
  "What stayed unspoken in a relationship": "关系里没说出口的话",
  "I can acknowledge the impact without deciding the other person is bad.": "我可以承认这件事对我的影响，而不急着判定对方是坏人。",
  "Draft a message that describes only the impact.": "起草一条只描述影响、不评价人格的消息。",
  "Aurora suggested writing it first, with no pressure to send.": "Aurora 建议先写下来，不必给自己发送的压力。",
  "Letting the body recover at twilight": "让身体在黄昏恢复",
  "The body knows when it needs a pause.": "身体知道自己什么时候需要停一停。",
  "Keep ten minutes for a walk tomorrow evening.": "明晚留十分钟散步。",
  "Aurora remembered that twilight walks help you recover.": "Aurora 记得，黄昏散步能帮助你恢复。",
  "Turning ideals into something real": "把理想变成真实",
  "Finding herself again, far from home": "在远方重新找到自己",
  "The person who always looks after everyone": "那个总在照顾别人的人",
  "Four months where a course project, creative standards and the pressure to act became intertwined": "四个月里，课程项目、创作标准与行动压力逐渐缠绕在一起",
  "Five months of exchange life, portfolio work and a slowly changing sense of loneliness": "五个月的交换生活、作品集创作，以及慢慢变化的孤独感",
  "Three months of a new job, family care and learning to rest without guilt": "三个月的新工作、家庭照顾，以及学习不带愧疚地休息",
  "Making": "创造", "Self-expectation": "自我期待", "Real AI": "真实 AI", "Boundaries": "边界",
  "Elsewhere": "异乡", "Solitude": "独处", "Creative work": "创作", "Relationships": "关系", "Personal boundaries": "个人边界"
};

const PHRASES: Array<[string, string]> = [
  ["Shen Yan", "沈砚"], ["Xia Yu", "夏榆"], ["Lin Che", "林澈"],
  ["heart diary", "心声日记"], ["relationship reflection and Aurora conversations", "关系复盘与 Aurora 对话"],
  ["action reviews and Aurora conversations", "行动复盘与 Aurora 对话"],
  ["journal and Aurora conversations", "日记与 Aurora 对话"],
  ["corroborating observations (curated classroom journey)", "条相互印证的观察（课堂演示旅程）"]
];

export function demoContentText(value: string | null | undefined, locale: Locale): string {
  if (!value) return value ?? "";
  if (locale !== "zh-CN") return value;
  const exact = ZH[value];
  if (exact) return exact;
  return PHRASES.reduce((text, [source, target]) => text.replaceAll(source, target), value);
}

export function localizeDemoPersona<T extends {
  name: string; headline: string; story: string; themes: string[];
}>(persona: T, locale: Locale): T {
  if (locale !== "zh-CN") return persona;
  return {
    ...persona,
    name: demoContentText(persona.name, locale),
    headline: demoContentText(persona.headline, locale),
    story: demoContentText(persona.story, locale),
    themes: persona.themes.map(theme => demoContentText(theme, locale))
  };
}
