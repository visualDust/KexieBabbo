package com.visualdust.kexiebabbo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.visualdust.kexiebabbo.agent.NonblockingSignAgent
import com.visualdust.kexiebabbo.agent.SignAgent
import java.util.function.Consumer
import com.visualdust.kexiebabbo.data.Resources as VDR


class ActivityAttendance : AppCompatActivity() {
    val agent = SignAgent.getAgent()
    val nbAgent = NonblockingSignAgent.getAgent()
    lateinit var timer: TextView
    lateinit var attendanceListGridLayout: GridLayout
    lateinit var topFiveListLinearLayout: LinearLayout
    lateinit var timeDesrip: TextView
    lateinit var progressBar: ProgressBar
    lateinit var clipboardManager: ClipboardManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        timer = findViewById<TextView>(R.id.dailyTimer_textView)
        attendanceListGridLayout = findViewById<GridLayout>(R.id.attendanceList_gridLayout)
        topFiveListLinearLayout = findViewById<LinearLayout>(R.id.topFiveList_linearLayout)
        timeDesrip = findViewById<TextView>(R.id.timeDescrip_textView)
        progressBar = findViewById<ProgressBar>(R.id.signInTime_ProcessBar)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        timer.setOnLongClickListener {
            if (timer.text == "长按进行签到" || timer.text == "0")
                refreshLogIn(SignAgent.UserStatus.ONLINE)
            else refreshLogIn(SignAgent.UserStatus.OFFLINE)
            true
        }

        Thread {
            while (true) {
                Thread.sleep(10000)
                refresh()
            }
        }.start()
    }

    fun refreshLogIn(status: SignAgent.UserStatus) {
        if (status == SignAgent.UserStatus.OFFLINE) {
            nbAgent.handleSignOut(VDR.userID, Consumer {
                timer.text = "长按进行签到"
                progressBar.progress = 0
                timeDesrip.text = "本周已签到0分钟，还需要1080分钟"
            })
            refresh()
        } else {
            nbAgent.handleSignOut(VDR.userID, Consumer {
                nbAgent.handleSignInResponse(VDR.userID, Consumer {
                    val response = it.body!!.string()
                    val code_start = response.indexOf("code\":") + 6
                    val code_end = response.indexOf(",\"", code_start)
                    val code = response.substring(code_start, code_end)
                    val time_start = response.indexOf("allTime\":") + 9
                    val time_end = response.indexOf(",\"", time_start)
                    val time = (response.substring(time_start, time_end).toDouble() * 60.0)
                    if (code == "500") {
                        runOnUiThread{Toast.makeText(this, "你号没了！！！！", Toast.LENGTH_SHORT).show()}
                    }
                    else{
                        val username_start = response.indexOf("username\":") + 11
                        val username_end = response.indexOf(",\"", username_start)
                        val school_number_start = response.indexOf("userid\":") + 8
                        val school_number_end = response.indexOf(",\"", school_number_start)
                        val status_start = response.indexOf("message\":") + 8
                        val status_end = response.indexOf(",\"", status_start)

                        val status = response.substring(status_start, status_end)
                        val school_number = response.substring(school_number_start, school_number_end)
                        val username = response.substring(username_start, username_end)
                        if (code == "200" && status == "签到成功") {
                            Toast.makeText(this, school_number + username + status , Toast.LENGTH_SHORT).show()
                        }
                        else if(code == "200" && status == "签退成功"){
                            Toast.makeText(this, school_number + username + status , Toast.LENGTH_SHORT).show()
                        }
                        else{
                            runOnUiThread{Toast.makeText(this, "你号没了！！", Toast.LENGTH_SHORT).show()}
                        }
                    }
                    runOnUiThread {
                        timer.text = "${time.toInt()}"
                        if (progressBar.max < time.toInt())
                            progressBar.max = time.toInt()
                        progressBar.progress = time.toInt()
                        timeDesrip.text = "本周已签到${time.toInt()}，还需要${(1080 - time.toInt())}分钟"
                    }
                    refresh()
                })
            })
        }
    }

    fun refresh() {
        nbAgent.handleAttendance(Consumer {
            runOnUiThread { attendanceListGridLayout.removeAllViews() }
            for (member in it) {
                val bt = Button(this)
                bt.setText(member.name)
                val popupMenu = PopupMenu(this, bt)
                val menu = popupMenu.menu
                menu.add(Menu.NONE, Menu.FIRST + 0, 0, "ID : ${member.id}")
                    .setOnMenuItemClickListener {
                        clipboardManager.setPrimaryClip(
                            ClipData.newPlainText(
                                "ID",
                                member.id.toString()
                            )
                        )
                        Toast.makeText(applicationContext, "已复制到剪贴板", Toast.LENGTH_LONG).show()
                        true
                    }
                menu.add(Menu.NONE, Menu.FIRST + 1, 1, "部门 : ${member.dept}")
                    .setOnMenuItemClickListener {
                        clipboardManager.setPrimaryClip(
                            ClipData.newPlainText(
                                "Department",
                                member.dept
                            )
                        )
                        Toast.makeText(applicationContext, "已复制到剪贴板", Toast.LENGTH_LONG).show()
                        true
                    }
                menu.add(Menu.NONE, Menu.FIRST + 2, 2, "地点 : ${member.location}")
                    .setOnMenuItemClickListener {
                        clipboardManager.setPrimaryClip(
                            ClipData.newPlainText(
                                "Location",
                                member.location
                            )
                        )
                        Toast.makeText(applicationContext, "已复制到剪贴板", Toast.LENGTH_LONG).show()
                        true
                    }
                menu.add(Menu.NONE, Menu.FIRST + 3, 3, "是兄弟就举报！")
                    .setOnMenuItemClickListener {
                        nbAgent.handleComplaint(member.id, Consumer {
                            if (member.id == VDR.userID)
                                refreshLogIn(SignAgent.UserStatus.OFFLINE)
                            else refresh()
                        })
                        Toast.makeText(applicationContext, "举报请求已发送", Toast.LENGTH_LONG)
                            .show()
                        true
                    }
                bt.setOnClickListener {
                    popupMenu.show()
                }
                runOnUiThread { attendanceListGridLayout.addView(bt) }
            }
        })
        nbAgent.handleTopFives(Consumer {
            runOnUiThread { topFiveListLinearLayout.removeAllViews() }
            for (ranker in it) {
                val bt = Button(this)
                bt.setText("${ranker.name} ${ranker.time.toInt()}分钟")
                runOnUiThread { topFiveListLinearLayout.addView(bt) }
            }
        })
    }
}
