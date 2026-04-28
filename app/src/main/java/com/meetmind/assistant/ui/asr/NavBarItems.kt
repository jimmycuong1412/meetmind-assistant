package com.meetmind.assistant.ui

import com.meetmind.assistant.ui.icons.AppIcons

object NavBarItems {
    val BarItems = listOf(
        BarItem(
            title = "Home",
            image = AppIcons.Home,
            route = "home",
        ),
        BarItem(
            title = "Help",
            image = AppIcons.Info,
            route = "help",
        ),
    )
}