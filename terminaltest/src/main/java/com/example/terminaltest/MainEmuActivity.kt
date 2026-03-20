package com.example.terminaltest

import android.content.Intent
import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.terminaltest.databinding.ActivityMainBinding
import com.example.terminaltest.ui.home.EmuAppSharedPreferences
import com.example.terminaltest.ui.home.TerminalManager
import com.example.terminaltest.ui.home.TerminalService
import com.termux.view.TerminalView

class MainEmuActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    val terminalManager = TerminalManager(this)
    lateinit var preferences: EmuAppSharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)


        // 启动terminalService
        terminalManager.onCreate(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        terminalManager.onDestroy(this)
    }
}