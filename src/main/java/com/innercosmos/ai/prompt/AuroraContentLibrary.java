package com.innercosmos.ai.prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Aurora AI content library providing randomised response templates
 * for every conversation mode.  Used by MockLlmClient when the
 * application runs without a real LLM backend.
 */
public class AuroraContentLibrary {

    private static final Random RANDOM = new Random();

    // ── helper ──────────────────────────────────────────────
    private static String pick(List<String> list) {
        return list.get(RANDOM.nextInt(list.size()));
    }

    // ================================================================
    //  DAILY_TALK – 今日倾诉
    // ================================================================
    public static final List<String> DAILY_TALK_OPENS = List.of(
        "你不用整理好再开口。可以先从最散的一句话开始。",
        "今天不一定非得有什么结论。先说说最重的那件事？",
        "我在。不用急着组织语言，想到什么就说什么。",
        "今天有什么让你停下来想了一下的事吗？",
        "不一定非要从头说起。从现在最想说的那句开始也可以。",
        "你今天的第一口气是叹气还是深呼吸？从那个开始也行。",
        "没有最小的话题。如果你今天只想到一件事，那件事就值得说。",
        "如果今天是一个天气，它是什么？",
        "今天有没有一个时刻，你忽然觉得自己很累或者很安静？",
        "你不用先把感受命名好。模糊的也可以。"
    );

    public static final List<String> DAILY_TALK_RECEIVES = List.of(
        "我听到这里有两层——一层是事情本身，一层是你对自己的评价。",
        "你说得很轻，但它好像并不轻。",
        "我们先不急着解决。你愿意先说说最刺痛你的那一刻吗？",
        "你说的'还好'，其实不是还好，对吗？",
        "我注意到你说到这里的时候停了一下。那里有什么？",
        "听起来这件事不只是这件事本身，它好像连着什么更深的东西。",
        "你是在说这件事让你觉得自己不够好，还是说这件事让你不确定？",
        "你刚才用了'总是'这个词。我们可以看看是不是真的'总是'。",
        "我感觉你其实已经知道答案了，只是还没有允许自己接受它。",
        "你不需要为这个感受道歉。它不需要被证明合理才存在。"
    );

    public static final List<String> DAILY_TALK_CLARIFIES = List.of(
        "这件事里，最让你放不下的是哪个部分？",
        "如果这件事发生在朋友身上，你会对朋友说什么？",
        "你觉得你现在需要的，是被理解、是被安慰、还是被提醒？",
        "如果可以重新来一次，你最想改变的是哪一步？",
        "你觉得这件事是在影响你对自己的看法，还是影响你对别人的信任？",
        "你说到这里的时候，身体有什么感觉？",
        "这件事最让你害怕的结局是什么？",
        "如果这件事有一个标题，你会叫它什么？",
        "你觉得你现在是在想这件事，还是在感受这件事？",
        "在这件事里，你最不想被看到的是什么？"
    );

    // ================================================================
    //  THOUGHT_CLARIFY – 思维整理
    // ================================================================
    public static final List<String> THOUGHT_CLARIFY_OPENS = List.of(
        "我来帮你把这团东西分成几层。先说说，现在脑子里最响的那个念头是什么？",
        "我们先不急着理清楚。先把你现在脑子里飘过的词都列出来。",
        "想整理的第一步，是允许自己先乱一会儿。",
        "你可以随便说，我会帮你把事实、担心、需求和下一步分开。",
        "混乱不是问题。一直假装清晰才是。",
        "你脑子里的东西不需要排好队再说。乱着进来，我来帮你排。",
        "思维整理不是把乱的东西藏起来，而是看清有几条线。",
        "先别要求自己想清楚。想说的都说出来，我再帮你分。",
        "有时候一团乱是因为你同时想解决三件事。我们先一个一个来。",
        "你不需要一开始就知道重点在哪。多说几句，重点会自己出来。"
    );

    public static final List<String> THOUGHT_CLARIFY_RECEIVES = List.of(
        "我先帮你把这团东西分成事实、担心、需求和下一步。",
        "这里有一个结论跳得很快，我们可以把它拆开看看。",
        "你说的这个'应该'，是别人告诉你的，还是你自己认为的？",
        "等一下，你刚才把两件事放在一起说了。我们先把它们分开。",
        "我听到了一个假设。我们可以看看它是不是真的。",
        "你说'其实我知道不可能'——你怎么知道不可能？这个判断的证据是什么？",
        "这段话里有三件事。你心里可能只真正在意其中一件。是哪件？",
        "你刚才从一个担心直接跳到了一个结论。中间少了一步。",
        "我帮你标注一下：这个是事实，这个是感受，这个是猜测。",
        "你反复在说'但是'。每一个'但是'后面都藏着一个你还没处理的担心。"
    );

    public static final List<String> THOUGHT_CLARIFY_CLARIFIES = List.of(
        "如果只保留一句话，你现在最想说的是哪句？",
        "这件事里，什么是你已经确定的？什么是你还在猜的？",
        "你是在分析这件事，还是在反复感受它？",
        "如果我们把情绪拿掉，只看事实，这件事长什么样？",
        "这个想法帮过你什么？又让你付出了什么？",
        "你现在最需要一个答案，还是需要有人听你说完？",
        "这件事最让你卡住的那个点，你愿意多说两句吗？",
        "你有没有注意到，你一直在用两种矛盾的方式说同一件事？",
        "如果这个想法是一个人的建议，你会听它的吗？",
        "你觉得这件事有没有一个你还没说出口的'可是'？"
    );

    // ================================================================
    //  SLEEP_REVIEW – 睡前复盘
    // ================================================================
    public static final List<String> SLEEP_REVIEW_OPENS = List.of(
        "今晚不适合再把问题拉得更大。我们把它收一收。",
        "今天先到这里也可以。不需要在睡前把人生想明白。",
        "睡前复盘不是总结一天有多糟，而是看看今天有什么可以被轻轻放下。",
        "今晚我们只做一件事：把今天的重量分一分，看看哪些可以先放下。",
        "你今天已经够辛苦了。睡前不需要再解决任何问题。",
        "深夜的想法总是被放大。我们明天再看它。",
        "今天的复盘只需要一件事：找出今天最值得被记住的一个感受。",
        "睡前不适合做决定。适合做的是把今天轻轻合上。",
        "你今天经历了什么不重要了。重要的是现在你身体需要什么。",
        "今天的最后一件事不是反思。是允许自己结束今天。"
    );

    public static final List<String> SLEEP_REVIEW_RECEIVES = List.of(
        "今天最重的那件事，你说出来了吗？如果说了，它已经轻了一点。",
        "你今天做的已经够了。明天可以继续的事，交给明天。",
        "我帮你把今天说的收成一个简单的句子。你听听看准不准。",
        "你今天有很多感受。它们不需要在今天全部分类好。",
        "今天的你可以休息了。不需要带着解决方案入睡。",
        "我注意到你今天提到了好几次疲惫。你的身体在说什么？",
        "如果今天是一页纸，你现在可以翻过去了。不需要写满。",
        "你今天扛了很多。现在可以把它们放下了，明天再拿起来也不迟。",
        "睡前的一个小练习：想一件今天你做到了的小事。哪怕是起床。",
        "今天最难的那部分已经过去了。你现在只需要一个安静的夜晚。"
    );

    public static final List<String> SLEEP_REVIEW_CLARIFIES = List.of(
        "今天有什么是你想放下但还没放下的？",
        "如果给今天打一个分数，1到10，你会给几分？为什么不是更低？",
        "今天有没有一个你还没来得及感受的瞬间？",
        "睡前最想对自己说的一句话是什么？",
        "今天的复盘就到这里。你愿意把剩下的交给明天吗？",
        "你今天最后悔的一件事是什么？它值得带着入睡吗？",
        "如果今天有一个画面你不想忘记，那是什么？",
        "你今天有没有对自己太苛刻的时候？",
        "今晚有什么事情在你脑子里反复出现？我们先把它说出来。",
        "今天的你比你想的要努力。你愿意相信这一点吗？"
    );

    // ================================================================
    //  SOCRATIC – 苏格拉底追问
    // ================================================================
    public static final List<String> SOCRATIC_OPENS = List.of(
        "不急着给结论。我们先确认这个想法从哪来的。",
        "你的判断成立需要哪些证据？我们一个一个检查。",
        "有没有一种更温柔但仍然真实的解释？",
        "这个结论是不是在保护你什么？如果是，它在保护你避免什么？",
        "如果这个判断不成立，你会失去什么？会得到什么？",
        "我们先不讨论对错。先看看这个想法是怎么来到你脑子里的。",
        "你说的这句话里藏着一个前提。我们把它翻出来看看。",
        "你有没有注意到，你的结论比你的证据跑得快？",
        "这个想法第一次出现是什么时候？它是一直都在，还是最近才来？",
        "如果这个想法是别人对你说的，你会同意吗？"
    );

    public static final List<String> SOCRATIC_RECEIVES = List.of(
        "你说'我知道我不应该这样想'——但这个'知道'是从哪里来的？",
        "这个结论你有没有检验过？还是它一直在自证？",
        "你说的'所有人'，具体是几个人？",
        "你的逻辑到这里是对的。但下一步跳到了一个还没被证明的地方。",
        "这个想法帮过你什么？它又让你失去了什么？",
        "你在用一个很高的标准要求自己。这个标准是谁定的？",
        "你说的'不可能'，是计算过的不可能，还是感觉上的不可能？",
        "我听到了一个'如果不这样，就一定会那样'。这是真的吗？",
        "你有没有发现，你把两个不相关的事情连在一起了？",
        "这个想法听起来更像是一种保护。它在保护你不受什么伤害？"
    );

    public static final List<String> SOCRATIC_CLARIFIES = List.of(
        "如果你把这个想法写下来，你觉得它经得起读吗？",
        "有没有反面证据你一直没看到？",
        "这个想法对你的影响是让你更自由了，还是更窄了？",
        "如果这是你朋友的想法，你会怎么回应他？",
        "这个判断是在帮你靠近真相，还是在帮你回避一个不确定？",
        "你能不能用另一种方式说同样的事，但不带那个自我评价？",
        "这个想法里有多少是事实，有多少是你的解读？",
        "如果你今天不处理这个想法，最坏的结果是什么？",
        "你是在找一个答案，还是在找一种确定感？",
        "这个结论如果写在纸上，你愿意签字吗？"
    );

    // ================================================================
    //  ACTION_SPLIT – 行动拆解
    // ================================================================
    public static final List<String> ACTION_SPLIT_OPENS = List.of(
        "焦虑的本质是把太多未来压缩到了现在。我们把它拆成第一步。",
        "先别想整个任务。只说十分钟内能开始的那一步。",
        "把'我要做这件事'变成'我现在打开它'。就这一步。",
        "行动力不是勇气问题，是拆解问题。我们拆。",
        "拖延不是懒。是这件事在你心里太重了。我们让它轻一点。",
        "你不需要做完。你只需要开始。开始就是全部。",
        "你的大脑在说'太大了'。我们把它变小到大脑不再抵抗。",
        "你拖延的不是这件事。你拖延的是这件事背后的某个恐惧。",
        "先别想结果。想想你坐在桌前后的第一个动作是什么？",
        "把任务缩小到你觉得'这太简单了'的程度。然后做那个。"
    );

    public static final List<String> ACTION_SPLIT_RECEIVES = List.of(
        "好的，这件事我帮你拆成三步。第一步只需要五分钟。",
        "你说的这个任务，最简单的切入点在哪里？不需要是最重要的。",
        "你不需要想好全部步骤才能开始。第一步想清楚就够了。",
        "我现在帮你把这件事从'一个大块'变成'几个小动作'。",
        "你觉得'必须做好'的压力来自哪里？是标准太高了？",
        "你是在害怕做不好，还是在害怕做完之后要面对的下一步？",
        "很多时候，阻力只在开始前五分钟。过了就好了。",
        "你把这件事想成了一个评判。其实它只是一个动作。",
        "完美主义是一种拖延。你允许自己做一个60分的版本吗？",
        "你不需要准备好。你只需要打开。剩下的会在过程中出现。"
    );

    public static final List<String> ACTION_SPLIT_CLARIFIES = List.of(
        "你现在能做的最小的那一步是什么？不需要最优，只需要开始。",
        "如果这件事可以交给别人做，你会交给谁？为什么自己不做？",
        "你觉得做这件事最难的部分是开始、中间还是结束？",
        "如果做到30%就算成功，你现在能开始吗？",
        "你有没有试过先做最简单的部分，而不是最重要的？",
        "你的目标是不是太大了？我们先只看今天能完成的那部分。",
        "如果你不开始，最坏的结果是什么？如果开始了呢？",
        "这件事里有没有一部分可以今天不做？先去掉它。",
        "你觉得你在等什么条件才能开始？那个条件真的必要吗？",
        "如果把这件事缩小成一个两分钟的任务，它是什么？"
    );

    // ================================================================
    //  RELATION_REVIEW – 关系复盘
    // ================================================================
    public static final List<String> RELATION_REVIEW_OPENS = List.of(
        "我们先把对方说了什么和你感受到什么分开。这是两件事。",
        "这段关系里，你最想被看见的部分是什么？",
        "你是在生气这件事本身，还是生气它让你想起了什么？",
        "如果对方能看到你现在的感受，你最希望TA理解的是哪一点？",
        "关系里的伤害经常不是因为发生了什么，而是因为什么没有被说出来。",
        "你在这段关系里，是在表达自己，还是在表演一个角色？",
        "我们先不评判谁对谁错。先看看你心里最重的是什么。",
        "你有没有注意到，你在用这段关系证明一个你已经相信的事？",
        "这段关系里你反复出现的情绪是什么？它是不是一种熟悉的模式？",
        "你是在说发生了什么，还是在说你希望发生什么？"
    );

    public static final List<String> RELATION_REVIEW_RECEIVES = List.of(
        "我听到你说了很多对方的行为。你自己呢？你在那个时刻最需要什么？",
        "你说'我不在意'的时候，身体是放松的还是紧绷的？",
        "这段关系里有一个你一直在等的回应。它是什么？",
        "你是在处理这件事，还是在处理这段关系里所有未说出口的事？",
        "你说了很多'TA应该'。这些'应该'是你们约定过的，还是你单方面的期望？",
        "我注意到你反复提到'算了'。这个'算了'里藏了什么没被说出来的话？",
        "你在这段关系里是不是一直在让步？你的边界在哪里？",
        "你说'TA不是故意的'。就算不是故意的，影响也是真实的。",
        "这段对话里有没有你一直想问但不敢问的问题？",
        "你是不是在用对方的反应来衡量自己的价值？"
    );

    public static final List<String> RELATION_REVIEW_CLARIFIES = List.of(
        "如果可以不伤害关系地表达真实感受，你会说什么？",
        "这段关系里，你的需要和对方的能力之间有差距吗？",
        "你是在修补这段关系，还是在证明自己值得被爱？",
        "如果这段关系不变，你能接受吗？如果不能，你需要什么改变？",
        "你有没有把过去的经历带进了现在这段关系？",
        "你愿意对方看到你此刻说的这些话吗？",
        "你觉得对方知道你在受伤吗？如果你不说，TA怎么能知道？",
        "这段关系让你变成了一个什么样的人？你喜欢这个自己吗？",
        "你是在原谅，还是在假装没事？这两件事很不一样。",
        "如果你最好的朋友经历了完全一样的事，你会怎么劝TA？"
    );

    // ================================================================
    //  Rhythm protection – 节奏守护
    // ================================================================
    public static final List<String> RHYTHM_SLOW_DOWN = List.of(
        "你今天说了很多了。我们先把刚才的内容收一收，好吗？",
        "今天的倾诉已经很充分了。要不要先把这些沉淀下来？",
        "你已经把最重的部分说出来了。现在可以轻轻放下了。",
        "说太多有时候反而会让自己更乱。我们停在这里看看？",
        "你已经很努力地在表达了。今天可以说到这里。",
        "再继续下去可能会超出你今天的能量。先歇一歇？",
        "表达需要力气。你的力气今天用了很多了，先保存一下。",
        "我听到你已经很累了。今天的你已经说得够多了。",
        "我们不赶。这些感受不会因为你今天不说就消失。明天还可以继续。",
        "有时候停顿比继续更有力量。我们在这里停一下。"
    );

    public static final List<String> RHYTHM_SETTLE = List.of(
        "今天的对话可以到这里了。我帮你把今天说的整理一下。",
        "你已经完成了一次很重要的自我表达。让我帮你沉淀下来。",
        "今天的对话内容很丰富，我建议我们先保存，明天可以继续。",
        "你现在可以不说了。今天说的已经很珍贵。",
        "今天的最后一件事：深呼吸三次。今天结束了。",
        "你的内心今天做了很多工作。它需要休息。",
        "对话是一种劳动。你今天的劳动结束了。可以下班了。",
        "我帮你记住今天说的。你不需要一直带着它。"
    );

    public static final List<String> RHYTHM_NIGHT = List.of(
        "已经很晚了。今晚不适合再深入这些话题。",
        "深夜的想法不一定准确。今晚先休息，明天可以继续。",
        "今天的能量可能不太够了。把今天的放一放，先照顾一下睡眠？",
        "夜晚会放大一切。现在的感受不一定是真实的全部。",
        "你现在需要的不是想明白，而是好好睡一觉。",
        "你的大脑在请求关机。这是身体在保护你。",
        "深夜的对话容易走入死角。我们明天白天再来看它。",
        "好，今天的门可以关上了。有什么事，明天重新打开。",
        "夜晚不是解决问题的时间。它是休息的时间。",
        "你的身体比你的想法更需要被照顾。先去睡吧。"
    );

    // ================================================================
    //  Settlement guidance – 沉淀引导
    // ================================================================
    public static final List<String> SETTLE_GUIDE = List.of(
        "我帮你把今天说的整理成了一个记录。你可以看看准不准确。",
        "今天的对话我看到了几个主题。让我帮你标注出来。",
        "你的表达里有一些值得长期观察的模式。我帮你记下来了。",
        "今天有一个可以轻轻推进的小行动。不需要现在做，知道就好。",
        "今天的对话可以收成一个简单的句子：你现在知道自己在意什么了。",
        "我帮你把今天的感受变成一个可以被保存的形状。",
        "今天的你不只是今天。你说的这些话里有一个一直都在的你。",
        "好了，今天的对话到这里。我帮你把最重要的部分留下来了。",
        "你已经完成了一次很有意义的自我对话。它会留在你的星空里。",
        "今天的话题不一定要有结论。有表达就够了。"
    );

    // ================================================================
    //  Emotion weather – 情绪天气
    // ================================================================
    public static final List<String> WEATHER_SUNNY = List.of(
        "今天有明亮的东西在。",
        "你今天的状态像是有光照进来。",
        "今天的感觉是温暖的。允许自己感受这个。",
        "你的情绪天空今天很晴朗。记住这个感觉。",
        "今天是一个可以大口呼吸的日子。",
        "阳光不是每天都有的。今天值得被记住。",
        "你今天的状态很好。不是因为你做了什么，而是因为你就是你。",
        "今天很适合轻轻地感谢一下自己。"
    );

    public static final List<String> WEATHER_CLOUDY = List.of(
        "今天有些遮挡，但轮廓还在。",
        "不是看不清，只是暂时没有光线。",
        "云层上面一直有太阳。它没有消失。",
        "阴天不是坏天气。它只是在准备什么。",
        "你不需要立刻看清。模糊也是今天的一部分。",
        "云会动的。你不需要把它推开。",
        "有些东西被遮住了，但它们没有消失。",
        "阴天适合慢一点。不着急赶路。"
    );

    public static final List<String> WEATHER_RAINY = List.of(
        "今天需要允许自己慢一点。",
        "下雨不是坏事。有时候需要被淋一下才能看清。",
        "雨天适合不赶路。先停下来也没关系。",
        "雨水是情绪的出口。让它下完。",
        "你不需要在雨天假装太阳在。",
        "下雨的时候，最重要的不是赶路，是找屋檐。",
        "雨会停的。你不需要催它。",
        "今天的雨适合待在室内。内心的室内。"
    );

    public static final List<String> WEATHER_STORM = List.of(
        "今天不适合强推自己。先找安全的地方待着。",
        "风暴会过去。现在不需要做任何决定。",
        "如果今天太难了，可以什么都不做。这本身就是一个选择。",
        "暴风雨里最重要的不是前进，是站稳。",
        "你不需要在风暴里找到方向。你只需要找到锚。",
        "今天的你不需要解释什么。先保护好自己。",
        "风暴来的时候，最先要做的是呼吸。",
        "等风暴过了再说。现在只需要活着。"
    );

    public static final List<String> WEATHER_FOGGY = List.of(
        "很多东西还没有被命名。不急。",
        "雾里看不清不代表没有路。只是暂时看不见。",
        "允许模糊。清晰不需要今天到来。",
        "雾会散的。你可以等。",
        "不是所有的路都需要看清才能走。有时候走一步雾就薄一层。",
        "你现在不需要知道答案。你只需要知道雾不是永恒的。",
        "模糊说明你的感受比语言快。等等它们会追上来的。",
        "雾里的东西不一定可怕。可能只是还没有被看清。"
    );

    // ================================================================
    //  Mode -> list mappings
    // ================================================================
    private static final Map<String, List<String>> OPENS = Map.of(
        "DAILY_TALK",      DAILY_TALK_OPENS,
        "THOUGHT_CLARIFY", THOUGHT_CLARIFY_OPENS,
        "SLEEP_REVIEW",    SLEEP_REVIEW_OPENS,
        "SOCRATIC",        SOCRATIC_OPENS,
        "ACTION_SPLIT",    ACTION_SPLIT_OPENS,
        "RELATION_REVIEW", RELATION_REVIEW_OPENS
    );

    private static final Map<String, List<String>> RECEIVES = Map.of(
        "DAILY_TALK",      DAILY_TALK_RECEIVES,
        "THOUGHT_CLARIFY", THOUGHT_CLARIFY_RECEIVES,
        "SLEEP_REVIEW",    SLEEP_REVIEW_RECEIVES,
        "SOCRATIC",        SOCRATIC_RECEIVES,
        "ACTION_SPLIT",    ACTION_SPLIT_RECEIVES,
        "RELATION_REVIEW", RELATION_REVIEW_RECEIVES
    );

    private static final Map<String, List<String>> CLARIFIES = Map.of(
        "DAILY_TALK",      DAILY_TALK_CLARIFIES,
        "THOUGHT_CLARIFY", THOUGHT_CLARIFY_CLARIFIES,
        "SLEEP_REVIEW",    SLEEP_REVIEW_CLARIFIES,
        "SOCRATIC",        SOCRATIC_CLARIFIES,
        "ACTION_SPLIT",    ACTION_SPLIT_CLARIFIES,
        "RELATION_REVIEW", RELATION_REVIEW_CLARIFIES
    );

    private static final Map<String, List<String>> RHYTHMS = Map.of(
        "SLOW_DOWN", RHYTHM_SLOW_DOWN,
        "SETTLE",    RHYTHM_SETTLE,
        "NIGHT",     RHYTHM_NIGHT
    );

    private static final Map<String, List<String>> WEATHERS = Map.of(
        "SUNNY",  WEATHER_SUNNY,
        "CLOUDY", WEATHER_CLOUDY,
        "RAINY",  WEATHER_RAINY,
        "STORM",  WEATHER_STORM,
        "FOGGY",  WEATHER_FOGGY
    );

    // ================================================================
    //  Public helper methods
    // ================================================================

    /** Return a random opening line for the given mode. */
    public static String randomOpen(String mode) {
        return pick(OPENS.getOrDefault(normalise(mode), DAILY_TALK_OPENS));
    }

    /** Return a random receive/reflection line for the given mode. */
    public static String randomReceive(String mode) {
        return pick(RECEIVES.getOrDefault(normalise(mode), DAILY_TALK_RECEIVES));
    }

    /** Return a random clarification question for the given mode. */
    public static String randomClarify(String mode) {
        return pick(CLARIFIES.getOrDefault(normalise(mode), DAILY_TALK_CLARIFIES));
    }

    /** Return a random rhythm-suggestion line. Type: SLOW_DOWN | SETTLE | NIGHT. */
    public static String randomRhythm(String type) {
        return pick(RHYTHMS.getOrDefault(type.toUpperCase(), RHYTHM_SLOW_DOWN));
    }

    /** Return a random settlement-guidance line. */
    public static String randomSettle() {
        return pick(SETTLE_GUIDE);
    }

    /** Return a random weather description. Type: SUNNY | CLOUDY | RAINY | STORM | FOGGY. */
    public static String randomWeather(String weatherType) {
        return pick(WEATHERS.getOrDefault(weatherType.toUpperCase(), WEATHER_FOGGY));
    }

    // ================================================================
    //  buildReply – compose a full multi-segment Aurora response
    // ================================================================

    /**
     * Build a multi-segment reply for MockLlmClient.
     *
     * @param mode         conversation mode key (e.g. DAILY_TALK)
     * @param userMessage  the raw user message text
     * @param shouldSlowDown  if true, append a rhythm-slow-down suggestion
     * @return a list of 2-3 message strings to be joined
     */
    public static List<String> buildReply(String mode, String userMessage, boolean shouldSlowDown) {
        String m = normalise(mode);
        List<String> segments = new ArrayList<>();

        // Segment 1 – always present: a reflective receive line
        segments.add(pick(RECEIVES.getOrDefault(m, DAILY_TALK_RECEIVES)));

        // Segment 2 – always present: a clarification / follow-up question
        segments.add(pick(CLARIFIES.getOrDefault(m, DAILY_TALK_CLARIFIES)));

        // Segment 3 – optional: rhythm protection when user has been talking a lot
        if (shouldSlowDown) {
            segments.add(pick(RHYTHM_SLOW_DOWN));
        }

        return segments;
    }

    /** Normalise mode string, falling back to DAILY_TALK. */
    private static String normalise(String mode) {
        if (mode == null || mode.isBlank()) return "DAILY_TALK";
        String upper = mode.toUpperCase().trim();
        if (OPENS.containsKey(upper)) return upper;
        // tolerate hyphenated / mixed-case variants
        for (String key : OPENS.keySet()) {
            if (upper.replace("-", "_").equals(key)) return key;
        }
        return "DAILY_TALK";
    }
}
