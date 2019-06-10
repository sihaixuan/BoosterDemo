package com.sihaixuan.booster.library

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var rv : RecyclerView



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.library_activity_main)

        initViews()


    }



    private fun initViews(){
        rv = findViewById(R.id.library_rv)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = SimpleAdapter(this)
    }
}
