package cn.edu.xyc.campus.data.local

import java.util.Calendar
import kotlin.random.Random

/** 一条名句：content 句子文本，source 出处（作品名） */
data class Quote(val content: String, val source: String)

/**
 * 本地兜底语录库：收录真实广为流传的动漫名句（33 条 / 22 部作品）。
 *
 * 纯 Kotlin 无 Android 依赖；作为「一言」接口不可用时的降级数据源——
 * [today] 按日期固定兜底首页每日摘录，[random] 供手动换一句。
 */
object QuoteStore {

    private val QUOTES = listOf(
        Quote("樱花飘落的速度，是秒速五厘米。", "秒速五厘米"),
        Quote("我们即使发了一千条短信，心与心的距离也只能靠近一厘米。", "秒速五厘米"),
        Quote("我想知道『爱』到底是什么意思。", "紫罗兰永恒花园"),
        Quote("自动手记人偶服务，随时为您效劳。", "紫罗兰永恒花园"),
        Quote("只要有想见的人，就不再是一个人了。", "夏目友人帐"),
        Quote("因为曾被温柔相待，所以也想成为温柔的人。", "夏目友人帐"),
        Quote("或许前路永夜，即便如此我也要前进，因为星光即使微弱也会为我照亮前路。", "四月是你的谎言"),
        Quote("能哭的地方，只有厕所和爸爸的怀里。", "CLANNAD"),
        Quote("人没有牺牲就什么都得不到，要得到什么就必须付出同等的代价。", "钢之炼金术师"),
        Quote("现在放弃的话，比赛就提前结束了。", "灌篮高手"),
        Quote("曾经发生的事情不会忘记，只是想不起来而已。", "千与千寻"),
        Quote("名字一旦被夺走，就再也找不到回家的路了。", "千与千寻"),
        Quote("生活坏到一定程度就会好起来，因为它无法更坏。", "龙猫"),
        Quote("如果把童年再放映一遍，我们一定会先开怀大笑，然后放声痛哭。", "龙猫"),
        Quote("无论拥有多么厉害的武器，人类离开土地都无法生存。", "天空之城"),
        Quote("虽然有时会失去信心，但我还是喜欢这座城市。", "魔女宅急便"),
        Quote("这一切，都是命运石之门的选择。", "命运石之门"),
        Quote("那一天，人类终于回想起了曾经被他们支配的恐怖。", "进击的巨人"),
        Quote("什么都无法舍弃的人，什么也改变不了。", "进击的巨人"),
        Quote("爱，是人类最扭曲的诅咒。", "咒术回战"),
        Quote("没关系，因为我可是最强的。", "咒术回战"),
        Quote("我当时为什么不试着去了解他呢。", "葬送的芙莉莲"),
        Quote("愿这个世界上，孩子们都不必再经历战争。", "间谍过家家"),
        Quote("心是原动力，心可以变得无限强大。", "鬼灭之刃"),
        Quote("我从不收回自己说过的话，这就是我的忍道。", "火影忍者"),
        Quote("不遵守规则的人是废物，不珍惜同伴的人连废物都不如。", "火影忍者"),
        Quote("我是要成为海贼王的男人！", "海贼王"),
        Quote("人的梦想，是不会结束的！", "海贼王"),
        Quote("背后受伤，是剑士的耻辱。", "海贼王"),
        Quote("不管是什么案件，真相永远只有一个！", "名侦探柯南"),
        Quote("不能逃避，不能逃避，不能逃避。", "新世纪福音战士"),
        Quote("能为别人的幸福而高兴，为别人的不幸而伤心，才是最重要的事。", "哆啦A梦"),
    )

    /** 本地兜底每日一句：取 yyyyMMdd 做随机种子，同一天固定同一句 */
    fun today(): Quote {
        val cal = Calendar.getInstance()
        val seed = cal.get(Calendar.YEAR) * 10_000 +
            (cal.get(Calendar.MONTH) + 1) * 100 +
            cal.get(Calendar.DAY_OF_MONTH)
        return QUOTES[Random(seed).nextInt(QUOTES.size)]
    }

    /** 本地随机换一句（exclude 不为空时结果必不与它相同） */
    fun random(exclude: Quote?): Quote {
        while (true) {
            val quote = QUOTES[Random.nextInt(QUOTES.size)]
            if (quote != exclude) return quote
        }
    }
}
