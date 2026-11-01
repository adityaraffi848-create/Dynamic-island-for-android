package com.example.dynisland

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class Todo(var t: String, var d: Boolean)

object Todos {
    fun load(sp: SharedPreferences): MutableList<Todo> {
        val out = ArrayList<Todo>()
        try {
            val a = JSONArray(sp.getString("todo", "[]") ?: "[]")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Todo(o.getString("t"), o.optBoolean("d")))
            }
        } catch (_: Exception) {}
        return out
    }

    fun save(sp: SharedPreferences, l: List<Todo>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("t", it.t).put("d", it.d)) }
        sp.edit().putString("todo", a.toString()).apply()
    }
}
