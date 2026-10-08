package cn.edu.xyc.campus.data.remote

import kotlin.random.Random

/** 一次吃饭推荐：canteen 食堂名，dish 菜品名 */
data class Meal(val canteen: String, val dish: String)

/**
 * 「今天吃什么」随机推荐：固定食堂 × 菜品清单本机组合（4 食堂 × 8 菜 = 32 种组合），
 * 纯本地随机，无网络依赖。
 */
object FoodRecommender {

    /** 食堂与其常见菜品 */
    private val MENUS: Map<String, List<String>> = linkedMapOf(
        "第一食堂" to listOf(
            "麻辣香锅", "黄焖鸡米饭", "重庆小面", "猪脚饭",
            "麻辣烫", "炒河粉", "咖喱鸡排饭", "水煮肉片",
        ),
        "第二食堂" to listOf(
            "兰州拉面", "石锅拌饭", "烤肉饭", "螺蛳粉",
            "烧鸭饭", "韩式炸鸡饭", "番茄鸡蛋面", "铁板炒饭",
        ),
        "第三食堂" to listOf(
            "梅菜扣肉饭", "糖醋里脊盖饭", "鱼香肉丝盖饭", "宫保鸡丁盖饭",
            "可乐鸡翅饭", "酸汤肥牛面", "狮子头套餐", "三鲜饺子",
        ),
        "清真食堂" to listOf(
            "手抓饭", "牛肉拉面", "羊肉泡馍", "大盘鸡拌面",
            "烤羊肉串饭", "牛肉水饺", "孜然牛肉盖饭", "清汤羊杂面",
        ),
    )

    private val CANTEENS = MENUS.keys.toList()

    /** 随机食堂+菜品；exclude != null 时结果必须 != exclude */
    fun random(exclude: Meal?): Meal {
        while (true) {
            val canteen = CANTEENS[Random.nextInt(CANTEENS.size)]
            val dishes = MENUS.getValue(canteen)
            val meal = Meal(canteen, dishes[Random.nextInt(dishes.size)])
            if (meal != exclude) return meal
        }
    }
}
