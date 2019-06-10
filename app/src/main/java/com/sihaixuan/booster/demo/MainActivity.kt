package com.sihaixuan.booster.demo

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.HandlerThread
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import com.sihaixuan.booster.library.Test
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var rv : RecyclerView

   companion object{
       const val sp_name = "BoosterDemo"
   }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()

        var spName = sp_name
        var spName2 = "BoosterDemo"

        var user = User.DEFAULT


        var name = User.NAME

        var age = User.age


        var libResId = R.string.library_str




        Test().text()


        var editor = getSharedPreferences(sp_name,0).edit()
        editor.commit()
        var result = editor.commit()
        ShadowEditor.apply(editor)
//        println(result)

        testThread()
    }

    fun testThread(){
        Thread({
            while (true) {
                Thread.sleep(5)
            }
        }, "#Booster").start()
        HandlerThread("Booster").start()
        val pool1 = Executors.newCachedThreadPool()
        val pool2 = Executors.newCachedThreadPool()

        for(i in 0..100){
            pool1.submit{
                run{
                    Thread.sleep(15)
                }
            }
        }

        for(i in 0..100){
            pool2.submit{
                run{
                    Thread.sleep(15)
                }
            }
        }




    }

    fun initViews(){
        rv = findViewById(R.id.rv)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = SimpleAdapter(this)
    }
}
