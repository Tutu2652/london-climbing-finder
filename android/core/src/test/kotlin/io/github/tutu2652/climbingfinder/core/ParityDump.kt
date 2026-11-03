package io.github.tutu2652.climbingfinder.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Harness for tests/test_pricing.py. Reads {"gyms", "bank_holidays", "dates", "out"}
 * from the JSON file named in args[0] and writes, for every date, climber type and
 * gym: [iso, conc, id, [best price, label, when] | null, [price, label] at 10:00, at 19:00].
 */
fun main(args: Array<String>) {
    val input = Json.parseToJsonElement(File(args[0]).readText()).jsonObject
    val data = ClimbingData.parse(File(input.getValue("gyms").jsonPrimitive.content).readText())
    val pricer = Pricer(input.getValue("bank_holidays").jsonArray.map { it.jsonPrimitive.content }.toSet())
    fun pair(r: PriceResult) = buildJsonArray {
        add(r.price?.let { JsonPrimitive(it) } ?: JsonNull)
        add(if (r.label == "no data") JsonNull else JsonPrimitive(r.label))
    }
    val out = buildJsonArray {
        for (iso in input.getValue("dates").jsonArray.map { it.jsonPrimitive.content }) {
            val p = iso.split("-").map { it.toInt() }
            val day = Moment(p[0], p[1], p[2])
            for (conc in listOf(false, true)) {
                for (g in data.gyms) {
                    val b = pricer.bestOnDay(g, day, conc)
                    add(buildJsonArray {
                        add(JsonPrimitive(iso)); add(JsonPrimitive(conc)); add(JsonPrimitive(g.id))
                        add(b?.let { JsonArray(listOf(JsonPrimitive(it.price), JsonPrimitive(it.label), JsonPrimitive(it.`when`))) } ?: JsonNull)
                        add(pair(pricer.applicable(g, day.at(600), conc)))
                        add(pair(pricer.applicable(g, day.at(1140), conc)))
                    })
                }
            }
        }
    }
    File(input.getValue("out").jsonPrimitive.content).writeText(out.toString())
}
