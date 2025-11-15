package com.k2fsa.sherpa.onnx

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        setFullscreen(true, true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }



    private fun setFullscreen(isSHowStatusBar: Boolean, isShowNavigationBar: Boolean) {
        var uiOptions =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        if(!isSHowStatusBar){
            uiOptions = uiOptions or View.SYSTEM_UI_FLAG_FULLSCREEN
        }
        if(!isShowNavigationBar){
            uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }

        //隐藏标题栏
        setNavigationStatusColor(Color.TRANSPARENT)
    }

    private fun setNavigationStatusColor(color: Int){
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.navigationBarColor = color
        window.statusBarColor = color
    }

}