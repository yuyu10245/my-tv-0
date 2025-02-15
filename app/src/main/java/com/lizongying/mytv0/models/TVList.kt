package com.lizongying.mytv0.models

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.lizongying.mytv0.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object TVList {
    private const val TAG = "TVList"
    private const val FILE_NAME = "channels.json"
    private lateinit var appDirectory: File
    private lateinit var serverUrl: String
    private lateinit var list: List<TV>
    lateinit var listModel: List<TVModel>
    val groupModel = TVGroupModel()

    private val _position = MutableLiveData<Int>()
    val position: LiveData<Int>
        get() = _position

    fun init(context: Context) {
        _position.value = 0
        appDirectory = context.filesDir
        serverUrl = context.resources.getString(R.string.server_url)
        val file = File(appDirectory, FILE_NAME)
        val str = if (file.exists()) {
            Log.i(TAG, "local file")
            file.readText()
        } else {
            Log.i(TAG, "read resource")
            context.resources.openRawResource(R.raw.channels).bufferedReader()
                .use { it.readText() }
        }
        Log.i("", "channel $str")
        str2List(str)
    }

    private fun update() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i("", "do request $serverUrl")
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder().url(serverUrl).build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val file = File(appDirectory, FILE_NAME)
                    if (!file.exists()) {
                        file.createNewFile()
                    }
                    val str = response.body()!!.string()
                    Log.i("", "request str $str")

                    file.writeText(str)
                    withContext(Dispatchers.Main) {
                        str2List(str)
                    }
                } else {
                    Log.e("", "request status ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("", "request error $e")
            }
        }
    }

    fun update(serverUrl: String) {
        this.serverUrl = serverUrl
        Log.i("", "update $serverUrl")
        update()
    }

    private fun str2List(str: String) {
        val type = object : com.google.gson.reflect.TypeToken<List<TV>>() {}.type
        list = com.google.gson.Gson().fromJson(str, type)
        Log.i("TVList", "$list")

        listModel = list.map { tv ->
            TVModel(tv)
        }

        val group: MutableList<TVListModel> = mutableListOf()

        var tvListModel = TVListModel("我的收藏")
        group.add(tvListModel)

        tvListModel = TVListModel("全部频道")
        tvListModel.setTVListModel(listModel)
        group.add(tvListModel)

        val map: MutableMap<String, MutableList<TVModel>> = mutableMapOf()
        for ((id, v) in list.withIndex()) {
            if (v.group !in map) {
                map[v.group] = mutableListOf()
            }
            v.id = id
            map[v.group]?.add(TVModel(v))
        }

        for ((k, v) in map) {
            tvListModel = TVListModel(k)
            for (v1 in v) {
                tvListModel.addTVModel(v1)
            }
            group.add(tvListModel)
        }

        groupModel.setTVListModelList(group)
    }

    fun getTVModel(idx: Int): TVModel {
        return listModel[idx]
    }

    fun setPosition(position: Int) {
        if (_position.value != position) {
            _position.value = position
        }

        // set a new position or retry when position same
        listModel[position].setReady()
    }

    fun size(): Int {
        return listModel.size
    }
}