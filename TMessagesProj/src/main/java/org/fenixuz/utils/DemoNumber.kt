package org.fenixuz.utils

import android.R
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET

object DemoNumber {

    var DEMO_NUMBER_FOR_PLAY_CONSOLE = "+998335999479"

    fun loadingDialog(context: Context): AlertDialog {
        val progressBar = ProgressBar(context).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(
                1000,
                1000
            ).apply {
                gravity = Gravity.CENTER
                setPadding(50, 50, 50, 50)
            }
        }

        val demoAccountInfoTv = TextView(context).apply {
            text = "This account is a Demo account for testing the application."
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Theme.getColor(Theme.key_chats_menuItemText))
            setPadding(20, 40, 0, 20)
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER
            addView(demoAccountInfoTv)
            addView(progressBar)
        }

        val builder = AlertDialog.Builder(context).apply {
            setView(layout)
        }

        val dialog = builder.create()

        dialog.window?.apply {
            setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundDrawableResource(R.color.transparent)
        }

        return dialog
    }


    fun getSmsCode(callback: (String?) -> Unit) {

        val apiService =
            RetrofitClientForDemoNumber.instance.create(ApiServiceForDemoNumber::class.java)

        try {
            apiService.getSmsCode()
                .enqueue(object : Callback<String> {
                    override fun onResponse(call: Call<String>, response: Response<String>) {
                        if (response.isSuccessful) {
                            callback(extractNumber(response.body() ?: ""))
                        } else {
                            callback(null)
                        }
                    }

                    override fun onFailure(call: Call<String>, t: Throwable) {
                        callback(null)
                    }
                })
        } catch (e: Exception) {
            callback(null)
        }
    }

    fun extractNumber(input: String): String {
        val numberString = input.filter { it.isDigit() }
        return numberString
    }

    fun checkNumber(number: String): String {
        val phoneNumber = number.replace(" ", "")
        return if (phoneNumber == DEMO_NUMBER_FOR_PLAY_CONSOLE) {
            phoneNumber
        } else {
            ""
        }
    }
}

interface ApiServiceForDemoNumber {
    @GET("code.php")
    fun getSmsCode(): Call<String>
}

object RetrofitClientForDemoNumber {
    private const val BASE_URL = "https://xmax.uz/"

    var gson: Gson = GsonBuilder()
        .setLenient()
        .create()

    val instance: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

}
